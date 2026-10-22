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
            binding.ivAvatar.setImageResource(if (notif.avatarRes != 0) notif.avatarRes else com.aroundus.app.R.drawable.shape_avatar_placeholder)
            binding.unreadDot.visibility = if (notif.isUnread) View.VISIBLE else View.GONE

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
