package com.fabrice.monumentsnearby.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

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

    private val client = Http.client

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
            // languagefallback : hors de France, beaucoup d'entités n'ont pas de
            // label FR — on demande FR puis EN plutôt que d'afficher le QID brut.
            val url = "$BASE?action=wbgetentities&ids=${chunk.joinToString("|")}" +
                    "&props=labels|descriptions|claims|sitelinks" +
                    "&languages=fr|en&languagefallback=1&format=json"
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
        // P31 = type, P84 = architecte, P149 = style, P186 = matériau,
        // P1435 = classement, P112 = fondateur, P127 = propriétaire,
        // P138 = nommé d'après, P793 = événements marquants, P131 = commune
        val props = listOf(
            "P31", "P84", "P149", "P186", "P1435", "P112", "P127",
            "P138", "P793", "P131"
        )
        for (entity in entities.values) {
            for (prop in props) {
                val arr = entity.optJSONObject("claims")?.optJSONArray(prop) ?: continue
                for (i in 0 until arr.length()) {
                    val value = arr.getJSONObject(i).optJSONObject("mainsnak")
                        ?.optJSONObject("datavalue")?.optJSONObject("value")
                    if (value?.optString("entity-type") == "item") {
                        value.optString("id").takeIf { it.isNotBlank() }?.let { typeIds.add(it) }
                    }
                }
            }
        }
        if (typeIds.isEmpty()) return emptyMap()

        val result = mutableMapOf<String, String>()
        for (chunk in typeIds.chunked(MAX_IDS)) {
            val url = "$BASE?action=wbgetentities&ids=${chunk.joinToString("|")}" +
                    "&props=labels&languages=fr|en&languagefallback=1&format=json"
            val root = getJson(url)
            val entities = root.optJSONObject("entities") ?: continue
            val keys = entities.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val labels = entities.getJSONObject(key).optJSONObject("labels")
                val label = labels?.optJSONObject("fr")?.optString("value")
                    ?.takeIf { it.isNotBlank() }
                    ?: labels?.optJSONObject("en")?.optString("value")
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
            ?.takeIf { it.isNotBlank() }
            ?: labels?.optJSONObject("en")?.optString("value")
        val desc = descriptions?.optJSONObject("fr")?.optString("value")
            ?.takeIf { it.isNotBlank() }
            ?: descriptions?.optJSONObject("en")?.optString("value")

        // Fallback : titre Wikipédia depuis les sitelinks Wikidata (FR puis EN —
        // hors de France beaucoup de monuments n'ont qu'un article anglais)
        var wikiTitle = m.wikipediaTitle
        if (wikiTitle.isNullOrBlank()) {
            val sitelinks = entity.optJSONObject("sitelinks")
            (sitelinks?.optJSONObject("frwiki")?.optString("title")
                ?.takeIf { it.isNotBlank() }
                ?: sitelinks?.optJSONObject("enwiki")?.optString("title")
                    ?.takeIf { it.isNotBlank() })
                ?.let { wikiTitle = it }
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

        // Catégorie Commons (P373) → galerie d'images du monument
        var commonsCategory: String? = null
        val p373 = claims?.optJSONArray("P373")
        if (p373 != null) {
            for (i in 0 until p373.length()) {
                val cat = p373.getJSONObject(i).optJSONObject("mainsnak")
                    ?.optJSONObject("datavalue")?.optString("value")
                if (!cat.isNullOrBlank()) {
                    commonsCategory = cat
                    break
                }
            }
        }

        // Richesses supplémentaires : architecte, style, matériau, classement
        val architect = firstItemLabel(claims, "P84", typeLabels)
        val style = firstItemLabel(claims, "P149", typeLabels)
        val material = firstItemLabel(claims, "P186", typeLabels)
        val heritage = firstItemLabel(claims, "P1435", typeLabels)
        val founder = firstItemLabel(claims, "P112", typeLabels)
        val owner = firstItemLabel(claims, "P127", typeLabels)
        val namedAfter = firstItemLabel(claims, "P138", typeLabels)
        val commune = firstItemLabel(claims, "P131", typeLabels)

        // Année du classement : qualificatif P580 (date de début) du premier P1435
        val heritageYear = claims?.optJSONArray("P1435")
            ?.optJSONObject(0)?.optJSONObject("qualifiers")
            ?.optJSONArray("P580")?.optJSONObject(0)
            ?.optJSONObject("datavalue")?.optJSONObject("value")
            ?.optString("time")?.let(::yearOf)

        // Identifiants pivots vers les bases patrimoniales officielles :
        // P380 = référence Mérimée, P539 = identifiant Muséofile (notices POP)
        val merimeeRef = firstStringValue(claims, "P380")
        val museofileRef = firstStringValue(claims, "P539")

        // Année d'ouverture officielle (P1619) — inaugurations, musées
        val openedYear = claims?.optJSONArray("P1619")
            ?.optJSONObject(0)?.optJSONObject("mainsnak")
            ?.optJSONObject("datavalue")?.optJSONObject("value")
            ?.optString("time")?.let(::yearOf)

        // Adresse postale (P6375, texte monolingue)
        val address = claims?.optJSONArray("P6375")
            ?.optJSONObject(0)?.optJSONObject("mainsnak")
            ?.optJSONObject("datavalue")?.optJSONObject("value")
            ?.optString("text")?.takeIf { it.isNotBlank() }

        // Événements marquants (P793) : label + année (qualificatif P585)
        val events = mutableListOf<String>()
        claims?.optJSONArray("P793")?.let { arr ->
            for (i in 0 until arr.length()) {
                val claim = arr.optJSONObject(i) ?: continue
                val value = claim.optJSONObject("mainsnak")
                    ?.optJSONObject("datavalue")?.optJSONObject("value")
                if (value?.optString("entity-type") != "item") continue
                val eventLabel = typeLabels[value.optString("id")] ?: continue
                val year = claim.optJSONObject("qualifiers")
                    ?.optJSONArray("P585")?.optJSONObject(0)
                    ?.optJSONObject("datavalue")?.optJSONObject("value")
                    ?.optString("time")?.let(::yearOf)
                events.add(if (year != null) "$eventLabel ($year)" else eventLabel)
                if (events.size >= 4) break // au-delà, la fiche devient un inventaire
            }
        }

        // Site web officiel (P856) — valeur string (URL)
        var website: String? = null
        val p856 = claims?.optJSONArray("P856")
        if (p856 != null) {
            for (i in 0 until p856.length()) {
                val url = p856.getJSONObject(i).optJSONObject("mainsnak")
                    ?.optJSONObject("datavalue")?.optString("value")
                if (!url.isNullOrBlank()) {
                    website = url
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
            commonsCategory = commonsCategory,
            architect = architect,
            style = style,
            material = material,
            heritage = heritage,
            founder = founder,
            owner = owner,
            website = website,
            heritageYear = heritageYear,
            merimeeRef = merimeeRef,
            museofileRef = museofileRef,
            namedAfter = namedAfter,
            events = events,
            openedYear = openedYear,
            address = address,
            commune = commune,
            wikipediaTitle = wikiTitle ?: m.wikipediaTitle
        )
    }

    /** Première valeur chaîne d'une propriété (P380, P539…). */
    private fun firstStringValue(claims: JSONObject?, prop: String): String? {
        val arr = claims?.optJSONArray(prop) ?: return null
        for (i in 0 until arr.length()) {
            val value = arr.optJSONObject(i)?.optJSONObject("mainsnak")
                ?.optJSONObject("datavalue")?.optString("value")
            if (!value.isNullOrBlank()) return value
        }
        return null
    }

    /** "+1889-03-31T00:00:00Z" → "1889". Null si année absente (dates av. J.-C.). */
    private fun yearOf(time: String): String? =
        time.trimStart('+').takeWhile { it != '-' }.takeIf { it.isNotBlank() }

    /** Premier label FR d'une propriété de type « item » (P84, P149…). */
    private fun firstItemLabel(
        claims: JSONObject?,
        prop: String,
        typeLabels: Map<String, String>
    ): String? {
        val arr = claims?.optJSONArray(prop) ?: return null
        for (i in 0 until arr.length()) {
            val value = arr.getJSONObject(i).optJSONObject("mainsnak")
                ?.optJSONObject("datavalue")?.optJSONObject("value")
            if (value?.optString("entity-type") == "item") {
                val resolved = typeLabels[value.optString("id")]
                if (resolved != null) return resolved
            }
        }
        return null
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

    /** Cache des types « musée » (Q33506 + toutes ses sous-classes P279*). */
    private var museumTypeIds: Set<String>? = null

    /**
     * Types considérés comme musées : Q33506 et toutes ses sous-classes
     * (musée d'art Q207694, musée d'histoire Q5634836…). Le Musée du Louvre
     * est par exemple typé « musée d'art » et pas « musée » directement.
     */
    private suspend fun museumTypes(): Set<String> {
        museumTypeIds?.let { return it }
        val ids = mutableSetOf(Q_MUSEUM)
        try {
            val sparql = "SELECT ?type WHERE { ?type wdt:P279* wd:$Q_MUSEUM . }"
            val url = "https://query.wikidata.org/sparql?query=" +
                    URLEncoder.encode(sparql, "UTF-8") + "&format=json"
            val root = getJson(url)
            val bindings = root.optJSONObject("results")?.optJSONArray("bindings")
            if (bindings != null) {
                for (i in 0 until bindings.length()) {
                    val type = bindings.getJSONObject(i).optJSONObject("type")
                        ?.optString("value")?.substringAfterLast('/')
                    if (!type.isNullOrBlank()) ids.add(type)
                }
            }
        } catch (e: Exception) {
            // cache réduit à Q33506 en cas d'échec
        }
        museumTypeIds = ids
        return ids
    }

    /**
     * Recherche un musée par nom (wbsearchentities), filtré sur le type
     * « musée » (P31 = Q33506 ou sous-classe). Idéal pour la barre de recherche.
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
            val valid = museumTypes()
            val entities = fetchEntities(ids)
            pending.filter { isMuseumType(entities[it.qid], valid) }
        }

    private fun isMuseumType(entity: JSONObject?, validTypes: Set<String>): Boolean {
        if (entity == null) return false
        val p31 = entity.optJSONObject("claims")?.optJSONArray("P31") ?: return false
        for (i in 0 until p31.length()) {
            val value = p31.getJSONObject(i).optJSONObject("mainsnak")
                ?.optJSONObject("datavalue")?.optJSONObject("value")
            if (value?.optString("entity-type") == "item" &&
                validTypes.contains(value.optString("id"))) return true
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

    // ---------------------------------------------------------------
    // Galerie d'images — catégorie Wikimedia Commons du monument (P373)
    // ---------------------------------------------------------------

    /**
     * Images d'une catégorie Wikimedia Commons (API categorymembers).
     * Pagine à travers les membres (les sous-catégories viennent en premier
     * dans le tri alphabétique) jusqu'à obtenir [limit] fichiers image.
     */
    suspend fun fetchCommonsCategoryImages(category: String, limit: Int = 10): List<String> =
        withContext(Dispatchers.IO) {
            if (category.isBlank()) return@withContext emptyList()
            val images = mutableListOf<String>()
            var cmcontinue: String? = null
            var contContinue: String? = null
            var guard = 0

            do {
                val url = "https://commons.wikimedia.org/w/api.php?action=query" +
                        "&list=categorymembers&cmtitle=Category:${URLEncoder.encode(category, "UTF-8")}" +
                        "&cmtype=file&cmlimit=50&format=json" +
                        (cmcontinue?.let { "&cmcontinue=${URLEncoder.encode(it, "UTF-8")}" } ?: "") +
                        (contContinue?.let { "&continue=${URLEncoder.encode(it, "UTF-8")}" } ?: "")
                val root = getJson(url)
                val members = root.optJSONObject("query")?.optJSONArray("categorymembers")
                    ?: break
                for (i in 0 until members.length()) {
                    val title = members.getJSONObject(i).optString("title")
                        .takeIf { it.isNotBlank() } ?: continue
                    // title = "File:X.jpg" → URL Special:FilePath dimensionnée
                    val encoded = URLEncoder.encode(title.removePrefix("File:").replace(' ', '_'), "UTF-8")
                        .replace("+", "%20")
                    images += "https://commons.wikimedia.org/wiki/Special:FilePath/$encoded?width=400"
                }
                val cont = root.optJSONObject("continue")
                cmcontinue = cont?.optString("cmcontinue")?.takeIf { it.isNotBlank() }
                contContinue = cont?.optString("continue")?.takeIf { it.isNotBlank() }
                guard++
            } while (cmcontinue != null && images.size < limit && guard < 8)

            images.take(limit)
        }

    /**
     * Recherche d'images dans Wikimedia Commons par texte (recherche dans les
     * titres de fichiers, `filetype:bitmap` exclut vidéos/audio).
     * Plan B quand la catégorie P373 ne contient que des sous-catégories.
     */
    suspend fun fetchCommonsSearchImages(query: String, limit: Int = 10): List<String> =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext emptyList()
            val url = "https://commons.wikimedia.org/w/api.php?action=query&generator=search" +
                    "&gsrsearch=${URLEncoder.encode("filetype:bitmap \"$query\"", "UTF-8")}" +
                    "&gsrnamespace=6&gsrlimit=$limit&prop=imageinfo&iiprop=url&iiurlwidth=400&format=json"
            val root = getJson(url)
            val pages = root.optJSONObject("query")?.optJSONObject("pages")
                ?: return@withContext emptyList()

            val images = mutableListOf<String>()
            val keys = pages.keys()
            while (keys.hasNext()) {
                val page = pages.getJSONObject(keys.next())
                val ii = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
                val thumb = ii.optString("thumburl").takeIf { it.isNotBlank() }
                    ?: ii.optString("url").takeIf { it.isNotBlank() } ?: continue
                images += thumb
            }
            images
        }
}
