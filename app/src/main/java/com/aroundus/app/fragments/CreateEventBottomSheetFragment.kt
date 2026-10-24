package com.aroundus.app.fragments

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.aroundus.app.R
import com.aroundus.app.databinding.BottomSheetCreateEventBinding
import com.aroundus.app.models.EventEntity
import com.aroundus.app.repository.EventRepository
import com.aroundus.app.repository.NetworkResult
import com.aroundus.app.utils.AppRefreshManager
import com.aroundus.app.utils.RefreshTarget
import com.aroundus.app.utils.UserLocationManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CreateEventBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: BottomSheetCreateEventBinding? = null
    private val binding get() = _binding!!

    private val eventRepository = EventRepository()
    var onEventCreated: ((EventEntity) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.Theme_AroundUS_BottomSheetDialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetCreateEventBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog ?: return
        dialog.behavior.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Pre-fill location if known
        val defaultLoc = UserLocationManager.getSelectedLocation(requireContext())?.displayName
        if (!defaultLoc.isNullOrBlank()) {
            binding.etEventLocation.setText(defaultLoc)
        }

        binding.btnCloseSheet.setOnClickListener {
            dismiss()
        }

        binding.btnPickDateTime.setOnClickListener {
            showDateTimePicker()
        }

        binding.btnUseCurrentLocation.setOnClickListener {
            val loc = UserLocationManager.getSelectedLocation(requireContext())?.displayName
            if (!loc.isNullOrBlank()) {
                binding.etEventLocation.setText(loc)
                Toast.makeText(requireContext(), "📍 Location set to: $loc", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "No device location detected yet.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnSubmitCreateEvent.setOnClickListener {
            submitCreateEvent()
        }
    }

    private fun showDateTimePicker() {
        val calendar = Calendar.getInstance()
        val datePicker = DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)

                // Pick Time after Date
                TimePickerDialog(
                    requireContext(),
                    { _, hourOfDay, minute ->
                        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                        calendar.set(Calendar.MINUTE, minute)

                        val format = SimpleDateFormat("EEE, MMM d · h:mm a", Locale.getDefault())
                        val formattedDate = format.format(calendar.time)
                        binding.etEventDate.setText(formattedDate)
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    false
                ).show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePicker.datePicker.minDate = System.currentTimeMillis() - 1000
        datePicker.show()
    }

    private fun submitCreateEvent() {
        val title = binding.etEventTitle.text.toString().trim()
        val date = binding.etEventDate.text.toString().trim()
        val location = binding.etEventLocation.text.toString().trim()

        if (title.isBlank()) {
            binding.etEventTitle.error = "Please enter an event title"
            binding.etEventTitle.requestFocus()
            return
        }

        if (date.isBlank()) {
            binding.etEventDate.error = "Please specify event date & time"
            binding.etEventDate.requestFocus()
            return
        }

        setLoading(true)

        lifecycleScope.launch {
            when (val result = eventRepository.createEvent(
                title = title,
                date = date,
                location = location.ifBlank { null },
                attendees = "1 Attending"
            )) {
                is NetworkResult.Success -> {
                    setLoading(false)
                    Toast.makeText(requireContext(), "🎉 Event created successfully!", Toast.LENGTH_SHORT).show()
                    AppRefreshManager.notifyDataChanged(RefreshTarget.EVENTS)
                    onEventCreated?.invoke(result.data)
                    dismiss()
                }
                is NetworkResult.Error -> {
                    setLoading(false)
                    Toast.makeText(requireContext(), "Error: ${result.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.btnSubmitCreateEvent.isEnabled = !isLoading
        binding.btnSubmitCreateEvent.text = if (isLoading) "" else "Create Event"
        binding.pbCreateEventLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnCloseSheet.isEnabled = !isLoading
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "CreateEventBottomSheetFragment"

        fun newInstance(): CreateEventBottomSheetFragment {
            return CreateEventBottomSheetFragment()
        }
    }
}
