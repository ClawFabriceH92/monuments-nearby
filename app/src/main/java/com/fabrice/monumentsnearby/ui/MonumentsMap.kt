package com.fabrice.monumentsnearby.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.fabrice.monumentsnearby.R
import com.fabrice.monumentsnearby.data.Monument
import com.fabrice.monumentsnearby.data.category
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.tileprovider.modules.SqlTileWriter
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min

/**
 * Poignée pour piloter la carte depuis Compose (recentrage).
 * Obtenue via [rememberMapHandle] et passée à [MonumentsMap].
 */
class MapHandle {
    internal var mapView: MapView? = null

    /** Recentre (animation) sur [lat],[lon] en zoomant assez pour voir les noms. */
    fun recenter(lat: Double, lon: Double) {
        val map = mapView ?: return
        val zoom = maxOf(map.zoomLevelDouble, LABEL_MIN_ZOOM)
        map.controller.animateTo(GeoPoint(lat, lon), zoom, 500L)
    }
}

@Composable
fun rememberMapHandle(): MapHandle = remember { MapHandle() }

/**
 * Carte (osmdroid — gratuit, sans clé API).
 * - Tuiles CARTO (Dark Matter en thème sombre, Positron en clair), haute densité
 * - Repère bleu "je suis ici", suivi de la position en balade guidée
 * - Cercles en pointillés (couleur d'accent) : temps de marche 5 min (400 m) et 15 min (1 200 m)
 * - Épingles regroupées en grappes numérotées sous le zoom [CLUSTER_MAX_ZOOM]
 *   (un tap sur une grappe zoome dessus), épingles individuelles au-delà
 * - Étiquettes avec le nom à partir de [LABEL_MIN_ZOOM], sans chevauchement
 * - [walkPath] : trace l'itinéraire de balade (ligne or)
 */
