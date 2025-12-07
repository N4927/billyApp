package com.example.billyapp.ble

import android.app.Application
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.shared.Encounter
import com.example.shared.EncounterBus
import com.example.billyapp.core.EncounterStore
import com.example.shared.ResolvedEncounter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BleViewModel(app: Application) : AndroidViewModel(app) {

    // 🔹 Lista interna con i dati grezzi (Encounter)
    val encounters = mutableStateListOf<EncounterWithCount>()

    // 🔹 Lista pronta per la UI (ResolvedEncounter)
    val resolvedEncounters = mutableStateListOf<ResolvedEncounter>()

    private val TIMEOUT_MINUTES = 10
    private val TIMEOUT_MS = TIMEOUT_MINUTES * 60 * 1000L

    init {
        // Carica i dati salvati
        val loaded = EncounterStore.loadAll(getApplication())
        encounters.clear()
        encounters.addAll(mergeEncounters(loaded))
        updateResolvedList()

        // 🔹 Riceve nuovi eventi in tempo reale dal bus
        viewModelScope.launch {
            EncounterBus.events.collect { encounter ->
                Log.d("BleViewModel", "📥 Received encounter in ViewModel: ${encounter.resolvedName}")
                Log.d("BleViewModel", "🧩 idHex=${encounter.idHex.take(16)}..., rssi=${encounter.rssi}, ts=${encounter.timestampSec}")

                withContext(Dispatchers.Main) {
                    addOrUpdateEncounter(encounter)
                    EncounterStore.append(getApplication(), encounter)
                }
            }
        }

        // 🔹 Pulisce periodicamente gli incontri vecchi
        viewModelScope.launch {
            while (true) {
                cleanupOldEncounters()
                delay(60 * 1000L) // Check every minute
            }
        }
    }

    // 🔹 Simulazione manuale (debug) - UPDATED for new architecture
    fun addEncounter(name: String) {
        val encounter = Encounter(
            idHex = "simulated_${System.currentTimeMillis()}", // Simulated rotating BID
            rssi = -50,
            timestampSec = System.currentTimeMillis() / 1000,
            resolvedName = name
        )
        addOrUpdateEncounter(encounter)
        EncounterStore.append(getApplication(), encounter)
    }

    // 🔹 Aggiorna o aggiunge un nuovo encounter - FIXED for rotating BIDs
    private fun addOrUpdateEncounter(encounter: Encounter) {
        Log.d("BleViewModel", "🧩 addOrUpdateEncounter for ${encounter.resolvedName}")

        val now = System.currentTimeMillis()

        // ✅ NEW: With rotating BIDs, we deduplicate by name AND time window
        // BIDs change every 10 minutes, so same user can have multiple BIDs
        val existingIndex = encounters.indexOfFirst { existing ->
            // Same user and within recent time window (to avoid duplicates from same scanning session)
            existing.encounter.resolvedName == encounter.resolvedName &&
                    (now / 1000 - existing.encounter.timestampSec) < 300 // 5 minutes window
        }

        if (existingIndex != -1) {
            // Update existing encounter with latest timestamp and BID
            val existing = encounters[existingIndex]
            val updated = existing.copy(
                count = existing.count + 1,
                encounter = encounter // Keep the new BID and timestamp
            )
            encounters[existingIndex] = updated
            Log.d("BleViewModel", "🔁 Updated encounter count=${updated.count} for ${encounter.resolvedName}")
        } else {
            // New encounter (either new user or same user after time window)
            encounters.add(EncounterWithCount(encounter, count = 1))
            Log.d("BleViewModel", "🆕 Added new encounter: ${encounter.resolvedName}")
        }

        updateResolvedList()
    }

    // 🔹 Unisce eventuali duplicati al caricamento - UPDATED for rotating BIDs
    private fun mergeEncounters(list: List<Encounter>): List<EncounterWithCount> {
        // Group by resolved name since BIDs rotate but users stay the same
        val grouped = list.groupBy { it.resolvedName ?: "unknown_${it.idHex.take(8)}" }
        return grouped.map { (name, encounters) ->
            // Take the most recent encounter for each user
            val last = encounters.maxByOrNull { it.timestampSec }!!
            // Sum all counts for this user
            val totalCount = encounters.size
            EncounterWithCount(encounter = last, count = totalCount)
        }
    }

    // 🔹 Pulisce gli incontri vecchi - ENABLED with proper timeout
    private fun cleanupOldEncounters() {
        val now = System.currentTimeMillis()
        val cutoff = now - TIMEOUT_MS

        val removed = encounters.removeAll { encounterWithCount ->
            val isOld = (encounterWithCount.encounter.timestampSec * 1000) < cutoff
            if (isOld) {
                Log.d("BleViewModel", "🧹 Removing old encounter: ${encounterWithCount.encounter.resolvedName}")
            }
            isOld
        }

        if (removed) {
            updateResolvedList()
            Log.d("BleViewModel", "🧹 Cleanup removed old encounters, remaining: ${encounters.size}")
        }
    }

    // 🔹 Conversione per la UI
    private fun updateResolvedList() {
        resolvedEncounters.clear()

        // Group by user name since BIDs rotate but users are constant
        val groupedByUser = encounters.groupBy { it.encounter.resolvedName ?: "Unknown" }

        resolvedEncounters.addAll(
            groupedByUser.map { (userName, userEncounters) ->
                // Use the most recent encounter for this user
                val mostRecent = userEncounters.maxByOrNull { it.encounter.timestampSec }!!
                // Sum counts from all encounters for this user
                val totalCount = userEncounters.sumOf { it.count }

                ResolvedEncounter(
                    name = userName,
                    count = totalCount,
                    idHex = mostRecent.encounter.idHex // Show current BID
                )
            }
        )

        Log.d("BleViewModel", "🎨 Updated resolved list. Size=${resolvedEncounters.size}")
        resolvedEncounters.forEach {
            Log.d("BleViewModel", "➡️ ${it.name} (count=${it.count}, current BID=${it.idHex.take(8)}...)")
        }
    }

    // 🔹 Clear all encounters (for testing)
    fun clearAllEncounters() {
        encounters.clear()
        updateResolvedList()
        // Also clear stored encounters
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                EncounterStore.saveAll(getApplication(), emptyList())
            }
        }
        Log.d("BleViewModel", "🧹 Cleared all encounters")
    }


    // 🔹 Get encounter statistics
    fun getEncounterStats(): String {
        val uniqueUsers = resolvedEncounters.size
        val totalEncounters = encounters.sumOf { it.count }
        return "Users: $uniqueUsers, Total encounters: $totalEncounters"
    }
}

// 🔹 Modello dati interno
data class EncounterWithCount(
    val encounter: Encounter,
    val count: Int
)