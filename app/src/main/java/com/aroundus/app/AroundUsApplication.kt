package com.aroundus.app

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.aroundus.app.network.SupabaseClient
import com.aroundus.app.repository.PostRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AroundUsApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Explicitly enforce Light Mode across the entire app
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        val prefs = getSharedPreferences("aroundus_settings_prefs", Context.MODE_PRIVATE)
        if (prefs.contains("dark_mode")) {
            prefs.edit().remove("dark_mode").apply()
        }

        com.aroundus.app.utils.NetworkUtils.init(this)
        SupabaseClient.init(this)
        com.aroundus.app.utils.UserLocationManager.init(this)
        com.aroundus.app.utils.UserPresenceManager.init(this)
        com.aroundus.app.utils.UnreadMessageManager.init(this)
        com.aroundus.app.utils.MessageNotificationManager.initNotificationChannel(this)

        // Production-grade safety: Intercept unhandled network/connectivity exceptions so
        // unexpected offline glitches or DNS failures never crash or kill the app process
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            if (com.aroundus.app.utils.NetworkUtils.isNetworkException(throwable)) {
                android.util.Log.w("AroundUS", "Safely caught unhandled network exception on [${thread.name}]: ${throwable.message}")
            } else {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }

        // Asynchronously pre-warm Retrofit client, OkHttp connection pool, Post cache, and start message notification listener
        val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        appScope.launch {
            try {
                if (com.aroundus.app.utils.NetworkUtils.isNetworkAvailable(applicationContext)) {
                    PostRepository().fetchPostsWithAuthors(applicationContext)
                } else {
                    PostRepository().getCachedPosts(applicationContext)
                }
            } catch (_: Exception) {}

            try {
                com.aroundus.app.utils.MessageNotificationManager.startListeningForIncomingMessages(
                    applicationContext,
                    appScope
                )
            } catch (_: Exception) {}
        }
    }
}
