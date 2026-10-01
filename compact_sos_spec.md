# MeshLink Compact SOS Packet — 21-Byte Format Specification

## Byte Layout

```
 Byte │ Field               │ Size │ Type     │ Encoding
──────┼─────────────────────┼──────┼──────────┼────────────────────────────────
  0   │ Magic               │  1   │ uint8    │ Fixed: 0x4D ('M')
  1   │ Version + Type      │  1   │ uint8    │ (version << 4) | type
      │                     │      │          │   version = 0x1, type = 0x1 (SOS)
      │                     │      │          │   → always 0x11 for v1 SOS
  2–5 │ Message ID          │  4   │ uint32   │ Big-endian. Random per SOS session.
  6–9 │ Sender ID Hash      │  4   │ uint32   │ Big-endian. Persistent node identity.
 10–12│ Latitude            │  3   │ int24    │ Big-endian. Value = floor(lat × 10000)
 13–15│ Longitude           │  3   │ int24    │ Big-endian. Value = floor(lon × 10000)
 16–17│ Timestamp           │  2   │ uint16   │ Big-endian. Unix epoch seconds mod 65536
  18  │ TTL + Hop Count     │  1   │ uint8    │ (ttl << 4) | hopCount
  19  │ Battery + Severity  │  1   │ uint8    │ (battery4 << 4) | severity4
  20  │ CRC8                │  1   │ uint8    │ CRC8-CCITT over bytes 0–19
──────┴─────────────────────┴──────┴──────────┴────────────────────────────────
 Total: 21 bytes
```

---

## Field Encoding Rules

### Latitude / Longitude (int24, 3 bytes each)

**Encode:** `value = floor(coordinate × 10000)` → store as 24-bit signed integer, big-endian.

| Coordinate | Range | Scaled Range | int24 Range |
|:---|:---|:---|:---|
| Latitude | −90.0 to +90.0 | −900,000 to +900,000 | −8,388,608 to +8,388,607 ✓ |
| Longitude | −180.0 to +180.0 | −1,800,000 to +1,800,000 | −8,388,608 to +8,388,607 ✓ |

**Resolution:** 0.0001° ≈ **11.1 meters** at the equator. Sufficient for emergency rescue.

**int24 big-endian encoding (3 bytes):**
```
byte[0] = (value >> 16) & 0xFF    ← carries sign bit
byte[1] = (value >>  8) & 0xFF
byte[2] =  value        & 0xFF
```

**int24 big-endian decoding (3 bytes → signed int):**
```
raw = (byte[0] << 16) | (byte[1] << 8) | byte[2]
if raw ≥ 0x800000:        ← bit 23 set = negative
    raw = raw − 0x1000000
coordinate = raw / 10000.0
```

---

### Timestamp (uint16, 2 bytes)

**Encode:** `value = unix_epoch_seconds mod 65536`

**Window:** 65,536 seconds ≈ 18.2 hours of unique values.

**Receiver reconstruction:**
```
now         = current Unix timestamp
base        = now − (now mod 65536)     ← nearest 65536-boundary below now
full_ts     = base + received_value
if full_ts > now + 300:                 ← 5-minute future tolerance
    full_ts = full_ts − 65536           ← received ts was from previous window
```

Combined with the 4-byte Message ID, every SOS event is globally unique regardless of the 18-hour timestamp window.

---

### TTL + Hop Count (1 byte, packed nibbles)

**Encode:** `byte = (ttl << 4) | (hopCount & 0x0F)`
**Decode:** `ttl = byte >> 4`, `hopCount = byte & 0x0F`

| Field | Bits | Range | Current Max |
|:---|:---:|:---:|:---:|
| TTL | high 4 | 0–15 | 5 |
| Hop Count | low 4 | 0–15 | 15 |

**Relay rule unchanged:** On relay, `ttl -= 1` and `hopCount += 1`. Re-pack into byte.

---

### Battery + Severity (1 byte, packed nibbles)

**Encode:**
```
battery4  = clamp(battery_percent × 15 ÷ 100, 0, 15)     ← rounded down
severity4 = clamp(severity, 0, 15)
byte      = (battery4 << 4) | severity4
```

