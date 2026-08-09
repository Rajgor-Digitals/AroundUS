package com.aroundus.app.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.aroundus.app.R
import com.aroundus.app.databinding.ItemPostBinding
import com.aroundus.app.models.Post

class PostAdapter(
    private val posts: List<Post>,
    private val onPostClick: (Post) -> Unit,
    private val onLikeClick: (Post) -> Unit,
    private val onCommentClick: (Post) -> Unit,
    private val onShareClick: (Post) -> Unit
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
            
            binding.btnLike.setOnClickListener {
                if (likedPosts.contains(post.id)) {
                    likedPosts.remove(post.id)
                } else {
                    likedPosts.add(post.id)
                    // Scale animation on like
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
