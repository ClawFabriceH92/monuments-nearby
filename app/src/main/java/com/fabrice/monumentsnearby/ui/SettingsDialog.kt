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
 * Réglages de l'application : rayon de recherche, mises à jour automatiques,
 * audioguide (vitesse + voix). Ouvert par le bouton ⚙️ de la barre du haut.
 */
@Composable
internal fun SettingsDialog(
    speaker: GuideSpeaker,
    viewModel: MonumentsViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val voices = speaker.voices
    val radius by viewModel.searchRadiusM.collectAsStateWithLifecycle()
    val guidedVisit by viewModel.guidedVisit.collectAsStateWithLifecycle()
    val dailyDiscovery by viewModel.dailyDiscovery.collectAsStateWithLifecycle()
    var autoUpdate by remember { mutableStateOf(UpdateManager.autoUpdateEnabled(context)) }
    var selectedSpeed by remember { mutableStateOf(speaker.currentSpeed) }
    var selectedVoice by remember { mutableStateOf(speaker.currentVoice) }
    val scope = rememberCoroutineScope()
    var updating by remember { mutableStateOf(false) }
    var updateStatus by remember { mutableStateOf<String?>(null) }

    // Plein écran : la fenêtre de réglages occupe toute la place disponible
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .auroraBackground(),
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "⚙️ Réglages",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismiss) { Text("✕ Fermer") }
                }
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
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

                // --- Apparence ---
                val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
                Text(
                    "Apparence",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index, count = ThemeMode.entries.size
                            ),
                            label = { Text(mode.label) }
                        )
                    }
                }
                Text(
                    "Thème sombre « Aurora » par défaut : la carte passe aussi en tuiles sombres.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))

                // --- Carte hors ligne ---
                val offlineStatus by viewModel.offlineStatus.collectAsStateWithLifecycle()
                val darkTiles = MaterialTheme.colorScheme.background.luminance() < 0.5f
                Text(
                    "Carte hors ligne",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Télécharge les tuiles de la dernière zone « Autour de moi » (rayon de " +
                        "recherche) pour garder la carte sans réseau. Les fiches déjà ouvertes " +
                        "restent disponibles.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = { viewModel.downloadOfflineTiles(darkTiles) },
                    enabled = offlineStatus?.startsWith("⏳") != true
                ) { Text("📥 Télécharger la zone") }
                offlineStatus?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(14.dp))

                // --- Découverte ---
                Text(
                    "Découverte",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Visite guidée", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Lecture audio automatique quand tu arrives à moins de 100 m " +
                                "d'un monument. Le pop-up de proximité, lui, s'affiche dès " +
                                "que l'alerte 🔔 est activée et l'app ouverte.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = guidedVisit,
                        onCheckedChange = { viewModel.setGuidedVisit(it) }
                    )
                }
                val wikidataDiscovery by viewModel.wikidataDiscovery.collectAsStateWithLifecycle()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Compléter avec Wikidata", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Après la recherche « Autour de moi », ajoute les lieux connus de " +
                                "Wikidata mais absents d'OpenStreetMap : art public, plaques, sites.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = wikidataDiscovery,
                        onCheckedChange = { viewModel.setWikidataDiscovery(it) }
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Monument du jour", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Notification quotidienne : un monument majeur non visité près de ta dernière recherche.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = dailyDiscovery,
                        onCheckedChange = { viewModel.setDailyDiscovery(it) }
                    )
                }
                Spacer(Modifier.height(14.dp))

                // --- Mises à jour ---
                Text(
                    "Mises à jour",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Téléchargement et installation automatiques",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "Vérifie chaque jour et installe la nouvelle version " +
                                "dès qu'elle est publiée sur GitHub.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoUpdate,
                        onCheckedChange = {
                            autoUpdate = it
                            UpdateManager.setAutoUpdate(context, it)
                        }
                    )
                }
                if (!AutoUpdater.canRequestInstalls(context)) {
                    // Sans cette permission système, l'installation auto est bloquée
                    TextButton(onClick = { AutoUpdater.openInstallSettings(context) }) {
                        Text("🔓 Autoriser l'installation automatique des mises à jour")
                    }
                }
                OutlinedButton(
                    onClick = {
                        updating = true
                        updateStatus = "Vérification de la dernière version…"
                        scope.launch {
                            val info = withContext(Dispatchers.IO) { UpdateChecker.latestWithApk() }
                            updating = false
                            updateStatus = when {
                                info == null ->
                                    "Vérification impossible (pas de réseau ?)."
                                UpdateChecker.compareVersions(
                                    info.versionName, BuildConfig.VERSION_NAME
                                ) <= 0 ->
                                    "✓ Déjà à jour (v${BuildConfig.VERSION_NAME})."
                                !AutoUpdater.canRequestInstalls(context) -> {
                                    AutoUpdater.openInstallSettings(context)
                                    "Autorise d'abord l'installation, puis réessaie."
                                }
                                AutoUpdater.download(context, info.downloadUrl) ->
                                    "⬇ v${info.versionName} en téléchargement — " +
                                        "installation automatique à la fin."
                                else ->
                                    "Téléchargement impossible pour le moment."
                            }
                        }
                    },
                    enabled = !updating,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (updating) "Vérification…"
                        else "⬇ Vérifier et installer maintenant"
                    )
                }
                updateStatus?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = {
                        openWebsite(
                            context,
                            "https://github.com/ClawFabriceH92/monuments-nearby/releases"
                        )
                    }
                ) { Text("📦 Télécharger l'APK sur GitHub") }
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
                                    voice.locale +
                                        (if (voice.quality >= 400) " · HD" else "") +
                                        (if (voice.network) " · réseau" else ""),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                // --- À propos & crédits ---
                Text(
                    "À propos & crédits",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                AboutContent(onOpenUrl = { url -> openWebsite(context, url) })

                Spacer(Modifier.height(10.dp))
                Text(
                    "Monuments Nearby v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                }
            }
        }
    }
}

internal fun formatRate(rate: Float): String = if (rate == 1f) "1×" else "${rate}×"
