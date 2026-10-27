package com.aroundus.app.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.aroundus.app.R
import com.aroundus.app.databinding.ItemMyGroupCardBinding
import com.aroundus.app.models.Group

class MyGroupsAdapter(
    private var groups: List<Group>,
    private val onGroupClick: (Group) -> Unit,
    private val onBellToggle: (Group) -> Unit,
    private val onLeaveClick: (Group) -> Unit = {}
) : RecyclerView.Adapter<MyGroupsAdapter.MyGroupViewHolder>() {

    inner class MyGroupViewHolder(val binding: ItemMyGroupCardBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyGroupViewHolder {
        val binding = ItemMyGroupCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MyGroupViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MyGroupViewHolder, position: Int) {
        val group = groups[position]
        val context = holder.itemView.context

        holder.binding.apply {
            if (!group.imageUrl.isNullOrBlank()) {
                com.bumptech.glide.Glide.with(context)
                    .load(group.imageUrl)
                    .placeholder(group.coverRes)
                    .error(group.coverRes)
                    .centerCrop()
                    .into(ivCover)
            } else {
                ivCover.setImageResource(group.coverRes)
            }
            tvGroupName.text = group.name
            tvMemberCount.text = group.membersText
            tvDescription.text = group.description

            // Admin badge visibility
            tvAdminBadge.visibility = if (group.isAdmin) View.VISIBLE else View.GONE

            // Icon badge
            if (group.iconBadgeRes != 0) {
                ivGroupIconBadge.setImageResource(group.iconBadgeRes)
                ivGroupIconBadge.visibility = View.VISIBLE
            } else {
                ivGroupIconBadge.setImageResource(R.drawable.ic_people)
            }

            // Bell icon state
            if (group.isNotificationOn) {
                ivBellIcon.setImageResource(R.drawable.ic_bell_outline)
                ivBellIcon.setColorFilter(Color.parseColor("#1E293B"))
            } else {
                ivBellIcon.setImageResource(R.drawable.ic_bell_outline)
                ivBellIcon.setColorFilter(Color.parseColor("#94A3B8"))
            }

            // Joined status
            if (group.isJoined) {
                btnJoined.text = "Joined"
                btnJoined.setBackgroundResource(R.drawable.bg_button_joined)
                btnJoined.setTextColor(ContextCompat.getColor(context, R.color.app_green))
            } else {
                btnJoined.text = "Join"
                btnJoined.setBackgroundResource(R.drawable.bg_button_join_solid)
                btnJoined.setTextColor(Color.WHITE)
            }

            btnJoined.setOnClickListener {
                onLeaveClick(group)
            }

            btnBell.setOnClickListener {
                group.isNotificationOn = !group.isNotificationOn
                notifyItemChanged(holder.bindingAdapterPosition)
                onBellToggle(group)
            }

            root.setOnClickListener {
                onGroupClick(group)
            }
        }
    }

    override fun getItemCount(): Int = groups.size

    fun updateList(newList: List<Group>) {
        groups = newList
        notifyDataSetChanged()
    }
}
