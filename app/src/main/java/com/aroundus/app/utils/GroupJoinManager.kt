package com.aroundus.app.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * GroupJoinManager tracks when the user joined groups so they can be
 * sorted from newest joined to lowest (oldest).
 */
object GroupJoinManager {

    private const val PREFS_NAME = "aroundus_group_join_prefs"
    private const val KEY_JOINED_PREFIX = "joined_group_"

    private fun getPrefs(context: Context?): SharedPreferences? {
        return context?.applicationContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Records the timestamp when a user joined a group.
     */
    fun recordGroupJoined(context: Context?, groupId: String, timestampMs: Long = System.currentTimeMillis()) {
        if (groupId.isBlank()) return
        getPrefs(context)?.edit()?.putLong(KEY_JOINED_PREFIX + groupId, timestampMs)?.apply()
    }

    /**
     * Returns the timestamp when the user joined the group, or 0L if not recorded.
     */
    fun getGroupJoinedTime(context: Context?, groupId: String): Long {
        if (groupId.isBlank()) return 0L
        return getPrefs(context)?.getLong(KEY_JOINED_PREFIX + groupId, 0L) ?: 0L
    }

    /**
     * Removes the join record when user leaves a group.
     */
    fun removeGroupJoined(context: Context?, groupId: String) {
        if (groupId.isBlank()) return
        getPrefs(context)?.edit()?.remove(KEY_JOINED_PREFIX + groupId)?.apply()
    }
}
