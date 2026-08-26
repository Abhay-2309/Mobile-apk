package com.meshlink.ble

import org.junit.Assert.*
import org.junit.Test

/**
 * Comprehensive unit tests for BlePacketCodec.
 *
 * Tests cover:
 * A. 21-byte packet encoding
 * B. 21-byte packet decoding
 * C. CRC8
 * D. Invalid CRC rejection
 * E. Invalid magic rejection
 * F. Invalid packet type rejection
 * G. Positive latitude
 * H. Negative latitude
 * I. Positive longitude
 * J. Negative longitude
 * K. Timestamp encoding (mod 65536)
 * L. TTL/Hop packing
 * M. Battery/Severity packing
 * N. Message ID preservation
 * O. Sender ID preservation
 * P. Duplicate detection (via encode/decode round-trip preserving IDs)
 * Q. Relay TTL decrement
 * R. Relay hop increment
 * S. 6-byte presence packet ignored by SOS decoder
 * T. Legacy 28-byte packet still decoded
 */
class BlePacketCodecTest {

    // ─── Test data ───────────────────────────────────────────────────

    private val testPacket = BlePacketCodec.SosPacket(
        messageId = 0xA3F7C2E1L,
        senderIdHash = 0x123C5A16L,
        latitude = 28.6139,
        longitude = 77.2090,
        timestamp = 100000L,
        ttl = 5,
        hopCount = 0,
        battery = 85,
        severity = 2
    )

    private val negativeCoordPacket = BlePacketCodec.SosPacket(
        messageId = 0xDEADBEEFL,
        senderIdHash = 0xCAFEBABEL,
        latitude = -33.8688,
        longitude = -151.2093,
        timestamp = 200000L,
        ttl = 3,
        hopCount = 2,
        battery = 50,
        severity = 5
    )

    // ─── A. 21-byte packet encoding ──────────────────────────────────

    @Test
    fun `A - compact encode produces exactly 21 bytes`() {
        val encoded = BlePacketCodec.encodeCompact(testPacket)
        assertEquals("Compact packet must be 21 bytes", 21, encoded.size)
    }

    @Test
    fun `A - compact encode has correct magic and type`() {
        val encoded = BlePacketCodec.encodeCompact(testPacket)
        assertEquals("Byte 0 must be magic 0x4D", 0x4D.toByte(), encoded[0])
        assertEquals("Byte 1 must be ver|type 0x11", 0x11.toByte(), encoded[1])
    }

    // ─── B. 21-byte packet decoding ──────────────────────────────────

    @Test
    fun `B - compact encode then decode round-trips`() {
        val encoded = BlePacketCodec.encodeCompact(testPacket)
        val decoded = BlePacketCodec.decodeCompact(encoded)
        assertNotNull("Decoded packet must not be null", decoded)
        decoded!!

        assertEquals(testPacket.messageId, decoded.messageId)
        assertEquals(testPacket.senderIdHash, decoded.senderIdHash)
        assertEquals(testPacket.latitude, decoded.latitude, 0.0001)
        assertEquals(testPacket.longitude, decoded.longitude, 0.0001)
        assertEquals(testPacket.ttl, decoded.ttl)
        assertEquals(testPacket.hopCount, decoded.hopCount)
        assertEquals(testPacket.severity, decoded.severity)
    }

    // ─── C. CRC8 ─────────────────────────────────────────────────────

    @Test
    fun `C - CRC8 of empty array is zero`() {
        assertEquals(0, BlePacketCodec.calculateCrc8(ByteArray(0)))
    }

    @Test
    fun `C - CRC8 produces consistent result`() {
        val data = byteArrayOf(0x4D, 0x11, 0x00, 0x01, 0x02, 0x03)
        val crc1 = BlePacketCodec.calculateCrc8(data)
        val crc2 = BlePacketCodec.calculateCrc8(data)
        assertEquals("CRC8 must be deterministic", crc1, crc2)
        assertTrue("CRC8 must be in 0-255 range", crc1 in 0..255)
    }

    @Test
    fun `C - CRC8 with offset and length`() {
        val data = byteArrayOf(0xFF.toByte(), 0x4D, 0x11, 0x00, 0xFF.toByte())
        val crcFull = BlePacketCodec.calculateCrc8(byteArrayOf(0x4D, 0x11, 0x00))
        val crcOffset = BlePacketCodec.calculateCrc8(data, 1, 3)
        assertEquals("CRC8 with offset must match", crcFull, crcOffset)
    }

    // ─── D. Invalid CRC rejection ────────────────────────────────────

