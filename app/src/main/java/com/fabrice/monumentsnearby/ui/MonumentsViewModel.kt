package com.fabrice.monumentsnearby.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.fabrice.monumentsnearby.DailyDiscoveryWorker
import com.fabrice.monumentsnearby.data.GeocoderClient
import com.fabrice.monumentsnearby.data.Monument
import com.fabrice.monumentsnearby.data.MonumentCache
import com.fabrice.monumentsnearby.data.OverpassClient
import com.fabrice.monumentsnearby.data.VisitRepository
import com.fabrice.monumentsnearby.data.WikidataClient
import com.fabrice.monumentsnearby.data.WikipediaClient
import com.fabrice.monumentsnearby.location.GeofenceHelper
import com.fabrice.monumentsnearby.location.WalkTracker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit

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

class MonumentsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VisitRepository(application)
    private val geofenceHelper = GeofenceHelper(application)
    private val settingsPrefs = application.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Rayon de recherche « Autour de moi » en mètres (réglable, persisté). */
    private val _searchRadiusM = MutableStateFlow(settingsPrefs.getInt("searchRadiusM", 3000))
    val searchRadiusM: StateFlow<Int> = _searchRadiusM

    fun setSearchRadius(radiusM: Int) {
        _searchRadiusM.value = radiusM
        settingsPrefs.edit().putInt("searchRadiusM", radiusM).apply()
    }

    /** Visite guidée : lecture audio automatique à l'approche d'un monument. */
    private val _guidedVisit = MutableStateFlow(settingsPrefs.getBoolean("guidedVisit", false))
    val guidedVisit: StateFlow<Boolean> = _guidedVisit

    fun setGuidedVisit(enabled: Boolean) {
        _guidedVisit.value = enabled
        settingsPrefs.edit().putBoolean("guidedVisit", enabled).apply()
    }

    /** Monument du jour : notification quotidienne (10h) depuis le cache. */
    private val _dailyDiscovery = MutableStateFlow(settingsPrefs.getBoolean("dailyDiscovery", false))
    val dailyDiscovery: StateFlow<Boolean> = _dailyDiscovery

    fun setDailyDiscovery(enabled: Boolean) {
        _dailyDiscovery.value = enabled
        settingsPrefs.edit().putBoolean("dailyDiscovery", enabled).apply()
        val workManager = WorkManager.getInstance(getApplication<Application>())
        if (enabled) {
            val request = PeriodicWorkRequestBuilder<DailyDiscoveryWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(millisUntilHour(10), TimeUnit.MILLISECONDS)
                .build()
            workManager.enqueueUniquePeriodicWork(
                "daily-monument", ExistingPeriodicWorkPolicy.UPDATE, request
            )
        } else {
            workManager.cancelUniqueWork("daily-monument")
        }
    }

    /** Millisecondes jusqu'à la prochaine occurrence de [hour]:00. */
    private fun millisUntilHour(hour: Int): Long {
        val now = Calendar.getInstance()
        val next = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_YEAR, 1)
        }
        return next.timeInMillis - now.timeInMillis
    }

    /** Alerte géofencing active ? */
    private val _geofencesActive = MutableStateFlow(false)
    val geofencesActive: StateFlow<Boolean> = _geofencesActive

    /** Active/désactive l'alerte quand on s'approche d'un monument majeur. */
    fun toggleGeofences() {
        if (_geofencesActive.value) {
            geofenceHelper.stop()
            _geofencesActive.value = false
        } else {
            val current = _state.value as? UiState.Success ?: return
            val monuments = GeofenceHelper.selectMonuments(current.monuments)
            if (monuments.isEmpty()) return
            geofenceHelper.start(monuments)
            _geofencesActive.value = true
        }
    }

    /** Favoris du carnet. */
    private val _favorites = MutableStateFlow(repository.favorites())
    val favorites: StateFlow<List<com.fabrice.monumentsnearby.data.FavoriteEntry>> = _favorites

    /** Monuments marqués visités : id → (nom, date). */
    private val _visited = MutableStateFlow(repository.visited())
    val visited: StateFlow<Map<String, VisitRepository.VisitedEntry>> = _visited

    /** Notes personnelles : id → note. */
    private val _notes = MutableStateFlow(repository.notes())
    val notes: StateFlow<Map<String, String>> = _notes

    fun setNote(id: String, note: String?) {
        _notes.value = repository.setNote(id, note)
    }

    fun toggleFavorite(monument: Monument) {
        _favorites.value = repository.toggleFavorite(
            com.fabrice.monumentsnearby.data.FavoriteEntry(
                id = monument.id,
                name = monument.name,
                lat = monument.lat,
                lon = monument.lon,
                kind = monument.kind,
                imageUrl = monument.imageUrl,
                description = monument.description,
                wikidataId = monument.wikidataId
            )
        )
    }

    fun isFavorite(id: String): Boolean = repository.isFavorite(id)

    fun toggleVisited(monument: Monument) {
        _visited.value = repository.toggleVisited(monument.id, monument.name)
    }

    fun isVisited(id: String): Boolean = repository.isVisited(id)

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

    /** Images de la galerie Commons du monument sélectionné. */
    private val _monumentImages = MutableStateFlow<List<String>>(emptyList())
    val monumentImages: StateFlow<List<String>> = _monumentImages

    private val _loadingImages = MutableStateFlow(false)
    val loadingImages: StateFlow<Boolean> = _loadingImages

    /** Dernier résultat « autour de moi » (monuments GPS) — conservé quand on
     *  passe en mode musée/ville, pour ne pas perdre la vue position. */
    private val _lastMonuments = MutableStateFlow<UiState.Success?>(null)
    val lastMonuments: StateFlow<UiState.Success?> = _lastMonuments

    /**
     * Retrouve un monument par son id dans les résultats connus — sert au
     * pop-up de proximité, qui ne reçoit du geofence que l'identifiant.
     */
    fun findMonument(id: String): Monument? {
        val fromState = (_state.value as? UiState.Success)?.monuments
            ?.firstOrNull { it.id == id }
        return fromState ?: _lastMonuments.value?.monuments?.firstOrNull { it.id == id }
    }

    /** Monuments autour de la position GPS (mode MONUMENTS). */
    fun load(lat: Double, lon: Double) {
        _state.value = UiState.Loading
        viewModelScope.launch {
            val result = try {
                val raw = OverpassClient.fetchMonuments(lat, lon, radiusM = _searchRadiusM.value)
                var enriched = WikidataClient.enrich(raw) // ontologie + types + photos
                enriched = WikipediaClient.enrich(enriched) // résumés d'articles
                MonumentCache.save(
                    getApplication<Application>(), enriched, lat, lon, "Monuments à proximité"
                )
                UiState.Success(enriched, lat, lon, AppMode.MONUMENTS, "Monuments à proximité")
            } catch (e: Exception) {
                // Réseau KO → derniers résultats en cache plutôt qu'une erreur sèche
                cachedFallback() ?: UiState.Error(e.message ?: "Erreur inconnue")
            }
            if (result is UiState.Success) _lastMonuments.value = result
            _state.value = result
        }
    }

    /** Derniers résultats en cache, présentés comme état hors-ligne. */
    private fun cachedFallback(): UiState.Success? =
        MonumentCache.load(getApplication<Application>())?.let {
            UiState.Success(
                it.monuments, it.lat, it.lon, AppMode.MONUMENTS,
                "${it.title} (hors-ligne)"
            )
        }

    private var searchJob: Job? = null

    /**
     * Recherche un musée par nom (barre de recherche du mode Musée).
     * Debounce 300 ms + annulation de la recherche précédente : sans cela,
     * chaque frappe part en requête et les réponses arrivées dans le désordre
     * peuvent écraser les résultats de la requête la plus récente.
     */
    fun searchMuseums(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _museumResults.value = emptyList()
            _searching.value = false
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            _searching.value = true
            try {
                _museumResults.value = WikidataClient.searchMuseums(query)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _museumResults.value = emptyList()
            } finally {
                _searching.value = false
            }
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
                // Guide Wikivoyage (non bloquant)
                _cityGuide.value = try {
                    WikipediaClient.fetchWikivoyageSummary(city.name)?.let { city.name to it }
                } catch (e: Exception) {
                    null
                }
                MonumentCache.save(
                    getApplication<Application>(), enriched, city.lat, city.lon, "Ville : ${city.name}"
                )
                UiState.Success(enriched, city.lat, city.lon, AppMode.CITY, "Ville : ${city.name}")
            } catch (e: Exception) {
                UiState.Error(e.message ?: "Erreur inconnue")
            }
        }
    }

    /** Guide Wikivoyage de la dernière ville chargée : (ville, extrait). */
    private val _cityGuide = MutableStateFlow<Pair<String, String>?>(null)
    val cityGuide: StateFlow<Pair<String, String>?> = _cityGuide

    /** Musées visibles autour du centre de la carte (bouton « musées de la zone »). */
    fun loadMuseumsInZone(lat: Double, lon: Double, radiusM: Int = 3000) {
        _state.value = UiState.Loading
        viewModelScope.launch {
            _state.value = try {
                val raw = OverpassClient.fetchMuseums(lat, lon, radiusM)
                var enriched = WikidataClient.enrich(raw)
                enriched = WikipediaClient.enrich(enriched)
                UiState.Success(enriched, lat, lon, AppMode.MUSEUM, "Musées dans la zone")
            } catch (e: Exception) {
                UiState.Error(e.message ?: "Erreur inconnue")
            }
        }
    }

    private var imagesJob: Job? = null

    /**
     * Charge la galerie Commons du monument sélectionné (P373 → catégorie).
     * Annule le chargement précédent : si on ferme la fiche A et ouvre la
     * fiche B pendant le chargement, B n'affichera pas les photos de A.
     */
    fun loadMonumentImages(monument: Monument) {
        imagesJob?.cancel()
        _monumentImages.value = emptyList()
        _loadingImages.value = true
        imagesJob = viewModelScope.launch {
            val images = try {
                // 1) Images directes de la catégorie Commons (P373)
                val category = monument.commonsCategory
                val fromCategory = category?.let { WikidataClient.fetchCommonsCategoryImages(it) }
                    ?: emptyList()
                // 2) Plan B : recherche d'images par titre (Wikipédia ou nom)
                if (fromCategory.isNotEmpty()) {
                    fromCategory
                } else {
                    val query = monument.wikipediaTitle ?: monument.name
                    WikidataClient.fetchCommonsSearchImages(query)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            _monumentImages.value = images
            _loadingImages.value = false
        }
    }

    fun clearMonumentImages() {
        imagesJob?.cancel()
        _monumentImages.value = emptyList()
        _loadingImages.value = false
    }

    fun onPermissionDenied() {
        _state.value = UiState.Error("Permission de localisation refusée — active-la dans les réglages.")
    }

    fun onLocationUnavailable() {
        // Sans position, montrer au moins les derniers résultats en cache
        val fallback = cachedFallback()
        if (fallback != null) {
            _lastMonuments.value = fallback
            _state.value = fallback
        } else {
            _state.value = UiState.Error(
                "Position introuvable. Vérifie que le GPS est activé et dehors/à la fenêtre."
            )
        }
    }

    // ---------------------------------------------------------------
    // Balade guidée — suivi GPS actif, lecture audio à chaque étape
    // ---------------------------------------------------------------

    /** Balade guidée en cours : étapes, prochaine étape, distance restante. */
    data class GuidedWalk(
        val stops: List<WalkStop>,
        val nextIndex: Int,
        val distanceToNextM: Double? = null
    )

    /** Étape atteinte — consommée par l'UI qui lance la lecture audio. */
    data class WalkArrival(
        val monument: Monument,
        val stepIndex: Int,
        val totalSteps: Int,
        val lastStep: Boolean
    )

    private val walkTracker = WalkTracker(application)

    private val _guidedWalk = MutableStateFlow<GuidedWalk?>(null)
    val guidedWalk: StateFlow<GuidedWalk?> = _guidedWalk

    private val _walkArrival = MutableStateFlow<WalkArrival?>(null)
    val walkArrival: StateFlow<WalkArrival?> = _walkArrival

    fun consumeWalkArrival() {
        _walkArrival.value = null
    }

    /**
     * Démarre la balade guidée : la position est suivie en continu et
     * l'arrivée à moins de [WALK_ARRIVAL_M] d'une étape émet [walkArrival].
     * Retourne false si la localisation précise n'est pas disponible.
     */
    fun startGuidedWalk(stops: List<WalkStop>): Boolean {
        if (stops.isEmpty()) return false
        val started = walkTracker.start { lat, lon -> onWalkLocation(lat, lon) }
        if (started) {
            _walkArrival.value = null
            _guidedWalk.value = GuidedWalk(stops, nextIndex = 0)
        }
        return started
    }

    fun stopGuidedWalk() {
        walkTracker.stop()
        _guidedWalk.value = null
        _walkArrival.value = null
    }

    private fun onWalkLocation(lat: Double, lon: Double) {
        val walk = _guidedWalk.value ?: return
        val stop = walk.stops.getOrNull(walk.nextIndex) ?: return
        val distance = haversineM(lat, lon, stop.monument.lat, stop.monument.lon)
        if (distance <= WALK_ARRIVAL_M) {
            val last = walk.nextIndex == walk.stops.lastIndex
            _walkArrival.value = WalkArrival(
                monument = stop.monument,
                stepIndex = walk.nextIndex,
                totalSteps = walk.stops.size,
                lastStep = last
            )
            if (last) {
                // Fin de balade : on coupe le suivi, l'UI lit la dernière étape
                walkTracker.stop()
                _guidedWalk.value = null
            } else {
                _guidedWalk.value = walk.copy(
                    nextIndex = walk.nextIndex + 1,
                    distanceToNextM = null
                )
            }
        } else {
            _guidedWalk.value = walk.copy(distanceToNextM = distance)
        }
    }

    override fun onCleared() {
        walkTracker.stop()
        super.onCleared()
    }

    private companion object {
        /** Rayon d'arrivée à une étape — sous 40 m, le GPS urbain divague. */
        const val WALK_ARRIVAL_M = 40.0
    }

    // ---------------------------------------------------------------
    // Itinéraire de balade — chaîne des monuments les plus proches
    // ---------------------------------------------------------------

    /** Une étape de la balade. */
    data class WalkStop(
        val monument: Monument,
        val stepM: Double,
        val cumulativeM: Double
    )

    /**
     * Construit un itinéraire en chaîne (plus proche voisin) depuis une position.
     * Priorité aux monuments majeurs, plafonné à [maxStops] étapes.
     */
    fun buildWalk(
        monuments: List<Monument>,
        startLat: Double,
        startLon: Double,
        maxStops: Int = 8
    ): List<WalkStop> {
        val pool = (
            monuments.filter { it.important } + monuments.filter { !it.important }
            ).distinctBy { it.id }.take(24)
        if (pool.isEmpty()) return emptyList()

        val remaining = pool.toMutableList()
        var curLat = startLat
        var curLon = startLon
        var total = 0.0
        val stops = mutableListOf<WalkStop>()
        while (remaining.isNotEmpty() && stops.size < maxStops) {
            val next = remaining.minByOrNull { haversineM(curLat, curLon, it.lat, it.lon) }!!
            remaining.remove(next)
            val step = haversineM(curLat, curLon, next.lat, next.lon)
            total += step
            stops.add(WalkStop(next, step, total))
            curLat = next.lat
            curLon = next.lon
        }
        return stops
    }

    private fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        return 2 * r * Math.asin(Math.sqrt(a))
    }
}
