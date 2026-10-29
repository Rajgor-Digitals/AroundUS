package com.aroundus.app.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.aroundus.app.R
import com.aroundus.app.activities.ConversationActivity
import com.aroundus.app.models.MessageEntity
import com.aroundus.app.network.SupabaseClient
import com.aroundus.app.repository.UserRepository
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages system notifications for incoming messages in AroundUS.
 * Notifies the user whenever someone sends them a direct or community message.
 */
object MessageNotificationManager {

    private const val CHANNEL_ID = "aroundus_messages_channel"
    private const val PREFS_NOTIF = "aroundus_message_notif_prefs"
    private const val KEY_LAST_SEEN_MSG_TIME = "last_seen_msg_time"

    // Holds the ID of the user the client is currently chatting with in ConversationActivity
    // When active, suppress redundant status bar notifications for that specific conversation
    @Volatile
    var activeConversationUserId: String? = null

    // Cache of user names and avatars to minimize network queries during notification delivery
    private val senderCache = ConcurrentHashMap<String, Pair<String, String?>>()

    // Active polling job reference
    private var messagePollingJob: Job? = null

    /**
     * Initializes the Android Notification Channel (Android 8.0+).
     */
    fun initNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            val existing = notificationManager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Messages & Chats",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications for incoming direct messages from neighbors"
                    enableLights(true)
                    lightColor = Color.parseColor("#0D8A3F")
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 200, 100, 200)
                    setShowBadge(true)
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
                }
                notificationManager.createNotificationChannel(channel)
            }
        }
    }

    /**
     * Shows a status bar notification when a message is received.
     */
    fun showMessageNotification(
        context: Context,
        senderId: String,
        senderName: String,
        messageText: String,
        avatarUrl: String? = null
    ) {
        initNotificationChannel(context)

        // Don't show notification if the user is actively inside the conversation with this sender
        if (activeConversationUserId == senderId) {
            return
        }

        // Check POST_NOTIFICATIONS permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        val intent = Intent(context, ConversationActivity::class.java).apply {
            putExtra("userId", senderId)
            putExtra("userName", senderName)
            putExtra("avatarUrl", avatarUrl)
            putExtra("isOnline", true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            senderId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_chat_bubble)
            .setContentTitle(senderName)
            .setContentText(messageText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setColor(Color.parseColor("#0D8A3F"))
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)

        val notificationManager = NotificationManagerCompat.from(context)

        // If avatar URL is provided, try loading bitmap for large icon
        if (!avatarUrl.isNullOrBlank()) {
            try {
                Glide.with(context.applicationContext)
                    .asBitmap()
                    .load(avatarUrl)
                    .circleCrop()
                    .into(object : CustomTarget<Bitmap>() {
                        override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                            try {
                                notificationBuilder.setLargeIcon(resource)
                                notificationManager.notify(senderId.hashCode(), notificationBuilder.build())
                            } catch (_: SecurityException) {}
                        }
                        override fun onLoadCleared(placeholder: Drawable?) {}
                    })
                return
            } catch (_: Exception) {}
        }

        try {
            notificationManager.notify(senderId.hashCode(), notificationBuilder.build())
        } catch (_: SecurityException) {}
    }

    /**
     * Starts background polling for new incoming messages across the app lifecycle.
     */
    fun startListeningForIncomingMessages(context: Context, scope: CoroutineScope) {
        initNotificationChannel(context)

        messagePollingJob?.cancel()
        messagePollingJob = scope.launch(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(PREFS_NOTIF, Context.MODE_PRIVATE)

            // On first start, set last seen timestamp to now so historical messages don't spam notifications
            var lastSeenTimestamp = prefs.getLong(KEY_LAST_SEEN_MSG_TIME, 0L)
            if (lastSeenTimestamp == 0L) {
                lastSeenTimestamp = System.currentTimeMillis() - 1000L
                prefs.edit().putLong(KEY_LAST_SEEN_MSG_TIME, lastSeenTimestamp).apply()
            }

            val userRepo = UserRepository()
            val service = SupabaseClient.getService()

            while (isActive) {
                try {
                    val currentUserId = userRepo.resolveValidAuthorId().ifBlank { SupabaseClient.getCurrentUserId() ?: "" }

                    if (currentUserId.isNotBlank()) {
                        val resp = service.getIncomingMessages(
                            queryFilter = "eq.$currentUserId",
                            order = "timestamp.desc",
                            limit = 15
                        )

                        if (resp.isSuccessful && !resp.body().isNullOrEmpty()) {
                            val incoming = resp.body()!!
                            var maxTimestamp = lastSeenTimestamp

                            // Process in chronological order (oldest to newest)
                            val newMessages = incoming
                                .filter { (it.timestamp.toLongOrNull() ?: 0L) > lastSeenTimestamp }
                                .sortedBy { it.timestamp.toLongOrNull() ?: 0L }

                            for (msg in newMessages) {
                                val msgTime = msg.timestamp.toLongOrNull() ?: 0L
                                if (msgTime > maxTimestamp) {
                                    maxTimestamp = msgTime
                                }

                                if (msg.senderId == currentUserId || msg.senderId.isBlank()) {
                                    continue
                                }

                                // Resolve sender details
                                var senderInfo = senderCache[msg.senderId]
                                if (senderInfo == null) {
                                    try {
                                        val uResp = service.getUserById("eq.${msg.senderId}")
                                        val u = uResp.body()?.firstOrNull()
                                        if (u != null) {
                                            senderInfo = Pair(u.name.ifBlank { "Neighbor" }, u.profilePhoto)
                                            senderCache[msg.senderId] = senderInfo
                                        }
                                    } catch (_: Exception) {}
                                }

                                val senderName = senderInfo?.first ?: "Neighbor"
                                val senderAvatar = senderInfo?.second

                                // Decrypt message if end-to-end encrypted
                                val plainText = if (CryptoManager.isEncrypted(msg.text)) {
                                    try {
                                        if (!msg.groupId.isNullOrBlank()) {
                                            val sharedSecret = CryptoManager.generateGroupSharedSecret(msg.groupId)
                                            val key = CryptoManager.deriveKey(sharedSecret)
                                            CryptoManager.decrypt(msg.text, key)
                                        } else {
                                            val sharedSecret = CryptoManager.generateSharedSecret(currentUserId, msg.senderId)
                                            val key = CryptoManager.deriveKey(sharedSecret)
                                            CryptoManager.decrypt(msg.text, key)
                                        }
                                    } catch (_: Exception) {
                                        "New encrypted message"
                                    }
                                } else {
                                    msg.text
                                }

                                withContext(Dispatchers.Main) {
                                    showMessageNotification(
                                        context = context,
                                        senderId = msg.senderId,
                                        senderName = senderName,
                                        messageText = ProductMessageUtils.cleanMessagePreview(plainText),
                                        avatarUrl = senderAvatar
                                    )
                                }
                            }

                            if (maxTimestamp > lastSeenTimestamp) {
                                lastSeenTimestamp = maxTimestamp
                                prefs.edit().putLong(KEY_LAST_SEEN_MSG_TIME, lastSeenTimestamp).apply()
                                withContext(Dispatchers.Main) {
                                    AppRefreshManager.notifyDataChanged(RefreshTarget.NOTIFICATIONS)
                                    AppRefreshManager.notifyDataChanged(RefreshTarget.MESSAGES)
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}

                delay(3000L) // Poll every 3 seconds
            }
        }
    }

    fun stopListening() {
        messagePollingJob?.cancel()
        messagePollingJob = null
    }
}
