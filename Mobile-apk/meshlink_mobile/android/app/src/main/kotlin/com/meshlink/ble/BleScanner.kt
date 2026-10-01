package com.meshlink.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import android.util.Log

class BleScanner(private val bluetoothAdapter: BluetoothAdapter?) {

    private var scanner: BluetoothLeScanner? = null
    private var isScanning = false
    private var scanCallback: ScanCallback? = null
    private var onPacketDiscoveredListener: ((ByteArray, Int, String?) -> Unit)? = null
    private var onStatusChangeListener: ((Boolean, String?) -> Unit)? = null

    companion object {
        private const val TAG = "BleScanner"
    }

    fun setOnPacketDiscoveredListener(listener: (ByteArray, Int, String?) -> Unit) {
        onPacketDiscoveredListener = listener
    }

    fun setOnStatusChangeListener(listener: (Boolean, String?) -> Unit) {
        onStatusChangeListener = listener
    }

    fun startScanning(): Boolean {
        if (isScanning) return true

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth adapter not available or disabled")
            onStatusChangeListener?.invoke(false, "Bluetooth disabled")
            return false
        }

        scanner = bluetoothAdapter.bluetoothLeScanner
        if (scanner == null) {
            Log.e(TAG, "BLE Scanner not available")
            onStatusChangeListener?.invoke(false, "Scanner unavailable")
            return false
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        val filters = mutableListOf<ScanFilter>()
        // Filter by MeshLink Manufacturer ID 0x4D4C
        val filter = ScanFilter.Builder()
            .setManufacturerData(BlePacketCodec.MANUFACTURER_ID, byteArrayOf())
            .build()
        filters.add(filter)

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                super.onScanResult(callbackType, result)
                if (result == null) return

                val record = result.scanRecord ?: return
                val manufacturerData = record.getManufacturerSpecificData(BlePacketCodec.MANUFACTURER_ID)

                if (manufacturerData != null && manufacturerData.isNotEmpty()) {
                    val rssi = result.rssi
                    val deviceAddress = try { result.device?.address } catch (e: SecurityException) { null }
                    onPacketDiscoveredListener?.invoke(manufacturerData, rssi, deviceAddress)
                }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                super.onBatchScanResults(results)
                results?.forEach { result ->
                    onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, result)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                isScanning = false
                Log.e(TAG, "BLE Scan failed with errorCode: $errorCode")
                onStatusChangeListener?.invoke(false, "Scan failed code: $errorCode")
            }
        }

        try {
            // Note: If filter is too strict on some devices, scanning with empty filter and manual matching works reliably
            scanner?.startScan(null, settings, scanCallback)
            isScanning = true
            Log.d(TAG, "BLE Scanner started successfully")
            onStatusChangeListener?.invoke(true, null)
            return true
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing Bluetooth Scan Permission", e)
            onStatusChangeListener?.invoke(false, "Missing permission")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error starting scan", e)
            onStatusChangeListener?.invoke(false, e.message)
            return false
        }
    }

    fun stopScanning() {
        if (!isScanning && scanCallback == null) return

        try {
            if (scanner != null && scanCallback != null) {
                scanner?.stopScan(scanCallback)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping scan", e)
        } finally {
            isScanning = false
            scanCallback = null
            Log.d(TAG, "BLE Scanner stopped")
            onStatusChangeListener?.invoke(false, null)
        }
    }

    fun isScanning(): Boolean = isScanning
}
