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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun MonumentDetailScreen(
    monument: Monument,
    viewModel: MonumentsViewModel,
    snackbarHostState: SnackbarHostState,
    onClose: () -> Unit,
    onHome: () -> Unit,
    onListen: () -> Unit,
    onListenText: (String) -> Unit,
    onViewWorks: () -> Unit,
    lectureBar: @Composable () -> Unit
) {
    val images by viewModel.monumentImages.collectAsStateWithLifecycle()
    val loadingImages by viewModel.loadingImages.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val visited by viewModel.visited.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val notify: (String) -> Unit = { msg ->
        scope.launch { snackbarHostState.showSnackbar(msg) }
    }
    LaunchedEffect(monument.id) { viewModel.loadMonumentImages(monument) }
    var selectedPhoto by remember { mutableStateOf<String?>(null) }

    val isMuseum = monument.kind.lowercase().contains("musée") ||
            monument.kind.lowercase().contains("museum")
    val isFav = favorites.any { it.id == monument.id }
    val isVis = visited.containsKey(monument.id)
    val note = notes[monument.id]
    var showNoteDialog by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }
    var loadingArticle by remember { mutableStateOf(false) }
    // Article Wikipédia chargé : affiché en plein écran, lisible à voix haute
    var articleText by remember(monument.id) { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = Modifier.auroraBackground(),
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(monument.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (!shareMonument(context, monument)) {
                            notify("Aucune application de partage disponible")
                        }
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = "Partager la fiche")
                    }
                    // Retour direct à l'accueil (onglet « Autour de moi »)
                    IconButton(onClick = onHome) {
                        Icon(Icons.Filled.Home, contentDescription = "Retour à l'accueil")
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = { lectureBar() },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
                // Faits clés en pastilles : type, date, style, classement
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text(monument.kind.replace('_', ' ')) }
                    )
                    monument.inception?.let {
                        SuggestionChip(onClick = {}, label = { Text("📅 $it") })
                    }
                    monument.style?.let {
                        SuggestionChip(onClick = {}, label = { Text("🏛 $it") })
                    }
                    monument.heritage?.let {
                        SuggestionChip(
                            onClick = {},
                            label = {
                                Text("🏅 $it" + (monument.heritageYear?.let { y -> " ($y)" } ?: ""))
                            }
                        )
                    }
                }
                monument.artist?.let { artist ->
                    Text(
                        text = "🎨 $artist",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                // Actions principales : y aller, écouter — puis favori, visité, note
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (!openMaps(context, monument)) {
                                notify("Aucune application de cartes installée")
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("🧭 Itinéraire") }
                    OutlinedButton(onClick = onListen, modifier = Modifier.weight(1f)) {
                        Text("🔊 Écouter")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(onClick = {
                        viewModel.toggleFavorite(monument)
                        notify(if (isFav) "Retiré des favoris" else "⭐ Ajouté aux favoris")
                    }) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = null,
                            tint = if (isFav) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Favori")
                    }
                    TextButton(onClick = {
                        viewModel.toggleVisited(monument)
                        notify(if (isVis) "Retiré des visités" else "✓ Marqué visité")
                    }) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = if (isVis) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (isVis) "Visité" else "Visiter")
                    }
                    TextButton(onClick = { showNoteDialog = true }) {
                        Text(if (note == null) "📝 Note" else "📝 Modifier")
                    }
                }
                if (isMuseum && !monument.wikidataId.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = onViewWorks,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("🏛️ Voir les œuvres de ce musée") }
                }

                // Note personnelle du carnet
                note?.let {
                    Spacer(Modifier.height(10.dp))
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "📝 Ta note",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                // À propos : description + article complet
                SectionHeader("À propos")
                Text(
                    text = monument.description ?: "Aucune description disponible.",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (!monument.wikipediaTitle.isNullOrBlank()) {
                    TextButton(
                        onClick = {
                            val title = monument.wikipediaTitle
                            if (title != null) {
                                loadingArticle = true
                                scope.launch {
                                    articleText = WikipediaClient.fetchFullText(title)
                                        ?: "L'article complet n'est pas disponible pour le moment."
                                    loadingArticle = false
                                }
                            }
                        },
                        enabled = !loadingArticle
                    ) {
                        Text(
                            if (loadingArticle) "Chargement de l'article…"
                            else "📖 Lire l'article complet"
                        )
                    }
                }

                // Infos pratiques
                val practical = listOfNotNull(
                    (monument.address ?: monument.commune)?.let { "📍 $it" },
                    monument.openingHours?.let { "🕒 Horaires : $it" },
                    monument.fee?.let { "🎟 Entrée : $it" }
                )
                if (practical.isNotEmpty() || monument.website != null) {
                    SectionHeader("Infos pratiques")
                    practical.forEach {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                    monument.website?.let { url ->
                        TextButton(onClick = {
                            if (!openWebsite(context, url)) notify("Aucun navigateur disponible")
                        }) { Text("🌐 Site officiel") }
                    }
                }

                // Histoire & architecture
                val history = listOfNotNull(
                    monument.architect?.let { "👷 Architecte : $it" },
                    monument.material?.let { "🧱 Matériau : $it" },
                    monument.founder?.let { "🏗 Fondé par : $it" },
                    monument.owner?.let { "🏢 Propriétaire : $it" },
                    monument.namedAfter?.let { "🏷 Nommé d'après : $it" },
                    monument.openedYear?.takeIf { it != monument.inception }
                        ?.let { "🎀 Ouverture : $it" },
                    monument.events.takeIf { it.isNotEmpty() }
                        ?.let { "📜 " + it.joinToString(" · ") }
                )
                if (history.isNotEmpty()) {
                    SectionHeader("Histoire & architecture")
                    history.forEach {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }

                // Photos
                if (loadingImages && images.isEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    SearchRow()
                } else if (images.isNotEmpty()) {
                    SectionHeader("Photos")
                    Spacer(Modifier.height(4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(images) { url ->
                            AsyncImage(
                                model = url,
                                contentDescription = "Agrandir la photo",
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { selectedPhoto = url },
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }

                // Données liées : notices officielles, bases du web sémantique, QR
                val linked = buildList {
                    monument.merimeeRef?.let {
                        add("🏛 Notice Mérimée" to "https://www.pop.culture.gouv.fr/notice/merimee/$it")
                    }
                    monument.museofileRef?.let {
                        add("🖼 Notice Muséofile" to "https://www.pop.culture.gouv.fr/notice/museo/$it")
                    }
                    monument.wikidataId?.let {
                        add("🌐 Wikidata" to "https://www.wikidata.org/wiki/$it")
                    }
                    monument.wikipediaTitle?.takeIf { it.isNotBlank() }?.let {
                        add(
                            "📖 Wikipédia" to
                                "https://fr.wikipedia.org/wiki/${Uri.encode(it.replace(' ', '_'))}"
                        )
                    }
                    monument.commonsCategory?.let {
                        add(
                            "🖼️ Commons" to
                                "https://commons.wikimedia.org/wiki/Category:${Uri.encode(it.replace(' ', '_'))}"
                        )
                    }
                }
                if (linked.isNotEmpty() || !monument.wikidataId.isNullOrBlank()) {
                    SectionHeader("🔗 Données liées")
                    FlowRow {
                        linked.forEach { (label, url) ->
                            TextButton(onClick = {
                                if (!openWebsite(context, url)) notify("Aucun navigateur disponible")
                            }) { Text(label) }
                        }
                        if (!monument.wikidataId.isNullOrBlank()) {
                            TextButton(onClick = { showQrDialog = true }) { Text("🔳 QR de la fiche") }
                        }
                    }
                }
            }
        }
    }

    if (showNoteDialog) {
        NoteDialog(
            monumentName = monument.name,
            initial = note ?: "",
            onSave = { text ->
                viewModel.setNote(monument.id, text)
                showNoteDialog = false
                notify("📝 Note enregistrée")
            },
            onDismiss = { showNoteDialog = false }
        )
    }

    if (showQrDialog && !monument.wikidataId.isNullOrBlank()) {
        QrDialog(
            monumentName = monument.name,
            qid = monument.wikidataId,
            onDismiss = { showQrDialog = false }
        )
    }

    articleText?.let { text ->
        ArticleScreen(
            monumentName = monument.name,
            text = text,
            onListen = { onListenText(text) },
            onClose = { articleText = null }
        )
    }
    selectedPhoto?.let { url ->
        PhotoDialog(
            url = url,
            onDismiss = { selectedPhoto = null },
            onOpenUrl = { link ->
                if (!openWebsite(context, link)) notify("Aucun navigateur disponible")
            }
        )
    }
}

/**
 * Article Wikipédia en plein écran : on peut le lire à l'œil, et lancer
 * l'audioguide dessus (la barre de lecture de la fiche reste disponible).
 */
@Composable
internal fun ArticleScreen(
    monumentName: String,
    text: String,
    onListen: () -> Unit,
    onClose: () -> Unit
) {
    BackHandler(enabled = true) { onClose() }
    Dialog(
        onDismissRequest = onClose,
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
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour"
                        )
                    }
                    Text(
                        monumentName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onListen) { Text("🔊 Écouter") }
                }
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                )
                Text(
                    "Source : Wikipédia (CC BY-SA)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
        }
    }
}

