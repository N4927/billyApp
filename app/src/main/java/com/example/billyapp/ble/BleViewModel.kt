package com.example.billyapp.ble

import android.app.Application
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.billyapp.core.Encounter
import com.example.billyapp.core.EncounterBus
import com.example.billyapp.core.EncounterStore
import com.example.billyapp.core.ResolvedEncounter
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
                Log.d("BleViewModel", "🧩 idHex=${encounter.idHex}, rssi=${encounter.rssi}, ts=${encounter.timestampSec}")

                withContext(Dispatchers.Main) {
                    addOrUpdateEncounter(encounter)
                    EncounterStore.append(getApplication(), encounter)
                }
            }
        }

        // 🔹 Pulisce periodicamente gli incontri vecchi (disattivato per debug)
        viewModelScope.launch {
            while (true) {
                // cleanupOldEncounters()
                delay(60 * 1000L)
            }
        }
    }

    // 🔹 Simulazione manuale (debug)
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

    // 🔹 Aggiorna o aggiunge un nuovo encounter (deduplica per nome)
    private fun addOrUpdateEncounter(encounter: Encounter) {
        Log.d("BleViewModel", "🧩 addOrUpdateEncounter for ${encounter.resolvedName}")

        val now = System.currentTimeMillis()

        // ✅ Deduplica per nome risolto, non per ID che ruota
        val existingIndex = encounters.indexOfFirst {
            it.encounter.resolvedName == encounter.resolvedName
        }

        if (existingIndex != -1) {
            val existing = encounters[existingIndex]
            val updated = existing.copy(
                count = existing.count + 1,
                encounter = encounter.copy(timestampSec = now / 1000)
            )
            encounters[existingIndex] = updated
            Log.d("BleViewModel", "🔁 Updated encounter count=${updated.count} for ${encounter.resolvedName}")
        } else {
            encounters.add(EncounterWithCount(encounter, count = 1))
            Log.d("BleViewModel", "🆕 Added new encounter: ${encounter.resolvedName}")
        }

        updateResolvedList()
    }

    // 🔹 Unisce eventuali duplicati al caricamento
    private fun mergeEncounters(list: List<Encounter>): List<EncounterWithCount> {
        val grouped = list.groupBy { it.resolvedName ?: "unknown" }
        return grouped.map { (_, encounters) ->
            val last = encounters.maxByOrNull { it.timestampSec }!!
            EncounterWithCount(encounter = last, count = encounters.size)
        }
    }

    // 🔹 Pulisce gli incontri vecchi (disabilitato per debug)
    private fun cleanupOldEncounters() {
        // ⚠️ Disattivato per debug (timestamp non realistici)
        if (encounters.isNotEmpty()) {
            Log.d("BleViewModel", "⏳ Skipping cleanup (debug mode), keeping ${encounters.size} encounters")
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
                    count = it.count,
                    idHex = it.encounter.idHex
                )
            }
        )

        Log.d("BleViewModel", "🎨 Updated resolved list. Size=${resolvedEncounters.size}")
        resolvedEncounters.forEach {
            Log.d("BleViewModel", "➡️ ${it.name} (count=${it.count}, id=${it.idHex.take(8)}...)")
        }
    }
}

// 🔹 Modello dati interno
data class EncounterWithCount(
    val encounter: Encounter,
    val count: Int
)
