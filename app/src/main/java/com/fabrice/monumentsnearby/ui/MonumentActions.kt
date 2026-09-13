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

/** Distance en toutes lettres pour l'audioguide (« 240 mètres », « 1,3 kilomètre »). */
internal fun spokenDistance(m: Double): String =
    if (m >= 1000) {
        "%.1f".format(m / 1000).replace('.', ',') + " kilomètre" + if (m >= 2000) "s" else ""
    } else {
        "${m.roundToInt()} mètres"
    }

fun formatDistance(m: Double): String =
    if (m >= 1000) "%.1f km".format(m / 1000).replace('.', ',') else "${m.roundToInt()} m"

internal fun guideText(m: Monument): String {
    val desc = m.description?.takeIf { it.isNotBlank() } ?: "Aucune description disponible."
    val artist = m.artist?.let { " Par $it." } ?: ""
    return "${m.name}. ${m.kind.replace('_', ' ')}.$artist $desc"
}

/**
 * Ouvre le circuit complet de la balade dans Google Maps (itinéraire piéton
 * multi-étapes : position de départ → étapes → dernière étape). L'URL Maps
 * accepte jusqu'à 9 waypoints — nos balades font 8 étapes au plus.
 */
internal fun openWalkCircuit(
    context: Context,
    startLat: Double,
    startLon: Double,
    stops: List<MonumentsViewModel.WalkStop>
): Boolean {
    if (stops.isEmpty()) return false
    fun pt(lat: Double, lon: Double) = "$lat,$lon"
    val destination = stops.last().monument
    val waypoints = stops.dropLast(1)
        .joinToString("|") { pt(it.monument.lat, it.monument.lon) }
    val url = buildString {
        append("https://www.google.com/maps/dir/?api=1")
        append("&origin=").append(pt(startLat, startLon))
        append("&destination=").append(pt(destination.lat, destination.lon))
        if (waypoints.isNotEmpty()) append("&waypoints=").append(Uri.encode(waypoints))
        append("&travelmode=walking")
    }
    return try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        true
    } catch (e: Exception) {
        false
    }
}

/** Ouvre l'itinéraire. Retourne false si aucune app de cartes n'est installée. */
internal fun openMaps(context: Context, m: Monument): Boolean {
    val uri = Uri.parse("geo:${m.lat},${m.lon}?q=${m.lat},${m.lon}(${Uri.encode(m.name)})")
    return try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        true
    } catch (e: Exception) {
        false // aucun gestionnaire d'intent geo: (appareil sans Maps)
    }
}

/** Ouvre une URL dans le navigateur. Retourne false si aucun n'est installé. */
internal fun openWebsite(context: Context, url: String): Boolean {
    val safe = if (url.startsWith("http")) url else "https://$url"
    return try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(safe)))
        true
    } catch (e: Exception) {
        false
    }
}

/**
 * Exporte la balade en fichier GPX (waypoints + route) et ouvre le partage —
 * importable dans Osmand, Organic Maps, Komoot, Garmin…
 */
internal fun exportWalkGpx(context: Context, stops: List<MonumentsViewModel.WalkStop>) {
    if (stops.isEmpty()) return
    try {
        fun esc(s: String) = s
            .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        val gpx = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            append(
                "<gpx version=\"1.1\" creator=\"Monuments Nearby\" " +
                    "xmlns=\"http://www.topografix.com/GPX/1/1\">\n"
            )
            stops.forEach { stop ->
                append(
                    "  <wpt lat=\"${stop.monument.lat}\" lon=\"${stop.monument.lon}\">" +
                        "<name>${esc(stop.monument.name)}</name></wpt>\n"
                )
            }
            append("  <rte><name>Balade Monuments Nearby</name>\n")
            stops.forEach { stop ->
                append(
                    "    <rtept lat=\"${stop.monument.lat}\" lon=\"${stop.monument.lon}\">" +
                        "<name>${esc(stop.monument.name)}</name></rtept>\n"
                )
            }
            append("  </rte>\n")
            append("</gpx>\n")
        }
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "balade-monuments.gpx")
        file.writeText(gpx)
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/gpx+xml"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Exporter la balade"))
    } catch (e: Exception) {
        // partage impossible → silencieux
    }
}

/** Partage la fiche du monument. Retourne false si aucune app de partage. */
internal fun shareMonument(context: Context, m: Monument): Boolean {
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
    return try {
        context.startActivity(Intent.createChooser(send, "Partager ${m.name}"))
        true
    } catch (e: Exception) {
        false
    }
}
