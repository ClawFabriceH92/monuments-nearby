package com.fabrice.monumentsnearby.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.fabrice.monumentsnearby.data.CommonsClient

/**
 * Photo en plein écran avec son attribution Commons (auteur, licence) et un
 * lien vers la page du fichier — indispensable pour les images sous CC.
 * Un tap sur le fond ferme la visionneuse.
 */
@Composable
fun PhotoDialog(url: String, onDismiss: () -> Unit, onOpenUrl: (String) -> Unit) {
    var attribution by remember(url) { mutableStateOf<CommonsClient.Attribution?>(null) }
    LaunchedEffect(url) {
        attribution = try {
            CommonsClient.attribution(url)
        } catch (e: Exception) {
            null
        }
    }
    // Vignette 400 px → version plus grande pour le plein écran
    val large = url.replace("?width=400", "?width=1200").replace("/400px-", "/1200px-")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
                .clickable(onClick = onDismiss)
        ) {
            AsyncImage(
                model = large,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 88.dp),
                contentScale = ContentScale.Fit
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    "📷 " + (attribution?.line ?: "Wikimedia Commons"),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium
                )
                attribution?.let { a ->
                    TextButton(onClick = { onOpenUrl(a.filePage) }) {
                        Text("Voir sur Wikimedia Commons", color = Color.White)
                    }
                }
            }
        }
    }
}
