package com.fabrice.monumentsnearby.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.fabrice.monumentsnearby.BuildConfig
import com.fabrice.monumentsnearby.data.FavoriteEntry
import com.fabrice.monumentsnearby.data.Monument
import com.fabrice.monumentsnearby.data.PhotonClient
import com.fabrice.monumentsnearby.data.GeocoderClient
import kotlinx.coroutines.delay
import com.fabrice.monumentsnearby.data.VisitRepository
import com.fabrice.monumentsnearby.data.WalkQuiz
import com.fabrice.monumentsnearby.data.WikidataClient
import com.fabrice.monumentsnearby.data.WikipediaClient
import com.fabrice.monumentsnearby.data.category
import com.fabrice.monumentsnearby.location.GuidedVisitBus
import com.fabrice.monumentsnearby.location.NearbyAlert
import com.fabrice.monumentsnearby.tts.GuideSpeaker
import com.fabrice.monumentsnearby.ui.theme.CategoryColors
import com.fabrice.monumentsnearby.ui.theme.ThemeMode
import com.fabrice.monumentsnearby.ui.theme.auroraBackground
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import com.fabrice.monumentsnearby.update.AutoUpdater
import com.fabrice.monumentsnearby.update.UpdateChecker
import com.fabrice.monumentsnearby.update.UpdateManager
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
enum class AppTab { AROUND, EXPLORE, BOOK }

