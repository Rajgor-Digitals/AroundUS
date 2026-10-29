package com.aroundus.app.adapters

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.aroundus.app.R
import com.aroundus.app.databinding.ItemMyPostBinding
import com.aroundus.app.models.Post
import com.aroundus.app.network.SupabaseClient
import com.aroundus.app.utils.loadPostImage
import com.aroundus.app.utils.loadUserAvatar
import java.util.Locale

class MyPostsAdapter(
    private var posts: List<Post>,
    private val onPostClick: (Post) -> Unit,
    private val onEditClick: (Post) -> Unit,
    private val onDeleteClick: (Post) -> Unit,
    private val onLikeClick: (Post) -> Unit,
    private val onCommentClick: (Post) -> Unit,
    private val onShareClick: (Post) -> Unit
) : RecyclerView.Adapter<MyPostsAdapter.MyPostViewHolder>() {

    fun updatePosts(newPosts: List<Post>) {
        this.posts = newPosts
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyPostViewHolder {
        val binding = ItemMyPostBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MyPostViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MyPostViewHolder, position: Int) {
        holder.bind(posts[position])
    }

    override fun getItemCount(): Int = posts.size

    inner class MyPostViewHolder(private val binding: ItemMyPostBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(post: Post) {
            val ctx = binding.root.context

            // 1. Author Avatar & Name (Use current user's profile name if own post)
            val loggedInUid = SupabaseClient.getCurrentUserId() ?: ""
            val loggedInEmail = SupabaseClient.getCurrentUserEmail() ?: ""
            val currentUserName = SupabaseClient.getCurrentUserName()
            val isOwnPost = loggedInUid.isNotBlank() && (
                post.authorId == loggedInUid ||
                (loggedInEmail.isNotBlank() && post.authorId.equals(loggedInEmail, ignoreCase = true))
            )
            val displayName = if (isOwnPost && !currentUserName.isNullOrBlank()) currentUserName else post.userName
            binding.tvAuthorName.text = displayName.ifBlank { "NEIGHBOR" }.uppercase()

            val avatarPath = if (isOwnPost) {
                SupabaseClient.getAvatarPath(loggedInUid) ?: post.userAvatarPath
            } else {
                post.userAvatarPath
            }
            binding.ivAuthorAvatar.loadUserAvatar(avatarPath, post.userAvatar)

            // 2. Location & Time
            binding.tvLocation.text = post.neighborhood.ifBlank { "Bhujpur, Kutch" }
            binding.tvTimeAgo.text = post.timeAgo.ifBlank { "Just now" }
            binding.tvDot.visibility = View.VISIBLE
            binding.tvTimeAgo.visibility = View.VISIBLE

            // 3. Category Badge
            setupCategoryBadge(ctx, post.category ?: "General")

            // 4. Content (Title & Description)
            val (title, description) = resolveTitleAndDescription(post)
            binding.tvPostTitle.text = title
            if (description.isNotBlank()) {
                binding.tvPostDescription.visibility = View.VISIBLE
                binding.tvPostDescription.text = description
            } else {
                binding.tvPostDescription.visibility = View.GONE
            }

            // 5. Media Thumbnail
            val media = post.mediaUrl
            if (!media.isNullOrBlank()) {
                binding.cardThumbnail.visibility = View.VISIBLE
                binding.ivPostThumbnail.loadPostImage(media)
            } else if (post.imageRes != null && post.imageRes != 0) {
                binding.cardThumbnail.visibility = View.VISIBLE
                binding.ivPostThumbnail.setImageResource(post.imageRes)
            } else {
                binding.cardThumbnail.visibility = View.GONE
            }

            // 6. Likes, Comments & Share
            if (post.isLiked) {
                binding.ivLikeIcon.setImageResource(R.drawable.ic_favorite)
                binding.ivLikeIcon.imageTintList = ColorStateList.valueOf(Color.parseColor("#EF4444"))
                binding.tvLikesCount.setTextColor(Color.parseColor("#EF4444"))
            } else {
                binding.ivLikeIcon.setImageResource(R.drawable.ic_favorite_border)
                binding.ivLikeIcon.imageTintList = ColorStateList.valueOf(Color.parseColor("#64748B"))
                binding.tvLikesCount.setTextColor(Color.parseColor("#64748B"))
            }
            binding.tvLikesCount.text = post.likesCount.toString()
            binding.tvCommentsCount.text = post.commentsCount.toString()

            // 7. Click Listeners
            binding.root.setOnClickListener { onPostClick(post) }
            binding.cardThumbnail.setOnClickListener { onPostClick(post) }
            binding.btnEditPost.setOnClickListener { onEditClick(post) }

            binding.btnLike.setOnClickListener { onLikeClick(post) }
            binding.btnComment.setOnClickListener { onCommentClick(post) }
            binding.btnShare.setOnClickListener { onShareClick(post) }

            // 3-Dots Popup Menu
            binding.btnMoreMenu.setOnClickListener { v ->
                showPopupMenu(ctx, v, post)
            }
        }

        private fun setupCategoryBadge(ctx: Context, category: String) {
            when {
                category.contains("Announcement", ignoreCase = true) || category.contains("Alert", ignoreCase = true) -> {
                    binding.llCategoryBadge.setBackgroundResource(R.drawable.bg_badge_announcement)
                    binding.ivCategoryIcon.setImageResource(R.drawable.ic_post_cat_announcement)
                    val color = Color.parseColor("#0D8A3F")
                    binding.ivCategoryIcon.imageTintList = ColorStateList.valueOf(color)
                    binding.tvCategoryText.setTextColor(color)
                    binding.tvCategoryText.text = "Announcement"
                }
                category.contains("Help", ignoreCase = true) || category.contains("Support", ignoreCase = true) -> {
                    binding.llCategoryBadge.setBackgroundResource(R.drawable.bg_badge_help)
                    binding.ivCategoryIcon.setImageResource(R.drawable.ic_help_heart)
                    val color = Color.parseColor("#7C3AED")
                    binding.ivCategoryIcon.imageTintList = ColorStateList.valueOf(color)
                    binding.tvCategoryText.setTextColor(color)
                    binding.tvCategoryText.text = "Help & Support"
                }
                category.contains("Event", ignoreCase = true) -> {
                    binding.llCategoryBadge.setBackgroundResource(R.drawable.bg_badge_event)
                    binding.ivCategoryIcon.setImageResource(R.drawable.ic_event_calendar)
                    val color = Color.parseColor("#059669")
                    binding.ivCategoryIcon.imageTintList = ColorStateList.valueOf(color)
                    binding.tvCategoryText.setTextColor(color)
                    binding.tvCategoryText.text = "Event"
                }
                category.contains("Sale", ignoreCase = true) || category.contains("Market", ignoreCase = true) -> {
                    binding.llCategoryBadge.setBackgroundResource(R.drawable.bg_badge_sale)
                    binding.ivCategoryIcon.setImageResource(R.drawable.ic_post_cat_sale)
                    val color = Color.parseColor("#D97706")
                    binding.ivCategoryIcon.imageTintList = ColorStateList.valueOf(color)
                    binding.tvCategoryText.setTextColor(color)
                    binding.tvCategoryText.text = "Buy / Sell"
                }
                else -> {
                    // General
                    binding.llCategoryBadge.setBackgroundResource(R.drawable.bg_badge_general)
                    binding.ivCategoryIcon.setImageResource(R.drawable.ic_chat_bubble)
                    val color = Color.parseColor("#0D8A3F")
                    binding.ivCategoryIcon.imageTintList = ColorStateList.valueOf(color)
                    binding.tvCategoryText.setTextColor(color)
                    binding.tvCategoryText.text = "General"
                }
            }
        }

        private fun showPopupMenu(ctx: Context, anchor: View, post: Post) {
            val popup = PopupMenu(ctx, anchor)
            popup.menu.add(0, 1, 0, "Edit Post")
            popup.menu.add(0, 2, 1, "Delete Post")
            popup.menu.add(0, 3, 2, "Share Post")

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> onEditClick(post)
                    2 -> onDeleteClick(post)
                    3 -> onShareClick(post)
                }
                true
            }
            popup.show()
        }

        private fun resolveTitleAndDescription(post: Post): Pair<String, String> {
            if (!post.title.isNullOrBlank()) {
                return Pair(post.title.trim(), (post.text ?: "").trim())
            }
            val raw = (post.text ?: "").trim()
            if (raw.isBlank()) return Pair("Post", "")

            if (raw.contains("\n")) {
                val lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
                if (lines.isNotEmpty()) {
                    val title = lines.first()
                    val desc = lines.drop(1).joinToString("\n")
                    return Pair(title, desc)
                }
            }

            if (raw.length > 40) {
                val punctuationIdx = raw.indexOfFirst { it == '.' || it == '?' || it == '!' }
                if (punctuationIdx in 5..50) {
                    val title = raw.substring(0, punctuationIdx + 1).trim()
                    val desc = raw.substring(punctuationIdx + 1).trim()
                    if (desc.isNotBlank()) {
                        return Pair(title, desc)
                    }
                }
            }

            return Pair(raw, "")
        }
    }
}
