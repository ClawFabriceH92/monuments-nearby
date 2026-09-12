package com.fabrice.monumentsnearby.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.fabrice.monumentsnearby.R
import com.fabrice.monumentsnearby.data.Monument
import com.fabrice.monumentsnearby.data.category
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline

/**
 * Carte OpenStreetMap (osmdroid — gratuit, sans clé API).
 * - Repère bleu "je suis ici"
 * - Cercles rouges en pointillés : temps de marche 5 min (400 m) et 15 min (1 200 m)
 *   (vitesse de marche 4,8 km/h)
 * - Un marqueur par monument — un tap ouvre la fiche détail
 * - Étiquettes avec le nom à partir d'un zoom suffisant ([LABEL_MIN_ZOOM]),
 *   sans chevauchement (les étiquettes qui se recouvriraient sont omises)
 * - [walkRoute] : trace l'itinéraire de balade (ligne or) depuis la position
 */
@Composable
fun MonumentsMap(
    monuments: List<Monument>,
    centerLat: Double,
    centerLon: Double,
    onSelectMonument: (Monument) -> Unit = {},
    walkRoute: List<Monument>? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Re-clé sur la liste : les marqueurs sont reconstruits si le filtre change
    // pendant que la carte est affichée.
    val mapView = remember(monuments) {
        // User-Agent requis par les serveurs de tuiles OSM
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
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
                    icon = context.getDrawable(R.drawable.ic_my_location)
                }
            )

            // Marqueurs des monuments — couleur selon le type, tap → fiche
            monuments.forEach { m ->
                overlays.add(
                    Marker(this).apply {
                        position = GeoPoint(m.lat, m.lon)
                        title = m.name
                        snippet = m.description ?: m.kind.replace('_', ' ')
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        icon = context.getDrawable(pinForCategory(m.category()))
                        setOnMarkerClickListener { _, _ ->
                            onSelectMonument(m)
                            true
                        }
                    }
                )
            }

            // Étiquettes de nom (au-dessus des marqueurs : elles reçoivent le
            // tap en premier, sans masquer les épingles)
            overlays.add(
                LabelOverlay(monuments, resources.displayMetrics.density, onSelectMonument)
            )
        }
    }

    // Itinéraire de balade : ligne or position → étapes, ajoutée/retirée
    // dynamiquement (la MapView est mémorisée, pas recréée).
    DisposableEffect(mapView, walkRoute) {
        val polyline = walkRoute?.takeIf { it.isNotEmpty() }?.let { stops ->
            Polyline(mapView).apply {
                setPoints(
                    listOf(GeoPoint(centerLat, centerLon)) +
                        stops.map { GeoPoint(it.lat, it.lon) }
                )
                outlinePaint.color = Color.rgb(201, 151, 43) // or du thème
                outlinePaint.strokeWidth = 9f
            }
        }
        if (polyline != null) {
            // Après les cercles de marche (index 0-1), sous les marqueurs
            mapView.overlays.add(2, polyline)
            mapView.invalidate()
        }
        onDispose {
            if (polyline != null) {
                mapView.overlays.remove(polyline)
                mapView.invalidate()
            }
        }
    }

    // Relayer resume/pause à osmdroid (tuiles, capteurs) et détacher à la sortie
    DisposableEffect(mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    // key : AndroidView ne ré-exécute pas sa factory quand mapView change,
    // il faut recréer le nœud pour attacher la nouvelle vue.
    // clipToBounds : osmdroid dessine ses tuiles au-delà de sa zone et Compose
    // ne rogne pas une AndroidView par défaut → la carte débordait sur la barre
    // d'outils au-dessus.
    key(mapView) {
        AndroidView(
            modifier = modifier
                .fillMaxSize()
                .clipToBounds(),
            factory = { mapView }
        )
    }
}

private const val WALK_5MIN_M = 400
private const val WALK_15MIN_M = 1200

/** Zoom à partir duquel les noms des monuments sont affichés sur la carte. */
private const val LABEL_MIN_ZOOM = 16.0

/**
 * Étiquettes « nom du monument » dessinées à droite de chaque épingle quand la
 * carte est assez zoomée. Les monuments majeurs sont placés en premier ; une
 * étiquette qui chevaucherait une étiquette déjà posée n'est pas dessinée, pour
 * que le contenu reste lisible. Un tap sur une étiquette ouvre la fiche.
 */
private class LabelOverlay(
    monuments: List<Monument>,
    private val density: Float,
    private val onTap: (Monument) -> Unit
) : Overlay() {

    private val ordered = monuments.sortedByDescending { it.important }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12f * density
        color = Color.rgb(26, 41, 72) // bleu nuit du thème
        typeface = Typeface.DEFAULT_BOLD
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(235, 255, 255, 255)
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(201, 151, 43) // or du thème
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }
    private val padding = 5f * density
    private val offsetX = 10f * density
    private val maxTextWidth = 170f * density
    private val corner = 6f * density
    private val point = Point()

    /** Étiquettes posées lors du dernier dessin, pour le hit-test au tap. */
    private val placed = ArrayList<Pair<RectF, Monument>>()

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        placed.clear()
        if (mapView.zoomLevelDouble < LABEL_MIN_ZOOM) return

        val projection = mapView.projection
        val textHeight = textPaint.descent() - textPaint.ascent()
        val width = canvas.width.toFloat()
        val height = canvas.height.toFloat()

        for (m in ordered) {
            projection.toPixels(GeoPoint(m.lat, m.lon), point)
            val label = TextUtils.ellipsize(
                m.name, textPaint, maxTextWidth, TextUtils.TruncateAt.END
            ).toString()
            if (label.isEmpty()) continue
            val textWidth = textPaint.measureText(label)
            // À droite de l'épingle, centré sur le milieu de l'icône (≈ 14 dp
            // au-dessus de la pointe)
            val centerY = point.y - 14f * density
            val rect = RectF(
                point.x + offsetX,
                centerY - textHeight / 2 - padding,
                point.x + offsetX + textWidth + 2 * padding,
                centerY + textHeight / 2 + padding
            )
            if (rect.right < 0f || rect.left > width || rect.bottom < 0f || rect.top > height) continue
            if (placed.any { RectF.intersects(it.first, rect) }) continue

            canvas.drawRoundRect(rect, corner, corner, fillPaint)
            canvas.drawRoundRect(rect, corner, corner, strokePaint)
            canvas.drawText(
                label,
                rect.left + padding,
                centerY - (textPaint.ascent() + textPaint.descent()) / 2,
                textPaint
            )
            placed.add(rect to m)
        }
    }

    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        val hit = placed.firstOrNull { it.first.contains(e.x, e.y) } ?: return false
        onTap(hit.second)
        return true
    }
}

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
