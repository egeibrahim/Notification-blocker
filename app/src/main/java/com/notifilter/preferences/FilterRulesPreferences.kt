package com.notifilter.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.notifilter.R
import com.notifilter.engine.FilterRulesConfig
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Sadeleştirilmiş blok kuralları.
 * Her blok kategorisi hem kanal ID hem içerik kelimelerini kapsar (Google Play kategorileri mantığı).
 * - channel_allow: Kanal ID'de geçerse bypass
 */
data class CustomWordCategory(
    val id: String,
    val title: String,
    val words: Set<String>
)

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
            return setOf(PACK_EN)
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
        val nativePack = localeToPack(java.util.Locale.getDefault().language)
        if (packId == PACK_EN || packId == nativePack) return
        val current = enabledLanguagePacks
        val next = if (packId in current) current - packId else current + packId
        if (next.isNotEmpty()) enabledLanguagePacks = next
    }

    fun setSingleLanguagePack(packId: String) {
        enabledLanguagePacks = setOf(packId)
    }

    /**
     * Onboarding'de kullanıcı dil paketlerini seçtiğinde çağrılır.
     * EN her zaman dahildir. Sistem diline göre otomatik öneri verilir.
     */
    fun applyLanguagePackSelection(selectedPacks: Set<String>) {
        val nativePack = localeToPack(java.util.Locale.getDefault().language)
        val locked = mutableSetOf(PACK_EN)
        if (nativePack != PACK_EN) locked.add(nativePack)
        val withLocked = (selectedPacks + locked).filter { it in ALL_PACKS }.toSet()
        enabledLanguagePacks = withLocked
        hasSelectedCountry = true
    }

    /**
     * Sistem dilini algılar, uygun dil paketini + EN döndürür.
     * Onboarding'de varsayılan seçim olarak kullanılır.
     */
    fun getDefaultLanguagePacks(): Set<String> {
        val locale = java.util.Locale.getDefault().language
        val pack = localeToPack(locale)
        return if (pack == PACK_EN) setOf(PACK_EN) else setOf(pack, PACK_EN)
    }

    /**
     * Eski applyCountrySelection — geri uyumluluk için korundu.
     */
    fun applyCountrySelection(isTurkey: Boolean) {
        enabledLanguagePacks = if (isTurkey) setOf(PACK_TR, PACK_EN) else setOf(PACK_EN)
        hasSelectedCountry = true
    }

    var hasSelectedCountry: Boolean
        get() = prefs.getBoolean(KEY_HAS_SELECTED_COUNTRY, false)
        set(value) = prefs.edit(commit = true) {
            putBoolean(KEY_HAS_SELECTED_COUNTRY, value)
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
        get() = prefs.getBoolean(KEY_GLOBAL_EMOJI_BLOCK_ENABLED, true)
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
            return getActiveBlockCategories().map { it.id }.toSet()
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

    /** Kullanıcının kendi başlığıyla oluşturduğu içerik blok kategorileri */
    fun getUserContentBlockCategories(): List<CustomWordCategory> {
        val loaded = loadCustomCategories(KEY_USER_CONTENT_BLOCK_CATEGORIES)
        if (loaded.isEmpty()) {
            val legacy = getStringSetCompat(KEY_USER_CONTENT_BLOCK_WORDS)
            if (legacy.isNotEmpty()) {
                val migrated = listOf(
                    CustomWordCategory(
                        id = "migrated_block",
                        title = context.getString(R.string.block_section_block_words_title),
                        words = legacy
                    )
                )
                saveCustomCategories(KEY_USER_CONTENT_BLOCK_CATEGORIES, migrated)
                prefs.edit(commit = true) { remove(KEY_USER_CONTENT_BLOCK_WORDS) }
                return migrated
            }
        }
        return loaded
    }

    fun addUserContentBlockCategory(title: String): String {
        val t = title.trim()
        if (t.isBlank()) return ""
        val id = "user_block_${System.currentTimeMillis()}"
        val list = getUserContentBlockCategories() + CustomWordCategory(id, t, emptySet())
        saveCustomCategories(KEY_USER_CONTENT_BLOCK_CATEGORIES, list)
        return id
    }

    fun removeUserContentBlockCategory(id: String) {
        val list = getUserContentBlockCategories().filter { it.id != id }
        saveCustomCategories(KEY_USER_CONTENT_BLOCK_CATEGORIES, list)
    }

    fun addUserContentBlockCategoryWord(id: String, word: String) {
        val w = word.trim().lowercase()
        if (w.isBlank()) return
        val list = getUserContentBlockCategories().map { cat ->
            if (cat.id == id) cat.copy(words = cat.words + w) else cat
        }
        saveCustomCategories(KEY_USER_CONTENT_BLOCK_CATEGORIES, list)
    }

    fun removeUserContentBlockCategoryWord(id: String, word: String) {
        val w = word.trim().lowercase()
        if (w.isBlank()) return
        val list = getUserContentBlockCategories().map { cat ->
            if (cat.id == id) cat.copy(words = cat.words - w) else cat
        }
        saveCustomCategories(KEY_USER_CONTENT_BLOCK_CATEGORIES, list)
    }

    fun getAllUserContentBlockWords(): Set<String> {
        return getUserContentBlockCategories().flatMap { it.words }.toSet()
    }

    /** Kullanıcının kendi başlığıyla oluşturduğu izin kelimesi kategorileri */
    fun getUserContentAllowCategories(): List<CustomWordCategory> {
        val loaded = loadCustomCategories(KEY_USER_CONTENT_ALLOW_CATEGORIES)
        if (loaded.isEmpty()) {
            val legacy = getStringSetCompat(KEY_USER_CONTENT_ALLOW_WORDS)
            if (legacy.isNotEmpty()) {
                val migrated = listOf(
                    CustomWordCategory(
                        id = "migrated_allow",
                        title = context.getString(R.string.block_section_allow_words_title),
                        words = legacy
                    )
                )
                saveCustomCategories(KEY_USER_CONTENT_ALLOW_CATEGORIES, migrated)
                prefs.edit(commit = true) { remove(KEY_USER_CONTENT_ALLOW_WORDS) }
                return migrated
            }
        }
        return loaded
    }

    fun addUserContentAllowCategory(title: String): String {
        val t = title.trim()
        if (t.isBlank()) return ""
        val id = "user_allow_${System.currentTimeMillis()}"
        val list = getUserContentAllowCategories() + CustomWordCategory(id, t, emptySet())
        saveCustomCategories(KEY_USER_CONTENT_ALLOW_CATEGORIES, list)
        return id
    }

    fun removeUserContentAllowCategory(id: String) {
        val list = getUserContentAllowCategories().filter { it.id != id }
        saveCustomCategories(KEY_USER_CONTENT_ALLOW_CATEGORIES, list)
    }

    fun addUserContentAllowCategoryWord(id: String, word: String) {
        val w = word.trim().lowercase()
        if (w.isBlank()) return
        val list = getUserContentAllowCategories().map { cat ->
            if (cat.id == id) cat.copy(words = cat.words + w) else cat
        }
        saveCustomCategories(KEY_USER_CONTENT_ALLOW_CATEGORIES, list)
    }

    fun removeUserContentAllowCategoryWord(id: String, word: String) {
        val w = word.trim().lowercase()
        if (w.isBlank()) return
        val list = getUserContentAllowCategories().map { cat ->
            if (cat.id == id) cat.copy(words = cat.words - w) else cat
        }
        saveCustomCategories(KEY_USER_CONTENT_ALLOW_CATEGORIES, list)
    }

    fun getAllUserContentAllowWords(): Set<String> {
        return getUserContentAllowCategories().flatMap { it.words }.toSet()
    }

    private fun loadCustomCategories(key: String): List<CustomWordCategory> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                val id = obj.getString("id")
                val title = obj.getString("title")
                val wordsArr = obj.getJSONArray("words")
                val words = (0 until wordsArr.length()).map { j -> wordsArr.getString(j) }.toSet()
                CustomWordCategory(id, title, words)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveCustomCategories(key: String, categories: List<CustomWordCategory>) {
        val arr = JSONArray()
        categories.forEach { cat ->
            val obj = JSONObject().apply {
                put("id", cat.id)
                put("title", cat.title)
                val wordsArr = JSONArray()
                cat.words.forEach { wordsArr.put(it) }
                put("words", wordsArr)
            }
            arr.put(obj)
        }
        prefs.edit(commit = true) { putString(key, arr.toString()) }
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
        val localePack = localeToPack(java.util.Locale.getDefault().language)
        val orderedPacks = enabledLanguagePacks.sortedByDescending { it == localePack }
        val selected = orderedPacks.flatMap { getCategoriesForPack(it) }
            .ifEmpty { EN_BLOCK_CATEGORIES }
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
        return merged.values.sortedBy { if (it.id == "pazarlama") 0 else 1 }
    }

    fun getMergedBlockCategories(): List<BlockCategory> = getActiveBlockCategories()

    private fun getCategoriesForPack(packId: String): List<BlockCategory> {
        return when (packId) {
            PACK_TR -> TR_BLOCK_CATEGORIES
            PACK_EN -> EN_BLOCK_CATEGORIES
            PACK_ES -> ES_BLOCK_CATEGORIES
            PACK_DE -> DE_BLOCK_CATEGORIES
            PACK_FR -> FR_BLOCK_CATEGORIES
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
        contentAllow = (userContentAllowWords + getAllUserContentAllowWords()).toList(),
        contentBlock = (getEnabledRecommendedContentWords() + userContentBlockWords + getAllUserContentBlockWords() + getUserCategoryWordsForActiveCategories()).toList(),
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
        private const val KEY_USER_CONTENT_BLOCK_CATEGORIES = "filter_user_content_block_categories_json"
        private const val KEY_USER_CONTENT_ALLOW_CATEGORIES = "filter_user_content_allow_categories_json"
        private const val KEY_ENABLED_LANGUAGE_PACKS = "filter_enabled_language_packs"
        private const val KEY_HAS_SELECTED_COUNTRY = "filter_has_selected_country"

        private const val KEY_DISABLED_RECOMMENDED_CONTENT_WORDS = "filter_disabled_recommended_content_words"
        private const val KEY_DISABLED_RECOMMENDED_CHANNEL_WORDS = "filter_disabled_recommended_channel_words"

        private const val KEY_GLOBAL_EMOJI_BLOCK_ENABLED = "filter_global_emoji_block_enabled"
        private const val KEY_GLOBAL_GAMES_BLOCK_ENABLED = "filter_global_games_block_enabled"

        const val PACK_TR = "tr"
        const val PACK_EN = "en"
        const val PACK_ES = "es"
        const val PACK_DE = "de"
        const val PACK_FR = "fr"

        val ALL_PACKS = listOf(PACK_TR, PACK_EN, PACK_ES, PACK_DE, PACK_FR)

        fun packLabel(packId: String): String = when (packId) {
            PACK_TR -> "Türkçe"
            PACK_EN -> "English"
            PACK_ES -> "Español"
            PACK_DE -> "Deutsch"
            PACK_FR -> "Français"
            else -> packId
        }

        fun packFlagEmoji(packId: String): String = when (packId) {
            PACK_TR -> "🇹🇷"
            PACK_EN -> "🇬🇧"
            PACK_ES -> "🇪🇸"
            PACK_DE -> "🇩🇪"
            PACK_FR -> "🇫🇷"
            else -> "🌍"
        }

        fun localeToPack(locale: String): String = when (locale) {
            "tr" -> PACK_TR
            "es" -> PACK_ES
            "de" -> PACK_DE
            "fr" -> PACK_FR
            else -> PACK_EN
        }

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
         * TR ve EN kanal/içerik kelimeleri kullanıcı tarafından açılıp kapatılabilir.
         */
        val TR_BLOCK_CATEGORIES = listOf(
            // Yeni kategoriler
            BlockCategory(
                id = "sosyal_etkilesim",
                label = "Sosyal Medya",
                channelKeywords = listOf("etkileşim", "sosyal"),
                contentKeywords = listOf("beğendi", "yorum yaptı", "seni etiketledi", "takip etmeye başladı", "arkadaşlık isteği", "seni andı", "yeni takipçi", "profilini görüntüledi")
            ),
            BlockCategory(
                id = "yeniden_katilim",
                label = "Geri Kazanım",
                channelKeywords = listOf("re_engagement", "win_back"),
                contentKeywords = listOf("seni özledik", "geri dön", "uzun zamandır görüşmedik", "senin için buradayız", "seni bekliyoruz", "tekrar hoş geldin")
            ),
            BlockCategory(
                id = "degerlendirme_istegi",
                label = "Puanlama İstekleri",
                channelKeywords = listOf("rating", "review_request"),
                contentKeywords = listOf("bizi değerlendir", "uygulamayı puanla", "yorum bırak", "deneyimini paylaş", "5 yıldız ver")
            ),
            BlockCategory(
                id = "anket",
                label = "Anket / Geri Bildirim",
                channelKeywords = listOf("survey", "anket"),
                contentKeywords = listOf("anketimize katıl", "görüşünü bildir", "kısa bir anket", "geri bildirimin bizim için önemli")
            ),
            BlockCategory(
                id = "terk_edilen_sepet",
                label = "Sepet Hatırlatma",
                channelKeywords = listOf("cart_reminder", "sepet"),
                contentKeywords = listOf("sepetinde ürün var", "alışverişini tamamla", "sepetini unutma", "seni sepette bekliyor")
            ),
            BlockCategory(
                id = "fiyat_stok_uyarisi",
                label = "Fiyat / Stok Uyarısı",
                channelKeywords = listOf("price_alert", "stock_alert"),
                contentKeywords = listOf("fiyat düştü", "tekrar stokta", "stoklar tükenmeden", "fiyat uyarısı", "favorilerinde indirim")
            ),
            BlockCategory(
                id = "sadakat_puan",
                label = "Sadakat / Puan",
                channelKeywords = listOf("loyalty", "puan_programi"),
                contentKeywords = listOf("puanların bitiyor", "sadakat puanı kazandın", "puanını kullan", "üyelik avantajların")
            ),
            BlockCategory(
                id = "abonelik_hatirlatma",
                label = "Abonelik Yenileme",
                channelKeywords = listOf("subscription_reminder"),
                contentKeywords = listOf("deneme süren bitiyor", "aboneliğini yenile", "premium'a geç", "ücretsiz deneme sona eriyor")
            ),
            BlockCategory(
                id = "sistem_guncelleme",
                label = "Güncelleme",
                channelKeywords = listOf("app_update", "guncelleme"),
                contentKeywords = listOf("yeni sürüm mevcut", "güncelleme mevcut", "yenilikleri gör", "uygulamanı güncelle")
            ),
            BlockCategory(
                id = "spor_canli",
                label = "Spor / Canlı Skor",
                channelKeywords = listOf("sports", "spor", "canli_skor"),
                contentKeywords = listOf("maç başladı", "gol", "skor güncellemesi", "canlı sonuç", "devre arası")
            ),
            BlockCategory(
                id = "burc_astroloji",
                label = "Burç / Astroloji",
                channelKeywords = listOf("horoscope", "burc"),
                contentKeywords = listOf("günlük burç yorumun", "yıldızların bugün", "burcuna özel")
            ),
            BlockCategory(
                id = "flort_eslesme",
                label = "Flört / Eşleşme",
                channelKeywords = listOf("dating", "match"),
                contentKeywords = listOf("seni beğendi", "yeni eşleşme", "sana mesaj gönderdi", "seninle eşleşti")
            ),
            BlockCategory(
                id = "kariyer_ilan",
                label = "Kariyer / İş İlanı",
                channelKeywords = listOf("job_alert", "kariyer"),
                contentKeywords = listOf("senin için yeni ilanlar", "sana uygun pozisyonlar", "kariyer fırsatı")
            ),
            BlockCategory(
                id = "kripto_borsa",
                label = "Kripto / Borsa",
                channelKeywords = listOf("price_watch", "kripto", "borsa"),
                contentKeywords = listOf("fiyatı yükseldi", "fiyatı düştü", "yüzde değişim", "hisse uyarısı")
            ),
            // Genişletilmiş kategoriler
            BlockCategory(
                id = "pazarlama",
                label = "Pazarlama",
                channelKeywords = emptyList(), // Bu kelimeler pazarlama metni/sloganı, gerçek bildirim kanalı ID'si değil
                contentKeywords = listOf("flaş indirim", "mega indirim", "süper fırsat", "kaçırılmayacak fırsat", "sepette indirim", "üyelere özel", "sadece bugün", "son gün", "tükenmeden", "stoklar tükenmeden", "ekstra indirim", "çifte kampanya", "vip teklif", "sınırlı süre", "sadece sana özel", "kupon", "kupon kodu", "indirim kodu", "promosyon kodu", "hediye çeki", "ücretsiz kargo", "bugüne özel", "yılın en büyük indirimi", "sezon sonu", "yaz indirimi", "kış indirimi")
            ),
            BlockCategory(
                id = "kredi",
                label = "Kredi ve Finans",
                channelKeywords = listOf("kredi_teklifi", "finans"),
                contentKeywords = listOf("kredi kartı", "ihtiyaç kredisi", "taksit", "faiz", "faizsiz", "kredi limiti", "borç yapılandırma", "kredi başvurusu", "kredi teklifi", "hızlı kredi", "anında kredi", "onaylı kredi", "kredi notu", "nakit avans", "ek limit")
            ),
            BlockCategory(
                id = "oneriler",
                label = "Öneriler",
                channelKeywords = listOf("senin_icin", "kesfet"),
                contentKeywords = listOf("tavsiye", "tavsiyeler", "senin için", "sana özel öneriler", "ilgini çekebilir", "beğenebilirsin", "keşfet", "senin için seçtik", "favorilerine göre", "geçmişine göre")
            ),
            BlockCategory(
                id = "oyun",
                label = "Oyunlar",
                channelKeywords = listOf("gunluk_odul", "etkinlik"),
                contentKeywords = listOf("günlük ödül", "günlük giriş ödülü", "seviye atla", "yeni bölüm", "etkinlik başladı", "sınırlı süreli etkinlik", "arkadaşların oynuyor", "turnuva", "ödül kazan", "günlük görev", "ücretsiz can", "ücretsiz jeton", "ücretsiz elmas", "enerji doldu")
            ),
            BlockCategory(
                id = "haber",
                label = "Haberler",
                channelKeywords = listOf("flas_haber", "gundem"),
                contentKeywords = listOf("gündem", "flaş haber", "canlı haber", "güncel", "haber özeti", "günün haberleri", "çok okunanlar", "editörün seçtikleri")
            ),
            BlockCategory(
                id = "genel",
                label = "Genel",
                channelKeywords = listOf("bildirim", "hatirlatma"),
                contentKeywords = listOf("hatırlatma", "sistem bildirimi", "uygulama bildirimi", "bilgilendirme")
            )
        )

        val ES_BLOCK_CATEGORIES = listOf(
            BlockCategory(
                id = "sosyal_etkilesim",
                label = "Redes Sociales",
                channelKeywords = listOf("social", "interaccion"),
                contentKeywords = listOf("le gustó tu", "comentó en", "te etiquetó", "empezó a seguirte", "solicitud de amistad", "te mencionó", "nuevo seguidor", "vio tu perfil")
            ),
            BlockCategory(
                id = "yeniden_katilim",
                label = "Recuperación",
                channelKeywords = listOf("re_engagement", "win_back"),
                contentKeywords = listOf("te extrañamos", "vuelve", "hace tiempo que no te vemos", "estamos aquí para ti", "bienvenido de nuevo")
            ),
            BlockCategory(
                id = "degerlendirme_istegi",
                label = "Pide Reseñas",
                channelKeywords = listOf("rating", "review_request"),
                contentKeywords = listOf("califícanos", "califica esta app", "deja una reseña", "comparte tu experiencia", "danos 5 estrellas")
            ),
            BlockCategory(
                id = "anket",
                label = "Encuestas / Feedback",
                channelKeywords = listOf("survey", "encuesta"),
                contentKeywords = listOf("participa en nuestra encuesta", "dinos qué piensas", "encuesta rápida", "tu opinión importa")
            ),
            BlockCategory(
                id = "terk_edilen_sepet",
                label = "Carrito Abandonado",
                channelKeywords = listOf("cart_reminder", "carrito"),
                contentKeywords = listOf("artículos en tu carrito", "completa tu compra", "no olvides tu carrito", "te espera en tu carrito")
            ),
            BlockCategory(
                id = "fiyat_stok_uyarisi",
                label = "Alertas de Precio / Stock",
                channelKeywords = listOf("price_alert", "stock_alert"),
                contentKeywords = listOf("bajó el precio", "de nuevo en stock", "antes de que se agote", "alerta de precio", "rebaja en tus favoritos")
            ),
            BlockCategory(
                id = "sadakat_puan",
                label = "Lealtad / Puntos",
                channelKeywords = listOf("loyalty", "puntos"),
                contentKeywords = listOf("tus puntos expiran", "ganaste puntos", "canjea tus puntos", "beneficios de miembro")
            ),
            BlockCategory(
                id = "abonelik_hatirlatma",
                label = "Renovación de Suscripción",
                channelKeywords = listOf("subscription_reminder"),
                contentKeywords = listOf("tu prueba termina", "renueva tu suscripción", "actualiza a premium", "la prueba gratis termina pronto")
            ),
            BlockCategory(
                id = "sistem_guncelleme",
                label = "Actualizaciones",
                channelKeywords = listOf("app_update", "actualizacion"),
                contentKeywords = listOf("nueva versión disponible", "actualización disponible", "ver novedades", "actualiza tu app")
            ),
            BlockCategory(
                id = "spor_canli",
                label = "Deportes / Resultados",
                channelKeywords = listOf("sports", "deportes", "resultado"),
                contentKeywords = listOf("el partido empezó", "gol", "actualización de marcador", "resultado en vivo", "medio tiempo")
            ),
            BlockCategory(
                id = "burc_astroloji",
                label = "Horóscopo / Astrología",
                channelKeywords = listOf("horoscope", "horoscopo"),
                contentKeywords = listOf("tu horóscopo de hoy", "las estrellas hoy", "para tu signo")
            ),
            BlockCategory(
                id = "flort_eslesme",
                label = "Citas / Matches",
                channelKeywords = listOf("dating", "match"),
                contentKeywords = listOf("le gustaste", "nuevo match", "te envió un mensaje", "hiciste match contigo")
            ),
            BlockCategory(
                id = "kariyer_ilan",
                label = "Alertas de Empleo",
                channelKeywords = listOf("job_alert", "empleo"),
                contentKeywords = listOf("nuevas ofertas para ti", "posiciones que coinciden con tu perfil", "oportunidad laboral")
            ),
            BlockCategory(
                id = "kripto_borsa",
                label = "Cripto / Bolsa",
                channelKeywords = listOf("price_watch", "cripto", "bolsa"),
                contentKeywords = listOf("el precio subió", "el precio bajó", "cambio porcentual", "alerta de acciones")
            ),
            BlockCategory(
                id = "pazarlama",
                label = "Marketing",
                channelKeywords = emptyList(),
                contentKeywords = listOf("oferta flash", "mega oferta", "súper oferta", "oferta exclusiva", "solo para miembros", "solo hoy", "último día", "antes de que se agote", "descuento extra", "doble descuento", "oferta vip", "solo para ti", "cupón", "código de cupón", "código promocional", "código de descuento", "tarjeta de regalo", "envío gratis", "la mayor oferta del año", "liquidación", "rebajas de verano", "rebajas de invierno", "fin de temporada", "última oportunidad", "apúrate")
            ),
            BlockCategory(
                id = "kredi",
                label = "Crédito y Finanzas",
                channelKeywords = listOf("loan_offer", "finance", "finanzas"),
                contentKeywords = listOf("tarjeta de crédito", "préstamo personal", "cuotas", "interés", "sin intereses", "límite de crédito", "reestructuración de deuda", "solicitud de préstamo", "oferta de préstamo", "préstamo rápido", "préstamo instantáneo", "préstamo preaprobado", "score crediticio", "avance en efectivo")
            ),
            BlockCategory(
                id = "oneriler",
                label = "Recomendaciones",
                channelKeywords = listOf("for_you", "descubre", "para_ti"),
                contentKeywords = listOf("para ti", "seleccionado para ti", "te puede gustar", "descubre", "según tu historial", "según tus favoritos", "curado para ti")
            ),
            BlockCategory(
                id = "oyun",
                label = "Juegos",
                channelKeywords = listOf("daily_reward", "event", "evento"),
                contentKeywords = listOf("recompensa diaria", "bono de inicio de sesión", "sube de nivel", "nueva etapa", "evento empezó", "evento por tiempo limitado", "tus amigos están jugando", "tabla de clasificación", "torneo", "gana un premio", "misión diaria", "vidas gratis", "monedas gratis", "gemas gratis", "energía llena")
            ),
            BlockCategory(
                id = "haber",
                label = "Noticias",
                channelKeywords = listOf("breaking_news", "titulares"),
                contentKeywords = listOf("última hora", "titulares", "noticias en vivo", "actualización", "resumen", "resumen de noticias", "noticias de hoy", "lo más leído", "selección del editor")
            ),
            BlockCategory(
                id = "genel",
                label = "General",
                channelKeywords = listOf("notification", "reminder", "recordatorio"),
                contentKeywords = listOf("recordatorio", "notificación del sistema", "notificación de la app", "info")
            ),
            BlockCategory(
                id = "global_shopping_events",
                label = "Ofertas Especiales",
                channelKeywords = listOf("shopping_event", "seasonal_sale"),
                contentKeywords = listOf("black friday", "cyber monday", "prime day", "compra uno lleva dos", "oferta relámpago", "rebajas de invierno", "volver a la escuela")
            ),
            BlockCategory(
                id = "global_food_delivery",
                label = "Delivery de Comida",
                channelKeywords = listOf("food_delivery", "restaurant_promo"),
                contentKeywords = listOf("envío gratis", "descuento en tu primer pedido", "descuento en tu próximo pedido", "gastos de envío gratis", "pide ahora y ahorra", "ofertas de restaurantes cerca")
            ),
            BlockCategory(
                id = "global_rideshare",
                label = "Viajes / Transporte",
                channelKeywords = listOf("ride_promo", "rideshare"),
                contentKeywords = listOf("crédito de viaje", "código promocional para tu próximo viaje", "descuento en tu próximo viaje", "viaje gratis", "reserva tu viaje y ahorra")
            ),
            BlockCategory(
                id = "global_real_estate",
                label = "Inmuebles",
                channelKeywords = listOf("listing_alert", "real_estate"),
                contentKeywords = listOf("nueva propiedad coincide con tu búsqueda", "precio reducido en una casa guardada", "nuevas casas en venta cerca", "puertas abiertas este fin de semana")
            ),
            BlockCategory(
                id = "global_insurance",
                label = "Seguros",
                channelKeywords = listOf("insurance_offer"),
                contentKeywords = listOf("cotización gratis", "combina y ahorra", "reduce tu prima", "compara tarifas de seguro", "podrías ahorrar en tu póliza")
            ),
            BlockCategory(
                id = "global_streaming",
                label = "Streaming",
                channelKeywords = listOf("streaming_promo"),
                contentKeywords = listOf("nueva temporada disponible", "prueba gratis hoy", "ahora en streaming", "añadido a tu lista", "maratón de la nueva temporada")
            ),
            BlockCategory(
                id = "global_local_deals",
                label = "Ofertas Locales",
                channelKeywords = listOf("local_deal", "daily_deal"),
                contentKeywords = listOf("oferta de hoy cerca", "oferta del día", "hasta 50% de descuento cerca", "ofertas locales solo para ti")
            ),
            BlockCategory(
                id = "global_travel_alerts",
                label = "Viajes / Vuelos",
                channelKeywords = listOf("fare_alert", "travel_deal"),
                contentKeywords = listOf("precios de vuelos bajaron", "alerta de tarifa", "bajó el precio de tu viaje guardado", "vuelos baratos a", "reserva ahora y ahorra en vuelos")
            )
        )

        val DE_BLOCK_CATEGORIES = listOf(
            BlockCategory(
                id = "sosyal_etkilesim",
                label = "Soziale Medien",
                channelKeywords = listOf("social", "interaktion"),
                contentKeywords = listOf("hat deinen Beitrag geliked", "hat kommentiert", "hat dich markiert", "folgt dir jetzt", "Freundschaftsanfrage", "hat dich erwähnt", "neuer Follower", "hat dein Profil angesehen")
            ),
            BlockCategory(
                id = "yeniden_katilim",
                label = "Rückgewinnung",
                channelKeywords = listOf("re_engagement", "win_back"),
                contentKeywords = listOf("wir vermissen dich", "komm zurück", "lange nicht gesehen", "wir sind für dich da", "willkommen zurück")
            ),
            BlockCategory(
                id = "degerlendirme_istegi",
                label = "Bewertungsanfragen",
                channelKeywords = listOf("rating", "review_request"),
                contentKeywords = listOf("bewerte uns", "bewerte diese App", "hinterlasse eine Bewertung", "teile deine Erfahrung", "gib uns 5 Sterne")
            ),
            BlockCategory(
                id = "anket",
                label = "Umfragen / Feedback",
                channelKeywords = listOf("survey", "umfrage"),
                contentKeywords = listOf("nimm an unserer Umfrage teil", "sag uns deine Meinung", "kurze Umfrage", "dein Feedback ist wichtig")
            ),
            BlockCategory(
                id = "terk_edilen_sepet",
                label = "Warenkorb-Erinnerung",
                channelKeywords = listOf("cart_reminder", "warenkorb"),
                contentKeywords = listOf("Artikel in deinem Warenkorb", "schließe deinen Kauf ab", "vergiss deinen Warenkorb nicht", "wartet noch in deinem Warenkorb")
            ),
            BlockCategory(
                id = "fiyat_stok_uyarisi",
                label = "Preis-/Bestandsalarme",
                channelKeywords = listOf("price_alert", "stock_alert"),
                contentKeywords = listOf("Preis gesenkt", "wieder auf Lager", "bevor es ausverkauft ist", "Preisalarm", "Angebot für deine Favoriten")
            ),
            BlockCategory(
                id = "sadakat_puan",
                label = "Treue / Punkte",
                channelKeywords = listOf("loyalty", "punkte"),
                contentKeywords = listOf("deine Punkte laufen ab", "du hast Punkte verdient", "löse deine Punkte ein", "Vorteile für Mitglieder")
            ),
            BlockCategory(
                id = "abonelik_hatirlatma",
                label = "Abo-Erneuerung",
                channelKeywords = listOf("subscription_reminder"),
                contentKeywords = listOf("deine Testphase endet", "erneuere dein Abonnement", "upgrade auf Premium", "kostenlose Testphase endet bald")
            ),
            BlockCategory(
                id = "sistem_guncelleme",
                label = "Updates",
                channelKeywords = listOf("app_update", "update"),
                contentKeywords = listOf("neue Version verfügbar", "Update verfügbar", "sieh was neu ist", "aktualisiere deine App")
            ),
            BlockCategory(
                id = "spor_canli",
                label = "Sport / Live-Ergebnisse",
                channelKeywords = listOf("sports", "sport", "ergebnis"),
                contentKeywords = listOf("Spiel gestartet", "Tor", "Stand aktualisiert", "Live-Ergebnis", "Halbzeit")
            ),
            BlockCategory(
                id = "burc_astroloji",
                label = "Horoskop / Astrologie",
                channelKeywords = listOf("horoscope", "horoskop"),
                contentKeywords = listOf("dein Horoskop heute", "die Sterne heute", "für dein Sternzeichen")
            ),
            BlockCategory(
                id = "flort_eslesme",
                label = "Dating / Matches",
                channelKeywords = listOf("dating", "match"),
                contentKeywords = listOf("hat dich geliked", "neuer Match", "hat dir eine Nachricht gesendet", "Match mit dir")
            ),
            BlockCategory(
                id = "kariyer_ilan",
                label = "Job-Benachrichtigungen",
                channelKeywords = listOf("job_alert", "jobs"),
                contentKeywords = listOf("neue Jobs für dich", "Positionen die zu deinem Profil passen", "Karrieremöglichkeit")
            ),
            BlockCategory(
                id = "kripto_borsa",
                label = "Krypto / Aktien",
                channelKeywords = listOf("price_watch", "krypto", "aktien"),
                contentKeywords = listOf("Preis steigt", "Preis fällt", "Prozentuale Änderung", "Aktienalarm")
            ),
            BlockCategory(
                id = "pazarlama",
                label = "Marketing",
                channelKeywords = emptyList(),
                contentKeywords = listOf("Blitzangebot", "Mega-Angebot", "Super-Angebot", "exklusives Angebot", "nur für Mitglieder", "nur heute", "letzter Tag", "solange Vorrat reicht", "zusätzlicher Rabatt", "Doppelrabatt", "VIP-Angebot", "nur für dich", "Gutschein", "Gutscheincode", "Promo-Code", "Rabattcode", "Geschenkkarte", "kostenloser Versand", "größter Sale des Jahres", "Ausverkauf", "Sommerschlussverkauf", "Winterschlussverkauf", "Saisonsende", "letzte Chance", "beeil dich")
            ),
            BlockCategory(
                id = "kredi",
                label = "Kredit & Finanzen",
                channelKeywords = listOf("loan_offer", "finance", "finanzen"),
                contentKeywords = listOf("Kreditkarte", "Privatkredit", "Raten", "Zinsen", "zinsfrei", "Kreditlimit", "Schuldenerstrukturierung", "Kreditantrag", "Kreditangebot", "Schnellkredit", "Sofortkredit", "vorab genehmigter Kredit", "Bonität", "Bargeldvorschuss")
            ),
            BlockCategory(
                id = "oneriler",
                label = "Empfehlungen",
                channelKeywords = listOf("for_you", "entdecken", "fuer_dich"),
                contentKeywords = listOf("für dich", "für dich ausgewählt", "könnte dir gefallen", "entdecke", "basierend auf deinem Verlauf", "basierend auf deinen Favoriten", "für dich kuratiert")
            ),
            BlockCategory(
                id = "oyun",
                label = "Spiele",
                channelKeywords = listOf("daily_reward", "event", "ereignis"),
                contentKeywords = listOf("tägliche Belohnung", "Tageslogin-Bonus", "Level aufsteigen", "neue Stufe", "Event gestartet", "zeitlich begrenztes Event", "deine Freunde spielen", "Bestenliste", "Turnier", "gewinne einen Preis", "tägliche Quest", "kostenlose Leben", "kostenlose Münzen", "kostenlose Edelsteine", "Energie voll")
            ),
            BlockCategory(
                id = "haber",
                label = "Nachrichten",
                channelKeywords = listOf("breaking_news", "schlagzeilen"),
                contentKeywords = listOf("Eilmeldung", "Schlagzeilen", "Live-Nachrichten", "Aktualisierung", "Zusammenfassung", "Nachrichtenüberblick", "Nachrichten von heute", "meistgelesen", "Empfehlungen der Redaktion")
            ),
            BlockCategory(
                id = "genel",
                label = "Allgemein",
                channelKeywords = listOf("notification", "reminder", "erinnerung"),
                contentKeywords = listOf("Erinnerung", "Systembenachrichtigung", "App-Benachrichtigung", "Info")
            ),
            BlockCategory(
                id = "global_shopping_events",
                label = "Shopping-Events",
                channelKeywords = listOf("shopping_event", "seasonal_sale"),
                contentKeywords = listOf("Black Friday", "Cyber Monday", "Prime Day", "kauf eins bekomm eins", "Blitzangebot", "Winterschlussverkauf", "Schulstart-Angebot")
            ),
            BlockCategory(
                id = "global_food_delivery",
                label = "Essenslieferung",
                channelKeywords = listOf("food_delivery", "restaurant_promo"),
                contentKeywords = listOf("kostenlose Lieferung", "Rabatt auf deine erste Bestellung", "Rabatt auf deine nächste Bestellung", "Liefergebühren erlassen", "bestelle jetzt und spare", "Restaurant-Angebote in deiner Nähe")
            ),
            BlockCategory(
                id = "global_rideshare",
                label = "Fahrgemeinschaft",
                channelKeywords = listOf("ride_promo", "rideshare"),
                contentKeywords = listOf("Fahrtguthaben", "Promo-Code für deine nächste Fahrt", "Rabatt auf deine nächste Fahrt", "kostenlose Fahrt", "buche deine Fahrt und spare")
            ),
            BlockCategory(
                id = "global_real_estate",
                label = "Immobilien",
                channelKeywords = listOf("listing_alert", "real_estate"),
                contentKeywords = listOf("neues Angebot passt zu deiner Suche", "Preis reduziert für ein gespeichertes Haus", "neue Häuser zum Verkauf in der Nähe", "Open House dieses Wochenende")
            ),
            BlockCategory(
                id = "global_insurance",
                label = "Versicherungen",
                channelKeywords = listOf("insurance_offer"),
                contentKeywords = listOf("kostenloses Angebot", "bündeln und sparen", "senke deine Prämie", "vergleiche Versicherungstarife", "du könntest bei deiner Police sparen")
            ),
            BlockCategory(
                id = "global_streaming",
                label = "Streaming",
                channelKeywords = listOf("streaming_promo"),
                contentKeywords = listOf("neue Staffel verfügbar", "kostenlose Testphase startet heute", "jetzt streamen", "zu deiner Watchlist hinzugefügt", "Binge die neue Staffel")
            ),
            BlockCategory(
                id = "global_local_deals",
                label = "Lokale Angebote",
                channelKeywords = listOf("local_deal", "daily_deal"),
                contentKeywords = listOf("Angebot des Tages in deiner Nähe", "Angebot des Tages", "bis zu 50% Rabatt in der Nähe", "lokale Angebote nur für dich")
            ),
            BlockCategory(
                id = "global_travel_alerts",
                label = "Reisen / Flüge",
                channelKeywords = listOf("fare_alert", "travel_deal"),
                contentKeywords = listOf("Flugpreise gesunken", "Tarifalarm", "Preissturz für deine gespeicherte Reise", "günstige Flüge nach", "buche jetzt und spare bei Flügen")
            )
        )

        val FR_BLOCK_CATEGORIES = listOf(
            BlockCategory(
                id = "sosyal_etkilesim",
                label = "Réseaux Sociaux",
                channelKeywords = listOf("social", "interaction"),
                contentKeywords = listOf("a aimé votre", "a commenté", "vous a tagué", "a commencé à vous suivre", "demande d'ami", "vous a mentionné", "nouvel abonné", "a vu votre profil")
            ),
            BlockCategory(
                id = "yeniden_katilim",
                label = "Rétention",
                channelKeywords = listOf("re_engagement", "win_back"),
                contentKeywords = listOf("vous nous manquez", "revenez", "ça fait longtemps", "nous sommes là pour vous", "bon retour")
            ),
            BlockCategory(
                id = "degerlendirme_istegi",
                label = "Demandes d'Avis",
                channelKeywords = listOf("rating", "review_request"),
                contentKeywords = listOf("notez-nous", "notez cette app", "laissez un avis", "partagez votre expérience", "donnez-nous 5 étoiles")
            ),
            BlockCategory(
                id = "anket",
                label = "Sondages / Feedback",
                channelKeywords = listOf("survey", "sondage"),
                contentKeywords = listOf("participez à notre sondage", "dites-nous ce que vous pensez", "sondage rapide", "votre avis compte")
            ),
            BlockCategory(
                id = "terk_edilen_sepet",
                label = "Panier Abandonné",
                channelKeywords = listOf("cart_reminder", "panier"),
                contentKeywords = listOf("articles dans votre panier", "finalisez votre achat", "n'oubliez pas votre panier", "vous attend dans votre panier")
            ),
            BlockCategory(
                id = "fiyat_stok_uyarisi",
                label = "Alertes Prix / Stock",
                channelKeywords = listOf("price_alert", "stock_alert"),
                contentKeywords = listOf("baisse de prix", "de nouveau en stock", "avant épuisement", "alerte de prix", "promo sur vos favoris")
            ),
            BlockCategory(
                id = "sadakat_puan",
                label = "Fidélité / Points",
                channelKeywords = listOf("loyalty", "points"),
                contentKeywords = listOf("vos points expirent", "vous avez gagné des points", "échangez vos points", "avantages membres")
            ),
            BlockCategory(
                id = "abonelik_hatirlatma",
                label = "Renouvellement d'Abonnement",
                channelKeywords = listOf("subscription_reminder"),
                contentKeywords = listOf("votre essai se termine", "renouvelez votre abonnement", "passez à premium", "l'essai gratuit se termine bientôt")
            ),
            BlockCategory(
                id = "sistem_guncelleme",
                label = "Mises à Jour",
                channelKeywords = listOf("app_update", "mise_a_jour"),
                contentKeywords = listOf("nouvelle version disponible", "mise à jour disponible", "voir les nouveautés", "mettez à jour votre app")
            ),
            BlockCategory(
                id = "spor_canli",
                label = "Sport / Scores en Direct",
                channelKeywords = listOf("sports", "sport", "score"),
                contentKeywords = listOf("le match a commencé", "but", "mise à jour du score", "résultat en direct", "mi-temps")
            ),
            BlockCategory(
                id = "burc_astroloji",
                label = "Horoscope / Astrologie",
                channelKeywords = listOf("horoscope"),
                contentKeywords = listOf("votre horoscope du jour", "les étoiles aujourd'hui", "pour votre signe")
            ),
            BlockCategory(
                id = "flort_eslesme",
                label = "Rencontres / Matches",
                channelKeywords = listOf("dating", "match"),
                contentKeywords = listOf("vous a liké", "nouveau match", "vous a envoyé un message", "match avec vous")
            ),
            BlockCategory(
                id = "kariyer_ilan",
                label = "Alertes Emploi",
                channelKeywords = listOf("job_alert", "emploi"),
                contentKeywords = listOf("nouvelles offres pour vous", "postes correspondant à votre profil", "opportunité de carrière")
            ),
            BlockCategory(
                id = "kripto_borsa",
                label = "Crypto / Bourse",
                channelKeywords = listOf("price_watch", "crypto", "bourse"),
                contentKeywords = listOf("le prix monte", "le prix baisse", "variation en pourcentage", "alerte action")
            ),
            BlockCategory(
                id = "pazarlama",
                label = "Marketing",
                channelKeywords = emptyList(),
                contentKeywords = listOf("vente flash", "méga vente", "super offre", "offre exclusive", "réservé aux membres", "aujourd'hui seulement", "dernier jour", "avant épuisement", "remise supplémentaire", "double remise", "offre VIP", "juste pour vous", "coupon", "code promo", "code de réduction", "carte cadeau", "livraison gratuite", "la plus grande promo de l'année", "liquidation", "soldes d'été", "soldes d'hiver", "fin de saison", "dernière chance", "dépêchez-vous")
            ),
            BlockCategory(
                id = "kredi",
                label = "Crédit & Finances",
                channelKeywords = listOf("loan_offer", "finance", "finances"),
                contentKeywords = listOf("carte de crédit", "prêt personnel", "mensualités", "intérêt", "sans intérêt", "limite de crédit", "restructuration de dette", "demande de prêt", "offre de prêt", "prêt rapide", "prêt instantané", "prêt pré-approuvé", "score de crédit", "avance de fonds")
            ),
            BlockCategory(
                id = "oneriler",
                label = "Recommandations",
                channelKeywords = listOf("for_you", "decouvrir", "pour_vous"),
                contentKeywords = listOf("pour vous", "sélectionné pour vous", "ça pourrait vous plaire", "découvrez", "selon votre historique", "selon vos favoris", "curaté pour vous")
            ),
            BlockCategory(
                id = "oyun",
                label = "Jeux",
                channelKeywords = listOf("daily_reward", "event", "evenement"),
                contentKeywords = listOf("récompense quotidienne", "bonus de connexion quotidien", "montez de niveau", "nouvel étage", "événement commencé", "événement à durée limitée", "vos amis jouent", "classement", "tournoi", "gagnez un prix", "quête quotidienne", "vies gratuites", "pièces gratuites", "gemmes gratuites", "énergie pleine")
            ),
            BlockCategory(
                id = "haber",
                label = "Actualités",
                channelKeywords = listOf("breaking_news", "titres"),
                contentKeywords = listOf("dernière minute", "titres", "actualités en direct", "mise à jour", "résumé", "résumé des actualités", "actualités du jour", "les plus lus", "sélection de la rédaction")
            ),
            BlockCategory(
                id = "genel",
                label = "Général",
                channelKeywords = listOf("notification", "reminder", "rappel"),
                contentKeywords = listOf("rappel", "notification système", "notification de l'app", "info")
            ),
            BlockCategory(
                id = "global_shopping_events",
                label = "Événements Shopping",
                channelKeywords = listOf("shopping_event", "seasonal_sale"),
                contentKeywords = listOf("Black Friday", "Cyber Monday", "Prime Day", "achetez un obtenez un", "offre éclair", "soldes d'hiver", "rentrée scolaire")
            ),
            BlockCategory(
                id = "global_food_delivery",
                label = "Livraison de Repas",
                channelKeywords = listOf("food_delivery", "restaurant_promo"),
                contentKeywords = listOf("livraison gratuite", "réduction sur votre première commande", "réduction sur votre prochaine commande", "frais de livraison offerts", "commandez maintenant et économisez", "offres de restaurants près de vous")
            ),
            BlockCategory(
                id = "global_rideshare",
                label = "VTC / Transport",
                channelKeywords = listOf("ride_promo", "rideshare"),
                contentKeywords = listOf("crédit de trajet", "code promo pour votre prochain trajet", "réduction sur votre prochain trajet", "trajet gratuit", "réservez votre trajet et économisez")
            ),
            BlockCategory(
                id = "global_real_estate",
                label = "Immobilier",
                channelKeywords = listOf("listing_alert", "real_estate"),
                contentKeywords = listOf("nouveau bien correspondant à votre recherche", "prix réduit sur un bien sauvegardé", "nouveaux biens à vendre près de vous", "portes ouvertes ce week-end")
            ),
            BlockCategory(
                id = "global_insurance",
                label = "Assurances",
                channelKeywords = listOf("insurance_offer"),
                contentKeywords = listOf("devis gratuit", "combinez et économisez", "réduisez votre prime", "comparez les tarifs d'assurance", "vous pourriez économiser sur votre police")
            ),
            BlockCategory(
                id = "global_streaming",
                label = "Streaming",
                channelKeywords = listOf("streaming_promo"),
                contentKeywords = listOf("nouvelle saison disponible", "essai gratuit aujourd'hui", "maintenant en streaming", "ajouté à votre liste", "bingez la nouvelle saison")
            ),
            BlockCategory(
                id = "global_local_deals",
                label = "Offres Locales",
                channelKeywords = listOf("local_deal", "daily_deal"),
                contentKeywords = listOf("offre du jour près de vous", "offre du jour", "jusqu'à 50% de réduction à proximité", "offres locales juste pour vous")
            ),
            BlockCategory(
                id = "global_travel_alerts",
                label = "Voyages / Vols",
                channelKeywords = listOf("fare_alert", "travel_deal"),
                contentKeywords = listOf("prix des vols baissés", "alerte de tarif", "baisse de prix sur votre voyage sauvegardé", "vols pas chers vers", "réservez maintenant et économisez sur les vols")
            )
        )

        val EN_BLOCK_CATEGORIES = listOf(
            // New categories
            BlockCategory(
                id = "sosyal_etkilesim",
                label = "Social Media",
                channelKeywords = listOf("social", "engagement"),
                contentKeywords = listOf("liked your", "commented on", "tagged you", "started following you", "friend request", "mentioned you", "new follower", "viewed your profile")
            ),
            BlockCategory(
                id = "yeniden_katilim",
                label = "Win-back",
                channelKeywords = listOf("re_engagement", "win_back"),
                contentKeywords = listOf("we miss you", "come back", "haven't seen you in a while", "we're here for you", "welcome back")
            ),
            BlockCategory(
                id = "degerlendirme_istegi",
                label = "Review Requests",
                channelKeywords = listOf("rating", "review_request"),
                contentKeywords = listOf("rate us", "rate this app", "leave a review", "share your experience", "give us 5 stars")
            ),
            BlockCategory(
                id = "anket",
                label = "Surveys / Feedback",
                channelKeywords = listOf("survey"),
                contentKeywords = listOf("take our survey", "tell us what you think", "quick survey", "your feedback matters")
            ),
            BlockCategory(
                id = "terk_edilen_sepet",
                label = "Cart Reminders",
                channelKeywords = listOf("cart_reminder"),
                contentKeywords = listOf("items in your cart", "complete your purchase", "don't forget your cart", "still waiting in your cart")
            ),
            BlockCategory(
                id = "fiyat_stok_uyarisi",
                label = "Price / Stock Alerts",
                channelKeywords = listOf("price_alert", "stock_alert"),
                contentKeywords = listOf("price drop", "back in stock", "before it sells out", "price alert", "sale on your favorites")
            ),
            BlockCategory(
                id = "sadakat_puan",
                label = "Loyalty / Points",
                channelKeywords = listOf("loyalty"),
                contentKeywords = listOf("points expiring", "you earned points", "redeem your points", "membership perks")
            ),
            BlockCategory(
                id = "abonelik_hatirlatma",
                label = "Subscription Renewal",
                channelKeywords = listOf("subscription_reminder"),
                contentKeywords = listOf("your trial is ending", "renew your subscription", "upgrade to premium", "free trial ends soon")
            ),
            BlockCategory(
                id = "sistem_guncelleme",
                label = "App Updates",
                channelKeywords = listOf("app_update"),
                contentKeywords = listOf("new version available", "update available", "see what's new", "update your app")
            ),
            BlockCategory(
                id = "spor_canli",
                label = "Sports / Live Score",
                channelKeywords = listOf("sports", "live_score"),
                contentKeywords = listOf("match started", "goal", "score update", "live result", "halftime")
            ),
            BlockCategory(
                id = "burc_astroloji",
                label = "Horoscope / Astrology",
                channelKeywords = listOf("horoscope"),
                contentKeywords = listOf("your horoscope today", "the stars today", "for your sign")
            ),
            BlockCategory(
                id = "flort_eslesme",
                label = "Dating / Matches",
                channelKeywords = listOf("dating", "match"),
                contentKeywords = listOf("likes you", "new match", "sent you a message", "matched with you")
            ),
            BlockCategory(
                id = "kariyer_ilan",
                label = "Job Alerts",
                channelKeywords = listOf("job_alert"),
                contentKeywords = listOf("new jobs for you", "positions matching your profile", "career opportunity")
            ),
            BlockCategory(
                id = "kripto_borsa",
                label = "Crypto / Stocks",
                channelKeywords = listOf("price_watch"),
                contentKeywords = listOf("price is up", "price is down", "percent change", "stock alert")
            ),
            // Expanded categories
            BlockCategory(
                id = "pazarlama",
                label = "Marketing",
                channelKeywords = emptyList(), // Same reason: marketing slogans, not real notification channel IDs
                contentKeywords = listOf("flash sale", "mega sale", "super deal", "exclusive offer", "members only", "today only", "last day", "while supplies last", "extra discount", "double discount", "vip offer", "just for you", "coupon", "coupon code", "promo code", "discount code", "gift card", "free shipping", "biggest sale of the year", "clearance", "summer sale", "winter sale", "end of season", "last chance", "hurry")
            ),
            BlockCategory(
                id = "kredi",
                label = "Credit & Finance",
                channelKeywords = listOf("loan_offer", "finance"),
                contentKeywords = listOf("credit card", "personal loan", "installment", "interest", "interest-free", "credit limit", "debt restructuring", "loan application", "loan offer", "quick loan", "instant loan", "pre-approved loan", "credit score", "cash advance")
            ),
            BlockCategory(
                id = "oneriler",
                label = "Recommendations",
                channelKeywords = listOf("for_you", "discover"),
                contentKeywords = listOf("for you", "picked for you", "you might like", "discover", "based on your history", "based on your favorites", "curated for you")
            ),
            BlockCategory(
                id = "oyun",
                label = "Games",
                channelKeywords = listOf("daily_reward", "event"),
                contentKeywords = listOf("daily reward", "daily login bonus", "level up", "new stage", "event started", "limited time event", "your friends are playing", "leaderboard", "tournament", "win a prize", "daily quest", "free lives", "free coins", "free gems", "energy full")
            ),
            BlockCategory(
                id = "haber",
                label = "News",
                channelKeywords = listOf("breaking_news", "headlines"),
                contentKeywords = listOf("breaking news", "breaking", "headlines", "live news", "update", "briefing", "news digest", "today's news", "most read", "editor's picks")
            ),
            BlockCategory(
                id = "genel",
                label = "General",
                channelKeywords = listOf("notification", "reminder"),
                contentKeywords = listOf("reminder", "system notification", "app notification", "info")
            ),
            // Global-market-only categories (no Turkish equivalent, not added to TR_BLOCK_CATEGORIES)
            BlockCategory(
                id = "global_shopping_events",
                label = "Shopping Day Sales",
                channelKeywords = listOf("shopping_event", "seasonal_sale"),
                contentKeywords = listOf("black friday", "cyber monday", "prime day", "buy one get one", "bogo", "doorbuster", "boxing day sale", "back to school sale")
            ),
            BlockCategory(
                id = "global_food_delivery",
                label = "Food Delivery Promos",
                channelKeywords = listOf("food_delivery", "restaurant_promo"),
                contentKeywords = listOf("free delivery", "% off your first order", "off your next order", "delivery fee waived", "order now and save", "restaurant deals near you")
            ),
            BlockCategory(
                id = "global_rideshare",
                label = "Ride-share Promos",
                channelKeywords = listOf("ride_promo", "rideshare"),
                contentKeywords = listOf("ride credit", "promo code for your next ride", "off your next ride", "free ride", "book your ride and save")
            ),
            BlockCategory(
                id = "global_real_estate",
                label = "Real Estate Alerts",
                channelKeywords = listOf("listing_alert", "real_estate"),
                contentKeywords = listOf("new listing matches your search", "price reduced on a saved home", "new homes for sale near you", "open house this weekend")
            ),
            BlockCategory(
                id = "global_insurance",
                label = "Insurance Marketing",
                channelKeywords = listOf("insurance_offer"),
                contentKeywords = listOf("get a free quote", "bundle and save", "lower your premium", "compare insurance rates", "you could save on your policy")
            ),
            BlockCategory(
                id = "global_streaming",
                label = "Streaming Platform Promos",
                channelKeywords = listOf("streaming_promo"),
                contentKeywords = listOf("new season available", "free trial starts today", "now streaming", "added to your watchlist", "binge the new season")
            ),
            BlockCategory(
                id = "global_local_deals",
                label = "Local Deals",
                channelKeywords = listOf("local_deal", "daily_deal"),
                contentKeywords = listOf("today's deal near you", "deal of the day", "up to 50% off nearby", "local deals just for you")
            ),
            BlockCategory(
                id = "global_travel_alerts",
                label = "Travel / Flight Price Alerts",
                channelKeywords = listOf("fare_alert", "travel_deal"),
                contentKeywords = listOf("flight prices dropped", "fare alert", "price drop on your saved trip", "cheap flights to", "book now and save on flights")
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
