package com.aroundus.app.fragments

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.aroundus.app.R
import com.aroundus.app.databinding.FragmentEventDetailBinding
import com.aroundus.app.network.SupabaseClient
import com.aroundus.app.repository.EventRepository
import com.aroundus.app.utils.AppRefreshManager
import com.aroundus.app.utils.RefreshTarget
import kotlinx.coroutines.launch

class EventDetailFragment : Fragment() {

    private var _binding: FragmentEventDetailBinding? = null
    private val binding get() = _binding!!

    private val eventRepository = EventRepository()

    private var eventId: String = ""
    private var eventTitle: String = ""
    private var eventDate: String = ""
    private var eventLocation: String = ""
    private var eventAttendees: String = ""
    private var eventCategory: String = "Community"
    private var eventDescription: String = ""
    private var attendeesCount: Int = 1
    private var isAttending: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEventDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        arguments?.let {
            eventId = it.getString("eventId", "")
            eventTitle = it.getString("eventTitle", "Community Event")
            eventDate = it.getString("eventDate", "Date & Time TBA")
            eventLocation = it.getString("eventLocation", "Neighborhood Center")
            eventAttendees = it.getString("eventAttendees", "1 Attending")
            eventCategory = it.getString("eventCategory", "Community")
            eventDescription = it.getString(
                "eventDescription",
                "Join neighbors from across the community for $eventTitle. Everyone from the neighborhood is welcome to attend!"
            )
            attendeesCount = eventAttendees.filter { c -> c.isDigit() }.toIntOrNull() ?: 1
        }

        isAttending = SupabaseClient.isEventAttended(eventId)

        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        binding.tvDetailEventTitle.text = eventTitle
        binding.tvDetailEventDate.text = eventDate
        binding.tvDetailEventLocation.text = eventLocation
        binding.tvDetailAttendeesCount.text = "$attendeesCount neighbors attending"
        binding.tvDetailCategory.text = eventCategory
        binding.tvDetailDescription.text = eventDescription

        // Category icon & Cover
        val catIconRes = when (eventCategory.lowercase()) {
            "music" -> R.drawable.ic_trending
            "sports" -> R.drawable.ic_activity_joined
            "workshop" -> R.drawable.ic_school
            "cultural" -> R.drawable.ic_star
            "volunteering" -> R.drawable.ic_support_heart
            "food" -> R.drawable.ic_restaurant
            else -> R.drawable.ic_event
        }
        binding.ivDetailCategoryIcon.setImageResource(catIconRes)

        val coverRes = when (eventCategory.lowercase()) {
            "music" -> R.drawable.img_sample_event_thumb_2
            "sports" -> R.drawable.img_group_yoga_wellness
            "workshop" -> R.drawable.img_sample_event_thumb_1
            "cultural" -> R.drawable.img_events_empty
            "food" -> R.drawable.img_group_bhujpur_buysell
            "volunteering" -> R.drawable.img_group_clean_green
            else -> R.drawable.img_sample_event_thumb_1
        }
        binding.ivDetailEventImage.setImageResource(coverRes)

        // Parse date for badge
        parseDateToBadge(eventDate)

        // Update Attend button state
        updateAttendButtonUI(isAttending)

        // Open in Maps Action
        binding.btnDetailOpenInMaps.setOnClickListener {
            try {
                val mapUri = Uri.parse("geo:0,0?q=" + Uri.encode(eventLocation))
                val mapIntent = Intent(Intent.ACTION_VIEW, mapUri)
                startActivity(mapIntent)
            } catch (_: Exception) {
                Toast.makeText(requireContext(), "Could not open map for: $eventLocation", Toast.LENGTH_SHORT).show()
            }
        }

