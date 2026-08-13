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

    // ---------------------------------------------------------------
    // Mode Musée — recherche d'un musée + œuvres de sa collection
    // ---------------------------------------------------------------

    /** Un musée (résultat de recherche ou d'une ville). */
    data class Museum(
        val qid: String,
        val name: String,
        val description: String? = null,
        val imageUrl: String? = null,
        val lat: Double? = null,
        val lon: Double? = null
    )

    /** QID du type « musée ». */
    private const val Q_MUSEUM = "Q33506"

    /**
     * Recherche un musée par nom (wbsearchentities), filtré sur le type
     * « musée » (P31 = Q33506). Idéal pour la barre de recherche.
     */
    suspend fun searchMuseums(query: String, limit: Int = 8): List<Museum> =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext emptyList()
            val url = "$BASE?action=wbsearchentities&search=${URLEncoder.encode(query, "UTF-8")}" +
                    "&language=fr&uselang=fr&type=item&limit=$limit&format=json"
            val root = getJson(url)
            val results = root.optJSONArray("search") ?: return@withContext emptyList()

            val ids = mutableListOf<String>()
            val pending = mutableListOf<Museum>()
            for (i in 0 until results.length()) {
                val r = results.getJSONObject(i)
                val id = r.optString("id")
                if (id.isBlank()) continue
                ids.add(id)
                pending += Museum(
                    qid = id,
                    name = r.optString("label").takeIf { it.isNotBlank() } ?: id,
                    description = r.optString("description").takeIf { it.isNotBlank() }
                )
            }
            if (ids.isEmpty()) return@withContext emptyList()

            // Filtrer : garder uniquement les entités de type « musée »
            val entities = fetchEntities(ids)
            pending.filter { isMuseumType(entities[it.qid]) }
        }

    private fun isMuseumType(entity: JSONObject?): Boolean {
        if (entity == null) return false
        val p31 = entity.optJSONObject("claims")?.optJSONArray("P31") ?: return false
        for (i in 0 until p31.length()) {
            val value = p31.getJSONObject(i).optJSONObject("mainsnak")
                ?.optJSONObject("datavalue")?.optJSONObject("value")
            if (value?.optString("id") == Q_MUSEUM) return true
        }
        return false
    }

    /**
     * Œuvres de la collection d'un musée (SPARQL, propriété P195 = collection).
     * Ne garde que les œuvres avec image Wikimedia Commons, triées par notoriété
     * de l'image, limitées pour rester réactif sur mobile.
     */
    suspend fun fetchMuseumArtworks(qid: String, limit: Int = 100): List<Monument> =
        withContext(Dispatchers.IO) {
            val sparql = """
                SELECT ?work ?workLabel ?artistLabel ?date ?image ?typeLabel ?wikiTitle WHERE {
                  ?work wdt:P195 wd:$qid .
                  ?work wdt:P18 ?image .
                  OPTIONAL { ?work wdt:P170 ?artist . }
                  OPTIONAL { ?work wdt:P571 ?date . }
                  OPTIONAL { ?work wdt:P31 ?type . }
                  OPTIONAL {
                    ?work schema:about ?wiki .
                    ?wiki schema:isPartOf <https://fr.wikipedia.org/> ; schema:name ?wikiTitle .
                  }
                  SERVICE wikibase:label { bd:serviceParam wikibase:language "fr". }
                } ORDER BY DESC(?image) LIMIT $limit
            """.trimIndent()
            val url = "https://query.wikidata.org/sparql?query=" +
                    URLEncoder.encode(sparql, "UTF-8") + "&format=json"
            val root = getJson(url)
            val bindings = root.optJSONObject("results")?.optJSONArray("bindings")
                ?: return@withContext emptyList()

            val seen = LinkedHashMap<String, Monument>()
            for (i in 0 until bindings.length()) {
                val b = bindings.getJSONObject(i)
                val workId = b.optJSONObject("work")?.optString("value")?.substringAfterLast('/')
                    ?: continue
                val name = b.optJSONObject("workLabel")?.optString("value")
                    ?.takeIf { it.isNotBlank() } ?: workId
                val artist = b.optJSONObject("artistLabel")?.optString("value")
                    ?.takeIf { it.isNotBlank() }
                val type = b.optJSONObject("typeLabel")?.optString("value")
                    ?.takeIf { it.isNotBlank() }
                val wikiTitle = b.optJSONObject("wikiTitle")?.optString("value")
                    ?.takeIf { it.isNotBlank() }
                val image = b.optJSONObject("image")?.optString("value")
                    ?.takeIf { it.isNotBlank() } ?: continue
                val candidate = Monument(
                    id = workId,
                    name = name,
                    lat = 0.0,
                    lon = 0.0,
                    distanceM = 0.0,
                    kind = type ?: "œuvre",
                    imageUrl = image
                        .replace("http://", "https://")
                        .let { if ('?' in it) it else "$it?width=400" },
                    wikidataId = workId,
                    wikipediaTitle = wikiTitle,
                    inception = parseYear(b.optJSONObject("date")?.optString("value")),
                    artist = artist
                )
                // Une œuvre peut apparaître plusieurs fois (plusieurs types P31) →
                // garder la première occurrence, ou celle qui a un article Wikipédia.
                val existing = seen[workId]
                if (existing == null ||
                    (existing.wikipediaTitle.isNullOrBlank() && !wikiTitle.isNullOrBlank())) {
                    seen[workId] = candidate
                }
            }
            seen.values.toList()
        }

    /** "+1661-00-00T00:00:00Z" → "1661" (année seule). */
    private fun parseYear(time: String?): String? {
        if (time.isNullOrBlank()) return null
        val year = time.trimStart('+').takeWhile { it != '-' }
        return year.takeIf { it.isNotBlank() }
    }
}
