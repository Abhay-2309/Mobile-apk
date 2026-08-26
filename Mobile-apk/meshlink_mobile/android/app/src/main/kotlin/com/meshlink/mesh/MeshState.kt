package com.meshlink.mesh

/**
 * Central mesh state model exposed via StateFlow.
 * Every field reflects actual native conditions — no fake state.
 */
data class MeshState(
    val serviceRunning: Boolean = false,
    val bluetoothEnabled: Boolean = false,
    val scannerRunning: Boolean = false,
    val advertiserRunning: Boolean = false,
    val nearbyPeerCount: Int = 0,
    val sosActive: Boolean = false,
    val lastError: String? = null
) {
    /**
     * Human-readable notification text.
     */
    fun toNotificationText(): String {
        if (!serviceRunning) return "Mesh unavailable"
        if (!bluetoothEnabled) return "Bluetooth disabled"
        if (!scannerRunning && lastError != null) return "Mesh unavailable — $lastError"
        if (!scannerRunning) return "Mesh unavailable"

        return when (nearbyPeerCount) {
            0 -> "Mesh running — 0 nearby devices"
            1 -> "Mesh running — 1 nearby device"
            else -> "Mesh running — $nearbyPeerCount nearby devices"
        }
    }

    /**
     * Convert to Map for Flutter MethodChannel.
     */
    fun toMap(): Map<String, Any?> = mapOf(
        "serviceRunning" to serviceRunning,
        "bluetoothEnabled" to bluetoothEnabled,
        "scannerRunning" to scannerRunning,
        "advertiserRunning" to advertiserRunning,
        "nearbyPeerCount" to nearbyPeerCount,
        "sosActive" to sosActive,
        "lastError" to lastError
    )
}
