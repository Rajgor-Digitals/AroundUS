package com.aroundus.app.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.aroundus.app.adapters.MessageAdapter
import com.aroundus.app.databinding.ActivityConversationBinding
import com.aroundus.app.models.Message

class ConversationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConversationBinding
    private lateinit var messageAdapter: MessageAdapter
    private val messages = mutableListOf<Message>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConversationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupMessages()
        setupSendButton()
    }

    private fun setupToolbar() {
        binding.btnMore.setOnClickListener { finish() }
    }

    private fun setupMessages() {
        messageAdapter = MessageAdapter(messages)
        binding.rvMessages.apply {
            layoutManager = LinearLayoutManager(this@ConversationActivity).apply {
                stackFromEnd = true
            }
            adapter = messageAdapter
        }

        // Dummy messages
        messages.addAll(listOf(
            Message("Hey! Are you coming to the community meeting?", true, "10:30 AM"),
            Message("Yes, I'll be there at 7 PM!", false, "10:31 AM"),
            Message("Great! Don't forget to bring your ideas for the park renovation.", true, "10:32 AM"),
            Message("Will do 👍 See you there!", false, "10:33 AM"),
            Message("Also, could you bring some chairs from the storage?", true, "10:34 AM"),
            Message("Sure, I have a few folding chairs I can bring.", false, "10:35 AM")
        ))
        messageAdapter.notifyDataSetChanged()
        binding.rvMessages.scrollToPosition(messages.size - 1)
    }

    private fun setupSendButton() {
        binding.btnSend.setOnClickListener {
            val text = binding.etMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                messages.add(Message(text, true, "Now"))
                messageAdapter.notifyItemInserted(messages.size - 1)
                binding.rvMessages.scrollToPosition(messages.size - 1)
                binding.etMessage.text?.clear()
            }
        }
    }
}