/** Dialog d'édition de la note personnelle d'un monument. */
@Composable
internal fun NoteDialog(
    monumentName: String,
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("📝 Note — $monumentName") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Ton souvenir, une anecdote, à revoir…") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) { Text("Enregistrer") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}

/**
 * QR code de la fiche (URL Wikidata) : un ami le scanne avec le mode QR de
 * l'app (ou n'importe quel lecteur) pour ouvrir la même fiche.
 */
@Composable
internal fun QrDialog(
    monumentName: String,
    qid: String,
    onDismiss: () -> Unit
) {
    val qrBitmap = remember(qid) { generateQr("https://www.wikidata.org/wiki/$qid") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🔳 $monumentName") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "QR code de $monumentName",
                        modifier = Modifier.size(240.dp)
                    )
                } else {
                    Text("Impossible de générer le QR code.")
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Scanne ce code avec le mode QR de Monuments Nearby pour ouvrir la fiche.",
                    style = MaterialTheme.typography.bodySmall,
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

/** Génère un QR code (noir sur blanc) pour [content]. */
internal fun generateQr(content: String, size: Int = 512): Bitmap? = try {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
    val pixels = IntArray(size * size)
    for (y in 0 until size) {
        for (x in 0 until size) {
            pixels[y * size + x] =
                if (matrix.get(x, y)) android.graphics.Color.BLACK
                else android.graphics.Color.WHITE
        }
    }
    Bitmap.createBitmap(pixels, size, size, Bitmap.Config.RGB_565)
} catch (e: Exception) {
    null
}
