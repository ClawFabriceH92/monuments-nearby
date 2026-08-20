package com.fabrice.monumentsnearby

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.fabrice.monumentsnearby.data.Http
import java.util.concurrent.TimeUnit

/**
 * Configure un ImageLoader Coil avec un User-Agent identifiant l'application.
 * Sans cela, Wikimedia Commons renvoie HTTP 403 (le User-Agent OkHttp par défaut
 * est bloqué) → toutes les images des monuments/œuvres restaient vides.
 */
class MonumentsApp : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        // Client partagé (User-Agent + pool de connexions communs),
        // timeout de lecture élargi pour les grandes images.
        val client = Http.client.newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(client)
            .crossfade(true)
            .build()
    }
}
