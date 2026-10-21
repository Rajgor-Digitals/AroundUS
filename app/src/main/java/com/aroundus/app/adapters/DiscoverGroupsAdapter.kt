package com.aroundus.app.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.aroundus.app.R
import com.aroundus.app.databinding.ItemDiscoverGroupBinding
import com.aroundus.app.models.Group

class DiscoverGroupsAdapter(
    private var groups: List<Group>,
    private val onGroupClick: (Group) -> Unit,
    private val onJoinClick: (Group) -> Unit
) : RecyclerView.Adapter<DiscoverGroupsAdapter.DiscoverGroupViewHolder>() {

    inner class DiscoverGroupViewHolder(val binding: ItemDiscoverGroupBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DiscoverGroupViewHolder {
        val binding = ItemDiscoverGroupBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DiscoverGroupViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DiscoverGroupViewHolder, position: Int) {
        val group = groups[position]
        val context = holder.itemView.context

        holder.binding.apply {
            ivGroupThumb.setImageResource(group.coverRes)
            tvDiscoverGroupName.text = group.name
            tvDiscoverMembers.text = group.membersText
            tvDiscoverCategory.text = group.category
            tvDiscoverDesc.text = group.description

            if (group.isJoined) {
                btnDiscoverJoin.text = "Joined"
                btnDiscoverJoin.setBackgroundResource(R.drawable.bg_button_joined)
                btnDiscoverJoin.setTextColor(ContextCompat.getColor(context, R.color.app_green))
            } else {
                btnDiscoverJoin.text = "Join"
                btnDiscoverJoin.setBackgroundResource(R.drawable.bg_button_join_solid)
                btnDiscoverJoin.setTextColor(Color.WHITE)
            }

            btnDiscoverJoin.setOnClickListener {
                onJoinClick(group)
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
