package com.fabrice.monumentsnearby.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.fabrice.monumentsnearby.BuildConfig
import com.fabrice.monumentsnearby.data.Monument
import com.fabrice.monumentsnearby.data.WikidataClient
import com.fabrice.monumentsnearby.data.category
import com.fabrice.monumentsnearby.tts.GuideSpeaker
import com.fabrice.monumentsnearby.ui.theme.CategoryColors
import com.fabrice.monumentsnearby.update.UpdateManager
import kotlin.math.roundToInt

enum class AppTab { AROUND, MUSEUMS, CITY, BOOK }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonumentsScreen(
    state: UiState,
    viewModel: MonumentsViewModel,
    onLocate: () -> Unit,
    onToggleGeofences: () -> Unit = { viewModel.toggleGeofences() }
) {
    val context = LocalContext.current
    val speaker = remember { GuideSpeaker(context) }

    var selectedTab by remember { mutableStateOf(AppTab.AROUND) }
    var showMap by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var selectedMonument by remember { mutableStateOf<Monument?>(null) }
    val paused = remember { mutableStateOf(false) }
    val speakerActive = remember { mutableStateOf(false) }
    var selectedMuseum by remember { mutableStateOf<WikidataClient.Museum?>(null) }
    var showMuseumSearch by remember { mutableStateOf(true) }
    var showCamera by remember { mutableStateOf(false) }
    var cameraState by remember { mutableStateOf<UiState.Success?>(null) }

    var lastTitle by remember { mutableStateOf("Monuments à proximité") }
    val success = state as? UiState.Success
    SideEffect {
        if (success != null) lastTitle = success.title
    }
    val isMuseumMode = success?.mode == AppMode.MUSEUM

    DisposableEffect(Unit) {
        speaker.onFinished = {
            paused.value = false
            speakerActive.value = false
        }
        onDispose { speaker.shutdown() }
    }

    if (selectedMonument != null) {
        MonumentDetailScreen(
            monument = selectedMonument!!,
            viewModel = viewModel,
            onClose = {
                selectedMonument = null
                viewModel.clearMonumentImages()
            },
            onListen = {
                speakerActive.value = true
                speaker.speak(guideText(selectedMonument!!))
            },
            onViewWorks = {
                val qid = selectedMonument!!.wikidataId ?: return@MonumentDetailScreen
                val museum = WikidataClient.Museum(
                    qid = qid,
                    name = selectedMonument!!.name,
                    description = selectedMonument!!.description,
                    imageUrl = selectedMonument!!.imageUrl,
                    lat = selectedMonument!!.lat,
                    lon = selectedMonument!!.lon
                )
                selectedMuseum = museum
                selectedTab = AppTab.MUSEUMS
                showMuseumSearch = false
                selectedMonument = null
                viewModel.clearMonumentImages()
                viewModel.loadMuseumArtworks(museum)
            },
            lectureBar = {
                if (speakerActive.value) {
                    LectureBar(
                        paused = paused.value,
                        onTogglePause = { paused.value = speaker.togglePause() },
                        onStop = { speaker.stop() }
                    )
                }
            }
        )
    } else if (showCamera) {
        cameraState?.let { cam ->
            CameraScreen(
                monuments = cam.monuments,
                lat = cam.lat,
                lon = cam.lon,
                onClose = { showCamera = false },
                onQrFound = { qid ->
                    cam.monuments.find { it.wikidataId == qid }?.let { selectedMonument = it }
                    showCamera = false
                }
            )
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            when (selectedTab) {
                                AppTab.AROUND -> lastTitle
                                AppTab.MUSEUMS -> if (selectedMuseum != null) selectedMuseum!!.name else "Musées"
                                AppTab.CITY -> "Ville"
                                AppTab.BOOK -> "Carnet"
                            }
                        )
                    },
                    colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = Color.White
                    ),
                    actions = {
                        // Alerte monuments à proximité (géofencing)
                        val geofencesActive by viewModel.geofencesActive.collectAsStateWithLifecycle()
                        TextButton(
                            onClick = onToggleGeofences,
                            modifier = Modifier.semantics {
                                contentDescription = if (geofencesActive) {
                                    "Désactiver l'alerte de proximité"
                                } else {
                                    "Activer l'alerte de proximité"
                                }
                            }
                        ) {
                            Text(if (geofencesActive) "🔔" else "🔕")
                        }
                        // Réglages (recherche, mises à jour, audioguide)
                        TextButton(
                            onClick = { showSettingsDialog = true },
                            modifier = Modifier.semantics {
                                contentDescription = "Réglages"
                            }
                        ) {
                            Text("⚙️")
                        }
                    }
                )
            },
            bottomBar = {
                Column {
                    // Barre de lecture flottante, visible seulement pendant l'écoute
                    if (speakerActive.value) {
                        LectureBar(
                            paused = paused.value,
                            onTogglePause = { paused.value = speaker.togglePause() },
                            onStop = { speaker.stop() }
                        )
                    }
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        NavigationBarItem(
                            selected = selectedTab == AppTab.AROUND,
                            onClick = { selectedTab = AppTab.AROUND },
                            icon = { Text("📍") },
                            label = { Text("Autour de moi") }
                        )
                        NavigationBarItem(
                            selected = selectedTab == AppTab.MUSEUMS,
                            onClick = { selectedTab = AppTab.MUSEUMS },
                            icon = { Text("🏛") },
                            label = { Text("Musées") }
                        )
                        NavigationBarItem(
                            selected = selectedTab == AppTab.CITY,
                            onClick = { selectedTab = AppTab.CITY },
                            icon = { Text("🏙") },
                            label = { Text("Ville") }
                        )
                        NavigationBarItem(
                            selected = selectedTab == AppTab.BOOK,
                            onClick = { selectedTab = AppTab.BOOK },
                            icon = { Text("📒") },
                            label = { Text("Carnet") }
                        )
                    }
                }
            },
            floatingActionButton = {
                if (selectedTab == AppTab.AROUND && showMap && !isMuseumMode) {
                    ExtendedFloatingActionButton(
                        text = { Text("🏛 Musées ici") },
                        icon = {},
                        onClick = {
                            val s = state as? UiState.Success ?: return@ExtendedFloatingActionButton
                            viewModel.loadMuseumsInZone(s.lat, s.lon)
                            showMap = false
                            selectedTab = AppTab.MUSEUMS
                            showMuseumSearch = false
                            selectedMuseum = null
                        }
                    )
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                when (selectedTab) {
                    AppTab.AROUND -> AroundContent(
                        state = state,
                        viewModel = viewModel,
                        speaker = speaker,
                        speakerActive = speakerActive,
                        context = context,
                        showMap = showMap,
                        onToggleMap = { showMap = !showMap },
                        onOpenMap = { showMap = true },
                        onSelectMonument = { selectedMonument = it },
                        onLocate = onLocate,
                        onOpenCamera = {
                            val s = (state as? UiState.Success)
                                ?.takeIf { it.mode == AppMode.MONUMENTS }
                                ?: viewModel.lastMonuments.value
                            if (s != null) {
                                cameraState = s
                                showCamera = true
                            }
                        }
                    )
                    AppTab.MUSEUMS -> MuseumContent(
                        state = state,
                        viewModel = viewModel,
                        showMuseumSearch = showMuseumSearch,
                        onShowMuseumSearch = { showMuseumSearch = true },
                        onSelectMuseum = { museum ->
                            selectedMuseum = museum
                            showMuseumSearch = false
                            viewModel.loadMuseumArtworks(museum)
                        },
                        onSelectMonument = { selectedMonument = it },
                        onListen = {
                            speakerActive.value = true
                            speaker.speak(guideText(it))
                        },
                        onNavigate = { openMaps(context, it) }
                    )
                    AppTab.CITY -> CityContent(
                        state = state,
                        viewModel = viewModel,
                        onSelectMonument = { selectedMonument = it },
                        onListen = {
                            speakerActive.value = true
                            speaker.speak(guideText(it))
                        },
                        onNavigate = { openMaps(context, it) }
                    )
                    AppTab.BOOK -> BookContent(
                        viewModel = viewModel,
                        onSelectMonument = { selectedMonument = it },
                        onListen = {
                            speakerActive.value = true
                            speaker.speak(guideText(it))
                        },
                        onNavigate = { openMaps(context, it) }
                    )
                }
            }
        }
    }

    if (showSettingsDialog) {
        SettingsDialog(
            speaker = speaker,
            viewModel = viewModel,
            onDismiss = { showSettingsDialog = false }
        )
    }
}

