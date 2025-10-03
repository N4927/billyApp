package com.example.billyapp.ble

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.billyapp.core.Encounter
import com.example.billyapp.core.EncounterBus
import com.example.billyapp.core.EncounterStore
import kotlinx.coroutines.launch

class BleViewModel(app: Application) : AndroidViewModel(app) {

    val encounters = mutableStateListOf<Encounter>()

    init {
        // Carica gli encounter salvati
        encounters.addAll(EncounterStore.loadAll(getApplication()))

        // Ascolta in tempo reale dal bus
        viewModelScope.launch {
            EncounterBus.events.collect { encounter ->
                // Aggiorna lista
                encounters.add(encounter)

                // Salva su file
                EncounterStore.append(getApplication(), encounter)
            }
        }
    }
    fun addEncounter(name: String) {
        val encounter = Encounter(
            idHex = name.lowercase(), // placeholder
            rssi = -50,
            timestampSec = System.currentTimeMillis() / 1000,
            resolvedName = name
        )

        encounters.add(encounter)
        EncounterStore.append(getApplication(), encounter)
    }

}