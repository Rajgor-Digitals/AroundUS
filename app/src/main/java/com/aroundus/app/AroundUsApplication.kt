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

        // Asynchronously pre-warm Retrofit client, OkHttp connection pool, and Post cache
        CoroutineScope(Dispatchers.IO).launch {
            try {
                PostRepository().fetchPostsWithAuthors(applicationContext)
            } catch (_: Exception) {}
        }
    }
}
