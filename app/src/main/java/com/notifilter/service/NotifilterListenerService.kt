package com.notifilter.service

import android.app.Notification
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlin.math.abs
import com.notifilter.BuildConfig
import com.notifilter.NotifilterApplication
import com.notifilter.billing.EntitlementStore
import com.notifilter.data.database.AppDatabase
import com.notifilter.data.entity.NotificationRecord
import com.notifilter.engine.SpamEngine
import com.notifilter.engine.SpamResult
import com.notifilter.preferences.FilterRulesPreferences
import com.notifilter.preferences.FocusModePreferences
import com.notifilter.preferences.ImportantChannelsPreferences
import com.notifilter.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Gelen bildirimleri dinler, SpamEngine ile analiz eder.
 * - Blok kararı: cancelNotification ile gizler, DB'ye isBlocked=true kaydeder
 * - İzin kararı: Sadece DB'ye kaydeder, gizlemez
 * - Genel Odak Modu aktif: Tüm bildirimleri gizler ve kaydeder
 */
class NotifilterListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        serviceScope.launch {
            kotlinx.coroutines.delay(500L)
            performRescanAndCancelAllActive()
        }
    }

    private fun isGamePackage(packageName: String): Boolean {
        return isGameCache.getOrPut(packageName) {
            try {
                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return@getOrPut false
                val ai = packageManager.getApplicationInfo(packageName, 0)
                ai.category == android.content.pm.ApplicationInfo.CATEGORY_GAME
            } catch (_: Throwable) {
                false
            }
        }
    }

    override fun onListenerDisconnected() {
        instance = null
        super.onListenerDisconnected()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            requestRebind(ComponentName(this, NotifilterListenerService::class.java))
        }
    }

    companion object {
        private const val TAG = "NotifilterListener"

        @Volatile
        var instance: NotifilterListenerService? = null
            private set

        /** Belirtilen uygulamanın aktif çekmecedeki bildirimlerini kaydedip kaldırır. */
        fun cancelAndSaveForPackage(packageName: String) {
            val svc = instance ?: return
            if (!EntitlementStore.isEntitled(svc)) return
            svc.performCancelAndSaveForPackage(packageName)
        }

        /** Belirtilen uygulamanın aktif bildirimlerini mevcut blok kurallarına göre yeniden değerlendirir, eşleşenleri gizler. */
        fun rescanAndCancelForPackage(packageName: String) {
            val svc = instance ?: return
            if (!EntitlementStore.isEntitled(svc)) return
            svc.performRescanAndCancelForPackage(packageName)
        }

        /** Tüm aktif bildirimleri mevcut blok kurallarına göre yeniden değerlendirir, eşleşenleri gizler. */
        fun rescanAndCancelAllActive() {
            val svc = instance ?: return
            if (!EntitlementStore.isEntitled(svc)) return
            svc.performRescanAndCancelAllActive()
        }

        /** Tüm aktif bildirimleri kaydedip çekmeceden temizler. */
        fun cancelAllAndSave() {
            val svc = instance ?: return
            if (!EntitlementStore.isEntitled(svc)) return
            svc.performCancelAllAndSave()
        }

        /**
         * Bildirim hâlâ çekmecedeyken, çekmecede tıklanınca açılacak hedefi Notifilter içinden aç.
         * Eşleşme bulunamazsa false döner (UI fallback yapabilir).
         */
        fun openActiveNotification(
            packageName: String,
            timestamp: Long,
            content: String
        ): Boolean {
            return instance?.performOpenActiveNotification(packageName, timestamp, content) ?: false
        }
    }

    private lateinit var spamEngine: SpamEngine
    private lateinit var database: AppDatabase
    private lateinit var focusModePrefs: FocusModePreferences
    private lateinit var importantChannelsPrefs: ImportantChannelsPreferences
    private lateinit var filterRulesPrefs: FilterRulesPreferences

    override fun onCreate() {
        super.onCreate()
        spamEngine = SpamEngine()
        database = (application as NotifilterApplication).database
        focusModePrefs = FocusModePreferences(this)
        importantChannelsPrefs = ImportantChannelsPreferences(this)
        filterRulesPrefs = FilterRulesPreferences(this)
    }

    private fun cancelSbn(sbn: StatusBarNotification, reason: String) {
        val key = sbn.key
        try {
            cancelNotification(key)
            Log.d(
                TAG,
                "cancelNotification(key) ok reason=$reason pkg=${sbn.packageName} key=$key id=${sbn.id} tag=${sbn.tag}"
            )
            return
        } catch (e: SecurityException) {
            Log.e(TAG, "cancelNotification(key) SecurityException reason=$reason pkg=${sbn.packageName} key=$key", e)
        } catch (e: Exception) {
            Log.e(TAG, "cancelNotification(key) failed reason=$reason pkg=${sbn.packageName} key=$key", e)
        }

        // Fallback: bazı cihazlarda / bazı bildirim tiplerinde key iptali işe yaramayabiliyor.
        try {
            @Suppress("DEPRECATION")
            cancelNotification(sbn.packageName, sbn.tag, sbn.id)
            Log.d(
                TAG,
                "cancelNotification(pkg,tag,id) ok reason=$reason pkg=${sbn.packageName} id=${sbn.id} tag=${sbn.tag}"
            )
        } catch (e: SecurityException) {
            Log.e(
                TAG,
                "cancelNotification(pkg,tag,id) SecurityException reason=$reason pkg=${sbn.packageName} id=${sbn.id} tag=${sbn.tag}",
                e
            )
        } catch (e: Exception) {
            Log.e(
                TAG,
                "cancelNotification(pkg,tag,id) failed reason=$reason pkg=${sbn.packageName} id=${sbn.id} tag=${sbn.tag}",
                e
            )
        }
    }

    private val dedupe = ConcurrentHashMap<String, Long>()
    private val isGameCache = ConcurrentHashMap<String, Boolean>()
    private val recentlyProcessedKeys = ConcurrentHashMap<String, Long>()
    private val recentlyProcessedContent = ConcurrentHashMap<String, Long>()
    private val dedupeWindowMs = 15000L  // 15 sn – aynı bildirim tekrar gelirse atla
    private val contentDedupeWindowMs = 30000L  // 30 sn – aynı uygulama + içerik = muhtemelen çift

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        if (!BuildConfig.BILLING_BYPASS && !EntitlementStore.isEntitled(this)) {
            return
        }
        try {
            val isGroupSummary = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH &&
                (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0

            val key = sbn.key
            val now = System.currentTimeMillis()

            val content = extractContent(sbn)

            // Dedupe: bazı uygulamalar (Instagram vb.) aynı bildirimi çok hızlı update ederek yeniden post edebilir.
            // Bu durumda DB'ye tekrar yazmayı atlamak isteyebiliriz ama iptal (cancel) işlemini atlamamalıyız.
            val lastSeenKey = recentlyProcessedKeys.put(key, now)
            val isDuplicateKey = lastSeenKey != null && (now - lastSeenKey) < dedupeWindowMs

            val contentKey = "${sbn.packageName}|${content.lowercase().trim().take(120)}"
            val contentLastSeen = recentlyProcessedContent.put(contentKey, now)
            val isDuplicateContent = contentLastSeen != null && (now - contentLastSeen) < contentDedupeWindowMs

            val appName = getAppName(sbn.packageName)

            val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                sbn.notification.channelId
            } else null

            val record = NotificationRecord(
                packageName = sbn.packageName,
                appName = appName,
                content = content,
                channelId = channelId,
                timestamp = sbn.postTime,
                isBlocked = false,
                blockReason = null
            )

            val shouldBlock: Boolean
            val blockReason: String?

            if (focusModePrefs.isFocusModeEnabled) {
                if (importantChannelsPrefs.isWhitelisted(record.packageName)) {
                    shouldBlock = false
                    blockReason = null
                } else {
                    shouldBlock = true
                    blockReason = "Genel Odak Modu aktif"
                }
            } else if (filterRulesPrefs.isGlobalGamesBlockEnabled && isGamePackage(record.packageName)) {
                shouldBlock = true
                blockReason = "Games pack active"
            } else {
                val whitelistedPackages = importantChannelsPrefs.whitelistedPackages
                val baseConfig = filterRulesPrefs.toFilterRulesConfig()
                val appAllow = filterRulesPrefs.getUserAppContentAllowWords(record.packageName)
                val appBlock = filterRulesPrefs.getUserAppContentBlockWords(record.packageName)
                val emojiBlockEnabled = filterRulesPrefs.isGlobalEmojiBlockEnabled &&
                    !filterRulesPrefs.isUserAppEmojiAllowed(record.packageName)
                val config = baseConfig.copy(
                    contentAllow = (baseConfig.contentAllow + appAllow).distinct(),
                    contentBlock = (baseConfig.contentBlock + appBlock).distinct(),
                    blockIfHasEmoji = emojiBlockEnabled
                )
                val result = spamEngine.analyze(record, whitelistedPackages, config)
                if (BuildConfig.DEBUG) {
                    val c = content.replace("\n", " ").take(180)
                    Log.d(
                        TAG,
                        "ANALYZE pkg=${sbn.packageName} key=${sbn.key} id=${sbn.id} tag=${sbn.tag} groupSummary=$isGroupSummary " +
                            "content='${c}' config{channelBlock=${config.channelBlock.size}, channelAllow=${config.channelAllow.size}, " +
                            "contentAllow=${config.contentAllow.size}, contentBlock=${config.contentBlock.size}, channelIdsBlocked=${config.channelIdsBlocked.size}} " +
                            "result=${result::class.simpleName}${if (result is SpamResult.Block) ":${result.reason}" else ""}"
                    )
                }
                when (result) {
                    is SpamResult.Allow -> {
                        shouldBlock = false
                        blockReason = null
                    }
                    is SpamResult.Block -> {
                        shouldBlock = true
                        blockReason = result.reason
                    }
                }
            }

            val finalRecord = record.copy(isBlocked = shouldBlock, blockReason = blockReason)

            // İzinli bildirimlerde dedupe DB'yi şişirmesin.
            // Ancak bloklanan bildirimlerde her zaman DB'ye yazmalıyız; aksi halde "gizlendi ama arşivde yok" olur.
            val shouldInsert = shouldBlock || (!isDuplicateKey && !isDuplicateContent)
            if (shouldInsert) {
                serviceScope.launch {
                    try {
                        database.notificationRecordDao().insert(finalRecord)
                    } catch (e: Exception) {
                        Log.e(TAG, "DB insert failed", e)
                    }
                }
            }

            if (shouldBlock) {
                Log.d(
                    TAG,
                    "BLOCK decision pkg=${sbn.packageName} key=${sbn.key} id=${sbn.id} tag=${sbn.tag} groupSummary=$isGroupSummary reason=$blockReason"
                )
                cancelSbn(sbn, blockReason ?: "blocked")
            }
            pruneOldKeys(now)
        } catch (e: Exception) {
            Log.e(TAG, "onNotificationPosted error", e)
        }
    }

    private fun getAppName(packageName: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    private fun extractContent(sbn: StatusBarNotification): String {
        val notification = sbn.notification
        val extras = notification.extras ?: return ""

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()
            ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString()
            ?: ""

        // MessagingStyle: bazı uygulamalar (Instagram DM vb.) mesaj metnini EXTRA_MESSAGES içine koyar.
        val messagesText = runCatching {
            @Suppress("DEPRECATION")
            val raw = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            raw
                ?.mapNotNull { it as? Bundle }
                ?.mapNotNull { b -> b.getCharSequence("text")?.toString() }
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.takeLast(3)
                ?.joinToString(" ")
        }.getOrNull().orEmpty()

        val lines = runCatching {
            @Suppress("DEPRECATION")
            extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
                ?.mapNotNull { it?.toString() }
                ?.filter { it.isNotBlank() }
                ?.joinToString(" ")
        }.getOrNull().orEmpty()

        return buildString {
            if (title.isNotBlank()) append(title)
            val combined = listOf(text, messagesText, lines).filter { it.isNotBlank() }.joinToString(" ").trim()
            if (title.isNotBlank() && combined.isNotBlank()) append(" ")
            if (combined.isNotBlank()) append(combined)
        }.ifBlank { getString(R.string.generic_notification) }
    }

    private fun performCancelAndSaveForPackage(packageName: String) {
        try {
            getActiveNotifications()
                ?.filter { it.packageName == packageName }
                ?.forEach { sbn ->
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT_WATCH ||
                        (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0
                    ) {
                        saveAndCancel(sbn, "Gizle kaydet")
                    }
                }
        } catch (e: SecurityException) { /* izin yok */ }
    }

    private fun performRescanAndCancelAllActive() {
        try {
            val active = getActiveNotifications() ?: return
            if (active.isEmpty()) return

            val whitelistedPackages = importantChannelsPrefs.whitelistedPackages
            val baseConfig = filterRulesPrefs.toFilterRulesConfig()

            active.forEach { sbn ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH &&
                    (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
                ) return@forEach

                val pkg = sbn.packageName
                if (pkg in whitelistedPackages) return@forEach

                val appAllow = filterRulesPrefs.getUserAppContentAllowWords(pkg)
                val appBlock = filterRulesPrefs.getUserAppContentBlockWords(pkg)
                val emojiBlockEnabled = filterRulesPrefs.isGlobalEmojiBlockEnabled &&
                    !filterRulesPrefs.isUserAppEmojiAllowed(pkg)
                val config = baseConfig.copy(
                    contentAllow = (baseConfig.contentAllow + appAllow).distinct(),
                    contentBlock = (baseConfig.contentBlock + appBlock).distinct(),
                    blockIfHasEmoji = emojiBlockEnabled
                )

                val content = extractContent(sbn)
                val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    sbn.notification.channelId
                } else null

                val record = NotificationRecord(
                    packageName = pkg,
                    appName = getAppName(pkg),
                    content = content,
                    channelId = channelId,
                    timestamp = sbn.postTime,
                    isBlocked = false,
                    blockReason = null
                )

                val result = spamEngine.analyze(record, whitelistedPackages, config)
                if (result is SpamResult.Block) {
                    Log.d(TAG, "STARTUP RESCAN BLOCK pkg=$pkg key=${sbn.key} reason=${result.reason}")
                    serviceScope.launch {
                        try {
                            database.notificationRecordDao().insert(record.copy(isBlocked = true, blockReason = result.reason))
                        } catch (e: Exception) {
                            Log.e(TAG, "DB insert failed during startup rescan", e)
                        }
                    }
                    cancelSbn(sbn, result.reason)
                }
            }
        } catch (e: SecurityException) { /* izin yok */ }
    }

    private fun performRescanAndCancelForPackage(packageName: String) {
        try {
            val active = getActiveNotifications()?.filter { it.packageName == packageName } ?: return
            val whitelistedPackages = importantChannelsPrefs.whitelistedPackages
            if (packageName in whitelistedPackages) return

            val baseConfig = filterRulesPrefs.toFilterRulesConfig()
            val appAllow = filterRulesPrefs.getUserAppContentAllowWords(packageName)
            val appBlock = filterRulesPrefs.getUserAppContentBlockWords(packageName)
            val emojiBlockEnabled = filterRulesPrefs.isGlobalEmojiBlockEnabled &&
                !filterRulesPrefs.isUserAppEmojiAllowed(packageName)
            val config = baseConfig.copy(
                contentAllow = (baseConfig.contentAllow + appAllow).distinct(),
                contentBlock = (baseConfig.contentBlock + appBlock).distinct(),
                blockIfHasEmoji = emojiBlockEnabled
            )

            active.forEach { sbn ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH &&
                    (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
                ) return@forEach

                val content = extractContent(sbn)
                val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    sbn.notification.channelId
                } else null

                val record = NotificationRecord(
                    packageName = sbn.packageName,
                    appName = getAppName(sbn.packageName),
                    content = content,
                    channelId = channelId,
                    timestamp = sbn.postTime,
                    isBlocked = false,
                    blockReason = null
                )

                val result = spamEngine.analyze(record, whitelistedPackages, config)
                if (result is SpamResult.Block) {
                    Log.d(TAG, "RESCAN BLOCK pkg=${sbn.packageName} key=${sbn.key} reason=${result.reason}")
                    serviceScope.launch {
                        try {
                            database.notificationRecordDao().insert(record.copy(isBlocked = true, blockReason = result.reason))
                        } catch (e: Exception) {
                            Log.e(TAG, "DB insert failed during rescan", e)
                        }
                    }
                    cancelSbn(sbn, result.reason)
                }
            }
        } catch (e: SecurityException) { /* izin yok */ }
    }

    private fun performOpenActiveNotification(
        packageName: String,
        timestamp: Long,
        content: String
    ): Boolean {
        val active = try {
            getActiveNotifications()?.filter { it.packageName == packageName }
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            null
        } ?: return false

        if (active.isEmpty()) return false

        val now = System.currentTimeMillis()
        val contentNorm = content.trim()

        val best = active
            .map { sbn ->
                val sbnContent = runCatching { extractContent(sbn) }.getOrDefault("")
                val timeDiffMs = abs(sbn.postTime - timestamp)
                val contentScore = when {
                    sbnContent == contentNorm -> 0
                    sbnContent.contains(contentNorm, ignoreCase = true) -> 1
                    contentNorm.isNotBlank() && sbnContent.contains(contentNorm.take(24), ignoreCase = true) -> 2
                    else -> 5
                }
                val timeScore = (timeDiffMs / 1000L).toInt()
                val freshnessScore = ((now - sbn.postTime).coerceAtLeast(0L) / 1000L).toInt()
                Triple(sbn, contentScore, timeScore + freshnessScore)
            }
            .sortedWith(compareBy<Triple<StatusBarNotification, Int, Int>> { it.second }.thenBy { it.third })
            .firstOrNull()
            ?.first

        if (best == null) return false

        val intent = best.notification.contentIntent ?: best.notification.fullScreenIntent
        if (intent == null) return false

        return try {
            intent.send()
            true
        } catch (e: Exception) {
            Log.e(TAG, "contentIntent.send() failed pkg=$packageName", e)
            false
        }
    }

    private fun performCancelAllAndSave() {
        try {
            getActiveNotifications()
                ?.forEach { sbn ->
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT_WATCH ||
                        (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0
                    ) {
                        saveAndCancel(sbn, "Genel Odak Modu aktif")
                    }
                }
        } catch (e: SecurityException) { /* izin yok */ }
    }

    private fun saveAndCancel(sbn: StatusBarNotification, blockReason: String) {
        val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            sbn.notification.channelId
        } else null
        val record = NotificationRecord(
            packageName = sbn.packageName,
            appName = getAppName(sbn.packageName),
            content = extractContent(sbn),
            channelId = channelId,
            timestamp = sbn.postTime,
            isBlocked = true,
            blockReason = blockReason
        )
        serviceScope.launch {
            database.notificationRecordDao().insert(record)
        }
        cancelSbn(sbn, blockReason)
    }

    private fun pruneOldKeys(now: Long) {
        val keyCutoff = now - dedupeWindowMs
        val contentCutoff = now - contentDedupeWindowMs
        if (recentlyProcessedKeys.size > 500) {
            recentlyProcessedKeys.keys.removeAll { (recentlyProcessedKeys[it] ?: 0L) < keyCutoff }
        }
        if (recentlyProcessedContent.size > 500) {
            recentlyProcessedContent.keys.removeAll { (recentlyProcessedContent[it] ?: 0L) < contentCutoff }
        }
    }
}
