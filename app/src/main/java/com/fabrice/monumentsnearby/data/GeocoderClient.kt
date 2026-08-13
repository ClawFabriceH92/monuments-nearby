package com.fabrice.monumentsnearby.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Géocodage de ville via Nominatim (OpenStreetMap) — gratuit, sans clé.
 * Utilisé par le mode Ville (monuments d'une ville) et le mode Musée
 * (« musées par ville ») pour transformer un nom de ville en coordonnées.
 */
object GeocoderClient {

    private const val BASE = "https://nominatim.openstreetmap.org/search"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "MonumentsNearby/0.1 (Android)")
                    .header("Referer", "https://github.com/ClawFabriceH92/monuments-nearby")
                    .build()
            )
        }
        .build()

    /** Une ville géocodée : nom affichable + coordonnées. */
    data class City(
        val name: String,
        val lat: Double,
        val lon: Double
    )

    /** Géocode un nom de ville (France prioritaire). Retourne null si introuvable. */
    suspend fun geocodeCity(query: String): City? =
        withContext(Dispatchers.IO) {
            val url = "$BASE?q=${URLEncoder.encode(query, "UTF-8")}" +
                    "&format=json&limit=1&countrycodes=fr&accept-language=fr"
            val request = Request.Builder().url(url).build()
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
