package com.aroundus.app.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.aroundus.app.databinding.ItemCommentBinding
import com.aroundus.app.models.Comment
import com.aroundus.app.network.SupabaseClient
import com.aroundus.app.utils.loadUserAvatar
import java.io.File

class CommentAdapter(
    private val comments: List<Comment>,
    private val onUserClick: ((Comment) -> Unit)? = null
) : RecyclerView.Adapter<CommentAdapter.CommentViewHolder>() {

    inner class CommentViewHolder(private val binding: ItemCommentBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(comment: Comment) {
            binding.tvUserName.text = comment.userName?.ifBlank { "Neighbor" } ?: "Neighbor"
            binding.tvCommentText.text = comment.text ?: ""
            binding.tvTime.text = comment.time ?: "Just now"

            val currentUserId = SupabaseClient.getCurrentUserId() ?: ""
            val isCurrentAuthor = !comment.userId.isNullOrBlank() && comment.userId == currentUserId

            val localAvatarFile = if (isCurrentAuthor && currentUserId.isNotBlank()) {
                File(binding.ivAvatar.context.filesDir, "avatar_${currentUserId}.jpg")
            } else null

            val cachedAvatar = if (!comment.userId.isNullOrBlank()) {
                SupabaseClient.getAvatarPath(comment.userId)
            } else null

            val avatarPath = if (!comment.avatarPath.isNullOrBlank()) {
                comment.avatarPath
            } else if (!cachedAvatar.isNullOrBlank()) {
                cachedAvatar
            } else if (localAvatarFile != null && localAvatarFile.exists() && localAvatarFile.length() > 0L) {
                localAvatarFile.absolutePath
            } else null

            binding.ivAvatar.loadUserAvatar(avatarPath, comment.avatarRes)

            binding.tvUserName.setOnClickListener { onUserClick?.invoke(comment) }
            binding.ivAvatar.setOnClickListener { onUserClick?.invoke(comment) }

            binding.btnLike.setOnClickListener {
                binding.tvLikeCount.text = "❤ ${comment.likeCount + 1}"
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val binding = ItemCommentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CommentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        holder.bind(comments[position])
    }

    override fun getItemCount() = comments.size
}
