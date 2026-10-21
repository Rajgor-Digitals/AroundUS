package com.aroundus.app.utils

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * UserPresenceManager tracks user activity, online status, and message seen state
 * across AroundUS.
 */
object UserPresenceManager {

    private const val PREFS_NAME = "aroundus_presence_prefs"
    private const val KEY_LAST_ACTIVE_PREFIX = "last_active_"
    private const val KEY_LAST_SEEN_PREFIX = "last_seen_room_"

    private var prefs: SharedPreferences? = null
    private val onlineUsers = ConcurrentHashMap.newKeySet<String>()
    private val memoryLastActive = ConcurrentHashMap<String, Long>()
    private val memoryLastSeen = ConcurrentHashMap<String, Long>()

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    /**
     * Records that the specified user was active right now.
     */
    fun recordUserActive(userId: String, timestampMs: Long = System.currentTimeMillis()) {
        if (userId.isBlank()) return
        memoryLastActive[userId] = timestampMs
        prefs?.edit()?.putLong(KEY_LAST_ACTIVE_PREFIX + userId, timestampMs)?.apply()
    }

    /**
     * Updates whether a user is currently connected/online.
     */
    fun setUserOnline(userId: String, isOnline: Boolean, timestampMs: Long = System.currentTimeMillis()) {
        if (userId.isBlank()) return
        if (isOnline) {
            onlineUsers.add(userId)
            recordUserActive(userId, timestampMs)
        } else {
            onlineUsers.remove(userId)
            recordUserActive(userId, timestampMs)
        }
    }

    /**
     * Returns true if user is known to be online right now or was active within 90 seconds.
     */
    fun isUserOnline(userId: String): Boolean {
        if (userId.isBlank()) return false
        if (onlineUsers.contains(userId)) return true

        val lastActive = getUserLastActive(userId)
        if (lastActive > 0L) {
            val diff = System.currentTimeMillis() - lastActive
            return diff < 90_000L // 90 seconds threshold
        }
        return false
    }

    /**
     * Returns the epoch millisecond of when the user was last active, or 0 if unknown.
     */
    fun getUserLastActive(userId: String): Long {
        if (userId.isBlank()) return 0L
        memoryLastActive[userId]?.let { return it }

        val stored = prefs?.getLong(KEY_LAST_ACTIVE_PREFIX + userId, 0L) ?: 0L
        if (stored > 0L) {
            memoryLastActive[userId] = stored
        }
        return stored
    }

    /**
     * Formats a last active timestamp into user-friendly text like:
     * - "Active now"
     * - "Active just now"
     * - "Last active 15m ago"
     * - "Last active today at 4:32 PM"
     * - "Last active yesterday at 7:15 PM"
     * - "Last active Oct 20"
     */
    fun formatLastActive(timestampMs: Long, isOnline: Boolean = false): String {
        val now = System.currentTimeMillis()
        if (isOnline || (timestampMs > 0L && now - timestampMs < 90_000L)) {
            return "Active now"
        }

        if (timestampMs <= 0L) {
            return "Offline"
        }

        val diffMs = now - timestampMs
        if (diffMs < 0L) return "Active now"

        val diffSec = diffMs / 1000L
        val diffMin = diffSec / 60L
        val diffHours = diffMin / 60L
        val diffDays = diffHours / 24L

        return when {
            diffMin < 2L -> "Active just now"
            diffMin < 60L -> "Last active ${diffMin}m ago"
            diffHours < 24L -> {
                val cal = Calendar.getInstance().apply { timeInMillis = timestampMs }
                val nowCal = Calendar.getInstance()
                val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestampMs))
                if (cal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR) &&
                    cal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR)) {
                    "Last active today at $timeStr"
                } else {
                    "Last active ${diffHours}h ago"
                }
            }
            diffDays == 1L -> {
                val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestampMs))
                "Last active yesterday at $timeStr"
            }
            diffDays < 7L -> "Last active ${diffDays}d ago"
            else -> {
                val dateStr = SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestampMs))
                "Last active $dateStr"
            }
        }
    }

    // --- MESSAGE SEEN MANAGEMENT ---

    fun recordConversationSeen(roomKey: String, timestampMs: Long = System.currentTimeMillis()) {
        if (roomKey.isBlank()) return
        val currentMax = memoryLastSeen[roomKey] ?: (prefs?.getLong(KEY_LAST_SEEN_PREFIX + roomKey, 0L) ?: 0L)
        if (timestampMs > currentMax) {
            memoryLastSeen[roomKey] = timestampMs
            prefs?.edit()?.putLong(KEY_LAST_SEEN_PREFIX + roomKey, timestampMs)?.apply()
        }
    }

    fun getConversationLastSeen(roomKey: String): Long {
        if (roomKey.isBlank()) return 0L
        memoryLastSeen[roomKey]?.let { return it }

        val stored = prefs?.getLong(KEY_LAST_SEEN_PREFIX + roomKey, 0L) ?: 0L
        if (stored > 0L) {
            memoryLastSeen[roomKey] = stored
        }
        return stored
    }

    /**
     * Determines whether a sent message is seen by the recipient.
     */
    fun isMessageSeen(
        roomKey: String,
        msgTimestampMs: Long,
        hasPartnerReplyAfter: Boolean
    ): Boolean {
        if (hasPartnerReplyAfter) return true
        val lastSeen = getConversationLastSeen(roomKey)
        return lastSeen > 0L && msgTimestampMs <= lastSeen
    }
}
