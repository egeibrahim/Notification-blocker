package com.notifilter.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Uygulama bazlı beyaz liste (Sessize Al).
 * Whitelist'teki uygulamaların bildirimleri filtrelenmez.
 */
class ImportantChannelsPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    /** Uygulama bazlı beyaz liste - tüm kanalları kapsar (Sessize Al) */
    var whitelistedPackages: Set<String>
        get() = prefs.getStringSet(KEY_WHITELISTED_PACKAGES, emptySet())?.toSet() ?: emptySet()
        set(value) = prefs.edit(commit = true) {
            putStringSet(KEY_WHITELISTED_PACKAGES, HashSet(value))
        }

    fun addToWhitelist(packageName: String) {
        whitelistedPackages = whitelistedPackages + packageName
    }

    fun removeFromWhitelist(packageName: String) {
        whitelistedPackages = whitelistedPackages - packageName
    }

    fun isWhitelisted(packageName: String): Boolean = packageName in whitelistedPackages

    companion object {
        private const val PREFS_NAME = "notifilter_prefs"
        private const val KEY_WHITELISTED_PACKAGES = "whitelisted_packages"
    }
}
