package com.notifilter.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Genel Odak Modu switch durumunu SharedPreferences ile saklar.
 * Aktifse tüm bildirimler gizlenir ve kaydedilir.
 */
class FocusModePreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    var isFocusModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_FOCUS_MODE, false)
        set(value) = prefs.edit { putBoolean(KEY_FOCUS_MODE, value) }

    companion object {
        private const val PREFS_NAME = "notifilter_prefs"
        private const val KEY_FOCUS_MODE = "genel_odak_modu"
    }
}
