package com.meshlink

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Bundle
import com.meshlink.ble.BleMeshManager
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {

    private val CHANNEL = "com.meshlink.ble/channel"
    private val EVENT_CHANNEL = "com.meshlink.ble/events"

    private var meshManager: BleMeshManager? = null
    private var eventSink: EventChannel.EventSink? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

        meshManager = BleMeshManager(this, bluetoothAdapter)

        meshManager?.setOnPacketReceivedListener { packetMap ->
            runOnUiThread {
                val data = mapOf(
                    "type" to "PACKET",
                    "data" to packetMap
                )
                eventSink?.success(data)
            }
        }

        meshManager?.setOnLogListener { tag, message ->
            runOnUiThread {
                val data = mapOf(
                    "type" to "LOG",
                    "tag" to tag,
                    "message" to message
                )
                eventSink?.success(data)
            }
        }

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "startMesh" -> {
                    val enableRelay = call.argument<Boolean>("enableRelay") ?: true
                    val ok = meshManager?.startMesh(enableRelay) ?: false
                    result.success(ok)
                }
                "stopMesh" -> {
                    meshManager?.stopMesh()
                    result.success(true)
                }
                "broadcastSos" -> {
                    val messageId = (call.argument<Number>("messageId") ?: 0L).toLong()
                    val senderIdHash = (call.argument<Number>("senderIdHash") ?: 0L).toLong()
                    val lat = call.argument<Double>("latitude") ?: 0.0
                    val lon = call.argument<Double>("longitude") ?: 0.0
                    val timestamp = (call.argument<Number>("timestamp") ?: 0L).toLong()
                    val ttl = call.argument<Int>("ttl") ?: 5
                    val hopCount = call.argument<Int>("hopCount") ?: 0
                    val battery = call.argument<Int>("battery") ?: 100
                    val severity = call.argument<Int>("severity") ?: 2

                    val ok = meshManager?.broadcastSos(
                        messageId = messageId,
                        senderIdHash = senderIdHash,
                        lat = lat,
                        lon = lon,
                        timestamp = timestamp,
                        ttl = ttl,
                        hopCount = hopCount,
                        battery = battery,
                        severity = severity
                    ) ?: false
                    result.success(ok)
                }
                "stopSosBroadcast" -> {
                    meshManager?.stopSosBroadcast()
                    result.success(true)
                }
                "getDiagnostics" -> {
                    val diagnostics = mapOf(
                        "isScanning" to (meshManager?.isScanning() ?: false),
                        "isAdvertising" to (meshManager?.isAdvertising() ?: false),
                        "bluetoothEnabled" to (bluetoothAdapter?.isEnabled ?: false)
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
                }

                override fun onCancel(arguments: Any?) {
                    eventSink = null
                }
            }
        )
    }
}
