package com.aroundus.app.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.aroundus.app.R
import com.aroundus.app.databinding.ItemAnnouncementBinding
import com.aroundus.app.models.Announcement
import com.bumptech.glide.Glide

import com.aroundus.app.network.SupabaseClient
import com.aroundus.app.utils.loadUserAvatar

class AnnouncementAdapter(
    private val announcements: MutableList<Announcement>,
    private val onAnnouncementClick: (Announcement) -> Unit = {},
    private val onLikeClick: (Announcement) -> Unit = {},
    private val onCommentClick: (Announcement) -> Unit = {},
    private val onShareClick: (Announcement) -> Unit = {},
    private val onBookmarkClick: (Announcement) -> Unit = {},
    private val onAuthorClick: (Announcement) -> Unit = {}
) : RecyclerView.Adapter<AnnouncementAdapter.AnnouncementViewHolder>() {

    inner class AnnouncementViewHolder(val binding: ItemAnnouncementBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(announcement: Announcement) {
            val context = binding.root.context

            // 1. Author Info
            binding.tvAuthorName.text = announcement.authorName.ifBlank { "Neighbor" }
            binding.ivVerifiedBadge.visibility = if (announcement.isVerified) View.VISIBLE else View.GONE
            binding.tvLocationTime.text = "${announcement.location.ifBlank { "Bhujpur, Kutch" }}  •  ${announcement.timeAgo}"

            // Avatar resolution
            val currentUserId = SupabaseClient.getCurrentUserId() ?: ""
            val currentName = SupabaseClient.getCurrentUserName() ?: ""
            val isCurrentAuthor = (!announcement.authorId.isNullOrBlank() && announcement.authorId == currentUserId) ||
                    announcement.authorName.equals(currentName, ignoreCase = true)
            val cachedAvatar = if (isCurrentAuthor && currentUserId.isNotBlank()) {
                SupabaseClient.getAvatarPath(currentUserId)
            } else if (!announcement.authorId.isNullOrBlank()) {
                SupabaseClient.getAvatarPath(announcement.authorId)
            } else null

            val avatarPath = announcement.authorAvatarUrl ?: cachedAvatar
            binding.ivAuthorAvatar.loadUserAvatar(avatarPath, announcement.authorAvatarRes ?: R.drawable.shape_avatar_placeholder)

            binding.ivAuthorAvatar.setOnClickListener {
                onAuthorClick(announcement)
            }
            binding.tvAuthorName.setOnClickListener {
                onAuthorClick(announcement)
            }

            // 2. Title & Category Pill
            binding.tvTitle.text = announcement.title
            binding.tvCategoryBadge.text = announcement.category

            when {
                announcement.category.equals("Community", ignoreCase = true) -> {
                    binding.tvCategoryBadge.setBackgroundResource(R.drawable.bg_announcement_badge_community)
                    binding.tvCategoryBadge.setTextColor(Color.parseColor("#16A34A"))
                }
                announcement.category.equals("Alerts", ignoreCase = true) ||
                        announcement.category.equals("Alert", ignoreCase = true) -> {
                    binding.tvCategoryBadge.setBackgroundResource(R.drawable.bg_announcement_badge_alerts)
                    binding.tvCategoryBadge.setTextColor(Color.parseColor("#DC2626"))
                }
                announcement.category.equals("Events", ignoreCase = true) ||
                        announcement.category.equals("Event", ignoreCase = true) -> {
                    binding.tvCategoryBadge.setBackgroundResource(R.drawable.bg_announcement_badge_events)
                    binding.tvCategoryBadge.setTextColor(Color.parseColor("#0D9488"))
                }
                else -> {
                    binding.tvCategoryBadge.setBackgroundResource(R.drawable.bg_announcement_badge_community)
                    binding.tvCategoryBadge.setTextColor(Color.parseColor("#16A34A"))
                }
            }

            // 3. Description Content
            binding.tvDescription.text = announcement.content

            // 4. Special Event Box
            if (!announcement.eventDate.isNullOrBlank()) {
                binding.layoutEventBox.visibility = View.VISIBLE
                binding.tvEventDate.text = announcement.eventDate
                binding.tvEventTime.text = announcement.eventTime ?: ""
                binding.tvEventVenue.text = announcement.eventVenue ?: ""
                binding.tvEventAddress.text = announcement.eventAddress ?: ""
            } else {
                binding.layoutEventBox.visibility = View.GONE
            }

            // 5. Attached Image
            when {
                announcement.imageRes != null && announcement.imageRes != 0 -> {
                    binding.cardImageContainer.visibility = View.VISIBLE
                    binding.ivAnnouncementImage.setImageResource(announcement.imageRes)
                }
                !announcement.imageUrl.isNullOrBlank() -> {
                    binding.cardImageContainer.visibility = View.VISIBLE
                    Glide.with(context)
                        .load(announcement.imageUrl)
                        .placeholder(R.drawable.bg_image_placeholder)
                        .error(R.drawable.bg_image_placeholder)
                        .into(binding.ivAnnouncementImage)
                }
                else -> {
                    binding.cardImageContainer.visibility = View.GONE
                }
            }

            // 6. Action Bar Data
            binding.tvLikesCount.text = announcement.likesCount.toString()
            binding.tvCommentsCount.text = announcement.commentsCount.toString()

            updateLikeUi(announcement.isLiked)
            updateBookmarkUi(announcement.isSaved)

            // Click Handlers
            binding.root.setOnClickListener {
                onAnnouncementClick(announcement)
            }

            binding.btnLike.setOnClickListener {
                announcement.isLiked = !announcement.isLiked
                if (announcement.isLiked) {
                    announcement.likesCount++
                } else {
                    announcement.likesCount = maxOf(0, announcement.likesCount - 1)
                }
                binding.tvLikesCount.text = announcement.likesCount.toString()
                updateLikeUi(announcement.isLiked)
                onLikeClick(announcement)
            }

            binding.btnComment.setOnClickListener {
                onCommentClick(announcement)
            }

            binding.btnShare.setOnClickListener {
                onShareClick(announcement)
            }

            binding.btnBookmark.setOnClickListener {
                announcement.isSaved = !announcement.isSaved
                updateBookmarkUi(announcement.isSaved)
                val msg = if (announcement.isSaved) "Announcement saved!" else "Announcement unsaved"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                onBookmarkClick(announcement)
            }

            binding.btnMore.setOnClickListener {
                Toast.makeText(context, "Options for ${announcement.title}", Toast.LENGTH_SHORT).show()
            }
        }

        private fun updateLikeUi(isLiked: Boolean) {
            if (isLiked) {
                binding.ivLikeIcon.setImageResource(R.drawable.ic_favorite)
                binding.ivLikeIcon.setColorFilter(Color.parseColor("#EF4444"))
                binding.tvLikesCount.setTextColor(Color.parseColor("#EF4444"))
            } else {
                binding.ivLikeIcon.setImageResource(R.drawable.ic_favorite_border)
                binding.ivLikeIcon.setColorFilter(Color.parseColor("#64748B"))
                binding.tvLikesCount.setTextColor(Color.parseColor("#64748B"))
            }
        }

        private fun updateBookmarkUi(isSaved: Boolean) {
            if (isSaved) {
                binding.btnBookmark.setImageResource(R.drawable.ic_bookmark)
                binding.btnBookmark.setColorFilter(Color.parseColor("#0D8A3F"))
            } else {
                binding.btnBookmark.setImageResource(R.drawable.ic_bookmark_outline)
                binding.btnBookmark.setColorFilter(Color.parseColor("#64748B"))
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AnnouncementViewHolder {
        val binding = ItemAnnouncementBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AnnouncementViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AnnouncementViewHolder, position: Int) {
        holder.bind(announcements[position])
    }

    override fun getItemCount(): Int = announcements.size

    fun updateList(newList: List<Announcement>) {
        announcements.clear()
        announcements.addAll(newList)
        notifyDataSetChanged()
    }
}