/**
 * Barre de lecture flottante de l'audioguide : pause/reprise + arrêt.
 * Affichée seulement quand une lecture est active ou en pause.
 */
@Composable
private fun LectureBar(
    paused: Boolean,
    onTogglePause: () -> Unit,
    onStop: () -> Unit
) {
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("🔊 Audioguide", style = MaterialTheme.typography.labelLarge)
            Row {
                TextButton(onClick = onTogglePause) {
                    Text(if (paused) "▶ Reprendre" else "⏸ Pause")
                }
                TextButton(onClick = onStop) { Text("✕ Arrêter") }
            }
        }
    }
}

/**
 * Pastilles de filtre par catégorie de monument.
 */
@Composable
private fun FilterBar(selected: String?, onSelect: (String?) -> Unit) {
    val categories = listOf("musée", "religieux", "château", "ruines", "monument", "autre")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text("Tous") }
        )
        categories.forEach { cat ->
            FilterChip(
                selected = selected == cat,
                onClick = { onSelect(if (selected == cat) null else cat) },
                label = { Text(cat.replaceFirstChar { it.uppercase() }) }
            )
        }
    }
}

@Composable
private fun AroundContent(
    state: UiState,
    viewModel: MonumentsViewModel,
    speaker: GuideSpeaker,
    speakerActive: MutableState<Boolean>,
    context: Context,
    showMap: Boolean,
    onToggleMap: () -> Unit,
    onOpenMap: () -> Unit,
    onSelectMonument: (Monument) -> Unit,
    onLocate: () -> Unit,
    onOpenCamera: () -> Unit
) {
    val lastMonuments by viewModel.lastMonuments.collectAsStateWithLifecycle()
    // Si le state courant est musée/ville, on montre le dernier résultat « autour de moi »
    val effective = when (state) {
        is UiState.Success ->
            if (state.mode == AppMode.MONUMENTS) state else lastMonuments
        else -> null
    }
    var filter by remember { mutableStateOf<String?>(null) }
    var showWalk by remember { mutableStateOf(false) }
    // Balade affichée sur la carte (null = pas de tracé)
    var walkStops by remember { mutableStateOf<List<MonumentsViewModel.WalkStop>?>(null) }

    when {
        effective != null -> {
            if (effective.monuments.isEmpty()) {
                CenteredMessage("Aucun monument trouvé à proximité.") {
                    Button(onClick = onLocate) { Text("📍 Réessayer") }
                }
            } else if (showMap) {
                Box(Modifier.fillMaxSize()) {
                    MonumentsMap(
                        monuments = effective.monuments,
                        centerLat = effective.lat,
                        centerLon = effective.lon,
                        onSelectMonument = onSelectMonument,
                        walkRoute = walkStops?.map { it.monument }
                    )
                    // La carte n'avait aucun bouton de retour vers la liste
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(onClick = onToggleMap) { Text("📋 Liste") }
                        if (walkStops != null) {
                            Button(onClick = { walkStops = null }) { Text("✕ Itinéraire") }
                        }
                    }
                }
            } else {
                val visible = if (filter == null) effective.monuments
                else effective.monuments.filter { it.category() == filter }
                val majors = visible.filter { it.important }
                val others = visible.filter { !it.important }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        FilterBar(selected = filter, onSelect = { filter = it })
                    }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${visible.size} monuments trouvés",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Row {
                                TextButton(
                                    onClick = { onOpenCamera() },
                                    modifier = Modifier.semantics {
                                        contentDescription = "Ouvrir la caméra (mode AR et scanner QR)"
                                    }
                                ) { Text("📷") }
                                TextButton(onClick = { showWalk = true }) { Text("🥾 Balade") }
                                TextButton(onClick = onToggleMap) {
                                    Text(if (showMap) "📋 Liste" else "🗺️ Carte")
                                }
                            }
                        }
                    }
                    if (majors.isNotEmpty()) {
                        item { SectionHeader("⭐ Monuments majeurs") }
                        items(majors, key = { it.id }) { monument ->
                            MonumentCard(
                                monument = monument,
                                onListen = {
                                    speakerActive.value = true
                                    speaker.speak(guideText(monument))
                                },
                                onNavigate = { openMaps(context, monument) },
                                onCardClick = { onSelectMonument(monument) }
                            )
                        }
                    }
                    if (others.isNotEmpty()) {
                        item { SectionHeader("Autres monuments") }
                        items(others, key = { it.id }) { monument ->
                            MonumentCard(
                                monument = monument,
                                onListen = {
                                    speakerActive.value = true
                                    speaker.speak(guideText(monument))
                                },
                                onNavigate = { openMaps(context, monument) },
                                onCardClick = { onSelectMonument(monument) }
                            )
                        }
                    }
                }
            }
        }
        state is UiState.Loading -> Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator()
            Text("Recherche…")
        }
        state is UiState.Error -> CenteredMessage(state.message) {
            Button(onClick = onLocate) { Text("Réessayer") }
        }
        else -> CenteredMessage("Appuie pour détecter les monuments autour de toi") {
            Button(onClick = onLocate) { Text("📍 Détecter ma position") }
        }
    }

    if (showWalk && effective != null) {
        WalkDialog(
            stops = viewModel.buildWalk(effective.monuments, effective.lat, effective.lon),
            onDismiss = { showWalk = false },
            onNavigate = { openMaps(context, it) },
            onShowOnMap = { stops ->
                walkStops = stops
                showWalk = false
                onOpenMap()
            }
        )
    }
}

