package com.fabrice.monumentsnearby.ui

import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.fabrice.monumentsnearby.R
import com.fabrice.monumentsnearby.data.Monument
import com.fabrice.monumentsnearby.data.category
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

/**
 * Carte OpenStreetMap (osmdroid — gratuit, sans clé API).
 * - Repère bleu "je suis ici"
 * - Cercles rouges en pointillés : temps de marche 5 min (400 m) et 15 min (1 200 m)
 *   (vitesse de marche 4,8 km/h)
 * - Un marqueur par monument
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

                val myPosition = GeoPoint(centerLat, centerLon)

                // Cercles de temps de marche : 15 min puis 5 min
                overlays.add(WalkCircle(this, myPosition, WALK_15MIN_M.toDouble()))
                overlays.add(WalkCircle(this, myPosition, WALK_5MIN_M.toDouble()))

                // Repère "je suis ici"
                overlays.add(
                    Marker(this).apply {
                        position = myPosition
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = ctx.getDrawable(R.drawable.ic_my_location)
                    }
                )

                // Marqueurs des monuments — couleur selon le type
                monuments.forEach { m ->
                    overlays.add(
                        Marker(this).apply {
                            position = GeoPoint(m.lat, m.lon)
                            title = m.name
                            snippet = m.description ?: m.kind.replace('_', ' ')
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            icon = ctx.getDrawable(pinForCategory(m.category()))
                        }
                    )
                }
            }
        }
    )
}

private const val WALK_5MIN_M = 400
private const val WALK_15MIN_M = 1200

/**
 * Couleur du marqueur selon la catégorie du monument :
 * rouge = musée, violet = religieux, orange = château/palais/fort,
 * marron = ruines, bleu = monument/mémorial, vert = autre.
 */
private fun pinForCategory(category: String): Int = when (category) {
    "musée" -> R.drawable.ic_pin_rouge
    "religieux" -> R.drawable.ic_pin_violet
    "château" -> R.drawable.ic_pin_orange
    "ruines" -> R.drawable.ic_pin_marron
    "monument" -> R.drawable.ic_pin_bleu
    else -> R.drawable.ic_pin_vert
}

/**
 * Cercle géographique avec contour rouge en pointillés.
 * osmdroid n'expose pas de setter pour le paint du contour → on configure
 * le champ protégé [PolyOverlayWithIW.mOutlinePaint] depuis une sous-classe.
 */
private class WalkCircle(
    map: MapView,
    center: GeoPoint,
    radiusMeters: Double
) : Polygon(map) {

    init {
        points = pointsAsCircle(center, radiusMeters)
        setStrokeColor(Color.RED)
        setStrokeWidth(4f)
        setFillColor(Color.argb(18, 255, 0, 0))
        mOutlinePaint?.let { paint ->
            paint.style = Paint.Style.STROKE
            paint.pathEffect = DashPathEffect(floatArrayOf(14f, 12f), 0f)
        }
    }
}
