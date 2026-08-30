package com.fabrice.monumentsnearby.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Cache hors-ligne du dernier résultat de recherche (monuments « Autour de
 * moi » ou d'une ville), persisté en JSON dans le stockage interne.
 * - Affiché quand la localisation ou le réseau échouent
 * - Lu par le worker « Monument du jour »
 */
object MonumentCache {

    private const val FILE_NAME = "last_results.json"

    data class Cached(
        val monuments: List<Monument>,
        val lat: Double,
        val lon: Double,
        val title: String,
        val savedAt: Long
    )

    fun save(context: Context, monuments: List<Monument>, lat: Double, lon: Double, title: String) {
        try {
            val root = JSONObject()
                .put("lat", lat)
                .put("lon", lon)
                .put("title", title)
                .put("savedAt", System.currentTimeMillis())
                .put("monuments", JSONArray().also { arr ->
                    monuments.forEach { arr.put(toJson(it)) }
                })
            File(context.filesDir, FILE_NAME).writeText(root.toString())
        } catch (e: Exception) {
            // cache best-effort : un échec d'écriture ne doit rien casser
        }
    }

    fun load(context: Context): Cached? {
        return try {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) return null
            val root = JSONObject(file.readText())
            val arr = root.optJSONArray("monuments") ?: return null
            val monuments = (0 until arr.length()).mapNotNull { i ->
                fromJson(arr.optJSONObject(i) ?: return@mapNotNull null)
            }
            if (monuments.isEmpty()) return null
            Cached(
                monuments = monuments,
                lat = root.optDouble("lat", 0.0),
                lon = root.optDouble("lon", 0.0),
                title = root.optString("title").ifBlank { "Derniers résultats" },
                savedAt = root.optLong("savedAt")
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun toJson(m: Monument): JSONObject = JSONObject()
        .put("id", m.id)
        .put("name", m.name)
        .put("lat", m.lat)
        .put("lon", m.lon)
        .put("distanceM", m.distanceM)
        .put("kind", m.kind)
        .putOpt("description", m.description)
        .putOpt("wikipedia", m.wikipedia)
        .putOpt("imageUrl", m.imageUrl)
        .putOpt("wikidataId", m.wikidataId)
        .putOpt("wikipediaTitle", m.wikipediaTitle)
        .putOpt("inception", m.inception)
        .putOpt("artist", m.artist)
        .putOpt("commonsCategory", m.commonsCategory)
        .putOpt("architect", m.architect)
        .putOpt("style", m.style)
        .putOpt("material", m.material)
        .putOpt("heritage", m.heritage)
        .putOpt("founder", m.founder)
        .putOpt("owner", m.owner)
        .putOpt("website", m.website)
        .putOpt("openingHours", m.openingHours)
        .putOpt("fee", m.fee)
        .putOpt("heritageYear", m.heritageYear)
        .putOpt("merimeeRef", m.merimeeRef)
        .putOpt("museofileRef", m.museofileRef)
        .putOpt("namedAfter", m.namedAfter)
        .put("events", JSONArray(m.events))
        .putOpt("openedYear", m.openedYear)
        .putOpt("address", m.address)
        .putOpt("commune", m.commune)
        .put("important", m.important)

    private fun fromJson(o: JSONObject): Monument? {
        val id = o.optString("id").takeIf { it.isNotBlank() } ?: return null
        val name = o.optString("name").takeIf { it.isNotBlank() } ?: return null
        fun opt(key: String): String? = o.optString(key).takeIf { it.isNotBlank() }
        return Monument(
            id = id,
            name = name,
            lat = o.optDouble("lat", 0.0),
            lon = o.optDouble("lon", 0.0),
            distanceM = o.optDouble("distanceM", 0.0),
            kind = o.optString("kind"),
            description = opt("description"),
            wikipedia = opt("wikipedia"),
            imageUrl = opt("imageUrl"),
            wikidataId = opt("wikidataId"),
            wikipediaTitle = opt("wikipediaTitle"),
            inception = opt("inception"),
            artist = opt("artist"),
            commonsCategory = opt("commonsCategory"),
            architect = opt("architect"),
            style = opt("style"),
            material = opt("material"),
            heritage = opt("heritage"),
            founder = opt("founder"),
            owner = opt("owner"),
            website = opt("website"),
            openingHours = opt("openingHours"),
            fee = opt("fee"),
            heritageYear = opt("heritageYear"),
            merimeeRef = opt("merimeeRef"),
            museofileRef = opt("museofileRef"),
            namedAfter = opt("namedAfter"),
            events = o.optJSONArray("events")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    arr.optString(i).takeIf { it.isNotBlank() }
                }
            } ?: emptyList(),
            openedYear = opt("openedYear"),
            address = opt("address"),
            commune = opt("commune"),
            important = o.optBoolean("important", false)
        )
    }
}
