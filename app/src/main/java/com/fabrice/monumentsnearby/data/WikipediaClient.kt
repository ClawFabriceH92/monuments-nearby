package com.fabrice.monumentsnearby.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Résumés Wikipédia (API REST) — descriptions riches et sourcées.
 * Gratuit, sans clé. Enrichissement non bloquant : en cas d'erreur,
 * on garde la description Wikidata/OSM existante.
 */
object WikipediaClient {

    /** Résumé suffisamment long pour un dialogue « tout le texte » (affichage
     *  carte tronqué à 3 lignes, mais le texte complet est conservé). */
    private const val MAX_SUMMARY_CHARS = 2000

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

    /**
     * Remplace la description par le résumé de l'article Wikipédia quand il existe.
     */
    suspend fun enrich(monuments: List<Monument>): List<Monument> {
        val withTitle = monuments.filter { !it.wikipediaTitle.isNullOrBlank() }
        if (withTitle.isEmpty()) return monuments
        return try {
            withContext(Dispatchers.IO) {
                monuments.map { m ->
                    val title = m.wikipediaTitle ?: return@map m
                    val summary = fetchSummary(title)
                    if (summary.isNullOrBlank()) m else m.copy(description = summary)
                }
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
}
