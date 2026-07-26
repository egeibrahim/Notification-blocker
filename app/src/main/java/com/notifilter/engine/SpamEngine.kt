package com.notifilter.engine

import com.notifilter.data.entity.NotificationRecord
import java.util.Locale

/**
 * Sadeleştirilmiş blok motoru. Akış:
 * 1. Whitelist (Sessize Al) → Allow
 * 2. Kanal allow → Allow
 * 3. Manuel engellenen kanal / Kanal block (EN+TR) → Block
 * 4. İçerik block → Block
 * 5. Yoksa → Allow
 */
class SpamEngine {

    private fun normalizeText(input: String): String {
        return input.lowercase(Locale("tr", "TR"))
    }

    private fun tokenize(text: String): List<String> {
        // Harf/rakam dışını ayırıcı kabul et
        return text
            .split(Regex("[^\\p{L}\\p{Nd}]+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun stripTurkishSuffixOnce(word: String): String {
        // Çok agresif olmasın diye kısa kelimelerde stem yapma
        if (word.length <= 4) return word

        // Not: Bu bir "gerçek" Türkçe kök bulma değil; pratik bir heuristik.
        // Ama amaç: kullanıcı tüm ekli varyasyonları tek tek eklemesin.
        val suffixes = listOf(
            // Çoğul
            "lar", "ler",
            // Hal ekleri (çoğul varyasyonlar dahil)
            "ları", "leri", "lara", "lere", "lardan", "lerden",
            "dan", "den", "tan", "ten",
            "da", "de", "ta", "te",
            // Belirtme (yükleme) hali
            "yı", "yi", "yu", "yü",
            "ı", "i", "u", "ü",
            // İyelik / tamlama (temel)
            "nın", "nin", "nun", "nün",
            "ın", "in", "un", "ün",
            // 1. tekil/çoğul (bazı sık görülenler)
            "m", "ım", "im", "um", "üm",
            "miz", "mız", "muz", "müz",
            "niz", "nız", "nuz", "nüz",
            // Türetme ekleri
            "li", "lı", "lu", "lü",
            "lik", "lık", "luk", "lük",
            "siz", "sız", "suz", "süz",
            "ci", "cı", "cu", "cü",
            "cik", "cık", "cuk", "cük",
            // Bildirme eki
            "dir", "dır", "dur", "dür",
            "tir", "tır", "tur", "tür"
        ).sortedBy { it.length }

        for (suf in suffixes) {
            if (word.endsWith(suf) && word.length - suf.length >= 4) {
                return word.dropLast(suf.length)
            }
        }
        return word
    }

    private fun generateTokenVariants(tokens: List<String>): Set<String> {
        val out = HashSet<String>(tokens.size * 3)
        for (t in tokens) {
            val w0 = t
            out.add(w0)

            val w1 = stripTurkishSuffixOnce(w0)
            out.add(w1)

            val w2 = stripTurkishSuffixOnce(w1)
            out.add(w2)
        }
        return out
    }

    private fun generateWordVariants(word: String): Set<String> {
        val w0 = normalizeText(word.trim())
        if (w0.isBlank()) return emptySet()

        val out = HashSet<String>(4)
        out.add(w0)
        val w1 = stripTurkishSuffixOnce(w0)
        out.add(w1)
        val w2 = stripTurkishSuffixOnce(w1)
        out.add(w2)
        return out
    }

    private fun contentMatchesKeyword(
        lowerContent: String,
        lowerContentDefault: String,
        tokenVariants: Set<String>,
        keyword: String
    ): Boolean {
        val k = keyword.trim()
        if (k.isBlank()) return false

        val kk = normalizeText(k)
        // Eğer içerik doğrudan anahtar kelimeyi barındırıyorsa engelle/izin ver
        if (lowerContent.contains(kk)) {
            return true
        }

        // İngilizce/Varsayılan klavye girdilerini (ör. büyük I harfi) desteklemek için varsayılan küçük harf kontrolü
        val kkDefault = k.lowercase()
        if (lowerContentDefault.contains(kkDefault)) {
            return true
        }

        // Çok kelimeli / ifade keyword'lerinde ("son dakika", "free gift" gibi) mevcut davranış
        if (k.contains(' ')) {
            return false
        }

        // Tek kelimelerde kök/ek varyasyon yakalama
        val keywordVariants = generateWordVariants(k)
        return keywordVariants.any { it in tokenVariants }
    }

    private fun containsEmoji(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            val cp = Character.codePointAt(text, i)
            val isEmojiLike =
                (cp in 0x1F1E6..0x1F1FF) || // flags
                    (cp in 0x1F300..0x1FAFF) || // most emoji blocks
                    (cp in 0x2600..0x27BF) || // misc symbols
                    (cp == 0xFE0F) // variation selector-16
            if (isEmojiLike) return true
            i += Character.charCount(cp)
        }
        return false
    }

    fun analyze(
        notification: NotificationRecord,
        whitelistedPackages: Set<String> = emptySet(),
        config: FilterRulesConfig
    ): SpamResult {
        val channelId = notification.channelId?.let { normalizeText(it) } ?: ""
        val channelIdDefault = notification.channelId?.lowercase() ?: ""
        val lowerContent = normalizeText(notification.content)
        val lowerContentDefault = notification.content.lowercase()
        val tokenVariants = generateTokenVariants(tokenize(lowerContent)) +
            generateTokenVariants(tokenize(lowerContentDefault))
        val channelKey = "${notification.packageName}|${notification.channelId ?: ""}"

        if (notification.packageName in whitelistedPackages) return SpamResult.Allow

        config.contentAllow.find { keyword ->
            contentMatchesKeyword(lowerContent, lowerContentDefault, tokenVariants, keyword)
        }?.let {
            return SpamResult.Allow
        }

        if (config.blockIfHasEmoji && containsEmoji(notification.content)) {
            return SpamResult.Block("EMOJI")
        }

        if (channelKey in config.channelIdsBlocked) return SpamResult.Block("MANUAL_CHANNEL:${notification.channelId ?: ""}")

        if (config.channelAllow.any { channelId.contains(it) || channelIdDefault.contains(it) }) return SpamResult.Allow

        config.channelBlock.find { keyword -> 
            channelId.contains(keyword) || channelIdDefault.contains(keyword.lowercase())
        }?.let { keyword ->
            return SpamResult.Block("CHANNEL_ID:$keyword")
        }

        config.contentBlock.find { keyword ->
            contentMatchesKeyword(lowerContent, lowerContentDefault, tokenVariants, keyword)
        }?.let { keyword ->
            return SpamResult.Block("CONTENT:$keyword")
        }

        return SpamResult.Allow
    }
}
