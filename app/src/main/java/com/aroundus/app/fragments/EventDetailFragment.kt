package com.aroundus.app.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.aroundus.app.databinding.FragmentEventDetailBinding
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
            eventDate = it.getString("eventDate", "TBA")
            eventLocation = it.getString("eventLocation", "Neighborhood")
            eventAttendees = it.getString("eventAttendees", "1 Attending")
            attendeesCount = eventAttendees.filter { c -> c.isDigit() }.toIntOrNull() ?: 1
        }

        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        binding.tvDetailEventTitle.text = eventTitle
        binding.tvDetailEventDate.text = eventDate
        binding.tvDetailEventLocation.text = eventLocation
        binding.tvDetailAttendeesCount.text = "$attendeesCount neighbors attending"

        // Parse date for badge
        parseDateToBadge(eventDate)

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
                binding.tvDetailAttendeesCount.text = "$attendeesCount neighbors attending"
                binding.btnDetailAttend.text = "Attending ✓"
                binding.btnDetailAttend.alpha = 0.85f
                Toast.makeText(requireContext(), "🎉 You are now attending this event!", Toast.LENGTH_SHORT).show()

                if (eventId.isNotBlank()) {
                    lifecycleScope.launch {
                        eventRepository.toggleAttendance(eventId, attendeesCount)
                        AppRefreshManager.notifyDataChanged(RefreshTarget.EVENTS)
                    }
                }
            } else {
                Toast.makeText(requireContext(), "You're already RSVP'd to this event!", Toast.LENGTH_SHORT).show()
            }
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
