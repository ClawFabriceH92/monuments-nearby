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

    private suspend fun fetchSummary(title: String): String? {
        val url = "https://fr.wikipedia.org/api/rest_v1/page/summary/" +
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

    /**
     * Résumé Wikivoyage d'une ville (guide touristique). Non bloquant.
     * Ex: https://fr.wikivoyage.org/api/rest_v1/page/summary/Asnières-sur-Seine
     */
    suspend fun fetchWikivoyageSummary(city: String): String? {
        val url = "https://fr.wikivoyage.org/api/rest_v1/page/summary/" +
                URLEncoder.encode(city.replace(' ', '_'), "UTF-8")
        val request = Request.Builder().url(url).build()
        return try {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val extract = JSONObject(body).optString("extract").takeIf { it.isNotBlank() }
                    ?: return null
                if (extract.length > 600) extract.take(600).trimEnd() + "…" else extract
            }
        } catch (e: Exception) {
            null
        }
    }
}
