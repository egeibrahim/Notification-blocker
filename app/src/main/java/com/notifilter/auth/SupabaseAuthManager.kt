package com.notifilter.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import com.notifilter.BuildConfig
import com.notifilter.sync.CloudSyncManager
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Base64
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object SupabaseAuthManager {
    private const val PREFS_NAME = "supabase_auth"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_TOKEN_TYPE = "token_type"
    private const val KEY_EXPIRES_IN = "expires_in"
    private const val KEY_USER_EMAIL = "user_email"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_CODE_VERIFIER = "code_verifier"

    private data class CodeExchangeResult(
        val ok: Boolean,
        val errorMessage: String? = null,
    )

    data class AuthResult(
        val success: Boolean,
        val errorMessage: String? = null
    )

    suspend fun signUpWithEmail(context: Context, email: String, password: String): AuthResult {
        val supabaseUrl = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
        if (supabaseUrl.isBlank()) return AuthResult(false, "missing SUPABASE_URL")
        val anonKey = BuildConfig.SUPABASE_ANON_KEY.trim()
        if (anonKey.isBlank()) return AuthResult(false, "missing SUPABASE_ANON_KEY")

        return withContext(Dispatchers.IO) {
            try {
                val url = URL("$supabaseUrl/auth/v1/signup")
                val conn = (url.openConnection() as HttpURLConnection)
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("apikey", anonKey)
                conn.setRequestProperty("Authorization", "Bearer $anonKey")
                conn.doOutput = true
                conn.connectTimeout = 10000
                conn.readTimeout = 10000

                val body = "{\"email\":\"${escapeJson(email)}\",\"password\":\"${escapeJson(password)}\"}"
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

                val httpCode = conn.responseCode
                val stream = if (httpCode in 200..299) conn.inputStream else conn.errorStream
                val resp = BufferedReader(InputStreamReader(stream)).use { it.readText() }

                if (httpCode !in 200..299) {
                    val errorMsg = runCatching { JSONObject(resp).optString("msg") }.getOrNull()
                        ?: runCatching { JSONObject(resp).optString("error_description") }.getOrNull()
                        ?: "HTTP $httpCode"
                    return@withContext AuthResult(false, errorMsg)
                }

                val accessToken = extractJsonString(resp, "access_token")
                val refreshToken = extractJsonString(resp, "refresh_token")
                val tokenType = extractJsonString(resp, "token_type")
                val expiresIn = extractJsonString(resp, "expires_in")
                val userObj = runCatching { JSONObject(resp).optJSONObject("user") }.getOrNull()
                val userId = userObj?.optString("id")
                val userEmail = userObj?.optString("email")

                if (!accessToken.isNullOrBlank()) {
                    prefs(context).edit()
                        .putString(KEY_ACCESS_TOKEN, accessToken)
                        .putString(KEY_REFRESH_TOKEN, refreshToken)
                        .putString(KEY_TOKEN_TYPE, tokenType)
                        .putString(KEY_EXPIRES_IN, expiresIn)
                        .putString(KEY_USER_ID, userId)
                        .putString(KEY_USER_EMAIL, userEmail)
                        .apply()

                    runCatching {
                        CloudSyncManager.restoreNow(context)
                    }
                    AuthResult(true)
                } else {
                    AuthResult(true, "Lütfen e-posta adresinize gönderilen doğrulama bağlantısını onaylayın.")
                }
            } catch (e: Exception) {
                AuthResult(false, e.message ?: "Bilinmeyen bir hata oluştu")
            }
        }
    }

    suspend fun signInWithEmail(context: Context, email: String, password: String): AuthResult {
        val supabaseUrl = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
        if (supabaseUrl.isBlank()) return AuthResult(false, "missing SUPABASE_URL")
        val anonKey = BuildConfig.SUPABASE_ANON_KEY.trim()
        if (anonKey.isBlank()) return AuthResult(false, "missing SUPABASE_ANON_KEY")

        return withContext(Dispatchers.IO) {
            try {
                val url = URL("$supabaseUrl/auth/v1/token?grant_type=password")
                val conn = (url.openConnection() as HttpURLConnection)
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("apikey", anonKey)
                conn.setRequestProperty("Authorization", "Bearer $anonKey")
                conn.doOutput = true
                conn.connectTimeout = 10000
                conn.readTimeout = 10000

                val body = "{\"email\":\"${escapeJson(email)}\",\"password\":\"${escapeJson(password)}\"}"
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

                val httpCode = conn.responseCode
                val stream = if (httpCode in 200..299) conn.inputStream else conn.errorStream
                val resp = BufferedReader(InputStreamReader(stream)).use { it.readText() }

                if (httpCode !in 200..299) {
                    val errorMsg = runCatching { JSONObject(resp).optString("error_description") }.getOrNull()
                        ?: runCatching { JSONObject(resp).optString("msg") }.getOrNull()
                        ?: "HTTP $httpCode"
                    return@withContext AuthResult(false, errorMsg)
                }

                val accessToken = extractJsonString(resp, "access_token")
                val refreshToken = extractJsonString(resp, "refresh_token")
                val tokenType = extractJsonString(resp, "token_type")
                val expiresIn = extractJsonString(resp, "expires_in")
                val userObj = runCatching { JSONObject(resp).optJSONObject("user") }.getOrNull()
                val userId = userObj?.optString("id")
                val userEmail = userObj?.optString("email") ?: extractJsonString(resp, "email")

                if (!accessToken.isNullOrBlank()) {
                    prefs(context).edit()
                        .putString(KEY_ACCESS_TOKEN, accessToken)
                        .putString(KEY_REFRESH_TOKEN, refreshToken)
                        .putString(KEY_TOKEN_TYPE, tokenType)
                        .putString(KEY_EXPIRES_IN, expiresIn)
                        .putString(KEY_USER_ID, userId)
                        .putString(KEY_USER_EMAIL, userEmail ?: email)
                        .apply()

                    runCatching {
                        CloudSyncManager.restoreNow(context)
                    }
                    AuthResult(true)
                } else {
                    AuthResult(false, "Giriş başarısız: Oturum anahtarı alınamadı.")
                }
            } catch (e: Exception) {
                AuthResult(false, e.message ?: "Bilinmeyen bir hata oluştu")
            }
        }
    }

    suspend fun resetPassword(context: Context, email: String): AuthResult {
        val supabaseUrl = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
        if (supabaseUrl.isBlank()) return AuthResult(false, "missing SUPABASE_URL")
        val anonKey = BuildConfig.SUPABASE_ANON_KEY.trim()
        if (anonKey.isBlank()) return AuthResult(false, "missing SUPABASE_ANON_KEY")

        return withContext(Dispatchers.IO) {
            try {
                val url = URL("$supabaseUrl/auth/v1/recover")
                val conn = (url.openConnection() as HttpURLConnection)
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("apikey", anonKey)
                conn.setRequestProperty("Authorization", "Bearer $anonKey")
                conn.doOutput = true
                conn.connectTimeout = 10000
                conn.readTimeout = 10000

                val body = "{\"email\":\"${escapeJson(email)}\"}"
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

                val httpCode = conn.responseCode
                val stream = if (httpCode in 200..299) conn.inputStream else conn.errorStream
                val resp = BufferedReader(InputStreamReader(stream)).use { it.readText() }

                if (httpCode !in 200..299) {
                    val errorMsg = runCatching { JSONObject(resp).optString("msg") }.getOrNull()
                        ?: runCatching { JSONObject(resp).optString("error_description") }.getOrNull()
                        ?: "HTTP $httpCode"
                    return@withContext AuthResult(false, errorMsg)
                }

                AuthResult(true)
            } catch (e: Exception) {
                AuthResult(false, e.message ?: "Bilinmeyen bir hata oluştu")
            }
        }
    }

    private const val REDIRECT_SCHEME = "com.ibrahimege.notifilter"
    private const val REDIRECT_HOST = "auth"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun redirectUrl(): String = "$REDIRECT_SCHEME://$REDIRECT_HOST"

    fun signInWithGoogle(context: Context) {
        val supabaseUrl = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
        if (supabaseUrl.isBlank()) {
            Toast.makeText(context, "Missing SUPABASE_URL", Toast.LENGTH_LONG).show()
            return
        }

        val anonKey = BuildConfig.SUPABASE_ANON_KEY.trim()
        if (anonKey.isBlank()) {
            Toast.makeText(context, "Missing SUPABASE_ANON_KEY", Toast.LENGTH_LONG).show()
            return
        }

        val verifier = generateCodeVerifier()
        val challenge = codeChallengeS256(verifier)
        prefs(context).edit().putString(KEY_CODE_VERIFIER, verifier).apply()

        val authUri = Uri.parse("$supabaseUrl/auth/v1/authorize").buildUpon()
            .appendQueryParameter("provider", "google")
            .appendQueryParameter("redirect_to", redirectUrl())
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "s256")
            .build()

        val activity = context as? Activity
        val launched = runCatching {
            if (activity != null) {
                CustomTabsIntent.Builder().build().launchUrl(activity, authUri)
            } else {
                val intent = Intent(Intent.ACTION_VIEW, authUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
            true
        }.getOrElse {
            Toast.makeText(context, "Failed to open browser for login", Toast.LENGTH_LONG).show()
            false
        }

        if (!launched) return
    }

    fun handleRedirect(activity: Activity, intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != REDIRECT_SCHEME || data.host != REDIRECT_HOST) return

        val fragmentParams = parseParams(data.fragment ?: "")
        val queryParams = parseParams(data.query ?: "")
        val params = queryParams + fragmentParams

        val accessToken = params["access_token"]
        if (!accessToken.isNullOrBlank()) {
            val refreshToken = params["refresh_token"]
            val tokenType = params["token_type"]
            val expiresIn = params["expires_in"]

            prefs(activity).edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .putString(KEY_TOKEN_TYPE, tokenType)
                .putString(KEY_EXPIRES_IN, expiresIn)
                .apply()

            // Best-effort: refresh cached email in background
            Thread {
                refreshUserEmailIfNeeded(activity)
            }.start()

            CoroutineScope(Dispatchers.IO).launch {
                CloudSyncManager.restoreNow(activity)
            }
        } else {
            val keys = params.keys.sorted().joinToString(",")
            val code = params["code"]
            val error = params["error_description"] ?: params["error"]
            if (!code.isNullOrBlank() && error.isNullOrBlank()) {
                CoroutineScope(Dispatchers.IO).launch {
                    val res = exchangeCodeForSession(activity, code)
                    if (!res.ok) {
                        activity.runOnUiThread {
                            val suffix = res.errorMessage?.let { ": $it" } ?: ""
                            Toast.makeText(activity, "OAuth code exchange failed$suffix", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } else {
                activity.runOnUiThread {
                    val msg = when {
                        !error.isNullOrBlank() -> "OAuth error: $error"
                        !code.isNullOrBlank() -> "OAuth returned code (not token). Keys: $keys"
                        else -> "OAuth redirect missing access_token. Keys: $keys"
                    }
                    Toast.makeText(activity, msg, Toast.LENGTH_LONG).show()
                }
            }
        }

        // Prevent re-processing
        activity.intent = Intent(activity.intent).setData(null)
    }

    fun getAccessToken(context: Context): String? = prefs(context).getString(KEY_ACCESS_TOKEN, null)

    fun getUserId(context: Context): String? = prefs(context).getString(KEY_USER_ID, null)

    fun getUserEmail(context: Context): String? = prefs(context).getString(KEY_USER_EMAIL, null)

    fun signOut(context: Context) {
        prefs(context).edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_TOKEN_TYPE)
            .remove(KEY_EXPIRES_IN)
            .remove(KEY_USER_EMAIL)
            .remove(KEY_USER_ID)
            .apply()
    }

    fun refreshUserIdIfNeeded(context: Context): String? {
        val cached = getUserId(context)
        if (!cached.isNullOrBlank()) return cached

        val token = getAccessToken(context) ?: return null
        val supabaseUrl = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
        if (supabaseUrl.isBlank()) return null

        val url = URL("$supabaseUrl/auth/v1/user")
        val conn = (url.openConnection() as HttpURLConnection)
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
        conn.connectTimeout = 8000
        conn.readTimeout = 8000

        return runCatching {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = BufferedReader(InputStreamReader(stream)).use { it.readText() }
            val id = runCatching { JSONObject(body).optString("id") }.getOrNull()
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            if (!id.isNullOrBlank()) {
                prefs(context).edit().putString(KEY_USER_ID, id).apply()
            }
            id
        }.getOrNull()
    }

    fun refreshUserEmailIfNeeded(context: Context): String? {
        val token = getAccessToken(context) ?: return null
        val supabaseUrl = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
        if (supabaseUrl.isBlank()) return null

        val url = URL("$supabaseUrl/auth/v1/user")
        val conn = (url.openConnection() as HttpURLConnection)
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
        conn.connectTimeout = 8000
        conn.readTimeout = 8000

        return runCatching {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = BufferedReader(InputStreamReader(stream)).use { it.readText() }
            val email = extractJsonString(body, "email")
            if (!email.isNullOrBlank()) {
                prefs(context).edit().putString(KEY_USER_EMAIL, email).apply()
            }
            email
        }.getOrNull()
    }

    private fun parseParams(fragment: String): Map<String, String> {
        if (fragment.isBlank()) return emptyMap()
        val clean = fragment.trimStart('#').trimStart('?')
        if (clean.isBlank()) return emptyMap()
        return clean.split('&')
            .mapNotNull { part ->
                val idx = part.indexOf('=')
                if (idx <= 0) return@mapNotNull null
                val k = Uri.decode(part.substring(0, idx))
                val v = Uri.decode(part.substring(idx + 1))
                k to v
            }
            .toMap()
    }

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        java.security.SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun codeChallengeS256(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    private suspend fun exchangeCodeForSession(context: Context, code: String): CodeExchangeResult {
        val verifier = prefs(context).getString(KEY_CODE_VERIFIER, null)
            ?: return CodeExchangeResult(false, "missing code_verifier")
        val supabaseUrl = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
        if (supabaseUrl.isBlank()) return CodeExchangeResult(false, "missing SUPABASE_URL")

        val anonKey = BuildConfig.SUPABASE_ANON_KEY.trim()
        if (anonKey.isBlank()) return CodeExchangeResult(false, "missing SUPABASE_ANON_KEY")

        val url = URL("$supabaseUrl/auth/v1/token?grant_type=pkce")
        val conn = (url.openConnection() as HttpURLConnection)
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("apikey", anonKey)
        conn.setRequestProperty("Authorization", "Bearer $anonKey")
        conn.doOutput = true
        conn.connectTimeout = 10000
        conn.readTimeout = 10000

        val body = "{\"auth_code\":\"${escapeJson(code)}\",\"code_verifier\":\"${escapeJson(verifier)}\"}"
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

        val httpCode = conn.responseCode
        val stream = if (httpCode in 200..299) conn.inputStream else conn.errorStream
        val resp = BufferedReader(InputStreamReader(stream)).use { it.readText() }
        val accessToken = extractJsonString(resp, "access_token")
        if (accessToken.isNullOrBlank()) {
            val snippet = resp.replace("\n", " ").take(180)
            return CodeExchangeResult(false, "HTTP $httpCode $snippet")
        }

        val refreshToken = extractJsonString(resp, "refresh_token")
        val tokenType = extractJsonString(resp, "token_type")
        val expiresIn = extractJsonString(resp, "expires_in")

        prefs(context).edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putString(KEY_TOKEN_TYPE, tokenType)
            .putString(KEY_EXPIRES_IN, expiresIn)
            .apply()

        runCatching {
            refreshUserEmailIfNeeded(context)
        }

        runCatching {
            CloudSyncManager.restoreNow(context)
        }

        return CodeExchangeResult(true)
    }

    private fun escapeJson(value: String): String {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
    }

    private fun extractJsonString(json: String, key: String): String? {
        // Minimal extraction: looks for "key":"value" patterns. Good enough for email.
        val needle = "\"$key\""
        val keyPos = json.indexOf(needle)
        if (keyPos < 0) return null
        val colonPos = json.indexOf(':', startIndex = keyPos + needle.length)
        if (colonPos < 0) return null
        val firstQuote = json.indexOf('"', startIndex = colonPos + 1)
        if (firstQuote < 0) return null
        val secondQuote = json.indexOf('"', startIndex = firstQuote + 1)
        if (secondQuote < 0) return null
        return json.substring(firstQuote + 1, secondQuote)
    }
}