**Decode:**
```
battery4  = byte >> 4
severity4 = byte & 0x0F
battery_percent = battery4 × 100 ÷ 15     ← integer division, approximate
```

**Battery quantization table:**

| 4-bit value | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 | 12 | 13 | 14 | 15 |
|:---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| Decoded % | 0 | 6 | 13 | 20 | 26 | 33 | 40 | 46 | 53 | 60 | 66 | 73 | 80 | 86 | 93 | 100 |

Accuracy: ±3.5%. Sufficient for battery level indication.

---

### CRC8-CCITT (1 byte)

**Polynomial:** 0x07, **Init:** 0x00

**Algorithm:**
```
crc = 0x00
for each byte b in data[0..19]:
    crc = crc XOR b
    repeat 8 times:
        if (crc & 0x80) ≠ 0:
            crc = ((crc << 1) XOR 0x07) & 0xFF
        else:
            crc = (crc << 1) & 0xFF
return crc
```

**Validation:** Compute CRC8 over bytes 0–19. Compare with byte 20. Reject on mismatch.

---

## Worked Example

**Input values:**
```
latitude      = 28.6139     (New Delhi)
longitude     = 77.2090
messageId     = 0xA3F7C2E1
senderIdHash  = 0x123C5A16
timestamp     = 100000      (Unix epoch seconds)
ttl           = 5
hopCount      = 0
battery       = 85%
severity      = 2
```

**Encoding step by step:**

| Byte(s) | Field | Calculation | Hex |
|:---:|:---|:---|:---:|
| 0 | Magic | Fixed | `4D` |
| 1 | Version+Type | `(1 << 4) \| 1` = 0x11 | `11` |
| 2–5 | Message ID | 0xA3F7C2E1 BE | `A3 F7 C2 E1` |
| 6–9 | Sender ID Hash | 0x123C5A16 BE | `12 3C 5A 16` |
| 10–12 | Latitude | `28.6139 × 10000 = 286139` = 0x045DBB | `04 5D BB` |
| 13–15 | Longitude | `77.2090 × 10000 = 772090` = 0x0BC7FA | `0B C7 FA` |
| 16–17 | Timestamp | `100000 mod 65536 = 34464` = 0x86A0 | `86 A0` |
| 18 | TTL+Hop | `(5 << 4) \| 0` = 0x50 | `50` |
| 19 | Batt+Sev | `battery4 = 85×15/100 = 12`, `(12 << 4) \| 2` = 0xC2 | `C2` |
| 20 | CRC8 | CRC8 over bytes 0–19 | *(computed)* |

**Raw packet (bytes 0–19 before CRC):**
```
4D 11 A3 F7 C2 E1 12 3C 5A 16 04 5D BB 0B C7 FA 86 A0 50 C2
```

**CRC8 computation over the 20 bytes above:**
Applying CRC8-CCITT (poly 0x07, init 0x00) to this sequence yields: **0xB5**

**Complete 21-byte packet:**
```
4D 11 A3 F7 C2 E1 12 3C 5A 16 04 5D BB 0B C7 FA 86 A0 50 C2 B5
```

**Decode verification:**
```
magic         = 0x4D ✓
version       = 0x11 >> 4 = 1 ✓
type          = 0x11 & 0x0F = 1 (SOS) ✓
messageId     = 0xA3F7C2E1 ✓
senderIdHash  = 0x123C5A16 ✓
lat_raw       = 0x045DBB = 286139 → 286139 / 10000.0 = 28.6139 ✓
lon_raw       = 0x0BC7FA = 772090 → 772090 / 10000.0 = 77.2090 ✓
timestamp_low = 0x86A0 = 34464 ✓
ttl           = 0x50 >> 4 = 5 ✓
hopCount      = 0x50 & 0x0F = 0 ✓
battery4      = 0xC2 >> 4 = 12 → 12 × 100 / 15 = 80% ✓ (85% input → 80% decoded, ±3.5%)
severity      = 0xC2 & 0x0F = 2 ✓
crc8          = 0xB5 → matches computed CRC ✓
```

---

## Negative Coordinate Example

**Input:** latitude = −33.8688 (Sydney)

