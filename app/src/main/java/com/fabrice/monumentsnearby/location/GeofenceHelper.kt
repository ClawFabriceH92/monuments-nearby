package com.fabrice.monumentsnearby.location

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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

/** Monument dont on vient d'entrer dans le rayon d'alerte. */
data class NearbyAlert(
    val id: String,
    val name: String,
    val description: String?
)

/**
 * Proximité : l'entrée dans une geofence prévient l'UI quand l'app est
 * ouverte — pop-up « tu es à moins de 100 m », et lecture audio automatique
 * si la visite guidée est activée. MonumentsScreen enregistre le listener.
 */
object GuidedVisitBus {
    /** Invoqué sur le thread principal à l'entrée dans un rayon d'alerte. */
    @Volatile
    var listener: ((NearbyAlert) -> Unit)? = null
}

/**
 * Géofencing des monuments les plus proches : alerte quand on entre dans un
 * rayon de 100 m autour d'un monument. Max 20 geofences (limite Play).
 */
class GeofenceHelper(private val context: Context) {

    private val client: GeofencingClient = LocationServices.getGeofencingClient(context)
    private val prefs = context.applicationContext
        .getSharedPreferences("geofence", Context.MODE_PRIVATE)

    companion object {
        const val ACTION_GEOFENCE = "com.fabrice.monumentsnearby.GEOFENCE"
        const val EXTRA_NAME = "monument_name"
        private const val CHANNEL_ID = "geofence"
        /** 100 m : rayon d'alerte. En dessous, le géofencing Play devient
         *  imprécis (recommandation Google : ne pas descendre sous 100 m). */
        const val RADIUS_M = 100f
        private const val MAX_GEOFENCES = 20

        /**
         * Les monuments à surveiller : majeurs d'abord (ils ont une fiche
         * riche), complétés par les plus proches jusqu'à MAX_GEOFENCES —
         * avant, seuls les majeurs déclenchaient une alerte.
         */
        fun selectMonuments(monuments: List<Monument>): List<Monument> {
            val majors = monuments.filter { it.important }.sortedBy { it.distanceM }
            val others = monuments.filter { !it.important }.sortedBy { it.distanceM }
            return (majors + others).take(MAX_GEOFENCES)
        }
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** PendingIntent stable : sert au start ET au stop (retirer par IDs ne
     *  survivait pas à la mort du process, la liste étant en mémoire). */
    private fun geofencePendingIntent(): PendingIntent {
        val intent = Intent(context, GeofenceReceiver::class.java).apply {
            action = ACTION_GEOFENCE
        }
        return PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun start(monuments: List<Monument>) {
        if (!hasPermission() || monuments.isEmpty()) return
        createChannel()
        val geofences = monuments.map { m ->
            Geofence.Builder()
                .setRequestId("monument_${m.id}")
                .setCircularRegion(m.lat, m.lon, RADIUS_M)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                .build()
        }
        // Mapper requestId → {id, nom, description} (lu par le receiver pour la
        // notification, le pop-up de proximité et la lecture audio)
        val names = JSONObject()
        geofences.forEachIndexed { index, g ->
            val m = monuments[index]
            names.put(
                g.requestId,
                JSONObject()
                    .put("i", m.id)
                    .put("n", m.name)
                    .put("d", m.description?.take(1200) ?: "")
            )
        }
        prefs.edit().putString("names", names.toString()).apply()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofences(geofences)
            .build()
        client.addGeofences(request, geofencePendingIntent())
    }

    fun stop() {
        client.removeGeofences(geofencePendingIntent())
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

        // Récupérer nom + description depuis la map sauvegardée.
        // Compat : les anciennes entrées étaient de simples chaînes (nom seul).
        val prefs = context.getSharedPreferences("geofence", Context.MODE_PRIVATE)
        val names = try {
            JSONObject(prefs.getString("names", "{}"))
        } catch (e: Exception) {
            JSONObject()
        }
        var description: String? = null
        var id: String? = null
        val name = event.triggeringGeofences
            ?.firstNotNullOfOrNull { geofence ->
                when (val entry = names.opt(geofence.requestId)) {
                    is JSONObject -> entry.optString("n").takeIf { it.isNotBlank() }?.also {
                        description = entry.optString("d").takeIf { d -> d.isNotBlank() }
                        id = entry.optString("i").takeIf { i -> i.isNotBlank() }
                            ?: geofence.requestId.removePrefix("monument_")
                    }
                    is String -> entry.takeIf { it.isNotBlank() }
                    else -> null
                }
            }
            ?: "Monument à proximité"

        // Pop-up de proximité (et lecture audio si la visite guidée est
        // activée) : l'UI décide, elle sait si l'app est au premier plan.
        GuidedVisitBus.listener?.invoke(
            NearbyAlert(id = id ?: name, name = name, description = description)
        )

        val channelId = "geofence"
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(channelId, "Monuments à proximité", NotificationManager.IMPORTANCE_HIGH)
        )

        val tapIntent = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            context, 0, tapIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_pin_bleu)
            .setContentTitle("🏛 $name")
            .setContentText("Tu es à moins de 100 m — regarde la fiche !")
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
