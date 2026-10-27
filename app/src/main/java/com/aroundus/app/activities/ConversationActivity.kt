package com.aroundus.app.activities

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.aroundus.app.R
import com.aroundus.app.adapters.MessageAdapter
import com.aroundus.app.databinding.ActivityConversationBinding
import com.aroundus.app.models.Message
import com.aroundus.app.models.resolveGroupCoverRes
import com.aroundus.app.models.toUIModel
import com.aroundus.app.utils.ProductMessageUtils
import com.aroundus.app.network.RealtimeTypingManager
import com.aroundus.app.network.SupabaseClient
import com.aroundus.app.repository.GroupRepository
import com.aroundus.app.repository.MessageRepository
import com.aroundus.app.repository.NetworkResult
import com.aroundus.app.repository.UserRepository
import com.aroundus.app.utils.AppRefreshManager
import com.aroundus.app.utils.CryptoManager
import com.aroundus.app.utils.MessageNotificationManager
import com.aroundus.app.utils.RefreshTarget
import com.aroundus.app.utils.UserPresenceManager
import com.aroundus.app.utils.loadUserAvatar
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConversationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConversationBinding
    private lateinit var messageAdapter: MessageAdapter
    private val messages = mutableListOf<Message>()
    private val messageRepository = MessageRepository()
    private val userRepository = UserRepository()
    private val groupRepository = GroupRepository()

    private var currentUserId: String = ""
    private var recipientUserId: String = ""
    private var recipientName: String = "Neighbor"
    private var groupId: String = ""
    private var isGroupChat: Boolean = false
    private var isPartnerOnline: Boolean = true
    private var originalStatusText: String = "Active now"

    private var autoRefreshJob: Job? = null
    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    // --- REALTIME TYPING & SEEN INDICATOR STATE ---
    private var realtimeTypingManager: RealtimeTypingManager? = null
    private var lastTypingBroadcastTime: Long = 0L
    private val typingDebounceHandler = Handler(Looper.getMainLooper())
    private val stopTypingRunnable = Runnable { sendTypingBroadcast(false) }

    // --- RECIPIENT TYPING UI & ANIMATION STATE ---
    private var dotsAnimatorSet: AnimatorSet? = null
    private var statusCycleJob: Job? = null
    private var isPartnerTypingNow: Boolean = false
    private val autoHideTypingHandler = Handler(Looper.getMainLooper())
    private val autoHideTypingRunnable = Runnable { hideTypingIndicator() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConversationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        groupId = intent.getStringExtra("groupId") ?: ""
        isGroupChat = groupId.isNotBlank() || intent.getBooleanExtra("isGroup", false)

        recipientUserId = intent.getStringExtra("userId") ?: ""
        val passedUserName = intent.getStringExtra("userName")
        recipientName = if (!passedUserName.isNullOrBlank() && passedUserName != "Group") {
            passedUserName
        } else if (isGroupChat) {
            "Group"
        } else {
            "Neighbor"
        }
        val avatarUrl = intent.getStringExtra("avatarUrl")
        var avatarRes = intent.getIntExtra("avatarRes", 0)
        if (isGroupChat && avatarRes == 0) {
            avatarRes = resolveGroupCoverRes(intent.getStringExtra("category"), recipientName)
        }

        // Resolve initial presence & last active status
        if (isGroupChat) {
            isPartnerOnline = true
            val membersText = intent.getStringExtra("members") ?: "Community members"
            originalStatusText = "Group • $membersText"
        } else {
            val knownOnline = UserPresenceManager.isUserOnline(recipientUserId)
            val knownLastActive = UserPresenceManager.getUserLastActive(recipientUserId)
            isPartnerOnline = if (knownLastActive > 0L) knownOnline else intent.getBooleanExtra("isOnline", true)
            originalStatusText = UserPresenceManager.formatLastActive(knownLastActive, isPartnerOnline)
        }

        setupHeader(recipientName, avatarUrl, avatarRes)
        setupMessagesList()
        setupInputBar()
        loadInitialMessages()
        startAutoRefreshLoop()
        if (isGroupChat && groupId.isNotBlank()) {
            loadGroupDetailsIfGroupChat()
        } else {
            setupRealtimeChat()
        }
    }

    private fun loadGroupDetailsIfGroupChat() {
        if (!isGroupChat || groupId.isBlank()) return
        lifecycleScope.launch {
            when (val res = groupRepository.fetchGroupById(groupId)) {
                is NetworkResult.Success -> {
                    val entity = res.data
                    val uiGroup = entity.toUIModel()
                    if (uiGroup.name.isNotBlank()) {
                        recipientName = uiGroup.name
                    }
                    val displayAvatarUrl = uiGroup.imageUrl
                    val displayAvatarRes = uiGroup.coverRes
                    val membersText = uiGroup.membersText
                    originalStatusText = "Group • $membersText"

                    setupHeader(recipientName, displayAvatarUrl, displayAvatarRes)
                }
                is NetworkResult.Error -> {}
            }
        }
    }

    private fun setupHeader(
        name: String,
        avatarUrl: String?,
        avatarRes: Int
    ) {
        binding.tvUserName.text = name

        if (!avatarUrl.isNullOrBlank()) {
            binding.tvAvatarInitial.visibility = View.GONE
            binding.ivAvatar.visibility = View.VISIBLE
            binding.ivAvatar.loadUserAvatar(
                avatarUrl,
                placeholderRes = if (avatarRes != 0) avatarRes else R.drawable.shape_avatar_placeholder
            )
        } else if (avatarRes != 0) {
            binding.tvAvatarInitial.visibility = View.GONE
            binding.ivAvatar.visibility = View.VISIBLE
            binding.ivAvatar.setImageResource(avatarRes)
        } else {
            binding.ivAvatar.visibility = View.GONE
            binding.tvAvatarInitial.visibility = View.VISIBLE
            binding.tvAvatarInitial.text = name.firstOrNull()?.uppercase() ?: "A"
        }

        updateHeaderStatusUI(isPartnerOnline, originalStatusText)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnCall.setOnClickListener {
            Toast.makeText(this, "Calling $name...", Toast.LENGTH_SHORT).show()
        }

        // Tapping status or more button allows options & testing
        binding.tvStatus.setOnClickListener {
            simulatePartnerTyping()
        }

        binding.btnMore.setOnClickListener { view ->
            showOptionsMenu(view)
        }
    }

    private fun showEncryptionDetailsDialog() {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(
            this,
            R.style.Theme_AroundUS_BottomSheetDialog
        )
        val dialogBinding = com.aroundus.app.databinding.DialogE2eeSecurityBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        val partnerName = recipientName.ifBlank { if (isGroupChat) "Group members" else "Neighbor" }
        val authorId = currentUserId.ifBlank {
            SupabaseClient.getCurrentUserId() ?: ""
        }

        if (isGroupChat) {
            dialogBinding.tvSecuritySubtitle.text = "Group Chat • Secured with 256-bit AES-GCM"
            dialogBinding.tvSecurityDescription.text = "All messages and inquiries in this group are end-to-end encrypted. Only verified members belonging to '$partnerName' hold the decryption key. Neither AroundUS nor third parties can read them."
            val secret = CryptoManager.generateGroupSharedSecret(groupId)
            dialogBinding.tvSafetyNumber.text = CryptoManager.getSafetyNumber(secret)
            dialogBinding.tvSafetyNumberHint.text = "Group verification fingerprint for '$partnerName'. All participants in this group share this cryptographic safety number."
        } else {
            dialogBinding.tvSecuritySubtitle.text = "Direct Chat • Secured with 256-bit AES-GCM"
            dialogBinding.tvSecurityDescription.text = "Messages and product inquiries exchanged with $partnerName are end-to-end encrypted. They are encrypted on your device and can only be decrypted on $partnerName's device. Neither AroundUS nor third parties can read them."
            val secret = CryptoManager.generateSharedSecret(authorId, recipientUserId)
            dialogBinding.tvSafetyNumber.text = CryptoManager.getSafetyNumber(secret)
            dialogBinding.tvSafetyNumberHint.text = "Compare this 60-digit number with $partnerName to verify that end-to-end encryption is active and keys match."
        }

        dialogBinding.btnDoneSecurity.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun updateHeaderStatusUI(isOnline: Boolean, statusText: String) {
        binding.tvStatus.text = statusText
        val activeGreen = Color.parseColor("#16A34A")
        val mutedSlate = Color.parseColor("#64748B")

        if (isOnline) {
            binding.tvStatus.setTextColor(activeGreen)
            binding.dotStatus.visibility = View.VISIBLE
            binding.onlineIndicator.visibility = View.VISIBLE
            binding.dotStatus.backgroundTintList = ColorStateList.valueOf(activeGreen)
        } else {
            binding.tvStatus.setTextColor(mutedSlate)
            binding.dotStatus.visibility = View.GONE
            binding.onlineIndicator.visibility = View.GONE
        }
    }

    private fun showOptionsMenu(anchorView: View) {
        val popup = PopupMenu(this, anchorView)
        popup.menu.add(0, 6, 0, "Verify End-to-End Encryption 🔒")
        popup.menu.add(0, 1, 1, "Simulate Neighbor Typing (Demo)")
        popup.menu.add(0, 2, 2, "Simulate Neighbor Seen Messages (Demo)")
        popup.menu.add(0, 3, 3, "Toggle Neighbor Active / Last Active (Demo)")
        popup.menu.add(0, 4, 4, "Mute Notifications")
        popup.menu.add(0, 5, 5, "Clear Chat")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                6 -> {
                    showEncryptionDetailsDialog()
                    true
                }
                1 -> {
                    simulatePartnerTyping()
                    true
                }
                2 -> {
                    simulatePartnerSeenMessages()
                    true
                }
                3 -> {
                    togglePartnerPresenceDemo()
                    true
                }
                4 -> {
                    Toast.makeText(this, "Notifications muted", Toast.LENGTH_SHORT).show()
                    true
                }
                5 -> {
                    Toast.makeText(this, "Chat history preserved", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    /**
     * Instantly triggers typing animation for testing/demonstration on a single device.
     */
    private fun simulatePartnerTyping() {
        Toast.makeText(this, "$recipientName is typing...", Toast.LENGTH_SHORT).show()
        showTypingIndicator()
        autoHideTypingHandler.removeCallbacks(autoHideTypingRunnable)
        autoHideTypingHandler.postDelayed(autoHideTypingRunnable, 3500L)
    }

    /**
     * Instantly marks sent messages as seen with double green checkmarks.
     */
    private fun simulatePartnerSeenMessages() {
        val now = System.currentTimeMillis()
        markSentMessagesAsSeen(now)
        realtimeTypingManager?.channelRoom?.let { room ->
            UserPresenceManager.recordConversationSeen(room, now)
        }
        Toast.makeText(this, "$recipientName marked messages as seen ✓✓", Toast.LENGTH_SHORT).show()
    }

    /**
     * Toggles between "Active now" and "Last active 15m ago" for testing presence display.
     */
    private fun togglePartnerPresenceDemo() {
        if (isPartnerOnline) {
            val fifteenMinAgo = System.currentTimeMillis() - (15 * 60 * 1000L)
            UserPresenceManager.setUserOnline(recipientUserId, false, fifteenMinAgo)
            isPartnerOnline = false
            originalStatusText = UserPresenceManager.formatLastActive(fifteenMinAgo, false)
            updateHeaderStatusUI(false, originalStatusText)
            Toast.makeText(this, "$recipientName status: $originalStatusText", Toast.LENGTH_SHORT).show()
        } else {
            val now = System.currentTimeMillis()
            UserPresenceManager.setUserOnline(recipientUserId, true, now)
            isPartnerOnline = true
            originalStatusText = "Active now"
            updateHeaderStatusUI(true, originalStatusText)
            Toast.makeText(this, "$recipientName status: Active now", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupMessagesList() {
        messages.clear()
        messageAdapter = MessageAdapter(messages) { productCard ->
            openProductDetail(productCard.id)
        }
        binding.rvMessages.apply {
            layoutManager = LinearLayoutManager(this@ConversationActivity).apply {
                stackFromEnd = !isGroupChat
            }
            adapter = messageAdapter
        }
    }

    private fun openProductDetail(productId: String) {
        if (productId.isBlank()) return
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("openProductId", productId)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    private fun loadInitialMessages() {
        lifecycleScope.launch {
            fetchMessagesAndCheckUpdate(isInitial = true)
            handleAutoProductInquiry()
            handlePrefillMessage()
        }
    }

    private fun handlePrefillMessage() {
        val prefill = intent.getStringExtra("prefillMessage")
        if (!prefill.isNullOrBlank()) {
            binding.etMessage.setText(prefill)
            binding.etMessage.setSelection(binding.etMessage.text.length)
        }
    }

    private fun handleAutoProductInquiry() {
        val shouldAutoSend = intent.getBooleanExtra("autoSendProductInquiry", false)
        if (!shouldAutoSend) return
        intent.removeExtra("autoSendProductInquiry")

        val prodId = intent.getStringExtra("productId") ?: ""
        val prodName = intent.getStringExtra("productName") ?: ""
        val prodPrice = intent.getStringExtra("productPrice") ?: ""
        val prodImageUrl = intent.getStringExtra("productImageUrl")
        val prodImageRes = intent.getIntExtra("productImageRes", 0)

        if (prodName.isBlank() && prodId.isBlank()) return

        val rawEncodedText = ProductMessageUtils.encodeProductMessage(
            id = prodId,
            title = prodName,
            price = prodPrice,
            imageUrl = prodImageUrl,
            imageRes = prodImageRes
        )

        val (cleanText, attachment) = ProductMessageUtils.parseMessage(rawEncodedText)
        val currentTime = timeFormat.format(Date())
        val now = System.currentTimeMillis()

        val localMsg = Message(
            text = cleanText,
            isSent = true,
            time = currentTime,
            isSeen = false,
            timestampMs = now,
            productCard = attachment
        )

        if (isGroupChat) {
            messages.add(0, localMsg)
            messageAdapter.notifyItemInserted(0)
            binding.rvMessages.scrollToPosition(0)
        } else {
            messages.add(localMsg)
            messageAdapter.notifyItemInserted(messages.size - 1)
            binding.rvMessages.scrollToPosition(messages.size - 1)
        }

        lifecycleScope.launch {
            if (currentUserId.isBlank()) {
                currentUserId = userRepository.resolveValidAuthorId().ifBlank {
                    SupabaseClient.getCurrentUserId() ?: ""
                }
            }
            val result = messageRepository.sendMessage(receiverId = recipientUserId, text = rawEncodedText)
            if (result is NetworkResult.Success) {
                AppRefreshManager.notifyDataChanged(RefreshTarget.MESSAGES)
            } else if (result is NetworkResult.Error) {
                Toast.makeText(this@ConversationActivity, "Failed to send inquiry: ${result.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupInputBar() {
        binding.etMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val hasText = !s.isNullOrBlank()
                binding.btnSend.visibility = if (hasText) View.VISIBLE else View.GONE
                binding.btnMic.visibility = if (hasText) View.GONE else View.VISIBLE
                handleUserTypingChange(hasText)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnAttach.setOnClickListener {
            Toast.makeText(this, "Attach photo, document, or location", Toast.LENGTH_SHORT).show()
        }

        binding.btnEmoji.setOnClickListener {
            val emojis = listOf("😊", "👍", "🙏", "🎉", "❤️", "👋")
            val randomEmoji = emojis.random()
            binding.etMessage.append(randomEmoji)
        }

        binding.btnMic.setOnClickListener {
            Toast.makeText(this, "Hold to record voice message", Toast.LENGTH_SHORT).show()
        }

        binding.btnSend.setOnClickListener {
            val text = binding.etMessage.text.toString().trim()
            if (text.isNotEmpty() && (recipientUserId.isNotBlank() || (isGroupChat && groupId.isNotBlank()))) {
                // Immediately cancel typing status when message is sent
                typingDebounceHandler.removeCallbacks(stopTypingRunnable)
                sendTypingBroadcast(false)

                val (cleanText, productAttachment) = ProductMessageUtils.parseMessage(text)
                val currentTime = timeFormat.format(Date())
                val now = System.currentTimeMillis()
                val localMsg = Message(
                    text = cleanText,
                    isSent = true,
                    time = currentTime,
                    isSeen = false,
                    timestampMs = now,
                    productCard = productAttachment
                )
                if (isGroupChat) {
                    messages.add(0, localMsg)
                    messageAdapter.notifyItemInserted(0)
                    binding.rvMessages.scrollToPosition(0)
                } else {
                    messages.add(localMsg)
                    messageAdapter.notifyItemInserted(messages.size - 1)
                    binding.rvMessages.scrollToPosition(messages.size - 1)
                }
                binding.etMessage.text?.clear()

                lifecycleScope.launch {
                    val result = if (isGroupChat) {
                        messageRepository.sendMessage(groupId = groupId, text = text)
                    } else {
                        messageRepository.sendMessage(receiverId = recipientUserId, text = text)
                    }
                    if (result is NetworkResult.Success) {
                        AppRefreshManager.notifyDataChanged(RefreshTarget.MESSAGES)
                    } else if (result is NetworkResult.Error) {
                        Toast.makeText(this@ConversationActivity, "Failed to send: ${result.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // --- REALTIME SYNC (TYPING, SEEN RECEIPTS, PRESENCE) ---

    private fun setupRealtimeChat() {
        lifecycleScope.launch {
            currentUserId = userRepository.resolveValidAuthorId().ifBlank {
                SupabaseClient.getCurrentUserId() ?: ""
            }

            if (currentUserId.isNotBlank() && recipientUserId.isNotBlank()) {
                UserPresenceManager.recordUserActive(currentUserId)

                realtimeTypingManager = RealtimeTypingManager(
                    currentUserId = currentUserId,
                    recipientUserId = recipientUserId
                ) { senderId, isTyping ->
                    // Callback received on Main Thread when chat partner is typing
                    if (senderId == recipientUserId) {
                        if (isTyping) {
                            showTypingIndicator()
                        } else {
                            hideTypingIndicator()
                        }
                    }
                }.apply {
                    // Message seen read receipt callback
                    onMessageSeenReceived = { _, seenAtMs, _ ->
                        markSentMessagesAsSeen(seenAtMs)
                    }

                    // Realtime presence state/diff callback
                    onPartnerPresenceChanged = { isOnline, lastActiveMs ->
                        isPartnerOnline = isOnline
                        val actualLastActive = if (lastActiveMs > 0L) lastActiveMs else UserPresenceManager.getUserLastActive(recipientUserId)
                        originalStatusText = UserPresenceManager.formatLastActive(actualLastActive, isOnline)
                        if (!isPartnerTypingNow) {
                            updateHeaderStatusUI(isOnline, originalStatusText)
                        }
                    }

                    connect()
                }

                // Send seen receipt indicating current user opened the conversation
                realtimeTypingManager?.sendMessageSeen(System.currentTimeMillis())
                realtimeTypingManager?.channelRoom?.let { room ->
                    UserPresenceManager.recordConversationSeen(room, System.currentTimeMillis())
                }
            }
        }
    }

    private fun markSentMessagesAsSeen(seenAtMs: Long) {
        var changed = false
        for (msg in messages) {
            if (msg.isSent && !msg.isSeen) {
                if (seenAtMs <= 0L || msg.timestampMs <= seenAtMs) {
                    msg.isSeen = true
                    changed = true
                }
            }
        }
        if (changed) {
            messageAdapter.notifyDataSetChanged()
        }
    }

    private fun handleUserTypingChange(hasText: Boolean) {
        if (hasText) {
            val now = System.currentTimeMillis()
            // Throttle outgoing typing broadcast to once every 1.8 seconds while typing
            if (now - lastTypingBroadcastTime > 1800L) {
                lastTypingBroadcastTime = now
                sendTypingBroadcast(true)
            }
            // Reset the 2.5 second inactivity timer: if user pauses typing, notify partner
            typingDebounceHandler.removeCallbacks(stopTypingRunnable)
            typingDebounceHandler.postDelayed(stopTypingRunnable, 2500L)
        } else {
            typingDebounceHandler.removeCallbacks(stopTypingRunnable)
            sendTypingBroadcast(false)
        }
    }

    private fun sendTypingBroadcast(isTyping: Boolean) {
        realtimeTypingManager?.sendTyping(isTyping)
    }

    // --- TYPING ANIMATION (RECIPIENT SIDE) ---

    private fun showTypingIndicator() {
        if (isFinishing || isDestroyed) return
        isPartnerTypingNow = true

        // Refresh auto-hide timer: if sender unexpectedly disconnects, hide after 4 seconds
        autoHideTypingHandler.removeCallbacks(autoHideTypingRunnable)
        autoHideTypingHandler.postDelayed(autoHideTypingRunnable, 4000L)

        binding.tvTypingStatus.text = "$recipientName is typing..."
        startDotsAnimation()

        // Smooth bubble entrance animation
        if (binding.layoutTypingIndicator.visibility != View.VISIBLE) {
            binding.layoutTypingIndicator.apply {
                alpha = 0f
                translationY = 16f
                visibility = View.VISIBLE
                animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(220)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }

            // Scroll if near bottom so typing bubble stays fully in view
            val layoutManager = binding.rvMessages.layoutManager as? LinearLayoutManager
            val lastVisible = layoutManager?.findLastVisibleItemPosition() ?: -1
            if (lastVisible >= messages.size - 2) {
                binding.rvMessages.smoothScrollToPosition(messages.size - 1)
            }
        }

        // Header status indicator transition
        val primaryGreen = ContextCompat.getColor(this, R.color.primary)
        binding.tvStatus.setTextColor(primaryGreen)
        binding.dotStatus.visibility = View.VISIBLE
        binding.dotStatus.backgroundTintList = ColorStateList.valueOf(primaryGreen)
        startHeaderStatusCycle()
    }

    private fun hideTypingIndicator() {
        if (!isPartnerTypingNow && binding.layoutTypingIndicator.visibility == View.GONE) return
        isPartnerTypingNow = false
        autoHideTypingHandler.removeCallbacks(autoHideTypingRunnable)
        statusCycleJob?.cancel()

        // Smooth bubble exit animation
        if (binding.layoutTypingIndicator.visibility == View.VISIBLE) {
            binding.layoutTypingIndicator.animate()
                .alpha(0f)
                .translationY(12f)
                .setDuration(180)
                .withEndAction {
                    binding.layoutTypingIndicator.visibility = View.GONE
                    stopDotsAnimation()
                }
                .start()
        } else {
            stopDotsAnimation()
        }

        // Restore header status
        updateHeaderStatusUI(isPartnerOnline, originalStatusText)
    }

    /**
     * Staggered bouncing 3-dot animation wave with subtle scaling and alpha pulsing.
     */
    private fun startDotsAnimation() {
        if (dotsAnimatorSet?.isRunning == true) return

        val dots = listOf(binding.dot1, binding.dot2, binding.dot3)
        val animators = dots.mapIndexed { index, dotView ->
            val translateY = ObjectAnimator.ofFloat(dotView, View.TRANSLATION_Y, 0f, -8f, 0f).apply {
                duration = 650
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                startDelay = (index * 140).toLong()
                interpolator = AccelerateDecelerateInterpolator()
            }
            val scaleX = ObjectAnimator.ofFloat(dotView, View.SCALE_X, 0.85f, 1.18f, 0.85f).apply {
                duration = 650
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                startDelay = (index * 140).toLong()
                interpolator = AccelerateDecelerateInterpolator()
            }
            val scaleY = ObjectAnimator.ofFloat(dotView, View.SCALE_Y, 0.85f, 1.18f, 0.85f).apply {
                duration = 650
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                startDelay = (index * 140).toLong()
                interpolator = AccelerateDecelerateInterpolator()
            }
            val alpha = ObjectAnimator.ofFloat(dotView, View.ALPHA, 0.5f, 1.0f, 0.5f).apply {
                duration = 650
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                startDelay = (index * 140).toLong()
                interpolator = AccelerateDecelerateInterpolator()
            }
            AnimatorSet().apply {
                playTogether(translateY, scaleX, scaleY, alpha)
            }
        }

        dotsAnimatorSet = AnimatorSet().apply {
            playTogether(animators)
            start()
        }
    }

    private fun stopDotsAnimation() {
        dotsAnimatorSet?.cancel()
        dotsAnimatorSet = null
        listOf(binding.dot1, binding.dot2, binding.dot3).forEach { dot ->
            dot.translationY = 0f
            dot.scaleX = 1f
            dot.scaleY = 1f
            dot.alpha = 1f
        }
    }

    /**
     * Cycles "typing", "typing.", "typing..", "typing..." in the top header status.
     */
    private fun startHeaderStatusCycle() {
        statusCycleJob?.cancel()
        statusCycleJob = lifecycleScope.launch {
            val steps = listOf("typing", "typing.", "typing..", "typing...")
            var i = 0
            while (isActive && isPartnerTypingNow) {
                binding.tvStatus.text = steps[i % steps.size]
                i++
                delay(380L)
            }
        }
    }

    // --- MESSAGE POLLING & BACKGROUND SYNC ---

    private fun startAutoRefreshLoop() {
        autoRefreshJob?.cancel()
        autoRefreshJob = lifecycleScope.launch {
            while (isActive) {
                delay(2000L) // Polling refresh for real-time conversation updates
                fetchMessagesAndCheckUpdate(isInitial = false)

                // Periodically update last active text if partner is offline
                if (!isPartnerOnline && !isPartnerTypingNow) {
                    val lastActive = UserPresenceManager.getUserLastActive(recipientUserId)
                    originalStatusText = UserPresenceManager.formatLastActive(lastActive, false)
                    binding.tvStatus.text = originalStatusText
                }
            }
        }
    }

    private suspend fun fetchMessagesAndCheckUpdate(isInitial: Boolean) {
        val authorId = currentUserId.ifBlank {
            userRepository.resolveValidAuthorId().ifBlank {
                SupabaseClient.getCurrentUserId() ?: ""
            }
        }
        if (authorId.isBlank()) return

        if (isGroupChat) {
            if (groupId.isBlank()) return
            when (val result = messageRepository.fetchGroupMessages(groupId)) {
                is NetworkResult.Success -> {
                    val groupSecret = CryptoManager.generateGroupSharedSecret(groupId)
                    val groupKey = CryptoManager.deriveKey(groupSecret)
                    val fetchedMessages = result.data.map { entity ->
                        val plainText = if (CryptoManager.isEncrypted(entity.text)) {
                            CryptoManager.decrypt(entity.text, groupKey)
                        } else {
                            entity.text
                        }
                        val (cleanText, productCard) = ProductMessageUtils.parseMessage(plainText)
                        val timestampLong = entity.timestamp.toLongOrNull() ?: 0L
                        val timeFormatted = if (timestampLong > 0L) timeFormat.format(Date(timestampLong)) else "Now"
                        val isSentByMe = entity.senderId == authorId
                        Message(
                            text = cleanText,
                            isSent = isSentByMe,
                            time = timeFormatted,
                            id = entity.messageId,
                            isSeen = true,
                            timestampMs = timestampLong,
                            productCard = productCard
                        )
                    }

                    if (isInitial || messages.isEmpty()) {
                        if (fetchedMessages.isNotEmpty()) {
                            messages.clear()
                            messages.addAll(fetchedMessages)
                            messageAdapter.notifyDataSetChanged()
                            binding.rvMessages.scrollToPosition(0)
                        }
                    } else {
                        var hasNew = false
                        for (newMsg in fetchedMessages) {
                            val existingIdx = messages.indexOfFirst {
                                (it.id.isNotBlank() && it.id == newMsg.id) ||
                                (it.text == newMsg.text && it.isSent == newMsg.isSent && Math.abs(it.timestampMs - newMsg.timestampMs) < 60000L)
                            }
                            if (existingIdx != -1) {
                                if (messages[existingIdx].id.isBlank() && newMsg.id.isNotBlank()) {
                                    messages[existingIdx] = messages[existingIdx].copy(id = newMsg.id)
                                    hasNew = true
                                }
                            } else {
                                messages.add(0, newMsg)
                                hasNew = true
                            }
                        }
                        if (hasNew) {
                            messages.sortByDescending { it.timestampMs }
                            messageAdapter.notifyDataSetChanged()
                            binding.rvMessages.scrollToPosition(0)
                        }
                    }
                }
                is NetworkResult.Error -> {}
            }
            return
        }

        if (recipientUserId.isBlank()) return

        val sharedSecret = CryptoManager.generateSharedSecret(authorId, recipientUserId)
        val secretKey = CryptoManager.deriveKey(sharedSecret)

        when (val result = messageRepository.fetchConversationMessages(recipientUserId)) {
            is NetworkResult.Success -> {
                val roomKey = realtimeTypingManager?.channelRoom
                    ?: RealtimeTypingManager.createChannelRoomName(authorId, recipientUserId)
                val conversationLastSeen = UserPresenceManager.getConversationLastSeen(roomKey)

                // Track partner's latest activity timestamp
                val partnerMessages = result.data.filter { it.senderId == recipientUserId }
                val maxPartnerTimestamp = partnerMessages.mapNotNull { it.timestamp.toLongOrNull() }.maxOrNull() ?: 0L
                if (maxPartnerTimestamp > 0L) {
                    UserPresenceManager.recordUserActive(recipientUserId, maxPartnerTimestamp)
                }

                val fetchedMessages = result.data.map { entity ->
                    val plainText = if (CryptoManager.isEncrypted(entity.text)) {
                        CryptoManager.decrypt(entity.text, secretKey)
                    } else {
                        entity.text
                    }
                    val (cleanText, productCard) = ProductMessageUtils.parseMessage(plainText)
                    val timestampLong = entity.timestamp.toLongOrNull() ?: 0L
                    val timeFormatted = if (timestampLong > 0L) timeFormat.format(Date(timestampLong)) else "Now"

                    val isSentByMe = entity.senderId == authorId
                    val isSeen = if (isSentByMe) {
                        (maxPartnerTimestamp > 0L && maxPartnerTimestamp >= timestampLong) ||
                        (conversationLastSeen > 0L && timestampLong <= conversationLastSeen)
                    } else false

                    Message(
                        text = cleanText,
                        isSent = isSentByMe,
                        time = timeFormatted,
                        id = entity.messageId,
                        isSeen = isSeen,
                        timestampMs = timestampLong,
                        productCard = productCard
                    )
                }

                if (isInitial || messages.isEmpty()) {
                    if (fetchedMessages.isNotEmpty()) {
                        messages.clear()
                        messages.addAll(fetchedMessages)
                        messageAdapter.notifyDataSetChanged()
                        binding.rvMessages.scrollToPosition(messages.size - 1)

                        // User opened and viewed these messages: send seen receipt
                        realtimeTypingManager?.sendMessageSeen(System.currentTimeMillis())
                        UserPresenceManager.recordConversationSeen(roomKey, System.currentTimeMillis())
                    }
                } else {
                    var hasNew = false
                    var receivedPartnerMessage = false
                    for (newMsg in fetchedMessages) {
                        val existingIdx = messages.indexOfFirst {
                            (it.id.isNotBlank() && it.id == newMsg.id) ||
                            (it.text == newMsg.text && it.isSent == newMsg.isSent && Math.abs(it.timestampMs - newMsg.timestampMs) < 60000L)
                        }
                        if (existingIdx != -1) {
                            if (messages[existingIdx].id.isBlank() && newMsg.id.isNotBlank()) {
                                messages[existingIdx] = messages[existingIdx].copy(id = newMsg.id)
                                hasNew = true
                            }
                        } else {
                            messages.add(newMsg)
                            hasNew = true
                            if (!newMsg.isSent) {
                                receivedPartnerMessage = true
                            }
                        }
                    }

                    // Update seen states on existing sent messages
                    for (msg in messages) {
                        if (msg.isSent && !msg.isSeen) {
                            if ((maxPartnerTimestamp > 0L && maxPartnerTimestamp >= msg.timestampMs) ||
                                (conversationLastSeen > 0L && msg.timestampMs <= conversationLastSeen)) {
                                msg.isSeen = true
                                hasNew = true
                            }
                        }
                    }

                    if (hasNew) {
                        messageAdapter.notifyDataSetChanged()
                        binding.rvMessages.scrollToPosition(messages.size - 1)
                    }

                    // When new message arrives from partner, dismiss typing indicator and acknowledge seen
                    if (receivedPartnerMessage) {
                        hideTypingIndicator()
                        realtimeTypingManager?.sendMessageSeen(System.currentTimeMillis())
                        UserPresenceManager.recordConversationSeen(roomKey, System.currentTimeMillis())
                    }
                }
            }
            is NetworkResult.Error -> {}
        }
    }

    override fun onResume() {
        super.onResume()
        MessageNotificationManager.activeConversationUserId = recipientUserId
        if (currentUserId.isNotBlank()) {
            UserPresenceManager.recordUserActive(currentUserId)
        }
    }

    override fun onPause() {
        super.onPause()
        MessageNotificationManager.activeConversationUserId = null
        typingDebounceHandler.removeCallbacks(stopTypingRunnable)
        sendTypingBroadcast(false)
        if (currentUserId.isNotBlank()) {
            UserPresenceManager.recordUserActive(currentUserId)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        MessageNotificationManager.activeConversationUserId = null
        autoRefreshJob?.cancel()
        statusCycleJob?.cancel()
        typingDebounceHandler.removeCallbacks(stopTypingRunnable)
        autoHideTypingHandler.removeCallbacks(autoHideTypingRunnable)
        stopDotsAnimation()
        realtimeTypingManager?.disconnect()
    }
}
