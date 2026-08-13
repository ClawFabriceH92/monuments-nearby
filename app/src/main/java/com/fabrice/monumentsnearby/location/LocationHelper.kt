package com.fabrice.monumentsnearby.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Localisation robuste, trois niveaux :
 * 1. FusedLocationProvider (Google Play Services) — position courante (timeout 15 s) puis dernière connue
 * 2. LocationManager — dernière position connue (GPS puis réseau)
 * 3. LocationManager — mise à jour active courte (12 s GPS, 8 s réseau) si jamais fixé
 *
 * Retourne null seulement si aucune source ne fournit de position.
 */
class LocationHelper(context: Context) {

    private val appContext = context.applicationContext
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val fused: FusedLocationProviderClient? by lazy {
        try {
            LocationServices.getFusedLocationProviderClient(appContext)
        } catch (e: Exception) {
            null // pas de Google Play Services
        }
    }

    @SuppressLint("MissingPermission") // la permission est vérifiée en tête de fonction
    suspend fun currentLocation(): Location? {
        // Défense en profondeur : ne jamais appeler les APIs de localisation
        // sans permission (SecurityException possible sinon).
        if (!hasLocationPermission()) return null

        // 1) Fused : position courante, puis dernière connue
        fused?.let { client ->
            val current = withTimeoutOrNull(15_000) { getCurrentLocation(client) }
            if (current != null) return current
            val last = withTimeoutOrNull(5_000) { getLastLocation(client) }
            if (last != null) return last
        }
        // 2) LocationManager : dernière connue
        lastKnownFromManager()?.let { return it }
        // 3) Mise à jour active courte (appareil jamais localisé)
        withTimeoutOrNull(12_000) { requestSingleUpdate(LocationManager.GPS_PROVIDER) }
            ?.let { return it }
        return withTimeoutOrNull(8_000) { requestSingleUpdate(LocationManager.NETWORK_PROVIDER) }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @RequiresPermission(
        anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION]
    )
    private suspend fun getCurrentLocation(client: FusedLocationProviderClient): Location? =
        suspendCancellableCoroutine { cont ->
            val tokenSource = CancellationTokenSource()
            cont.invokeOnCancellation { tokenSource.cancel() }
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, tokenSource.token)
                .addOnSuccessListener { loc -> if (cont.isActive) cont.resume(loc) }
                .addOnFailureListener { if (cont.isActive) cont.resume(null) }
        }

    @RequiresPermission(
        anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION]
    )
    private suspend fun getLastLocation(client: FusedLocationProviderClient): Location? =
        suspendCancellableCoroutine { cont ->
            client.lastLocation
                .addOnSuccessListener { loc -> if (cont.isActive) cont.resume(loc) }
                .addOnFailureListener { if (cont.isActive) cont.resume(null) }
        }

    private fun lastKnownFromManager(): Location? {
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            try {
                locationManager.getLastKnownLocation(provider)?.let { return it }
            } catch (_: SecurityException) {
                // permission retirée entre-temps
            } catch (_: IllegalArgumentException) {
                // provider inexistant
            }
        }
        return null
    }

    @RequiresPermission(
        anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION]
    )
    private suspend fun requestSingleUpdate(provider: String): Location? =
        suspendCancellableCoroutine { cont ->
            val listener = LocationListener { loc ->
                if (cont.isActive) cont.resume(loc)
            }
            cont.invokeOnCancellation {
                try {
                    locationManager.removeUpdates(listener)
                } catch (_: Exception) {
                }
            }
            try {
                locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            } catch (e: Exception) {
                if (cont.isActive) cont.resume(null)
            }
        }
}
