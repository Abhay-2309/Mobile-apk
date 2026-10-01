package com.meshlink.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Result of an asynchronous BLE advertising attempt.
 * Only [Success] means the advertisement is physically on-air.
 */
sealed class AdvertiseResult {
    object Success : AdvertiseResult()
    data class Failure(val reason: String) : AdvertiseResult()
}

class BleAdvertiser(private val bluetoothAdapter: BluetoothAdapter?) {

    private var advertiser: BluetoothLeAdvertiser? = null
    private var isAdvertising = false
    private var callback: AdvertiseCallback? = null
    private var onStatusChangeListener: ((Boolean, String?) -> Unit)? = null

    companion object {
        private const val TAG = "BleAdvertiser"
    }

    fun setOnStatusChangeListener(listener: (Boolean, String?) -> Unit) {
        onStatusChangeListener = listener
    }

    /**
     * Fire-and-forget advertising start. Returns true if the API call was made
     * without throwing, but does NOT guarantee the advertisement is on-air.
     *
     * Kept for relay burst advertising where exact success tracking is not critical.
     * For SOS broadcasting, use [startAdvertisingSuspend] instead.
     */
    fun startAdvertising(payload: ByteArray): Boolean {
        if (isAdvertising) {
            stopAdvertising()
        }

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth adapter not available or disabled")
            onStatusChangeListener?.invoke(false, "Bluetooth disabled")
            return false
        }

        advertiser = bluetoothAdapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            Log.e(TAG, "Device does not support BLE Advertising")
            onStatusChangeListener?.invoke(false, "BLE Advertising unsupported")
            return false
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .setTimeout(0) // Advertise indefinitely until explicitly stopped
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addManufacturerData(BlePacketCodec.MANUFACTURER_ID, payload)
            .build()

        callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                super.onStartSuccess(settingsInEffect)
                isAdvertising = true
                Log.d(TAG, "BLE Advertising started successfully")
                onStatusChangeListener?.invoke(true, null)
            }

            override fun onStartFailure(errorCode: Int) {
                super.onStartFailure(errorCode)
                isAdvertising = false
                val reason = when (errorCode) {
                    ADVERTISE_FAILED_DATA_TOO_LARGE -> "Data too large"
                    ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "Too many advertisers"
                    ADVERTISE_FAILED_ALREADY_STARTED -> "Already started"
                    ADVERTISE_FAILED_INTERNAL_ERROR -> "Internal error"
                    ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "Feature unsupported"
                    else -> "ErrorCode: $errorCode"
                }
                Log.e(TAG, "BLE Advertising start failed: $reason")
                onStatusChangeListener?.invoke(false, reason)
            }
        }

        try {
            advertiser?.startAdvertising(settings, data, callback)
            return true
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing Bluetooth Advertise Permission", e)
            onStatusChangeListener?.invoke(false, "Missing permission")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error starting advertising", e)
            onStatusChangeListener?.invoke(false, e.message)
            return false
        }
    }

    /**
     * Suspend until the Android BLE stack confirms advertising is on-air
     * (onStartSuccess) or reports a failure (onStartFailure).
     *
     * Returns [AdvertiseResult.Success] ONLY when the advertisement is
     * physically active. Returns [AdvertiseResult.Failure] with the exact
     * Android error reason otherwise.
     *
     * This is the authoritative method for SOS broadcasting.
     */
    suspend fun startAdvertisingSuspend(payload: ByteArray): AdvertiseResult {
        if (isAdvertising) {
            stopAdvertising()
        }

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e(TAG, "[SOS] Bluetooth adapter not available or disabled")
            return AdvertiseResult.Failure("Bluetooth disabled")
        }

        advertiser = bluetoothAdapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            Log.e(TAG, "[SOS] Device does not support BLE Advertising")
            return AdvertiseResult.Failure("BLE Advertising unsupported")
        }

        Log.d(TAG, "[SOS] startAdvertising requested, payload=${payload.size} bytes, " +
                    "hex=${payload.joinToString(" ") { "%02X".format(it) }}")

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .setTimeout(0)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addManufacturerData(BlePacketCodec.MANUFACTURER_ID, payload)
            .build()

        return suspendCancellableCoroutine { cont ->
            val cb = object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                    super.onStartSuccess(settingsInEffect)
                    isAdvertising = true
                    Log.d(TAG, "[SOS] advertiser callback SUCCESS")
                    onStatusChangeListener?.invoke(true, null)
                    if (cont.isActive) cont.resume(AdvertiseResult.Success)
                }

                override fun onStartFailure(errorCode: Int) {
                    super.onStartFailure(errorCode)
                    isAdvertising = false
                    val reason = when (errorCode) {
                        ADVERTISE_FAILED_DATA_TOO_LARGE -> "DATA_TOO_LARGE"
                        ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "TOO_MANY_ADVERTISERS"
                        ADVERTISE_FAILED_ALREADY_STARTED -> "ALREADY_STARTED"
                        ADVERTISE_FAILED_INTERNAL_ERROR -> "INTERNAL_ERROR"
                        ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "FEATURE_UNSUPPORTED"
                        else -> "ErrorCode: $errorCode"
                    }
                    Log.e(TAG, "[SOS] advertiser callback FAILED: $reason")
                    onStatusChangeListener?.invoke(false, reason)
                    if (cont.isActive) cont.resume(AdvertiseResult.Failure(reason))
                }
            }
            callback = cb

            try {
                advertiser?.startAdvertising(settings, data, cb)
            } catch (e: SecurityException) {
                Log.e(TAG, "[SOS] Missing Bluetooth Advertise Permission", e)
                if (cont.isActive) cont.resume(AdvertiseResult.Failure("Missing permission"))
            } catch (e: Exception) {
                Log.e(TAG, "[SOS] Error starting advertising", e)
                if (cont.isActive) cont.resume(AdvertiseResult.Failure(e.message ?: "Unknown error"))
            }

            cont.invokeOnCancellation {
                stopAdvertising()
            }
        }
    }

    fun stopAdvertising() {
        if (!isAdvertising && callback == null) return

        try {
            if (advertiser != null && callback != null) {
                advertiser?.stopAdvertising(callback)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping advertiser", e)
        } finally {
            isAdvertising = false
            callback = null
            Log.d(TAG, "BLE Advertising stopped")
            onStatusChangeListener?.invoke(false, null)
        }
    }

    fun isAdvertising(): Boolean = isAdvertising
}
