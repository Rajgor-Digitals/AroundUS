package com.aroundus.app.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.aroundus.app.databinding.ItemGroupBinding
import com.aroundus.app.models.Group

class GroupAdapter(
    private val groups: List<Group>,
    private val onGroupClick: (Group) -> Unit
) : RecyclerView.Adapter<GroupAdapter.GroupViewHolder>() {

    inner class GroupViewHolder(private val binding: ItemGroupBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(group: Group) {
            binding.tvGroupName.text = group.name
            binding.tvGroupDesc.text = group.description
            binding.tvMembersCount.text = group.membersText
            binding.chipPrivacy.text = group.category
            binding.ivGroupCover.setImageResource(group.coverRes)

            binding.btnJoin.text = if (group.isJoined) "Joined" else "Join"
            binding.btnJoin.setOnClickListener {
                group.isJoined = !group.isJoined
                binding.btnJoin.text = if (group.isJoined) "Joined" else "Join"
            }

            binding.root.setOnClickListener { onGroupClick(group) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val binding = ItemGroupBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return GroupViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        holder.bind(groups[position])
    }

    override fun getItemCount() = groups.size
}
