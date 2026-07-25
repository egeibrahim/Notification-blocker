package com.notifilter.util

import android.content.pm.ApplicationInfo
import android.os.Build

/**
 * Android ApplicationInfo.category değerini Türkçe Google Play kategori adına çevirir.
 * API 26+ için category alanı kullanılır; yoksa "—" döner.
 */
object AppCategoryUtil {

    fun getCategoryLabel(applicationInfo: ApplicationInfo): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return "—"
        return when (applicationInfo.category) {
            ApplicationInfo.CATEGORY_GAME -> "Oyun"
            ApplicationInfo.CATEGORY_AUDIO -> "Müzik ve Ses"
            ApplicationInfo.CATEGORY_VIDEO -> "Video"
            ApplicationInfo.CATEGORY_IMAGE -> "Fotoğrafçılık"
            ApplicationInfo.CATEGORY_SOCIAL -> "Sosyal"
            ApplicationInfo.CATEGORY_NEWS -> "Haberler"
            ApplicationInfo.CATEGORY_MAPS -> "Haritalar"
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Verimlilik"
            ApplicationInfo.CATEGORY_ACCESSIBILITY -> "Erişilebilirlik"
            else -> "—"
        }
    }
}
