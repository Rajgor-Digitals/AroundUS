package com.aroundus.app.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.aroundus.app.R
import com.aroundus.app.databinding.ItemHelpRequestBinding
import com.aroundus.app.models.Post
import com.aroundus.app.utils.PostActionUtils
import com.aroundus.app.utils.loadPostImage
import com.aroundus.app.utils.loadUserAvatar

class HelpRequestAdapter(
    private val posts: List<Post>,
    private val onPostClick: (Post) -> Unit,
    private val onLikeClick: (Post) -> Unit,
    private val onCommentClick: (Post) -> Unit,
    private val onShareClick: (Post) -> Unit,
    private val onAuthorClick: (Post) -> Unit
) : RecyclerView.Adapter<HelpRequestAdapter.HelpViewHolder>() {

    inner class HelpViewHolder(private val binding: ItemHelpRequestBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(post: Post) {
            val context = binding.root.context

            // 1. Author and Avatar
            binding.tvAuthorName.text = post.userName
            binding.ivVerified.visibility = if (post.isVerified) View.VISIBLE else View.GONE

            if (!post.userAvatarPath.isNullOrBlank()) {
                binding.ivAvatar.visibility = View.VISIBLE
                binding.tvInitialAvatar.visibility = View.GONE
                binding.ivAvatar.loadUserAvatar(post.userAvatarPath, R.drawable.shape_avatar_placeholder)
            } else {
                // Initial badge fallback (e.g. 'R' for Ramesh Patel)
                val initial = post.userName.trim().take(1).uppercase()
                if (initial.isNotBlank()) {
                    binding.tvInitialAvatar.text = initial
                    binding.tvInitialAvatar.visibility = View.VISIBLE
                    binding.ivAvatar.visibility = View.GONE
                } else {
                    binding.ivAvatar.visibility = View.VISIBLE
                    binding.tvInitialAvatar.visibility = View.GONE
                    binding.ivAvatar.setImageResource(R.drawable.shape_avatar_placeholder)
                }
            }

            // 2. Location & Time
            binding.tvLocation.text = post.neighborhood.ifBlank { "Nearby" }
            binding.tvDotTime.text = "• ${post.timeAgo}"

            // 3. Category Badge styling
            setupCategoryBadge(post.category)

            // 4. Title & Description
            val displayTitle: String
            val displayDescription: String
            if (!post.title.isNullOrBlank()) {
                displayTitle = post.title
                displayDescription = post.text
            } else if (post.text.contains("\n")) {
                displayTitle = post.text.substringBefore("\n").trim()
                displayDescription = post.text.substringAfter("\n").trim()
            } else if (post.text.length <= 45) {
                displayTitle = post.text.ifBlank { post.category }
                displayDescription = ""
            } else {
                displayTitle = post.category
                displayDescription = post.text
            }
            binding.tvTitle.text = displayTitle
            if (displayDescription.isNotBlank()) {
                binding.tvDescription.text = displayDescription
                binding.tvDescription.visibility = View.VISIBLE
            } else {
                binding.tvDescription.visibility = View.GONE
            }

            // 5. Right Thumbnail Image
            if (!post.mediaUrl.isNullOrBlank()) {
                binding.cardThumbnail.visibility = View.VISIBLE
                binding.ivThumbnail.loadPostImage(post.mediaUrl)
            } else {
                binding.cardThumbnail.visibility = View.GONE
            }

            // 6. Action counts
            binding.tvLikeCount.text = post.likesCount.toString()
            binding.tvCommentCount.text = post.commentsCount.toString()

            val likeColor = if (post.isLiked) ContextCompat.getColor(context, R.color.primary) else Color.parseColor("#64748B")
            binding.ivLikeIcon.setImageResource(if (post.isLiked) R.drawable.ic_favorite else R.drawable.ic_favorite_border)
            binding.ivLikeIcon.setColorFilter(likeColor)

            val bookmarkColor = if (post.isSaved) ContextCompat.getColor(context, R.color.primary) else Color.parseColor("#64748B")
            binding.btnBookmark.setImageResource(if (post.isSaved) R.drawable.ic_bookmark else R.drawable.ic_bookmark_outline)
            binding.btnBookmark.setColorFilter(bookmarkColor)

            // 7. Click Listeners
            binding.root.setOnClickListener { onPostClick(post) }

            binding.btnLike.setOnClickListener {
                post.isLiked = !post.isLiked
                onLikeClick(post)
                notifyItemChanged(bindingAdapterPosition)
            }

            binding.btnComment.setOnClickListener { onCommentClick(post) }
            binding.btnShare.setOnClickListener { onShareClick(post) }

            binding.btnBookmark.setOnClickListener {
                post.isSaved = !post.isSaved
                notifyItemChanged(bindingAdapterPosition)
            }

            binding.ivAvatar.setOnClickListener { onAuthorClick(post) }
            binding.tvInitialAvatar.setOnClickListener { onAuthorClick(post) }
            binding.tvAuthorName.setOnClickListener { onAuthorClick(post) }

            binding.btnMore.setOnClickListener {
                PostActionUtils.showPostOptionsMenu(context, post = post, onDeleted = {})
            }
        }

        private fun setupCategoryBadge(category: String) {
            val cat = category.trim().lowercase()
            when {
                cat.contains("general") || cat.contains("help") -> {
                    binding.tvCategoryBadge.setBackgroundResource(R.drawable.bg_badge_general_help)
                    binding.tvCategoryBadge.setTextColor(Color.parseColor("#059669"))
                    binding.tvCategoryBadge.text = "General Help"
                    setBadgeIcon(R.drawable.ic_support_heart, Color.parseColor("#059669"))
                }
                cat.contains("recommend") -> {
                    binding.tvCategoryBadge.setBackgroundResource(R.drawable.bg_badge_recommendations)
                    binding.tvCategoryBadge.setTextColor(Color.parseColor("#2563EB"))
                    binding.tvCategoryBadge.text = "Recommendations"
                    setBadgeIcon(R.drawable.ic_star, Color.parseColor("#2563EB"))
                }
                cat.contains("lost") || cat.contains("found") -> {
                    binding.tvCategoryBadge.setBackgroundResource(R.drawable.bg_badge_lost_found)
                    binding.tvCategoryBadge.setTextColor(Color.parseColor("#D97706"))
                    binding.tvCategoryBadge.text = "Lost & Found"
                    setBadgeIcon(R.drawable.ic_search, Color.parseColor("#D97706"))
                }
                cat.contains("service") || cat.contains("plumb") || cat.contains("electr") -> {
                    binding.tvCategoryBadge.setBackgroundResource(R.drawable.bg_badge_services)
                    binding.tvCategoryBadge.setTextColor(Color.parseColor("#7C3AED"))
                    binding.tvCategoryBadge.text = "Services"
                    setBadgeIcon(R.drawable.ic_tools_wrench, Color.parseColor("#7C3AED"))
                }
                cat.contains("emerg") || cat.contains("alert") -> {
                    binding.tvCategoryBadge.setBackgroundResource(R.drawable.bg_badge_emergency)
                    binding.tvCategoryBadge.setTextColor(Color.parseColor("#DC2626"))
                    binding.tvCategoryBadge.text = "Emergency"
                    setBadgeIcon(R.drawable.ic_bell_outline, Color.parseColor("#DC2626"))
                }
                else -> {
                    binding.tvCategoryBadge.setBackgroundResource(R.drawable.bg_badge_others)
                    binding.tvCategoryBadge.setTextColor(Color.parseColor("#475569"))
                    binding.tvCategoryBadge.text = category.ifBlank { "Others" }
                    binding.tvCategoryBadge.setCompoundDrawables(null, null, null, null)
                }
            }
        }

        private fun setBadgeIcon(resId: Int, color: Int) {
            val ctx = binding.root.context
            val d = ContextCompat.getDrawable(ctx, resId)?.mutate()
            if (d != null) {
                val size = (13 * ctx.resources.displayMetrics.density).toInt()
                d.setBounds(0, 0, size, size)
                d.setTint(color)
                binding.tvCategoryBadge.setCompoundDrawables(d, null, null, null)
                binding.tvCategoryBadge.compoundDrawablePadding = (5 * ctx.resources.displayMetrics.density).toInt()
            } else {
                binding.tvCategoryBadge.setCompoundDrawables(null, null, null, null)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HelpViewHolder {
        val binding = ItemHelpRequestBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HelpViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HelpViewHolder, position: Int) {
        holder.bind(posts[position])
    }

    override fun getItemCount(): Int = posts.size
}
