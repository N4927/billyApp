package com.example.billyapp.core

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel

class BleViewModel : ViewModel() {
    val encounters = mutableStateListOf<ResolvedEncounter>()

    fun addEncounter(name: String) {
        val existing = encounters.find { it.name == name }
        if (existing != null) {
            val idx = encounters.indexOf(existing)
            encounters[idx] = existing.copy(count = existing.count + 1)
        } else {
            encounters.add(ResolvedEncounter(name))
        }
    }
}
