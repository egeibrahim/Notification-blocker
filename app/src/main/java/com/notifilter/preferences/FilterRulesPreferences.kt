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
        val selected = TR_BLOCK_CATEGORIES + EN_BLOCK_CATEGORIES
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
        contentAllow = getAllUserContentAllowWords().toList(),
        contentBlock = (getEnabledRecommendedContentWords() + getAllUserContentBlockWords() + getUserCategoryWordsForActiveCategories()).toList(),
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

        private const val KEY_DISABLED_RECOMMENDED_CONTENT_WORDS = "filter_disabled_recommended_content_words"
        private const val KEY_DISABLED_RECOMMENDED_CHANNEL_WORDS = "filter_disabled_recommended_channel_words"

        private const val KEY_GLOBAL_EMOJI_BLOCK_ENABLED = "filter_global_emoji_block_enabled"
        private const val KEY_GLOBAL_GAMES_BLOCK_ENABLED = "filter_global_games_block_enabled"

        const val PACK_TR = "tr"
        const val PACK_EN = "en"

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

        val EN_BLOCK_CATEGORIES = listOf(
            // New categories
            BlockCategory(
                id = "sosyal_etkilesim",
                label = "Sosyal Medya",
                channelKeywords = listOf("social", "engagement"),
                contentKeywords = listOf("liked your", "commented on", "tagged you", "started following you", "friend request", "mentioned you", "new follower", "viewed your profile")
            ),
            BlockCategory(
                id = "yeniden_katilim",
                label = "Geri Kazanım",
                channelKeywords = listOf("re_engagement", "win_back"),
                contentKeywords = listOf("we miss you", "come back", "haven't seen you in a while", "we're here for you", "welcome back")
            ),
            BlockCategory(
                id = "degerlendirme_istegi",
                label = "Puanlama İstekleri",
                channelKeywords = listOf("rating", "review_request"),
                contentKeywords = listOf("rate us", "rate this app", "leave a review", "share your experience", "give us 5 stars")
            ),
            BlockCategory(
                id = "anket",
                label = "Anket / Geri Bildirim",
                channelKeywords = listOf("survey"),
                contentKeywords = listOf("take our survey", "tell us what you think", "quick survey", "your feedback matters")
            ),
            BlockCategory(
                id = "terk_edilen_sepet",
                label = "Sepet Hatırlatma",
                channelKeywords = listOf("cart_reminder"),
                contentKeywords = listOf("items in your cart", "complete your purchase", "don't forget your cart", "still waiting in your cart")
            ),
            BlockCategory(
                id = "fiyat_stok_uyarisi",
                label = "Fiyat / Stok Uyarısı",
                channelKeywords = listOf("price_alert", "stock_alert"),
                contentKeywords = listOf("price drop", "back in stock", "before it sells out", "price alert", "sale on your favorites")
            ),
            BlockCategory(
                id = "sadakat_puan",
                label = "Sadakat / Puan",
                channelKeywords = listOf("loyalty"),
                contentKeywords = listOf("points expiring", "you earned points", "redeem your points", "membership perks")
            ),
            BlockCategory(
                id = "abonelik_hatirlatma",
                label = "Abonelik Yenileme",
                channelKeywords = listOf("subscription_reminder"),
                contentKeywords = listOf("your trial is ending", "renew your subscription", "upgrade to premium", "free trial ends soon")
            ),
            BlockCategory(
                id = "sistem_guncelleme",
                label = "Güncelleme",
                channelKeywords = listOf("app_update"),
                contentKeywords = listOf("new version available", "update available", "see what's new", "update your app")
            ),
            BlockCategory(
                id = "spor_canli",
                label = "Spor / Canlı Skor",
                channelKeywords = listOf("sports", "live_score"),
                contentKeywords = listOf("match started", "goal", "score update", "live result", "halftime")
            ),
            BlockCategory(
                id = "burc_astroloji",
                label = "Burç / Astroloji",
                channelKeywords = listOf("horoscope"),
                contentKeywords = listOf("your horoscope today", "the stars today", "for your sign")
            ),
            BlockCategory(
                id = "flort_eslesme",
                label = "Flört / Eşleşme",
                channelKeywords = listOf("dating", "match"),
                contentKeywords = listOf("likes you", "new match", "sent you a message", "matched with you")
            ),
            BlockCategory(
                id = "kariyer_ilan",
                label = "Kariyer / İş İlanı",
                channelKeywords = listOf("job_alert"),
                contentKeywords = listOf("new jobs for you", "positions matching your profile", "career opportunity")
            ),
            BlockCategory(
                id = "kripto_borsa",
                label = "Kripto / Borsa",
                channelKeywords = listOf("price_watch"),
                contentKeywords = listOf("price is up", "price is down", "percent change", "stock alert")
            ),
            // Expanded categories
            BlockCategory(
                id = "pazarlama",
                label = "Pazarlama",
                channelKeywords = emptyList(), // Same reason: marketing slogans, not real notification channel IDs
                contentKeywords = listOf("flash sale", "mega sale", "super deal", "exclusive offer", "members only", "today only", "last day", "while supplies last", "extra discount", "double discount", "vip offer", "just for you", "coupon", "coupon code", "promo code", "discount code", "gift card", "free shipping", "biggest sale of the year", "clearance", "summer sale", "winter sale", "end of season", "last chance", "hurry")
            ),
            BlockCategory(
                id = "kredi",
                label = "Kredi ve Finans",
                channelKeywords = listOf("loan_offer", "finance"),
                contentKeywords = listOf("credit card", "personal loan", "installment", "interest", "interest-free", "credit limit", "debt restructuring", "loan application", "loan offer", "quick loan", "instant loan", "pre-approved loan", "credit score", "cash advance")
            ),
            BlockCategory(
                id = "oneriler",
                label = "Öneriler",
                channelKeywords = listOf("for_you", "discover"),
                contentKeywords = listOf("for you", "picked for you", "you might like", "discover", "based on your history", "based on your favorites", "curated for you")
            ),
            BlockCategory(
                id = "oyun",
                label = "Oyunlar",
                channelKeywords = listOf("daily_reward", "event"),
                contentKeywords = listOf("daily reward", "daily login bonus", "level up", "new stage", "event started", "limited time event", "your friends are playing", "leaderboard", "tournament", "win a prize", "daily quest", "free lives", "free coins", "free gems", "energy full")
            ),
            BlockCategory(
                id = "haber",
                label = "Haberler",
                channelKeywords = listOf("breaking_news", "headlines"),
                contentKeywords = listOf("breaking news", "breaking", "headlines", "live news", "update", "briefing", "news digest", "today's news", "most read", "editor's picks")
            ),
            BlockCategory(
                id = "genel",
                label = "Genel",
                channelKeywords = listOf("notification", "reminder"),
                contentKeywords = listOf("reminder", "system notification", "app notification", "info")
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
