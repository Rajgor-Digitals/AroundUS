package com.aroundus.app.repository

import android.content.Context
import com.aroundus.app.R
import com.aroundus.app.models.Notification
import com.aroundus.app.network.SupabaseClient
import com.aroundus.app.utils.CryptoManager
import com.aroundus.app.utils.ProductMessageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Production Repository for Notifications in AroundUS.
 * Synchronizes real backend events (community announcements, direct neighbor inquiries, sent/received messages)
 * into a live, interactive notification feed with read/unread tracking.
 */
class NotificationRepository {

    private val postRepository = PostRepository()
    private val userRepository = UserRepository()

    companion object {
        private const val PREFS_NAME = "aroundus_notifications_prefs"
        private const val KEY_READ_IDS = "read_notification_ids"
        private const val KEY_CACHED_MSG_UNREAD = "cached_msg_unread_count"
    }

    private fun getReadNotificationIds(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_READ_IDS, emptySet()) ?: emptySet()
    }

    private fun saveReadNotificationIds(context: Context, ids: Set<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_READ_IDS, ids).apply()
    }

    private fun formatTimestamp(timestampStr: String?): String {
        val millis = timestampStr?.toLongOrNull() ?: return "Recent"
        val now = System.currentTimeMillis()
        val diff = now - millis
        return when {
            diff < 60_000L -> "Just now"
            diff < 3600_000L -> "${diff / 60_000L}m ago"
            diff < 86400_000L -> "${diff / 3600_000L}h ago"
            diff < 604800_000L -> "${diff / 86400_000L}d ago"
            else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(millis))
        }
    }

    suspend fun fetchNotifications(context: Context): NetworkResult<List<Notification>> =
        withContext(Dispatchers.IO) {
            try {
                val readIds = getReadNotificationIds(context)
                val notifications = mutableListOf<Notification>()

                // 1. Fetch real live community announcements from Supabase
                when (val postRes = postRepository.fetchPostsWithAuthors(context)) {
                    is NetworkResult.Success -> {
                        val announcements = postRes.data.filter {
                            it.category.equals("Announcement", ignoreCase = true) ||
                            it.category.equals("Announcements", ignoreCase = true) ||
                            it.category.equals("Alert", ignoreCase = true) ||
                            it.category.equals("Notice", ignoreCase = true)
                        }

                        for (post in announcements) {
                            val notifId = "ann_${post.id}"
                            val isRead = readIds.contains(notifId)
                            val author = post.userName.takeIf { it.isNotBlank() } ?: "Community Admin"
                            notifications.add(
                                Notification(
                                    id = notifId,
                                    type = "announcement",
                                    text = "$author posted an announcement: \"${post.text.take(65)}\"",
                                    avatarRes = R.drawable.ic_notifications,
                                    time = post.timeAgo.ifBlank { "Recent" },
                                    isUnread = !isRead,
                                    targetId = post.id,
                                    timestampMs = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                    is NetworkResult.Error -> {}
                }

                // 2. Fetch real live direct conversation messages (both sent & received)
                val currentUserId = userRepository.resolveValidAuthorId().ifBlank {
                    SupabaseClient.getCurrentUserId() ?: ""
                }

                if (currentUserId.isNotBlank()) {
                    try {
                        val service = SupabaseClient.getService()
                        val messagesResp = service.getAllMessages()
                        val usersResp = service.getUsers()

                        val allMessages = if (messagesResp.isSuccessful) messagesResp.body() ?: emptyList() else emptyList()
                        val allUsers = if (usersResp.isSuccessful) usersResp.body() ?: emptyList() else emptyList()
                        val usersMap = allUsers.associateBy { it.userId }

                        // Filter direct messages involving current user
                        val directMessages = allMessages.filter {
                            it.groupId.isNullOrBlank() &&
                            (it.senderId == currentUserId || it.receiverId == currentUserId)
                        }

                        // Group by conversation partner
                        val grouped = directMessages.groupBy {
                            if (it.senderId == currentUserId) it.receiverId else it.senderId
                        }

                        for ((partnerId, msgs) in grouped) {
                            if (partnerId.isNullOrBlank() || partnerId == currentUserId) continue

                            val partnerUser = usersMap[partnerId]
                            val partnerName = partnerUser?.name?.takeIf { it.isNotBlank() } ?: "Neighbor"
                            val partnerAvatar = partnerUser?.profilePhoto

                            val latestMsg = msgs.maxByOrNull { it.timestamp.toLongOrNull() ?: 0L } ?: continue
                            val isSentByMe = latestMsg.senderId == currentUserId

                            // Decrypt text
                            val sharedSecret = CryptoManager.generateSharedSecret(currentUserId, partnerId)
                            val key = CryptoManager.deriveKey(sharedSecret)
                            val plainText = if (CryptoManager.isEncrypted(latestMsg.text)) {
                                CryptoManager.decrypt(latestMsg.text, key)
                            } else {
                                latestMsg.text
                            }
                            val cleanPreview = ProductMessageUtils.cleanMessagePreview(plainText)
                            val formattedTime = formatTimestamp(latestMsg.timestamp)
                            val msgTimestamp = latestMsg.timestamp.toLongOrNull() ?: System.currentTimeMillis()

                            val notifId = "msg_${latestMsg.messageId}"
                            // Outgoing messages don't add to unread badge, but display in the feed
                            val isUnread = if (isSentByMe) false else !readIds.contains(notifId)

                            val notifText = if (isSentByMe) {
                                "You sent a message to $partnerName: \"${cleanPreview.take(60)}\""
                            } else {
                                "$partnerName sent you a message: \"${cleanPreview.take(60)}\""
                            }

                            notifications.add(
                                Notification(
                                    id = notifId,
                                    type = "message",
                                    text = notifText,
                                    avatarRes = R.drawable.shape_avatar_placeholder,
                                    time = formattedTime,
                                    isUnread = isUnread,
                                    targetId = partnerId,
                                    avatarUrl = partnerAvatar,
                                    timestampMs = msgTimestamp
                                )
                            )
                        }
                    } catch (_: Exception) {}
                }

                // 3. Welcome notification for all community members
                val welcomeId = "sys_welcome"
                notifications.add(
                    Notification(
                        id = welcomeId,
                        type = "follow",
                        text = "Welcome to AroundUS! Connect with verified neighbors in your local community.",
                        avatarRes = R.drawable.ic_check_circle,
                        time = "Community",
                        isUnread = !readIds.contains(welcomeId),
                        timestampMs = 0L
                    )
                )

                // 4. Sort notifications by timestamp descending (newest on top)
                notifications.sortByDescending { it.timestampMs }

                // Cache current unread message count for 0ms initial badge display
                val unreadMessagesCount = notifications.count { it.type == "message" && it.isUnread }
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putInt(KEY_CACHED_MSG_UNREAD, unreadMessagesCount).apply()

                NetworkResult.Success(notifications)
            } catch (e: Exception) {
                NetworkResult.Error(e.localizedMessage ?: "Failed to load notifications", e)
            }
        }

    fun markAsRead(context: Context, notificationId: String) {
        val current = getReadNotificationIds(context).toMutableSet()
        current.add(notificationId)
        saveReadNotificationIds(context, current)
    }

    fun markAllAsRead(context: Context, notificationIds: List<String>) {
        val current = getReadNotificationIds(context).toMutableSet()
        current.addAll(notificationIds)
        saveReadNotificationIds(context, current)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_CACHED_MSG_UNREAD, 0).apply()
    }

    fun markConversationAsRead(context: Context, partnerId: String, latestMessageId: String? = null) {
        val current = getReadNotificationIds(context).toMutableSet()
        if (!latestMessageId.isNullOrBlank()) {
            current.add("msg_$latestMessageId")
        }
        current.add("conv_$partnerId")
        saveReadNotificationIds(context, current)
    }

    fun getUnreadCount(context: Context, notifications: List<Notification>): Int {
        val readIds = getReadNotificationIds(context)
        return notifications.count { !readIds.contains(it.id) && it.isUnread }
    }

    /**
     * Fast 0ms cache-based unread count to render notification badge immediately.
     */
    fun getCachedUnreadCount(context: Context): Int {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val readIds = getReadNotificationIds(context)
            var count = 0

            // 1. Cached announcements
            val cachedPosts = postRepository.getCachedPosts(context)
            val announcements = cachedPosts.filter {
                it.category.equals("Announcement", ignoreCase = true) ||
                it.category.equals("Announcements", ignoreCase = true) ||
                it.category.equals("Alert", ignoreCase = true) ||
                it.category.equals("Notice", ignoreCase = true)
            }
            count += announcements.count { !readIds.contains("ann_${it.id}") }

            // 2. Cached unread messages count
            count += prefs.getInt(KEY_CACHED_MSG_UNREAD, 0)

            // 3. System welcome notice
            if (!readIds.contains("sys_welcome")) {
                count += 1
            }

            return count
        } catch (_: Exception) {
            return 0
        }
    }

    /**
     * Live asynchronous check of unread notifications from all active sources.
     */
    suspend fun getUnreadNotificationCount(context: Context): Int = withContext(Dispatchers.IO) {
        try {
            when (val res = fetchNotifications(context)) {
                is NetworkResult.Success -> getUnreadCount(context, res.data)
                is NetworkResult.Error -> getCachedUnreadCount(context)
            }
        } catch (_: Exception) {
            getCachedUnreadCount(context)
        }
    }
}
