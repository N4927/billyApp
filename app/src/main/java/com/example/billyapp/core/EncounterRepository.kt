package com.example.billyapp.core

import java.util.concurrent.ConcurrentHashMap

/**
 * Semplice filtro anti-duplicati: evita di processare lo stesso ID troppo spesso.
 */
class EncounterRepository(
    private val ttlSeconds: Long = 120
) {
    private val lastSeen = ConcurrentHashMap<String, Long>() // idHex -> ts

    fun shouldProcess(idHex: String, nowSec: Long): Boolean {
        val prev = lastSeen[idHex] ?: 0L
        val ok = (nowSec - prev) >= (ttlSeconds / 4) // es. 30s se ttl=120s
        if (ok) lastSeen[idHex] = nowSec
        return ok
    }
}
