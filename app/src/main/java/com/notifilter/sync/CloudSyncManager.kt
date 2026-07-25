package com.notifilter.sync

import android.content.Context
import android.net.Uri
import com.notifilter.BuildConfig
import com.notifilter.auth.SupabaseAuthManager
import com.notifilter.data.database.AppDatabase
import com.notifilter.data.entity.NotificationRecord
import com.notifilter.preferences.FilterRulesPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

object CloudSyncManager {
    private const val TABLE_SETTINGS = "notifilter_settings"
    private const val TABLE_ARCHIVE = "notifilter_archive"

    private fun baseUrl(): String = BuildConfig.SUPABASE_URL.trim().trimEnd('/')

    suspend fun backupNow(context: Context) {
        withContext(Dispatchers.IO) {
            val token = SupabaseAuthManager.getAccessToken(context) ?: return@withContext
            val userId = SupabaseAuthManager.refreshUserIdIfNeeded(context) ?: return@withContext
            val supabaseUrl = baseUrl()
            if (supabaseUrl.isBlank()) return@withContext

            val filterPrefs = FilterRulesPreferences(context)
            val prefsSnapshot = filterPrefs.exportAllPrefs()
            upsertSettings(supabaseUrl, token, userId, prefsSnapshot)

            val db = AppDatabase.getInstance(context)
            val since = System.currentTimeMillis() - 7L * 24L * 60L * 60L * 1000L
            val records = db.notificationRecordDao().getSinceOnce(since)
            upsertArchiveBatch(supabaseUrl, token, userId, records)
        }
    }

    suspend fun restoreNow(context: Context) {
        withContext(Dispatchers.IO) {
            val token = SupabaseAuthManager.getAccessToken(context) ?: return@withContext
            val userId = SupabaseAuthManager.refreshUserIdIfNeeded(context) ?: return@withContext
            val supabaseUrl = baseUrl()
            if (supabaseUrl.isBlank()) return@withContext

            val settings = fetchLatestSettings(supabaseUrl, token, userId)
            if (settings != null) {
                val filterPrefs = FilterRulesPreferences(context)
                filterPrefs.importAllPrefs(settings)
            }

            val db = AppDatabase.getInstance(context)
            val since = System.currentTimeMillis() - 7L * 24L * 60L * 60L * 1000L
            val archive = fetchArchiveSince(supabaseUrl, token, userId, since)
            if (archive.isNotEmpty()) {
                db.notificationRecordDao().deleteSince(since)
                db.notificationRecordDao().insertAll(archive)
            }
        }
    }

    private fun upsertSettings(supabaseUrl: String, token: String, userId: String, prefsSnapshot: Map<String, Any?>) {
        val url = URL("$supabaseUrl/rest/v1/$TABLE_SETTINGS")
        val body = JSONObject().apply {
            put("user_id", userId)
            put("prefs", JSONObject(prefsSnapshot))
            put("updated_at", Instant.now().toString())
        }
        postJson(url, token, body, prefer = "resolution=merge-duplicates")
    }

    private fun upsertArchiveBatch(supabaseUrl: String, token: String, userId: String, records: List<NotificationRecord>) {
        if (records.isEmpty()) return
        val url = URL("$supabaseUrl/rest/v1/$TABLE_ARCHIVE")
        val arr = JSONArray()
        records.forEach { r ->
            val obj = JSONObject()
            obj.put("user_id", userId)
            obj.put("dedupe_key", dedupeKey(r))
            obj.put("package_name", r.packageName)
            obj.put("app_name", r.appName)
            obj.put("content", r.content)
            obj.put("channel_id", r.channelId)
            obj.put("timestamp_ms", r.timestamp)
            obj.put("is_blocked", r.isBlocked)
            obj.put("block_reason", r.blockReason)
            arr.put(obj)
        }
        postJson(url, token, arr, prefer = "resolution=merge-duplicates")
    }

    private fun fetchLatestSettings(supabaseUrl: String, token: String, userId: String): Map<String, Any?>? {
        val url = URL(
            "$supabaseUrl/rest/v1/$TABLE_SETTINGS?select=prefs&user_id=eq.${Uri.encode(userId)}&order=updated_at.desc&limit=1"
        )
        val json = getJson(url, token) ?: return null
        val arr = JSONArray(json)
        if (arr.length() == 0) return null
        val prefs = arr.getJSONObject(0).optJSONObject("prefs") ?: return null
        return prefs.toMap()
    }

    private fun fetchArchiveSince(supabaseUrl: String, token: String, userId: String, sinceMs: Long): List<NotificationRecord> {
        val url = URL(
            "$supabaseUrl/rest/v1/$TABLE_ARCHIVE?select=package_name,app_name,content,channel_id,timestamp_ms,is_blocked,block_reason&user_id=eq.${Uri.encode(userId)}&timestamp_ms=gte.${sinceMs}&order=timestamp_ms.desc&limit=2000"
        )
        val json = getJson(url, token) ?: return emptyList()
        val arr = JSONArray(json)
        val out = ArrayList<NotificationRecord>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                NotificationRecord(
                    packageName = o.getString("package_name"),
                    appName = o.getString("app_name"),
                    content = o.getString("content"),
                    channelId = o.optString("channel_id").takeIf { it.isNotBlank() },
                    timestamp = o.getLong("timestamp_ms"),
                    isBlocked = o.getBoolean("is_blocked"),
                    blockReason = o.optString("block_reason").takeIf { it.isNotBlank() }
                )
            )
        }
        return out
    }

    private fun dedupeKey(r: NotificationRecord): String {
        val contentPart = r.content.trim().lowercase().take(120)
        return "${r.packageName}|${r.timestamp}|${contentPart.hashCode()}"
    }

    private fun postJson(url: URL, token: String, json: Any, prefer: String? = null): String? {
        val conn = (url.openConnection() as HttpURLConnection)
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
        conn.setRequestProperty("Prefer", prefer ?: "return=minimal")

        OutputStreamWriter(conn.outputStream).use { w ->
            w.write(json.toString())
        }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = BufferedReader(stream.reader()).use { it.readText() }
        if (code !in 200..299) {
            val snippet = body.replace("\n", " ").take(240)
            throw IllegalStateException("CloudSync POST failed ($code): $snippet")
        }
        return body
    }

    private fun getJson(url: URL, token: String): String? {
        val conn = (url.openConnection() as HttpURLConnection)
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
        conn.connectTimeout = 8000
        conn.readTimeout = 8000

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = BufferedReader(stream.reader()).use { it.readText() }
        if (code !in 200..299) {
            val snippet = body.replace("\n", " ").take(240)
            throw IllegalStateException("CloudSync GET failed ($code): $snippet")
        }
        return body
    }

    private fun JSONObject.toMap(): Map<String, Any?> {
        val map = LinkedHashMap<String, Any?>()
        val keys = keys()
        while (keys.hasNext()) {
            val k = keys.next()
            map[k] = when (val v = opt(k)) {
                JSONObject.NULL -> null
                is JSONObject -> v.toMap()
                is JSONArray -> v.toList()
                else -> v
            }
        }
        return map
    }

    private fun JSONArray.toList(): List<Any?> {
        val list = ArrayList<Any?>(length())
        for (i in 0 until length()) {
            val v = opt(i)
            list.add(
                when (v) {
                    JSONObject.NULL -> null
                    is JSONObject -> v.toMap()
                    is JSONArray -> v.toList()
                    else -> v
                }
            )
        }
        return list
    }
}
