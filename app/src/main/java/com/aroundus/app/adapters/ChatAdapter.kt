package com.aroundus.app.adapters

import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.aroundus.app.R
import com.aroundus.app.databinding.ItemChatBinding
import com.aroundus.app.models.ChatItem
import com.aroundus.app.utils.loadUserAvatar

class ChatAdapter(
    private var chats: List<ChatItem>,
    private val onChatClick: (ChatItem) -> Unit,
    private val onChatLongClick: ((ChatItem) -> Boolean)? = null
) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    fun updateList(newChats: List<ChatItem>) {
        chats = newChats
        notifyDataSetChanged()
    }

    inner class ChatViewHolder(private val binding: ItemChatBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(chat: ChatItem) {
            val context = binding.root.context
            binding.tvUserName.text = chat.userName
            binding.tvLastMessage.text = chat.lastMessage
            binding.tvTime.text = chat.time

            if (!chat.avatarUrl.isNullOrBlank()) {
                binding.tvAvatarInitial.visibility = View.GONE
                binding.ivAvatar.visibility = View.VISIBLE
                binding.ivAvatar.loadUserAvatar(
                    chat.avatarUrl,
                    placeholderRes = if (chat.avatarRes != 0) chat.avatarRes else R.drawable.shape_avatar_placeholder
                )
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
                binding.tvUnreadCount.text = if (chat.unreadCount > 99) "99+" else chat.unreadCount.toString()
                binding.dotUnreadIndicator.visibility = View.VISIBLE

                // Premium unread styling
                val greenPrimary = ContextCompat.getColor(context, R.color.primary)
                binding.tvTime.setTextColor(greenPrimary)
                binding.tvTime.setTypeface(null, Typeface.BOLD)

                binding.tvUserName.setTextColor(Color.parseColor("#0F172A"))
                binding.tvUserName.setTypeface(null, Typeface.BOLD)

                binding.tvLastMessage.setTextColor(Color.parseColor("#0F172A"))
                binding.tvLastMessage.setTypeface(null, Typeface.BOLD)
            } else {
                binding.tvUnreadCount.visibility = View.GONE
                binding.dotUnreadIndicator.visibility = View.GONE

                // Normal read styling
                binding.tvTime.setTextColor(Color.parseColor("#64748B"))
                binding.tvTime.setTypeface(null, Typeface.NORMAL)

                binding.tvUserName.setTextColor(Color.parseColor("#0F172A"))
                binding.tvUserName.setTypeface(null, Typeface.BOLD)

                binding.tvLastMessage.setTextColor(Color.parseColor("#64748B"))
                binding.tvLastMessage.setTypeface(null, Typeface.NORMAL)
            }

            binding.root.setOnClickListener { onChatClick(chat) }
            binding.root.setOnLongClickListener {
                onChatLongClick?.invoke(chat) ?: false
            }
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
