package com.fabrice.monumentsnearby.data

import com.fabrice.monumentsnearby.BuildConfig
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Client OkHttp partagé par tous les accès réseau (Overpass, Wikidata,
 * Wikipédia, Nominatim, Coil) : un seul pool de connexions/threads, et un
 * User-Agent commun identifiant l'application avec sa vraie version —
 * exigé par les APIs Wikimedia et OSM.
 *
 * Les clients qui ont besoin d'autres timeouts dérivent via `newBuilder()`
 * (le pool de connexions reste partagé).
 */
object Http {

    val userAgent: String =
        "MonumentsNearby/${BuildConfig.VERSION_NAME} (Android; +https://github.com/ClawFabriceH92/monuments-nearby)"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", userAgent)
                    .build()
            )
        }
        .build()
}