/** Onglet Explorer : chercher ailleurs — une ville, ou un musée. */
enum class ExploreMode { CITY, MUSEUM }

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

    // rememberSaveable : survivre à la rotation et au retour depuis Maps
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.AROUND) }
    var exploreMode by rememberSaveable { mutableStateOf(ExploreMode.CITY) }
    var showMap by rememberSaveable { mutableStateOf(false) }
    var showWalk by rememberSaveable { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var selectedMonument by remember { mutableStateOf<Monument?>(null) }
    val paused = remember { mutableStateOf(false) }
    val speakerActive = remember { mutableStateOf(false) }
    // Titre du monument en cours de lecture (affiché dans la barre audioguide)
    val speakingTitle = remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val uiScope = rememberCoroutineScope()
    // Feedback utilisateur : confirmations (favori, visité…) et échecs d'intents
    val notify: (String) -> Unit = { msg ->
        uiScope.launch { snackbarHostState.showSnackbar(msg) }
    }
    // Itinéraire : jusque-là, l'échec (pas d'app de cartes) était silencieux
    val navigateTo: (Monument) -> Unit = { m ->
        if (!openMaps(context, m)) notify("Aucune application de cartes installée")
    }
    val listenMonument: (Monument) -> Unit = { m ->
        speakerActive.value = true
        speakingTitle.value = m.name
        speaker.speak(guideText(m), key = "guide:${m.id}")
        if (speaker.resumedFromPhrase > 0) {
            notify("▶ Reprise là où tu t'étais arrêté (${speaker.resumedFromPhrase + 1}/${speaker.phraseCount})")
        }
    }
    var selectedMuseum by remember { mutableStateOf<WikidataClient.Museum?>(null) }
    var showMuseumSearch by rememberSaveable { mutableStateOf(true) }
    var showCamera by remember { mutableStateOf(false) }
    var cameraState by remember { mutableStateOf<UiState.Success?>(null) }
    // Monument dont on vient d'entrer dans le rayon de 100 m (pop-up)
    var nearbyAlert by remember { mutableStateOf<NearbyAlert?>(null) }

    var lastTitle by remember { mutableStateOf("Monuments à proximité") }
    val success = state as? UiState.Success
    SideEffect {
        if (success != null) lastTitle = success.title
    }
    val isMuseumMode = success?.mode == AppMode.MUSEUM
    // La carte n'est réellement affichable qu'avec des résultats « Autour de
    // moi » (état courant ou dernier résultat) — sinon un showMap=true restauré
    // après mort de process ferait intercepter le geste retour pour rien.
    val lastAround by viewModel.lastMonuments.collectAsStateWithLifecycle()
    val mapDisplayable = success?.mode == AppMode.MONUMENTS || lastAround != null

    // Navigation retour (bouton/geste système) : on remonte la hiérarchie
    // écran par écran au lieu de quitter l'app — fiche → écran précédent,
    // caméra → retour, œuvres d'un musée → recherche, carte → liste,
    // autre onglet → Autour de moi. Sans état à dépiler, le retour quitte.
    BackHandler(
        enabled = selectedMonument != null || showCamera ||
            (showMap && mapDisplayable) || selectedTab != AppTab.AROUND
    ) {
        when {
            selectedMonument != null -> {
                selectedMonument = null
                viewModel.clearMonumentImages()
            }
            showCamera -> showCamera = false
            selectedTab == AppTab.EXPLORE && exploreMode == ExploreMode.MUSEUM &&
                !showMuseumSearch -> {
                showMuseumSearch = true
                selectedMuseum = null
            }
            selectedTab != AppTab.AROUND -> selectedTab = AppTab.AROUND
            showMap && mapDisplayable -> showMap = false
        }
    }

    // Balade guidée : barre d'étape + lecture audio automatique à l'arrivée
    val guidedWalk by viewModel.guidedWalk.collectAsStateWithLifecycle()
    val walkArrival by viewModel.walkArrival.collectAsStateWithLifecycle()
    val walkQuizQuestions by viewModel.walkQuiz.collectAsStateWithLifecycle()
    LaunchedEffect(walkArrival) {
        val arrival = walkArrival ?: return@LaunchedEffect
        speakerActive.value = true
        speakingTitle.value = arrival.monument.name
        val next = viewModel.guidedWalk.value?.let { w -> w.stops.getOrNull(w.nextIndex) }
        speaker.speak(
            buildString {
                append("Étape ${arrival.stepIndex + 1} sur ${arrival.totalSteps} : ")
                append("${arrival.monument.name}. ")
                append(arrival.monument.description ?: "Regarde autour de toi !")
                // Enchaînement : les faits marquants de la fiche, sans relancer
                // une lecture séparée
                arrival.monument.architect?.let { append(" Architecte : $it.") }
                arrival.monument.inception?.let { append(" Construit en $it.") }
                arrival.monument.style?.let { append(" Style $it.") }
                when {
                    arrival.lastStep -> {
                        append(" C'était la dernière étape. Belle balade !")
                        if (viewModel.walkQuiz.value != null) {
                            append(" Un petit quiz t'attend à l'écran.")
                        }
                    }
                    next != null ->
                        append(
                            " Prochaine étape : ${next.monument.name}, " +
                                "à ${spokenDistance(next.stepM)}."
                        )
                }
            }
        )
        viewModel.consumeWalkArrival()
    }

    val guidedVisit by viewModel.guidedVisit.collectAsStateWithLifecycle()
    DisposableEffect(guidedVisit) {
        speaker.onFinished = {
            paused.value = false
            speakerActive.value = false
            speakingTitle.value = null
        }
        // Proximité : le receiver de geofence invoque ce listener à l'entrée
        // dans le rayon de 100 m d'un monument. Pop-up systématique quand
        // l'app est ouverte ; lecture audio seulement si la visite guidée
        // est activée dans les réglages.
        GuidedVisitBus.listener = { alert ->
            nearbyAlert = alert
            if (guidedVisit) {
                speakerActive.value = true
                speakingTitle.value = alert.name
                speaker.speak(
                    "Tu approches de ${alert.name}. " +
                        (alert.description ?: "Regarde autour de toi !")
                )
            }
        }
        onDispose { GuidedVisitBus.listener = null }
    }
    // Le moteur TTS ne se libère qu'à la sortie de l'écran (pas à chaque
    // changement de réglage, sinon la lecture en cours serait coupée).
    DisposableEffect(Unit) {
        onDispose { speaker.shutdown() }
    }

    // Lecture d'un texte arbitraire (article complet, visite guidée…)
    val listenText: (String) -> Unit = { text ->
        speakerActive.value = true
        speakingTitle.value = selectedMonument?.name
        speaker.speak(text, key = "article:${selectedMonument?.id ?: text.hashCode()}")
        if (speaker.resumedFromPhrase > 0) {
            notify("▶ Reprise là où tu t'étais arrêté (${speaker.resumedFromPhrase + 1}/${speaker.phraseCount})")
        }
    }

    if (selectedMonument != null) {
        MonumentDetailScreen(
            monument = selectedMonument!!,
            viewModel = viewModel,
            snackbarHostState = snackbarHostState,
            onClose = {
                selectedMonument = null
                viewModel.clearMonumentImages()
            },
            onHome = {
                // Retour à l'accueil : liste « Autour de moi »
                selectedMonument = null
                viewModel.clearMonumentImages()
                selectedTab = AppTab.AROUND
                showMap = false
                showMuseumSearch = true
                selectedMuseum = null
            },
            onListen = { listenMonument(selectedMonument!!) },
            onListenText = listenText,
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
                selectedTab = AppTab.EXPLORE
                exploreMode = ExploreMode.MUSEUM
                showMuseumSearch = false
                selectedMonument = null
                viewModel.clearMonumentImages()
                viewModel.loadMuseumArtworks(museum)
            },
            lectureBar = {
                if (speakerActive.value) {
                    LectureBar(
                        title = speakingTitle.value,
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
            modifier = Modifier.auroraBackground(),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            when (selectedTab) {
                                AppTab.AROUND -> lastTitle
                                AppTab.EXPLORE ->
                                    if (exploreMode == ExploreMode.MUSEUM && selectedMuseum != null) {
                                        selectedMuseum!!.name
                                    } else {
                                        "Explorer"
                                    }
                                AppTab.BOOK -> "Carnet"
                            }
                        )
                    },
                    colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        actionIconContentColor = MaterialTheme.colorScheme.onBackground
                    ),
                    actions = {
                        // Alerte de proximité : cloche dorée quand elle est active
                        val geofencesActive by viewModel.geofencesActive.collectAsStateWithLifecycle()
                        IconButton(onClick = onToggleGeofences) {
                            Icon(
                                Icons.Filled.Notifications,
                                contentDescription = if (geofencesActive) {
                                    "Désactiver l'alerte de proximité"
                                } else {
                                    "Activer l'alerte de proximité"
                                },
                                tint = if (geofencesActive) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        IconButton(onClick = { showSettingsDialog = true }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Réglages")
                        }
                    }
                )
            },
            bottomBar = {
                Column {
                    // Balade guidée en cours : prochaine étape + distance restante
                    guidedWalk?.let { walk ->
                        walk.stops.getOrNull(walk.nextIndex)?.let { nextStop ->
                            Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        // Tap : revenir à la carte et à l'itinéraire
                                        .clickable {
                                            selectedTab = AppTab.AROUND
                                            showMap = true
                                        }
                                        .padding(horizontal = 16.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            "🥾 ${walk.nextIndex + 1}/${walk.stops.size} · " +
                                                nextStop.monument.name +
                                                (walk.distanceToNextM
                                                    ?.let { " — ${formatDistance(it)}" } ?: ""),
                                            style = MaterialTheme.typography.labelLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            "${formatDistance(walk.distanceM)} parcourus · " +
                                                "${walk.elapsedMin} min · ${walk.reached} étape(s) atteinte(s)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    TextButton(
                                        onClick = { viewModel.stopGuidedWalk() },
                                        modifier = Modifier.semantics {
                                            contentDescription = "Arrêter la balade guidée"
                                        }
                                    ) { Text("✕") }
                                }
                            }
                        }
                    }
                    // Barre de lecture flottante, visible seulement pendant l'écoute
                    if (speakerActive.value) {
                        LectureBar(
                            title = speakingTitle.value,
                            paused = paused.value,
                            onTogglePause = { paused.value = speaker.togglePause() },
                            onStop = { speaker.stop() }
                        )
                    }
                    val navGlow = MaterialTheme.colorScheme.primary
                    val navColors = NavigationBarItemDefaults.colors(
                        selectedIconColor = navGlow,
                        selectedTextColor = navGlow,
                        indicatorColor = navGlow.copy(alpha = 0.18f),
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                        modifier = Modifier.drawBehind {
                            // Liseré lumineux en haut de la barre
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    listOf(Color.Transparent, navGlow, Color.Transparent)
                                ),
                                size = Size(size.width, 2.dp.toPx())
                            )
                        }
                    ) {
                        NavigationBarItem(
                            colors = navColors,
                            selected = selectedTab == AppTab.AROUND,
                            onClick = { selectedTab = AppTab.AROUND },
                            icon = { Icon(Icons.Filled.LocationOn, contentDescription = null) },
                            label = { Text("Autour de moi", maxLines = 1, softWrap = false) }
                        )
                        NavigationBarItem(
                            colors = navColors,
                            selected = selectedTab == AppTab.EXPLORE,
                            onClick = { selectedTab = AppTab.EXPLORE },
                            icon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            label = { Text("Explorer") }
                        )
                        NavigationBarItem(
                            colors = navColors,
                            selected = selectedTab == AppTab.BOOK,
                            onClick = { selectedTab = AppTab.BOOK },
                            icon = { Icon(Icons.Filled.Favorite, contentDescription = null) },
                            label = { Text("Carnet") }
                        )
                    }
                }
            },
            floatingActionButton = {
                // Balades : action phare, toujours à portée (liste comme carte)
                if (selectedTab == AppTab.AROUND && mapDisplayable && !isMuseumMode) {
                    val fabGlow = MaterialTheme.colorScheme.primary
                    ExtendedFloatingActionButton(
                        text = { Text("🥾 Balades") },
                        icon = {},
                        onClick = { showWalk = true },
                        shape = RoundedCornerShape(20.dp),
                        containerColor = fabGlow,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                        // Halo lumineux de la couleur d'accent
                        modifier = Modifier.shadow(
                            elevation = 14.dp,
                            shape = RoundedCornerShape(20.dp),
                            ambientColor = fabGlow,
                            spotColor = fabGlow
                        )
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
                        onListen = listenMonument,
                        onNavigate = navigateTo,
                        onMessage = notify,
                        showMap = showMap,
                        showWalk = showWalk,
                        onShowWalkChange = { showWalk = it },
                        onToggleMap = { showMap = !showMap },
                        onOpenMap = { showMap = true },
                        onSelectMonument = { selectedMonument = it },
                        onLocate = onLocate,
                        onMuseumsHere = {
                            val s = (state as? UiState.Success)
                                ?.takeIf { it.mode == AppMode.MONUMENTS }
                                ?: viewModel.lastMonuments.value
                            if (s != null) {
                                viewModel.loadMuseumsInZone(s.lat, s.lon)
                                showMap = false
                                selectedTab = AppTab.EXPLORE
                                exploreMode = ExploreMode.MUSEUM
                                showMuseumSearch = false
                                selectedMuseum = null
                            }
                        },
                        onOpenCamera = {
                            val s = (state as? UiState.Success)
                                ?.takeIf { it.mode == AppMode.MONUMENTS }
                                ?: viewModel.lastMonuments.value
                            if (s != null) {
                                snackbarHostState.currentSnackbarData?.dismiss()
                                cameraState = s
                                showCamera = true
                            }
                        }
                    )
                    AppTab.EXPLORE -> Column(Modifier.fillMaxSize()) {
                        // Explorer ailleurs : une ville, ou un musée et ses œuvres
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            SegmentedButton(
                                selected = exploreMode == ExploreMode.CITY,
                                onClick = { exploreMode = ExploreMode.CITY },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                label = { Text("🏙 Une ville") }
                            )
                            SegmentedButton(
                                selected = exploreMode == ExploreMode.MUSEUM,
                                onClick = { exploreMode = ExploreMode.MUSEUM },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                label = { Text("🏛 Un musée") }
                            )
                        }
                        Box(Modifier.weight(1f)) {
                            when (exploreMode) {
                                ExploreMode.CITY -> CityContent(
                                    state = state,
                                    viewModel = viewModel,
                                    onSelectMonument = { selectedMonument = it },
                                    onListen = listenMonument,
                                    onNavigate = navigateTo,
                                    onMessage = notify
                                )
                                ExploreMode.MUSEUM -> MuseumContent(
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
                                    onListen = listenMonument,
                                    onNavigate = navigateTo,
                                    onMessage = notify
                                )
                            }
                        }
                    }
                    AppTab.BOOK -> BookContent(
                        viewModel = viewModel,
                        onSelectMonument = { selectedMonument = it },
                        onListen = listenMonument,
                        onNavigate = navigateTo,
                        onMessage = notify
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

    // Quiz de fin de balade guidée
    walkQuizQuestions?.let { questions ->
        WalkQuizDialog(
            questions = questions,
            onDismiss = { viewModel.consumeWalkQuiz() },
            onFinished = { score, total -> viewModel.recordQuizScore(score, total) }
        )
    }

    // Pop-up de proximité : « tu es à moins de 100 m de … »
    nearbyAlert?.let { alert ->
        val known = viewModel.findMonument(alert.id)
        NearbyDialog(
            alert = alert,
            onOpenSheet = if (known != null) {
                {
                    selectedMonument = known
                    nearbyAlert = null
                }
            } else null,
            onListen = {
                speakerActive.value = true
                speakingTitle.value = alert.name
                speaker.speak(
                    "${alert.name}. " + (alert.description ?: "Regarde autour de toi !")
                )
                nearbyAlert = null
            },
            onDismiss = { nearbyAlert = null }
        )
    }
}

/**
 * Pop-up affiché quand on entre dans le rayon de 100 m d'un monument
 * (app ouverte, alerte de proximité 🔔 activée).
 */
@Composable
private fun NearbyDialog(
    alert: NearbyAlert,
    onOpenSheet: (() -> Unit)?,
    onListen: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🏛 À moins de 100 m") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(alert.name, style = MaterialTheme.typography.titleMedium)
                alert.description?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        confirmButton = {
            if (onOpenSheet != null) {
                TextButton(onClick = onOpenSheet) { Text("Voir la fiche") }
            } else {
                TextButton(onClick = onListen) { Text("🔊 Écouter") }
            }
        },
        dismissButton = {
            Row {
                if (onOpenSheet != null) {
                    TextButton(onClick = onListen) { Text("🔊 Écouter") }
                }
                TextButton(onClick = onDismiss) { Text("Plus tard") }
            }
        }
    )
}

/**
 * Barre de lecture flottante de l'audioguide : pause/reprise + arrêt.
 * Affichée seulement quand une lecture est active ou en pause.
 */
@Composable
private fun LectureBar(
    title: String?,
    paused: Boolean,
    onTogglePause: () -> Unit,
    onStop: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "🔊 ${title ?: "Audioguide"}",
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Row {
                TextButton(onClick = onTogglePause) {
                    Text(if (paused) "▶ Reprendre" else "⏸ Pause")
                }
                TextButton(onClick = onStop) { Text("✕ Arrêter") }
            }
        }
    }
}
