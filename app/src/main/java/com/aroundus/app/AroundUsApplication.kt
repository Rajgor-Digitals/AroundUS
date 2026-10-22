package com.aroundus.app

import android.app.Application
import com.aroundus.app.network.SupabaseClient
import com.aroundus.app.repository.PostRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AroundUsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SupabaseClient.init(this)
        com.aroundus.app.utils.UserLocationManager.init(this)
        com.aroundus.app.utils.UserPresenceManager.init(this)
        com.aroundus.app.utils.MessageNotificationManager.initNotificationChannel(this)

        // Asynchronously pre-warm Retrofit client, OkHttp connection pool, Post cache, and start message notification listener
        CoroutineScope(Dispatchers.IO).launch {
            try {
                PostRepository().fetchPostsWithAuthors(applicationContext)
            } catch (_: Exception) {}
            com.aroundus.app.utils.MessageNotificationManager.startListeningForIncomingMessages(
                applicationContext,
                CoroutineScope(Dispatchers.IO)
            )
        }
    }
}
