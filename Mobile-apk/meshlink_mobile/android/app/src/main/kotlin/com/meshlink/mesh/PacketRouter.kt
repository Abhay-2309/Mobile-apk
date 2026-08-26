package com.meshlink.mesh

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.meshlink.ble.BleAdvertiser
import com.meshlink.ble.BlePacketCodec
import java.util.concurrent.ConcurrentHashMap

/**
 * Handles incoming MeshLink SOS packet processing:
 * validate → deduplicate → notify → relay.
 *
 * Extracted from BleMeshManager.handleDiscoveredPacket() to separate
 * packet routing from BLE transport management.
 *
 * The relay logic is EXACTLY the same as the original implementation:
 * - TTL decremented by 1
 * - Hop count incremented by 1
 * - Packet re-encoded in COMPACT 21-byte format and advertised for 15s burst
 * - Duplicates suppressed by messageId
 * - CRC validated by BlePacketCodec.decodeAny()
 *
 * Supports both compact (21-byte) and legacy (28-byte) incoming packets.
 * All outgoing relays use the compact 21-byte format.
 */
class PacketRouter(private val advertiser: BleAdvertiser) {

    companion object {
        private const val TAG = "PacketRouter"
        private const val RELAY_BURST_MS = 15_000L
    }

    private val seenMessageIds = ConcurrentHashMap.newKeySet<Long>()

    @Volatile
    var isVictimModeActive = false

    var onPacketReceived: ((Map<String, Any>) -> Unit)? = null
    var onLog: ((String, String) -> Unit)? = null

    /**
     * Process a raw manufacturer-data byte array discovered by the BLE scanner.
     * Accepts both compact (21-byte) and legacy (28-byte) SOS packets.
     */
    fun processPacket(rawBytes: ByteArray, rssi: Int) {
        val packet = BlePacketCodec.decodeAny(rawBytes)
        if (packet == null) {
            log("VALIDATION", "Rejected malformed or corrupted BLE packet")
            return
        }

        val hexId = "0x${packet.messageId.toString(16).uppercase()}"
        log("BLE", "Received packet $hexId | TTL: ${packet.ttl} | HOPS: ${packet.hopCount} | RSSI: $rssi dBm")

        // Check Duplicate
        if (seenMessageIds.contains(packet.messageId)) {
            log("DUPLICATE", "Message $hexId already processed. Discarding.")
            notifyFlutter(packet, rssi, "DUPLICATE_DISCARDED")
            return
        }

        // Add to seen set
        seenMessageIds.add(packet.messageId)
        log("STORE", "Saved new message $hexId locally")

        // Notify Flutter layer of valid incoming packet
        notifyFlutter(packet, rssi, "PROCESSED")

        // Relay — always enabled in production
        if (packet.ttl <= 0) {
            log("RELAY", "Packet $hexId TTL reached 0. Stopping relay chain.")
            return
        }

        // Create Relay Packet: Decrement TTL, Increment Hop Count
        // Message ID and Sender ID are preserved — never changed during relay
        val newTtl = packet.ttl - 1
        val newHops = packet.hopCount + 1

        val relayPacket = packet.copy(
            ttl = newTtl,
            hopCount = newHops
        )

        log("RELAY", "Packet $hexId: TTL ${packet.ttl} -> $newTtl, HOPS ${packet.hopCount} -> $newHops")

        // All relays use the compact 21-byte format
        val encodedRelay = BlePacketCodec.encodeCompact(relayPacket)

        // Schedule burst advertising of forwarded packet
        Handler(Looper.getMainLooper()).post {
            log("BLE", "Forwarding packet $hexId over BLE advertising (compact ${encodedRelay.size} bytes)...")
            advertiser.startAdvertising(encodedRelay)

            // Re-advertise for 15 seconds burst, then resume previous mode if active
            Handler(Looper.getMainLooper()).postDelayed({
                if (!isVictimModeActive) {
                    advertiser.stopAdvertising()
                }
            }, RELAY_BURST_MS)
        }
    }

    /**
     * Mark a messageId as seen (used when broadcasting own SOS to prevent self-relay).
     */
    fun markSeen(messageId: Long) {
        seenMessageIds.add(messageId)
    }

    private fun notifyFlutter(packet: BlePacketCodec.SosPacket, rssi: Int, status: String) {
        val map = mapOf<String, Any>(
            "messageId" to packet.messageId,
            "senderIdHash" to packet.senderIdHash,
            "latitude" to packet.latitude,
            "longitude" to packet.longitude,
            "timestamp" to packet.timestamp,
            "ttl" to packet.ttl,
            "hopCount" to packet.hopCount,
            "battery" to packet.battery,
            "severity" to packet.severity,
            "rssi" to rssi,
            "status" to status
        )
        onPacketReceived?.invoke(map)
    }

    private fun log(tag: String, message: String) {
        Log.d(TAG, "[$tag] $message")
        onLog?.invoke(tag, message)
    }
}
