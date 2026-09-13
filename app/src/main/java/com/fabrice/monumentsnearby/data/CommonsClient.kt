package com.fabrice.monumentsnearby.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Métadonnées d'attribution d'une image Wikimedia Commons (auteur, licence,
 * page du fichier) — nécessaires pour réutiliser une photo sous CC.
 */
object CommonsClient {

    private const val API = "https://commons.wikimedia.org/w/api.php"
    private val client = Http.client

    data class Attribution(
        val fileTitle: String,
        val author: String?,
        val license: String?,
        val licenseUrl: String?,
        val filePage: String
    ) {
        /** Ligne d'attribution courte : « Auteur · CC BY-SA 4.0 ». */
        val line: String
            get() = listOfNotNull(author, license).joinToString(" · ").ifBlank { "Wikimedia Commons" }
    }

    /**
     * Titre du fichier ("File:X.jpg") déduit d'une URL d'image Commons
     * (Special:FilePath/… ou upload.wikimedia.org/…/thumb/…/X.jpg/400px-X.jpg).
     */
    internal fun fileTitleFromUrl(url: String): String? {
        val path = url.substringBefore('?')
        val name = when {
            path.contains("Special:FilePath/") -> path.substringAfter("Special:FilePath/")
            path.contains("/thumb/") -> {
                // …/thumb/a/ab/Name.jpg/400px-Name.jpg → Name.jpg
                val afterThumb = path.substringAfter("/thumb/")
                afterThumb.split('/').getOrNull(2)
            }
            path.contains("upload.wikimedia.org") -> path.substringAfterLast('/')
            else -> null
        } ?: return null
        val decoded = try {
            URLDecoder.decode(name.replace("+", "%2B"), "UTF-8")
        } catch (e: Exception) {
            name
        }
        return "File:" + decoded.replace('_', ' ')
    }

    /** Attribution d'une image à partir de son URL ; null si indisponible. */
    suspend fun attribution(imageUrl: String): Attribution? = withContext(Dispatchers.IO) {
        val title = fileTitleFromUrl(imageUrl) ?: return@withContext null
        val url = "$API?action=query&titles=${URLEncoder.encode(title, "UTF-8")}" +
            "&prop=imageinfo&iiprop=extmetadata&iiextmetadatafilter=Artist|LicenseShortName|LicenseUrl" +
            "&format=json"
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { resp ->
            val text = if (resp.isSuccessful) resp.body?.string() else null
            if (text == null) {
                null
            } else {
                parse(text, title)
            }
        }
    }

    internal fun parse(json: String, title: String): Attribution? {
        val pages = JSONObject(json).optJSONObject("query")?.optJSONObject("pages") ?: return null
        val keys = pages.keys()
        while (keys.hasNext()) {
            val page = pages.getJSONObject(keys.next())
            val meta = page.optJSONArray("imageinfo")?.optJSONObject(0)?.optJSONObject("extmetadata")
                ?: continue
            fun value(key: String): String? =
                meta.optJSONObject(key)?.optString("value")?.let { stripHtml(it) }?.takeIf { it.isNotBlank() }
            return Attribution(
                fileTitle = title,
                author = value("Artist"),
                license = value("LicenseShortName"),
                licenseUrl = value("LicenseUrl"),
                filePage = "https://commons.wikimedia.org/wiki/" +
                    URLEncoder.encode(title.replace(' ', '_'), "UTF-8").replace("+", "%20")
            )
        }
        return null
    }

    private fun stripHtml(html: String): String =
        html.replace(Regex("<[^>]+>"), "").replace("&amp;", "&").replace("&quot;", "\"").trim()
}
