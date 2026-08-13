package com.fabrice.monumentsnearby.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Entrée du carnet : monument favori (suffisant pour rouvrir sa fiche). */
data class FavoriteEntry(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val kind: String,
    val imageUrl: String? = null,
    val description: String? = null,
    val wikidataId: String? = null
)

/**
 * Carnet de visites : favoris + marqués visités, persistés en SharedPreferences.
 */
class VisitRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("carnet", Context.MODE_PRIVATE)

    fun favorites(): List<FavoriteEntry> {
        val raw = prefs.getString("favorites", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                FavoriteEntry(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    lat = o.optDouble("lat"),
                    lon = o.optDouble("lon"),
                    kind = o.optString("kind"),
                    imageUrl = o.optString("imageUrl").takeIf { it.isNotBlank() },
                    description = o.optString("description").takeIf { it.isNotBlank() },
                    wikidataId = o.optString("wikidataId").takeIf { it.isNotBlank() }
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveFavorites(list: List<FavoriteEntry>) {
        val arr = JSONArray()
        list.forEach { f ->
            arr.put(
                JSONObject()
                    .put("id", f.id)
                    .put("name", f.name)
                    .put("lat", f.lat)
                    .put("lon", f.lon)
                    .put("kind", f.kind)
                    .put("imageUrl", f.imageUrl ?: "")
                    .put("description", f.description ?: "")
                    .put("wikidataId", f.wikidataId ?: "")
            )
        }
        prefs.edit().putString("favorites", arr.toString()).apply()
    }

    fun isFavorite(id: String): Boolean = favorites().any { it.id == id }

    fun toggleFavorite(m: FavoriteEntry): List<FavoriteEntry> {
        val list = favorites().toMutableList()
        val existing = list.indexOfFirst { it.id == m.id }
        if (existing >= 0) list.removeAt(existing) else list.add(0, m)
        saveFavorites(list)
        return list
    }

    /** Monuments visités : id → nom. */
    fun visited(): Map<String, String> {
        val set = prefs.getStringSet("visited", emptySet()) ?: emptySet()
        return set.mapNotNull { line ->
            val idx = line.indexOf("::")
            if (idx > 0) line.substring(0, idx) to line.substring(idx + 2) else null
        }.toMap()
    }

    fun isVisited(id: String): Boolean = visited().containsKey(id)

    fun toggleVisited(id: String, name: String): Map<String, String> {
        val map = visited().toMutableMap()
        if (map.remove(id) == null) map[id] = name
        prefs.edit().putStringSet("visited", map.map { "${it.key}::${it.value}" }.toSet()).apply()
        return map
    }
}
