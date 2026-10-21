package com.aroundus.app.adapters

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.aroundus.app.R
import com.aroundus.app.databinding.ItemUserSearchBinding
import com.aroundus.app.models.UserEntity
import com.aroundus.app.utils.loadUserAvatar
import java.io.File

class PeopleAdapter(
    private val users: List<UserEntity>,
    private val onUserClick: (UserEntity) -> Unit,
    private val onFollowClick: (UserEntity, Boolean) -> Unit,
    private val onMessageClick: ((UserEntity) -> Unit)? = null
) : RecyclerView.Adapter<PeopleAdapter.PeopleViewHolder>() {

    private val followedUserIds = mutableSetOf<String>()

    inner class PeopleViewHolder(private val binding: ItemUserSearchBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(user: UserEntity) {
            binding.tvUserName.text = user.name.ifBlank { "Neighbor" }
            val handle = user.email.substringBefore("@").replace(".", "_").lowercase()
            binding.tvUserHandle.text = "@$handle"
            binding.tvUserLocation.text = if (!user.location.isNullOrBlank()) user.location else "Bhujpur, Kutch"

            val userPath = user.profilePhoto ?: user.profile
            binding.ivUserAvatar.loadUserAvatar(userPath, R.drawable.shape_avatar_placeholder)

            val isFollowed = followedUserIds.contains(user.userId)
            if (isFollowed) {
                binding.btnFollow.text = "Following"
                binding.btnFollow.setBackgroundResource(R.drawable.bg_button_joined)
                binding.btnFollow.setTextColor(android.graphics.Color.parseColor("#0D8A3F"))
            } else {
                binding.btnFollow.text = "Follow"
                binding.btnFollow.setBackgroundResource(R.drawable.bg_primary_button)
                binding.btnFollow.setTextColor(android.graphics.Color.parseColor("#FFFFFF"))
            }

            binding.root.setOnClickListener { onUserClick(user) }

            binding.btnMessageUser.setOnClickListener {
                if (onMessageClick != null) {
                    onMessageClick.invoke(user)
                } else {
                    onUserClick(user)
                }
            }

            binding.btnFollow.setOnClickListener {
                if (followedUserIds.contains(user.userId)) {
                    followedUserIds.remove(user.userId)
                    onFollowClick(user, false)
                } else {
                    followedUserIds.add(user.userId)
                    onFollowClick(user, true)
                }
                bind(user)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PeopleViewHolder {
        val binding = ItemUserSearchBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PeopleViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PeopleViewHolder, position: Int) {
        holder.bind(users[position])
    }

    override fun getItemCount() = users.size
}