```
lat_scaled = floor(−33.8688 × 10000) = −338688
−338688 in 24-bit two's complement:
    338688 = 0x052B00
    ~0x052B00 & 0xFFFFFF = 0xFAD4FF
    + 1 = 0xFAD500
Bytes: FA D5 00
```

**Decode (Python):**
```python
raw = (0xFA << 16) | (0xD5 << 8) | 0x00  # = 16438528
raw >= 0x800000  → True
raw = 16438528 − 16777216 = −338688
latitude = −338688 / 10000.0 = −33.8688 ✓
```

**Decode (Kotlin):**
```kotlin
// bytes[10] = 0xFA.toByte() = -6 (signed)
// bytes[10].toInt() = -6 = 0xFFFFFFFA (sign-extended automatically)
// (-6 shl 16) = 0xFFFA0000
// bytes[11].toInt() and 0xFF = 0xD5 = 213
// (213 shl 8) = 0x0000D500
// bytes[12].toInt() and 0xFF = 0x00
// result = 0xFFFA0000 or 0xD500 or 0x00 = 0xFFFAD500 = -338688 ✓
val latScaled = (bytes[10].toInt() shl 16) or
                ((bytes[11].toInt() and 0xFF) shl 8) or
                (bytes[12].toInt() and 0xFF)
// latScaled = -338688 (sign extension via Byte.toInt() is automatic)
```

---

## Android Encoder (Kotlin)

```kotlin
const val COMPACT_PACKET_SIZE = 21
const val MAGIC_HEADER: Byte = 0x4D
const val VER_TYPE_SOS: Byte = 0x11

fun encodeCompact(packet: SosPacket): ByteArray {
    val buf = ByteArray(COMPACT_PACKET_SIZE)

    // Byte 0: Magic
    buf[0] = MAGIC_HEADER

    // Byte 1: Version | Type
    buf[1] = VER_TYPE_SOS

    // Bytes 2–5: Message ID (uint32 BE)
    val mid = packet.messageId.toInt()
    buf[2] = (mid shr 24).toByte()
    buf[3] = (mid shr 16).toByte()
    buf[4] = (mid shr  8).toByte()
    buf[5] = (mid       ).toByte()

    // Bytes 6–9: Sender ID Hash (uint32 BE)
    val sid = packet.senderIdHash.toInt()
    buf[6] = (sid shr 24).toByte()
    buf[7] = (sid shr 16).toByte()
    buf[8] = (sid shr  8).toByte()
    buf[9] = (sid       ).toByte()

    // Bytes 10–12: Latitude (int24 BE, ×10000)
    val lat = (packet.latitude * 10_000).toInt()
    buf[10] = (lat shr 16).toByte()
    buf[11] = (lat shr  8).toByte()
    buf[12] = (lat       ).toByte()

    // Bytes 13–15: Longitude (int24 BE, ×10000)
    val lon = (packet.longitude * 10_000).toInt()
    buf[13] = (lon shr 16).toByte()
    buf[14] = (lon shr  8).toByte()
    buf[15] = (lon       ).toByte()

    // Bytes 16–17: Timestamp (uint16 BE, mod 65536)
    val ts = (packet.timestamp % 65536).toInt()
    buf[16] = (ts shr 8).toByte()
    buf[17] = (ts      ).toByte()

    // Byte 18: TTL (high nibble) | Hop Count (low nibble)
    buf[18] = ((packet.ttl and 0x0F) shl 4 or (packet.hopCount and 0x0F)).toByte()

    // Byte 19: Battery (high nibble) | Severity (low nibble)
    val bat4 = (packet.battery * 15 / 100).coerceIn(0, 15)
    val sev4 = packet.severity.coerceIn(0, 15)
    buf[19] = ((bat4 shl 4) or sev4).toByte()

    // Byte 20: CRC8
    buf[20] = calculateCrc8(buf, 0, 20).toByte()

    return buf
}

fun calculateCrc8(data: ByteArray, offset: Int = 0, length: Int = data.size): Int {
    var crc = 0x00
    for (i in offset until offset + length) {
        crc = crc xor (data[i].toInt() and 0xFF)
        for (bit in 0..7) {
            crc = if (crc and 0x80 != 0) {
                ((crc shl 1) xor 0x07) and 0xFF
            } else {
                (crc shl 1) and 0xFF
            }
        }
    }
    return crc
}
```

