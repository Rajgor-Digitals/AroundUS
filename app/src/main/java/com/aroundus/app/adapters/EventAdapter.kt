package com.aroundus.app.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.aroundus.app.databinding.ItemEventBinding
import com.aroundus.app.models.Event

class EventAdapter(
    private val events: List<Event>,
    private val onEventClick: (Event) -> Unit
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
            binding.chipFree.text = if (event.isFree) "Free" else "Paid"
            binding.ivEventImage.setImageResource(event.imageRes)
            binding.root.setOnClickListener { onEventClick(event) }

            binding.btnAttend.setOnClickListener {
                event.isAttending = !event.isAttending
                binding.btnAttend.text = if (event.isAttending) "Attending" else "Attend"
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
