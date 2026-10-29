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
 * Synchronizes real backend events (community announcements, direct neighbor inquiries, sent/received messages, events, help requests)
 * into a live, interactive notification feed with read/unread tracking.
 */
class NotificationRepository {

    private val postRepository = PostRepository()
    private val userRepository = UserRepository()
    private val eventRepository = EventRepository()

    companion object {
        private const val PREFS_NAME = "aroundus_notifications_prefs"
        private const val KEY_READ_IDS = "read_notification_ids"
        private const val KEY_DISMISSED_IDS = "dismissed_notification_ids"
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

    private fun getDismissedNotificationIds(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_DISMISSED_IDS, emptySet()) ?: emptySet()
    }

    private fun saveDismissedNotificationIds(context: Context, ids: Set<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_DISMISSED_IDS, ids).apply()
    }

    private fun formatTimestamp(timestampStr: String?): String {
        val millis = timestampStr?.toLongOrNull() ?: return "Recent"
        val now = System.currentTimeMillis()
        val diff = (now - millis).coerceAtLeast(0L)
        return when {
            diff < 60_000L -> "Just now"
            diff < 3600_000L -> "${diff / 60_000L}m ago"
            diff < 86400_000L -> "${diff / 3600_000L}h ago"
            diff < 604800_000L -> "${diff / 86400_000L}d ago"
            else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(millis))
        }
    }

    private fun parseTimeAgoToTimestamp(timeAgo: String?): Long {
        if (timeAgo.isNullOrBlank()) return System.currentTimeMillis()
        val clean = timeAgo.trim().lowercase()
        val now = System.currentTimeMillis()
        return try {
            when {
                clean.contains("just now") || clean.contains("recent") -> now
                clean.contains("m ago") || clean.endsWith("m") -> {
                    val num = clean.replace(Regex("[^0-9]"), "").toLongOrNull() ?: 5L
                    now - num * 60_000L
                }
                clean.contains("h ago") || clean.endsWith("h") -> {
                    val num = clean.replace(Regex("[^0-9]"), "").toLongOrNull() ?: 1L
                    now - num * 3600_000L
                }
                clean.contains("d ago") || clean.endsWith("d") -> {
                    val num = clean.replace(Regex("[^0-9]"), "").toLongOrNull() ?: 1L
                    now - num * 86400_000L
                }
                else -> now - 172800_000L
            }
        } catch (_: Exception) {
            now - 86400_000L
        }
    }

    suspend fun fetchNotifications(context: Context): NetworkResult<List<Notification>> =
        withContext(Dispatchers.IO) {
            try {
                val readIds = getReadNotificationIds(context)
                val dismissedIds = getDismissedNotificationIds(context)
                val notifications = mutableListOf<Notification>()

                val currentUserId = userRepository.resolveValidAuthorId().ifBlank {
                    SupabaseClient.getCurrentUserId() ?: ""
                }

                // 1. Fetch real live community posts from Supabase (Announcements, Help Requests, Likes/Comments)
                when (val postRes = postRepository.fetchPostsWithAuthors(context)) {
                    is NetworkResult.Success -> {
                        val posts = postRes.data

                        // 1A. Announcements
                        val announcements = posts.filter { post ->
                            post.category.equals("Announcement", ignoreCase = true) ||
                            post.category.equals("Announcements", ignoreCase = true) ||
                            post.category.equals("Alert", ignoreCase = true) ||
                            post.category.equals("Alerts", ignoreCase = true) ||
                            post.category.equals("Notice", ignoreCase = true) ||
                            post.category.equals("Emergency", ignoreCase = true) ||
                            post.category.equals("Community", ignoreCase = true)
                        }

                        for (post in announcements) {
                            val notifId = "ann_${post.id}"
                            if (dismissedIds.contains(notifId)) continue
                            val isRead = readIds.contains(notifId)
                            val author = post.userName.takeIf { it.isNotBlank() } ?: "Neighbor"
                            val timeAgo = post.timeAgo.ifBlank { "Recent" }

                            notifications.add(
                                Notification(
                                    id = notifId,
                                    type = "announcement",
                                    text = "$author posted an announcement: \"${post.text.trim().take(80)}\"",
                                    avatarRes = R.drawable.ic_notifications,
                                    time = timeAgo,
                                    isUnread = !isRead,
                                    targetId = post.id,
                                    avatarUrl = post.userAvatarPath,
                                    timestampMs = parseTimeAgoToTimestamp(post.timeAgo),
                                    title = "$author posted an announcement: \"${post.text.trim().take(60)}\"",
                                    subtitle = "${post.neighborhood.ifBlank { "Bhujpur, Kutch" }}  •  $timeAgo",
                                    location = post.neighborhood.ifBlank { "Bhujpur, Kutch" },
                                    mediaUrl = post.mediaUrl,
                                    authorName = author
                                )
                            )
                        }

                        // 1B. Help Requests
                        val helpPosts = posts.filter { post ->
                            post.category.equals("General Help", ignoreCase = true) ||
                            post.category.equals("Lost & Found", ignoreCase = true) ||
                            post.category.equals("Recommendations", ignoreCase = true) ||
                            post.category.equals("Services", ignoreCase = true) ||
                            post.category.equals("Help & Support", ignoreCase = true) ||
                            post.category.contains("help", ignoreCase = true)
                        }

                        for (post in helpPosts) {
                            val notifId = "help_${post.id}"
                            if (dismissedIds.contains(notifId)) continue
                            val isRead = readIds.contains(notifId)
                            val timeAgo = post.timeAgo.ifBlank { "Recent" }
                            val helpTitle = if (!post.title.isNullOrBlank()) {
                                post.title
                            } else if (post.text.contains("\n")) {
                                post.text.substringBefore("\n").trim()
                            } else {
                                post.text.take(45).ifBlank { post.category }
                            }

                            notifications.add(
                                Notification(
                                    id = notifId,
                                    type = "help",
                                    text = "New help request in your area:\n$helpTitle",
                                    avatarRes = R.drawable.ic_tools_wrench,
                                    time = timeAgo,
                                    isUnread = !isRead,
                                    targetId = post.id,
                                    avatarUrl = post.userAvatarPath,
                                    timestampMs = parseTimeAgoToTimestamp(post.timeAgo),
                                    title = "New help request in your area:\n$helpTitle",
                                    subtitle = "${post.neighborhood.ifBlank { "Bhujpur, Kutch" }}  •  $timeAgo",
                                    location = post.neighborhood.ifBlank { "Bhujpur, Kutch" },
                                    mediaUrl = post.mediaUrl,
                                    authorName = helpTitle
                                )
                            )
                        }

                        // 1C. Likes & Comments on current user's posts
                        if (currentUserId.isNotBlank()) {
                            val myPosts = posts.filter { it.authorId == currentUserId }
                            for (post in myPosts) {
                                if (post.likesCount > 0) {
                                    val notifId = "like_${post.id}"
                                    if (!dismissedIds.contains(notifId)) {
                                        val isRead = readIds.contains(notifId)
                                        val timeAgo = post.timeAgo.ifBlank { "Recent" }
                                        notifications.add(
                                            Notification(
                                                id = notifId,
                                                type = "like",
                                                text = "A neighbor liked your post",
                                                avatarRes = R.drawable.ic_favorite,
                                                time = timeAgo,
                                                isUnread = !isRead,
                                                targetId = post.id,
                                                timestampMs = parseTimeAgoToTimestamp(post.timeAgo) - 1000L,
                                                title = "A neighbor liked your post",
                                                subtitle = timeAgo,
                                                mediaUrl = post.mediaUrl
                                            )
                                        )
                                    }
                                }

                                if (post.commentsCount > 0) {
                                    val notifId = "comment_${post.id}"
                                    if (!dismissedIds.contains(notifId)) {
                                        val isRead = readIds.contains(notifId)
                                        val timeAgo = post.timeAgo.ifBlank { "Recent" }
                                        notifications.add(
                                            Notification(
                                                id = notifId,
                                                type = "comment",
                                                text = "A neighbor commented on your post",
                                                avatarRes = R.drawable.ic_chat_bubble,
                                                time = timeAgo,
                                                isUnread = !isRead,
                                                targetId = post.id,
                                                timestampMs = parseTimeAgoToTimestamp(post.timeAgo) - 2000L,
                                                title = "A neighbor commented on your post",
                                                subtitle = timeAgo,
                                                mediaUrl = post.mediaUrl
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                    is NetworkResult.Error -> {}
                }

                // 2. Fetch real live direct conversation messages (both sent & received)
                if (currentUserId.isNotBlank()) {
                    try {
                        val service = SupabaseClient.getService()
                        val messagesResp = service.getAllMessages()
                        val usersResp = service.getUsers()

                        val allMessages = if (messagesResp.isSuccessful) messagesResp.body() ?: emptyList() else emptyList()
                        val allUsers = if (usersResp.isSuccessful) usersResp.body() ?: emptyList() else emptyList()
                        val usersMap = allUsers.associateBy { it.userId }

                        val directMessages = allMessages.filter {
                            it.groupId.isNullOrBlank() &&
                            (it.senderId == currentUserId || it.receiverId == currentUserId)
                        }

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
                            if (dismissedIds.contains(notifId)) continue
                            val isUnread = if (isSentByMe) false else !readIds.contains(notifId)

                            val notifTitle = if (isSentByMe) {
                                "You sent a message to $partnerName: \"${cleanPreview.take(55)}\""
                            } else {
                                "$partnerName sent you a message: \"${cleanPreview.take(55)}\""
                            }

                            notifications.add(
                                Notification(
                                    id = notifId,
                                    type = "message",
                                    text = notifTitle,
                                    avatarRes = R.drawable.shape_avatar_placeholder,
                                    time = formattedTime,
                                    isUnread = isUnread,
                                    targetId = partnerId,
                                    avatarUrl = partnerAvatar,
                                    timestampMs = msgTimestamp,
                                    title = notifTitle,
                                    subtitle = formattedTime,
                                    authorName = partnerName
                                )
                            )
                        }
                    } catch (_: Exception) {}
                }

                // 3. Fetch real events from Supabase events table
                try {
                    when (val eventsRes = eventRepository.fetchEvents()) {
                        is NetworkResult.Success -> {
                            val events = eventsRes.data
                            for (event in events) {
                                val notifId = "evt_${event.eventId}"
                                if (dismissedIds.contains(notifId)) continue
                                val isRead = readIds.contains(notifId)
                                val eventDateStr = event.date.ifBlank { "Upcoming" }
                                val eventVenue = event.location?.ifBlank { "Nearby" } ?: "Nearby"

                                notifications.add(
                                    Notification(
                                        id = notifId,
                                        type = "event",
                                        text = "New event near you:\n${event.title}",
                                        avatarRes = R.drawable.ic_event,
                                        time = "Upcoming",
                                        isUnread = !isRead,
                                        targetId = event.eventId,
                                        timestampMs = System.currentTimeMillis() - 18000000L,
                                        title = "New event near you:\n${event.title}",
                                        subtitle = "$eventDateStr  •  $eventVenue",
                                        location = eventVenue,
                                        authorName = event.title
                                    )
                                )
                            }
                        }
                        is NetworkResult.Error -> {}
                    }
                } catch (_: Exception) {}

                // 4. Welcome notification for all community members
                val welcomeId = "sys_welcome"
                if (!dismissedIds.contains(welcomeId)) {
                    notifications.add(
                        Notification(
                            id = welcomeId,
                            type = "welcome",
                            text = "Welcome to AroundUS! Connect with verified neighbors in your local community.",
                            avatarRes = R.drawable.ic_check_circle,
                            time = "3h ago",
                            isUnread = !readIds.contains(welcomeId),
                            timestampMs = System.currentTimeMillis() - 10800000L,
                            title = "Welcome to AroundUS!",
                            subtitle = "Connect with verified neighbors in your local community."
                        )
                    )
                }

                // 5. Sort notifications by timestamp descending (newest on top)
                notifications.sortByDescending { it.timestampMs }

                // Cache current unread message count
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

    fun markAsUnread(context: Context, notificationId: String) {
        val current = getReadNotificationIds(context).toMutableSet()
        current.remove(notificationId)
        saveReadNotificationIds(context, current)
    }

    fun dismissNotification(context: Context, notificationId: String) {
        val current = getDismissedNotificationIds(context).toMutableSet()
        current.add(notificationId)
        saveDismissedNotificationIds(context, current)
    }

    fun markAllAsRead(context: Context, notificationIds: List<String>) {
        val current = getReadNotificationIds(context).toMutableSet()
        current.addAll(notificationIds)
        saveReadNotificationIds(context, current)
    }

    fun getUnreadCount(context: Context, notifications: List<Notification>): Int {
        val readIds = getReadNotificationIds(context)
        return notifications.count { !readIds.contains(it.id) && it.isUnread }
    }

    fun getCachedUnreadCount(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_CACHED_MSG_UNREAD, 0)
    }

    suspend fun getUnreadNotificationCount(context: Context): Int {
        return when (val res = fetchNotifications(context)) {
            is NetworkResult.Success -> getUnreadCount(context, res.data)
            is NetworkResult.Error -> getCachedUnreadCount(context)
        }
    }
}
