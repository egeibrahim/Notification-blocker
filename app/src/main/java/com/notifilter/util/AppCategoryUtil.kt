package com.notifilter.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import com.notifilter.R

object AppCategoryUtil {

    fun getCategoryLabel(context: Context, applicationInfo: ApplicationInfo): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return context.getString(R.string.app_category_unknown)
        return when (applicationInfo.category) {
            ApplicationInfo.CATEGORY_GAME -> context.getString(R.string.app_category_game)
            ApplicationInfo.CATEGORY_AUDIO -> context.getString(R.string.app_category_audio)
            ApplicationInfo.CATEGORY_VIDEO -> context.getString(R.string.app_category_video)
            ApplicationInfo.CATEGORY_IMAGE -> context.getString(R.string.app_category_image)
            ApplicationInfo.CATEGORY_SOCIAL -> context.getString(R.string.app_category_social)
            ApplicationInfo.CATEGORY_NEWS -> context.getString(R.string.app_category_news)
            ApplicationInfo.CATEGORY_MAPS -> context.getString(R.string.app_category_maps)
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> context.getString(R.string.app_category_productivity)
            ApplicationInfo.CATEGORY_ACCESSIBILITY -> context.getString(R.string.app_category_accessibility)
            else -> context.getString(R.string.app_category_unknown)
        }
    }
}
