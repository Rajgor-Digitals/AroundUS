package com.aroundus.app.utils

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * High-performance App-Wide Refresh Manager for AroundUS.
 * Orchestrates reactive event invalidation, stale-data policy detection,
 * and cross-screen synchronization without full-app reloading.
 */
enum class RefreshTarget {
    HOME_FEED,
    GROUPS,
    MARKETPLACE,
    MESSAGES,
    NOTIFICATIONS,
    PROFILE,
    ANNOUNCEMENTS,
    EVENTS,
    ALL
}

object AppRefreshManager {

    private val _refreshEvents = MutableSharedFlow<RefreshTarget>(extraBufferCapacity = 32)
    val refreshEvents: SharedFlow<RefreshTarget> = _refreshEvents.asSharedFlow()

    private val lastRefreshTimestamps = ConcurrentHashMap<RefreshTarget, Long>()
    private val staleFlags = ConcurrentHashMap<RefreshTarget, Boolean>()

    // Data older than 60 seconds is considered stale on tab switch / screen resume
    private const val STALE_THRESHOLD_MS = 60_000L

    /**
     * Flags a target screen's data as stale and notifies any active observers immediately.
     */
    fun notifyDataChanged(target: RefreshTarget) {
        staleFlags[target] = true
        _refreshEvents.tryEmit(target)
        if (target != RefreshTarget.ALL) {
            _refreshEvents.tryEmit(RefreshTarget.ALL)
        }
    }

    /**
     * Marks a target screen as freshly synced with the backend.
     */
    fun markRefreshed(target: RefreshTarget) {
        lastRefreshTimestamps[target] = System.currentTimeMillis()
        staleFlags[target] = false
    }

    /**
     * Returns true if data was modified by an action or has exceeded the stale-data threshold.
     */
    fun isStale(target: RefreshTarget): Boolean {
        if (staleFlags[target] == true) return true
        val last = lastRefreshTimestamps[target] ?: 0L
        return (System.currentTimeMillis() - last) > STALE_THRESHOLD_MS
    }
}
