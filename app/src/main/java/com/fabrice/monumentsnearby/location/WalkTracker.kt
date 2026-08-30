package com.fabrice.monumentsnearby.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * Suivi de position continu pour la balade guidée : tant que la balade est
 * active (app ouverte), la position est rafraîchie toutes les ~4 s et remontée
 * au ViewModel, qui déclenche la lecture audio à l'arrivée à chaque étape.
 * FusedLocationProvider si disponible, repli LocationManager GPS sinon.
 */
class WalkTracker(context: Context) {

    private val appContext = context.applicationContext
    private val manager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val fused: FusedLocationProviderClient? = try {
        LocationServices.getFusedLocationProviderClient(appContext)
    } catch (e: Exception) {
        null // pas de Google Play Services
    }

    private var fusedCallback: LocationCallback? = null
    private var managerListener: LocationListener? = null

    /** La balade guidée exige la localisation précise (arrivée détectée à 40 m). */
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Démarre le suivi ; [onLocation] est invoqué sur le thread principal.
     * Retourne false si la permission manque ou qu'aucune source n'est utilisable.
     */
    @SuppressLint("MissingPermission") // vérifiée par hasPermission() en tête
    fun start(onLocation: (lat: Double, lon: Double) -> Unit): Boolean {
        if (!hasPermission()) return false
        stop()
        val client = fused
        if (client != null) {
            val request = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS
            )
                .setMinUpdateDistanceMeters(MIN_MOVE_M)
                .build()
            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { onLocation(it.latitude, it.longitude) }
                }
            }
            fusedCallback = callback
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            return true
        }
        val listener = LocationListener { loc -> onLocation(loc.latitude, loc.longitude) }
        return try {
            manager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, UPDATE_INTERVAL_MS, MIN_MOVE_M,
                listener, Looper.getMainLooper()
            )
            managerListener = listener
            true
        } catch (e: Exception) {
            false // fournisseur GPS absent/désactivé
        }
    }

    fun stop() {
        fusedCallback?.let { fused?.removeLocationUpdates(it) }
        fusedCallback = null
        managerListener?.let { manager.removeUpdates(it) }
        managerListener = null
    }

    private companion object {
        const val UPDATE_INTERVAL_MS = 4000L
        const val MIN_MOVE_M = 5f
    }
}
