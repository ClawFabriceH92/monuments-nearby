package com.fabrice.monumentsnearby.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Découverte géospatiale via le service SPARQL de Wikidata : lieux patrimoniaux
 * situés autour d'une position mais absents d'OpenStreetMap (œuvres d'art
 * public, plaques, sites archéologiques, monuments protégés…).
 * Les éléments retournés portent leur QID : [WikidataClient.enrich] les
 * complète ensuite comme les résultats Overpass.
 */
object WikidataNearby {

    private const val ENDPOINT = "https://query.wikidata.org/sparql"

    private val client = Http.client.newBuilder()
        .readTimeout(40, TimeUnit.SECONDS)
        .build()

    /** Types Wikidata retenus quand l'élément n'a pas de statut patrimonial (P1435). */
    private val TYPES = listOf(
        "Q4989906",  // monument
        "Q5003624",  // mémorial
        "Q860861",   // sculpture
        "Q557141",   // art public
        "Q839954",   // site archéologique
        "Q721747",   // plaque commémorative
        "Q1440300",  // tour d'observation
        "Q16970",    // église
        "Q23413",    // château
        "Q33506"     // musée
    )

    /**
     * Éléments dans un rayon de [radiusM] autour de [lat],[lon], hors [excludeQids].
     * Au plus [limit] résultats, les plus proches d'abord.
     */
    suspend fun discover(
        lat: Double,
        lon: Double,
        radiusM: Int,
        excludeQids: Set<String>,
        limit: Int = 40
    ): List<Monument> = withContext(Dispatchers.IO) {
        val radiusKm = "%.2f".format(java.util.Locale.US, radiusM / 1000.0)
        val point = "Point(%.6f %.6f)".format(java.util.Locale.US, lon, lat)
        val query = """
            SELECT DISTINCT ?item ?itemLabel ?itemDescription ?coord ?typeLabel WHERE {
              SERVICE wikibase:around {
                ?item wdt:P625 ?coord .
                bd:serviceParam wikibase:center "$point"^^geo:wktLiteral .
                bd:serviceParam wikibase:radius "$radiusKm" .
              }
              { ?item wdt:P1435 ?heritage . }
              UNION
              { VALUES ?t { ${TYPES.joinToString(" ") { "wd:$it" }} } ?item wdt:P31 ?t . }
              OPTIONAL { ?item wdt:P31 ?type . }
              SERVICE wikibase:label { bd:serviceParam wikibase:language "fr,en". }
            } LIMIT 300
        """.trimIndent()
        val url = "$ENDPOINT?format=json&query=${URLEncoder.encode(query, "UTF-8")}"
        val request = Request.Builder().url(url).header("Accept", "application/sparql-results+json").build()
        client.newCall(request).execute().use { resp ->
            val text = if (resp.isSuccessful) resp.body?.string() else null
            if (text == null) {
                emptyList()
            } else {
                parse(text, lat, lon, excludeQids, limit)
            }
        }
    }

    internal fun parse(
        json: String,
        lat: Double,
        lon: Double,
        excludeQids: Set<String>,
        limit: Int
    ): List<Monument> {
        val bindings = JSONObject(json).getJSONObject("results").getJSONArray("bindings")
        val byQid = LinkedHashMap<String, Monument>()
        for (i in 0 until bindings.length()) {
            val b = bindings.getJSONObject(i)
            val qid = b.optJSONObject("item")?.optString("value")?.substringAfterLast('/') ?: continue
            if (qid in excludeQids || byQid.containsKey(qid)) continue
            val label = b.optJSONObject("itemLabel")?.optString("value")?.takeIf { it.isNotBlank() } ?: continue
            if (label == qid) continue // pas de libellé fr/en
            val wkt = b.optJSONObject("coord")?.optString("value") ?: continue
            val (pLon, pLat) = parsePoint(wkt) ?: continue
            val type = b.optJSONObject("typeLabel")?.optString("value")?.takeIf { it.isNotBlank() && !it.startsWith("Q") }
            byQid[qid] = Monument(
                id = "wd_$qid",
                name = label.toDisplayName(),
                lat = pLat,
                lon = pLon,
                distanceM = haversineM(lat, lon, pLat, pLon),
                kind = type ?: "lieu patrimonial",
                description = b.optJSONObject("itemDescription")?.optString("value")?.takeIf { it.isNotBlank() },
                wikidataId = qid
            )
        }
        return byQid.values.sortedBy { it.distanceM }.take(limit)
    }

    /** "Point(lon lat)" → (lon, lat). */
    internal fun parsePoint(wkt: String): Pair<Double, Double>? {
        val inner = wkt.substringAfter("Point(", "").substringBefore(")", "").trim()
        val parts = inner.split(Regex("\\s+"))
        if (parts.size != 2) return null
        val lon = parts[0].toDoubleOrNull() ?: return null
        val lat = parts[1].toDoubleOrNull() ?: return null
        return lon to lat
    }

    private fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
