package com.fabrice.monumentsnearby.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Suggestions de villes pendant la frappe via Photon (komoot, OpenStreetMap)
 * — gratuit, sans clé, conçu pour l'autocomplétion (contrairement à
 * Nominatim, dont la politique l'interdit).
 */
object PhotonClient {

    private const val BASE = "https://photon.komoot.io/api/"
    private val client = Http.client

    /** Une ville suggérée : nom, contexte (région/pays) et coordonnées. */
    data class Suggestion(
        val name: String,
        val context: String,
        val lat: Double,
        val lon: Double
    ) {
        val label: String get() = if (context.isBlank()) name else "$name — $context"
    }

    /** Villes, bourgs et villages correspondant au début de [query]. */
    suspend fun suggestCities(query: String, limit: Int = 6): List<Suggestion> =
        withContext(Dispatchers.IO) {
            if (query.length < 2) return@withContext emptyList()
            val url = "$BASE?q=${URLEncoder.encode(query, "UTF-8")}&limit=${limit * 2}&lang=fr" +
                "&osm_tag=place:city&osm_tag=place:town&osm_tag=place:village"
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { resp ->
                val text = if (resp.isSuccessful) resp.body?.string() else null
                if (text == null) {
                    emptyList()
                } else {
                    val features = JSONObject(text).optJSONArray("features")
                    val result = ArrayList<Suggestion>()
                    val seen = HashSet<String>()
                    for (i in 0 until (features?.length() ?: 0)) {
                        val f = features!!.getJSONObject(i)
                        val props = f.optJSONObject("properties") ?: continue
                        val coords = f.optJSONObject("geometry")?.optJSONArray("coordinates") ?: continue
                        val name = props.optString("name").takeIf { it.isNotBlank() } ?: continue
                        val context = listOfNotNull(
                            props.optString("state").takeIf { it.isNotBlank() },
                            props.optString("country").takeIf { it.isNotBlank() }
                        ).joinToString(", ")
                        val key = "$name|$context"
                        if (!seen.add(key)) continue
                        result += Suggestion(name, context, coords.getDouble(1), coords.getDouble(0))
                        if (result.size >= limit) break
                    }
                    result
                }
            }
        }
}
