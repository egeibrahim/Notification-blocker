package com.notifilter.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.notifilter.engine.FilterRulesConfig
import java.util.Locale

/**
 * Sadeleştirilmiş blok kuralları.
 * Her blok kategorisi hem kanal ID hem içerik kelimelerini kapsar (Google Play kategorileri mantığı).
 * - channel_allow: Kanal ID'de geçerse bypass
 */
class FilterRulesPreferences(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun exportAllPrefs(): Map<String, Any?> {
        return prefs.all
    }

    fun importAllPrefs(snapshot: Map<String, Any?>) {
        prefs.edit(commit = true) {
            clear()
            snapshot.forEach { (key, value) ->
                when (value) {
                    null -> remove(key)
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Float -> putFloat(key, value)
                    is String -> {
                        val parsed = parseStringAsStringSetIfApplicable(key, value)
                        if (parsed != null) {
                            putStringSet(key, parsed)
                        } else {
                            putString(key, value)
                        }
                    }
                    is Set<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        putStringSet(key, value.filterIsInstance<String>().toSet())
                    }
                    is List<*> -> {
                        val asStrings = value.filterIsInstance<String>()
                        if (asStrings.size == value.size) {
                            putStringSet(key, asStrings.toSet())
                        } else {
                            putString(key, value.toString())
                        }
                    }
                    else -> {
                        putString(key, value.toString())
                    }
                }
            }
        }
    }

    private fun parseStringAsStringSetIfApplicable(key: String, raw: String): Set<String>? {
        val isKnownSetKey = (
            key == KEY_USER_CONTENT_BLOCK_WORDS ||
                key == KEY_USER_CONTENT_ALLOW_WORDS ||
                key == KEY_USER_BLOCKED_CHANNELS ||
                key == KEY_BLOCK_CATEGORIES_ACTIVE ||
                key == KEY_CHANNEL_ALLOW ||
                key == KEY_DISABLED_RECOMMENDED_CONTENT_WORDS ||
                key == KEY_DISABLED_RECOMMENDED_CHANNEL_WORDS ||
                key == KEY_ENABLED_LANGUAGE_PACKS ||
                key.startsWith("filter_user_category_words_") ||
                key.startsWith("filter_user_app_content_allow_words_") ||
                key.startsWith("filter_user_app_content_block_words_")
            )

        if (!isKnownSetKey) return null

        val trimmed = raw.trim()
        val content = when {
            trimmed.startsWith("[") && trimmed.endsWith("]") -> trimmed.substring(1, trimmed.length - 1)
            else -> trimmed
        }

        val parts = content
            .split(',', ';', '|')
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .toSet()

        return parts.takeIf { it.isNotEmpty() }
    }

    var enabledLanguagePacks: Set<String>
        get() {
            val fromPrefs = getStringSetCompat(KEY_ENABLED_LANGUAGE_PACKS)
            if (fromPrefs.isNotEmpty()) return fromPrefs
            return setOf(PACK_TR, PACK_EN)
        }
        set(value) = prefs.edit(commit = true) {
            putStringSet(KEY_ENABLED_LANGUAGE_PACKS, HashSet(value))
        }

    private fun getStringSetCompat(key: String): Set<String> {
        val raw = prefs.all[key] ?: return emptySet()
        return when (raw) {
            is Set<*> -> normalizeStringSet(raw.filterIsInstance<String>())
            is String -> {
                val parsed = normalizeStringSet(listOf(raw))
                if (parsed.isNotEmpty()) {
                    prefs.edit(commit = true) {
                        putStringSet(key, HashSet(parsed))
                    }
                }
                parsed
            }
            else -> emptySet()
        }
    }

    private fun normalizeStringSet(values: Iterable<String>): Set<String> {
        return values
            .flatMap { v ->
                v.split(',', ';', '|')
                    .map { it.trim().lowercase() }
                    .filter { it.isNotBlank() }
            }
            .toSet()
    }

    fun isLanguagePackEnabled(packId: String): Boolean = packId in enabledLanguagePacks

    fun toggleLanguagePack(packId: String) {
        val current = enabledLanguagePacks
        val next = if (packId in current) current - packId else current + packId
        if (next.isNotEmpty()) enabledLanguagePacks = next
    }

    fun setSingleLanguagePack(packId: String) {
        enabledLanguagePacks = setOf(packId)
    }

    fun getDisabledRecommendedContentWords(packId: String): Set<String> = getDisabledRecommendedContentWords()

    fun isRecommendedContentWordEnabled(packId: String, word: String): Boolean =
        isRecommendedContentWordEnabled(word)

    fun toggleRecommendedContentWord(packId: String, word: String) {
        toggleRecommendedContentWord(word)
    }

    fun toggleRecommendedContentWord(word: String) {
        val w = word.trim().lowercase()
        if (w.isBlank()) return

        val disabled = getDisabledRecommendedContentWords()
        val next = if (w in disabled) disabled - w else disabled + w
        setDisabledRecommendedContentWords(next)
    }

    private fun migrateDisabledRecommendedContentWordsIfNeeded(): Set<String> {
        val fromNew = getStringSetCompat(KEY_DISABLED_RECOMMENDED_CONTENT_WORDS)
        if (fromNew.isNotEmpty()) return fromNew

        val legacyTr = getStringSetCompat("filter_disabled_recommended_content_words_${PACK_TR}")
        val legacyEn = getStringSetCompat("filter_disabled_recommended_content_words_${PACK_EN}")
        val merged = (legacyTr + legacyEn)
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .toSet()

        prefs.edit(commit = true) {
            putStringSet(KEY_DISABLED_RECOMMENDED_CONTENT_WORDS, HashSet(merged))
        }
        return merged
    }

    fun getDisabledRecommendedContentWords(): Set<String> = migrateDisabledRecommendedContentWordsIfNeeded()

    private fun setDisabledRecommendedContentWords(words: Set<String>) {
        prefs.edit(commit = true) {
            putStringSet(
                KEY_DISABLED_RECOMMENDED_CONTENT_WORDS,
                HashSet(words.map { it.trim().lowercase() }.filter { it.isNotBlank() })
            )
        }
    }

    var isGlobalEmojiBlockEnabled: Boolean
        get() = prefs.getBoolean(KEY_GLOBAL_EMOJI_BLOCK_ENABLED, false)
        set(value) = prefs.edit(commit = true) {
            putBoolean(KEY_GLOBAL_EMOJI_BLOCK_ENABLED, value)
        }

    var isGlobalGamesBlockEnabled: Boolean
        get() = prefs.getBoolean(KEY_GLOBAL_GAMES_BLOCK_ENABLED, false)
        set(value) = prefs.edit(commit = true) {
            putBoolean(KEY_GLOBAL_GAMES_BLOCK_ENABLED, value)
        }

    fun isUserAppEmojiBlockEnabled(packageName: String): Boolean {
        val key = getUserAppEmojiBlockEnabledKey(packageName)
        return prefs.getBoolean(key, false)
    }

    fun setUserAppEmojiBlockEnabled(packageName: String, enabled: Boolean) {
        val key = getUserAppEmojiBlockEnabledKey(packageName)
        prefs.edit(commit = true) {
            putBoolean(key, enabled)
        }
    }

    fun isUserAppEmojiAllowed(packageName: String): Boolean {
        val key = getUserAppEmojiAllowKey(packageName)
        return prefs.getBoolean(key, false)
    }

    fun setUserAppEmojiAllowed(packageName: String, allowed: Boolean) {
        val key = getUserAppEmojiAllowKey(packageName)
        prefs.edit(commit = true) {
            putBoolean(key, allowed)
        }
    }

    fun isRecommendedContentWordEnabled(word: String): Boolean =
        word.trim().lowercase() !in getDisabledRecommendedContentWords()

    private fun migrateDisabledRecommendedChannelWordsIfNeeded(): Set<String> {
        val fromNew = getStringSetCompat(KEY_DISABLED_RECOMMENDED_CHANNEL_WORDS)
        if (fromNew.isNotEmpty()) return fromNew

        val legacyTr = getStringSetCompat("filter_disabled_recommended_channel_words_${PACK_TR}")
        val legacyEn = getStringSetCompat("filter_disabled_recommended_channel_words_${PACK_EN}")
        val merged = (legacyTr + legacyEn)
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .toSet()

        prefs.edit(commit = true) {
            putStringSet(KEY_DISABLED_RECOMMENDED_CHANNEL_WORDS, HashSet(merged))
        }
        return merged
    }

    fun getDisabledRecommendedChannelWords(): Set<String> = migrateDisabledRecommendedChannelWordsIfNeeded()

    private fun setDisabledRecommendedChannelWords(words: Set<String>) {
        prefs.edit(commit = true) {
            putStringSet(
                KEY_DISABLED_RECOMMENDED_CHANNEL_WORDS,
                HashSet(words.map { it.trim().lowercase() }.filter { it.isNotBlank() })
            )
        }
    }

    fun isRecommendedChannelWordEnabled(word: String): Boolean =
        word.trim().lowercase() !in getDisabledRecommendedChannelWords()

    fun toggleRecommendedChannelWord(packId: String, word: String) {
        toggleRecommendedChannelWord(word)
    }

    fun toggleRecommendedChannelWord(word: String) {
        val w = word.trim().lowercase()
        if (w.isBlank()) return

        val disabled = getDisabledRecommendedChannelWords()
        val next = if (w in disabled) disabled - w else disabled + w
        setDisabledRecommendedChannelWords(next)
    }

    private fun getUserCategoryWordsKey(categoryId: String): String =
        "filter_user_category_words_${categoryId.trim().lowercase()}"

    fun getUserCategoryWords(categoryId: String): Set<String> {
        val key = getUserCategoryWordsKey(categoryId)
        return getStringSetCompat(key)
    }

    private fun setUserCategoryWords(categoryId: String, words: Set<String>) {
        val key = getUserCategoryWordsKey(categoryId)
        prefs.edit(commit = true) {
            putStringSet(key, HashSet(words.map { it.trim().lowercase() }.filter { it.isNotBlank() }))
        }
    }

    fun addUserCategoryWord(categoryId: String, word: String) {
        val w = word.trim().lowercase()
        if (w.isBlank()) return
        setUserCategoryWords(categoryId, getUserCategoryWords(categoryId) + w)
    }

    fun removeUserCategoryWord(categoryId: String, word: String) {
        val w = word.trim().lowercase()
        if (w.isBlank()) return
        setUserCategoryWords(categoryId, getUserCategoryWords(categoryId) - w)
    }

    fun getUserCategoryWordsForActiveCategories(): Set<String> {
        return blockCategoriesActive
            .flatMap { catId -> getUserCategoryWords(catId) }
            .toSet()
    }

    var userContentAllowWords: Set<String>
        get() = getStringSetCompat(KEY_USER_CONTENT_ALLOW_WORDS)
        set(value) = prefs.edit(commit = true) {
            putStringSet(
                KEY_USER_CONTENT_ALLOW_WORDS,
                HashSet(value.map { it.trim().lowercase() }.filter { it.isNotBlank() })
            )
        }

    fun addUserContentAllowWord(word: String) {
        val w = word.trim().lowercase()
        if (w.isBlank()) return
        userContentAllowWords = userContentAllowWords + w
    }

    fun removeUserContentAllowWord(word: String) {
        val w = word.trim().lowercase()
        if (w.isBlank()) return
        userContentAllowWords = userContentAllowWords - w
    }

    private fun normalizePackageKey(packageName: String): String =
        packageName.trim().lowercase().replace(Regex("[^a-z0-9_.]+"), "_")

    private fun getUserAppContentAllowWordsKey(packageName: String): String =
        "filter_user_app_content_allow_words_${normalizePackageKey(packageName)}"

    private fun getUserAppContentBlockWordsKey(packageName: String): String =
        "filter_user_app_content_block_words_${normalizePackageKey(packageName)}"

    private fun getUserAppEmojiBlockEnabledKey(packageName: String): String =
        "filter_user_app_emoji_block_enabled_${normalizePackageKey(packageName)}"

    private fun getUserAppEmojiAllowKey(packageName: String): String =
        "filter_user_app_emoji_allow_${normalizePackageKey(packageName)}"

    fun getUserAppContentAllowWords(packageName: String): Set<String> {
        val key = getUserAppContentAllowWordsKey(packageName)
        return getStringSetCompat(key)
    }

    fun addUserAppContentAllowWord(packageName: String, word: String) {
        val w = word.trim().lowercase()
        if (w.isBlank()) return

        val key = getUserAppContentAllowWordsKey(packageName)
        val current = getStringSetCompat(key)
        prefs.edit(commit = true) {
            putStringSet(key, HashSet(current + w))
        }
    }

    fun removeUserAppContentAllowWord(packageName: String, word: String) {
        val w = word.trim().lowercase()
        if (w.isBlank()) return

        val key = getUserAppContentAllowWordsKey(packageName)
        val current = getStringSetCompat(key)
        prefs.edit(commit = true) {
            putStringSet(key, HashSet(current - w))
        }
    }

    fun getUserAppContentBlockWords(packageName: String): Set<String> {
        val key = getUserAppContentBlockWordsKey(packageName)
        return getStringSetCompat(key)
    }

    fun addUserAppContentBlockWord(packageName: String, word: String) {
        val w = word.trim().lowercase()
        if (w.isBlank()) return

        val key = getUserAppContentBlockWordsKey(packageName)
        val current = getStringSetCompat(key)
        prefs.edit(commit = true) {
            putStringSet(key, HashSet(current + w))
        }
    }

    fun removeUserAppContentBlockWord(packageName: String, word: String) {
        val w = word.trim().lowercase()
        if (w.isBlank()) return

        val key = getUserAppContentBlockWordsKey(packageName)
        val current = getStringSetCompat(key)
        prefs.edit(commit = true) {
            putStringSet(key, HashSet(current - w))
        }
    }

    /** Aktif blok kategorileri (her biri hem kanal ID hem içerik blokları içerir) */
    var blockCategoriesActive: Set<String>
        get() {
            // Yeni anahtar kullan; eski prefs yoksa varsayılan
            val fromNew = getStringSetCompat(KEY_BLOCK_CATEGORIES_ACTIVE)
            if (fromNew.isNotEmpty()) return fromNew
            // Eski prefs → yeni kategorilere migrasyon
            val migrated = migrateFromLegacyPrefs()
            if (migrated.isNotEmpty()) {
                blockCategoriesActive = migrated
                return migrated
            }
            return DEFAULT_BLOCK_CATEGORIES
        }
        set(value) = prefs.edit(commit = true) {
            putStringSet(KEY_BLOCK_CATEGORIES_ACTIVE, HashSet(value))
        }

    fun isBlockCategoryActive(categoryId: String): Boolean = categoryId in blockCategoriesActive

    fun toggleBlockCategory(categoryId: String) {
        blockCategoriesActive = if (categoryId in blockCategoriesActive) {
            blockCategoriesActive - categoryId
        } else {
            blockCategoriesActive + categoryId
        }
    }

    /** Eski channel/content prefs → yeni kategori ID'lerine dönüştür */
    private fun migrateFromLegacyPrefs(): Set<String> {
        val oldChannel = getStringSetCompat(KEY_CHANNEL_BLOCK_ACTIVE)
        val oldContent = getStringSetCompat(KEY_CONTENT_BLOCK_ACTIVE)
        if (oldChannel.isEmpty() || oldContent.isEmpty()) return emptySet()
        val ids = mutableSetOf<String>()
        LEGACY_TO_CATEGORY.forEach { (legacy, categoryId) ->
            if (legacy in oldChannel || legacy in oldContent) ids.add(categoryId)
        }
        return ids
    }

    /** Kanal ID allow kelimeleri (geçir) */
    var channelAllowKeywords: Set<String>
        get() = getStringSetCompat(KEY_CHANNEL_ALLOW).ifEmpty { DEFAULT_CHANNEL_ALLOW }
        set(value) = prefs.edit(commit = true) {
            putStringSet(KEY_CHANNEL_ALLOW, HashSet(value.map { it.trim().lowercase() }.filter { it.isNotBlank() }))
        }

    fun addChannelAllow(word: String) {
        val w = word.trim().lowercase()
        if (w.isNotBlank()) channelAllowKeywords = channelAllowKeywords + w
    }

    fun removeChannelAllow(word: String) {
        channelAllowKeywords = channelAllowKeywords - word
    }

    /** Kullanıcının eklediği içerik blok kelimeleri */
    var userContentBlockWords: Set<String>
        get() = getStringSetCompat(KEY_USER_CONTENT_BLOCK_WORDS)
        set(value) = prefs.edit(commit = true) {
            putStringSet(
                KEY_USER_CONTENT_BLOCK_WORDS,
                HashSet(value.map { it.trim().lowercase() }.filter { it.isNotBlank() })
            )
        }

    fun addUserContentBlockWord(word: String) {
        val w = word.trim().lowercase()
        if (w.isNotBlank()) userContentBlockWords = userContentBlockWords + w
    }

    fun removeUserContentBlockWord(word: String) {
        userContentBlockWords = userContentBlockWords - word
    }

    /** Kullanıcının tek tıkla engellediği kanallar: "packageName|channelId" */
    var userBlockedChannelKeys: Set<String>
        get() = getStringSetCompat(KEY_USER_BLOCKED_CHANNELS)
        set(value) = prefs.edit(commit = true) {
            putStringSet(KEY_USER_BLOCKED_CHANNELS, HashSet(value.map { it.trim() }.filter { it.isNotBlank() }))
        }

    fun addUserBlockedChannel(packageName: String, channelId: String?) {
        val key = toChannelKey(packageName, channelId)
        if (key.isNotBlank()) userBlockedChannelKeys = userBlockedChannelKeys + key
    }

    fun removeUserBlockedChannel(packageName: String, channelId: String?) {
        val key = toChannelKey(packageName, channelId)
        if (key.isNotBlank()) userBlockedChannelKeys = userBlockedChannelKeys - key
    }

    fun isUserBlockedChannel(packageName: String, channelId: String?): Boolean =
        toChannelKey(packageName, channelId) in userBlockedChannelKeys

    private fun toChannelKey(packageName: String, channelId: String?): String =
        "$packageName|${channelId ?: ""}"

    private fun getActiveBlockCategories(): List<BlockCategory> {
        val selectedPacks = enabledLanguagePacks
        val selected = buildList {
            if (selectedPacks.isEmpty()) {
                addAll(TR_BLOCK_CATEGORIES)
                addAll(EN_BLOCK_CATEGORIES)
            } else {
                if (PACK_TR in selectedPacks) addAll(TR_BLOCK_CATEGORIES)
                if (PACK_EN in selectedPacks) addAll(EN_BLOCK_CATEGORIES)
            }
        }

        val merged = LinkedHashMap<String, BlockCategory>()
        selected.forEach { cat ->
            val existing = merged[cat.id]
            if (existing == null) {
                merged[cat.id] = cat
            } else {
                merged[cat.id] = existing.copy(
                    channelKeywords = (existing.channelKeywords + cat.channelKeywords).distinct(),
                    contentKeywords = (existing.contentKeywords + cat.contentKeywords).distinct()
                )
            }
        }
        return merged.values.toList()
    }

    fun getMergedBlockCategories(): List<BlockCategory> = getActiveBlockCategories()

    private fun getCategoriesForPack(packId: String): List<BlockCategory> {
        return when (packId) {
            PACK_TR -> TR_BLOCK_CATEGORIES
            PACK_EN -> EN_BLOCK_CATEGORIES
            else -> emptyList()
        }
    }

    fun getRecommendedContentWordsForPack(packId: String): Set<String> {
        return getRecommendedContentWords()
    }

    fun getRecommendedChannelWordsForPack(packId: String): Set<String> {
        return getRecommendedChannelWords()
    }

    fun getEnabledRecommendedContentWordsForPack(packId: String): Set<String> {
        return getEnabledRecommendedContentWords()
    }

    fun getEnabledRecommendedChannelWordsForPack(packId: String): Set<String> {
        return getEnabledRecommendedChannelWords()
    }

    fun getEnabledRecommendedContentWordsForEnabledPacks(): Set<String> {
        return getEnabledRecommendedContentWords()
    }

    fun getEnabledRecommendedChannelWordsForEnabledPacks(): Set<String> {
        return getEnabledRecommendedChannelWords()
    }

    fun getRecommendedContentWords(): Set<String> {
        val categories = getActiveBlockCategories().filter { it.id in blockCategoriesActive }
        return categories
            .flatMap { it.contentKeywords }
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .toSet()
    }

    fun getRecommendedChannelWords(): Set<String> {
        val categories = getActiveBlockCategories().filter { it.id in blockCategoriesActive }
        return categories
            .flatMap { it.channelKeywords }
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .toSet()
    }

    fun getEnabledRecommendedContentWords(): Set<String> {
        val all = getRecommendedContentWords()
        val disabled = getDisabledRecommendedContentWords()
        return all - disabled
    }

    fun getEnabledRecommendedChannelWords(): Set<String> {
        val all = getRecommendedChannelWords()
        val disabled = getDisabledRecommendedChannelWords()
        return all - disabled
    }

    /** Aktif kategorilerden kanal ID blok kelimeleri */
    fun getExpandedChannelBlockKeywords(): Set<String> {
        val categories = getActiveBlockCategories()
        return blockCategoriesActive.flatMap { id ->
            categories.find { it.id == id }?.channelKeywords ?: emptyList()
        }.toSet()
    }

    /** Aktif kategorilerden içerik blok kelimeleri */
    fun getExpandedContentBlockWords(): Set<String> {
        val categories = getActiveBlockCategories()
        return blockCategoriesActive.flatMap { id ->
            categories.find { it.id == id }?.contentKeywords ?: emptyList()
        }.toSet()
    }

    /** Tek liste: (id, etiket) – Blok Kuralları sayfasında gösterilecek */
    fun getBlockCategoryOptions(): List<Pair<String, String>> =
        getActiveBlockCategories().map { it.id to it.label }

    fun toFilterRulesConfig(): FilterRulesConfig = FilterRulesConfig(
        channelBlock = getEnabledRecommendedChannelWords().toList(),
        channelAllow = channelAllowKeywords.toList(),
        contentAllow = userContentAllowWords.toList(),
        contentBlock = (getEnabledRecommendedContentWords() + userContentBlockWords + getUserCategoryWordsForActiveCategories()).toList(),
        channelIdsBlocked = userBlockedChannelKeys.toList()
    )

    companion object {
        private const val PREFS_NAME = "notifilter_prefs"
        private const val KEY_BLOCK_CATEGORIES_ACTIVE = "filter_block_categories_active"
        private const val KEY_CHANNEL_BLOCK_ACTIVE = "filter_channel_block_active"
        private const val KEY_CHANNEL_ALLOW = "filter_channel_allow"
        private const val KEY_CONTENT_BLOCK_ACTIVE = "filter_content_block_active"
        private const val KEY_USER_BLOCKED_CHANNELS = "filter_user_blocked_channels"
        private const val KEY_USER_CONTENT_BLOCK_WORDS = "filter_user_content_block_words"
        private const val KEY_USER_CONTENT_ALLOW_WORDS = "filter_user_content_allow_words"
        private const val KEY_ENABLED_LANGUAGE_PACKS = "filter_enabled_language_packs"

        private const val KEY_DISABLED_RECOMMENDED_CONTENT_WORDS = "filter_disabled_recommended_content_words"
        private const val KEY_DISABLED_RECOMMENDED_CHANNEL_WORDS = "filter_disabled_recommended_channel_words"

        private const val KEY_GLOBAL_EMOJI_BLOCK_ENABLED = "filter_global_emoji_block_enabled"
        private const val KEY_GLOBAL_GAMES_BLOCK_ENABLED = "filter_global_games_block_enabled"

        const val PACK_TR = "tr"
        const val PACK_EN = "en"

        /** Varsayılan aktif blok kategorileri */
        private val DEFAULT_BLOCK_CATEGORIES = setOf(
            "pazarlama", "kredi", "oneriler"
        )

        /** Eski keyword → yeni kategori ID (migrasyon) */
        private val LEGACY_TO_CATEGORY = mapOf(
            "marketing" to "pazarlama", "promo" to "pazarlama", "ads" to "pazarlama",
            "offers" to "pazarlama", "deals" to "pazarlama", "discounts" to "pazarlama",
            "sales" to "pazarlama", "campaigns" to "pazarlama", "newsletters" to "pazarlama",
            "announcements" to "pazarlama", "recommendations" to "oneriler", "suggestions" to "oneriler",
            "news" to "haber", "featured" to "genel", "misc" to "genel", "general" to "genel",
            "indirim" to "pazarlama", "discount" to "pazarlama", "kampanya" to "pazarlama",
            "campaign" to "pazarlama", "bedava" to "pazarlama", "free" to "pazarlama",
            "kazan" to "pazarlama", "win" to "pazarlama", "hediye" to "pazarlama",
            "gift" to "pazarlama", "firsat" to "pazarlama", "deal" to "pazarlama",
            "promosyon" to "pazarlama", "reklam" to "pazarlama",
            "offer" to "pazarlama", "teklif" to "pazarlama", "son_dakika" to "pazarlama",
            "urgency" to "pazarlama", "kredi" to "kredi", "loan" to "kredi", "hemen" to "pazarlama",
            "now" to "pazarlama", "ozel" to "pazarlama", "special" to "pazarlama",
            "kaçırma" to "pazarlama", "miss" to "pazarlama", "sınırlı" to "pazarlama",
            "limited" to "pazarlama"
        )

        /** Kanal allow (EN + kritik TR) */
        private val DEFAULT_CHANNEL_ALLOW = setOf(
            "order", "orders", "delivery", "shipping", "bank", "otp", "transaction",
            "payment", "messages", "chat", "calls", "security", "alerts", "critical",
            "siparis", "sipariş", "işlem", "teslimat"
        )

        /**
         * Birleşik blok kategorileri.
         * Her kategori: kanal ID (bildirim kanalı) + içerik (metin) kelimelerini kapsar.
         * Google Play kategorileri ve alt kategorilere göre gruplandırılmıştır.
         */
        val TR_BLOCK_CATEGORIES = listOf(
            BlockCategory(
                id = "pazarlama",
                label = "Pazarlama İletişimleri",
                channelKeywords = listOf(
                    "pazarlama", "kampanya", "kampanyalar", "tanitim", "tanıtım",
                    "bülten", "duyuru", "duyurular", "kampanya", "kampanyalar"
                ),
                contentKeywords = listOf(
                    "kampanya", "kampanyalar", "pazarlama", "duyuru"
                )
            ),
            BlockCategory(
                id = "pazarlama",
                label = "Reklam ve Tanıtım",
                channelKeywords = listOf("reklam", "reklamlar", "ilan", "ilanlar"),
                contentKeywords = listOf("reklam", "reklamlar")
            ),
            BlockCategory(
                id = "pazarlama",
                label = "İndirim ve Kampanya",
                channelKeywords = listOf(
                    "teklif", "fırsat", "firsat", "indirim", "indirimler", "kampanya",
                    "fırsatlar", "kampanyalar"
                ),
                contentKeywords = listOf(
                    "indirim", "indirimler", "fırsat", "fırsatlar", "firsat",
                    "teklif", "teklifler"
                )
            ),
            BlockCategory(
                id = "pazarlama",
                label = "Promosyon İletişimleri",
                channelKeywords = listOf("promosyon", "promo"),
                contentKeywords = listOf("promosyon", "promo")
            ),
            BlockCategory(
                id = "pazarlama",
                label = "Hediye ve Ücretsiz Teklif",
                channelKeywords = emptyList(),
                contentKeywords = listOf("bedava", "ücretsiz", "kazan", "kazanın", "hediye", "hediyeler")
            ),
            BlockCategory(
                id = "pazarlama",
                label = "Özel ve Sınırlı Teklif",
                channelKeywords = listOf("öne çıkan", "özel", "sınırlı"),
                contentKeywords = listOf("özel", "kaçırma", "sınırlı", "kaçırma")
            ),
            BlockCategory(
                id = "pazarlama",
                label = "Aciliyet Uyarıları",
                channelKeywords = emptyList(),
                contentKeywords = listOf("son dakika", "acele", "hemen")
            ),
            BlockCategory(
                id = "kredi",
                label = "Kredi ve Finans",
                channelKeywords = emptyList(),
                contentKeywords = listOf("kredi")
            ),
            BlockCategory(
                id = "oneriler",
                label = "Öneri ve Tavsiye",
                channelKeywords = listOf("öneri", "öneriler"),
                contentKeywords = emptyList()
            ),
            BlockCategory(
                id = "oyun",
                label = "Oyun Bildirimleri",
                channelKeywords = listOf("oyun", "oyunlar"),
                contentKeywords = listOf("oyun", "oyna", "can", "hayat", "geri gel")
            ),
            BlockCategory(
                id = "haber",
                label = "Haber Bildirimleri",
                channelKeywords = listOf("haber", "haberler"),
                contentKeywords = listOf("son dakika", "haber")
            ),
            BlockCategory(
                id = "genel",
                label = "Genel Bildirimler",
                channelKeywords = listOf("genel", "diger", "diğer"),
                contentKeywords = emptyList()
            )
        )

        val EN_BLOCK_CATEGORIES = listOf(
            BlockCategory(
                id = "pazarlama",
                label = "Pazarlama İletişimleri",
                channelKeywords = listOf(
                    "marketing", "newsletter", "newsletters", "announcement", "announcements", "campaign", "campaigns"
                ),
                contentKeywords = listOf(
                    "campaign", "campaigns", "marketing", "announcement", "announcements"
                )
            ),
            BlockCategory(
                id = "pazarlama",
                label = "Reklam ve Tanıtım",
                channelKeywords = listOf("ads", "advertisement"),
                contentKeywords = listOf("ads", "advertisement")
            ),
            BlockCategory(
                id = "pazarlama",
                label = "İndirim ve Kampanya",
                channelKeywords = listOf(
                    "offers", "offer", "deals", "deal", "discounts", "discount", "sales", "sale"
                ),
                contentKeywords = listOf(
                    "offer", "offers", "deal", "deals", "discount", "discounts", "sale", "sales", "opportunity"
                )
            ),
            BlockCategory(
                id = "pazarlama",
                label = "Promosyon İletişimleri",
                channelKeywords = listOf("promo", "promotion", "promotions"),
                contentKeywords = listOf("promo", "promotion", "promotions")
            ),
            BlockCategory(
                id = "pazarlama",
                label = "Hediye ve Ücretsiz Teklif",
                channelKeywords = emptyList(),
                contentKeywords = listOf("gratis", "free", "win", "wins", "gift", "gifts", "free gift")
            ),
            BlockCategory(
                id = "pazarlama",
                label = "Özel ve Sınırlı Teklif",
                channelKeywords = listOf("featured", "special", "limited"),
                contentKeywords = listOf("special", "limited", "miss out", "don't miss")
            ),
            BlockCategory(
                id = "pazarlama",
                label = "Aciliyet Uyarıları",
                channelKeywords = emptyList(),
                contentKeywords = listOf("last minute", "hurry", "now", "immediately")
            ),
            BlockCategory(
                id = "kredi",
                label = "Kredi ve Finans",
                channelKeywords = emptyList(),
                contentKeywords = listOf("credit", "loan", "loans")
            ),
            BlockCategory(
                id = "oneriler",
                label = "Öneri ve Tavsiye",
                channelKeywords = listOf("recommendation", "recommendations", "suggestion", "suggestions"),
                contentKeywords = emptyList()
            ),
            BlockCategory(
                id = "oyun",
                label = "Oyun Bildirimleri",
                channelKeywords = listOf("game", "games", "gaming", "play", "default_game"),
                contentKeywords = listOf("game", "play", "energy", "come back", "free gift")
            ),
            BlockCategory(
                id = "haber",
                label = "Haber Bildirimleri",
                channelKeywords = listOf("news"),
                contentKeywords = listOf("last minute", "news")
            ),
            BlockCategory(
                id = "genel",
                label = "Genel Bildirimler",
                channelKeywords = listOf("misc", "miscellaneous", "general"),
                contentKeywords = emptyList()
            )
        )
    }

    data class BlockCategory(
        val id: String,
        val label: String,
        val channelKeywords: List<String>,
        val contentKeywords: List<String>
    )
}
