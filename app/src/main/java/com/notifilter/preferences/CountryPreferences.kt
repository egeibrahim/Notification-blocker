package com.notifilter.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Kullanıcının uygulamayı ilk kurulumda seçtiği yaşadığı ülke.
 * Bu seçim, hazır blok kategorilerinin Türkçe mi yoksa tamamen
 * İngilizce mi hazırlanacağını belirler.
 */
class CountryPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    /** ISO 3166-1 alpha-2 ülke kodu, örn. "TR", "US", "DE" */
    var countryCode: String?
        get() = prefs.getString(KEY_COUNTRY_CODE, null)
        set(value) = prefs.edit { putString(KEY_COUNTRY_CODE, value) }

    val isCountrySelected: Boolean
        get() = !countryCode.isNullOrBlank()

    val isTurkey: Boolean
        get() = countryCode.equals(TURKEY_CODE, ignoreCase = true)

    companion object {
        private const val PREFS_NAME = "notifilter_prefs"
        private const val KEY_COUNTRY_CODE = "user_country_code"
        const val TURKEY_CODE = "TR"
    }
}
