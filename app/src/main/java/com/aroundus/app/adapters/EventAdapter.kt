package com.aroundus.app.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.aroundus.app.R
import com.aroundus.app.databinding.ItemEventBinding
import com.aroundus.app.models.Event

class EventAdapter(
    private val events: List<Event>,
    private val onEventClick: (Event) -> Unit,
    private val onAttendClick: ((Event) -> Unit)? = null
) : RecyclerView.Adapter<EventAdapter.EventViewHolder>() {

    inner class EventViewHolder(private val binding: ItemEventBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(event: Event) {
            binding.tvEventTitle.text = event.title
            binding.tvMonth.text = event.month
            binding.tvDay.text = event.day
            binding.tvTime.text = event.time
            binding.tvLocation.text = event.location
            binding.tvAttendees.text = "${event.attendeesCount} attending"
            binding.tvCategoryBadge.text = event.category

            if (!event.imageUrl.isNullOrBlank()) {
                Glide.with(binding.ivEventImage.context)
                    .load(event.imageUrl)
                    .placeholder(event.imageRes)
                    .error(event.imageRes)
                    .centerCrop()
                    .into(binding.ivEventImage)
            } else {
                binding.ivEventImage.setImageResource(event.imageRes)
            }

            // Category icon
            val catIconRes = when (event.category.lowercase()) {
                "music" -> R.drawable.ic_trending
                "sports" -> R.drawable.ic_activity_joined
                "workshop" -> R.drawable.ic_school
                "cultural" -> R.drawable.ic_star
                "volunteering" -> R.drawable.ic_support_heart
                "food" -> R.drawable.ic_restaurant
                else -> R.drawable.ic_event
            }
            binding.ivCategoryIcon.setImageResource(catIconRes)

            updateAttendButton(event.isAttending)

            binding.root.setOnClickListener { onEventClick(event) }

            binding.btnAttend.setOnClickListener {
                onAttendClick?.invoke(event)
            }
        }

        private fun updateAttendButton(isAttending: Boolean) {
            if (isAttending) {
                binding.btnAttend.setBackgroundResource(R.drawable.bg_green_pill_outlined)
                binding.btnAttend.text = "Attending ✓"
                binding.btnAttend.setTextColor(Color.parseColor("#0D8A3F"))
            } else {
                binding.btnAttend.setBackgroundResource(R.drawable.bg_green_pill_button)
                binding.btnAttend.text = "Attend"
                binding.btnAttend.setTextColor(Color.parseColor("#FFFFFF"))
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val binding = ItemEventBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return EventViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        holder.bind(events[position])
    }

    override fun getItemCount() = events.size
}
