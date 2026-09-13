package com.fabrice.monumentsnearby.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Itinéraire piéton réel via Valhalla (instance publique FOSSGIS, gratuite,
 * sans clé, usage raisonnable). Retourne la géométrie du trajet passant par
 * tous les points, ou null en cas d'échec — l'appelant retombe alors sur des
 * lignes droites.
 */
object RouteClient {

    private const val ENDPOINT = "https://valhalla1.openstreetmap.de/route"
    private val json = "application/json; charset=utf-8".toMediaType()

    private val client = Http.client.newBuilder()
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * @param points points de passage (lat, lon), dans l'ordre, au moins deux
     * @return polyligne (lat, lon) ou null
     */
    suspend fun pedestrianRoute(points: List<Pair<Double, Double>>): List<Pair<Double, Double>>? {
        if (points.size < 2) return null
        return withContext(Dispatchers.IO) {
            try {
                val locations = JSONArray()
                points.forEach { (lat, lon) ->
                    locations.put(
                        JSONObject().put("lat", lat).put("lon", lon).put("type", "break")
                    )
                }
                val body = JSONObject()
                    .put("locations", locations)
                    .put("costing", "pedestrian")
                    .put("units", "kilometers")
                    .toString()
                val request = Request.Builder()
                    .url(ENDPOINT)
                    .post(body.toRequestBody(json))
                    .build()
                client.newCall(request).execute().use { response ->
                    val text = if (response.isSuccessful) response.body?.string() else null
                    if (text == null) {
                        null
                    } else {
                        val legs = JSONObject(text).getJSONObject("trip").getJSONArray("legs")
                        val path = ArrayList<Pair<Double, Double>>()
                        for (i in 0 until legs.length()) {
                            val shape = legs.getJSONObject(i).optString("shape")
                            val decoded = decodePolyline6(shape)
                            // Le premier point d'une étape répète le dernier de la précédente
                            path.addAll(
                                if (path.isNotEmpty() && decoded.isNotEmpty()) decoded.drop(1) else decoded
                            )
                        }
                        path.takeIf { it.size >= 2 }
                    }
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    /** Décode une polyligne encodée (précision 1e-6, format Valhalla). */
    internal fun decodePolyline6(encoded: String): List<Pair<Double, Double>> {
        val result = ArrayList<Pair<Double, Double>>()
        var index = 0
        var lat = 0
        var lon = 0
        while (index < encoded.length) {
            var shift = 0
            var value = 0
            var b: Int
            do {
                b = encoded[index++].code - 63
                value = value or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20 && index < encoded.length)
            val dLat = if (value and 1 != 0) (value shr 1).inv() else value shr 1
            lat += dLat

            shift = 0
            value = 0
            do {
                b = encoded[index++].code - 63
                value = value or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20 && index < encoded.length)
            val dLon = if (value and 1 != 0) (value shr 1).inv() else value shr 1
            lon += dLon

            result.add(lat / 1e6 to lon / 1e6)
        }
        return result
    }
}
