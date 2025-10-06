package com.example.billyapp.ble

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.billyapp.core.Encounter
import com.example.billyapp.core.EncounterBus
import com.example.billyapp.core.EncounterStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BleViewModel(app: Application) : AndroidViewModel(app) {

    val encounters = mutableStateListOf<EncounterWithCount>()

    // 🔹 Timeout in minutes before removing an old encounter
    private val TIMEOUT_MINUTES = 10
    private val TIMEOUT_MS = TIMEOUT_MINUTES * 60 * 1000L

    init {
        // Load saved encounters and merge duplicates
        val loaded = EncounterStore.loadAll(getApplication())
        encounters.clear()
        encounters.addAll(mergeEncounters(loaded))

        // Listen in real time from EncounterBus
        viewModelScope.launch {
            EncounterBus.events.collect { encounter ->
                addOrUpdateEncounter(encounter)
                EncounterStore.append(getApplication(), encounter)
            }
        }

        // 🔹 Periodically cleanup old encounters
        viewModelScope.launch {
            while (true) {
                cleanupOldEncounters()
                delay(60 * 1000L) // check every minute
            }
        }
    }

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
            // Update existing entry
            val existing = encounters[existingIndex]
            val updated = existing.copy(
                count = existing.count + 1,
                encounter = encounter.copy(timestampSec = now / 1000)
            )
            encounters[existingIndex] = updated
        } else {
            // Add new one
            encounters.add(EncounterWithCount(encounter, count = 1))
        }

        // 🔹 Cleanup outdated encounters after each new one
        cleanupOldEncounters()
    }

    private fun mergeEncounters(list: List<Encounter>): List<EncounterWithCount> {
        val grouped = list.groupBy { it.idHex.ifBlank { it.resolvedName ?: "unknown" } }
        return grouped.map { (_, encounters) ->
            val last = encounters.maxByOrNull { it.timestampSec }!!
            EncounterWithCount(encounter = last, count = encounters.size)
        }
    }

    // 🔹 Remove encounters older than TIMEOUT_MINUTES
    private fun cleanupOldEncounters() {
        val now = System.currentTimeMillis()
        encounters.removeAll {
            val lastSeen = it.encounter.timestampSec * 1000
            now - lastSeen > TIMEOUT_MS
        }
    }
}

data class EncounterWithCount(
    val encounter: Encounter,
    val count: Int
)
