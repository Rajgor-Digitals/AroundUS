package com.aroundus.app.adapters

import android.content.res.ColorStateList
import android.graphics.Color
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.aroundus.app.R
import com.aroundus.app.databinding.ItemNotificationBinding
import com.aroundus.app.databinding.ItemNotificationHeaderBinding
import com.aroundus.app.models.Notification
import com.bumptech.glide.Glide

sealed class NotificationListItem {
    data class Header(val title: String, val showMarkAllRead: Boolean = false) : NotificationListItem()
    data class Item(val notification: Notification) : NotificationListItem()
}

class NotificationAdapter(
    private var items: List<NotificationListItem> = emptyList(),
    private val onNotificationClick: ((Notification) -> Unit)? = null,
    private val onMarkAllAsReadClick: (() -> Unit)? = null,
    private val onToggleReadStatusClick: ((Notification) -> Unit)? = null,
    private val onDismissNotificationClick: ((Notification) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }

    fun submitItems(newItems: List<NotificationListItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is NotificationListItem.Header -> TYPE_HEADER
            is NotificationListItem.Item -> TYPE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> {
                val binding = ItemNotificationHeaderBinding.inflate(inflater, parent, false)
                HeaderViewHolder(binding)
            }
            else -> {
                val binding = ItemNotificationBinding.inflate(inflater, parent, false)
                NotificationViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is NotificationListItem.Header -> (holder as HeaderViewHolder).bind(item)
            is NotificationListItem.Item -> (holder as NotificationViewHolder).bind(item.notification)
        }
    }

    override fun getItemCount(): Int = items.size

    inner class HeaderViewHolder(private val binding: ItemNotificationHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(header: NotificationListItem.Header) {
            binding.tvSectionTitle.text = header.title
            binding.layoutMarkAllRead.visibility = if (header.showMarkAllRead) View.VISIBLE else View.GONE
            binding.layoutMarkAllRead.setOnClickListener {
                onMarkAllAsReadClick?.invoke()
            }
        }
    }

    inner class NotificationViewHolder(private val binding: ItemNotificationBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(notif: Notification) {
            val ctx = binding.root.context

            // 1. Setup Left Avatar / Icon
            when (notif.type) {
                "announcement" -> {
                    binding.flAvatarContainer.visibility = View.VISIBLE
                    binding.flSystemIconContainer.visibility = View.GONE

                    if (!notif.avatarUrl.isNullOrBlank()) {
                        Glide.with(ctx)
                            .load(notif.avatarUrl)
                            .placeholder(R.drawable.shape_avatar_placeholder)
                            .error(R.drawable.shape_avatar_placeholder)
                            .into(binding.ivAvatar)
                    } else {
                        binding.ivAvatar.setImageResource(R.drawable.shape_avatar_placeholder)
                    }

                    // Green or Red badge with Megaphone icon
                    val badgeColor = if (notif.text.contains("alert", ignoreCase = true) || notif.text.contains("emergency", ignoreCase = true)) {
                        Color.parseColor("#EF4444")
                    } else {
                        Color.parseColor("#0D8A3F")
                    }
                    binding.badgeContainer.backgroundTintList = ColorStateList.valueOf(badgeColor)
                    binding.ivBadgeIcon.setImageResource(R.drawable.ic_announcements_megaphone)
                }
                "message" -> {
                    binding.flAvatarContainer.visibility = View.VISIBLE
                    binding.flSystemIconContainer.visibility = View.GONE

                    if (!notif.avatarUrl.isNullOrBlank()) {
                        Glide.with(ctx)
                            .load(notif.avatarUrl)
                            .placeholder(R.drawable.shape_avatar_placeholder)
                            .error(R.drawable.shape_avatar_placeholder)
                            .into(binding.ivAvatar)
                    } else {
                        binding.ivAvatar.setImageResource(R.drawable.shape_avatar_placeholder)
                    }

                    // Blue badge with Chat bubble icon
                    binding.badgeContainer.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2563EB"))
                    binding.ivBadgeIcon.setImageResource(R.drawable.ic_chat_bubble)
                }
                "event" -> {
                    binding.flAvatarContainer.visibility = View.GONE
                    binding.flSystemIconContainer.visibility = View.VISIBLE
                    binding.flSystemIconContainer.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F3E8FF"))
                    binding.ivSystemIcon.setImageResource(R.drawable.ic_event)
                    binding.ivSystemIcon.setColorFilter(Color.parseColor("#9333EA"))
                }
                "like" -> {
                    binding.flAvatarContainer.visibility = View.GONE
                    binding.flSystemIconContainer.visibility = View.VISIBLE
                    binding.flSystemIconContainer.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEE2E2"))
                    binding.ivSystemIcon.setImageResource(R.drawable.ic_favorite)
                    binding.ivSystemIcon.setColorFilter(Color.parseColor("#EF4444"))
                }
                "comment" -> {
                    binding.flAvatarContainer.visibility = View.GONE
                    binding.flSystemIconContainer.visibility = View.VISIBLE
                    binding.flSystemIconContainer.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E0F2FE"))
                    binding.ivSystemIcon.setImageResource(R.drawable.ic_chat_bubble)
                    binding.ivSystemIcon.setColorFilter(Color.parseColor("#0284C7"))
                }
                "help" -> {
                    binding.flAvatarContainer.visibility = View.GONE
                    binding.flSystemIconContainer.visibility = View.VISIBLE
                    binding.flSystemIconContainer.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEF3C7"))
                    binding.ivSystemIcon.setImageResource(R.drawable.ic_tools_wrench)
                    binding.ivSystemIcon.setColorFilter(Color.parseColor("#D97706"))
                }
                "welcome", "follow" -> {
                    binding.flAvatarContainer.visibility = View.GONE
                    binding.flSystemIconContainer.visibility = View.VISIBLE
                    binding.flSystemIconContainer.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#DCFCE7"))
                    binding.ivSystemIcon.setImageResource(R.drawable.ic_check_circle)
                    binding.ivSystemIcon.setColorFilter(Color.parseColor("#16A34A"))
                }
                else -> {
                    binding.flAvatarContainer.visibility = View.GONE
                    binding.flSystemIconContainer.visibility = View.VISIBLE
                    binding.flSystemIconContainer.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#DCFCE7"))
                    binding.ivSystemIcon.setImageResource(R.drawable.ic_notifications)
                    binding.ivSystemIcon.setColorFilter(Color.parseColor("#0D8A3F"))
                }
            }

            // 2. Setup Title and Subtitle Text
            val formattedTitle: CharSequence = when (notif.type) {
                "announcement" -> {
                    val author = notif.authorName ?: "Neighbor"
                    val contentPreview = notif.text.substringAfter("posted an announcement:", notif.text)
                        .trim().removeSurrounding("\"").take(48)
                    Html.fromHtml("<b>$author</b> posted an announcement: &ldquo;$contentPreview&rdquo;", Html.FROM_HTML_MODE_COMPACT)
                }
                "message" -> {
                    Html.fromHtml(notif.text, Html.FROM_HTML_MODE_COMPACT)
                }
                "welcome", "follow" -> {
                    Html.fromHtml("<b>Welcome to AroundUS!</b>", Html.FROM_HTML_MODE_COMPACT)
                }
                "event" -> {
                    val eventName = notif.authorName ?: "Community Event"
                    Html.fromHtml("New event near you:<br/><b>$eventName</b>", Html.FROM_HTML_MODE_COMPACT)
                }
                "like" -> {
                    val author = notif.authorName ?: "A neighbor"
                    Html.fromHtml("<b>$author</b> liked your post", Html.FROM_HTML_MODE_COMPACT)
                }
                "comment" -> {
                    val author = notif.authorName ?: "A neighbor"
                    Html.fromHtml("<b>$author</b> commented on your post", Html.FROM_HTML_MODE_COMPACT)
                }
                "help" -> {
                    val helpTitle = notif.authorName ?: "Request"
                    Html.fromHtml("New help request in your area:<br/><b>$helpTitle</b>", Html.FROM_HTML_MODE_COMPACT)
                }
                else -> {
                    Html.fromHtml(notif.text, Html.FROM_HTML_MODE_COMPACT)
                }
            }
            binding.tvTitle.text = formattedTitle

            // 3. Subtitle / Location Row
            when (notif.type) {
                "announcement" -> {
                    val loc = notif.location?.ifBlank { "Bhujpur, Kutch" } ?: "Bhujpur, Kutch"
                    binding.ivLocationPin.visibility = View.VISIBLE
                    binding.tvSubtitle.text = "$loc  •  ${notif.time}"
                }
                "welcome", "follow" -> {
                    binding.ivLocationPin.visibility = View.GONE
                    val sub = notif.subtitle ?: "Connect with verified neighbors in your local community."
                    binding.tvSubtitle.text = "$sub\n${notif.time}"
                }
                "event" -> {
                    binding.ivLocationPin.visibility = View.GONE
                    val sub = notif.subtitle ?: "Upcoming"
                    binding.tvSubtitle.text = "$sub\n${notif.time}"
                }
                else -> {
                    binding.ivLocationPin.visibility = View.GONE
                    binding.tvSubtitle.text = notif.time
                }
            }

            // 4. Thumbnail Image (if attached)
            if (!notif.mediaUrl.isNullOrBlank()) {
                binding.cardThumbnail.visibility = View.VISIBLE
                Glide.with(ctx)
                    .load(notif.mediaUrl)
                    .centerCrop()
                    .placeholder(R.drawable.shape_avatar_placeholder)
                    .error(R.drawable.shape_avatar_placeholder)
                    .into(binding.ivThumbnail)
            } else {
                binding.cardThumbnail.visibility = View.GONE
            }

            // 5. Unread Dot
            binding.unreadDot.visibility = if (notif.isUnread) View.VISIBLE else View.GONE
            val dotColor = if (notif.type == "welcome") Color.parseColor("#2563EB") else Color.parseColor("#10B981")
            binding.unreadDot.backgroundTintList = ColorStateList.valueOf(dotColor)

            // 6. 3-Dots More Options Menu
            binding.btnMore.setOnClickListener { view ->
                val popup = PopupMenu(ctx, view)
                val readTitle = if (notif.isUnread) "Mark as read" else "Mark as unread"
                popup.menu.add(0, 1, 0, readTitle)
                popup.menu.add(0, 2, 1, "Remove notification")
                popup.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        1 -> {
                            onToggleReadStatusClick?.invoke(notif)
                            true
                        }
                        2 -> {
                            onDismissNotificationClick?.invoke(notif)
                            true
                        }
                        else -> false
                    }
                }
                popup.show()
            }

            // 7. Item Click
            binding.root.setOnClickListener {
                onNotificationClick?.invoke(notif)
            }
        }
    }
}
