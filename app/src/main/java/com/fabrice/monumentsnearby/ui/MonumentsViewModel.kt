package com.fabrice.monumentsnearby.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fabrice.monumentsnearby.data.GeocoderClient
import com.fabrice.monumentsnearby.data.Monument
import com.fabrice.monumentsnearby.data.OverpassClient
import com.fabrice.monumentsnearby.data.WikidataClient
import com.fabrice.monumentsnearby.data.WikipediaClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Mode d'affichage principal de l'application. */
enum class AppMode { MONUMENTS, MUSEUM, CITY }

sealed interface UiState {
    data object Idle : UiState
    data object Loading : UiState
    data class Success(
        val monuments: List<Monument>,
        val lat: Double,
        val lon: Double,
        val mode: AppMode = AppMode.MONUMENTS,
        val title: String = "Monuments à proximité"
    ) : UiState
    data class Error(val message: String) : UiState
}

/** Résultat du sélecteur « musées par ville ». */
data class CityMuseums(
    val cityName: String,
    val lat: Double,
    val lon: Double,
    val museums: List<WikidataClient.Museum>
)

class MonumentsViewModel : ViewModel() {

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state

    /** Résultats de la recherche de musée par nom (barre de recherche). */
    private val _museumResults = MutableStateFlow<List<WikidataClient.Museum>>(emptyList())
    val museumResults: StateFlow<List<WikidataClient.Museum>> = _museumResults

    /** Musées trouvés pour une ville (sélecteur « musées par ville »). */
    private val _cityMuseums = MutableStateFlow<CityMuseums?>(null)
    val cityMuseums: StateFlow<CityMuseums?> = _cityMuseums

    /** Recherche en cours (sélecteurs musée/ville). */
    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching

    /** Monuments autour de la position GPS (mode MONUMENTS). */
    fun load(lat: Double, lon: Double) {
        _state.value = UiState.Loading
        viewModelScope.launch {
            _state.value = try {
                val raw = OverpassClient.fetchMonuments(lat, lon)
                var enriched = WikidataClient.enrich(raw) // ontologie + types + photos
                enriched = WikipediaClient.enrich(enriched) // résumés d'articles
                UiState.Success(enriched, lat, lon, AppMode.MONUMENTS, "Monuments à proximité")
            } catch (e: Exception) {
                UiState.Error(e.message ?: "Erreur inconnue")
            }
        }
    }

    /** Recherche un musée par nom (barre de recherche du mode Musée). */
    fun searchMuseums(query: String) {
        viewModelScope.launch {
            _searching.value = true
            _museumResults.value = try {
                WikidataClient.searchMuseums(query)
            } catch (e: Exception) {
                emptyList()
            }
            _searching.value = false
        }
    }

    fun clearMuseumResults() {
        _museumResults.value = emptyList()
    }

    /** Charge les œuvres d'un musée (mode MUSEUM). */
    fun loadMuseumArtworks(museum: WikidataClient.Museum) {
        _state.value = UiState.Loading
        viewModelScope.launch {
            _state.value = try {
                var works = WikidataClient.fetchMuseumArtworks(museum.qid)
                works = WikipediaClient.enrich(works)
                val lat = museum.lat ?: 0.0
                val lon = museum.lon ?: 0.0
                val placed = works.map { it.copy(lat = lat, lon = lon, distanceM = 0.0) }
                UiState.Success(placed, lat, lon, AppMode.MUSEUM, museum.name)
            } catch (e: Exception) {
                UiState.Error(e.message ?: "Erreur inconnue")
            }
        }
    }

    /** Musées d'une ville (géocodage + Overpass) — sélecteur avant le choix. */
    fun loadMuseumsInCity(cityName: String) {
        _searching.value = true
        viewModelScope.launch {
            try {
                val city = GeocoderClient.geocodeCity(cityName)
                if (city == null) {
                    _cityMuseums.value = CityMuseums(cityName, 0.0, 0.0, emptyList())
                } else {
                    val raw = OverpassClient.fetchMuseums(city.lat, city.lon)
                    val enriched = WikidataClient.enrich(raw)
                    val museums = enriched
                        .filter { !it.wikidataId.isNullOrBlank() }
                        .map {
                            WikidataClient.Museum(
                                qid = it.wikidataId!!,
                                name = it.name,
                                description = it.description,
                                imageUrl = it.imageUrl,
                                lat = it.lat,
                                lon = it.lon
                            )
                        }
                    _cityMuseums.value = CityMuseums(city.name, city.lat, city.lon, museums)
                }
            } catch (e: Exception) {
                _cityMuseums.value = CityMuseums(cityName, 0.0, 0.0, emptyList())
            }
            _searching.value = false
        }
    }

    /** Monuments d'une ville (mode CITY) : géocodage + Overpass rayon élargi. */
    fun loadCity(cityName: String) {
        _state.value = UiState.Loading
        viewModelScope.launch {
            _state.value = try {
                val city = GeocoderClient.geocodeCity(cityName)
                    ?: throw RuntimeException(
                        "Ville introuvable — vérifie le nom (ex: Paris, Asnières-sur-Seine)."
                    )
                val raw = OverpassClient.fetchMonuments(city.lat, city.lon, radiusM = 6000)
                var enriched = WikidataClient.enrich(raw)
                enriched = WikipediaClient.enrich(enriched)
                UiState.Success(enriched, city.lat, city.lon, AppMode.CITY, "Ville : ${city.name}")
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
