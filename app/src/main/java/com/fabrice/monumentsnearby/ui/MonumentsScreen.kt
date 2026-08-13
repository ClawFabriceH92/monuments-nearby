package com.fabrice.monumentsnearby.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.fabrice.monumentsnearby.data.Monument
import com.fabrice.monumentsnearby.tts.GuideSpeaker
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonumentsScreen(
    state: UiState,
    viewModel: MonumentsViewModel,
    onLocate: () -> Unit
) {
    val context = LocalContext.current
    val speaker = remember { GuideSpeaker(context) }
    DisposableEffect(Unit) { onDispose { speaker.shutdown() } }

    var showMap by remember { mutableStateOf(false) }
    var showVoiceDialog by remember { mutableStateOf(false) }
    var showMuseumDialog by remember { mutableStateOf(false) }
    var showCityDialog by remember { mutableStateOf(false) }

    // Titre conservé pendant le chargement
    var lastTitle by remember { mutableStateOf("Monuments à proximité") }
    val success = state as? UiState.Success
    SideEffect {
        if (success != null) lastTitle = success.title
    }
    val isMuseumMode = success?.mode == AppMode.MUSEUM
    if (isMuseumMode) showMap = false

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(lastTitle) },
                actions = {
                    // Mode monuments autour de moi
                    TextButton(onClick = onLocate) {
                        Text("📍", style = MaterialTheme.typography.titleLarge)
                    }
                    // Mode musée : rechercher / musées par ville
                    TextButton(onClick = { showMuseumDialog = true }) {
                        Text("🏛️", style = MaterialTheme.typography.titleLarge)
                    }
                    // Mode ville : monuments d'une ville
                    TextButton(onClick = { showCityDialog = true }) {
                        Text("🏙️", style = MaterialTheme.typography.titleLarge)
                    }
                    // Bascule liste ↔ carte (inutile en mode musée : œuvres au même point)
                    if (!isMuseumMode) {
                        TextButton(onClick = { showMap = !showMap }) {
                            Text(
                                text = if (showMap) "📋" else "🗺️",
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    }
                    // Choix de la voix et de la vitesse de l'audioguide
                    TextButton(onClick = { showVoiceDialog = true }) {
                        Text("🔊", style = MaterialTheme.typography.titleLarge)
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when (state) {
                is UiState.Idle -> CenteredMessage("Appuie pour détecter les monuments autour de toi") {
                    Button(onClick = onLocate) { Text("📍 Détecter ma position") }
                }

                is UiState.Loading -> Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text("Recherche…")
                }

                is UiState.Error -> CenteredMessage(state.message) {
                    Button(onClick = onLocate) { Text("Réessayer") }
                }

                is UiState.Success -> {
                    if (state.monuments.isEmpty()) {
                        CenteredMessage("Aucun résultat trouvé. Essaie une autre ville ou un autre musée !") {
                            Button(onClick = onLocate) { Text("📍 Retour aux monuments") }
                        }
                    } else if (showMap && !isMuseumMode) {
                        MonumentsMap(
                            monuments = state.monuments,
                            centerLat = state.lat,
                            centerLon = state.lon
                        )
                    } else {
                        val majors = state.monuments.filter { it.important }
                        val others = state.monuments.filter { !it.important }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item {
                                Text(
                                    "${state.monuments.size} ${if (isMuseumMode) "œuvres" else "monuments"} trouvés",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                            if (majors.isNotEmpty()) {
                                item { SectionHeader(if (isMuseumMode) "⭐ Œuvres majeures" else "⭐ Monuments majeurs") }
                                items(majors, key = { it.id }) { monument ->
                                    MonumentCard(
                                        monument = monument,
                                        onListen = { speaker.speak(guideText(monument)) },
                                        onNavigate = { openMaps(context, monument) }
                                    )
                                }
                            }
                            if (others.isNotEmpty()) {
                                item { SectionHeader(if (isMuseumMode) "Autres œuvres" else "Autres monuments") }
                                items(others, key = { it.id }) { monument ->
                                    MonumentCard(
                                        monument = monument,
                                        onListen = { speaker.speak(guideText(monument)) },
                                        onNavigate = { openMaps(context, monument) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showMuseumDialog) {
        MuseumDialog(viewModel = viewModel, onDismiss = { showMuseumDialog = false })
    }
    if (showCityDialog) {
        CityDialog(viewModel = viewModel, onDismiss = { showCityDialog = false })
    }
    if (showVoiceDialog) {
        VoiceDialog(speaker = speaker, onDismiss = { showVoiceDialog = false })
    }
}

/**
 * Sélecteur de musée : recherche par nom (Wikidata) ou musées d'une ville.
 */
@Composable
private fun MuseumDialog(
    viewModel: MonumentsViewModel,
    onDismiss: () -> Unit
) {
    var cityMode by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var cityQuery by remember { mutableStateOf("") }
    val searching by viewModel.searching.collectAsStateWithLifecycle()
    val museumResults by viewModel.museumResults.collectAsStateWithLifecycle()
    val cityMuseums by viewModel.cityMuseums.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🏛️ Musée") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (!cityMode) {
                    // Recherche par nom
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
                            MuseumResultButton(museum, onClick = {
                                viewModel.loadMuseumArtworks(museum)
                                onDismiss()
                            })
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
                    // Musées par ville
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
                                MuseumResultButton(museum, onClick = {
                                    viewModel.loadMuseumArtworks(museum)
                                    onDismiss()
                                })
                            }
                        }
                    }
                    TextButton(
                        onClick = { cityMode = false },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("← Recherche par nom") }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Fermer") }
        }
    )
}

/** Sélecteur de ville : monuments de la ville choisie. */
@Composable
private fun CityDialog(
    viewModel: MonumentsViewModel,
    onDismiss: () -> Unit
) {
    var cityQuery by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🏙️ Ville") },
        text = {
            Column {
                Text(
                    "Afficher les monuments d'une ville (rayon 6 km autour du centre).",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = cityQuery,
                    onValueChange = { cityQuery = it },
                    label = { Text("Nom de la ville") },
                    placeholder = { Text("Versailles, Lyon, Asnières…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.loadCity(cityQuery.trim())
                    onDismiss()
                },
                enabled = cityQuery.isNotBlank()
            ) { Text("Afficher") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
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

@Composable
private fun VoiceDialog(speaker: GuideSpeaker, onDismiss: () -> Unit) {
    val voices = speaker.voices
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Audioguide") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                // Vitesse de lecture — chaque palier joue un test immédiatement
                Text(
                    "Vitesse de lecture",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f).forEach { rate ->
                        val selected = speaker.currentSpeed == rate
                        TextButton(
                            onClick = {
                                speaker.setSpeed(rate)
                                speaker.speak("Vitesse réglée sur ${formatRate(rate)}.")
                            }
                        ) {
                            Text(
                                text = formatRate(rate),
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
                Text(
                    "Vitesse actuelle : ${formatRate(speaker.currentSpeed)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                // Choix de la voix
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
                        TextButton(
                            onClick = {
                                speaker.setVoice(voice.name)
                                onDismiss()
                            }
                        ) {
                            Text(voice.name)
                        }
                    }
                }
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
        Text(message, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        action()
    }
}

@Composable
private fun MonumentCard(
    monument: Monument,
    onListen: () -> Unit,
    onNavigate: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            monument.imageUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = monument.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.height(10.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = monument.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                if (monument.distanceM > 0) {
                    Text(
                        text = formatDistance(monument.distanceM),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Row {
                Text(
                    text = monument.kind.replace('_', ' '),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f)
                )
                monument.inception?.let {
                    Text(
                        text = "Construit en $it",
                        style = MaterialTheme.typography.labelSmall
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
                OutlinedButton(onClick = onListen) { Text("🔊 Écouter") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onNavigate) { Text("Itinéraire") }
            }
        }
    }
}

private fun formatDistance(m: Double): String =
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
