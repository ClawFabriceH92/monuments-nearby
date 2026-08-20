package com.fabrice.monumentsnearby

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fabrice.monumentsnearby.data.MonumentCache
import com.fabrice.monumentsnearby.data.VisitRepository
import kotlin.math.roundToInt

/**
 * « Monument du jour » : notification quotidienne proposant un monument
 * majeur non visité, pioché dans le cache de la dernière recherche.
 * Activable dans les réglages ; sans cache, ne fait rien.
 */
class DailyDiscoveryWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val cached = MonumentCache.load(context) ?: return Result.success()
        val visited = VisitRepository(context).visited().keys
        val candidates = cached.monuments.filter { it.important && it.id !in visited }
        val pick = candidates.randomOrNull() ?: return Result.success()

        val channelId = "discovery"
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                channelId,
                "Monument du jour",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Un monument à découvrir près de chez toi" }
        )

        val tapIntent = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            context, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val distance = if (pick.distanceM >= 1000) {
            "à %.1f km".format(pick.distanceM / 1000).replace('.', ',')
        } else {
            "à ${pick.distanceM.roundToInt()} m"
        }
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_pin_bleu)
            .setContentTitle("🏛 Monument du jour : ${pick.name}")
            .setContentText(
                listOfNotNull(
                    pick.kind.replace('_', ' '),
                    distance.takeIf { pick.distanceM > 0 }
                ).joinToString(" · ")
            )
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(2002, notification)
        } catch (e: SecurityException) {
            // permission POST_NOTIFICATIONS non accordée → silencieux
        }
        return Result.success()
    }
}
