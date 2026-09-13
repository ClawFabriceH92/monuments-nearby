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

@Composable
internal fun MuseumContent(
    state: UiState,
    viewModel: MonumentsViewModel,
    showMuseumSearch: Boolean,
    onShowMuseumSearch: () -> Unit,
    onSelectMuseum: (WikidataClient.Museum) -> Unit,
    onSelectMonument: (Monument) -> Unit,
    onListen: (Monument) -> Unit,
    onNavigate: (Monument) -> Unit,
    onMessage: (String) -> Unit
) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
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
                }
                else -> CenteredMessage(
                    "Recherche un musée pour explorer ses œuvres."
                ) {
                    Button(onClick = onShowMuseumSearch) { Text("🔍 Rechercher un musée") }
                }
            }
        }
    }
}

@Composable
internal fun MuseumSearchForm(
    viewModel: MonumentsViewModel,
    onSelectMuseum: (WikidataClient.Museum) -> Unit
) {
    var cityMode by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var cityQuery by rememberSaveable { mutableStateOf("") }
    val searching by viewModel.searching.collectAsStateWithLifecycle()
    val museumResults by viewModel.museumResults.collectAsStateWithLifecycle()
    val cityMuseums by viewModel.cityMuseums.collectAsStateWithLifecycle()

    // Requête restaurée (rememberSaveable) sans résultats en mémoire :
    // relancer la recherche au lieu d'afficher « Aucun musée trouvé » à tort
    LaunchedEffect(Unit) {
        if (searchQuery.isNotBlank() && museumResults.isEmpty() && !searching) {
            viewModel.searchMuseums(searchQuery)
        }
    }

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
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    if (cityQuery.isNotBlank() && !searching) {
                        viewModel.loadMuseumsInCity(cityQuery)
                    }
                }),
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
internal fun CityContent(
    state: UiState,
    viewModel: MonumentsViewModel,
    onSelectMonument: (Monument) -> Unit,
    onListen: (Monument) -> Unit,
    onNavigate: (Monument) -> Unit,
    onMessage: (String) -> Unit
) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    var cityQuery by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    val cityGuide by viewModel.cityGuide.collectAsStateWithLifecycle()
    val recentCities by viewModel.recentCities.collectAsStateWithLifecycle()
    // Suggestions pendant la frappe (Photon), avec un léger délai anti-rafale
    var suggestions by remember { mutableStateOf<List<PhotonClient.Suggestion>>(emptyList()) }
    var suggestionsFor by remember { mutableStateOf("") }
    LaunchedEffect(cityQuery) {
        if (cityQuery.trim().length < 2 || cityQuery.trim() == suggestionsFor) {
            if (cityQuery.trim().length < 2) suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(350)
        suggestions = try {
            PhotonClient.suggestCities(cityQuery.trim())
        } catch (e: Exception) {
            emptyList()
        }
    }

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
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    if (cityQuery.isNotBlank()) viewModel.loadCity(cityQuery.trim())
                }),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (suggestions.isNotEmpty()) {
            items(suggestions, key = { "sug_${it.label}" }) { suggestion ->
                Text(
                    "📍 ${suggestion.label}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            suggestionsFor = suggestion.name
                            cityQuery = suggestion.name
                            suggestions = emptyList()
                            viewModel.loadCityAt(
                                GeocoderClient.City(suggestion.name, suggestion.lat, suggestion.lon)
                            )
                        }
                        .padding(vertical = 8.dp)
                )
            }
        }
        item {
            Button(
                onClick = { viewModel.loadCity(cityQuery.trim()) },
                enabled = cityQuery.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Afficher") }
        }
        if (recentCities.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Récentes :",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    recentCities.forEach { city ->
                        AssistChip(
                            onClick = {
                                suggestionsFor = city.name
                                cityQuery = city.name
                                viewModel.loadCityAt(city)
                            },
                            label = { Text("🕘 ${city.name}") }
                        )
                    }
                }
            }
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
            else -> {}
        }
    }
}

@Composable
internal fun SearchRow() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(8.dp))
        Text("Recherche…", style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
internal fun MuseumResultButton(
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
