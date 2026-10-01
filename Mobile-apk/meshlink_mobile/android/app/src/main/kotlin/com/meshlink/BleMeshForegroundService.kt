package com.meshlink

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.meshlink.mesh.MeshRuntime
import com.meshlink.mesh.MeshState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

/**
 * Android Foreground Service that owns the MeshRuntime.
 *
 * Survives Activity lifecycle — the mesh continues when Flutter is
 * backgrounded, another app is opened, or the screen is locked.
 *
 * Inspired by BitChat's MeshForegroundService pattern:
 * - Persistent notification with live peer count
 * - Service owns all BLE resources
 * - START_STICKY for OS restart
 * - State survives Activity recreation
 */
class BleMeshForegroundService : Service() {

    companion object {
        private const val TAG = "BleMeshFgService"
        private const val CHANNEL_ID = "meshlink_ble_mesh"
        private const val NOTIFICATION_ID = 1001

        // Debounce notification updates to max once per 2 seconds
        private const val NOTIFICATION_DEBOUNCE_MS = 2_000L
        private const val ACTION_STOP_MESH = "com.meshlink.ACTION_STOP_MESH"

        @Volatile
        var instance: BleMeshForegroundService? = null
            private set
    }

    var meshRuntime: MeshRuntime? = null
        private set

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var notificationManager: NotificationManager? = null
    private var lastNotificationUpdateMs = 0L

    // Listeners for Flutter bridge (attached/detached by MainActivity)
    private var onPacketReceivedListener: ((Map<String, Any>) -> Unit)? = null
    private var onLogListener: ((String, String) -> Unit)? = null
    private var onStateChangedListener: ((MeshState) -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        instance = this
        notificationManager = getSystemService(NotificationManager::class.java)

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

        meshRuntime = MeshRuntime(this, bluetoothAdapter)

        // Wire runtime callbacks
        meshRuntime?.onPacketReceived = { packetMap ->
            onPacketReceivedListener?.invoke(packetMap)
        }
        meshRuntime?.onLog = { tag, message ->
            onLogListener?.invoke(tag, message)
        }
        meshRuntime?.onStateChanged = { newState ->
            onStateChangedListener?.invoke(newState)
            updateNotificationDebounced(newState)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand")

        if (intent?.action == ACTION_STOP_MESH) {
            Log.d(TAG, "User requested Stop Mesh via notification")
            stopSelf()
            return START_NOT_STICKY
        }

        createNotificationChannel()
        val notification = buildNotification("Starting mesh...")

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            Log.d(TAG, "startForeground successful")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground", e)
        }

        // Start the mesh runtime
        meshRuntime?.start()

        // Observe mesh state for notification updates
        serviceScope.launch {
            meshRuntime?.meshState?.collectLatest { state ->
                updateNotificationDebounced(state)
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy")
        serviceScope.cancel()
        meshRuntime?.stop()
        meshRuntime = null
        instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // --- Listener management for Flutter bridge ---

    fun setOnPacketReceivedListener(listener: ((Map<String, Any>) -> Unit)?) {
        onPacketReceivedListener = listener
        meshRuntime?.onPacketReceived = { packetMap ->
            listener?.invoke(packetMap)
        }
    }

    fun setOnLogListener(listener: ((String, String) -> Unit)?) {
        onLogListener = listener
        meshRuntime?.onLog = { tag, message ->
            listener?.invoke(tag, message)
        }
    }

    fun setOnStateChangedListener(listener: ((MeshState) -> Unit)?) {
        onStateChangedListener = listener
    }

    // --- Notification ---

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "MeshLink BLE Mesh",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps the BLE emergency mesh running in the background"
            setShowBadge(false)
        }
        notificationManager?.createNotificationChannel(channel)
    }

    private fun buildNotification(contentText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, BleMeshForegroundService::class.java).apply {
                action = ACTION_STOP_MESH
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopAction = Notification.Action.Builder(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Stop Mesh",
            stopIntent
        ).build()

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("MeshLink Rescue")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(stopAction)
            .build()
    }

    /**
     * Update notification with debouncing to avoid excessive system overhead.
     * Max 1 update per [NOTIFICATION_DEBOUNCE_MS] milliseconds.
     */
    private fun updateNotificationDebounced(state: MeshState) {
        val now = System.currentTimeMillis()
        if (now - lastNotificationUpdateMs < NOTIFICATION_DEBOUNCE_MS) return
        lastNotificationUpdateMs = now

        val text = state.toNotificationText()
        val notification = buildNotification(text)
        try {
            notificationManager?.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update notification", e)
        }
    }
}
