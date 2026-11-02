package com.aroundus.app.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.aroundus.app.R
import com.aroundus.app.databinding.ItemMyCommentBinding
import com.aroundus.app.models.MyCommentItem
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy

class MyCommentsAdapter(
    private var items: List<MyCommentItem>,
    private val onCommentClick: (MyCommentItem) -> Unit
) : RecyclerView.Adapter<MyCommentsAdapter.MyCommentViewHolder>() {

    inner class MyCommentViewHolder(private val binding: ItemMyCommentBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MyCommentItem) {
            binding.tvPostTitle.text = item.postTitle.ifBlank { "Community Post" }
            binding.tvCommentText.text = item.commentText.ifBlank { "Comment" }
            binding.tvTimeAgo.text = item.timeAgo.ifBlank { "Just now" }

            val postImage = item.postMediaUrl?.takeIf { it.isNotBlank() }
            val authorAvatar = item.postAuthorAvatarUrl?.takeIf { it.isNotBlank() }

            if (postImage != null) {
                // 1. Post has its own uploaded image/media
                Glide.with(binding.root.context)
                    .load(postImage)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.bg_image_placeholder)
                    .error(R.drawable.bg_image_placeholder)
                    .centerCrop()
                    .into(binding.ivThumbnail)
            } else if (authorAvatar != null) {
                // 2. Text-only post: Show post author's profile avatar
                Glide.with(binding.root.context)
                    .load(authorAvatar)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.shape_avatar_placeholder)
                    .error(R.drawable.shape_avatar_placeholder)
                    .centerCrop()
                    .into(binding.ivThumbnail)
            } else {
                // 3. Fallback placeholder avatar
                binding.ivThumbnail.setImageResource(R.drawable.shape_avatar_placeholder)
            }

            binding.root.setOnClickListener {
                onCommentClick(item)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyCommentViewHolder {
        val binding = ItemMyCommentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MyCommentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MyCommentViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<MyCommentItem>) {
        this.items = newItems
        notifyDataSetChanged()
    }
}
