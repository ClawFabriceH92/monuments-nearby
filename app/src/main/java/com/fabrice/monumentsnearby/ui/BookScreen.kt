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

/**
 * Carnet de visites : favoris (rouvrables) + monuments visités.
 */
@Composable
internal fun BookContent(
    viewModel: MonumentsViewModel,
    onSelectMonument: (Monument) -> Unit,
    onListen: (Monument) -> Unit,
    onNavigate: (Monument) -> Unit,
    onMessage: (String) -> Unit
) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val visited by viewModel.visited.collectAsStateWithLifecycle()
    val walks by viewModel.walks.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (favorites.isEmpty() && visited.isEmpty() && walks.isEmpty()) {
            item {
                CenteredMessage(
                    "Ton carnet est vide.\nMarque un monument ★ favori ou ✓ visité depuis sa fiche, " +
                        "ou lance une balade guidée."
                ) {}
            }
            return@LazyColumn
        }
        if (walks.isNotEmpty()) {
            item { SectionHeader("🥾 Balades (${walks.size})") }
            item {
                // Totaux : distance, temps, étapes
                val totalKm = walks.sumOf { it.distanceM } / 1000.0
                val totalMin = walks.sumOf { it.durationMin }
                val totalStops = walks.sumOf { it.stopsReached }
                Text(
                    "%.1f km · %d min · %d étapes atteintes au total"
                        .format(Locale.FRANCE, totalKm, totalMin, totalStops),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(walks.take(10), key = { "walk_${it.startedAt}" }) { walk ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            (if (walk.completed) "✅ " else "⏹ ") + walk.title,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            formatDate(walk.startedAt) + " · " +
                                "${formatDistance(walk.distanceM)} · ${walk.durationMin} min · " +
                                "${walk.stopsReached}/${walk.stops} étapes" +
                                (walk.quizScore?.let { " · quiz $it/${walk.quizTotal}" } ?: ""),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${visited.size} visités · ${favorites.size} favoris",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    if (!shareBook(context, favorites, visited)) {
                        onMessage("Aucune application de partage disponible")
                    }
                }) {
                    Text("📤 Partager")
                }
            }
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
                    onCardClick = { onSelectMonument(monument) },
                    isFavorite = true,
                    onToggleFavorite = {
                        viewModel.toggleFavorite(monument)
                        onMessage("Retiré des favoris")
                    }
                )
            }
        }
        if (visited.isNotEmpty()) {
            item { SectionHeader("✓ Visités (${visited.size})") }
            // Les plus récents en premier (anciennes entrées sans date à la fin)
            visited.entries
                .sortedWith(
                    compareByDescending<Map.Entry<String, VisitRepository.VisitedEntry>> {
                        it.value.visitedAt ?: 0L
                    }.thenBy { it.value.name }
                )
                .forEach { (id, entry) ->
                    item(key = "vis_$id") {
                        // Carte vivante : tap → fiche (si le monument est encore
                        // dans les résultats connus), ✕ → retirer des visités
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val known = viewModel.findMonument(id)
                                    if (known != null) {
                                        onSelectMonument(known)
                                    } else {
                                        onMessage(
                                            "Fiche indisponible — relance une recherche à proximité"
                                        )
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    Modifier
                                        .weight(1f)
                                        .padding(vertical = 14.dp)
                                ) {
                                    Text(
                                        text = "✓ ${entry.name}",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    entry.visitedAt?.let { millis ->
                                        Text(
                                            text = "Visité le ${formatDate(millis)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                TextButton(
                                    onClick = {
                                        viewModel.toggleVisited(
                                            Monument(
                                                id = id,
                                                name = entry.name,
                                                lat = 0.0,
                                                lon = 0.0,
                                                distanceM = 0.0,
                                                kind = ""
                                            )
                                        )
                                        onMessage("Retiré des visités")
                                    },
                                    modifier = Modifier.semantics {
                                        contentDescription = "Retirer ${entry.name} des visités"
                                    }
                                ) { Text("✕") }
                            }
                        }
                    }
                }
        }
    }
}

internal fun formatDate(millis: Long): String =
    SimpleDateFormat("d MMMM yyyy", Locale.FRANCE).format(Date(millis))

/** Partage le carnet (favoris + visités). Retourne false si aucune app. */
internal fun shareBook(
    context: Context,
    favorites: List<FavoriteEntry>,
    visited: Map<String, VisitRepository.VisitedEntry>
): Boolean {
    val text = buildString {
        append("Mon carnet de monuments 🏛\n")
        if (visited.isNotEmpty()) {
            append("\n✓ Visités (${visited.size}) :\n")
            visited.values.sortedBy { it.name }.forEach { entry ->
                append("• ${entry.name}")
                entry.visitedAt?.let { append(" — ${formatDate(it)}") }
                append("\n")
            }
        }
        if (favorites.isNotEmpty()) {
            append("\n⭐ À voir (${favorites.size}) :\n")
            favorites.forEach { append("• ${it.name}\n") }
        }
        append("\nPartagé depuis Monuments Nearby")
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Mon carnet de monuments")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    return try {
        context.startActivity(Intent.createChooser(send, "Partager le carnet"))
        true
    } catch (e: Exception) {
        false
    }
}
