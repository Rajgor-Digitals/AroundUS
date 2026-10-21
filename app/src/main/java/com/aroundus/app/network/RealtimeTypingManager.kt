package com.aroundus.app.network

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.aroundus.app.utils.UserPresenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * RealtimeTypingManager handles live conversation synchronization:
 * - Live typing indicators (staggered dots & "typing..." header)
 * - Message seen receipts (single check -> double green check)
 * - User presence & last active tracking (Phoenix Presence)
 */
class RealtimeTypingManager(
    private val currentUserId: String,
    private val recipientUserId: String,
    private val onTypingStatusChanged: (userId: String, isTyping: Boolean) -> Unit
) {

    var onMessageSeenReceived: ((viewerId: String, seenAtMs: Long, lastSeenMessageId: String?) -> Unit)? = null
    var onPartnerPresenceChanged: ((isOnline: Boolean, lastActiveMs: Long) -> Unit)? = null

    companion object {
        private const val TAG = "RealtimeChat"
        private const val HEARTBEAT_INTERVAL_MS = 20_000L
        private const val RECONNECT_DELAY_MS = 3_000L

        fun createChannelRoomName(userIdA: String, userIdB: String): String {
            val sorted = listOf(userIdA.trim(), userIdB.trim()).sorted()
            return "chat_${sorted[0]}_${sorted[1]}"
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val refCounter = AtomicLong(1)

    val channelRoom = createChannelRoomName(currentUserId, recipientUserId)
    private val topic = "realtime:$channelRoom"

    private var webSocket: WebSocket? = null
    private var isConnected = false
    private var isJoined = false
    private var isDestroyed = false
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Keep connection open
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    fun connect() {
        if (isDestroyed || currentUserId.isBlank() || recipientUserId.isBlank()) return
        Log.d(TAG, "Connecting Realtime WebSocket for room: $channelRoom")

        val baseUrl = SupabaseConfig.SUPABASE_URL
            .replace("https://", "wss://")
            .replace("http://", "ws://")
            .trimEnd('/')
        val wsUrl = "$baseUrl/realtime/v1/websocket?apikey=${SupabaseConfig.SUPABASE_ANON_KEY}&vsn=1.0.0"

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected successfully. Joining topic: $topic")
                isConnected = true

                // Send Phoenix Join Channel with presence & broadcast support
                val joinMsg = JSONObject().apply {
                    put("topic", topic)
                    put("event", "phx_join")
                    put("payload", JSONObject().apply {
                        put("config", JSONObject().apply {
                            put("presence", JSONObject().apply {
                                put("key", currentUserId)
                            })
                            put("broadcast", JSONObject().apply {
                                put("ack", false)
                                put("self", false)
                            })
                        })
                    })
                    put("ref", refCounter.getAndIncrement().toString())
                }
                webSocket.send(joinMsg.toString())
                isJoined = true
                startHeartbeat()

                // Track self presence as online
                trackSelfPresence()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code / $reason")
                isConnected = false
                isJoined = false
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code / $reason")
                isConnected = false
                isJoined = false
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "WebSocket failure: ${t.localizedMessage}")
                isConnected = false
                isJoined = false
                scheduleReconnect()
            }
        })
    }

    private fun handleIncomingMessage(text: String) {
        try {
            val json = JSONObject(text)
            val event = json.optString("event")

            when (event) {
                "broadcast" -> {
                    val payloadObj = json.optJSONObject("payload") ?: return
                    val broadcastEvent = payloadObj.optString("event")
                    val innerPayload = payloadObj.optJSONObject("payload") ?: return

                    when (broadcastEvent) {
                        "typing" -> {
                            val senderId = innerPayload.optString("userId")
                            val isTyping = innerPayload.optBoolean("isTyping", false)
                            if (senderId.isNotBlank() && senderId != currentUserId) {
                                mainHandler.post {
                                    onTypingStatusChanged(senderId, isTyping)
                                }
                            }
                        }
                        "message_seen" -> {
                            val viewerId = innerPayload.optString("viewerId")
                            val seenAt = innerPayload.optLong("seenAt", System.currentTimeMillis())
                            val lastSeenMessageId = innerPayload.optString("lastSeenMessageId").takeIf { it.isNotBlank() }

                            if (viewerId.isNotBlank() && viewerId == recipientUserId) {
                                UserPresenceManager.recordConversationSeen(channelRoom, seenAt)
                                mainHandler.post {
                                    onMessageSeenReceived?.invoke(viewerId, seenAt, lastSeenMessageId)
                                }
                            }
                        }
                    }
                }

                "presence_state" -> {
                    // Phoenix presence state listing all users currently connected to this conversation room
                    val stateObj = json.optJSONObject("payload") ?: return
                    val isPartnerPresent = stateObj.has(recipientUserId)
                    var partnerOnlineAt = 0L

                    if (isPartnerPresent) {
                        val partnerData = stateObj.optJSONObject(recipientUserId)
                        val metas = partnerData?.optJSONArray("metas")
                        val metaObj = metas?.optJSONObject(0)
                        partnerOnlineAt = metaObj?.optLong("online_at", System.currentTimeMillis()) ?: System.currentTimeMillis()
                    }

                    UserPresenceManager.setUserOnline(recipientUserId, isPartnerPresent, partnerOnlineAt)
                    mainHandler.post {
                        onPartnerPresenceChanged?.invoke(isPartnerPresent, partnerOnlineAt)
                    }
                }

                "presence_diff" -> {
                    val payloadObj = json.optJSONObject("payload") ?: return
                    val joins = payloadObj.optJSONObject("joins")
                    val leaves = payloadObj.optJSONObject("leaves")

                    if (joins != null && joins.has(recipientUserId)) {
                        val now = System.currentTimeMillis()
                        UserPresenceManager.setUserOnline(recipientUserId, true, now)
                        mainHandler.post {
                            onPartnerPresenceChanged?.invoke(true, now)
                        }
                    } else if (leaves != null && leaves.has(recipientUserId)) {
                        val now = System.currentTimeMillis()
                        UserPresenceManager.setUserOnline(recipientUserId, false, now)
                        mainHandler.post {
                            onPartnerPresenceChanged?.invoke(false, now)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing incoming realtime event", e)
        }
    }

    private fun trackSelfPresence() {
        try {
            val presenceMsg = JSONObject().apply {
                put("topic", topic)
                put("event", "presence")
                put("payload", JSONObject().apply {
                    put("type", "presence")
                    put("event", "track")
                    put("payload", JSONObject().apply {
                        put("user_id", currentUserId)
                        put("online_at", System.currentTimeMillis())
                    })
                })
                put("ref", refCounter.getAndIncrement().toString())
            }
            webSocket?.send(presenceMsg.toString())
            UserPresenceManager.recordUserActive(currentUserId)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to track presence", e)
        }
    }

    fun sendTyping(isTyping: Boolean) {
        if (!isConnected || webSocket == null) return

        try {
            val broadcastMsg = JSONObject().apply {
                put("topic", topic)
                put("event", "broadcast")
                put("payload", JSONObject().apply {
                    put("type", "broadcast")
                    put("event", "typing")
                    put("payload", JSONObject().apply {
                        put("userId", currentUserId)
                        put("isTyping", isTyping)
                        put("timestamp", System.currentTimeMillis())
                    })
                })
                put("ref", refCounter.getAndIncrement().toString())
            }

            webSocket?.send(broadcastMsg.toString())
            UserPresenceManager.recordUserActive(currentUserId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send typing event", e)
        }
    }

    /**
     * Broadcasts that current user has viewed the chat and seen the partner's messages.
     */
    fun sendMessageSeen(seenAtMs: Long = System.currentTimeMillis(), latestMessageId: String? = null) {
        if (!isConnected || webSocket == null) return

        try {
            val seenMsg = JSONObject().apply {
                put("topic", topic)
                put("event", "broadcast")
                put("payload", JSONObject().apply {
                    put("type", "broadcast")
                    put("event", "message_seen")
                    put("payload", JSONObject().apply {
                        put("viewerId", currentUserId)
                        put("recipientId", recipientUserId)
                        put("seenAt", seenAtMs)
                        if (!latestMessageId.isNullOrBlank()) {
                            put("lastSeenMessageId", latestMessageId)
                        }
                    })
                })
                put("ref", refCounter.getAndIncrement().toString())
            }

            webSocket?.send(seenMsg.toString())
            UserPresenceManager.recordConversationSeen(channelRoom, seenAtMs)
            UserPresenceManager.recordUserActive(currentUserId)
            Log.d(TAG, "Sent message_seen broadcast: seenAt=$seenAtMs")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message_seen event", e)
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive && isConnected) {
                delay(HEARTBEAT_INTERVAL_MS)
                try {
                    val hbMsg = JSONObject().apply {
                        put("topic", "phoenix")
                        put("event", "heartbeat")
                        put("payload", JSONObject())
                        put("ref", "hb_${refCounter.getAndIncrement()}")
                    }
                    webSocket?.send(hbMsg.toString())
                    UserPresenceManager.recordUserActive(currentUserId)
                } catch (e: Exception) {
                    Log.w(TAG, "Heartbeat failed", e)
                }
            }
        }
    }

    private fun scheduleReconnect() {
        if (isDestroyed) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(RECONNECT_DELAY_MS)
            if (!isDestroyed && !isConnected) {
                Log.d(TAG, "Reconnecting Realtime WebSocket...")
                connect()
            }
        }
    }

    fun disconnect() {
        isDestroyed = true
        heartbeatJob?.cancel()
        reconnectJob?.cancel()

        try {
            if (isJoined && webSocket != null) {
                val leaveMsg = JSONObject().apply {
                    put("topic", topic)
                    put("event", "phx_leave")
                    put("payload", JSONObject())
                    put("ref", refCounter.getAndIncrement().toString())
                }
                webSocket?.send(leaveMsg.toString())
            }
        } catch (_: Exception) {}

        try {
            webSocket?.close(1000, "Leaving conversation")
        } catch (_: Exception) {}

        webSocket = null
        isConnected = false
        isJoined = false
    }
}
