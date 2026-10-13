package dev.surco.playback

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.surco.SurcoApp
import java.util.concurrent.TimeUnit

/**
 * Envía los scrobbles que se quedaron pendientes sin conexión. Android lo ejecuta en cuanto
 * vuelve la red, aunque la app esté cerrada, y lo reintenta con espera creciente si falla.
 */
class ScrobbleWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = (applicationContext as SurcoApp).container.repository
        return if (repo.flushScrobbles()) Result.success() else Result.retry()
    }

    companion object {
        private const val NAME = "surco-scrobbles"

        fun schedule(context: Context) {
            runCatching {
                val request = OneTimeWorkRequestBuilder<ScrobbleWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                    .build()
                // KEEP: si ya hay uno esperando a la red, no se duplica.
                WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, request)
            }
        }
    }
}
