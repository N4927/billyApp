package com.example.billyapp.ble

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.billyapp.core.Encounter
import com.example.billyapp.core.EncounterBus
import com.example.billyapp.core.EncounterStore
import com.example.billyapp.core.ResolvedEncounter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

        // Riceve nuovi eventi in tempo reale dal bus
        viewModelScope.launch {
            EncounterBus.events.collect { encounter ->
                addOrUpdateEncounter(encounter)
                EncounterStore.append(getApplication(), encounter)
            }
        }

        // Pulisce periodicamente gli incontri vecchi
        viewModelScope.launch {
            while (true) {
                cleanupOldEncounters()
                delay(60 * 1000L)
            }
        }
    }

    // Simulazione manuale (debug)
    fun addEncounter(name: String) {
        val encounter = Encounter(
            idHex = name.lowercase(),
            rssi = -50,
            timestampSec = System.currentTimeMillis() / 1000,
            resolvedName = name
        )
        addOrUpdateEncounter(encounter)
        EncounterStore.append(getApplication(), encounter)
    }

    private fun addOrUpdateEncounter(encounter: Encounter) {
        val now = System.currentTimeMillis()
        val existingIndex = encounters.indexOfFirst {
            it.encounter.idHex == encounter.idHex ||
                    (!encounter.resolvedName.isNullOrBlank() &&
                            it.encounter.resolvedName == encounter.resolvedName)
        }

        if (existingIndex != -1) {
            val existing = encounters[existingIndex]
            val updated = existing.copy(
                count = existing.count + 1,
                encounter = encounter.copy(timestampSec = now / 1000)
            )
            encounters[existingIndex] = updated
        } else {
            encounters.add(EncounterWithCount(encounter, count = 1))
        }

        cleanupOldEncounters()
        updateResolvedList() // ✅ aggiorna la lista per la UI
    }

    private fun mergeEncounters(list: List<Encounter>): List<EncounterWithCount> {
        val grouped = list.groupBy { it.idHex.ifBlank { it.resolvedName ?: "unknown" } }
        return grouped.map { (_, encounters) ->
            val last = encounters.maxByOrNull { it.timestampSec }!!
            EncounterWithCount(encounter = last, count = encounters.size)
        }
    }

    private fun cleanupOldEncounters() {
        val now = System.currentTimeMillis()
        encounters.removeAll {
            val lastSeen = it.encounter.timestampSec * 1000
            now - lastSeen > TIMEOUT_MS
        }
        updateResolvedList()
    }

    // 🔹 Conversione per la UI
    private fun updateResolvedList() {
        resolvedEncounters.clear()
        resolvedEncounters.addAll(
            encounters.map {
                ResolvedEncounter(
                    name = it.encounter.resolvedName ?: "Unknown",
                    count = it.count
                )
            }
        )
    }
}

data class EncounterWithCount(
    val encounter: Encounter,
    val count: Int
)
