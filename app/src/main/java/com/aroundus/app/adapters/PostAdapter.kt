package com.aroundus.app.adapters

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.aroundus.app.R
import com.aroundus.app.databinding.ItemPostBinding
import com.aroundus.app.models.Post
import com.aroundus.app.network.SupabaseClient
import com.aroundus.app.utils.loadUserAvatar
import android.widget.Toast
import java.io.File

class PostAdapter(
    private val posts: List<Post>,
    private val onPostClick: (Post) -> Unit,
    private val onLikeClick: (Post) -> Unit,
    private val onCommentClick: (Post) -> Unit,
    private val onShareClick: (Post) -> Unit,
    private val onAuthorClick: ((Post) -> Unit)? = null
) : RecyclerView.Adapter<PostAdapter.PostViewHolder>() {

    private val likedPosts = mutableSetOf<String>()

    inner class PostViewHolder(private val binding: ItemPostBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(post: Post) {
            binding.tvUserName.text = post.userName ?: "Neighbor"
            binding.tvNeighborhood.text = post.neighborhood ?: "Bhujpur, Kutch"
            binding.tvTime.text = post.timeAgo ?: "Just now"
            binding.chipCategory.text = post.category ?: "General"
            binding.tvPostText.text = post.text ?: ""
            binding.tvCommentsCount.text = post.commentsCount.toString()

            // Original Author Avatar Resolution from Database / Cache
            val currentUserId = SupabaseClient.getCurrentUserId() ?: ""
            val currentName = SupabaseClient.getCurrentUserName() ?: ""
            val isCurrentAuthor = (!post.authorId.isNullOrBlank() && post.authorId == currentUserId) ||
                    (!post.userName.isNullOrBlank() && post.userName.equals(currentName, ignoreCase = true))

            val localAvatarFile = if (isCurrentAuthor && currentUserId.isNotBlank()) {
                File(binding.ivAvatar.context.filesDir, "avatar_${currentUserId}.jpg")
            } else null

            val cachedAvatar = if (!post.authorId.isNullOrBlank()) {
                SupabaseClient.getAvatarPath(post.authorId)
            } else if (isCurrentAuthor && currentUserId.isNotBlank()) {
                SupabaseClient.getAvatarPath(currentUserId)
            } else null

            val avatarPath = if (!post.userAvatarPath.isNullOrBlank()) {
                post.userAvatarPath
            } else if (!cachedAvatar.isNullOrBlank()) {
                cachedAvatar
            } else if (localAvatarFile != null && localAvatarFile.exists() && localAvatarFile.length() > 0L) {
                localAvatarFile.absolutePath
            } else {
                null
            }

            binding.ivAvatar.loadUserAvatar(avatarPath, post.userAvatar)

            val isLiked = likedPosts.contains(post.id)
            val currentLikes = if (isLiked) post.likesCount + 1 else post.likesCount
            binding.tvLikesCount.text = currentLikes.toString()

            if (isLiked) {
                binding.ivLikeIcon.setImageResource(R.drawable.ic_favorite)
                binding.ivLikeIcon.setColorFilter(android.graphics.Color.parseColor("#EF4444"))
                binding.tvLikesCount.setTextColor(android.graphics.Color.parseColor("#EF4444"))
            } else {
                binding.ivLikeIcon.setImageResource(R.drawable.ic_favorite_border)
                binding.ivLikeIcon.setColorFilter(android.graphics.Color.parseColor("#64748B"))
                binding.tvLikesCount.setTextColor(android.graphics.Color.parseColor("#64748B"))
            }

            val media = post.mediaUrl
            if (!media.isNullOrBlank()) {
                binding.cardImageContainer.visibility = View.VISIBLE
                if (media.startsWith("http://") || media.startsWith("https://") || media.startsWith("content://") || media.startsWith("/") || media.startsWith("file://")) {
                    Glide.with(binding.ivPostImage.context)
                        .load(media)
                        .placeholder(R.drawable.bg_image_placeholder)
                        .error(R.drawable.bg_image_placeholder)
                        .into(binding.ivPostImage)
                } else if (post.imageRes != null) {
                    binding.ivPostImage.setImageResource(post.imageRes)
                } else {
                    binding.ivPostImage.setImageResource(R.drawable.bg_image_placeholder)
                }
            } else if (post.imageRes != null) {
                binding.cardImageContainer.visibility = View.VISIBLE
                binding.ivPostImage.setImageResource(post.imageRes)
            } else {
                binding.cardImageContainer.visibility = View.GONE
            }

            binding.root.setOnClickListener { onPostClick(post) }
            binding.tvPostText.setOnClickListener { onPostClick(post) }
            binding.cardImageContainer.setOnClickListener { onPostClick(post) }

            binding.ivAvatar.setOnClickListener {
                if (onAuthorClick != null) onAuthorClick.invoke(post) else onPostClick(post)
            }

            binding.tvUserName.setOnClickListener {
                if (onAuthorClick != null) onAuthorClick.invoke(post) else onPostClick(post)
            }

            binding.userRow.setOnClickListener {
                if (onAuthorClick != null) onAuthorClick.invoke(post) else onPostClick(post)
            }

            binding.btnLike.setOnClickListener {
                if (likedPosts.contains(post.id)) {
                    likedPosts.remove(post.id)
                } else {
                    likedPosts.add(post.id)
                    binding.ivLikeIcon.scaleX = 0.7f
                    binding.ivLikeIcon.scaleY = 0.7f
                    binding.ivLikeIcon.animate().scaleX(1.2f).scaleY(1.2f).setDuration(150).withEndAction {
                        binding.ivLikeIcon.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                    }.start()
                }
                bind(post)
                onLikeClick(post)
            }

            binding.btnComment.setOnClickListener { onCommentClick(post) }
            binding.btnShare.setOnClickListener { onShareClick(post) }

            var isSaved = SupabaseClient.getSavedPostIds(currentUserId).contains(post.id)
            if (isSaved) {
                binding.btnSave.setColorFilter(android.graphics.Color.parseColor("#0D8A3F"))
            } else {
                binding.btnSave.setColorFilter(android.graphics.Color.parseColor("#64748B"))
            }

            binding.btnSave.setOnClickListener {
                isSaved = SupabaseClient.toggleSavedPost(currentUserId, post.id)
                if (isSaved) {
                    binding.btnSave.setColorFilter(android.graphics.Color.parseColor("#0D8A3F"))
                    Toast.makeText(binding.root.context, "Post saved to your profile", Toast.LENGTH_SHORT).show()
                } else {
                    binding.btnSave.setColorFilter(android.graphics.Color.parseColor("#64748B"))
                    Toast.makeText(binding.root.context, "Post removed from saved", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val binding = ItemPostBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PostViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        holder.bind(posts[position])
    }

    override fun getItemCount() = posts.size
}
