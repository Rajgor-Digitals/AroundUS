package com.aroundus.app.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aroundus.app.databinding.ActivityAuthCallbackBinding
import com.aroundus.app.models.UserEntity
import com.aroundus.app.network.SupabaseClient
import com.aroundus.app.utils.GoogleAuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder

/**
 * Intercepts Supabase OAuth redirects (e.g., aroundus://auth/callback)
 * after Google Authentication.
 *
 * Flow:
 * 1. Extracts tokens from URL fragment or query parameters.
 * 2. Parses user data (ID, email, name, avatar) from token / Supabase Auth.
 * 3. Checks if the user already exists in public.users with a completed profile.
 *    - First time user (or incomplete profile) -> redirects to ProfileSetupActivity.
 *    - Returning user (completed profile) -> redirects to MainActivity.
 */
class AuthCallbackActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthCallbackBinding
    private val service get() = SupabaseClient.getService()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthCallbackBinding.inflate(layoutInflater)
        setContentView(binding.root)

        handleAuthIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthIntent(intent)
    }

    private fun handleAuthIntent(intent: Intent?) {
        val uri = intent?.data
        if (uri == null) {
            Toast.makeText(this, "Authentication failed: No callback data received", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        lifecycleScope.launch {
            processOAuthCallback(uri)
        }
    }

    private suspend fun processOAuthCallback(uri: Uri) {
        var accessToken: String? = uri.getQueryParameter("access_token")
        var refreshToken: String? = uri.getQueryParameter("refresh_token")
        var code: String? = uri.getQueryParameter("code")
        var error: String? = uri.getQueryParameter("error_description") ?: uri.getQueryParameter("error")

        // Parse Fragment (#access_token=...&refresh_token=...)
        val fragment = uri.fragment
        if (!fragment.isNullOrBlank()) {
            val params = fragment.split("&")
            for (p in params) {
                val parts = p.split("=")
                if (parts.size >= 2) {
                    val key = parts[0].trim()
                    val value = try {
                        URLDecoder.decode(parts.subList(1, parts.size).joinToString("="), "UTF-8")
                    } catch (_: Exception) {
                        parts.subList(1, parts.size).joinToString("=")
                    }
                    when (key) {
                        "access_token" -> accessToken = value
                        "refresh_token" -> refreshToken = value
                        "error" -> if (error == null) error = value
                        "error_description" -> error = value
                    }
                }
            }
        }

        if (!error.isNullOrBlank()) {
            Toast.makeText(this, "Google Sign-In canceled or failed: $error", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // If PKCE authorization code was returned, exchange it for session tokens
        if (accessToken.isNullOrBlank() && !code.isNullOrBlank()) {
            binding.tvCallbackSubtitle.text = "Exchanging security code with Supabase..."
            withContext(Dispatchers.IO) {
                try {
                    val resp = service.exchangePkceCode(mapOf("auth_code" to code!!))
                    if (resp.isSuccessful && resp.body() != null) {
                        accessToken = resp.body()?.accessToken
                    }
                } catch (_: Exception) {}
            }
        }

        if (accessToken.isNullOrBlank()) {
            Toast.makeText(this, "Unable to extract authentication token from Google callback.", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        binding.tvCallbackSubtitle.text = "Verifying profile and neighborhood status..."

        withContext(Dispatchers.IO) {
            try {
                // 1. Parse Google user data from JWT token
                val jwtData = GoogleAuthManager.parseJwtPayload(accessToken!!)
                var userId = jwtData?.userId ?: ""
                var email = jwtData?.email ?: ""
                var name = jwtData?.name ?: ""
                var avatarUrl = jwtData?.avatarUrl

                // 2. Query Supabase Auth API if metadata is incomplete
                if (userId.isBlank() || email.isBlank()) {
                    try {
                        val authUserResp = service.getAuthUser("Bearer $accessToken")
                        if (authUserResp.isSuccessful && authUserResp.body() != null) {
                            val authUser = authUserResp.body()!!
                            if (userId.isBlank()) userId = authUser.id
                            if (email.isBlank()) email = authUser.email ?: ""

                            val meta = authUser.userMetadata
                            if (meta != null && name.isBlank()) {
                                name = (meta["full_name"] as? String)
                                    ?: (meta["name"] as? String)
                                    ?: email.substringBefore("@")
                            }
                            if (avatarUrl == null && meta != null) {
                                avatarUrl = (meta["avatar_url"] as? String) ?: (meta["picture"] as? String)
                            }
                        }
                    } catch (_: Exception) {}
                }

                if (userId.isBlank()) {
                    userId = "usr_" + Math.abs(email.hashCode())
                }
                if (name.isBlank()) {
                    name = email.substringBefore("@").replace(".", " ")
                        .split(" ")
                        .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
                }

                // 3. Check public.users database table to see if user has completed profile setup
                var existingUser: UserEntity? = null
                try {
                    val respById = service.getUserById("eq.$userId")
                    if (respById.isSuccessful && !respById.body().isNullOrEmpty()) {
                        existingUser = respById.body()!!.first()
                    } else if (email.isNotBlank()) {
                        val respByEmail = service.getUserByEmail("eq.$email")
                        if (respByEmail.isSuccessful && !respByEmail.body().isNullOrEmpty()) {
                            existingUser = respByEmail.body()!!.first()
                        }
                    }
                } catch (_: Exception) {}

                // Save session in SharedPreferences
                SupabaseClient.saveSession(
                    accessToken = accessToken,
                    userId = userId,
                    email = email,
                    name = existingUser?.name?.ifBlank { name } ?: name
                )
                SupabaseClient.saveAccount(
                    email = email,
                    pass = "",
                    userId = userId,
                    name = existingUser?.name?.ifBlank { name } ?: name
                )
                if (!avatarUrl.isNullOrBlank()) {
                    SupabaseClient.saveAvatarPath(email, avatarUrl)
                    SupabaseClient.saveAvatarPath(userId, avatarUrl)
                }

                // Determine if this is first-time signup/signin or returning user:
                // User is considered FIRST TIME if:
                // 1) User does NOT exist in public.users table yet, OR
                // 2) User exists but has not completed their profile setup (location/neighborhood is missing)
                val isFirstTime = existingUser == null || existingUser.location.isNullOrBlank()

                withContext(Dispatchers.Main) {
                    if (isFirstTime) {
                        Toast.makeText(
                            this@AuthCallbackActivity,
                            "Google Sign-In successful! Please complete your profile.",
                            Toast.LENGTH_SHORT
                        ).show()

                        val intent = Intent(this@AuthCallbackActivity, ProfileSetupActivity::class.java).apply {
                            putExtra("userId", userId)
                            putExtra("email", email)
                            putExtra("name", name)
                            putExtra("avatarUrl", avatarUrl)
                            putExtra("isFirstTimeGoogle", true)
                        }
                        startActivity(intent)
                        finish()
                    } else {
                        val displayName = existingUser?.name?.ifBlank { name } ?: "Neighbor"
                        Toast.makeText(this@AuthCallbackActivity, "Welcome back, $displayName!", Toast.LENGTH_SHORT).show()

                        val intent = Intent(this@AuthCallbackActivity, MainActivity::class.java)
                        startActivity(intent)
                        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                        finishAffinity()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AuthCallbackActivity, "Sign-in error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    startActivity(Intent(this@AuthCallbackActivity, LoginActivity::class.java))
                    finish()
                }
            }
        }
    }
}
