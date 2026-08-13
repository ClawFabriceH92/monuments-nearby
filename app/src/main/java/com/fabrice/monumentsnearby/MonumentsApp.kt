package com.fabrice.monumentsnearby

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Configure un ImageLoader Coil avec un User-Agent identifiant l'application.
 * Sans cela, Wikimedia Commons renvoie HTTP 403 (le User-Agent OkHttp par défaut
 * est bloqué) → toutes les images des monuments/œuvres restaient vides.
 */
class MonumentsApp : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "MonumentsNearby/0.5 (Android)")
                        .build()
                )
            }
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(client)
            .crossfade(true)
            .build()
    }
}
