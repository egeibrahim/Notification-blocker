package com.notifilter

import android.app.Application
import android.content.ComponentName
import android.os.Build
import android.service.notification.NotificationListenerService
import com.notifilter.data.database.AppDatabase
import com.notifilter.billing.BillingManager
import com.notifilter.service.NotifilterListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class NotifilterApplication : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    val billingManager: BillingManager by lazy { BillingManager(applicationContext) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun requestListenerRebindIfPossible() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runCatching {
                NotificationListenerService.requestRebind(
                    ComponentName(this, NotifilterListenerService::class.java)
                )
            }
        }
    }

    private fun runWithListenerRetry(action: () -> Unit) {
        // If already connected, do immediately.
        if (NotifilterListenerService.instance != null) {
            action()
            return
        }

        requestListenerRebindIfPossible()

        // Retry a few times; some devices reconnect the listener asynchronously.
        appScope.launch {
            repeat(6) {
                delay(500L)
                if (NotifilterListenerService.instance != null) {
                    action()
                    return@launch
                }
                requestListenerRebindIfPossible()
            }
        }
    }

    /** Sessize Al açılınca ilgili uygulamanın çekmecedeki bildirimlerini kaldırır ve kaydeder. */
    fun requestCancelNotificationsForPackage(packageName: String) {
        runWithListenerRetry {
            NotifilterListenerService.cancelAndSaveForPackage(packageName)
        }
    }

    /** Focus Mode açılınca çekmecedeki tüm bildirimleri kaldırır ve kaydeder. */
    fun requestCancelAllNotifications() {
        runWithListenerRetry {
            NotifilterListenerService.cancelAllAndSave()
        }
    }

    override fun onCreate() {
        super.onCreate()
        NotificationCleanupScheduler.schedule(this)

        billingManager.start()

        appScope.launch {
            database.notificationRecordDao().deleteDuplicateRecords()
        }

        runWithListenerRetry {
            NotifilterListenerService.rescanAndCancelAllActive()
        }
    }

    override fun onTerminate() {
        billingManager.stop()
        super.onTerminate()
    }
}
