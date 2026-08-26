package com.meshlink.mesh

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.meshlink.ble.AdvertiseResult
import com.meshlink.ble.BleAdvertiser
import com.meshlink.ble.BlePacketCodec
import com.meshlink.ble.BleScanner
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest

/**
 * Central mesh engine owned by the foreground service.
 *
 * Coordinates:
 * - BLE scanner (existing, unchanged)
 * - BLE advertiser (existing, unchanged)
 * - Peer registry (new — tracks nearby MeshLink devices)
 * - Packet router (extracted from BleMeshManager — dedup/relay)
 * - Presence advertising (new — lightweight "I am MeshLink" beacon)
 * - BLE recovery with exponential backoff
 * - Bluetooth state monitoring
 *
 * Uses Kotlin coroutines and StateFlow for non-blocking operation.
 */
class MeshRuntime(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter?
) {

    companion object {
        private const val TAG = "MeshRuntime"

        // Peer cleanup interval
        private const val PEER_CLEANUP_INTERVAL_MS = 15_000L

        // Scanner retry backoff
        private const val INITIAL_RETRY_DELAY_MS = 2_000L
        private const val MAX_RETRY_DELAY_MS = 30_000L
        private const val MAX_RETRIES = 10
    }

    val scanner = BleScanner(bluetoothAdapter)
    val advertiser = BleAdvertiser(bluetoothAdapter)
    val peerRegistry = PeerRegistry()
    val packetRouter = PacketRouter(advertiser)

    private val localNodeId: Long
    @Volatile private var activeSosSenderIdHash: Long? = null

    private val _meshState = MutableStateFlow(MeshState())
    val meshState: StateFlow<MeshState> = _meshState

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Presence advertiser (separate from SOS advertiser)
    private var presenceCallback: AdvertiseCallback? = null
    @Volatile
    private var isPresenceAdvertising = false

    // Bluetooth state receiver
    private var btReceiver: BroadcastReceiver? = null

    // State change listener for Flutter bridge
    var onStateChanged: ((MeshState) -> Unit)? = null
    var onPacketReceived: ((Map<String, Any>) -> Unit)? = null
    var onLog: ((String, String) -> Unit)? = null

    init {
        val prefs = context.getSharedPreferences("meshlink_prefs", Context.MODE_PRIVATE)
        var savedId = prefs.getLong("local_node_id", 0L)
        if (savedId == 0L) {
            savedId = java.util.Random().nextInt(0x7FFFFFFF).toLong()
            prefs.edit().putLong("local_node_id", savedId).apply()
        }
        localNodeId = savedId

        // Wire scanner to process discovered packets and track peers.
        // Supports compact (21-byte), legacy (28-byte) SOS, and presence (6-byte) packets.
        scanner.setOnPacketDiscoveredListener { rawBytes, rssi, _ ->
            var peerId: String? = null

            // Try SOS decode (compact 21-byte or legacy 28-byte)
            if (rawBytes.size >= BlePacketCodec.COMPACT_PACKET_SIZE) {
                val packet = BlePacketCodec.decodeAny(rawBytes)
                if (packet != null) {
                    val senderId = packet.senderIdHash
                    if (senderId == localNodeId || senderId == activeSosSenderIdHash) {
                        return@setOnPacketDiscoveredListener // Ignore self
                    }
                    peerId = senderId.toString()
                    packetRouter.processPacket(rawBytes, rssi)
                }
            } else if (rawBytes.size >= 6 && rawBytes[0] == 0x4D.toByte() && rawBytes[1] == 0x50.toByte()) {
                // Presence packet
                val id = ((rawBytes[2].toInt() and 0xFF) shl 24) or
                         ((rawBytes[3].toInt() and 0xFF) shl 16) or
                         ((rawBytes[4].toInt() and 0xFF) shl 8) or
                         (rawBytes[5].toInt() and 0xFF)
                val nodeId = id.toLong() and 0xFFFFFFFFL
                if (nodeId == localNodeId || nodeId == activeSosSenderIdHash) {
                    return@setOnPacketDiscoveredListener // Ignore self
                }
                peerId = nodeId.toString()
            }

            if (peerId != null) {
                peerRegistry.onPeerSeen(peerId, rssi)
            }
        }

        // Wire packet router callbacks
        packetRouter.onPacketReceived = { packetMap ->
            onPacketReceived?.invoke(packetMap)
        }
        packetRouter.onLog = { tag, message ->
            onLog?.invoke(tag, message)
        }
    }

    /**
     * Start the mesh: scanner, presence advertiser, peer cleanup, BT monitoring.
     */
    fun start(): Boolean {
        Log.d(TAG, "Starting MeshRuntime")

        val btEnabled = bluetoothAdapter?.isEnabled ?: false
        if (!btEnabled) {
            updateState { copy(serviceRunning = true, bluetoothEnabled = false, lastError = "Bluetooth disabled") }
            registerBluetoothReceiver()
            return false
        }

        updateState { copy(serviceRunning = true, bluetoothEnabled = true) }

        // Start scanner with retry
        startScannerWithRetry()

        // Start presence advertising
        startPresenceAdvertising()

        // Launch periodic peer cleanup
        scope.launch {
            while (isActive) {
                delay(PEER_CLEANUP_INTERVAL_MS)
                peerRegistry.evictStale()
                updatePeerCount()
            }
        }

        // Launch peer count observer
        scope.launch {
            peerRegistry.peerCount.collectLatest { count ->
                updateState { copy(nearbyPeerCount = count) }
            }
        }

        // Register Bluetooth state receiver
        registerBluetoothReceiver()

        return true
    }

    /**
     * Stop the mesh cleanly.
     */
    fun stop() {
        Log.d(TAG, "Stopping MeshRuntime")
        scope.coroutineContext.cancelChildren()
        scanner.stopScanning()
        advertiser.stopAdvertising()
        stopPresenceAdvertising()
        peerRegistry.clear()
        unregisterBluetoothReceiver()
        updateState { MeshState() }
    }

    /**
     * Broadcast own SOS using the compact 21-byte format.
     *
     * This is a suspend function that waits for the actual Android
     * AdvertiseCallback before reporting success or failure.
     * sosActive is set to true ONLY after onStartSuccess().
     *
     * The BLE scanner continues running — this phone is simultaneously
     * VICTIM + RELAY NODE.
     */
    suspend fun broadcastSos(
        messageId: Long,
        senderIdHash: Long,
        lat: Double,
        lon: Double,
        timestamp: Long,
        ttl: Int,
        hopCount: Int,
        battery: Int,
        severity: Int
    ): Boolean {
        activeSosSenderIdHash = senderIdHash
        packetRouter.isVictimModeActive = true
        packetRouter.markSeen(messageId)

        // Stop presence while SOS is active (SOS ad already identifies us)
        withContext(Dispatchers.Main) {
            stopPresenceAdvertising()
        }

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

        // All new SOS transmissions use compact 21-byte format
        val encoded = BlePacketCodec.encodeCompact(packet)
        Log.d(TAG, "[SOS] encoded compact packet length = ${encoded.size}")
        Log.d(TAG, "[SOS] Broadcasting SOS: 0x${messageId.toString(16).uppercase()}")
        Log.d(TAG, "[SOS] packet hex: ${encoded.joinToString(" ") { "%02X".format(it) }}")

        // Await the actual BLE advertising result — do NOT report success until
        // Android confirms the advertisement is on-air
        val result = withContext(Dispatchers.Main) {
            advertiser.startAdvertisingSuspend(encoded)
        }

        return when (result) {
            is AdvertiseResult.Success -> {
                Log.d(TAG, "[SOS] BLE advertising confirmed ACTIVE")
                updateState { copy(sosActive = true, advertiserRunning = true, lastError = null) }
                true
            }
            is AdvertiseResult.Failure -> {
                Log.e(TAG, "[SOS] BLE advertising FAILED: ${result.reason}")
                // Clean up failed state
                packetRouter.isVictimModeActive = false
                activeSosSenderIdHash = null
                updateState { copy(sosActive = false, advertiserRunning = false, lastError = "SOS failed: ${result.reason}") }
                // Resume presence since SOS failed
                withContext(Dispatchers.Main) {
                    startPresenceAdvertising()
                }
                false
            }
        }
    }

    /**
     * Stop SOS broadcast, resume presence advertising.
     */
    fun stopSosBroadcast() {
        Log.d(TAG, "Stopping SOS broadcast")
        activeSosSenderIdHash = null
        advertiser.stopAdvertising()
        packetRouter.isVictimModeActive = false
        updateState { copy(sosActive = false, advertiserRunning = false) }

        // Resume presence advertising
        startPresenceAdvertising()
    }

    // --- Presence Advertising ---

    private fun startPresenceAdvertising() {
        if (isPresenceAdvertising) return
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return

        val bleAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser ?: return

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(false)
            .setTimeout(0)
            .build()

        val presencePayload = byteArrayOf(
            0x4D, 0x50,
            ((localNodeId shr 24) and 0xFF).toByte(),
            ((localNodeId shr 16) and 0xFF).toByte(),
            ((localNodeId shr 8) and 0xFF).toByte(),
            (localNodeId and 0xFF).toByte()
        )

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addManufacturerData(BlePacketCodec.MANUFACTURER_ID, presencePayload)
            .build()

        presenceCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                isPresenceAdvertising = true
                Log.d(TAG, "Presence advertising started")
            }

            override fun onStartFailure(errorCode: Int) {
                isPresenceAdvertising = false
                Log.e(TAG, "Presence advertising failed: $errorCode")
            }
        }

        try {
            bleAdvertiser.startAdvertising(settings, data, presenceCallback)
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing BLE advertise permission for presence", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting presence advertising", e)
        }
    }

    private fun stopPresenceAdvertising() {
        if (!isPresenceAdvertising && presenceCallback == null) return
        try {
            val bleAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser
            if (bleAdvertiser != null && presenceCallback != null) {
                bleAdvertiser.stopAdvertising(presenceCallback)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping presence advertising", e)
        } finally {
            isPresenceAdvertising = false
            presenceCallback = null
        }
    }

    // --- Scanner Retry with Backoff ---

    private fun startScannerWithRetry() {
        scope.launch {
            var retryCount = 0
            var delayMs = INITIAL_RETRY_DELAY_MS

            while (isActive && retryCount <= MAX_RETRIES) {
                val btEnabled = bluetoothAdapter?.isEnabled ?: false
                if (!btEnabled) {
                    updateState { copy(bluetoothEnabled = false, scannerRunning = false, lastError = "Bluetooth disabled") }
                    return@launch
                }

                // Try starting scanner (runs on main thread for BLE API)
                val success = withContext(Dispatchers.Main) {
                    scanner.startScanning()
                }

                if (success) {
                    Log.d(TAG, "Scanner started successfully${if (retryCount > 0) " (after $retryCount retries)" else ""}")
                    updateState { copy(scannerRunning = true, lastError = null) }
                    return@launch
                }

                retryCount++
                if (retryCount > MAX_RETRIES) {
                    Log.e(TAG, "Scanner failed after $MAX_RETRIES retries")
                    updateState { copy(scannerRunning = false, lastError = "BLE scanner failed") }
                    return@launch
                }

                Log.w(TAG, "Scanner start failed, retry $retryCount/$MAX_RETRIES in ${delayMs}ms")
                updateState { copy(scannerRunning = false, lastError = "Scanner retry $retryCount/$MAX_RETRIES") }
                delay(delayMs)
                delayMs = (delayMs * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
            }
        }
    }

    // --- Bluetooth State Monitoring ---

    private fun registerBluetoothReceiver() {
        if (btReceiver != null) return

        btReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)

                when (state) {
                    BluetoothAdapter.STATE_ON -> {
                        Log.d(TAG, "Bluetooth turned ON — attempting mesh recovery")
                        updateState { copy(bluetoothEnabled = true, lastError = null) }
                        startScannerWithRetry()
                        startPresenceAdvertising()
                    }
                    BluetoothAdapter.STATE_OFF -> {
                        Log.d(TAG, "Bluetooth turned OFF")
                        scanner.stopScanning()
                        stopPresenceAdvertising()
                        peerRegistry.clear()
                        updateState {
                            copy(
                                bluetoothEnabled = false,
                                scannerRunning = false,
                                advertiserRunning = false,
                                nearbyPeerCount = 0,
                                lastError = "Bluetooth disabled"
                            )
                        }
                    }
                }
            }
        }

        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        context.registerReceiver(btReceiver, filter)
    }

    private fun unregisterBluetoothReceiver() {
        btReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering BT receiver", e)
            }
        }
        btReceiver = null
    }

    // --- State Management ---

    private fun updatePeerCount() {
        updateState { copy(nearbyPeerCount = peerRegistry.count()) }
    }

    private inline fun updateState(transform: MeshState.() -> MeshState) {
        val newState = _meshState.value.transform()
        _meshState.value = newState
        onStateChanged?.invoke(newState)
    }

    fun isScanning(): Boolean = scanner.isScanning()
    fun isAdvertising(): Boolean = advertiser.isAdvertising() || isPresenceAdvertising
}
