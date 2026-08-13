package com.fabrice.monumentsnearby.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Enrichissement sémantique via Wikidata (l'ontologie de Wikimedia) :
 * - label + description en français
 * - type ontologique (P31 : château, église, musée...) résolu en label FR
 * - date de création (P571)
 * - image Wikimedia Commons (P18)
 *
 * Gratuit, sans clé. Les APIs Wikimedia limitent à 50 IDs par appel (batch).
 * L'enrichissement n'est jamais bloquant : en cas d'erreur, on garde les
 * données OSM brutes.
 */
object WikidataClient {

    private const val BASE = "https://www.wikidata.org/w/api.php"
    private const val MAX_IDS = 50

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "MonumentsNearby/0.1 (Android)")
                    .build()
            )
        }
        .build()

    suspend fun enrich(monuments: List<Monument>): List<Monument> {
        val withIds = monuments.filter { !it.wikidataId.isNullOrBlank() }
        if (withIds.isEmpty()) return monuments
        return try {
            withContext(Dispatchers.IO) {
                val entities = fetchEntities(withIds.map { it.wikidataId!! })
                val typeLabels = fetchTypeLabels(entities)
                val enriched = monuments.map { m ->
                    val qid = m.wikidataId ?: return@map m
                    val entity = entities[qid] ?: return@map m
                    enrichOne(m, entity, typeLabels)
                }
                deduplicate(enriched).map { it.copy(important = isMajor(it)) }
            }
        } catch (e: Exception) {
            monuments // enrichissement non bloquant
        }
    }

    /**
     * Un même monument peut apparaître plusieurs fois dans OSM (node + repères
     * voisins) avec le même QID Wikidata → on garde l'occurrence la plus proche.
     */
    private fun deduplicate(monuments: List<Monument>): List<Monument> {
        val best = mutableMapOf<String, Monument>()
        for (m in monuments) {
            val key = m.wikidataId?.takeIf { it.isNotBlank() } ?: m.id
            val existing = best[key]
            if (existing == null || m.distanceM < existing.distanceM) {
                best[key] = m
            }
        }
        return best.values.sortedBy { it.distanceM }
    }

    /**
     * Un monument est "majeur" s'il a un article Wikipédia dédié (proxy de
     * notoriété) ou si son type est un monument d'importance (château, église…).
     */
    private val MAJOR_KINDS = setOf(
        // tags OSM
        "monument", "castle", "church", "cathedral", "abbey", "monastery", "convent",
        "palace", "manor", "museum", "ruins", "archaeological_site", "fort", "tower",
        "city_gate", "temple", "mill", "bridge", "battlefield",
        // labels Wikidata (FR)
        "château", "église", "cathédrale", "abbaye", "monastère", "palais", "musée",
        "ruines", "site archéologique", "fort", "tour", "temple", "moulin", "pont",
        "manoir", "hôtel particulier", "basilique", "chapelle", "champ de bataille"
    )

    private fun isMajor(m: Monument): Boolean {
        if (!m.wikipediaTitle.isNullOrBlank()) return true
        return MAJOR_KINDS.contains(m.kind.lowercase())
    }

    private suspend fun fetchEntities(ids: List<String>): Map<String, JSONObject> {
        val result = mutableMapOf<String, JSONObject>()
        for (chunk in ids.chunked(MAX_IDS)) {
            val url = "$BASE?action=wbgetentities&ids=${chunk.joinToString("|")}" +
                    "&props=labels|descriptions|claims|sitelinks&languages=fr&format=json"
            val root = getJson(url)
            val entities = root.optJSONObject("entities") ?: continue
            val keys = entities.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                result[key] = entities.getJSONObject(key)
            }
        }
        return result
    }

    private suspend fun fetchTypeLabels(entities: Map<String, JSONObject>): Map<String, String> {
        val typeIds = LinkedHashSet<String>()
        for (entity in entities.values) {
            val p31 = entity.optJSONObject("claims")?.optJSONArray("P31") ?: continue
            for (i in 0 until p31.length()) {
                val value = p31.getJSONObject(i).optJSONObject("mainsnak")
                    ?.optJSONObject("datavalue")?.optJSONObject("value")
                if (value?.optString("entity-type") == "item") {
                    value.optString("id").takeIf { it.isNotBlank() }?.let { typeIds.add(it) }
                }
            }
        }
        if (typeIds.isEmpty()) return emptyMap()

        val result = mutableMapOf<String, String>()
        for (chunk in typeIds.chunked(MAX_IDS)) {
            val url = "$BASE?action=wbgetentities&ids=${chunk.joinToString("|")}" +
                    "&props=labels&languages=fr&format=json"
            val root = getJson(url)
            val entities = root.optJSONObject("entities") ?: continue
            val keys = entities.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val label = entities.getJSONObject(key).optJSONObject("labels")
                    ?.optJSONObject("fr")?.optString("value")
                if (!label.isNullOrBlank()) result[key] = label
            }
        }
        return result
    }

    private fun enrichOne(m: Monument, entity: JSONObject, typeLabels: Map<String, String>): Monument {
        val labels = entity.optJSONObject("labels")
        val descriptions = entity.optJSONObject("descriptions")
        val claims = entity.optJSONObject("claims")

        val label = labels?.optJSONObject("fr")?.optString("value")
        val desc = descriptions?.optJSONObject("fr")?.optString("value")

        // Fallback : titre Wikipédia depuis le sitelink frwiki de Wikidata
        var wikiTitle = m.wikipediaTitle
        if (wikiTitle.isNullOrBlank()) {
            entity.optJSONObject("sitelinks")?.optJSONObject("frwiki")?.optString("title")
                ?.takeIf { it.isNotBlank() }?.let { wikiTitle = it }
        }

        // Type ontologique : premier P31 résolu en label FR
        var typeLabel: String? = null
        val p31 = claims?.optJSONArray("P31")
        if (p31 != null) {
            for (i in 0 until p31.length()) {
                val value = p31.getJSONObject(i).optJSONObject("mainsnak")
                    ?.optJSONObject("datavalue")?.optJSONObject("value")
                if (value?.optString("entity-type") == "item") {
                    val resolved = typeLabels[value.optString("id")]
                    if (resolved != null) {
                        typeLabel = resolved
                        break
                    }
                }
            }
        }

        // Date de création (P571) → année, format "+1661-00-00T00:00:00Z" → "1661"
        var inception: String? = null
        val p571 = claims?.optJSONArray("P571")
        if (p571 != null) {
            for (i in 0 until p571.length()) {
                val time = p571.getJSONObject(i).optJSONObject("mainsnak")
                    ?.optJSONObject("datavalue")?.optJSONObject("value")?.optString("time")
                if (!time.isNullOrBlank()) {
                    val year = time.trimStart('+').takeWhile { it != '-' }
                    if (year.isNotBlank()) { // P571 peut être avant J.-C. → pas d'année exploitable
                        inception = year
                        break
                    }
                }
            }
        }

        // Image Commons (P18) → URL Special:FilePath (espaces → underscores)
        var imageUrl: String? = null
        val p18 = claims?.optJSONArray("P18")
        if (p18 != null) {
            for (i in 0 until p18.length()) {
                val filename = p18.getJSONObject(i).optJSONObject("mainsnak")
                    ?.optJSONObject("datavalue")?.optString("value")
                if (!filename.isNullOrBlank()) {
                    val encoded = URLEncoder.encode(filename.replace(' ', '_'), "UTF-8")
                        .replace("+", "%20")
                    imageUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/$encoded?width=400"
                    break
                }
            }
        }

        return m.copy(
            name = label ?: m.name,
            kind = typeLabel ?: m.kind,
            description = desc ?: m.description,
            imageUrl = imageUrl ?: m.imageUrl,
            inception = inception,
            wikipediaTitle = wikiTitle ?: m.wikipediaTitle
        )
    }

    private suspend fun getJson(url: String): JSONObject {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw RuntimeException("Wikidata HTTP ${resp.code}")
            return JSONObject(resp.body!!.string())
        }
    }
}
