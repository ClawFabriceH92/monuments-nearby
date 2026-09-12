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
                        // La pointe de l'épingle est à 57 % de la hauteur du
                        // dessin (pas en bas) : ancrer dessus pour que la
                        // pointe tombe exactement sur le lieu.
                        setAnchor(Marker.ANCHOR_CENTER, PIN_TIP_ANCHOR)
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
                LabelOverlay(
                    monuments = monuments,
                    density = resources.displayMetrics.density,
                    scaledDensity = resources.displayMetrics.scaledDensity,
                    onTap = onSelectMonument
                )
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

/** Ancrage vertical de la pointe des épingles (y = 62 sur un viewport de 108). */
private const val PIN_TIP_ANCHOR = 62f / 108f

/** Demi-largeur et hauteur visibles de l'épingle (dp), pour éviter de la couvrir. */
private const val PIN_HALF_WIDTH_DP = 9f
private const val PIN_HEIGHT_DP = 21f

/**
 * Étiquettes « nom du monument » dessinées à côté de chaque épingle quand la
 * carte est assez zoomée. Le texte suit la taille de police du téléphone.
 * Chaque étiquette est posée à droite, à gauche, au-dessus ou en dessous de
 * l'épingle, à la première place qui ne recouvre ni une autre étiquette ni une
 * épingle ; sans place libre, elle n'est pas dessinée (monuments majeurs
 * servis en premier). Un tap sur une étiquette ouvre la fiche.
 */
private class LabelOverlay(
    monuments: List<Monument>,
    private val density: Float,
    scaledDensity: Float,
    private val onTap: (Monument) -> Unit
) : Overlay() {

    private val ordered = monuments.sortedByDescending { it.important }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 14f * scaledDensity // 14 sp : suit « Taille de police » du téléphone
        color = Color.rgb(26, 41, 72) // bleu nuit du thème
        typeface = Typeface.DEFAULT_BOLD
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(201, 151, 43) // or du thème
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    private val padding = 6f * density
    private val gap = 3f * density
    private val maxTextWidth = 200f * density
    private val corner = 8f * density
    private val pinHalfWidth = PIN_HALF_WIDTH_DP * density
    private val pinHeight = PIN_HEIGHT_DP * density
    private val point = Point()

    /** Étiquettes posées lors du dernier dessin, pour le hit-test au tap. */
    private val placed = ArrayList<Pair<RectF, Monument>>()
    private val pins = ArrayList<RectF>()

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        placed.clear()
        pins.clear()
        if (mapView.zoomLevelDouble < LABEL_MIN_ZOOM) return

        val projection = mapView.projection
        val width = canvas.width.toFloat()
        val height = canvas.height.toFloat()
        val textHeight = textPaint.descent() - textPaint.ascent()
        val labelHeight = textHeight + 2 * padding

        // Position écran de chaque épingle (la pointe est sur le lieu)
        val anchors = ordered.map { m ->
            projection.toPixels(GeoPoint(m.lat, m.lon), point)
            val x = point.x.toFloat()
            val y = point.y.toFloat()
            pins.add(RectF(x - pinHalfWidth, y - pinHeight, x + pinHalfWidth, y + gap))
            x to y
        }

        ordered.forEachIndexed { index, m ->
            val (x, y) = anchors[index]
            if (x < -width || x > 2 * width || y < -height || y > 2 * height) return@forEachIndexed
            val label = TextUtils.ellipsize(
                m.name, textPaint, maxTextWidth, TextUtils.TruncateAt.END
            ).toString()
            if (label.isEmpty()) return@forEachIndexed
            val labelWidth = textPaint.measureText(label) + 2 * padding
            val pinCenterY = y - pinHeight / 2

            // Candidats : droite, gauche, au-dessus, en dessous de l'épingle
            val candidates = listOf(
                RectF(x + pinHalfWidth + gap, pinCenterY - labelHeight / 2,
                    x + pinHalfWidth + gap + labelWidth, pinCenterY + labelHeight / 2),
                RectF(x - pinHalfWidth - gap - labelWidth, pinCenterY - labelHeight / 2,
                    x - pinHalfWidth - gap, pinCenterY + labelHeight / 2),
                RectF(x - labelWidth / 2, y - pinHeight - gap - labelHeight,
                    x + labelWidth / 2, y - pinHeight - gap),
                RectF(x - labelWidth / 2, y + gap * 2,
                    x + labelWidth / 2, y + gap * 2 + labelHeight)
            )
            val rect = candidates.firstOrNull { c ->
                c.left >= 0f && c.right <= width && c.top >= 0f && c.bottom <= height &&
                    placed.none { RectF.intersects(it.first, c) } &&
                    pins.none { RectF.intersects(it, c) }
            } ?: return@forEachIndexed

            canvas.drawRoundRect(rect, corner, corner, fillPaint)
            canvas.drawRoundRect(rect, corner, corner, strokePaint)
            canvas.drawText(
                label,
                rect.left + padding,
                rect.centerY() - (textPaint.ascent() + textPaint.descent()) / 2,
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
