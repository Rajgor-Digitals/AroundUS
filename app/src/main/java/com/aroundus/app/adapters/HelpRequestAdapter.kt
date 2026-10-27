package com.aroundus.app.adapters

import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
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

import android.widget.Toast
import com.aroundus.app.network.SupabaseClient
import com.aroundus.app.utils.AppRefreshManager
import com.aroundus.app.utils.RefreshTarget
import java.util.concurrent.ConcurrentHashMap

class HelpRequestAdapter(
    private val posts: List<Post>,
    private val onPostClick: (Post) -> Unit,
    private val onLikeClick: (Post) -> Unit,
    private val onCommentClick: (Post) -> Unit,
    private val onShareClick: (Post) -> Unit,
    private val onAuthorClick: (Post) -> Unit
) : RecyclerView.Adapter<HelpRequestAdapter.HelpViewHolder>() {

    private val optimisticLikedStatus = ConcurrentHashMap<String, Boolean>()
    private val optimisticLikesCount = ConcurrentHashMap<String, Int>()

    fun rollbackLike(postId: String, previousLiked: Boolean, previousCount: Int) {
        optimisticLikedStatus[postId] = previousLiked
        optimisticLikesCount[postId] = previousCount
        val idx = posts.indexOfFirst { it.id == postId }
        if (idx != -1) {
            notifyItemChanged(idx)
        }
    }

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

            // 2. Location & Time on single line
            val loc = post.neighborhood.ifBlank { "Nearby" }
            val time = post.timeAgo.ifBlank { "Just now" }
            val fullSubtitle = "$loc  •  $time"
            val spannable = SpannableStringBuilder(fullSubtitle)
            spannable.setSpan(
                ForegroundColorSpan(Color.parseColor("#2563EB")),
                0,
                loc.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            binding.tvSubtitle.text = spannable

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

            // 6. Dynamic Likes with Optimistic UI
            val isLiked = optimisticLikedStatus[post.id] ?: post.isLiked
            val likesCount = optimisticLikesCount[post.id] ?: post.likesCount

            binding.tvLikeCount.text = likesCount.toString()
            if (isLiked) {
                binding.ivLikeIcon.setImageResource(R.drawable.ic_favorite)
                binding.ivLikeIcon.setColorFilter(Color.parseColor("#EF4444"))
                binding.tvLikeCount.setTextColor(Color.parseColor("#EF4444"))
            } else {
                binding.ivLikeIcon.setImageResource(R.drawable.ic_favorite_border)
                binding.ivLikeIcon.setColorFilter(Color.parseColor("#64748B"))
                binding.tvLikeCount.setTextColor(Color.parseColor("#64748B"))
            }

            // Dynamic Comments Count
            binding.tvCommentCount.text = post.commentsCount.toString()

            // Dynamic Bookmark / Saved status
            val currentUserId = SupabaseClient.getCurrentUserId() ?: ""
            var isSaved = if (currentUserId.isNotBlank()) SupabaseClient.getSavedPostIds(currentUserId).contains(post.id) else post.isSaved
            binding.btnBookmark.setImageResource(if (isSaved) R.drawable.ic_bookmark else R.drawable.ic_bookmark_outline)
            binding.btnBookmark.setColorFilter(if (isSaved) Color.parseColor("#0D8A3F") else Color.parseColor("#64748B"))

            // 7. Click Listeners
            binding.root.setOnClickListener { onPostClick(post) }

            binding.btnLike.setOnClickListener {
                val wasLiked = optimisticLikedStatus[post.id] ?: post.isLiked
                val prevCount = optimisticLikesCount[post.id] ?: post.likesCount
                val nextLiked = !wasLiked
                val nextCount = if (nextLiked) prevCount + 1 else (prevCount - 1).coerceAtLeast(0)

                optimisticLikedStatus[post.id] = nextLiked
                optimisticLikesCount[post.id] = nextCount

                binding.tvLikeCount.text = nextCount.toString()
                if (nextLiked) {
                    binding.ivLikeIcon.setImageResource(R.drawable.ic_favorite)
                    binding.ivLikeIcon.setColorFilter(Color.parseColor("#EF4444"))
                    binding.tvLikeCount.setTextColor(Color.parseColor("#EF4444"))
                    binding.ivLikeIcon.scaleX = 0.7f
                    binding.ivLikeIcon.scaleY = 0.7f
                    binding.ivLikeIcon.animate().scaleX(1.25f).scaleY(1.25f).setDuration(150).withEndAction {
                        binding.ivLikeIcon.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                    }.start()
                } else {
                    binding.ivLikeIcon.setImageResource(R.drawable.ic_favorite_border)
                    binding.ivLikeIcon.setColorFilter(Color.parseColor("#64748B"))
                    binding.tvLikeCount.setTextColor(Color.parseColor("#64748B"))
                }

                onLikeClick(post.copy(isLiked = wasLiked, likesCount = prevCount))
            }

            binding.btnComment.setOnClickListener { onCommentClick(post) }
            binding.btnShare.setOnClickListener { onShareClick(post) }

            binding.btnBookmark.setOnClickListener {
                if (currentUserId.isNotBlank()) {
                    isSaved = SupabaseClient.toggleSavedPost(currentUserId, post.id)
                    binding.btnBookmark.setImageResource(if (isSaved) R.drawable.ic_bookmark else R.drawable.ic_bookmark_outline)
                    binding.btnBookmark.setColorFilter(if (isSaved) Color.parseColor("#0D8A3F") else Color.parseColor("#64748B"))
                    val msg = if (isSaved) "Post saved to your profile" else "Post removed from saved"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    AppRefreshManager.notifyDataChanged(RefreshTarget.PROFILE)
                }
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
                val size = (10 * ctx.resources.displayMetrics.density).toInt()
                d.setBounds(0, 0, size, size)
                d.setTint(color)
                binding.tvCategoryBadge.setCompoundDrawables(d, null, null, null)
                binding.tvCategoryBadge.compoundDrawablePadding = (3 * ctx.resources.displayMetrics.density).toInt()
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