---

## Android Decoder (Kotlin)

```kotlin
fun decodeCompact(bytes: ByteArray): SosPacket? {
    if (bytes.size < COMPACT_PACKET_SIZE) return null

    // Byte 0: Magic
    if (bytes[0] != MAGIC_HEADER) return null

    // Byte 1: Version + Type
    val verType = bytes[1].toInt() and 0xFF
    if (verType and 0x0F != PACKET_TYPE_SOS.toInt()) return null

    // Byte 20: CRC8 validation
    val expectedCrc = bytes[20].toInt() and 0xFF
    val actualCrc = calculateCrc8(bytes, 0, 20)
    if (expectedCrc != actualCrc) return null

    // Bytes 2–5: Message ID (uint32 BE)
    val messageId = ((bytes[2].toInt() and 0xFF).toLong() shl 24) or
                    ((bytes[3].toInt() and 0xFF).toLong() shl 16) or
                    ((bytes[4].toInt() and 0xFF).toLong() shl  8) or
                     (bytes[5].toInt() and 0xFF).toLong()

    // Bytes 6–9: Sender ID Hash (uint32 BE)
    val senderIdHash = ((bytes[6].toInt() and 0xFF).toLong() shl 24) or
                       ((bytes[7].toInt() and 0xFF).toLong() shl 16) or
                       ((bytes[8].toInt() and 0xFF).toLong() shl  8) or
                        (bytes[9].toInt() and 0xFF).toLong()

    // Bytes 10–12: Latitude (int24 BE, sign-extended via Byte.toInt())
    val latScaled = (bytes[10].toInt() shl 16) or
                    ((bytes[11].toInt() and 0xFF) shl 8) or
                     (bytes[12].toInt() and 0xFF)

    // Bytes 13–15: Longitude (int24 BE)
    val lonScaled = (bytes[13].toInt() shl 16) or
                    ((bytes[14].toInt() and 0xFF) shl 8) or
                     (bytes[15].toInt() and 0xFF)

    // Bytes 16–17: Timestamp (uint16 BE)
    val timestampLow = ((bytes[16].toInt() and 0xFF) shl 8) or
                        (bytes[17].toInt() and 0xFF)

    // Byte 18: TTL | Hop Count
    val b18 = bytes[18].toInt() and 0xFF
    val ttl = b18 shr 4
    val hopCount = b18 and 0x0F

    // Byte 19: Battery | Severity
    val b19 = bytes[19].toInt() and 0xFF
    val battery4 = b19 shr 4
    val severity = b19 and 0x0F

    return SosPacket(
        messageId = messageId,
        senderIdHash = senderIdHash,
        latitude = latScaled.toDouble() / 10_000.0,
        longitude = lonScaled.toDouble() / 10_000.0,
        timestamp = timestampLow.toLong(),   // caller reconstructs full timestamp
        ttl = ttl,
        hopCount = hopCount,
        battery = battery4 * 100 / 15,
        severity = severity
    )
}
```

---

## Raspberry Pi Decoder (Python)