/**
 * Dialog de balade : chaîne des monuments les plus proches, étape par étape.
 */
@Composable
private fun WalkDialog(
    stops: List<MonumentsViewModel.WalkStop>,
    onDismiss: () -> Unit,
    onNavigate: (Monument) -> Unit,
    onShowOnMap: (List<MonumentsViewModel.WalkStop>) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🥾 Balade à pied") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Chaîne des monuments les plus proches — touche une étape pour l'itinéraire.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                stops.forEachIndexed { index, stop ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigate(stop.monument) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${index + 1}.",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stop.monument.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${formatDistance(stop.stepM)} · total ${formatDistance(stop.cumulativeM)} (~${(stop.cumulativeM / 80).roundToInt()} min)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onShowOnMap(stops) },
                enabled = stops.isNotEmpty()
            ) { Text("🗺️ Sur la carte") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Fermer") }
        }
    )
}

@Composable
private fun MuseumContent(
    state: UiState,
    viewModel: MonumentsViewModel,
    showMuseumSearch: Boolean,
    onShowMuseumSearch: () -> Unit,
    onSelectMuseum: (WikidataClient.Museum) -> Unit,
    onSelectMonument: (Monument) -> Unit,
    onListen: (Monument) -> Unit,
    onNavigate: (Monument) -> Unit
) {
    if (showMuseumSearch) {
        MuseumSearchForm(
            viewModel = viewModel,
            onSelectMuseum = onSelectMuseum
        )
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onShowMuseumSearch) {
                    Text("← Retour à la recherche")
                }
                Spacer(Modifier.weight(1f))
            }
            when (state) {
                is UiState.Loading -> Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Text("Chargement…")
                }
                is UiState.Error -> CenteredMessage(state.message) {
                    Button(onClick = onShowMuseumSearch) { Text("Retour") }
                }
                is UiState.Success -> {
                    if (state.monuments.isEmpty()) {
                        CenteredMessage("Aucune œuvre trouvée pour ce musée.") {
                            Button(onClick = onShowMuseumSearch) { Text("Retour") }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item {
                                Text(
                                    "${state.monuments.size} œuvres trouvées",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                            items(state.monuments, key = { it.id }) { monument ->
                                MonumentCard(
                                    monument = monument,
                                    onListen = { onListen(monument) },
                                    onNavigate = { onNavigate(monument) },
                                    onCardClick = { onSelectMonument(monument) }
                                )
                            }
                        }
                    }
                }
                else -> CenteredMessage("Chargement…") {}
            }
        }
    }
}

