package com.aroundus.app.utils

import android.content.Context
import android.content.SharedPreferences
import com.aroundus.app.models.MessageEntity
import com.aroundus.app.repository.NotificationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * UnreadMessageManager tracks read/unread status of direct and group conversations
 * across AroundUS with persistent storage, real-time StateFlow updates, and seamless
 * badge synchronization.
 */
object UnreadMessageManager {

    private const val PREFS_NAME = "aroundus_unread_messages_prefs"
    private const val KEY_LAST_READ_USER_PREFIX = "last_read_user_"
    private const val KEY_LAST_READ_GROUP_PREFIX = "last_read_group_"
    private const val KEY_MANUAL_UNREAD_PREFIX = "manual_unread_"
    private const val KEY_READ_MSG_IDS = "read_message_ids_set"
    private const val KEY_CACHED_TOTAL_UNREAD = "cached_total_unread_count"

    private var prefs: SharedPreferences? = null

    private val _totalUnreadCountFlow = MutableStateFlow(0)
    val totalUnreadCountFlow: StateFlow<Int> = _totalUnreadCountFlow.asStateFlow()

    private val directUnreadCounts = ConcurrentHashMap<String, Int>()
    private val groupUnreadCounts = ConcurrentHashMap<String, Int>()

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val cachedTotal = prefs?.getInt(KEY_CACHED_TOTAL_UNREAD, 0) ?: 0
            _totalUnreadCountFlow.value = cachedTotal
        }
    }

    private fun getPreferences(context: Context? = null): SharedPreferences? {
        if (prefs == null && context != null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
        return prefs
    }

    /**
     * Returns the epoch millisecond of when the user last read this 1-on-1 conversation.
     */
    fun getLastReadUserTimestamp(currentUserId: String, partnerId: String, context: Context? = null): Long {
        if (currentUserId.isBlank() || partnerId.isBlank()) return 0L
        val p = getPreferences(context) ?: return 0L
        val key = "${KEY_LAST_READ_USER_PREFIX}${currentUserId}_${partnerId}"
        return p.getLong(key, 0L)
    }

    /**
     * Returns the epoch millisecond of when the user last read this group conversation.
     */
    fun getLastReadGroupTimestamp(currentUserId: String, groupId: String, context: Context? = null): Long {
        if (currentUserId.isBlank() || groupId.isBlank()) return 0L
        val p = getPreferences(context) ?: return 0L
        val key = "${KEY_LAST_READ_GROUP_PREFIX}${currentUserId}_${groupId}"
        return p.getLong(key, 0L)
    }

    /**
     * Checks whether a conversation was manually marked as unread by the user.
     */
    fun isManuallyMarkedUnread(currentUserId: String, targetId: String, context: Context? = null): Boolean {
        if (currentUserId.isBlank() || targetId.isBlank()) return false
        val p = getPreferences(context) ?: return false
        val key = "${KEY_MANUAL_UNREAD_PREFIX}${currentUserId}_${targetId}"
        return p.getBoolean(key, false)
    }

    /**
     * Computes the unread count for a 1-on-1 direct conversation with [partnerId].
     */
    fun getUnreadCountForPartner(
        currentUserId: String,
        partnerId: String,
        messages: List<MessageEntity>,
        context: Context? = null
    ): Int {
        if (currentUserId.isBlank() || partnerId.isBlank()) return 0

        val isManual = isManuallyMarkedUnread(currentUserId, partnerId, context)
        val lastReadMs = getLastReadUserTimestamp(currentUserId, partnerId, context)

        // Incoming messages from this partner to current user
        val incoming = messages.filter {
            it.senderId == partnerId &&
            (it.receiverId == currentUserId || it.receiverId.isNullOrBlank()) &&
            it.groupId.isNullOrBlank()
        }

        if (incoming.isEmpty()) {
            val count = if (isManual) 1 else 0
            directUnreadCounts[partnerId] = count
            recalculateTotalUnread()
            return count
        }

        val unreadMsgs = incoming.filter { msg ->
            val timestamp = msg.timestamp.toLongOrNull() ?: 0L
            timestamp > lastReadMs
        }

        val count = if (isManual && unreadMsgs.isEmpty()) 1 else unreadMsgs.size
        directUnreadCounts[partnerId] = count
        recalculateTotalUnread()
        return count
    }

    /**
     * Computes the unread count for a group conversation [groupId].
     */
    fun getUnreadCountForGroup(
        currentUserId: String,
        groupId: String,
        messages: List<MessageEntity>,
        context: Context? = null
    ): Int {
        if (currentUserId.isBlank() || groupId.isBlank()) return 0

        val isManual = isManuallyMarkedUnread(currentUserId, groupId, context)
        val lastReadMs = getLastReadGroupTimestamp(currentUserId, groupId, context)

        // Messages from other members in this group
        val incoming = messages.filter {
            it.groupId == groupId && it.senderId != currentUserId && it.senderId.isNotBlank()
        }

        if (incoming.isEmpty()) {
            val count = if (isManual) 1 else 0
            groupUnreadCounts[groupId] = count
            recalculateTotalUnread()
            return count
        }

        // If user never opened group before, default lastRead to group latest - 1 or 0
        val effectiveLastRead = if (lastReadMs <= 0L) {
            // Treat last 24h as potential unread, or 0
            val maxMsgTime = incoming.mapNotNull { it.timestamp.toLongOrNull() }.maxOrNull() ?: 0L
            if (maxMsgTime > 0L) maxMsgTime - (3600_000L * 24L) else 0L
        } else {
            lastReadMs
        }

        val unreadMsgs = incoming.filter { msg ->
            val timestamp = msg.timestamp.toLongOrNull() ?: 0L
            timestamp > effectiveLastRead
        }

        val count = if (isManual && unreadMsgs.isEmpty()) 1 else unreadMsgs.size
        groupUnreadCounts[groupId] = count
        recalculateTotalUnread()
        return count
    }

    /**
     * Marks a 1-on-1 direct conversation as read up to [readUntilMs].
     */
    fun markConversationAsRead(
        context: Context,
        currentUserId: String,
        partnerId: String,
        readUntilMs: Long = System.currentTimeMillis()
    ) {
        if (currentUserId.isBlank() || partnerId.isBlank()) return
        val p = getPreferences(context) ?: return

        val userKey = "${KEY_LAST_READ_USER_PREFIX}${currentUserId}_${partnerId}"
        val manualKey = "${KEY_MANUAL_UNREAD_PREFIX}${currentUserId}_${partnerId}"

        val currentVal = p.getLong(userKey, 0L)
        val targetVal = maxOf(currentVal, readUntilMs)

        p.edit()
            .putLong(userKey, targetVal)
            .putBoolean(manualKey, false)
            .apply()

        directUnreadCounts[partnerId] = 0
        recalculateTotalUnread()

        // Sync with notification repository so message alerts clear
        try {
            val notifRepo = NotificationRepository()
            notifRepo.markAsRead(context, "msg_$partnerId")
        } catch (_: Exception) {}
    }

    /**
     * Manually marks a 1-on-1 direct conversation as unread.
     */
    fun markConversationAsUnread(context: Context, currentUserId: String, partnerId: String) {
        if (currentUserId.isBlank() || partnerId.isBlank()) return
        val p = getPreferences(context) ?: return

        val manualKey = "${KEY_MANUAL_UNREAD_PREFIX}${currentUserId}_${partnerId}"
        p.edit().putBoolean(manualKey, true).apply()

        val existing = directUnreadCounts[partnerId] ?: 0
        directUnreadCounts[partnerId] = maxOf(1, existing)
        recalculateTotalUnread()
    }

    /**
     * Marks a group conversation as read up to [readUntilMs].
     */
    fun markGroupAsRead(
        context: Context,
        currentUserId: String,
        groupId: String,
        readUntilMs: Long = System.currentTimeMillis()
    ) {
        if (currentUserId.isBlank() || groupId.isBlank()) return
        val p = getPreferences(context) ?: return

        val groupKey = "${KEY_LAST_READ_GROUP_PREFIX}${currentUserId}_${groupId}"
        val manualKey = "${KEY_MANUAL_UNREAD_PREFIX}${currentUserId}_${groupId}"

        val currentVal = p.getLong(groupKey, 0L)
        val targetVal = maxOf(currentVal, readUntilMs)

        p.edit()
            .putLong(groupKey, targetVal)
            .putBoolean(manualKey, false)
            .apply()

        groupUnreadCounts[groupId] = 0
        recalculateTotalUnread()
    }

    /**
     * Manually marks a group conversation as unread.
     */
    fun markGroupAsUnread(context: Context, currentUserId: String, groupId: String) {
        if (currentUserId.isBlank() || groupId.isBlank()) return
        val p = getPreferences(context) ?: return

        val manualKey = "${KEY_MANUAL_UNREAD_PREFIX}${currentUserId}_${groupId}"
        p.edit().putBoolean(manualKey, true).apply()

        val existing = groupUnreadCounts[groupId] ?: 0
        groupUnreadCounts[groupId] = maxOf(1, existing)
        recalculateTotalUnread()
    }

    /**
     * Marks all conversations (direct + group) as read in one action.
     */
    fun markAllAsRead(
        context: Context,
        currentUserId: String,
        directPartnerIds: List<String>,
        groupIds: List<String>
    ) {
        val now = System.currentTimeMillis()
        directPartnerIds.forEach { partnerId ->
            markConversationAsRead(context, currentUserId, partnerId, now)
        }
        groupIds.forEach { groupId ->
            markGroupAsRead(context, currentUserId, groupId, now)
        }
        directUnreadCounts.clear()
        groupUnreadCounts.clear()
        _totalUnreadCountFlow.value = 0
        getPreferences(context)?.edit()?.putInt(KEY_CACHED_TOTAL_UNREAD, 0)?.apply()
    }

    /**
     * Returns total unread messages count specifically in 1-on-1 direct chats.
     */
    fun getDirectChatsUnreadTotal(): Int = directUnreadCounts.values.sum()

    /**
     * Returns total unread messages count specifically in group chats.
     */
    fun getGroupsUnreadTotal(): Int = groupUnreadCounts.values.sum()

    /**
     * Returns overall total unread count across all conversations.
     */
    fun getTotalUnreadCount(): Int = _totalUnreadCountFlow.value

    private fun recalculateTotalUnread() {
        val total = directUnreadCounts.values.sum() + groupUnreadCounts.values.sum()
        _totalUnreadCountFlow.value = total
        prefs?.edit()?.putInt(KEY_CACHED_TOTAL_UNREAD, total)?.apply()
    }
}

