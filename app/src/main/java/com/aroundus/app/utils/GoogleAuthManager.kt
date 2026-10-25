package com.aroundus.app.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import com.aroundus.app.R
import com.aroundus.app.network.SupabaseConfig
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Manages Google Sign-In with Supabase OAuth:
 * - Initiates Google OAuth consent via Chrome Custom Tabs
 * - Handles deep link callback uri (aroundus://auth/callback)
 * - Decodes JWT authentication tokens and Google user metadata
 */
object GoogleAuthManager {

    const val REDIRECT_URI = "aroundus://auth/callback"

    fun getGoogleOAuthUrl(): String {
        val baseUrl = SupabaseConfig.SUPABASE_URL.trimEnd('/')
        val encodedRedirect = URLEncoder.encode(REDIRECT_URI, "UTF-8")
        return "$baseUrl/auth/v1/authorize?provider=google&redirect_to=$encodedRedirect"
    }

    fun startGoogleSignIn(context: Context) {
        if (!SupabaseConfig.isConfigured()) {
            Toast.makeText(context, "Supabase is not configured.", Toast.LENGTH_SHORT).show()
            return
        }

        // Start local loopback server to intercept localhost:3000 if Supabase falls back
        OAuthLoopbackServer.start(context)

        try {
            val url = getGoogleOAuthUrl()
            val uri = Uri.parse(url)

            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setToolbarColor(ContextCompat.getColor(context, R.color.primary))
                .build()

            customTabsIntent.launchUrl(context, uri)
        } catch (e: Exception) {
            // Fallback to system browser
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(getGoogleOAuthUrl()))
                context.startActivity(intent)
            } catch (err: Exception) {
                OAuthLoopbackServer.stop()
                Toast.makeText(context, "No web browser found to open Google Sign-In", Toast.LENGTH_SHORT).show()
            }
        }
    }

    data class GoogleUserData(
        val userId: String,
        val email: String,
        val name: String,
        val avatarUrl: String? = null
    )

    fun parseJwtPayload(accessToken: String): GoogleUserData? {
        try {
            val parts = accessToken.split(".")
            if (parts.size >= 2) {
                val decodedBytes = Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
                val json = JSONObject(String(decodedBytes, Charsets.UTF_8))
                val uid = json.optString("sub", "")
                val email = json.optString("email", "")

                val meta = json.optJSONObject("user_metadata")
                val name = meta?.optString("full_name")?.ifBlank { null }
                    ?: meta?.optString("name")?.ifBlank { null }
                    ?: email.substringBefore("@")
                val avatar = meta?.optString("avatar_url")?.ifBlank { null }
                    ?: meta?.optString("picture")?.ifBlank { null }

                if (uid.isNotBlank() || email.isNotBlank()) {
                    return GoogleUserData(
                        userId = uid.ifBlank { "usr_" + Math.abs(email.hashCode()) },
                        email = email,
                        name = name,
                        avatarUrl = avatar
                    )
                }
            }
        } catch (_: Exception) {}
        return null
    }
}
