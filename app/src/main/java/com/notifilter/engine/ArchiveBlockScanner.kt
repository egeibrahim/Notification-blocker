package com.notifilter.engine

import android.content.Context
import com.notifilter.data.database.AppDatabase
import com.notifilter.preferences.FilterRulesPreferences
import com.notifilter.preferences.ImportantChannelsPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ArchiveBlockScanner {

    /**
     * Mevcut blok kurallarını arşivdeki son 7 günlük açık bildirimlere yeniden uygular.
     * Yeni eklenen blok kelimelerine uyan bildirimleri hemen engeller ve DB'de günceller.
     * Dönüş değeri: kaç adet bildirimin engellendiği
     */
    suspend fun rescan(context: Context, sinceMs: Long = 7L * 24 * 60 * 60 * 1000): Int = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)
        val dao = db.notificationRecordDao()
        val filterPrefs = FilterRulesPreferences(context)
        val importantPrefs = ImportantChannelsPreferences(context)
        val spamEngine = SpamEngine()

        val since = System.currentTimeMillis() - sinceMs
        val records = dao.getSinceOnce(since)
        val whitelistedPackages = importantPrefs.whitelistedPackages
        val baseConfig = filterPrefs.toFilterRulesConfig()

        var blockedCount = 0
        records.forEach { record ->
            if (record.isBlocked) return@forEach
            if (record.packageName in whitelistedPackages) return@forEach

            val appAllow = filterPrefs.getUserAppContentAllowWords(record.packageName)
            val appBlock = filterPrefs.getUserAppContentBlockWords(record.packageName)
            val emojiBlockEnabled = filterPrefs.isGlobalEmojiBlockEnabled &&
                !filterPrefs.isUserAppEmojiAllowed(record.packageName)
            val config = baseConfig.copy(
                contentAllow = (baseConfig.contentAllow + appAllow).distinct(),
                contentBlock = (baseConfig.contentBlock + appBlock).distinct(),
                blockIfHasEmoji = emojiBlockEnabled
            )

            val result = spamEngine.analyze(record, whitelistedPackages, config)
            if (result is SpamResult.Block) {
                dao.insert(record.copy(isBlocked = true, blockReason = result.reason))
                blockedCount++
            }
        }
        blockedCount
    }
}
