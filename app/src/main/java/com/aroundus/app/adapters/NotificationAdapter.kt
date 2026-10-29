package com.aroundus.app.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.aroundus.app.databinding.ItemNotificationBinding
import com.aroundus.app.models.Notification

class NotificationAdapter(
    private var notifications: List<Notification>,
    private val onNotificationClick: ((Notification) -> Unit)? = null
) : RecyclerView.Adapter<NotificationAdapter.NotifViewHolder>() {

    fun updateList(newNotifications: List<Notification>) {
        notifications = newNotifications
        notifyDataSetChanged()
    }

    inner class NotifViewHolder(private val binding: ItemNotificationBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(notif: Notification) {
            binding.tvNotifText.text = notif.text
            binding.tvTime.text = notif.time
            binding.unreadDot.visibility = if (notif.isUnread) View.VISIBLE else View.GONE

            if (!notif.avatarUrl.isNullOrBlank()) {
                com.bumptech.glide.Glide.with(binding.root.context)
                    .load(notif.avatarUrl)
                    .placeholder(if (notif.avatarRes != 0) notif.avatarRes else com.aroundus.app.R.drawable.shape_avatar_placeholder)
                    .error(if (notif.avatarRes != 0) notif.avatarRes else com.aroundus.app.R.drawable.shape_avatar_placeholder)
                    .circleCrop()
                    .into(binding.ivAvatar)
            } else {
                binding.ivAvatar.setImageResource(if (notif.avatarRes != 0) notif.avatarRes else com.aroundus.app.R.drawable.shape_avatar_placeholder)
            }

            // Configure notification badge icon and tint based on type
            when (notif.type) {
                "message" -> {
                    binding.ivNotifType.setImageResource(com.aroundus.app.R.drawable.ic_chat_bubble)
                    binding.ivNotifType.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#2563EB"))
                    binding.ivNotifType.visibility = View.VISIBLE
                }
                "announcement" -> {
                    binding.ivNotifType.setImageResource(com.aroundus.app.R.drawable.ic_announcements_megaphone)
                    binding.ivNotifType.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#7C3AED"))
                    binding.ivNotifType.visibility = View.VISIBLE
                }
                "event" -> {
                    binding.ivNotifType.setImageResource(com.aroundus.app.R.drawable.ic_event)
                    binding.ivNotifType.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#D97706"))
                    binding.ivNotifType.visibility = View.VISIBLE
                }
                "follow" -> {
                    binding.ivNotifType.setImageResource(com.aroundus.app.R.drawable.ic_check_circle)
                    binding.ivNotifType.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#10B981"))
                    binding.ivNotifType.visibility = View.VISIBLE
                }
                else -> {
                    binding.ivNotifType.setImageResource(com.aroundus.app.R.drawable.ic_favorite)
                    binding.ivNotifType.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#EF4444"))
                    binding.ivNotifType.visibility = View.VISIBLE
                }
            }

            binding.root.setOnClickListener {
                onNotificationClick?.invoke(notif)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotifViewHolder {
        val binding = ItemNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NotifViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NotifViewHolder, position: Int) {
        holder.bind(notifications[position])
    }

    override fun getItemCount() = notifications.size
}
