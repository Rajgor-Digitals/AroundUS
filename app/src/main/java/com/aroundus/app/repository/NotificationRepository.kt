package com.aroundus.app.repository

import android.content.Context
import com.aroundus.app.R
import com.aroundus.app.models.Notification
import com.aroundus.app.network.SupabaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Production Repository for Notifications in AroundUS.
 * Synchronizes real backend events (community announcements, direct neighbor inquiries, group updates)
 * into a live, interactive notification feed with read/unread tracking.
 */
class NotificationRepository {

    private val postRepository = PostRepository()
    private val messageRepository = MessageRepository()
    private val userRepository = UserRepository()

    companion object {
        private const val PREFS_NAME = "aroundus_notifications_prefs"
        private const val KEY_READ_IDS = "read_notification_ids"
    }

    private fun getReadNotificationIds(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_READ_IDS, emptySet()) ?: emptySet()
    }

    private fun saveReadNotificationIds(context: Context, ids: Set<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_READ_IDS, ids).apply()
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
                                    type = "event",
                                    text = "$author posted an important update: \"${post.text.take(65)}\"",
                                    avatarRes = R.drawable.ic_notifications,
                                    time = post.timeAgo.ifBlank { "Recent" },
                                    isUnread = !isRead
                                )
                            )
                        }
                    }
                    is NetworkResult.Error -> {}
                }

                // 2. Fetch real live conversation updates
                when (val convRes = messageRepository.fetchUserConversations()) {
                    is NetworkResult.Success -> {
                        for (chat in convRes.data) {
                            if (chat.unreadCount > 0 || (chat.lastMessage.isNotBlank() && chat.lastMessage != "Tap to start conversation")) {
                                val notifId = "msg_${chat.id}"
                                val isRead = readIds.contains(notifId)
                                notifications.add(
                                    Notification(
                                        id = notifId,
                                        type = "comment",
                                        text = "${chat.userName}: ${chat.lastMessage.take(60)}",
                                        avatarRes = if (chat.avatarRes != 0) chat.avatarRes else R.drawable.shape_avatar_placeholder,
                                        time = chat.time.ifBlank { "Recently" },
                                        isUnread = !isRead
                                    )
                                )
                            }
                        }
                    }
                    is NetworkResult.Error -> {}
                }

                // 3. Welcome notification for all registered community members
                val welcomeId = "sys_welcome"
                notifications.add(
                    Notification(
                        id = welcomeId,
                        type = "follow",
                        text = "Welcome to AroundUS! Connect with verified neighbors in your local community.",
                        avatarRes = R.drawable.ic_check_circle,
                        time = "1d ago",
                        isUnread = !readIds.contains(welcomeId)
                    )
                )

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
    }

    fun getUnreadCount(context: Context, notifications: List<Notification>): Int {
        val readIds = getReadNotificationIds(context)
        return notifications.count { !readIds.contains(it.id) }
    }
}
