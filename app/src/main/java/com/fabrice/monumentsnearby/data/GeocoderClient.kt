package com.fabrice.monumentsnearby.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder

/**
 * Géocodage de ville via Nominatim (OpenStreetMap) — gratuit, sans clé.
 * Utilisé par le mode Ville (monuments d'une ville) et le mode Musée
 * (« musées par ville ») pour transformer un nom de ville en coordonnées.
 */
object GeocoderClient {

    private const val BASE = "https://nominatim.openstreetmap.org/search"

    private val client = Http.client

    /** Une ville géocodée : nom affichable + coordonnées. */
    data class City(
        val name: String,
        val lat: Double,
        val lon: Double
    )

    /** Géocode un nom de ville — monde entier, libellés en français. */
    suspend fun geocodeCity(query: String): City? =
        withContext(Dispatchers.IO) {
            val url = "$BASE?q=${URLEncoder.encode(query, "UTF-8")}" +
                    "&format=json&limit=1&accept-language=fr"
            // Referer demandé par la politique d'usage de Nominatim
            val request = Request.Builder().url(url)
                .header("Referer", "https://github.com/ClawFabriceH92/monuments-nearby")
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val array = JSONArray(resp.body?.string() ?: return@use null)
                if (array.length() == 0) return@use null
                val first = array.getJSONObject(0)
                City(
                    name = first.optString("display_name").substringBefore(',').trim()
                        .ifBlank { query },
                    lat = first.optDouble("lat", 0.0),
                    lon = first.optDouble("lon", 0.0)
                )
            }
        }
}