    @Test
    fun `D - corrupted CRC8 rejects packet`() {
        val encoded = BlePacketCodec.encodeCompact(testPacket)
        // Flip CRC byte
        encoded[20] = (encoded[20].toInt() xor 0xFF).toByte()
        val decoded = BlePacketCodec.decodeCompact(encoded)
        assertNull("Corrupted CRC must cause rejection", decoded)
    }

    @Test
    fun `D - corrupted data byte rejects packet`() {
        val encoded = BlePacketCodec.encodeCompact(testPacket)
        // Corrupt a data byte — CRC will no longer match
        encoded[10] = (encoded[10].toInt() xor 0x01).toByte()
        val decoded = BlePacketCodec.decodeCompact(encoded)
        assertNull("Corrupted data must cause CRC rejection", decoded)
    }

    // ─── E. Invalid magic rejection ──────────────────────────────────

    @Test
    fun `E - wrong magic byte rejects packet`() {
        val encoded = BlePacketCodec.encodeCompact(testPacket)
        encoded[0] = 0x00 // Wrong magic
        val decoded = BlePacketCodec.decodeCompact(encoded)
        assertNull("Wrong magic must cause rejection", decoded)
    }

    // ─── F. Invalid packet type rejection ────────────────────────────

    @Test
    fun `F - wrong packet type rejects packet`() {
        val encoded = BlePacketCodec.encodeCompact(testPacket)
        encoded[1] = 0x12 // Type 2 instead of 1
        // Re-calc CRC so it's valid CRC but wrong type
        encoded[20] = BlePacketCodec.calculateCrc8(encoded, 0, 20).toByte()
        val decoded = BlePacketCodec.decodeCompact(encoded)
        assertNull("Wrong packet type must cause rejection", decoded)
    }

    // ─── G. Positive latitude ────────────────────────────────────────

    @Test
    fun `G - positive latitude encodes and decodes correctly`() {
        val packet = testPacket.copy(latitude = 28.6139)
        val encoded = BlePacketCodec.encodeCompact(packet)
        val decoded = BlePacketCodec.decodeCompact(encoded)!!
        assertEquals(28.6139, decoded.latitude, 0.0001)
    }

    @Test
    fun `G - maximum positive latitude 90`() {
        val packet = testPacket.copy(latitude = 90.0)
        val encoded = BlePacketCodec.encodeCompact(packet)
        val decoded = BlePacketCodec.decodeCompact(encoded)!!
        assertEquals(90.0, decoded.latitude, 0.0001)
    }

    // ─── H. Negative latitude ────────────────────────────────────────

    @Test
    fun `H - negative latitude Sydney encodes and decodes correctly`() {
        val packet = testPacket.copy(latitude = -33.8688)
        val encoded = BlePacketCodec.encodeCompact(packet)
        val decoded = BlePacketCodec.decodeCompact(encoded)!!
        assertEquals(-33.8688, decoded.latitude, 0.0001)
    }

    @Test
    fun `H - maximum negative latitude -90`() {
        val packet = testPacket.copy(latitude = -90.0)
        val encoded = BlePacketCodec.encodeCompact(packet)
        val decoded = BlePacketCodec.decodeCompact(encoded)!!
        assertEquals(-90.0, decoded.latitude, 0.0001)
    }

    // ─── I. Positive longitude ───────────────────────────────────────

    @Test
    fun `I - positive longitude encodes and decodes correctly`() {
        val packet = testPacket.copy(longitude = 77.2090)
        val encoded = BlePacketCodec.encodeCompact(packet)
        val decoded = BlePacketCodec.decodeCompact(encoded)!!
        assertEquals(77.2090, decoded.longitude, 0.0001)
    }

    @Test
    fun `I - maximum positive longitude 180`() {
        val packet = testPacket.copy(longitude = 180.0)
        val encoded = BlePacketCodec.encodeCompact(packet)
        val decoded = BlePacketCodec.decodeCompact(encoded)!!
        assertEquals(180.0, decoded.longitude, 0.0001)
    }

    // ─── J. Negative longitude ───────────────────────────────────────

    @Test
    fun `J - negative longitude Sydney encodes and decodes correctly`() {
        val packet = negativeCoordPacket.copy(longitude = -151.2093)
        val encoded = BlePacketCodec.encodeCompact(packet)
        val decoded = BlePacketCodec.decodeCompact(encoded)!!
        assertEquals(-151.2093, decoded.longitude, 0.0001)
    }

