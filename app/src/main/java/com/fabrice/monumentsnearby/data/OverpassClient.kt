package com.fabrice.monumentsnearby.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Client Overpass API (OpenStreetMap) — gratuit, sans clé.
 * Cherche les monuments historiques importants (filtre sémantique sur les tags
 * historic=*) et les musées autour d'une position, et extrait leur QID Wikidata
 * quand il existe (pour l'enrichissement ontologique).
 */
object OverpassClient {

    private const val DEFAULT_RADIUS_M = 3000

    /**
     * Miroirs Overpass. openstreetmap.fr en tête : fiable et pertinent pour la France.
     */
    private val mirrors = listOf(
        "https://overpass.openstreetmap.fr/api/interpreter",
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter"
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        // Overpass exige un User-Agent identifiant l'application
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "MonumentsNearby/0.1 (Android)")
                    .build()
            )
        }
        .build()

    /**
     * @throws Exception si tous les miroirs échouent
     */
    suspend fun fetchMonuments(lat: Double, lon: Double, radiusM: Int = DEFAULT_RADIUS_M): List<Monument> =
        fetch(buildQuery(lat, lon, radiusM), lat, lon)

    /**
     * Musées autour d'une position (tag tourism=museum uniquement).
     * Utilisé par le mode Musée « musées par ville » (rayon élargi).
     */
    suspend fun fetchMuseums(lat: Double, lon: Double, radiusM: Int = 12000): List<Monument> =
        fetch(
            """
            [out:json][timeout:25];
            (
              node["tourism"="museum"](around:$radiusM,$lat,$lon);
              way["tourism"="museum"](around:$radiusM,$lat,$lon);
            );
            out center tags 300;
            """.trimIndent(),
            lat, lon
        )

    /** Exécute une requête Overpass sur les miroirs, puis parse. */
    private suspend fun fetch(query: String, lat: Double, lon: Double): List<Monument> =
        withContext(Dispatchers.IO) {
            val body = ("data=" + URLEncoder.encode(query, "UTF-8"))
                .toRequestBody("application/x-www-form-urlencoded".toMediaType())

            var lastError: Exception? = null
            for (mirror in mirrors) {
                try {
                    val request = Request.Builder()
                        .url(mirror)
                        .post(body)
                        .build()
                    client.newCall(request).execute().use { resp ->
                        if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code}")
                        val bodyText = resp.body?.string() ?: throw RuntimeException("Réponse vide")
                        val json = JSONObject(bodyText)
                        return@withContext parse(json, lat, lon)
                    }
                } catch (e: Exception) {
                    lastError = e
                }
            }
            throw lastError ?: RuntimeException("Tous les miroirs Overpass ont échoué")
        }

    /**
     * Filtre sur les monuments "importants" : on exclut les tags génériques
     * (wayside_cross, boundary_stone, milestone...) qui saturent la requête
     * et polluent la liste.
     */
    private fun buildQuery(lat: Double, lon: Double, radiusM: Int): String = """
        [out:json][timeout:25];
        (
          node["historic"~"monument|castle|ruins|archaeological_site|fort|tower|city_gate|battlefield|manor|palace|church|cathedral|chapel|monastery|convent|abbey|temple|mill|bridge|fountain|memorial|artwork|building|house"](around:$radiusM,$lat,$lon);
          way["historic"~"monument|castle|ruins|archaeological_site|fort|tower|city_gate|battlefield|manor|palace|church|cathedral|chapel|monastery|convent|abbey|temple|mill|bridge|fountain|memorial|artwork|building|house"](around:$radiusM,$lat,$lon);
          node["tourism"="museum"](around:$radiusM,$lat,$lon);
          way["tourism"="museum"](around:$radiusM,$lat,$lon);
        );
        out center tags 100;
    """.trimIndent()

    private fun parse(root: JSONObject, lat: Double, lon: Double): List<Monument> {
        // Overpass peut répondre 200 avec une erreur dans "remark" (ex: timeout)
        val remark = root.optString("remark")
        if (remark.isNotBlank()) throw RuntimeException("Overpass : $remark")

        val elements = root.optJSONArray("elements") ?: return emptyList()
        val result = mutableListOf<Monument>()

        for (i in 0 until elements.length()) {
            val el = elements.getJSONObject(i)
            val tags = el.optJSONObject("tags") ?: continue

            val name = tags.optString("name").takeIf { it.isNotBlank() }
                ?: tags.optString("historic").takeIf { it.isNotBlank() }
                ?: tags.optString("tourism").takeIf { it.isNotBlank() }
                ?: continue

            val center = el.optJSONObject("center")
            val mLat = if (el.has("lat")) el.getDouble("lat") else center?.optDouble("lat", 0.0) ?: 0.0
            val mLon = if (el.has("lon")) el.getDouble("lon") else center?.optDouble("lon", 0.0) ?: 0.0
            if (mLat == 0.0 || mLon == 0.0) continue

            val kind = tags.optString("historic").ifBlank { tags.optString("tourism") }
            val wikipedia = tags.optString("wikipedia").takeIf { it.isNotBlank() }
            result += Monument(
                id = el.optString("type") + "/" + el.optLong("id"),
                name = name,
                lat = mLat,
                lon = mLon,
                distanceM = haversine(lat, lon, mLat, mLon),
                kind = kind,
                description = tags.optString("description").takeIf { it.isNotBlank() }
                    ?: tags.optString("historic").takeIf { it.isNotBlank() && it != kind },
                wikipedia = wikipedia,
                imageUrl = tags.optString("image").takeIf { it.isNotBlank() }
                    ?.replace("http://", "https://"),
                wikidataId = tags.optString("wikidata").takeIf { it.isNotBlank() },
                wikipediaTitle = wikipedia?.substringAfter(':'),
                openingHours = tags.optString("opening_hours").takeIf { it.isNotBlank() },
                fee = tags.optString("charge").takeIf { it.isNotBlank() }
                    ?: tags.optString("fee").takeIf { it == "yes" || it == "no" }
                        ?.let { if (it == "yes") "payant" else "gratuit" }
            )
        }
        return result.sortedBy { it.distanceM }
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        return 2 * r * Math.asin(Math.sqrt(a))
    }
}
