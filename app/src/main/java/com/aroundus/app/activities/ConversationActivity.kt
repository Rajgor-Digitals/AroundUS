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
import com.aroundus.app.repository.UserRepository
import com.aroundus.app.utils.CryptoManager
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
    private var recipientUserId: String = ""
    private var autoRefreshJob: Job? = null
    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConversationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        recipientUserId = intent.getStringExtra("userId") ?: ""
        val recipientName = intent.getStringExtra("userName") ?: "Neighbor"
        val avatarUrl = intent.getStringExtra("avatarUrl")
        val avatarRes = intent.getIntExtra("avatarRes", 0)
        val isOnline = intent.getBooleanExtra("isOnline", true)

        setupHeader(recipientName, avatarUrl, avatarRes, isOnline)
        setupMessagesList()
        setupInputBar()
        loadInitialMessages()
        startAutoRefreshLoop()
    }

    private fun setupHeader(
        name: String,
        avatarUrl: String?,
        avatarRes: Int,
        isOnline: Boolean
    ) {
        binding.tvUserName.text = name

        if (!avatarUrl.isNullOrBlank()) {
            binding.tvAvatarInitial.visibility = View.GONE
            binding.ivAvatar.visibility = View.VISIBLE
            binding.ivAvatar.loadUserAvatar(avatarUrl)
        } else if (avatarRes != 0) {
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
        messages.clear()
        messageAdapter = MessageAdapter(messages)
        binding.rvMessages.apply {
            layoutManager = LinearLayoutManager(this@ConversationActivity).apply {
                stackFromEnd = true
            }
            adapter = messageAdapter
        }
    }

    private fun loadInitialMessages() {
        lifecycleScope.launch {
            fetchMessagesAndCheckUpdate(isInitial = true)
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
            if (text.isNotEmpty() && recipientUserId.isNotBlank()) {
                val currentTime = timeFormat.format(Date())
                val localMsg = Message(text = text, isSent = true, time = currentTime)
                messages.add(localMsg)
                messageAdapter.notifyItemInserted(messages.size - 1)
                binding.rvMessages.scrollToPosition(messages.size - 1)
                binding.etMessage.text?.clear()

                lifecycleScope.launch {
                    val result = messageRepository.sendMessage(receiverId = recipientUserId, text = text)
                    if (result is NetworkResult.Error) {
                        Toast.makeText(this@ConversationActivity, "Failed to send: ${result.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun startAutoRefreshLoop() {
        autoRefreshJob?.cancel()
        autoRefreshJob = lifecycleScope.launch {
            while (isActive) {
                delay(2000L) // Polling refresh for real-time conversation updates
                fetchMessagesAndCheckUpdate(isInitial = false)
            }
        }
    }

    private suspend fun fetchMessagesAndCheckUpdate(isInitial: Boolean) {
        val currentUserId = userRepository.resolveValidAuthorId().ifBlank { SupabaseClient.getCurrentUserId() ?: "" }
        if (currentUserId.isBlank() || recipientUserId.isBlank()) return

        val sharedSecret = CryptoManager.generateSharedSecret(currentUserId, recipientUserId)
        val secretKey = CryptoManager.deriveKey(sharedSecret)

        when (val result = messageRepository.fetchConversationMessages(recipientUserId)) {
            is NetworkResult.Success -> {
                val fetchedMessages = result.data.map { entity ->
                    val plainText = if (entity.text.startsWith("E2EE:")) {
                        CryptoManager.decrypt(entity.text, secretKey)
                    } else {
                        entity.text
                    }
                    val timeFormatted = entity.timestamp.toLongOrNull()?.let { timeFormat.format(Date(it)) } ?: "Now"
                    Message(
                        text = plainText,
                        isSent = entity.senderId == currentUserId,
                        time = timeFormatted,
                        id = entity.messageId
                    )
                }

                if (isInitial || messages.isEmpty()) {
                    if (fetchedMessages.isNotEmpty()) {
                        messages.clear()
                        messages.addAll(fetchedMessages)
                        messageAdapter.notifyDataSetChanged()
                        binding.rvMessages.scrollToPosition(messages.size - 1)
                    }
                } else {
                    var hasNew = false
                    for (newMsg in fetchedMessages) {
                        val alreadyExists = messages.any {
                            (it.id.isNotBlank() && it.id == newMsg.id) ||
                            (it.id.isBlank() && it.text == newMsg.text && it.isSent == newMsg.isSent)
                        }
                        if (!alreadyExists) {
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