        // Add to Calendar Action
        binding.btnDetailAddToCalendar.setOnClickListener {
            try {
                val calIntent = Intent(Intent.ACTION_INSERT).apply {
                    data = CalendarContract.Events.CONTENT_URI
                    putExtra(CalendarContract.Events.TITLE, eventTitle)
                    putExtra(CalendarContract.Events.EVENT_LOCATION, eventLocation)
                    putExtra(CalendarContract.Events.DESCRIPTION, eventDescription)
                }
                startActivity(calIntent)
            } catch (_: Exception) {
                Toast.makeText(requireContext(), "Could not open calendar", Toast.LENGTH_SHORT).show()
            }
        }

        // Share button
        binding.btnDetailShare.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(
                    Intent.EXTRA_TEXT,
                    "📅 Join me at \"$eventTitle\"!\n📍 Location: $eventLocation\n🕒 Date & Time: $eventDate\n\nFound on AroundUS app."
                )
            }
            startActivity(Intent.createChooser(shareIntent, "Share Event via"))
        }

        // Attend toggle button
        binding.btnDetailAttend.setOnClickListener {
            if (!isAttending) {
                isAttending = true
                attendeesCount++
                SupabaseClient.setEventAttended(eventId, true)
                updateAttendButtonUI(true)
                binding.tvDetailAttendeesCount.text = "$attendeesCount neighbors attending"
                Toast.makeText(requireContext(), "🎉 You are attending this event!", Toast.LENGTH_SHORT).show()
            } else {
                isAttending = false
                attendeesCount = (attendeesCount - 1).coerceAtLeast(1)
                SupabaseClient.setEventAttended(eventId, false)
                updateAttendButtonUI(false)
                binding.tvDetailAttendeesCount.text = "$attendeesCount neighbors attending"
                Toast.makeText(requireContext(), "RSVP removed", Toast.LENGTH_SHORT).show()
            }

            if (eventId.isNotBlank()) {
                lifecycleScope.launch {
                    eventRepository.toggleAttendance(eventId, attendeesCount)
                    AppRefreshManager.notifyDataChanged(RefreshTarget.EVENTS)
                }
            }
        }
    }

    private fun updateAttendButtonUI(attending: Boolean) {
        if (attending) {
            binding.btnDetailAttend.setBackgroundResource(R.drawable.bg_green_pill_outlined)
            binding.btnDetailAttend.text = "Attending ✓"
            binding.btnDetailAttend.setTextColor(Color.parseColor("#0D8A3F"))
        } else {
            binding.btnDetailAttend.setBackgroundResource(R.drawable.bg_green_pill_button)
            binding.btnDetailAttend.text = "Attend Event"
            binding.btnDetailAttend.setTextColor(Color.parseColor("#FFFFFF"))
        }
    }

    private fun parseDateToBadge(date: String) {
        try {
            val cleanDate = date.trim()
            val parts = cleanDate.split(" ", "-", "/", ",", "·").filter { it.isNotBlank() }
            val monthNames = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
            val foundMonth = parts.firstOrNull { p ->
                monthNames.any { p.uppercase().startsWith(it) }
            }

            if (foundMonth != null) {
                binding.tvDetailMonth.text = foundMonth.uppercase().take(3)
                val foundDay = parts.firstOrNull { it.all { c -> c.isDigit() } && (it.toIntOrNull() in 1..31) }
                if (foundDay != null) binding.tvDetailDay.text = foundDay
            } else {
                val digits = parts.filter { it.all { c -> c.isDigit() } }
                if (digits.size >= 3 && digits[0].length == 4) {
                    val mIdx = digits[1].toIntOrNull() ?: 1
                    if (mIdx in 1..12) binding.tvDetailMonth.text = monthNames[mIdx - 1]
                    binding.tvDetailDay.text = digits[2].toIntOrNull()?.toString() ?: "1"
                } else if (digits.isNotEmpty()) {
                    val d = digits.firstOrNull { (it.toIntOrNull() in 1..31) }
                    if (d != null) binding.tvDetailDay.text = d
                    binding.tvDetailMonth.text = "UPCOMING"
                }
            }
        } catch (_: Exception) {}
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