@Composable
private fun MuseumSearchForm(
    viewModel: MonumentsViewModel,
    onSelectMuseum: (WikidataClient.Museum) -> Unit
) {
    var cityMode by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var cityQuery by remember { mutableStateOf("") }
    val searching by viewModel.searching.collectAsStateWithLifecycle()
    val museumResults by viewModel.museumResults.collectAsStateWithLifecycle()
    val cityMuseums by viewModel.cityMuseums.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        if (!cityMode) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    viewModel.searchMuseums(it)
                },
                label = { Text("Rechercher un musée") },
                placeholder = { Text("Louvre, Orsay, Pompidou…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            if (searching) {
                SearchRow()
            } else {
                museumResults.forEach { museum ->
                    MuseumResultButton(museum, onClick = { onSelectMuseum(museum) })
                }
                if (searchQuery.isNotBlank() && museumResults.isEmpty()) {
                    Text(
                        "Aucun musée trouvé pour « $searchQuery ».",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            TextButton(
                onClick = { cityMode = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🏙️ Voir les musées d'une ville")
            }
        } else {
            OutlinedTextField(
                value = cityQuery,
                onValueChange = { cityQuery = it },
                label = { Text("Nom de la ville") },
                placeholder = { Text("Paris, Asnières-sur-Seine…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { viewModel.loadMuseumsInCity(cityQuery) },
                enabled = cityQuery.isNotBlank() && !searching,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Rechercher les musées") }
            Spacer(Modifier.height(8.dp))
            if (searching) {
                SearchRow()
            } else {
                val cm = cityMuseums
                if (cm != null) {
                    Text(
                        "${cm.museums.size} musées à ${cm.cityName}",
                        style = MaterialTheme.typography.labelMedium
                    )
                    if (cm.museums.isEmpty()) {
                        Text(
                            "Aucun musée indexé (avec identifiant Wikidata) trouvé.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    cm.museums.forEach { museum ->
                        MuseumResultButton(museum, onClick = { onSelectMuseum(museum) })
                    }
                }
            }
            TextButton(
                onClick = { cityMode = false },
                modifier = Modifier.fillMaxWidth()
            ) { Text("← Recherche par nom") }
        }
    }
}

@Composable
private fun CityContent(
    state: UiState,
    viewModel: MonumentsViewModel,
    onSelectMonument: (Monument) -> Unit,
    onListen: (Monument) -> Unit,
    onNavigate: (Monument) -> Unit
) {
    var cityQuery by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<String?>(null) }
    val cityGuide by viewModel.cityGuide.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        cityGuide?.let { (city, extract) ->
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "🌍 Guide Wikivoyage — $city",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(extract, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item {
            Text(
                "Afficher les monuments d'une ville (rayon 6 km autour du centre).",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        item {
            OutlinedTextField(
                value = cityQuery,
                onValueChange = { cityQuery = it },
                label = { Text("Nom de la ville") },
                placeholder = { Text("Versailles, Lyon, Asnières…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Button(
                onClick = { viewModel.loadCity(cityQuery.trim()) },
                enabled = cityQuery.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Afficher") }
        }

        when (state) {
            is UiState.Loading -> item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Recherche…")
                }
            }
            is UiState.Error -> item {
                CenteredMessage(state.message) {}
            }
            is UiState.Success -> {
                if (state.mode == AppMode.CITY) {
                    val cityName = state.title.removePrefix("Ville : ")
                    val visible = if (filter == null) state.monuments
                    else state.monuments.filter { it.category() == filter }
                    item {
                        Text(
                            "${visible.size} monuments à $cityName",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    item {
                        FilterBar(selected = filter, onSelect = { filter = it })
                    }
                    items(visible, key = { it.id }) { monument ->
                        MonumentCard(
                            monument = monument,
                            onListen = { onListen(monument) },
                            onNavigate = { onNavigate(monument) },
                            onCardClick = { onSelectMonument(monument) }
                        )
                    }
                }
            }
            else -> {}
        }
    }
}

/**
 * Carnet de visites : favoris (rouvrables) + monuments visités.
 */
@Composable
private fun BookContent(
    viewModel: MonumentsViewModel,
    onSelectMonument: (Monument) -> Unit,
    onListen: (Monument) -> Unit,
    onNavigate: (Monument) -> Unit
) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val visited by viewModel.visited.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (favorites.isEmpty() && visited.isEmpty()) {
            item {
                CenteredMessage(
                    "Ton carnet est vide.\nMarque un monument ★ favori ou ✓ visité depuis sa fiche."
                ) {}
            }
            return@LazyColumn
        }
        if (favorites.isNotEmpty()) {
            item { SectionHeader("⭐ Favoris (${favorites.size})") }
            items(favorites, key = { "fav_${it.id}" }) { fav ->
                val monument = Monument(
                    id = fav.id,
                    name = fav.name,
                    lat = fav.lat,
                    lon = fav.lon,
                    distanceM = 0.0,
                    kind = fav.kind,
                    description = fav.description,
                    imageUrl = fav.imageUrl,
                    wikidataId = fav.wikidataId
                )
                MonumentCard(
                    monument = monument,
                    onListen = { onListen(monument) },
                    onNavigate = { onNavigate(monument) },
                    onCardClick = { onSelectMonument(monument) }
                )
            }
        }
        if (visited.isNotEmpty()) {
            item { SectionHeader("✓ Visités (${visited.size})") }
            visited.entries.sortedBy { it.value }.forEach { (id, name) ->
                item(key = "vis_$id") {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "✓ $name",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonumentDetailScreen(
    monument: Monument,
    viewModel: MonumentsViewModel,
    onClose: () -> Unit,
    onListen: () -> Unit,
    onViewWorks: () -> Unit,
    lectureBar: @Composable () -> Unit
) {
    val images by viewModel.monumentImages.collectAsStateWithLifecycle()
    val loadingImages by viewModel.loadingImages.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val visited by viewModel.visited.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(monument.id) { viewModel.loadMonumentImages(monument) }

    val isMuseum = monument.kind.lowercase().contains("musée") ||
            monument.kind.lowercase().contains("museum")
    val isFav = favorites.any { it.id == monument.id }
    val isVis = visited.containsKey(monument.id)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("") },
                navigationIcon = {
                    TextButton(onClick = onClose) { Text("← Retour") }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    navigationIconContentColor = Color.White,
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = { lectureBar() }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // En-tête : grande image avec dégradé et titre par-dessus
            val headerImage = monument.imageUrl
            if (headerImage != null) {
                Box {
                    AsyncImage(
                        model = headerImage,
                        contentDescription = monument.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color(0xE6000000)),
                                    endY = 1000f
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                    ) {
                        val cat = monument.category()
                        Surface(
                            color = CategoryColors.forCategory(cat),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                cat.replaceFirstChar { it.uppercase() },
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            monument.name,
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White
                        )
                    }
                }
            } else {
                Text(
                    monument.name,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(16.dp)
                )
            }
            Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = monument.kind.replace('_', ' '),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f)
                )
                monument.inception?.let {
                    Text(
                        text = if (monument.artist != null) "📅 $it" else "Construit en $it",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
            monument.artist?.let { artist ->
                Text(
                    text = "🎨 $artist",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            monument.architect?.let {
                Text("👷 Architecte : $it", style = MaterialTheme.typography.bodyMedium)
            }
            monument.style?.let {
                Text("🏛 Style : $it", style = MaterialTheme.typography.bodyMedium)
            }
            monument.material?.let {
                Text("🧱 Matériau : $it", style = MaterialTheme.typography.bodyMedium)
            }
            monument.heritage?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    "🏅 $it",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            monument.founder?.let {
                Text("🏗 Fondé par : $it", style = MaterialTheme.typography.bodyMedium)
            }
            monument.owner?.let {
                Text("🏢 Propriétaire : $it", style = MaterialTheme.typography.bodyMedium)
            }
            monument.openingHours?.let {
                Text("🕒 Horaires : $it", style = MaterialTheme.typography.bodyMedium)
            }
            monument.fee?.let {
                Text("🎟 Entrée : $it", style = MaterialTheme.typography.bodyMedium)
            }
            monument.website?.let { url ->
                TextButton(
                    onClick = { openWebsite(context, url) },
                    modifier = Modifier.padding(top = 2.dp)
                ) { Text("🌐 Site officiel") }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                OutlinedButton(onClick = { viewModel.toggleFavorite(monument) }) {
                    Text(if (isFav) "★ Favori" else "☆ Favori")
                }
                OutlinedButton(onClick = { viewModel.toggleVisited(monument) }) {
                    Text(if (isVis) "✓ Visité" else "○ Visiter")
                }
                OutlinedButton(onClick = { shareMonument(context, monument) }) {
                    Text("📤 Partager")
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = monument.description ?: "Aucune description disponible.",
                style = MaterialTheme.typography.bodyMedium
            )

            if (loadingImages && images.isEmpty()) {
                Spacer(Modifier.height(10.dp))
                SearchRow()
            } else if (images.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Photos",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(images) { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            modifier = Modifier
                                .size(120.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onListen) { Text("🔊 Écouter") }
                Button(onClick = { openMaps(context, monument) }) { Text("Itinéraire") }
            }

            if (isMuseum && !monument.wikidataId.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onViewWorks,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("🏛️ Voir les œuvres de ce musée") }
            }
        }
        }
    }
}

@Composable
private fun SearchRow() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(8.dp))
        Text("Recherche…", style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun MuseumResultButton(
    museum: com.fabrice.monumentsnearby.data.WikidataClient.Museum,
    onClick: () -> Unit
) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
            Text(museum.name, style = MaterialTheme.typography.bodyLarge)
            museum.description?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Réglages de l'application : rayon de recherche, mises à jour automatiques,
 * audioguide (vitesse + voix). Ouvert par le bouton ⚙️ de la barre du haut.
 */
@Composable
private fun SettingsDialog(
    speaker: GuideSpeaker,
    viewModel: MonumentsViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val voices = speaker.voices
    val radius by viewModel.searchRadiusM.collectAsStateWithLifecycle()
    var autoUpdate by remember { mutableStateOf(UpdateManager.autoUpdateEnabled(context)) }
    var checkLaunched by remember { mutableStateOf(false) }
    var selectedSpeed by remember { mutableStateOf(speaker.currentSpeed) }
    var selectedVoice by remember { mutableStateOf(speaker.currentVoice) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Réglages") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                // --- Rayon de recherche ---
                Text(
                    "Rayon de recherche",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(1000, 3000, 6000, 10000).forEach { r ->
                        FilterChip(
                            selected = radius == r,
                            onClick = { viewModel.setSearchRadius(r) },
                            label = { Text("${r / 1000} km") }
                        )
                    }
                }
                Text(
                    "Appliqué à la prochaine recherche « Autour de moi ».",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))

                // --- Mises à jour ---
                Text(
                    "Mises à jour",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Mise à jour automatique",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = autoUpdate,
                        onCheckedChange = {
                            autoUpdate = it
                            UpdateManager.setAutoUpdate(context, it)
                        }
                    )
                }
                TextButton(
                    onClick = {
                        UpdateManager.checkNow(context)
                        checkLaunched = true
                    },
                    enabled = !checkLaunched
                ) {
                    Text(if (checkLaunched) "✓ Vérification lancée" else "Vérifier maintenant")
                }
                Spacer(Modifier.height(14.dp))

                // --- Audioguide ---
                Text(
                    "Vitesse de lecture",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f).forEach { rate ->
                        TextButton(
                            onClick = {
                                speaker.setSpeed(rate)
                                selectedSpeed = rate
                                speaker.speak("Vitesse réglée sur ${formatRate(rate)}.")
                            }
                        ) {
                            Text(
                                text = formatRate(rate),
                                color = if (selectedSpeed == rate) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                Text(
                    "Voix",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                if (voices.isEmpty()) {
                    Text("Aucune voix disponible sur cet appareil.")
                } else {
                    voices.forEach { voice ->
                        val isSelected = voice.name == selectedVoice
                        TextButton(
                            onClick = {
                                speaker.setVoice(voice.name)
                                selectedVoice = voice.name
                            }
                        ) {
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                                Text(
                                    text = if (isSelected) "✓ ${voice.name}" else voice.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                Text(
                                    voice.locale,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    "Monuments Nearby v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Fermer") }
        }
    )
}

private fun formatRate(rate: Float): String = if (rate == 1f) "1×" else "${rate}×"

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun CenteredMessage(message: String, action: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "🏛",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            fontSize = 56.sp
        )
        Spacer(Modifier.height(12.dp))
        Text(
            message,
            style = MaterialTheme.typography.titleMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        action()
    }
}

@Composable
private fun MonumentCard(
    monument: Monument,
    onListen: () -> Unit,
    onNavigate: () -> Unit,
    onCardClick: () -> Unit
) {
    val category = monument.category()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCardClick),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            if (monument.imageUrl != null) {
                Box {
                    AsyncImage(
                        model = monument.imageUrl,
                        contentDescription = monument.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp),
                        contentScale = ContentScale.Crop
                    )
                    Surface(
                        color = CategoryColors.forCategory(category).copy(alpha = 0.95f),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = category.replaceFirstChar { it.uppercase() },
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    if (monument.distanceM > 0) {
                        Surface(
                            color = Color(0xCC000000),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(10.dp)
                        ) {
                            Text(
                                text = formatDistance(monument.distanceM),
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
            Column(Modifier.padding(14.dp)) {
                if (monument.imageUrl == null) {
                    // Sans photo, le badge distance de l'image n'existe pas :
                    // on l'affiche à côté du badge de catégorie.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = CategoryColors.forCategory(category).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = category.replaceFirstChar { it.uppercase() },
                                color = CategoryColors.forCategory(category),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        if (monument.distanceM > 0) {
                            Text(
                                text = formatDistance(monument.distanceM),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    text = monument.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Row(Modifier.padding(top = 2.dp)) {
                    Text(
                        text = monument.kind.replace('_', ' '),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    monument.inception?.let {
                        Text(
                            text = if (monument.artist != null) "📅 $it" else "Construit en $it",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                monument.artist?.let { artist ->
                    Text(
                        text = "🎨 $artist",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                monument.description?.let { desc ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row {
                    OutlinedButton(
                        onClick = onListen,
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("🔊 Écouter") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onNavigate,
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Itinéraire") }
                }
            }
        }
    }
}

fun formatDistance(m: Double): String =
    if (m >= 1000) "%.1f km".format(m / 1000).replace('.', ',') else "${m.roundToInt()} m"

private fun guideText(m: Monument): String {
    val desc = m.description?.takeIf { it.isNotBlank() } ?: "Aucune description disponible."
    val artist = m.artist?.let { " Par $it." } ?: ""
    return "${m.name}. ${m.kind.replace('_', ' ')}.$artist $desc"
}

private fun openMaps(context: Context, m: Monument) {
    val uri = Uri.parse("geo:${m.lat},${m.lon}?q=${m.lat},${m.lon}(${Uri.encode(m.name)})")
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (e: Exception) {
        // aucun gestionnaire d'intent geo: (appareil sans Maps) → on ne crashe pas
    }
}

/** Ouvre le site web officiel du monument. */
private fun openWebsite(context: Context, url: String) {
    val safe = if (url.startsWith("http")) url else "https://$url"
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(safe)))
    } catch (e: Exception) {
        // pas de navigateur → silencieux
    }
}

/** Partage la fiche du monument (nom, infos, description, lien Wikipédia). */
private fun shareMonument(context: Context, m: Monument) {
    val wikiLink = m.wikipediaTitle?.takeIf { it.isNotBlank() }
        ?.let { "https://fr.wikipedia.org/wiki/${Uri.encode(it.replace(' ', '_'))}" }
    val text = buildString {
        append(m.name)
        m.artist?.let { append(" — $it") }
        append("\n${m.kind.replace('_', ' ')}")
        m.inception?.let { append(" · $it") }
        m.description?.let { append("\n\n$it") }
        wikiLink?.let { append("\n\n$it") }
        m.imageUrl?.let { append("\n\nPhoto : $it") }
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, m.name)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    try {
        context.startActivity(Intent.createChooser(send, "Partager ${m.name}"))
    } catch (e: Exception) {
        // aucun app de partage → silencieux
    }
}
