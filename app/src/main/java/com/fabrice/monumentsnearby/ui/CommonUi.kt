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
internal fun SectionHeader(title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
    ) {
        // Trait d'accent lumineux devant le titre
        Box(
            Modifier
                .width(3.dp)
                .height(14.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
internal fun CenteredMessage(message: String, action: @Composable () -> Unit) {
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
internal fun MonumentCard(
    monument: Monument,
    onListen: () -> Unit,
    onNavigate: () -> Unit,
    onCardClick: () -> Unit,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null
) {
    val category = monument.category()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCardClick),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        // Bord « verre » : dégradé cyan → violet
        border = BorderStroke(
            1.dp,
            Brush.linearGradient(
                listOf(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f)
                )
            )
        )
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = onListen,
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("🔊 Écouter") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onNavigate,
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Itinéraire") }
                    if (onToggleFavorite != null) {
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier.semantics {
                                contentDescription = if (isFavorite) {
                                    "Retirer des favoris"
                                } else {
                                    "Ajouter aux favoris"
                                }
                            }
                        ) {
                            Text(
                                if (isFavorite) "★" else "☆",
                                fontSize = 22.sp,
                                color = if (isFavorite) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
