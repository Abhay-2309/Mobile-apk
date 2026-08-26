package com.meshlink.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Binary Packet Codec for MeshLink BLE advertising frames.
 *
 * Supports two SOS frame formats:
 *
 * COMPACT FORMAT (21 bytes) — New, preferred for all transmissions:
 * BYTE 0: Magic Header (0x4D = 'M')
 * BYTE 1: Version (high 4 bits) & Packet Type (low 4 bits, 0x01 = SOS)
 * BYTE 2-5: Message ID (uint32 BE)
 * BYTE 6-9: Sender ID Hash (uint32 BE)
 * BYTE 10-12: Latitude * 10,000 (int24 BE)
 * BYTE 13-15: Longitude * 10,000 (int24 BE)
 * BYTE 16-17: Timestamp Unix Epoch Seconds mod 65536 (uint16 BE)
 * BYTE 18: TTL (high nibble) | Hop Count (low nibble)
 * BYTE 19: Battery (high nibble) | Severity (low nibble)
 * BYTE 20: CRC8-CCITT (polynomial 0x07, init 0x00)
 *
 * LEGACY FORMAT (28 bytes) — Backward compatible decoder only:
 * BYTE 0: Magic Header (0x4D = 'M')
 * BYTE 1: Version (high 4 bits) & Packet Type (low 4 bits, 0x01 = SOS)
 * BYTE 2-5: Message ID (uint32 BE)
 * BYTE 6-9: Sender ID Hash (uint32 BE)
 * BYTE 10-13: Latitude * 1,000,000 (int32 BE)
 * BYTE 14-17: Longitude * 1,000,000 (int32 BE)
 * BYTE 18-21: Timestamp Unix Epoch Seconds (uint32 BE)
 * BYTE 22: TTL (uint8)
 * BYTE 23: Hop Count (uint8)
 * BYTE 24: Battery percentage (uint8)
 * BYTE 25: Severity (uint8)
 * BYTE 26-27: CRC16 Checksum (uint16 BE)
 */
object BlePacketCodec {
    const val MAGIC_HEADER: Byte = 0x4D
    const val PACKET_TYPE_SOS: Byte = 0x01
    const val PROTOCOL_VERSION: Byte = 0x01
    const val PACKET_SIZE = 28
    const val COMPACT_PACKET_SIZE = 21
    const val MANUFACTURER_ID = 0x4D4C // "ML"

    data class SosPacket(
        val messageId: Long, // uint32 stored in Long to avoid overflow
        val senderIdHash: Long,
        val latitude: Double,
        val longitude: Double,
        val timestamp: Long,
        val ttl: Int,
        val hopCount: Int,
        val battery: Int,
        val severity: Int
    )

    // ─── Compact Format (21 bytes) ────────────────────────────────────

