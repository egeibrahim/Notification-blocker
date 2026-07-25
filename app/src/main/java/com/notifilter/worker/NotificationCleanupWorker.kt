package com.notifilter.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.notifilter.data.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NotificationCleanupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val database = AppDatabase.getInstance(applicationContext)
            val dao = database.notificationRecordDao()

            // 7 günden eski kayıtları sil
            val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
            dao.deleteOlderThan(sevenDaysAgo)

            // Çift kayıtları temizle (aynı bildirimin tekrarları)
            dao.deleteDuplicateRecords()

            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
