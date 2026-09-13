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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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

/**
 * Pastilles de filtre par catégorie de monument.
 */
@Composable
internal fun FilterBar(selected: String?, onSelect: (String?) -> Unit) {
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

/**
 * Quiz de fin de balade : une question à la fois, options révélées après la
 * réponse, score final. Questions générées depuis les données des étapes.
 */
@Composable
internal fun WalkQuizDialog(
    questions: List<WalkQuiz.Question>,
    onDismiss: () -> Unit,
    onFinished: (score: Int, total: Int) -> Unit = { _, _ -> }
) {
    var index by remember { mutableStateOf(0) }
    var score by remember { mutableStateOf(0) }
    var answered by remember { mutableStateOf<Int?>(null) }
    val finished = index >= questions.size
    LaunchedEffect(finished) {
        if (finished) onFinished(score, questions.size)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (finished) "🎓 Résultat" else "🎓 Quiz de la balade") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (finished) {
                    val n = questions.size
                    Text(
                        when {
                            score == n -> "Sans faute : $score/$n ! Guide confirmé 🏆"
                            score * 2 >= n -> "Bien joué : $score/$n — la balade a laissé des traces !"
                            else -> "$score/$n… une bonne raison de refaire la balade 😉"
                        },
                        style = MaterialTheme.typography.bodyLarge
                    )
                } else {
                    val q = questions[index]
                    Text(
                        "Question ${index + 1}/${questions.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(q.text, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(10.dp))
                    q.options.forEachIndexed { i, option ->
                        val revealed = answered != null
                        val isCorrect = i == q.correctIndex
                        OutlinedButton(
                            onClick = {
                                if (answered == null) {
                                    answered = i
                                    if (isCorrect) score++
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        ) {
                            Text(
                                buildString {
                                    if (revealed && isCorrect) append("✅ ")
                                    if (revealed && !isCorrect && answered == i) append("❌ ")
                                    append(option)
                                },
                                color = when {
                                    revealed && isCorrect -> MaterialTheme.colorScheme.primary
                                    revealed && answered == i -> MaterialTheme.colorScheme.error
                                    else -> Color.Unspecified
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            when {
                finished -> TextButton(onClick = onDismiss) { Text("Terminer") }
                answered != null -> TextButton(
                    onClick = {
                        index++
                        answered = null
                    }
                ) {
                    Text(if (index == questions.lastIndex) "Voir le score" else "Question suivante")
                }
            }
        },
        dismissButton = {
            if (!finished) {
                TextButton(onClick = onDismiss) { Text("Plus tard") }
            }
        }
    )
}

/**
 * En-tête de l'onglet Autour de moi : compteur et rayon, sélecteur Liste/Carte
 * segmenté, puis les actions secondaires (caméra, tri, musées de la zone).
 * Les balades ont leur bouton flottant.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AroundToolbar(
    count: Int,
    radiusM: Int,
    sortByYear: Boolean,
    updatedLabel: String? = null,
    onToggleSort: () -> Unit,
    onOpenCamera: () -> Unit,
    onMuseumsHere: () -> Unit,
    showMap: Boolean,
    onToggleMap: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "$count monuments · ${radiusM / 1000} km" + (updatedLabel?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f)
            )
            SingleChoiceSegmentedButtonRow {
                SegmentedButton(
                    selected = !showMap,
                    onClick = { if (showMap) onToggleMap() },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    label = { Text("Liste") }
                )
                SegmentedButton(
                    selected = showMap,
                    onClick = { if (!showMap) onToggleMap() },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    label = { Text("Carte") }
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AssistChip(
                onClick = onOpenCamera,
                label = { Text("📷 Caméra") },
                modifier = Modifier.semantics {
                    contentDescription = "Ouvrir la caméra (mode AR, scanner QR, identification photo)"
                }
            )
            AssistChip(
                onClick = onToggleSort,
                label = { Text(if (sortByYear) "📅 Tri : année" else "📍 Tri : distance") },
                modifier = Modifier.semantics {
                    contentDescription = if (sortByYear) {
                        "Trier par distance"
                    } else {
                        "Trier par année de construction"
                    }
                }
            )
            AssistChip(onClick = onMuseumsHere, label = { Text("🏛 Musées ici") })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AroundContent(
    state: UiState,
    viewModel: MonumentsViewModel,
    onListen: (Monument) -> Unit,
    onNavigate: (Monument) -> Unit,
    onMessage: (String) -> Unit,
    showMap: Boolean,
    showWalk: Boolean,
    onShowWalkChange: (Boolean) -> Unit,
    onToggleMap: () -> Unit,
    onOpenMap: () -> Unit,
    onSelectMonument: (Monument) -> Unit,
    onLocate: () -> Unit,
    onMuseumsHere: () -> Unit,
    onOpenCamera: () -> Unit
) {
    val context = LocalContext.current
    val radiusM by viewModel.searchRadiusM.collectAsStateWithLifecycle()
    val lastMonuments by viewModel.lastMonuments.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    // Balade guidée active : son itinéraire s'affiche sur la carte même si
    // l'état local walkStops a été perdu (rotation, changement d'onglet)
    val activeGuidedWalk by viewModel.guidedWalk.collectAsStateWithLifecycle()
    // Si le state courant est musée/ville, on montre le dernier résultat « autour de moi » ;
    // pendant un rafraîchissement, la liste précédente reste affichée (tirer pour actualiser)
    val effective = when (state) {
        is UiState.Success ->
            if (state.mode == AppMode.MONUMENTS) state else lastMonuments
        is UiState.Loading -> lastMonuments
        else -> null
    }
    // « Position mise à jour il y a… » : horloge rafraîchie chaque minute
    val lastLocatedAt by viewModel.lastLocatedAt.collectAsStateWithLifecycle()
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = System.currentTimeMillis()
        }
    }
    val updatedLabel = lastLocatedAt?.let { t ->
        val min = ((now - t) / 60_000L).coerceAtLeast(0)
        when {
            min < 1 -> "à l'instant"
            min < 60 -> "il y a $min min"
            else -> "il y a ${min / 60} h"
        }
    }
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var sortByYear by rememberSaveable { mutableStateOf(false) }
    // Balade affichée sur la carte (null = pas de tracé)
    var walkStops by remember { mutableStateOf<List<MonumentsViewModel.WalkStop>?>(null) }

    when {
        effective != null -> {
            if (effective.monuments.isEmpty()) {
                CenteredMessage("Aucun monument trouvé à proximité.") {
                    Button(onClick = onLocate) { Text("📍 Réessayer") }
                }
            } else {
                val visible = if (filter == null) effective.monuments
                else effective.monuments.filter { it.category() == filter }
                // Tri : distance (ordre d'origine) ou année de construction
                val sorted = if (sortByYear) {
                    visible.sortedBy { it.inception?.toIntOrNull() ?: Int.MAX_VALUE }
                } else {
                    visible
                }
                if (showMap) {
                    // La carte reste sous le compteur « X monuments trouvés » :
                    // filtres et boutons restent accessibles au-dessus.
                    Column(Modifier.fillMaxSize()) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                            FilterBar(selected = filter, onSelect = { filter = it })
                            AroundToolbar(
                                count = visible.size,
                                radiusM = radiusM,
                                sortByYear = sortByYear,
                                updatedLabel = updatedLabel,
                                onToggleSort = { sortByYear = !sortByYear },
                                onOpenCamera = onOpenCamera,
                                onMuseumsHere = onMuseumsHere,
                                showMap = true,
                                onToggleMap = onToggleMap
                            )
                        }
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            val route = activeGuidedWalk?.stops ?: walkStops
                            // Tracé : lignes droites tout de suite, puis
                            // itinéraire piéton réel dès qu'il est disponible
                            var walkPath by remember { mutableStateOf<List<Pair<Double, Double>>?>(null) }
                            LaunchedEffect(route, effective.lat, effective.lon) {
                                walkPath = route?.let { stops ->
                                    listOf(effective.lat to effective.lon) +
                                        stops.map { it.monument.lat to it.monument.lon }
                                }
                                if (route != null) {
                                    viewModel.walkPath(effective.lat, effective.lon, route)?.let {
                                        walkPath = it
                                    }
                                }
                            }
                            val livePosition = activeGuidedWalk?.let { walk ->
                                walk.lat?.let { la -> walk.lon?.let { lo -> la to lo } }
                            }
                            val mapHandle = rememberMapHandle()
                            MonumentsMap(
                                monuments = visible,
                                centerLat = effective.lat,
                                centerLon = effective.lon,
                                onSelectMonument = onSelectMonument,
                                walkPath = walkPath,
                                livePosition = livePosition,
                                mapHandle = mapHandle
                            )
                            // Recentrer sur ma position (celle de la marche en balade guidée)
                            FilledTonalIconButton(
                                onClick = {
                                    val target = livePosition ?: (effective.lat to effective.lon)
                                    mapHandle.recenter(target.first, target.second)
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(10.dp)
                                    .semantics { contentDescription = "Recentrer sur ma position" }
                            ) {
                                Icon(Icons.Filled.LocationOn, contentDescription = null)
                            }
                            // Retour toujours visible sur la carte (le geste
                            // retour système ramène aussi à la liste)
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(onClick = onToggleMap) { Text("← Liste") }
                                // En balade guidée, l'itinéraire s'arrête via la
                                // barre d'étape (✕), pas ici
                                if (walkStops != null && activeGuidedWalk == null) {
                                    Button(onClick = { walkStops = null }) { Text("✕ Itinéraire") }
                                }
                            }
                        }
                    }
                } else {
                    // Créateur en vedette : l'architecte le plus présent dans
                    // les résultats (au moins deux monuments), filtre en un tap
                    var creatorFilter by rememberSaveable { mutableStateOf<String?>(null) }
                    val featuredCreator = visible
                        .filter { it.architect != null }
                        .groupBy { it.architect!! }
                        .filter { it.value.size >= 2 }
                        .maxByOrNull { it.value.size }
                    val creatorVisible = creatorFilter?.let { c -> sorted.filter { it.architect == c } } ?: sorted
                    val majors = creatorVisible.filter { it.important }
                    val others = creatorVisible.filter { !it.important }
                    // Tirer vers le bas relance la détection de position
                    PullToRefreshBox(
                        isRefreshing = state is UiState.Loading,
                        onRefresh = onLocate,
                        modifier = Modifier.fillMaxSize()
                    ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            FilterBar(selected = filter, onSelect = { filter = it })
                        }
                        item {
                            AroundToolbar(
                                count = visible.size,
                                radiusM = radiusM,
                                sortByYear = sortByYear,
                                updatedLabel = updatedLabel,
                                onToggleSort = { sortByYear = !sortByYear },
                                onOpenCamera = onOpenCamera,
                                onMuseumsHere = onMuseumsHere,
                                showMap = false,
                                onToggleMap = onToggleMap
                            )
                        }
                        when {
                            creatorFilter != null -> item {
                                AssistChip(
                                    onClick = { creatorFilter = null },
                                    label = { Text("🎨 $creatorFilter · ${creatorVisible.size} — ✕ tout afficher") }
                                )
                            }
                            featuredCreator != null -> item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                "🎨 Créateur en vedette",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                            Text(
                                                featuredCreator.key,
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            Text(
                                                "${featuredCreator.value.size} monuments à proximité",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                        TextButton(onClick = { creatorFilter = featuredCreator.key }) {
                                            Text("Voir")
                                        }
                                    }
                                }
                            }
                        }
                        if (visible.isEmpty() && filter != null) {
                            // Filtre sans résultat : proposer la sortie plutôt
                            // qu'un écran blanc sous « 0 monuments trouvés »
                            item {
                                CenteredMessage(
                                    "Aucun monument « $filter » dans les résultats."
                                ) {
                                    Button(onClick = { filter = null }) { Text("Afficher tous") }
                                }
                            }
                        }
                        if (majors.isNotEmpty()) {
                            item { SectionHeader("⭐ Monuments majeurs") }
                            items(majors, key = { it.id }) { monument ->
                                MonumentCard(
                                    monument = monument,
                                    onListen = { onListen(monument) },
                                    onNavigate = { onNavigate(monument) },
                                    onCardClick = { onSelectMonument(monument) },
                                    isFavorite = favorites.any { it.id == monument.id },
                                    onToggleFavorite = {
                                        val wasFav = favorites.any { it.id == monument.id }
                                        viewModel.toggleFavorite(monument)
                                        onMessage(
                                            if (wasFav) "Retiré des favoris"
                                            else "⭐ Ajouté aux favoris — retrouve-le dans le Carnet"
                                        )
                                    }
                                )
                            }
                        }
                        if (others.isNotEmpty()) {
                            item { SectionHeader("Autres monuments") }
                            items(others, key = { it.id }) { monument ->
                                MonumentCard(
                                    monument = monument,
                                    onListen = { onListen(monument) },
                                    onNavigate = { onNavigate(monument) },
                                    onCardClick = { onSelectMonument(monument) },
                                    isFavorite = favorites.any { it.id == monument.id },
                                    onToggleFavorite = {
                                        val wasFav = favorites.any { it.id == monument.id }
                                        viewModel.toggleFavorite(monument)
                                        onMessage(
                                            if (wasFav) "Retiré des favoris"
                                            else "⭐ Ajouté aux favoris — retrouve-le dans le Carnet"
                                        )
                                    }
                                )
                            }
                        }
                    }
                    } // PullToRefreshBox
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
            walks = viewModel.buildThemedWalks(effective.monuments, effective.lat, effective.lon),
            onDismiss = { onShowWalkChange(false) },
            onNavigate = onNavigate,
            onShowOnMap = { stops ->
                walkStops = stops
                onShowWalkChange(false)
                onOpenMap()
            },
            onStartGuided = { walk ->
                if (viewModel.startGuidedWalk(walk.stops, walk.title)) {
                    walkStops = walk.stops
                    onShowWalkChange(false)
                    onOpenMap()
                    onMessage("🥾 Balade guidée lancée — je te parle à chaque étape")
                } else {
                    onMessage("Autorise la localisation précise pour la balade guidée")
                }
            },
            onOpenCircuit = { stops ->
                if (!openWalkCircuit(context, effective.lat, effective.lon, stops)) {
                    onMessage("Aucune application de cartes installée")
                }
            }
        )
    }
}

/**
 * Dialog des balades : plusieurs circuits thématiques composés depuis les
 * résultats (grande balade, essentiel, catégories, époques). Choisir une
 * balade ouvre ses étapes et les actions (guidée, carte, Maps, GPX).
 */
@Composable
internal fun WalkDialog(
    walks: List<MonumentsViewModel.ThemedWalk>,
    onDismiss: () -> Unit,
    onNavigate: (Monument) -> Unit,
    onShowOnMap: (List<MonumentsViewModel.WalkStop>) -> Unit,
    onStartGuided: (MonumentsViewModel.ThemedWalk) -> Unit,
    onOpenCircuit: (List<MonumentsViewModel.WalkStop>) -> Unit
) {
    val context = LocalContext.current
    // Une seule proposition → on l'ouvre directement
    var selected by remember { mutableStateOf(walks.singleOrNull()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(selected?.title ?: "🥾 Balades à pied") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val current = selected
                if (current == null) {
                    Text(
                        "Des circuits composés à partir des monuments trouvés — choisis ta balade.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    walks.forEach { walk ->
                        val total = walk.stops.last().cumulativeM
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected = walk }
                                .padding(vertical = 8.dp)
                        ) {
                            Text(walk.title, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${walk.stops.size} étapes · ${formatDistance(total)} " +
                                    "(~${(total / 80).roundToInt()} min)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (walks.isEmpty()) {
                        Text("Pas assez de monuments pour composer une balade.")
                    }
                } else {
                    if (walks.size > 1) {
                        TextButton(onClick = { selected = null }) { Text("← Autres balades") }
                    }
                    Text(
                        "Touche une étape pour son itinéraire.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    current.stops.forEachIndexed { index, stop ->
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
                    Spacer(Modifier.height(6.dp))
                    // Itinéraire piéton complet (toutes les étapes) dans Google Maps
                    TextButton(onClick = { onOpenCircuit(current.stops) }) {
                        Text("🧭 Circuit dans Google Maps")
                    }
                    TextButton(onClick = { exportWalkGpx(context, current.stops) }) {
                        Text("💾 Exporter la balade (GPX)")
                    }
                }
            }
        },
        confirmButton = {
            selected?.let { walk ->
                Row {
                    TextButton(onClick = { onShowOnMap(walk.stops) }) { Text("🗺️ Carte") }
                    // Balade guidée : suivi GPS + lecture audio à chaque étape
                    TextButton(onClick = { onStartGuided(walk) }) { Text("▶ Guidée") }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Fermer") }
        }
    )
}