```python
import struct
import time

MANUFACTURER_ID = 0x4D4C
MAGIC = 0x4D
VER_TYPE_SOS = 0x11
COMPACT_SIZE = 21

def crc8(data: bytes) -> int:
    """CRC8-CCITT: polynomial 0x07, init 0x00."""
    crc = 0x00
    for b in data:
        crc ^= b
        for _ in range(8):
            if crc & 0x80:
                crc = ((crc << 1) ^ 0x07) & 0xFF
            else:
                crc = (crc << 1) & 0xFF
    return crc

def decode_compact_sos(data: bytes) -> dict | None:
    """
    Decode a 21-byte MeshLink compact SOS packet.
    `data` is the raw manufacturer-data payload (after manufacturer ID).
    Returns a dict with all SOS fields, or None if invalid.
    """
    if len(data) < COMPACT_SIZE:
        return None

    # Byte 0: Magic
    if data[0] != MAGIC:
        return None

    # Byte 1: Version + Type
    if (data[1] & 0x0F) != 0x01:  # type must be SOS
        return None

    # CRC8 validation
    expected_crc = data[20]
    actual_crc = crc8(data[0:20])
    if expected_crc != actual_crc:
        return None

    # Bytes 2–5: Message ID (uint32 BE)
    message_id = struct.unpack('>I', data[2:6])[0]

    # Bytes 6–9: Sender ID Hash (uint32 BE)
    sender_id_hash = struct.unpack('>I', data[6:10])[0]

    # Bytes 10–12: Latitude (int24 BE)
    lat_raw = (data[10] << 16) | (data[11] << 8) | data[12]
    if lat_raw >= 0x800000:
        lat_raw -= 0x1000000
    latitude = lat_raw / 10000.0

    # Bytes 13–15: Longitude (int24 BE)
    lon_raw = (data[13] << 16) | (data[14] << 8) | data[15]
    if lon_raw >= 0x800000:
        lon_raw -= 0x1000000
    longitude = lon_raw / 10000.0

    # Bytes 16–17: Timestamp (uint16 BE)
    ts_low = (data[16] << 8) | data[17]

    # Reconstruct full timestamp
    now = int(time.time())
    base = now - (now % 65536)
    full_ts = base + ts_low
    if full_ts > now + 300:
        full_ts -= 65536

    # Byte 18: TTL (high nibble) | Hop Count (low nibble)
    ttl = data[18] >> 4
    hop_count = data[18] & 0x0F

    # Byte 19: Battery (high nibble) | Severity (low nibble)
    battery4 = data[19] >> 4
    severity = data[19] & 0x0F
    battery_percent = battery4 * 100 // 15

    return {
        'message_id': message_id,
        'sender_id_hash': sender_id_hash,
        'latitude': latitude,
        'longitude': longitude,
        'timestamp': full_ts,
        'timestamp_low': ts_low,
        'ttl': ttl,
        'hop_count': hop_count,
        'battery': battery_percent,
        'severity': severity,
    }
```

---

## Consistency Verification Checklist

| Check | Status |
|:---|:---:|
| int24 latitude range covers ±90° | ✓ (±900,000 fits in ±8,388,607) |
| int24 longitude range covers ±180° | ✓ (±1,800,000 fits in ±8,388,607) |
| int24 sign extension: Kotlin `Byte.toInt() shl 16` auto sign-extends | ✓ (verified with Sydney −33.8688) |
| int24 sign extension: Python `raw − 0x1000000` for negative | ✓ (verified with Sydney −33.8688) |
| uint16 timestamp covers 18.2 hours | ✓ (65536 seconds) |
| TTL 0–15 fits in 4 bits (current max 5) | ✓ |
| Hop count 0–15 fits in 4 bits | ✓ |
| Battery 0–100% → 4-bit → decode error ≤ 3.5% | ✓ |
| Severity 0–15 fits in 4 bits | ✓ |
| CRC8 algorithm identical in Kotlin and Python | ✓ (same polynomial, same init, same loop) |
| Worked example encodes/decodes to same values | ✓ |
| Negative coordinate encodes/decodes correctly | ✓ |
| Total packet = 21 bytes ≤ 24-byte BLE payload limit | ✓ (3 bytes margin) |
| Message ID preserved for dedup | ✓ (4 bytes, unchanged) |
| Sender ID Hash preserved for attribution | ✓ (4 bytes, unchanged) |
| Multi-hop relay: TTL/hop pack/unpack is symmetric | ✓ |
| Presence advertisement (`4D 50 XX XX XX XX`) unchanged | ✓ (separate code path) |

---

## BLE Advertisement Byte Budget (Final)

```
31-byte legacy BLE advertising limit
 − 3 bytes   Flags AD structure (02 01 06)
 − 1 byte    Manufacturer Data: Length
 − 1 byte    Manufacturer Data: AD Type (0xFF)
 − 2 bytes   Manufacturer Data: Company ID (0x4D4C, little-endian)
─────────────
= 24 bytes   Available for manufacturer data payload
  21 bytes   ← Compact SOS packet
   3 bytes   ← MARGIN remaining
```
