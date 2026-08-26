# MeshLink Rescue — BLE Encoding Specification

## 1. BLE Advertising Frame Structure

BLE Legacy Advertising frames allow up to **31 bytes** of payload in an advertising packet. To ensure full compatibility across all Android devices, iOS devices, and Linux/Raspberry Pi BLE hardware without requiring BLE 5.0 Extended Advertising, MeshLink uses **Manufacturer Specific Data** with Manufacturer ID `0x4D4C` ("ML").

### Layout of BLE Advertising Packet Data:

```text
[Length: 30 Bytes]
├── [1 Byte] Length = 29
├── [1 Byte] AD Type = 0xFF (Manufacturer Specific Data)
├── [2 Bytes] Company ID = 0x4D4C (MeshLink Manufacturer ID)
└── [28 Bytes] MeshLink Binary SOS Payload
```

---

## 2. Binary Byte Map (28-Byte Payload)

```text
Byte 00: 0x4D                      (Magic Header 'M')
Byte 01: 0x11                      (Version 1, Type 1 = SOS)
Byte 02-05: [Message ID]           (32-bit Big Endian)
Byte 06-09: [Sender ID Hash]       (32-bit Big Endian)
Byte 10-13: [Latitude * 10^6]      (32-bit Signed Big Endian)
Byte 14-17: [Longitude * 10^6]     (32-bit Signed Big Endian)
Byte 18-21: [Timestamp Seconds]    (32-bit Big Endian)
Byte 22: [TTL]                     (8-bit Unsigned)
Byte 23: [Hop Count]               (8-bit Unsigned)
Byte 24: [Battery %]               (8-bit Unsigned)
Byte 25: [Severity]                (8-bit Unsigned)
Byte 26-27: [CRC16-CCITT]          (16-bit Big Endian)
```

---

## 3. CRC16-CCITT Algorithm

- **Polynomial**: `0x1021` ($x^{16} + x^{12} + x^5 + 1$)
- **Initial Value**: `0xFFFF`
- **Input Data**: Bytes 0 through 25 (26 bytes total)
- **Output**: 16-bit Big-Endian unsigned integer placed in Bytes 26 and 27.

---

## 4. Multi-Language Determinism

The binary codec is implemented deterministically across three languages:
1. **Kotlin (Android Native BLE)**: `BlePacketCodec.kt` using `java.nio.ByteBuffer`
2. **Dart (Flutter Mobile App)**: `packet_service.dart` using `ByteData` & `Crc16`
3. **Python (Raspberry Pi Drone Receiver)**: `packet_codec.py` using `struct.pack('>BBIIiiIBBBB')`
