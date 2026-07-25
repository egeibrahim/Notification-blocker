package com.notifilter

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.notifilter.worker.NotificationCleanupWorker
import java.util.concurrent.TimeUnit

/**
 * Her gece yaklaşık 02:00'da çalışacak şekilde temizlik işini planlar.
 * PeriodicWorkRequest minimum 15 dakika aralıklarla çalışabilir;
 * 24 saatlik aralık ve setInitialDelay ile gece çalışması hedeflenir.
 */
object NotificationCleanupScheduler {

    private const val WORK_NAME = "notification_cleanup"

    fun schedule(context: Context) {
        val workRequest = PeriodicWorkRequestBuilder<NotificationCleanupWorker>(
            24, TimeUnit.HOURS
        )
            .setInitialDelay(initialDelayUntil2AM(), TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    /**
     * Bir sonraki gece 02:00'a kadar olan gecikmeyi hesaplar.
     */
    private fun initialDelayUntil2AM(): Long {
        val now = System.currentTimeMillis()
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = now
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 2)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)

        var targetTime = calendar.timeInMillis
        if (targetTime <= now) {
            calendar.add(java.util.Calendar.DAY_OF_MONTH, 1)
            targetTime = calendar.timeInMillis
        }

        return targetTime - now
    }
}
