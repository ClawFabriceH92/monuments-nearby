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
 * active (app ouverte), la position est remontée au ViewModel, qui déclenche
 * la lecture audio à l'arrivée à chaque étape.
 * FusedLocationProvider si disponible, repli LocationManager GPS sinon.
 *
 * Précision adaptative : loin de la prochaine étape, des relevés espacés et
 * économes ; à l'approche, des relevés rapprochés et précis (voir [Tier]).
 */
class WalkTracker(context: Context) {

    /** Paliers de suivi selon la distance à la prochaine étape. */
    enum class Tier(val intervalMs: Long, val minMoveM: Float, val highAccuracy: Boolean) {
        /** > 600 m : économie de batterie */
        FAR(12_000L, 20f, false),
        /** 150–600 m */
        MID(5_000L, 8f, true),
        /** < 150 m : détection d'arrivée fine (rayon 40 m) */
        NEAR(2_000L, 3f, true);

        companion object {
            fun forDistance(distanceM: Double): Tier = when {
                distanceM > 600 -> FAR
                distanceM > 150 -> MID
                else -> NEAR
            }
        }
    }

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
    private var onLocation: ((Double, Double) -> Unit)? = null
    private var tier: Tier = Tier.MID

    /** La balade guidée exige la localisation précise (arrivée détectée à 40 m). */
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Démarre le suivi ; [onLocation] est invoqué sur le thread principal.
     * Retourne false si la permission manque ou qu'aucune source n'est utilisable.
     */
    fun start(onLocation: (lat: Double, lon: Double) -> Unit): Boolean {
        if (!hasPermission()) return false
        this.onLocation = onLocation
        tier = Tier.MID
        return subscribe()
    }

    /**
     * Adapte la fréquence et la précision à la distance restante ; ne
     * réabonne le fournisseur que si le palier change.
     */
    fun setDistanceHint(distanceM: Double) {
        val next = Tier.forDistance(distanceM)
        if (next == tier || onLocation == null) return
        tier = next
        subscribe()
    }

    @SuppressLint("MissingPermission") // vérifiée par hasPermission() en tête
    private fun subscribe(): Boolean {
        val onLocation = this.onLocation ?: return false
        unsubscribe()
        val client = fused
        if (client != null) {
            val request = LocationRequest.Builder(
                if (tier.highAccuracy) Priority.PRIORITY_HIGH_ACCURACY
                else Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                tier.intervalMs
            )
                .setMinUpdateDistanceMeters(tier.minMoveM)
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
                LocationManager.GPS_PROVIDER, tier.intervalMs, tier.minMoveM,
                listener, Looper.getMainLooper()
            )
            managerListener = listener
            true
        } catch (e: Exception) {
            false // fournisseur GPS absent/désactivé
        }
    }

    fun stop() {
        unsubscribe()
        onLocation = null
    }

    private fun unsubscribe() {
        fusedCallback?.let { fused?.removeLocationUpdates(it) }
        fusedCallback = null
        managerListener?.let { manager.removeUpdates(it) }
        managerListener = null
    }
}
