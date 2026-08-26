package com.meshlink.mesh

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks nearby MeshLink devices observed via BLE scanning.
 *
 * A "peer" is any device advertising with MeshLink manufacturer ID 0x4D4C.
 * Peers are NOT global network members — they are locally observable nodes.
 *
 * Peers expire after [PEER_STALE_TIMEOUT_MS] milliseconds without being seen.
 */
class PeerRegistry {

    companion object {
        private const val TAG = "PeerRegistry"

        /**
         * Peer expiry timeout: 45 seconds.
         * BLE advertisements at LOW_LATENCY are seen multiple times per second,
         * so 45s is generous enough to avoid flicker while still removing truly
         * absent devices within a reasonable time.
         */
        const val PEER_STALE_TIMEOUT_MS = 45_000L
    }

    data class Peer(
        val address: String,
        var lastSeenMs: Long,
        var rssi: Int
    )

    private val peers = ConcurrentHashMap<String, Peer>()
    private val _peerCount = MutableStateFlow(0)

    /** Observable peer count. */
    val peerCount: StateFlow<Int> = _peerCount

    /**
     * Called on every BLE scan result identified as a MeshLink device.
     * Updates lastSeen or creates a new peer entry.
     */
    fun onPeerSeen(address: String, rssi: Int) {
        val now = System.currentTimeMillis()
        val existing = peers[address]
        if (existing != null) {
            existing.lastSeenMs = now
            existing.rssi = rssi
        } else {
            peers[address] = Peer(address = address, lastSeenMs = now, rssi = rssi)
            updateCount()
            Log.d(TAG, "New peer discovered: $address (total: ${peers.size})")
        }
    }

    /**
     * Remove peers not seen within [maxAgeMs] milliseconds.
     * Should be called periodically (e.g. every 15 seconds).
     */
    fun evictStale(maxAgeMs: Long = PEER_STALE_TIMEOUT_MS) {
        val now = System.currentTimeMillis()
        val before = peers.size
        val iter = peers.entries.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            if (now - entry.value.lastSeenMs > maxAgeMs) {
                Log.d(TAG, "Evicting stale peer: ${entry.key}")
                iter.remove()
            }
        }
        val after = peers.size
        if (before != after) {
            updateCount()
        }
    }

    /** Current snapshot of peer count. */
    fun count(): Int = peers.size

    /** Clear all peers (e.g. on mesh stop). */
    fun clear() {
        peers.clear()
        updateCount()
    }

    private fun updateCount() {
        _peerCount.value = peers.size
    }
}