    /**
     * Encode a SosPacket into the 21-byte compact BLE advertisement format.
     * All new SOS transmissions and relays MUST use this format.
     */
    fun encodeCompact(packet: SosPacket): ByteArray {
        val buf = ByteArray(COMPACT_PACKET_SIZE)

        // Byte 0: Magic
        buf[0] = MAGIC_HEADER

        // Byte 1: (Version << 4) | Type
        buf[1] = ((PROTOCOL_VERSION.toInt() and 0x0F) shl 4 or
                  (PACKET_TYPE_SOS.toInt() and 0x0F)).toByte()

        // Bytes 2–5: Message ID (uint32 BE)
        val mid = (packet.messageId and 0xFFFFFFFFL).toInt()
        buf[2] = (mid shr 24).toByte()
        buf[3] = (mid shr 16).toByte()
        buf[4] = (mid shr  8).toByte()
        buf[5] = mid.toByte()

        // Bytes 6–9: Sender ID Hash (uint32 BE)
        val sid = (packet.senderIdHash and 0xFFFFFFFFL).toInt()
        buf[6] = (sid shr 24).toByte()
        buf[7] = (sid shr 16).toByte()
        buf[8] = (sid shr  8).toByte()
        buf[9] = sid.toByte()

        // Bytes 10–12: Latitude (int24 BE, ×10000)
        val lat = (packet.latitude * 10_000).toInt()
        buf[10] = (lat shr 16).toByte()
        buf[11] = (lat shr  8).toByte()
        buf[12] = lat.toByte()

        // Bytes 13–15: Longitude (int24 BE, ×10000)
        val lon = (packet.longitude * 10_000).toInt()
        buf[13] = (lon shr 16).toByte()
        buf[14] = (lon shr  8).toByte()
        buf[15] = lon.toByte()

        // Bytes 16–17: Timestamp (uint16 BE, mod 65536)
        val ts = (packet.timestamp % 65536).toInt()
        buf[16] = (ts shr 8).toByte()
        buf[17] = ts.toByte()

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

    /**
     * Decode a 21-byte compact SOS packet.
     * Validates magic, type, and CRC8.
     * Reconstructs full timestamp from uint16 modulo using current time.
     */
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

        // Bytes 13–15: Longitude (int24 BE, sign-extended via Byte.toInt())
        val lonScaled = (bytes[13].toInt() shl 16) or
                        ((bytes[14].toInt() and 0xFF) shl 8) or
                         (bytes[15].toInt() and 0xFF)

        // Bytes 16–17: Timestamp (uint16 BE)
        val timestampLow = ((bytes[16].toInt() and 0xFF) shl 8) or
                            (bytes[17].toInt() and 0xFF)

        // Reconstruct full timestamp from uint16 modulo
        val nowSec = System.currentTimeMillis() / 1000
        val base = nowSec - (nowSec % 65536)
        var fullTimestamp = base + timestampLow
        if (fullTimestamp > nowSec + 300) { // 5-minute future tolerance
            fullTimestamp -= 65536
        }

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
            timestamp = fullTimestamp,
            ttl = ttl,
            hopCount = hopCount,
            battery = battery4 * 100 / 15,
            severity = severity
        )
    }

    // ─── Legacy Format (28 bytes) — Backward Compatible ──────────────

    /**
     * Encode a SosPacket into the legacy 28-byte format.
     * Kept for backward compatibility. New transmissions should use encodeCompact().
     */
    fun encode(packet: SosPacket): ByteArray {
        val buffer = ByteBuffer.allocate(PACKET_SIZE).order(ByteOrder.BIG_ENDIAN)
        
        // Byte 0: Magic Header
        buffer.put(MAGIC_HEADER)

        // Byte 1: (Version << 4) | PacketType
        val verAndType = ((PROTOCOL_VERSION.toInt() and 0x0F) shl 4) or (PACKET_TYPE_SOS.toInt() and 0x0F)
        buffer.put(verAndType.toByte())

        // Bytes 2-5: Message ID (uint32)
        buffer.putInt((packet.messageId and 0xFFFFFFFFL).toInt())

        // Bytes 6-9: Sender ID Hash (uint32)
        buffer.putInt((packet.senderIdHash and 0xFFFFFFFFL).toInt())

        // Bytes 10-13: Latitude (int32)
        buffer.putInt((packet.latitude * 1_000_000).toInt())

        // Bytes 14-17: Longitude (int32)
        buffer.putInt((packet.longitude * 1_000_000).toInt())

        // Bytes 18-21: Timestamp (uint32)
        buffer.putInt((packet.timestamp and 0xFFFFFFFFL).toInt())

        // Byte 22: TTL
        buffer.put(packet.ttl.toByte())

        // Byte 23: Hop Count
        buffer.put(packet.hopCount.toByte())

        // Byte 24: Battery
        buffer.put(packet.battery.toByte())

        // Byte 25: Severity
        buffer.put(packet.severity.toByte())

        // Calculate CRC16 on first 26 bytes
        val bytesForCrc = buffer.array().copyOfRange(0, 26)
        val crc = calculateCrc16(bytesForCrc)

        // Bytes 26-27: CRC16 (uint16)
        buffer.putShort(crc.toShort())

        return buffer.array()
    }

    /**
     * Decode a legacy 28-byte SOS packet.
     * Validates magic, type, and CRC16.
     */
    fun decode(bytes: ByteArray): SosPacket? {
        if (bytes.size < PACKET_SIZE) return null

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)

        val magic = buffer.get()
        if (magic != MAGIC_HEADER) return null

        val verAndType = buffer.get().toInt() and 0xFF
        val type = verAndType and 0x0F
        if (type != PACKET_TYPE_SOS.toInt()) return null

        val messageId = buffer.getInt().toLong() and 0xFFFFFFFFL
        val senderIdHash = buffer.getInt().toLong() and 0xFFFFFFFFL
        val latInt = buffer.getInt()
        val lonInt = buffer.getInt()
        val timestamp = buffer.getInt().toLong() and 0xFFFFFFFFL
        val ttl = buffer.get().toInt() and 0xFF
        val hopCount = buffer.get().toInt() and 0xFF
        val battery = buffer.get().toInt() and 0xFF
        val severity = buffer.get().toInt() and 0xFF

        val expectedCrc = buffer.getShort().toInt() and 0xFFFF
        val actualCrc = calculateCrc16(bytes.copyOfRange(0, 26))

        if (expectedCrc != actualCrc) {
            return null // CRC mismatch
        }

        return SosPacket(
            messageId = messageId,
            senderIdHash = senderIdHash,
            latitude = latInt.toDouble() / 1_000_000.0,
            longitude = lonInt.toDouble() / 1_000_000.0,
            timestamp = timestamp,
            ttl = ttl,
            hopCount = hopCount,
            battery = battery,
            severity = severity
        )
    }

    // ─── Multi-Format Detection ──────────────────────────────────────

    /**
     * Try to decode any SOS packet: legacy 28-byte first (stronger CRC16),
     * then compact 21-byte. Returns null if neither format matches.
     *
     * Packet discrimination:
     * - 6-byte with 4D 50 → presence (caller should check separately)
     * - 21-byte with 4D 11 + valid CRC8 → compact SOS
     * - 28-byte with 4D 11 + valid CRC16 → legacy SOS
     * - Anything else → null
     */
    fun decodeAny(bytes: ByteArray): SosPacket? {
        if (bytes.size < COMPACT_PACKET_SIZE) return null

        // For packets >= 28 bytes, try legacy first (stronger CRC16 validation)
        if (bytes.size >= PACKET_SIZE) {
            val legacy = decode(bytes)
            if (legacy != null) return legacy
        }

        // Try compact decode (21+ bytes, validates CRC8 at byte 20)
        return decodeCompact(bytes)
    }

    /**
     * Check if data looks like a presence packet (6+ bytes starting with 4D 50).
     */
    fun isPresencePacket(bytes: ByteArray): Boolean {
        return bytes.size >= 6 &&
               bytes[0] == 0x4D.toByte() &&
               bytes[1] == 0x50.toByte()
    }

    // ─── CRC Algorithms ──────────────────────────────────────────────

    /**
     * CRC8-CCITT (Polynomial 0x07, Init 0x00)
     * Used by compact 21-byte format.
     */
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

    /**
     * CRC16-CCITT (Polynomial 0x1021, Init 0xFFFF)
     * Used by legacy 28-byte format.
     */
    fun calculateCrc16(data: ByteArray): Int {
        var crc = 0xFFFF
        val polynomial = 0x1021

        for (b in data) {
            for (i in 0..7) {
                val bit = ((b.toInt() shr (7 - i)) and 1) == 1
                val c15 = ((crc shr 15) and 1) == 1
                crc = crc shl 1
                if (c15 xor bit) {
                    crc = crc xor polynomial
                }
            }
        }
        return crc and 0xFFFF
    }
}