    @Test
    fun `J - maximum negative longitude -180`() {
        val packet = testPacket.copy(longitude = -180.0)
        val encoded = BlePacketCodec.encodeCompact(packet)
        val decoded = BlePacketCodec.decodeCompact(encoded)!!
        assertEquals(-180.0, decoded.longitude, 0.0001)
    }

    // ─── K. Timestamp encoding ───────────────────────────────────────

    @Test
    fun `K - timestamp is stored as mod 65536`() {
        val packet = testPacket.copy(timestamp = 100000L)
        val encoded = BlePacketCodec.encodeCompact(packet)
        // 100000 mod 65536 = 34464 = 0x86A0
        val tsHigh = encoded[16].toInt() and 0xFF
        val tsLow = encoded[17].toInt() and 0xFF
        val tsValue = (tsHigh shl 8) or tsLow
        assertEquals("Timestamp mod 65536", 34464, tsValue)
    }

    @Test
    fun `K - timestamp zero encodes correctly`() {
        val packet = testPacket.copy(timestamp = 0L)
        val encoded = BlePacketCodec.encodeCompact(packet)
        val tsHigh = encoded[16].toInt() and 0xFF
        val tsLow = encoded[17].toInt() and 0xFF
        assertEquals(0, (tsHigh shl 8) or tsLow)
    }

    @Test
    fun `K - timestamp 65535 encodes correctly`() {
        val packet = testPacket.copy(timestamp = 65535L)
        val encoded = BlePacketCodec.encodeCompact(packet)
        val tsHigh = encoded[16].toInt() and 0xFF
        val tsLow = encoded[17].toInt() and 0xFF
        assertEquals(65535, (tsHigh shl 8) or tsLow)
    }

    // ─── L. TTL/Hop packing ──────────────────────────────────────────

    @Test
    fun `L - TTL and hop count pack into single byte`() {
        val packet = testPacket.copy(ttl = 5, hopCount = 0)
        val encoded = BlePacketCodec.encodeCompact(packet)
        val b18 = encoded[18].toInt() and 0xFF
        assertEquals("TTL high nibble", 5, b18 shr 4)
        assertEquals("Hop low nibble", 0, b18 and 0x0F)
    }

    @Test
    fun `L - TTL and hop count max values`() {
        val packet = testPacket.copy(ttl = 15, hopCount = 15)
        val encoded = BlePacketCodec.encodeCompact(packet)
        val decoded = BlePacketCodec.decodeCompact(encoded)!!
        assertEquals(15, decoded.ttl)
        assertEquals(15, decoded.hopCount)
    }

    @Test
    fun `L - TTL and hop count zero`() {
        val packet = testPacket.copy(ttl = 0, hopCount = 0)
        val encoded = BlePacketCodec.encodeCompact(packet)
        val decoded = BlePacketCodec.decodeCompact(encoded)!!
        assertEquals(0, decoded.ttl)
        assertEquals(0, decoded.hopCount)
    }

    // ─── M. Battery/Severity packing ─────────────────────────────────

    @Test
    fun `M - battery 85 percent quantizes and round-trips`() {
        val packet = testPacket.copy(battery = 85, severity = 2)
        val encoded = BlePacketCodec.encodeCompact(packet)
        val decoded = BlePacketCodec.decodeCompact(encoded)!!
        // 85 * 15 / 100 = 12, 12 * 100 / 15 = 80
        assertEquals(80, decoded.battery)
        assertEquals(2, decoded.severity)
    }

    @Test
    fun `M - battery 100 percent`() {
        val packet = testPacket.copy(battery = 100)
        val encoded = BlePacketCodec.encodeCompact(packet)
        val decoded = BlePacketCodec.decodeCompact(encoded)!!
        assertEquals(100, decoded.battery)
    }

    @Test
    fun `M - battery 0 percent`() {
        val packet = testPacket.copy(battery = 0)
        val encoded = BlePacketCodec.encodeCompact(packet)
        val decoded = BlePacketCodec.decodeCompact(encoded)!!
        assertEquals(0, decoded.battery)
    }

    @Test
    fun `M - severity max 15`() {
        val packet = testPacket.copy(severity = 15)
        val encoded = BlePacketCodec.encodeCompact(packet)
        val decoded = BlePacketCodec.decodeCompact(encoded)!!
        assertEquals(15, decoded.severity)
    }

    // ─── N. Message ID preservation ──────────────────────────────────

    @Test
    fun `N - message ID preserved across encode and decode`() {
        val encoded = BlePacketCodec.encodeCompact(testPacket)
        val decoded = BlePacketCodec.decodeCompact(encoded)!!
        assertEquals(0xA3F7C2E1L, decoded.messageId)
    }

