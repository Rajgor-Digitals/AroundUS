package com.aroundus.app.activities

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.aroundus.app.R
import com.aroundus.app.adapters.MessageAdapter
import com.aroundus.app.databinding.ActivityConversationBinding
import com.aroundus.app.models.Message
import com.aroundus.app.network.SupabaseClient
import com.aroundus.app.repository.MessageRepository
import com.aroundus.app.repository.NetworkResult
import com.aroundus.app.utils.CryptoManager
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
    private var recipientUserId: String = "u_priya"
    private var autoRefreshJob: Job? = null
    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConversationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        recipientUserId = intent.getStringExtra("userId") ?: "u_priya"
        val recipientName = intent.getStringExtra("userName") ?: "Priya Sharma"
        val avatarRes = intent.getIntExtra("avatarRes", 0)
        val isOnline = intent.getBooleanExtra("isOnline", true)

        setupHeader(recipientName, avatarRes, isOnline)
        setupMessagesList()
        setupInputBar()
        startAutoRefreshLoop()
    }

    private fun setupHeader(
        name: String,
        avatarRes: Int,
        isOnline: Boolean
    ) {
        binding.tvUserName.text = name

        if (avatarRes != 0) {
            binding.tvAvatarInitial.visibility = View.GONE
            binding.ivAvatar.visibility = View.VISIBLE
            binding.ivAvatar.setImageResource(avatarRes)
        } else {
            binding.ivAvatar.visibility = View.GONE
            binding.tvAvatarInitial.visibility = View.VISIBLE
            binding.tvAvatarInitial.text = name.firstOrNull()?.uppercase() ?: "A"
        }

        binding.onlineIndicator.visibility = if (isOnline) View.VISIBLE else View.GONE
        binding.dotStatus.visibility = if (isOnline) View.VISIBLE else View.GONE
        binding.tvStatus.text = if (isOnline) "Active now" else "Offline"

        binding.btnBack.setOnClickListener { finish() }
        binding.btnCall.setOnClickListener {
            Toast.makeText(this, "Calling $name...", Toast.LENGTH_SHORT).show()
        }
        binding.btnMore.setOnClickListener {
            Toast.makeText(this, "Options: Mute, Block, Clear Chat", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupMessagesList() {
        if (recipientUserId == "u_priya") {
            messages.addAll(
                listOf(
                    Message("Hi Gaurav! 👋", isSent = false, time = "9:28 AM"),
                    Message("I saw your post about the tuition teacher. I think I can help with that.", isSent = false, time = "9:28 AM"),
                    Message("Hey Priya! That's great 😊", isSent = true, time = "9:30 AM"),
                    Message("I know a good Maths teacher who takes classes for 10th students.", isSent = false, time = "9:31 AM"),
                    Message("That would be amazing! Can you share the details?", isSent = true, time = "9:32 AM"),
                    Message("Sure!\nName: Rahul Sir\nContact: 9876543210\nLocation: Near Shakti Nagar", isSent = false, time = "9:33 AM"),
                    Message("Thank you so much! Really appreciate it 🙏", isSent = true, time = "9:34 AM"),
                    Message("You're welcome! Let me know if you need anything else.", isSent = false, time = "9:35 AM")
                )
            )
        } else {
            messages.add(
                Message("Hello! Let's connect on AroundUS.", isSent = false, time = "10:00 AM")
            )
        }

        messageAdapter = MessageAdapter(messages)
        binding.rvMessages.apply {
            layoutManager = LinearLayoutManager(this@ConversationActivity).apply {
                stackFromEnd = true
            }
            adapter = messageAdapter
            scrollToPosition(messages.size - 1)
        }
    }

    private fun setupInputBar() {
        binding.etMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val hasText = !s.isNullOrBlank()
                binding.btnSend.visibility = if (hasText) View.VISIBLE else View.GONE
                binding.btnMic.visibility = if (hasText) View.GONE else View.VISIBLE
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
            if (text.isNotEmpty()) {
                val currentTime = timeFormat.format(Date())
                messages.add(Message(text, isSent = true, time = currentTime))
                messageAdapter.notifyItemInserted(messages.size - 1)
                binding.rvMessages.scrollToPosition(messages.size - 1)
                binding.etMessage.text?.clear()

                val currentUserId = SupabaseClient.getCurrentUserId() ?: ""
                val sharedSecret = CryptoManager.generateSharedSecret(currentUserId, recipientUserId)
                val secretKey = CryptoManager.deriveKey(sharedSecret)
                val encryptedText = CryptoManager.encrypt(text, secretKey)

                lifecycleScope.launch {
                    messageRepository.sendMessage(receiverId = recipientUserId, text = encryptedText)
                }
            }
        }
    }

    private fun startAutoRefreshLoop() {
        autoRefreshJob?.cancel()
        autoRefreshJob = lifecycleScope.launch {
            while (isActive) {
                fetchMessagesAndCheckUpdate()
                delay(1500L) // 1.5s real-time E2EE chat refresh
            }
        }
    }

    private suspend fun fetchMessagesAndCheckUpdate() {
        val currentUserId = SupabaseClient.getCurrentUserId() ?: ""
        if (currentUserId.isBlank()) return
        val sharedSecret = CryptoManager.generateSharedSecret(currentUserId, recipientUserId)
        val secretKey = CryptoManager.deriveKey(sharedSecret)

        when (val result = messageRepository.fetchConversationMessages(recipientUserId)) {
            is NetworkResult.Success -> {
                if (result.data.isNotEmpty()) {
                    val decryptedNewMessages = result.data.map { entity ->
                        val plainText = CryptoManager.decrypt(entity.text, secretKey)
                        val timeFormatted = entity.timestamp.toLongOrNull()?.let { timeFormat.format(Date(it)) } ?: "Now"
                        Message(
                            text = plainText,
                            isSent = entity.senderId == currentUserId,
                            time = timeFormatted
                        )
                    }

                    // Append any new messages from Supabase not already displayed
                    var hasNew = false
                    for (newMsg in decryptedNewMessages) {
                        if (!messages.any { it.text == newMsg.text && it.isSent == newMsg.isSent }) {
                            messages.add(newMsg)
                            hasNew = true
                        }
                    }
                    if (hasNew) {
                        messageAdapter.notifyDataSetChanged()
                        binding.rvMessages.scrollToPosition(messages.size - 1)
                    }
                }
            }
            is NetworkResult.Error -> {}
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        autoRefreshJob?.cancel()
    }
}
