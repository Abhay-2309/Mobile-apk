package com.meshlink

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.*

class MainActivity : FlutterActivity() {

    private val CHANNEL = "com.meshlink.ble/channel"
    private val EVENT_CHANNEL = "com.meshlink.ble/events"
    private val PERMISSION_REQUEST_CODE = 1001

    private var eventSink: EventChannel.EventSink? = null
    private var pendingPermissionResult: MethodChannel.Result? = null

    // Coroutine scope for async BLE operations (tied to Activity lifecycle)
    private val mainScope = MainScope()

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        // Attach listeners to service if it's already running
        attachServiceListeners()

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "requestPermissions" -> {
                    handleRequestPermissions(result)
                }
                "checkBluetoothEnabled" -> {
                    val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                    val enabled = bluetoothManager?.adapter?.isEnabled ?: false
                    result.success(enabled)
                }
                "startMesh" -> {
                    startMeshService()
                    // Give service a moment to start, then report state
                    android.os.Handler(mainLooper).postDelayed({
                        val service = BleMeshForegroundService.instance
                        val runtime = service?.meshRuntime
                        val isRunning = runtime?.isScanning() ?: false
                        result.success(isRunning)
                    }, 500)
                }
                "stopMesh" -> {
                    stopMeshService()
                    result.success(true)
                }
                "broadcastSos" -> {
                    val runtime = BleMeshForegroundService.instance?.meshRuntime
                    if (runtime == null) {
                        result.success(false)
                        return@setMethodCallHandler
                    }

                    val messageId = (call.argument<Number>("messageId") ?: 0L).toLong()
                    val senderIdHash = (call.argument<Number>("senderIdHash") ?: 0L).toLong()
                    val lat = call.argument<Double>("latitude") ?: 0.0
                    val lon = call.argument<Double>("longitude") ?: 0.0
                    val timestamp = (call.argument<Number>("timestamp") ?: 0L).toLong()
                    val ttl = call.argument<Int>("ttl") ?: 5
                    val hopCount = call.argument<Int>("hopCount") ?: 0
                    val battery = call.argument<Int>("battery") ?: 100
                    val severity = call.argument<Int>("severity") ?: 2

                    // Launch coroutine to await actual BLE advertising result.
                    // broadcastSos is now a suspend function that only returns true
                    // after AdvertiseCallback.onStartSuccess() fires.
                    mainScope.launch {
                        try {
                            val ok = runtime.broadcastSos(
                                messageId = messageId,
                                senderIdHash = senderIdHash,
                                lat = lat,
                                lon = lon,
                                timestamp = timestamp,
                                ttl = ttl,
                                hopCount = hopCount,
                                battery = battery,
                                severity = severity
                            )
                            result.success(ok)
                        } catch (e: Exception) {
                            Log.e(TAG, "[SOS] broadcastSos failed with exception", e)
                            result.success(false)
                        }
                    }
                }
                "stopSosBroadcast" -> {
                    BleMeshForegroundService.instance?.meshRuntime?.stopSosBroadcast()
                    result.success(true)
                }
                "getMeshState" -> {
                    val runtime = BleMeshForegroundService.instance?.meshRuntime
                    val state = runtime?.meshState?.value
                    if (state != null) {
                        result.success(state.toMap())
                    } else {
                        result.success(mapOf(
                            "serviceRunning" to false,
                            "bluetoothEnabled" to false,
                            "scannerRunning" to false,
                            "advertiserRunning" to false,
                            "nearbyPeerCount" to 0,
                            "sosActive" to false,
                            "lastError" to null
                        ))
                    }
                }
                "getDiagnostics" -> {
                    val runtime = BleMeshForegroundService.instance?.meshRuntime
                    val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                    val diagnostics = mapOf(
                        "isScanning" to (runtime?.isScanning() ?: false),
                        "isAdvertising" to (runtime?.isAdvertising() ?: false),
                        "bluetoothEnabled" to (bluetoothManager?.adapter?.isEnabled ?: false),
                        "serviceRunning" to (BleMeshForegroundService.instance != null),
                        "nearbyPeerCount" to (runtime?.peerRegistry?.count() ?: 0)
                    )
                    result.success(diagnostics)
                }
                else -> {
                    result.notImplemented()
                }
            }
        }

        EventChannel(flutterEngine.dartExecutor.binaryMessenger, EVENT_CHANNEL).setStreamHandler(
            object : EventChannel.StreamHandler {
                override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                    eventSink = events
                    attachServiceListeners()

                    // Immediately send current state so Flutter is up-to-date
                    val runtime = BleMeshForegroundService.instance?.meshRuntime
                    val state = runtime?.meshState?.value
                    if (state != null) {
                        events?.success(mapOf(
                            "type" to "MESH_STATE",
                            "data" to state.toMap()
                        ))
                    }
                }

                override fun onCancel(arguments: Any?) {
                    eventSink = null
                    detachServiceListeners()
                }
            }
        )
    }

    override fun cleanUpFlutterEngine(flutterEngine: FlutterEngine) {
        detachServiceListeners()
        super.cleanUpFlutterEngine(flutterEngine)
    }

    override fun onDestroy() {
        mainScope.cancel()
        super.onDestroy()
    }

    // --- Permission Handling ---

    private fun handleRequestPermissions(result: MethodChannel.Result) {
        val permissionsNeeded = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsNeeded.isEmpty()) {
            Log.d(TAG, "All permissions already granted")
            val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            result.success(mapOf(
                "granted" to true,
                "bluetoothEnabled" to (bluetoothManager?.adapter?.isEnabled ?: false)
            ))
            return
        }

        Log.d(TAG, "Requesting permissions: $permissionsNeeded")
        pendingPermissionResult = result
        ActivityCompat.requestPermissions(
            this,
            permissionsNeeded.toTypedArray(),
            PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

            Log.d(TAG, "Permission result: allGranted=$allGranted, permissions=${permissions.toList()}")

            pendingPermissionResult?.success(mapOf(
                "granted" to allGranted,
                "bluetoothEnabled" to (bluetoothManager?.adapter?.isEnabled ?: false)
            ))
            pendingPermissionResult = null
        }
    }

    // --- Service Management ---

    private fun startMeshService() {
        Log.d(TAG, "Starting BleMeshForegroundService")
        val intent = Intent(this, BleMeshForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        android.os.Handler(mainLooper).postDelayed({
            attachServiceListeners()
        }, 300)
    }

    private fun stopMeshService() {
        Log.d(TAG, "Stopping BleMeshForegroundService")
        val intent = Intent(this, BleMeshForegroundService::class.java)
        stopService(intent)
    }

    // --- Service Listener Bridge ---

    private fun attachServiceListeners() {
        val service = BleMeshForegroundService.instance ?: return

        service.setOnPacketReceivedListener { packetMap ->
            runOnUiThread {
                eventSink?.success(mapOf(
                    "type" to "PACKET",
                    "data" to packetMap
                ))
            }
        }

        service.setOnLogListener { tag, message ->
            runOnUiThread {
                eventSink?.success(mapOf(
                    "type" to "LOG",
                    "tag" to tag,
                    "message" to message
                ))
            }
        }

        service.setOnStateChangedListener { meshState ->
            runOnUiThread {
                eventSink?.success(mapOf(
                    "type" to "MESH_STATE",
                    "data" to meshState.toMap()
                ))
            }
        }
    }

    private fun detachServiceListeners() {
        val service = BleMeshForegroundService.instance ?: return
        service.setOnPacketReceivedListener(null)
        service.setOnLogListener(null)
        service.setOnStateChangedListener(null)
    }
}
