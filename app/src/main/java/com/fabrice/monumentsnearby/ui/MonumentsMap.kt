package com.fabrice.monumentsnearby.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.fabrice.monumentsnearby.data.Monument
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/**
 * Carte OpenStreetMap (osmdroid — gratuit, sans clé API).
 * Affiche la position centrale et un marqueur par monument.
 */
@Composable
fun MonumentsMap(
    monuments: List<Monument>,
    centerLat: Double,
    centerLon: Double,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            // User-Agent requis par les serveurs de tuiles OSM
            Configuration.getInstance().userAgentValue = ctx.packageName
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(15.0)
                controller.setCenter(GeoPoint(centerLat, centerLon))
                monuments.forEach { m ->
                    val marker = Marker(this).apply {
                        position = GeoPoint(m.lat, m.lon)
                        title = m.name
                        snippet = m.description ?: m.kind.replace('_', ' ')
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    }
                    overlays.add(marker)
                }
            }
        }
    )
}
