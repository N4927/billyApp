package com.example.billyapp.proximity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel centrale per la "radar view" (lista degli incontri Billy).
 *
 * Usa una astrazione [ProximityDataSource]; di default usiamo KmmProximityDataSource
 * che è implementata usando il KMM / BillyCore.
 */
class ProximityViewModel(
    private val dataSource: ProximityDataSource = KmmProximityDataSource()
) : ViewModel() {

    /**
     * Modello dati UI per un singolo incontro (prossimità).
     */
    data class ProximityEncounterUiModel(
        val id: String,
        val title: String?,
        val subtitle: String?,
        val firstSeenMillis: Long,
        val lastSeenMillis: Long,
        val isActive: Boolean,
        val totalDurationSeconds: Long
    )

    /**
     * Stato complessivo della schermata.
     */
    data class ProximityUiState(
        val encounters: List<ProximityEncounterUiModel> = emptyList(),
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
        val lastUpdatedMillis: Long? = null
    )

    private val _uiState = MutableStateFlow(ProximityUiState(isLoading = true))
    val uiState: StateFlow<ProximityUiState> = _uiState.asStateFlow()

    init {
        observeEncounters()
        refresh()
    }

    /**
     * Osserva il flusso di incontri proveniente dal dataSource.
     */
    private fun observeEncounters() {
        viewModelScope.launch {
            dataSource.encounters
                .catch { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = e.message ?: "Errore nel flusso incontri"
                        )
                    }
                }
                .collect { list ->
                    _uiState.update {
                        it.copy(
                            encounters = list,
                            isLoading = false,
                            errorMessage = null,
                            lastUpdatedMillis = System.currentTimeMillis()
                        )
                    }
                }
        }
    }

    /**
     * Chiamata tipicamente da:
     * - init {} (per avere subito dati)
     * - pull-to-refresh della UI
     */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                dataSource.refresh()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        lastUpdatedMillis = System.currentTimeMillis()
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Errore durante il refresh"
                    )
                }
            }
        }
    }

    /**
     * Opzionale: permette alla UI di pulire gli incontri (es. bottone "Clear").
     */
    fun clear() {
        viewModelScope.launch {
            try {
                dataSource.clear()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = e.message ?: "Errore durante clear()")
                }
            }
        }
    }
}

/**
 * Astrazione che rappresenta "tutto ciò che serve alla ViewModel per avere la lista degli incontri".
 */
interface ProximityDataSource {

    /**
     * Flusso reattivo di incontri già trasformati in [ProximityEncounterUiModel].
     */
    val encounters: Flow<List<ProximityViewModel.ProximityEncounterUiModel>>

    /**
     * Richiesta esplicita di "rileggere/sincronizzare" gli incontri.
     */
    suspend fun refresh()

    /**
     * Opzionale: usato per pulire lo stato (es. clear dell’active set).
     */
    suspend fun clear()
}