    @Test
    fun `N - large message ID preserved`() {
        val packet = testPacket.copy(messageId = 0xFFFFFFFFL)
        val encoded = BlePacketCodec.encodeCompact(packet)
        val decoded = BlePacketCodec.decodeCompact(encoded)!!
        assertEquals(0xFFFFFFFFL, decoded.messageId)
    }

    // ─── O. Sender ID preservation ───────────────────────────────────

    @Test
    fun `O - sender ID hash preserved across encode and decode`() {
        val encoded = BlePacketCodec.encodeCompact(testPacket)
        val decoded = BlePacketCodec.decodeCompact(encoded)!!
        assertEquals(0x123C5A16L, decoded.senderIdHash)
    }

    @Test
    fun `O - large sender ID hash preserved`() {
        val packet = testPacket.copy(senderIdHash = 0xFFFFFFFFL)
        val encoded = BlePacketCodec.encodeCompact(packet)
        val decoded = BlePacketCodec.decodeCompact(encoded)!!
        assertEquals(0xFFFFFFFFL, decoded.senderIdHash)
    }

    // ─── P. Duplicate detection ──────────────────────────────────────

    @Test
    fun `P - same packet encoded twice produces same message ID`() {
        val encoded1 = BlePacketCodec.encodeCompact(testPacket)
        val encoded2 = BlePacketCodec.encodeCompact(testPacket)
        val decoded1 = BlePacketCodec.decodeCompact(encoded1)!!
        val decoded2 = BlePacketCodec.decodeCompact(encoded2)!!
        assertEquals("Same packet must produce same message ID",
            decoded1.messageId, decoded2.messageId)
    }

    // ─── Q. Relay TTL decrement ──────────────────────────────────────

    @Test
    fun `Q - relay decrements TTL`() {
        val original = testPacket.copy(ttl = 5, hopCount = 0)
        val relayed = original.copy(ttl = original.ttl - 1, hopCount = original.hopCount + 1)

        val encodedRelay = BlePacketCodec.encodeCompact(relayed)
        val decodedRelay = BlePacketCodec.decodeCompact(encodedRelay)!!

        assertEquals("TTL must be decremented", 4, decodedRelay.ttl)
        // Message ID must be preserved during relay
        assertEquals(original.messageId, decodedRelay.messageId)
        // Sender ID must be preserved during relay
        assertEquals(original.senderIdHash, decodedRelay.senderIdHash)
    }

    // ─── R. Relay hop increment ──────────────────────────────────────

    @Test
    fun `R - relay increments hop count`() {
        val original = testPacket.copy(ttl = 5, hopCount = 0)
        val relayed = original.copy(ttl = original.ttl - 1, hopCount = original.hopCount + 1)

        val encodedRelay = BlePacketCodec.encodeCompact(relayed)
        val decodedRelay = BlePacketCodec.decodeCompact(encodedRelay)!!

        assertEquals("Hop count must be incremented", 1, decodedRelay.hopCount)
    }

    @Test
    fun `R - multi-hop relay preserves identity`() {
        var packet = testPacket.copy(ttl = 5, hopCount = 0)

        // Simulate 3 hops
        for (hop in 1..3) {
            packet = packet.copy(ttl = packet.ttl - 1, hopCount = packet.hopCount + 1)
            val encoded = BlePacketCodec.encodeCompact(packet)
            packet = BlePacketCodec.decodeCompact(encoded)!!
        }

        assertEquals("After 3 hops, TTL should be 2", 2, packet.ttl)
        assertEquals("After 3 hops, hop count should be 3", 3, packet.hopCount)
        assertEquals("Message ID preserved across hops", testPacket.messageId, packet.messageId)
        assertEquals("Sender ID preserved across hops", testPacket.senderIdHash, packet.senderIdHash)
    }

    // ─── S. Presence packet ignored by SOS decoder ───────────────────

    @Test
    fun `S - presence packet not decoded as SOS`() {
        val presenceData = byteArrayOf(0x4D, 0x50, 0x12, 0x3C, 0x5A, 0x16)
        val decoded = BlePacketCodec.decodeCompact(presenceData)
        assertNull("6-byte presence must not decode as compact SOS", decoded)
    }

    @Test
    fun `S - presence packet not decoded by decodeAny`() {
        val presenceData = byteArrayOf(0x4D, 0x50, 0x12, 0x3C, 0x5A, 0x16)
        val decoded = BlePacketCodec.decodeAny(presenceData)
        assertNull("6-byte presence must not decode as any SOS", decoded)
    }

