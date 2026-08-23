package com.fabrice.monumentsnearby.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Résumés Wikipédia (API REST) — descriptions riches et sourcées.
 * Gratuit, sans clé. Enrichissement non bloquant : en cas d'erreur,
 * on garde la description Wikidata/OSM existante.
 */
object WikipediaClient {

    /** Résumé suffisamment long pour un dialogue « tout le texte » (affichage
     *  carte tronqué à 3 lignes, mais le texte complet est conservé). */
    private const val MAX_SUMMARY_CHARS = 2000

    private val client = Http.client

    /** Nombre maximal de requêtes simultanées vers l'API Wikimedia. */
    private const val MAX_CONCURRENT = 6

    /**
     * Remplace la description par le résumé de l'article Wikipédia quand il existe.
     * Les requêtes partent en parallèle (plafonnées à [MAX_CONCURRENT] par
     * politesse envers l'API) : en séquentiel, une ville avec 80 articles
     * prenait 80 allers-retours réseau bout à bout.
     */
    suspend fun enrich(monuments: List<Monument>): List<Monument> {
        val withTitle = monuments.filter { !it.wikipediaTitle.isNullOrBlank() }
        if (withTitle.isEmpty()) return monuments
        return try {
            withContext(Dispatchers.IO) {
                val semaphore = Semaphore(MAX_CONCURRENT)
                monuments.map { m ->
                    async {
                        val title = m.wikipediaTitle ?: return@async m
                        val summary = semaphore.withPermit {
                            try {
                                fetchSummary(title)
                            } catch (e: Exception) {
                                null // un échec isolé ne bloque pas les autres
                            }
                        }
                        if (summary.isNullOrBlank()) m else m.copy(description = summary)
                    }
                }.awaitAll()
            }
        } catch (e: Exception) {
            monuments // enrichissement non bloquant
        }
    }

    /** Résumé : Wikipédia FR d'abord, repli EN (voyages hors de France). */
    private suspend fun fetchSummary(title: String): String? =
        fetchSummary("fr", title) ?: fetchSummary("en", title)

    private fun fetchSummary(lang: String, title: String): String? {
        val url = "https://$lang.wikipedia.org/api/rest_v1/page/summary/" +
                URLEncoder.encode(title.replace(' ', '_'), "UTF-8")
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string() ?: return null
            val extract = JSONObject(body).optString("extract").takeIf { it.isNotBlank() }
                ?: return null
            return if (extract.length > MAX_SUMMARY_CHARS) {
                extract.take(MAX_SUMMARY_CHARS).trimEnd() + "…"
            } else {
                extract
            }
        }
    }

    /** Longueur maximale de l'article complet lu par l'audioguide (~12 min). */
    private const val MAX_ARTICLE_CHARS = 12000

    /**
     * Texte intégral (brut) de l'article Wikipédia — pour « écouter l'article
     * complet ». FR d'abord, repli EN. Null si introuvable ou réseau KO.
     */
    suspend fun fetchFullText(title: String): String? =
        withContext(Dispatchers.IO) {
            try {
                fetchFullText("fr", title) ?: fetchFullText("en", title)
            } catch (e: Exception) {
                null
            }
        }

    private fun fetchFullText(lang: String, title: String): String? {
        val url = "https://$lang.wikipedia.org/w/api.php?action=query&prop=extracts" +
                "&explaintext=1&redirects=1&format=json" +
                "&titles=${URLEncoder.encode(title, "UTF-8")}"
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string() ?: return null
            val pages = JSONObject(body).optJSONObject("query")?.optJSONObject("pages")
                ?: return null
            val keys = pages.keys()
            while (keys.hasNext()) {
                val page = pages.optJSONObject(keys.next()) ?: continue
                val extract = page.optString("extract").takeIf { it.isNotBlank() } ?: continue
                val clean = cleanArticleText(extract)
                if (clean.isBlank()) continue
                return if (clean.length > MAX_ARTICLE_CHARS) {
                    clean.take(MAX_ARTICLE_CHARS).trimEnd() + "…"
                } else {
                    clean
                }
            }
            return null
        }
    }

    /**
     * Sections de fin d'article : ce ne sont que des listes de renvois
     * (références, liens, bibliographie) — inutiles à lire ou à afficher.
     */
    private val SKIPPED_SECTIONS = setOf(
        "notes et références", "notes et references", "références", "references", "notes",
        "voir aussi", "annexes", "annexe", "bibliographie", "liens externes",
        "articles connexes", "sources", "galerie", "galerie d'images", "filmographie",
        "references and notes", "see also", "external links", "further reading",
        "bibliography", "gallery", "citations", "footnotes"
    )

    /**
     * Met le texte brut de l'API (`explaintext`) en forme pour l'audioguide et
     * l'affichage. Les titres arrivent sous la forme `== Histoire ==` : lus tels
     * quels, le TTS prononçait « égale égale égale ». On les transforme en
     * phrases (« Histoire. ») et on coupe les sections de renvois finales.
     */
    internal fun cleanArticleText(raw: String): String {
        val out = StringBuilder()
        var skipping = false
        raw.lineSequence().forEach { line ->
            val trimmed = line.trim()
            val heading = trimmed
                .takeIf { it.startsWith("=") && it.endsWith("=") && it.length > 2 }
                ?.trim('=')
                ?.trim()
            if (heading != null) {
                // Une section ignorée l'est jusqu'au titre suivant
                skipping = heading.lowercase() in SKIPPED_SECTIONS
                if (!skipping && heading.isNotBlank()) {
                    // Titre lu comme une phrase : le point force une pause TTS
                    out.append("\n\n")
                    out.append(if (heading.endsWith(".")) heading else "$heading.")
                    out.append("\n")
                }
                return@forEach
            }
            if (skipping || trimmed.isBlank()) return@forEach
            out.append(trimmed).append("\n")
        }
        return out.toString()
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    /**
     * Résumé Wikivoyage d'une ville (guide touristique). Non bloquant.
     * Ex: https://fr.wikivoyage.org/api/rest_v1/page/summary/Asnières-sur-Seine
     */
    suspend fun fetchWikivoyageSummary(city: String): String? =
        // withContext(IO) indispensable : appelé depuis le thread principal,
        // l'appel réseau levait NetworkOnMainThreadException (guide jamais affiché)
        withContext(Dispatchers.IO) {
            val url = "https://fr.wikivoyage.org/api/rest_v1/page/summary/" +
                    URLEncoder.encode(city.replace(' ', '_'), "UTF-8")
            val request = Request.Builder().url(url).build()
            try {
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    val body = resp.body?.string() ?: return@withContext null
                    val extract = JSONObject(body).optString("extract")
                        .takeIf { it.isNotBlank() } ?: return@withContext null
                    if (extract.length > 600) extract.take(600).trimEnd() + "…" else extract
                }
            } catch (e: Exception) {
                null
            }
        }
}
