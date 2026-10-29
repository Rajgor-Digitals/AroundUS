package com.aroundus.app.network

import android.content.Context
import android.content.SharedPreferences
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Singleton Supabase Client initializing OkHttpClient & Retrofit for PostgREST & Auth calls.
 */
object SupabaseClient {

    private const val PREFS_NAME = "aroundus_supabase_prefs"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USER_EMAIL = "user_email"
    private const val KEY_USER_NAME = "user_name"

    private var appContext: Context? = null
    private var retrofit: Retrofit? = null
    private var supabaseService: SupabaseService? = null
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun getAppContext(): Context? = appContext

    fun getService(): SupabaseService {
        if (supabaseService == null) {
            val authInterceptor = Interceptor { chain ->
                val originalRequest = chain.request()
                val existingAuth = originalRequest.header("Authorization")
                
                // If caller already passed an explicit Authorization header (e.g. Bearer token for updateAuthUser or getAuthUser),
                // do NOT clobber it with the anon key or default session!
                val token = getAccessToken()
                val validToken = if (!token.isNullOrBlank() && !isJwtExpired(token)) {
                    token
                } else {
                    if (!token.isNullOrBlank()) {
                        prefs?.edit()?.remove(KEY_ACCESS_TOKEN)?.apply()
                    }
                    null
                }

                val authHeaderValue = if (!existingAuth.isNullOrBlank() && !existingAuth.equals("Bearer ${SupabaseConfig.SUPABASE_ANON_KEY}", ignoreCase = true)) {
                    if (existingAuth.startsWith("Bearer ", ignoreCase = true)) {
                        existingAuth
                    } else {
                        "Bearer $existingAuth"
                    }
                } else if (!validToken.isNullOrBlank() && validToken.startsWith("eyJ")) {
                    "Bearer $validToken"
                } else {
                    "Bearer ${SupabaseConfig.SUPABASE_ANON_KEY}"
                }

                val requestBuilder = originalRequest.newBuilder()
                    .header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                    .header("Authorization", authHeaderValue)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")

                var response = chain.proceed(requestBuilder.build())
                if (response.code == 401 && authHeaderValue != "Bearer ${SupabaseConfig.SUPABASE_ANON_KEY}") {
                    prefs?.edit()?.remove(KEY_ACCESS_TOKEN)?.apply()
                    response.close()
                    val retryRequest = originalRequest.newBuilder()
                        .header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                        .header("Authorization", "Bearer ${SupabaseConfig.SUPABASE_ANON_KEY}")
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .build()
                    response = chain.proceed(retryRequest)
                }
                response
            }

            val loggingInterceptor = HttpLoggingInterceptor { message ->
                android.util.Log.d("SupabaseHttp", message)
            }.apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val clientBuilder = OkHttpClient.Builder()
                .addInterceptor(authInterceptor)
                .addInterceptor(loggingInterceptor)
                .connectionPool(okhttp3.ConnectionPool(10, 5, TimeUnit.MINUTES))
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS)
                .writeTimeout(12, TimeUnit.SECONDS)

            appContext?.cacheDir?.let { cacheDir ->
                try {
                    val httpCache = okhttp3.Cache(java.io.File(cacheDir, "http_cache"), 10L * 1024L * 1024L)
                    clientBuilder.cache(httpCache)
                } catch (_: Exception) {}
            }

            val okHttpClient = clientBuilder.build()

            val baseUrl = if (SupabaseConfig.SUPABASE_URL.endsWith("/")) {
                SupabaseConfig.SUPABASE_URL
            } else {
                "${SupabaseConfig.SUPABASE_URL}/"
            }

            retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            supabaseService = retrofit!!.create(SupabaseService::class.java)
        }
        return supabaseService!!
    }

    // --- SESSION MANAGEMENT ---

    fun saveSession(accessToken: String?, userId: String?, email: String?, name: String? = null) {
        prefs?.edit()?.apply {
            putString(KEY_ACCESS_TOKEN, accessToken)
            putString(KEY_USER_ID, userId)
            putString(KEY_USER_EMAIL, email?.trim()?.lowercase())
            if (name != null) putString(KEY_USER_NAME, name.trim())
            apply()
        }
    }

    fun saveAccount(email: String, pass: String, userId: String, name: String) {
        val cleanEmail = email.trim().lowercase()
        prefs?.edit()?.apply {
            putString("pwd_$cleanEmail", pass)
            putString("uid_$cleanEmail", userId)
            putString("name_$cleanEmail", name)
            apply()
        }
    }

    fun getAccountPassword(email: String): String? {
        val cleanEmail = email.trim().lowercase()
        return prefs?.getString("pwd_$cleanEmail", null)
    }

    fun getAccountUserId(email: String): String? {
        val cleanEmail = email.trim().lowercase()
        return prefs?.getString("uid_$cleanEmail", null)
    }

    fun getAccountName(email: String): String? {
        val cleanEmail = email.trim().lowercase()
        return prefs?.getString("name_$cleanEmail", null)
    }

    fun saveAvatarPath(key: String, path: String) {
        if (key.isBlank() || path.isBlank()) return
        val cleanKey = key.trim().lowercase()
        prefs?.edit()?.putString("avatar_$cleanKey", path)?.apply()
    }

    fun getAvatarPath(key: String): String? {
        if (key.isBlank()) return null
        val cleanKey = key.trim().lowercase()
        return prefs?.getString("avatar_$cleanKey", null)
    }

    fun addLocalFollower(targetUserId: String, followerUserId: String) {
        if (targetUserId.isBlank() || followerUserId.isBlank()) return
        val set = getLocalFollowersSet(targetUserId).toMutableSet()
        set.add(followerUserId)
        prefs?.edit()?.putStringSet("followers_$targetUserId", set)?.apply()
    }

    fun removeLocalFollower(targetUserId: String, followerUserId: String) {
        if (targetUserId.isBlank() || followerUserId.isBlank()) return
        val set = getLocalFollowersSet(targetUserId).toMutableSet()
        set.remove(followerUserId)
        prefs?.edit()?.putStringSet("followers_$targetUserId", set)?.apply()
    }

    fun getLocalFollowersSet(targetUserId: String): Set<String> {
        if (targetUserId.isBlank()) return emptySet()
        return prefs?.getStringSet("followers_$targetUserId", emptySet()) ?: emptySet()
    }

    fun saveOtpForEmail(email: String, otpCode: String) {
        val cleanEmail = email.trim().lowercase()
        prefs?.edit()?.putString("otp_$cleanEmail", otpCode)?.apply()
    }

    fun getOtpForEmail(email: String): String? {
        val cleanEmail = email.trim().lowercase()
        return prefs?.getString("otp_$cleanEmail", null)
    }

    fun addLocalFollowing(currentUserId: String, targetUserId: String) {
        val set = getLocalFollowingSet(currentUserId).toMutableSet()
        set.add(targetUserId)
        prefs?.edit()?.putStringSet("following_$currentUserId", set)?.apply()
    }

    fun removeLocalFollowing(currentUserId: String, targetUserId: String) {
        val set = getLocalFollowingSet(currentUserId).toMutableSet()
        set.remove(targetUserId)
        prefs?.edit()?.putStringSet("following_$currentUserId", set)?.apply()
    }

    fun getLocalFollowingSet(currentUserId: String): Set<String> {
        return prefs?.getStringSet("following_$currentUserId", emptySet()) ?: emptySet()
    }

    fun getSavedPostIds(userId: String): Set<String> {
        if (userId.isBlank()) return emptySet()
        return prefs?.getStringSet("saved_posts_$userId", emptySet()) ?: emptySet()
    }

    fun toggleSavedPost(userId: String, postId: String): Boolean {
        if (userId.isBlank() || postId.isBlank()) return false
        val set = getSavedPostIds(userId).toMutableSet()
        val isNowSaved = if (set.contains(postId)) {
            set.remove(postId)
            false
        } else {
            set.add(postId)
            true
        }
        prefs?.edit()?.putStringSet("saved_posts_$userId", set)?.apply()
        return isNowSaved
    }

    // --- EVENT ATTENDANCE PERSISTENCE ---

    fun getAttendedEventIds(userId: String = getCurrentUserId() ?: "default"): Set<String> {
        val safeUid = userId.ifBlank { "default" }
        return prefs?.getStringSet("attended_events_$safeUid", emptySet()) ?: emptySet()
    }

    fun isEventAttended(eventId: String, userId: String = getCurrentUserId() ?: "default"): Boolean {
        if (eventId.isBlank()) return false
        val safeUid = userId.ifBlank { "default" }
        return getAttendedEventIds(safeUid).contains(eventId)
    }

    fun setEventAttended(eventId: String, attended: Boolean, userId: String = getCurrentUserId() ?: "default") {
        if (eventId.isBlank()) return
        val safeUid = userId.ifBlank { "default" }
        val set = getAttendedEventIds(safeUid).toMutableSet()
        if (attended) {
            set.add(eventId)
        } else {
            set.remove(eventId)
        }
        prefs?.edit()?.putStringSet("attended_events_$safeUid", set)?.apply()
    }

    // --- EVENT CREATION TRACKING ---

    fun getCreatedEventIds(userId: String = getCurrentUserId() ?: "default"): Set<String> {
        val safeUid = userId.ifBlank { "default" }
        return prefs?.getStringSet("created_events_$safeUid", emptySet()) ?: emptySet()
    }

    fun isEventCreatedByMe(eventId: String, userId: String = getCurrentUserId() ?: "default"): Boolean {
        if (eventId.isBlank()) return false
        val safeUid = userId.ifBlank { "default" }
        return getCreatedEventIds(safeUid).contains(eventId)
    }

    fun addCreatedEventId(eventId: String, userId: String = getCurrentUserId() ?: "default") {
        if (eventId.isBlank()) return
        val safeUid = userId.ifBlank { "default" }
        val set = getCreatedEventIds(safeUid).toMutableSet()
        set.add(eventId)
        prefs?.edit()?.putStringSet("created_events_$safeUid", set)?.apply()
    }

    private var testUserId: String? = null
    private var testUserEmail: String? = null
    private var testUserName: String? = null
    private var testAccessToken: String? = null

    fun setTestSession(userId: String?, email: String?, name: String? = null, token: String? = null) {
        testUserId = userId
        testUserEmail = email?.trim()?.lowercase()
        testUserName = name?.trim()
        testAccessToken = token
    }

    fun clearTestSession() {
        testUserId = null
        testUserEmail = null
        testUserName = null
        testAccessToken = null
    }

    fun getAccessToken(): String? = testAccessToken ?: prefs?.getString(KEY_ACCESS_TOKEN, null)
    fun getCurrentUserId(): String? = testUserId ?: prefs?.getString(KEY_USER_ID, null)
    fun getCurrentUserEmail(): String? = testUserEmail ?: prefs?.getString(KEY_USER_EMAIL, null)
    fun getCurrentUserName(): String? = testUserName ?: prefs?.getString(KEY_USER_NAME, null)

    fun clearSession() {
        clearTestSession()
        prefs?.edit()?.remove(KEY_ACCESS_TOKEN)?.remove(KEY_USER_ID)?.remove(KEY_USER_EMAIL)?.remove(KEY_USER_NAME)?.apply()
    }

    fun isLoggedIn(): Boolean = !getCurrentUserId().isNullOrBlank()

    fun isJwtExpired(token: String?): Boolean {
        if (token.isNullOrBlank()) return true
        try {
            val parts = token.split(".")
            if (parts.size >= 2) {
                val payload = String(
                    android.util.Base64.decode(
                        parts[1],
                        android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
                    ),
                    Charsets.UTF_8
                )
                val expIdx = payload.indexOf("\"exp\":")
                if (expIdx != -1) {
                    val numStr = payload.substring(expIdx + 6).trim().takeWhile { it.isDigit() }
                    val expSec = numStr.toLongOrNull() ?: 0L
                    val nowSec = System.currentTimeMillis() / 1000L
                    return expSec <= (nowSec + 15L) // Buffer 15 seconds
                }
            }
        } catch (_: Exception) {}
        return false
    }
}
