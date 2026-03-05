package com.example.billyapp.proximity

import com.billyapp.shared.BillySDK  // ← CAMBIATO
import com.billyapp.shared.domain.model.ResolvedUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class KmmProximityDataSource : ProximityDataSource {

    // ← CAMBIATO: Usa BillySDK
    private val core get() = BillySDK.core

    override val encounters: Flow<List<ProximityViewModel.ProximityEncounterUiModel>>
        get() = BillySDK.getUIState().activeSet  // ← CAMBIATO
            .map { list -> list.map { it.toUiModel() } }

    override suspend fun refresh() {
        core.syncQueue()
    }

    override suspend fun clear() {
        // TODO
    }
}

private fun ResolvedUser.toUiModel(): ProximityViewModel.ProximityEncounterUiModel {
    return ProximityViewModel.ProximityEncounterUiModel(
        id = name,
        title = name,
        subtitle = null,
        firstSeenMillis = 0L,
        lastSeenMillis = 0L,
        isActive = true,
        totalDurationSeconds = 0L
    )
}