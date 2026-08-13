package com.fabrice.monumentsnearby.location

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.fabrice.monumentsnearby.MainActivity
import com.fabrice.monumentsnearby.R
import com.fabrice.monumentsnearby.data.Monument
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import org.json.JSONObject

/**
 * Géofencing des monuments majeurs : alerte par notification quand on entre
 * dans un rayon de 200 m autour d'un monument. Max 20 geofences (limite Play).
 */
class GeofenceHelper(private val context: Context) {

    private val client: GeofencingClient = LocationServices.getGeofencingClient(context)
    private val prefs = context.applicationContext
        .getSharedPreferences("geofence", Context.MODE_PRIVATE)

    companion object {
        const val ACTION_GEOFENCE = "com.fabrice.monumentsnearby.GEOFENCE"
        const val EXTRA_NAME = "monument_name"
        private const val CHANNEL_ID = "geofence"
        private const val RADIUS_M = 200f
        private const val MAX_GEOFENCES = 20
        private val requestIds = mutableListOf<String>()

        /** Les monuments majeurs les plus proches, limités à MAX_GEOFENCES. */
        fun selectMonuments(monuments: List<Monument>): List<Monument> =
            monuments.filter { it.important }.sortedBy { it.distanceM }.take(MAX_GEOFENCES)
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun start(monuments: List<Monument>) {
        if (!hasPermission() || monuments.isEmpty()) return
        createChannel()
        val geofences = monuments.map { m ->
            Geofence.Builder()
                .setRequestId("monument_${m.id}")
                .setCircularRegion(m.lat, m.lon, RADIUS_M)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                .setLoiteringDelay(3000)
                .build()
        }
        // Mapper requestId → nom du monument (lu par le receiver)
        val names = JSONObject()
        geofences.forEachIndexed { index, g ->
            names.put(g.requestId, monuments[index].name)
        }
        prefs.edit().putString("names", names.toString()).apply()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofences(geofences)
            .build()
        val intent = Intent(context, GeofenceReceiver::class.java).apply {
            action = ACTION_GEOFENCE
        }
        val pending = PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        client.addGeofences(request, pending)
        requestIds.clear()
        requestIds.addAll(geofences.map { it.requestId })
    }

    fun stop() {
        if (requestIds.isNotEmpty()) {
            client.removeGeofences(requestIds)
            requestIds.clear()
        }
    }

    private fun createChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Monuments à proximité",
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "Alerte quand tu passes près d'un monument" }
        manager.createNotificationChannel(channel)
    }
}

/** Reçoit les transitions de geofence et affiche la notification. */
class GeofenceReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != GeofenceHelper.ACTION_GEOFENCE) return
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return
        if (event.triggeringGeofences.isNullOrEmpty()) return

        // Récupérer le nom depuis la map sauvegardée
        val prefs = context.getSharedPreferences("geofence", Context.MODE_PRIVATE)
        val names = try {
            JSONObject(prefs.getString("names", "{}"))
        } catch (e: Exception) {
            JSONObject()
        }
        val name = event.triggeringGeofences
            ?.firstNotNullOfOrNull { names.optString(it.requestId).takeIf { s -> s.isNotBlank() } }
            ?: "Monument à proximité"

        val channelId = "geofence"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(channelId, "Monuments à proximité", NotificationManager.IMPORTANCE_HIGH)
            )
        }

        val tapIntent = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            context, 0, tapIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_pin_bleu)
            .setContentTitle("🏛 $name")
            .setContentText("Tu es à moins de 200 m — regarde la fiche !")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(name.hashCode(), notification)
        } catch (e: SecurityException) {
            // permission POST_NOTIFICATIONS non accordée → silencieux
        }
    }
}
