package com.meshlink.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Binary Packet Codec for MeshLink BLE advertising frames.
 * Layout (28 bytes total):
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

    /**
     * CRC16-CCITT (Polynomial 0x1021, Init 0xFFFF)
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
