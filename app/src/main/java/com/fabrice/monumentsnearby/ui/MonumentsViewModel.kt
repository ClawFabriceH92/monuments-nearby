package com.fabrice.monumentsnearby.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fabrice.monumentsnearby.data.Monument
import com.fabrice.monumentsnearby.data.OverpassClient
import com.fabrice.monumentsnearby.data.WikidataClient
import com.fabrice.monumentsnearby.data.WikipediaClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface UiState {
    data object Idle : UiState
    data object Loading : UiState
    data class Success(val monuments: List<Monument>, val lat: Double, val lon: Double) : UiState
    data class Error(val message: String) : UiState
}

class MonumentsViewModel : ViewModel() {

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state

    fun load(lat: Double, lon: Double) {
        _state.value = UiState.Loading
        viewModelScope.launch {
            _state.value = try {
                val raw = OverpassClient.fetchMonuments(lat, lon)
                var enriched = WikidataClient.enrich(raw) // ontologie + types + photos
                enriched = WikipediaClient.enrich(enriched) // résumés d'articles
                UiState.Success(enriched, lat, lon)
            } catch (e: Exception) {
                UiState.Error(e.message ?: "Erreur inconnue")
            }
        }
    }

    fun onPermissionDenied() {
        _state.value = UiState.Error("Permission de localisation refusée — active-la dans les réglages.")
    }

    fun onLocationUnavailable() {
        _state.value = UiState.Error("Position introuvable. Vérifie que le GPS est activé et dehors/à la fenêtre.")
    }
}
