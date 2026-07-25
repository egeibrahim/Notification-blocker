package com.notifilter.util

import android.content.Context
import androidx.core.app.NotificationManagerCompat

object NotificationAccessHelper {
    fun isNotificationAccessEnabled(context: Context): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)
    }
}
