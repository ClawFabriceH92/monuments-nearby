package com.fabrice.monumentsnearby.update

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Worker WorkManager de la vérification quotidienne des mises à jour.
 * Planifié par [UpdateManager.start] ; tourne même si l'app est fermée.
 */
class UpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (UpdateManager.autoUpdateEnabled(applicationContext)) {
            UpdateManager.performCheck(applicationContext)
        }
        return Result.success()
    }
}
