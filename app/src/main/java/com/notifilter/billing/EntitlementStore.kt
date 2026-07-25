package com.notifilter.billing

import android.content.Context

object EntitlementStore {
    private const val PREFS = "entitlement"
    private const val KEY_IS_ENTITLED = "is_entitled"
    private const val KEY_LAST_ENTITLED_AT = "last_entitled_at"
    private const val GRACE_PERIOD_MS = 7L * 24L * 60L * 60L * 1000L

    fun setEntitled(context: Context, entitled: Boolean) {
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_IS_ENTITLED, entitled)

        if (entitled) {
            editor.putLong(KEY_LAST_ENTITLED_AT, System.currentTimeMillis())
        }

        editor.apply()
    }

    fun isEntitled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        if (prefs.getBoolean(KEY_IS_ENTITLED, false)) return true

        val lastEntitledAt = prefs.getLong(KEY_LAST_ENTITLED_AT, 0L)
        if (lastEntitledAt <= 0L) return false

        return (System.currentTimeMillis() - lastEntitledAt) <= GRACE_PERIOD_MS
    }
}
