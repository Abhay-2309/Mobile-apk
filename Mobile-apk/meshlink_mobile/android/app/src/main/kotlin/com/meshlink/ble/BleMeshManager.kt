package com.meshlink.ble

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

class BleMeshManager(private val context: Context, private val bluetoothAdapter: BluetoothAdapter?) {

    private val advertiser = BleAdvertiser(bluetoothAdapter)
    private val scanner = BleScanner(bluetoothAdapter)
    
    // In-memory set of seen message IDs to prevent immediate duplicate rebroadcasts
    private val seenMessageIds = ConcurrentHashMap.newKeySet<Long>()
    
    private var isRelayEnabled = false
    private var isVictimModeActive = false
    
    private var onPacketReceivedListener: ((Map<String, Any>) -> Unit)? = null
    private var onLogListener: ((String, String) -> Unit)? = null

    companion object {
        private const val TAG = "BleMeshManager"
    }

    init {
        scanner.setOnPacketDiscoveredListener { rawBytes, rssi, _ ->
            handleDiscoveredPacket(rawBytes, rssi)
        }
    }

    fun setOnPacketReceivedListener(listener: (Map<String, Any>) -> Unit) {
        onPacketReceivedListener = listener
    }

    fun setOnLogListener(listener: (String, String) -> Unit) {
        onLogListener = listener
    }

    fun startMesh(enableRelay: Boolean): Boolean {
        this.isRelayEnabled = enableRelay
        log("MESH", "Starting BLE Mesh (Relay Enabled: $enableRelay)")
        
        val scanOk = scanner.startScanning()
        if (!scanOk) {
            log("ERROR", "Failed to start BLE Scanner")
        }
        return scanOk
    }

    fun stopMesh() {
        log("MESH", "Stopping BLE Mesh")
        scanner.stopScanning()
        advertiser.stopAdvertising()
        isVictimModeActive = false
    }

    fun broadcastSos(
        messageId: Long,
        senderIdHash: Long,
        lat: Double,
        lon: Double,
        timestamp: Long,
        ttl: Int = 5,
        hopCount: Int = 0,
        battery: Int,
        severity: Int
    ): Boolean {
        isVictimModeActive = true
        // Mark own message as seen so we don't process our own broadcast if scanner picks it up
        seenMessageIds.add(messageId)

        val packet = BlePacketCodec.SosPacket(
            messageId = messageId,
            senderIdHash = senderIdHash,
            latitude = lat,
            longitude = lon,
            timestamp = timestamp,
            ttl = ttl,
            hopCount = hopCount,
            battery = battery,
            severity = severity
        )

        // All new transmissions use compact 21-byte format
        val encoded = BlePacketCodec.encodeCompact(packet)
        log("SOS", "Creating compact SOS packet (${encoded.size} bytes, MessageId: 0x${messageId.toString(16).uppercase()}), TTL=$ttl, HOPS=$hopCount")

        val success = advertiser.startAdvertising(encoded)
        if (success) {
            log("BLE", "Started advertising compact SOS packet (MessageId: 0x${messageId.toString(16).uppercase()})")
        } else {
            log("ERROR", "Failed to start advertising SOS packet")
        }
        return success
    }

    fun stopSosBroadcast() {
        log("SOS", "Stopping SOS broadcast")
        advertiser.stopAdvertising()
        isVictimModeActive = false
    }

    private fun handleDiscoveredPacket(rawBytes: ByteArray, rssi: Int) {
        // Use decodeAny to support both compact (21-byte) and legacy (28-byte) packets
        val packet = BlePacketCodec.decodeAny(rawBytes)
        if (packet == null) {
            // Could be a presence packet or noise — ignore silently
            if (!BlePacketCodec.isPresencePacket(rawBytes)) {
                log("VALIDATION", "Rejected malformed or corrupted BLE packet")
            }
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

        // Relay Algorithm Execution
        if (!isRelayEnabled) {
            log("RELAY", "Relay mode disabled on this device. Not forwarding.")
            return
        }

        if (packet.ttl <= 0) {
            log("RELAY", "Packet $hexId TTL reached 0. Stopping relay chain.")
            return
        }

        // Create Relay Packet: Decrement TTL, Increment Hop Count
        // Message ID and Sender ID preserved — never changed during relay
        val newTtl = packet.ttl - 1
        val newHops = packet.hopCount + 1

        val relayPacket = packet.copy(
            ttl = newTtl,
            hopCount = newHops
        )

        log("RELAY", "Packet $hexId: TTL ${packet.ttl} -> $newTtl, HOPS ${packet.hopCount} -> $newHops")

        // All relays use compact 21-byte format
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
            }, 15000)
        }
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
        onPacketReceivedListener?.invoke(map)
    }

    private fun log(tag: String, message: String) {
        Log.d(TAG, "[$tag] $message")
        onLogListener?.invoke(tag, message)
    }

    fun isScanning(): Boolean = scanner.isScanning()
    fun isAdvertising(): Boolean = advertiser.isAdvertising()
}
