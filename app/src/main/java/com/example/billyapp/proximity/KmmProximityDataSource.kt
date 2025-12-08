package com.example.billyapp.proximity

import com.billyapp.shared.domain.model.ResolvedUser
import com.example.billyapp.kmm.KmmEnvironment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Implementazione reale del datasource usando il KMM (BillyCore).
 */
class KmmProximityDataSource : ProximityDataSource {

    private val core get() = KmmEnvironment.core

    /**
     * Prende lo StateFlow dal KMM e lo mappa in UI models.
     */
    override val encounters: Flow<List<ProximityViewModel.ProximityEncounterUiModel>>
        get() = KmmEnvironment.resolvedUsersFlow
            .map { list -> list.map { it.toUiModel() } }

    override suspend fun refresh() {
        // per ora: forza solo una sync con il backend
        core.syncQueue()
    }

    override suspend fun clear() {
        // TODO: se in futuro BillyCore espone una API per svuotare l'active set, chiamala qui.
    }
}

/**
 * Conversione da ResolvedUser (KMM) a modello UI.
 *
 * Per ora NON usiamo i timestamp: mettiamo 0L e true come semplificazione,
 * così non servono dipendenze extra (kotlinx-datetime) nel modulo app.
 */
private fun ResolvedUser.toUiModel(): ProximityViewModel.ProximityEncounterUiModel {
    return ProximityViewModel.ProximityEncounterUiModel(
        id = name,               // chiave logica (per ora il nome)
        title = name,
        subtitle = null,
        firstSeenMillis = 0L,
        lastSeenMillis = 0L,
        isActive = true,
        totalDurationSeconds = 0L
    )
}