@Composable
fun MonumentsMap(
    monuments: List<Monument>,
    centerLat: Double,
    centerLon: Double,
    onSelectMonument: (Monument) -> Unit = {},
    walkPath: List<Pair<Double, Double>>? = null,
    livePosition: Pair<Double, Double>? = null,
    mapHandle: MapHandle? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Style de carte dérivé du thème : tuiles sombres + accents cyan en sombre
    val scheme = MaterialTheme.colorScheme
    val style = MapStyle(
        dark = scheme.background.luminance() < 0.5f,
        accent = scheme.primary.toArgb(),
        onAccent = scheme.onPrimary.toArgb(),
        route = scheme.secondary.toArgb(),
        labelFill = scheme.surface.toArgb(),
        labelText = scheme.onSurface.toArgb(),
        labelStroke = scheme.primary.toArgb()
    )

    // Re-clé sur la liste et le style : les épingles sont reconstruites si le
    // filtre ou le thème change pendant que la carte est affichée.
    val bundle = remember(monuments, style) {
        configureOsmdroid(context)
        val myPosition = GeoPoint(centerLat, centerLon)
        lateinit var myMarker: Marker
        val mapView = MapView(context).apply {
            setTileSource(if (style.dark) CARTO_DARK else CARTO_LIGHT)
            setMultiTouchControls(true)
            controller.setZoom(15.0)
            controller.setCenter(myPosition)

            // Attribution des tuiles (obligatoire pour OSM comme pour CARTO)
            overlays.add(
                CopyrightOverlay(context).apply {
                    setTextColor(if (style.dark) Color.LTGRAY else Color.DKGRAY)
                }
            )

            // Cercles de temps de marche : 15 min puis 5 min
            overlays.add(WalkCircle(this, myPosition, WALK_15MIN_M.toDouble(), style.accent))
            overlays.add(WalkCircle(this, myPosition, WALK_5MIN_M.toDouble(), style.accent))

            // Repère "je suis ici" (déplacé en balade guidée)
            myMarker = Marker(this).apply {
                position = myPosition
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = context.getDrawable(R.drawable.ic_my_location)
                setOnMarkerClickListener { _, _ -> true }
            }
            overlays.add(myMarker)

            // Épingles + grappes, puis étiquettes (au-dessus : elles reçoivent
            // le tap en premier, sans masquer les épingles)
            overlays.add(
                MonumentsOverlay(
                    context = context,
                    monuments = monuments,
                    density = resources.displayMetrics.density,
                    scaledDensity = resources.displayMetrics.scaledDensity,
                    style = style,
                    onTap = onSelectMonument
                )
            )
            overlays.add(
                LabelOverlay(
                    monuments = monuments,
                    density = resources.displayMetrics.density,
                    scaledDensity = resources.displayMetrics.scaledDensity,
                    style = style,
                    onTap = onSelectMonument
                )
            )
        }
        MapBundle(mapView, myMarker)
    }
    val mapView = bundle.mapView

    // Poignée de pilotage (recentrage) liée à la vue courante
    DisposableEffect(mapView, mapHandle) {
        mapHandle?.mapView = mapView
        onDispose { if (mapHandle?.mapView === mapView) mapHandle.mapView = null }
    }

    // Position courante en balade guidée : le repère suit la marche
    LaunchedEffect(mapView, livePosition) {
        val target = livePosition?.let { GeoPoint(it.first, it.second) }
            ?: GeoPoint(centerLat, centerLon)
        bundle.myMarker.position = target
        mapView.invalidate()
    }

    // Itinéraire de balade : ligne or, ajoutée/retirée dynamiquement (la
    // MapView est mémorisée, pas recréée).
    DisposableEffect(mapView, walkPath) {
        val polyline = walkPath?.takeIf { it.size >= 2 }?.let { path ->
            Polyline(mapView).apply {
                setPoints(path.map { GeoPoint(it.first, it.second) })
                outlinePaint.color = style.route // or du thème
                outlinePaint.strokeWidth = 9f
                outlinePaint.strokeCap = Paint.Cap.ROUND
                outlinePaint.strokeJoin = Paint.Join.ROUND
            }
        }
        if (polyline != null) {
            // Après l'attribution et les cercles de marche (index 0-2), sous
            // le repère et les épingles
            mapView.overlays.add(3, polyline)
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

private class MapBundle(val mapView: MapView, val myMarker: Marker)

private const val WALK_5MIN_M = 400
private const val WALK_15MIN_M = 1200

/** Zoom à partir duquel les noms des monuments sont affichés sur la carte. */
private const val LABEL_MIN_ZOOM = 16.0

/** En dessous de ce zoom, les épingles proches sont regroupées en grappes. */
private const val CLUSTER_MAX_ZOOM = LABEL_MIN_ZOOM

/** Ancrage vertical de la pointe des épingles (y = 62 sur un viewport de 108). */
private const val PIN_TIP_ANCHOR = 62f / 108f

/** Demi-largeur et hauteur visibles de l'épingle (dp), pour éviter de la couvrir. */
private const val PIN_HALF_WIDTH_DP = 9f
private const val PIN_HEIGHT_DP = 21f

/** Couleurs (ARGB) et variante de tuiles dérivées du thème Compose. */
private data class MapStyle(
    val dark: Boolean,
    val accent: Int,
    val onAccent: Int,
    val route: Int,
    val labelFill: Int,
    val labelText: Int,
    val labelStroke: Int
)

private const val CARTO_ATTRIBUTION = "© OpenStreetMap contributors © CARTO"

private fun cartoSource(name: String, variant: String) = XYTileSource(
    name, 0, 20, 512, "@2x.png",
    arrayOf(
        "https://a.basemaps.cartocdn.com/$variant/",
        "https://b.basemaps.cartocdn.com/$variant/",
        "https://c.basemaps.cartocdn.com/$variant/"
    ),
    CARTO_ATTRIBUTION
)

/** Tuiles CARTO (gratuites, attribution requise), en haute densité (@2x). */
private val CARTO_DARK = cartoSource("CartoDarkMatter2x", "dark_all")
private val CARTO_LIGHT = cartoSource("CartoPositron2x", "light_all")

/** Configuration osmdroid commune (User-Agent, rétention du cache de tuiles). */
private fun configureOsmdroid(context: Context) {
    Configuration.getInstance().apply {
        // User-Agent requis par les serveurs de tuiles
        userAgentValue = context.packageName
        // Les tuiles expirées restent servies 30 jours : utile hors ligne
        expirationExtendedDuration = 30L * 24 * 60 * 60 * 1000
    }
}

/** Avancement d'un téléchargement de tuiles pour l'usage hors ligne. */
sealed class OfflineTilesEvent {
    data class Progress(val done: Int, val total: Int) : OfflineTilesEvent()
    data class Done(val total: Int) : OfflineTilesEvent()
    data class Failed(val errors: Int) : OfflineTilesEvent()
}

/**
 * Télécharge dans le cache osmdroid les tuiles CARTO d'un carré de [radiusM]
 * autour de [lat],[lon] (zooms 12 à 16, 15 au-delà de 5 km). Doit être appelé
 * sur le thread principal ; [onEvent] y est rappelé.
 */
fun downloadOfflineTiles(
    context: Context,
    dark: Boolean,
    lat: Double,
    lon: Double,
    radiusM: Int,
    onEvent: (OfflineTilesEvent) -> Unit
) {
    configureOsmdroid(context)
    val source = if (dark) CARTO_DARK else CARTO_LIGHT
    val manager = CacheManager(source, SqlTileWriter(), 0, 20)
    val dLat = radiusM / 111_320.0
    val dLon = radiusM / (111_320.0 * cos(Math.toRadians(lat)))
    val box = BoundingBox(lat + dLat, lon + dLon, lat - dLat, lon - dLon)
    val maxZoom = if (radiusM > 5000) 15 else 16
    var total = 0
    manager.downloadAreaAsyncNoUI(
        context, box, 12, maxZoom,
        object : CacheManager.CacheManagerCallback {
            override fun onTaskComplete() = onEvent(OfflineTilesEvent.Done(total))
            override fun updateProgress(progress: Int, currentZoomLevel: Int, zoomMin: Int, zoomMax: Int) =
                onEvent(OfflineTilesEvent.Progress(progress, total))
            override fun downloadStarted() = Unit
            override fun setPossibleTilesInArea(total0: Int) {
                total = total0
                onEvent(OfflineTilesEvent.Progress(0, total0))
            }
            override fun onTaskFailed(errors: Int) = onEvent(OfflineTilesEvent.Failed(errors))
        }
    )
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
 * Épingles des monuments et grappes. Sous [CLUSTER_MAX_ZOOM], les épingles
 * tombant dans une même cellule d'écran (~64 dp) sont fusionnées en une
 * pastille numérotée ; un tap dessus zoome de deux niveaux sur la grappe.
 * Un tap sur une épingle ouvre la fiche.
 */
private class MonumentsOverlay(
    context: Context,
    private val monuments: List<Monument>,
    private val density: Float,
    scaledDensity: Float,
    style: MapStyle, // paramètre (pas propriété) : sinon Paint.style le masquerait dans apply {}
    private val onTap: (Monument) -> Unit
) : Overlay() {

    private class Pin(val rect: RectF, val monument: Monument)
    private class Cluster(val x: Float, val y: Float, val radius: Float, val lat: Double, val lon: Double)

    private val drawables = HashMap<Int, Drawable>()
    private fun drawableFor(context: Context, m: Monument): Drawable =
        drawables.getOrPut(pinForCategory(m.category())) { context.getDrawable(pinForCategory(m.category()))!! }

    private val appContext = context.applicationContext
    private val pinWidth: Int
    private val pinHeight: Int
    private val pinTip: Int

    init {
        val sample = appContext.getDrawable(R.drawable.ic_pin_bleu)!!
        pinWidth = sample.intrinsicWidth
        pinHeight = sample.intrinsicHeight
        pinTip = (pinHeight * PIN_TIP_ANCHOR).toInt()
    }

    private val cell = (64f * density).toInt().coerceAtLeast(1)
    private val point = Point()
    private val pins = ArrayList<Pin>()
    private val clusters = ArrayList<Cluster>()

    private val clusterFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = style.accent }
    private val clusterRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = style.labelFill
        this.style = Paint.Style.STROKE
        strokeWidth = 3f * density
    }
    private val clusterText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = style.onAccent
        textSize = 14f * scaledDensity
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        pins.clear()
        clusters.clear()
        val projection = mapView.projection
        val width = canvas.width
        val height = canvas.height
        val clustering = mapView.zoomLevelDouble < CLUSTER_MAX_ZOOM

        // Regroupement par cellule d'écran
        val groups = LinkedHashMap<Long, MutableList<Triple<Monument, Int, Int>>>()
        for (m in monuments) {
            projection.toPixels(GeoPoint(m.lat, m.lon), point)
            val x = point.x
            val y = point.y
            if (x < -cell || x > width + cell || y < -cell || y > height + cell) continue
            val key = if (clustering) {
                (Math.floorDiv(x, cell).toLong() shl 32) or (Math.floorDiv(y, cell).toLong() and 0xffffffffL)
            } else {
                groups.size.toLong() // pas de regroupement : une cellule par épingle
            }
            groups.getOrPut(key) { ArrayList() }.add(Triple(m, x, y))
        }

        for (members in groups.values) {
            if (members.size == 1) {
                val (m, x, y) = members[0]
                drawPin(canvas, m, x, y)
            } else {
                val cx = members.sumOf { it.second }.toFloat() / members.size
                val cy = members.sumOf { it.third }.toFloat() / members.size
                val radius = (16f + min(members.size, 40) * 0.35f) * density
                canvas.drawCircle(cx, cy, radius, clusterFill)
                canvas.drawCircle(cx, cy, radius, clusterRing)
                canvas.drawText(
                    members.size.toString(),
                    cx,
                    cy - (clusterText.ascent() + clusterText.descent()) / 2,
                    clusterText
                )
                clusters.add(
                    Cluster(
                        cx, cy, radius,
                        members.sumOf { it.first.lat } / members.size,
                        members.sumOf { it.first.lon } / members.size
                    )
                )
            }
        }
    }

    private fun drawPin(canvas: Canvas, m: Monument, x: Int, y: Int) {
        val d = drawableFor(appContext, m)
        val left = x - pinWidth / 2
        val top = y - pinTip
        d.setBounds(left, top, left + pinWidth, top + pinHeight)
        d.draw(canvas)
        pins.add(
            Pin(
                RectF(
                    (x - PIN_HALF_WIDTH_DP * density), y - PIN_HEIGHT_DP * density,
                    (x + PIN_HALF_WIDTH_DP * density), y + 2f * density
                ),
                m
            )
        )
    }

    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        clusters.firstOrNull { hypot(e.x - it.x, e.y - it.y) <= it.radius }?.let { c ->
            val zoom = min(mapView.zoomLevelDouble + 2.0, 19.0)
            mapView.controller.animateTo(GeoPoint(c.lat, c.lon), zoom, 400L)
            return true
        }
        val hit = pins.lastOrNull { it.rect.contains(e.x, e.y) } ?: return false
        onTap(hit.monument)
        return true
    }
}

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
    style: MapStyle,
    private val onTap: (Monument) -> Unit
) : Overlay() {

    private val ordered = monuments.sortedByDescending { it.important }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 14f * scaledDensity // 14 sp : suit « Taille de police » du téléphone
        color = style.labelText
        typeface = Typeface.DEFAULT_BOLD
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = style.labelFill
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = style.labelStroke
        this.style = Paint.Style.STROKE
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
 * Cercle géographique avec contour en pointillés (couleur d'accent du thème).
 * osmdroid n'expose pas de setter pour le paint du contour → on configure
 * le champ protégé [PolyOverlayWithIW.mOutlinePaint] depuis une sous-classe.
 */
private class WalkCircle(
    map: MapView,
    center: GeoPoint,
    radiusMeters: Double,
    color: Int
) : Polygon(map) {

    init {
        points = pointsAsCircle(center, radiusMeters)
        setStrokeColor(color)
        setStrokeWidth(4f)
        setFillColor(
            Color.argb(18, Color.red(color), Color.green(color), Color.blue(color))
        )
        mOutlinePaint?.let { paint ->
            paint.style = Paint.Style.STROKE
            paint.pathEffect = DashPathEffect(floatArrayOf(14f, 12f), 0f)
        }
    }
}
