package com.aroundus.app.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.aroundus.app.R
import com.aroundus.app.databinding.ItemChatBinding
import com.aroundus.app.models.ChatItem

import com.aroundus.app.utils.loadUserAvatar

class ChatAdapter(
    private var chats: List<ChatItem>,
    private val onChatClick: (ChatItem) -> Unit
) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    fun updateList(newChats: List<ChatItem>) {
        chats = newChats
        notifyDataSetChanged()
    }

    inner class ChatViewHolder(private val binding: ItemChatBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(chat: ChatItem) {
            binding.tvUserName.text = chat.userName
            binding.tvLastMessage.text = chat.lastMessage
            binding.tvTime.text = chat.time

            if (!chat.avatarUrl.isNullOrBlank()) {
                binding.tvAvatarInitial.visibility = View.GONE
                binding.ivAvatar.visibility = View.VISIBLE
                binding.ivAvatar.loadUserAvatar(chat.avatarUrl)
            } else if (chat.avatarRes != 0) {
                binding.tvAvatarInitial.visibility = View.GONE
                binding.ivAvatar.visibility = View.VISIBLE
                binding.ivAvatar.setImageResource(chat.avatarRes)
            } else {
                binding.ivAvatar.visibility = View.GONE
                binding.tvAvatarInitial.visibility = View.VISIBLE
                binding.tvAvatarInitial.text = chat.userName.firstOrNull()?.uppercase() ?: "A"
            }

            binding.onlineIndicator.visibility = if (chat.isOnline) View.VISIBLE else View.GONE

            if (chat.unreadCount > 0) {
                binding.tvUnreadCount.visibility = View.VISIBLE
                binding.tvUnreadCount.text = chat.unreadCount.toString()
            } else {
                binding.tvUnreadCount.visibility = View.GONE
            }

            binding.root.setOnClickListener { onChatClick(chat) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val binding = ItemChatBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ChatViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(chats[position])
    }

    override fun getItemCount() = chats.size
}