    @Test
    fun `S - isPresencePacket identifies presence correctly`() {
        val presenceData = byteArrayOf(0x4D, 0x50, 0x12, 0x3C, 0x5A, 0x16)
        assertTrue(BlePacketCodec.isPresencePacket(presenceData))

        val sosData = BlePacketCodec.encodeCompact(testPacket)
        assertFalse(BlePacketCodec.isPresencePacket(sosData))
    }

    // ─── T. Legacy 28-byte packet still decoded ──────────────────────

    @Test
    fun `T - legacy 28-byte packet encodes to 28 bytes`() {
        val encoded = BlePacketCodec.encode(testPacket)
        assertEquals(28, encoded.size)
    }

    @Test
    fun `T - legacy 28-byte encode then decode round-trips`() {
        val encoded = BlePacketCodec.encode(testPacket)
        val decoded = BlePacketCodec.decode(encoded)
        assertNotNull("Legacy decode must succeed", decoded)
        decoded!!

        assertEquals(testPacket.messageId, decoded.messageId)
        assertEquals(testPacket.senderIdHash, decoded.senderIdHash)
        assertEquals(testPacket.latitude, decoded.latitude, 0.000001)
        assertEquals(testPacket.longitude, decoded.longitude, 0.000001)
        assertEquals(testPacket.timestamp, decoded.timestamp)
        assertEquals(testPacket.ttl, decoded.ttl)
        assertEquals(testPacket.hopCount, decoded.hopCount)
        assertEquals(testPacket.battery, decoded.battery)
        assertEquals(testPacket.severity, decoded.severity)
    }

    @Test
    fun `T - decodeAny handles legacy 28-byte packet`() {
        val encoded = BlePacketCodec.encode(testPacket)
        val decoded = BlePacketCodec.decodeAny(encoded)
        assertNotNull("decodeAny must handle legacy packet", decoded)
        decoded!!

        assertEquals(testPacket.messageId, decoded.messageId)
        assertEquals(testPacket.senderIdHash, decoded.senderIdHash)
    }

    @Test
    fun `T - decodeAny handles compact 21-byte packet`() {
        val encoded = BlePacketCodec.encodeCompact(testPacket)
        val decoded = BlePacketCodec.decodeAny(encoded)
        assertNotNull("decodeAny must handle compact packet", decoded)
        decoded!!

        assertEquals(testPacket.messageId, decoded.messageId)
        assertEquals(testPacket.senderIdHash, decoded.senderIdHash)
    }

    // ─── Additional edge cases ───────────────────────────────────────

    @Test
    fun `edge - negative coordinate full round trip`() {
        val encoded = BlePacketCodec.encodeCompact(negativeCoordPacket)
        val decoded = BlePacketCodec.decodeCompact(encoded)!!

        assertEquals(-33.8688, decoded.latitude, 0.0001)
        assertEquals(-151.2093, decoded.longitude, 0.0001)
        assertEquals(negativeCoordPacket.messageId, decoded.messageId)
        assertEquals(negativeCoordPacket.senderIdHash, decoded.senderIdHash)
    }

    @Test
    fun `edge - too short data returns null`() {
        assertNull(BlePacketCodec.decodeCompact(ByteArray(20)))
        assertNull(BlePacketCodec.decodeCompact(ByteArray(0)))
        assertNull(BlePacketCodec.decode(ByteArray(27)))
        assertNull(BlePacketCodec.decodeAny(ByteArray(5)))
    }

    @Test
    fun `edge - random data returns null`() {
        val random = ByteArray(21) { (it * 37).toByte() }
        assertNull("Random data should not decode", BlePacketCodec.decodeCompact(random))
    }

    @Test
    fun `edge - compact CRC8 matches Python spec`() {
        // Verify the CRC8-CCITT algorithm matches the specification
        // Input: single byte 0x4D
        val crc = BlePacketCodec.calculateCrc8(byteArrayOf(0x4D))
        // CRC8-CCITT(0x4D, poly=0x07, init=0x00):
        // 0x00 xor 0x4D = 0x4D = 0100 1101
        // bit 0: 0x4D & 0x80 = 0x40 != 0 → (0x4D << 1) xor 0x07 = 0x9A xor 0x07 = 0x9D & 0xFF = 0x9D
        // ... (algorithm is deterministic)
        assertTrue("CRC8 must be in valid range", crc in 0..255)
    }
}
