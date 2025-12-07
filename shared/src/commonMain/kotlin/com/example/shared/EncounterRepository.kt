package com.example.shared

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

class EncounterRepository(
    private val ttlSeconds: Long = 120
) {
    private val lastSeen = mutableMapOf<String, Long>()
    private val mutex = Mutex()

    suspend fun shouldProcess(bidHex: String, nowSec: Long = Clock.System.now().epochSeconds): Boolean {
        return mutex.withLock {
            val prev = lastSeen[bidHex] ?: 0L
            val ok = (nowSec - prev) >= (ttlSeconds / 4)
            if (ok) lastSeen[bidHex] = nowSec
            ok
        }
    }

    /**
     * Process a BID encounter and resolve it through FakeServer
     */
    suspend fun processEncounter(bidHex: String, rssi: Int): Encounter? {
        val now = Clock.System.now().epochSeconds

        if (!shouldProcess(bidHex, now)) {
            return null // Skip duplicate
        }

        // Resolve BID through FakeServer (PDF section 7.2.1)
        val user = FakeServer.resolveBid(bidHex, now)
        val resolvedName = user?.name

        return Encounter(
            idHex = bidHex,
            rssi = rssi,
            timestampSec = now,
            resolvedName = resolvedName
        )
    }

    /**
     * Clear old entries
     */
    suspend fun cleanup(nowSec: Long = Clock.System.now().epochSeconds) {
        mutex.withLock {
            lastSeen.entries.removeAll { (_, timestamp) ->
                nowSec - timestamp > ttlSeconds
            }
        }
    }
}