package com.fabrice.monumentsnearby.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Mise à jour automatique via GitHub Releases.
 * - Vérification immédiate au lancement
 * - Puis vérification quotidienne vers 14h00 planifiée par WorkManager
 *   (fonctionne même app fermée, sans boucle de polling)
 * - Téléchargement auto si permission "installer des apps inconnues" OK,
 *   sinon notification avec action vers l'écran d'autorisation.
 * - Activable/désactivable via SharedPreferences ("autoUpdate", défaut true).
 */
object UpdateManager {

    private const val PREFS = "monuments-nearbyupdate"
    private const val KEY_AUTO = "autoUpdate"
    private const val CHANNEL_ID = "com.fabrice.monumentsnearby.updates"
    private const val WORK_NAME = "update-check"

    private var started = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun autoUpdateEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_AUTO, true)

    fun setAutoUpdate(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTO, enabled).apply()
    }

    /** À appeler une fois depuis le onCreate de l'activité principale. */
    fun start(context: Context) {
        if (started) return
        started = true
        val appContext = context.applicationContext
        ensureChannel(appContext)
        // Vérification immédiate au lancement
        scope.launch {
            if (autoUpdateEnabled(appContext)) performCheck(appContext)
        }
        // Vérification quotidienne (WorkManager persiste après fermeture/reboot)
        val request = PeriodicWorkRequestBuilder<UpdateWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(millisUntilNextTwoPm(), TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .build()
        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
        )
    }

    /** Vérifie GitHub Releases et télécharge si une MAJ existe. Peut être appelé par un bouton "Vérifier maintenant". */
    fun checkNow(context: Context) {
        val appContext = context.applicationContext
        scope.launch { performCheck(appContext) }
    }

    /** Cœur de la vérification : appelé au lancement, par le worker et à la demande. */
    internal suspend fun performCheck(context: Context) {
        val info = withContext(Dispatchers.IO) { UpdateChecker.latestWithApk() } ?: return
        val current = currentVersion(context)
        if (UpdateChecker.compareVersions(info.versionName, current) <= 0) return
        if (AutoUpdater.canRequestInstalls(context)) {
            AutoUpdater.download(context, info.downloadUrl)
        } else {
            notifyPermissionNeeded(context, info)
        }
    }

    /** Millisecondes jusqu'au prochain 14h00 (aujourd'hui ou demain). */
    private fun millisUntilNextTwoPm(): Long {
        val now = Calendar.getInstance()
        val next = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 14)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_YEAR, 1)
        }
        return next.timeInMillis - now.timeInMillis
    }

    private fun currentVersion(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    } catch (_: PackageManager.NameNotFoundException) {
        ""
    }

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Mises à jour",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "Mises à jour automatiques de Monuments Nearby" }
        )
    }

    /** Notification : MAJ dispo mais il faut d'abord autoriser l'installation. */
    private fun notifyPermissionNeeded(context: Context, info: UpdateInfo) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val pi = android.app.PendingIntent.getActivity(
                context,
                0,
                settingsIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("⬆ Mise à jour Monuments Nearby v${info.versionName} disponible")
                .setContentText("Touchez pour autoriser l'installation, puis la mise à jour s'installera automatiquement.")
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            nm.notify(1001, notification)
        } catch (_: Exception) {
            // Notification impossible → on laisse tomber silencieusement
        }
    }
}
