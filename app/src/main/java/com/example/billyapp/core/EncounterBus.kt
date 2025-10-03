// com.example.billyapp.core.EncounterBus.kt
package com.example.billyapp.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object EncounterBus {
    // Flow che trasporta gli encounter in tempo reale
    private val _events = MutableSharedFlow<Encounter>(extraBufferCapacity = 64)
    val events = _events.asSharedFlow()

    suspend fun emit(encounter: Encounter) {
        _events.emit(encounter)
    }

}
