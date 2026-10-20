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
            binding.tvUserName.text = post.userName
            binding.tvNeighborhood.text = post.neighborhood
            binding.tvTime.text = post.timeAgo
            binding.chipCategory.text = post.category
            binding.tvPostText.text = post.text
            binding.tvCommentsCount.text = post.commentsCount.toString()

            // Multi-Stage Avatar Resolution
            var loadedPhoto = false
            val currentUserId = SupabaseClient.getCurrentUserId() ?: ""
            val currentEmail = SupabaseClient.getCurrentUserEmail() ?: ""
            val currentName = SupabaseClient.getCurrentUserName() ?: ""

            val localAvatarFile = File(binding.ivAvatar.context.filesDir, "avatar_${currentUserId}.jpg")

            val avatarPath = if (post.userName.equals(currentName, ignoreCase = true) && localAvatarFile.exists() && localAvatarFile.length() > 0L) {
                localAvatarFile.absolutePath
            } else {
                post.userAvatarPath
                    ?: SupabaseClient.getAvatarPath(currentUserId)
                    ?: SupabaseClient.getAvatarPath(currentEmail)
            }

            if (!avatarPath.isNullOrBlank()) {
                try {
                    if (avatarPath.startsWith("http://") || avatarPath.startsWith("https://")) {
                        Glide.with(binding.ivAvatar.context)
                            .load(avatarPath)
                            .placeholder(R.drawable.shape_avatar_placeholder)
                            .error(R.drawable.shape_avatar_placeholder)
                            .into(binding.ivAvatar)
                        loadedPhoto = true
                    } else if (avatarPath.startsWith("/")) {
                        val file = File(avatarPath)
                        if (file.exists() && file.length() > 0L) {
                            Glide.with(binding.ivAvatar.context)
                                .load(file)
                                .placeholder(R.drawable.shape_avatar_placeholder)
                                .into(binding.ivAvatar)
                            loadedPhoto = true
                        }
                    } else if (avatarPath.startsWith("content://") || avatarPath.startsWith("file://")) {
                        Glide.with(binding.ivAvatar.context)
                            .load(Uri.parse(avatarPath))
                            .placeholder(R.drawable.shape_avatar_placeholder)
                            .into(binding.ivAvatar)
                        loadedPhoto = true
                    }
                } catch (_: Exception) {}
            }

            if (!loadedPhoto) {
                binding.ivAvatar.setImageResource(post.userAvatar)
            }

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

            if (post.imageRes != null) {
                binding.cardImageContainer.visibility = View.VISIBLE
                binding.ivPostImage.setImageResource(post.imageRes)
            } else {
                binding.cardImageContainer.visibility = View.GONE
            }

            binding.root.setOnClickListener { onPostClick(post) }

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
