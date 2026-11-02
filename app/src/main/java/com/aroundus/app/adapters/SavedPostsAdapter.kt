package com.aroundus.app.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.aroundus.app.R
import com.aroundus.app.databinding.ItemSavedPostGridBinding
import com.aroundus.app.databinding.ItemSavedPostListBinding
import com.aroundus.app.models.Post
import com.aroundus.app.utils.loadPostImage

class SavedPostsAdapter(
    private val onPostClick: (Post) -> Unit,
    private val onBookmarkClick: (Post, Int) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val VIEW_TYPE_GRID = 0
        const val VIEW_TYPE_LIST = 1
    }

    var isGridView: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    private val items = mutableListOf<Post>()

    fun submitList(newItems: List<Post>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun removeItemAt(position: Int) {
        if (position in items.indices) {
            items.removeAt(position)
            notifyItemRemoved(position)
            notifyItemRangeChanged(position, items.size - position)
        }
    }

    fun getItems(): List<Post> = items

    override fun getItemViewType(position: Int): Int {
        return if (isGridView) VIEW_TYPE_GRID else VIEW_TYPE_LIST
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_GRID) {
            val binding = ItemSavedPostGridBinding.inflate(inflater, parent, false)
            GridViewHolder(binding)
        } else {
            val binding = ItemSavedPostListBinding.inflate(inflater, parent, false)
            ListViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val post = items[position]
        if (holder is GridViewHolder) {
            holder.bind(post)
        } else if (holder is ListViewHolder) {
            holder.bind(post)
        }
    }

    override fun getItemCount(): Int = items.size

    inner class GridViewHolder(private val binding: ItemSavedPostGridBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(post: Post) {
            val titleText = post.title?.takeIf { it.isNotBlank() }
                ?: post.text.lines().firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
                ?: "Community Post"
            binding.tvPostTitle.text = titleText

            val category = if (post.category.isNullOrBlank()) "General" else post.category
            binding.tvCategoryName.text = category
            binding.ivCategoryIcon.setImageResource(resolveCategoryIcon(category))

            // Load media thumbnail or author profile avatar fallback
            if (!post.mediaUrl.isNullOrBlank()) {
                binding.ivPostMedia.loadPostImage(post.mediaUrl)
            } else if (!post.userAvatarPath.isNullOrBlank()) {
                binding.ivPostMedia.loadPostImage(post.userAvatarPath)
            } else {
                binding.ivPostMedia.setImageResource(R.drawable.bg_image_placeholder)
            }

            // Video Duration indicator
            if (post.isVideo) {
                binding.layoutVideoDuration.visibility = View.VISIBLE
            } else {
                binding.layoutVideoDuration.visibility = View.GONE
            }

            // Click listener on full card
            binding.root.setOnClickListener {
                onPostClick(post)
            }

            // Un-save bookmark toggle
            binding.btnBookmarkBadge.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onBookmarkClick(post, pos)
                }
            }
        }
    }

    inner class ListViewHolder(private val binding: ItemSavedPostListBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(post: Post) {
            val titleText = post.title?.takeIf { it.isNotBlank() }
                ?: post.text.lines().firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
                ?: "Community Post"
            binding.tvPostTitle.text = titleText

            val category = if (post.category.isNullOrBlank()) "General" else post.category
            binding.tvCategoryName.text = category
            binding.ivCategoryIcon.setImageResource(resolveCategoryIcon(category))

            val subtitle = "${post.neighborhood ?: "Nearby"} • ${post.timeAgo ?: "Recently"}"
            binding.tvNeighborhoodAndTime.text = subtitle

            // Load media thumbnail or author profile avatar fallback
            if (!post.mediaUrl.isNullOrBlank()) {
                binding.ivPostMedia.loadPostImage(post.mediaUrl)
            } else if (!post.userAvatarPath.isNullOrBlank()) {
                binding.ivPostMedia.loadPostImage(post.userAvatarPath)
            } else {
                binding.ivPostMedia.setImageResource(R.drawable.bg_image_placeholder)
            }

            // Video Duration indicator
            if (post.isVideo) {
                binding.layoutVideoDuration.visibility = View.VISIBLE
            } else {
                binding.layoutVideoDuration.visibility = View.GONE
            }

            // Click listener on full card
            binding.root.setOnClickListener {
                onPostClick(post)
            }

            // Un-save bookmark toggle
            binding.btnBookmarkBadge.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onBookmarkClick(post, pos)
                }
            }
        }
    }

    private fun resolveCategoryIcon(category: String): Int {
        val lower = category.lowercase().trim()
        return when {
            lower.contains("announcement") || lower.contains("alert") -> R.drawable.ic_cat_announcement_gray
            lower.contains("lost") -> R.drawable.ic_cat_lost_found_gray
            lower.contains("event") -> R.drawable.ic_cat_event_gray
            lower.contains("help") || lower.contains("support") -> R.drawable.ic_cat_help_gray
            else -> R.drawable.ic_cat_general_gray
        }
    }
}

