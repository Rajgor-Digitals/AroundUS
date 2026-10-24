package com.aroundus.app.fragments

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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

    private var selectedCategory: String = "Community"
    private var selectedDate: String = ""
    private var selectedTime: String = ""
    private var photoCount: Int = 2

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            photoCount = (photoCount + 1).coerceAtMost(5)
            binding.tvImageCounter.text = "$photoCount/5"
            binding.ivThumb1.setImageURI(uri)
            Toast.makeText(requireContext(), "Photo added", Toast.LENGTH_SHORT).show()
        }
    }

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

        setupCloseButton()
        setupPhotoPicker()
        setupCategories()
        setupDateTimePickers()
        setupLocation()
        setupDescriptionCounter()
        setupSubmitButton()
    }

    private fun setupCloseButton() {
        binding.btnCloseSheet.setOnClickListener {
            dismiss()
        }
    }

    private fun setupPhotoPicker() {
        binding.cardAddPhotos.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }
        binding.btnAddMorePhotos.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }
        binding.tvImageCounter.text = "$photoCount/5"
    }

    private fun setupCategories() {
        val categories = listOf(
            Triple(binding.chipCatCommunity, binding.tvCatCommunity, binding.ivCatCommunity to "Community"),
            Triple(binding.chipCatMusic, binding.tvCatMusic, binding.ivCatMusic to "Music"),
            Triple(binding.chipCatSports, binding.tvCatSports, binding.ivCatSports to "Sports"),
            Triple(binding.chipCatWorkshop, binding.tvCatWorkshop, binding.ivCatWorkshop to "Workshop"),
            Triple(binding.chipCatCultural, binding.tvCatCultural, binding.ivCatCultural to "Cultural"),
            Triple(binding.chipCatVolunteering, binding.tvCatVolunteering, binding.ivCatVolunteering to "Volunteering"),
            Triple(binding.chipCatFood, binding.tvCatFood, binding.ivCatFood to "Food"),
            Triple(binding.chipCatOther, binding.tvCatOther, binding.ivCatOther to "Other")
        )

        fun updateSelection(selectedName: String) {
            selectedCategory = selectedName
            val selectedBg = R.drawable.bg_chip_category_selected
            val unselectedBg = R.drawable.bg_chip_category_unselected
            val selectedGreen = Color.parseColor("#0D8A3F")
            val unselectedGray = Color.parseColor("#475569")
            val iconGray = Color.parseColor("#64748B")

            for ((chip, tv, iconPair) in categories) {
                val (iv, name) = iconPair
                val isSelected = name.equals(selectedName, ignoreCase = true)
                chip.setBackgroundResource(if (isSelected) selectedBg else unselectedBg)
                tv.setTextColor(if (isSelected) selectedGreen else unselectedGray)
                iv.setColorFilter(if (isSelected) selectedGreen else iconGray)
            }
        }

        for ((chip, _, iconPair) in categories) {
            val name = iconPair.second
            chip.setOnClickListener {
                updateSelection(name)
            }
        }

        updateSelection("Community")
    }

    private fun setupDateTimePickers() {
        val calendar = Calendar.getInstance()

        binding.boxSelectDate.setOnClickListener {
            val datePicker = DatePickerDialog(
                requireContext(),
                { _, year, month, dayOfMonth ->
                    calendar.set(Calendar.YEAR, year)
                    calendar.set(Calendar.MONTH, month)
                    calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)

                    val format = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault())
                    selectedDate = format.format(calendar.time)
                    binding.tvSelectedDate.text = selectedDate
                    binding.tvSelectedDate.setTextColor(Color.parseColor("#0F172A"))
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )
            datePicker.datePicker.minDate = System.currentTimeMillis() - 1000
            datePicker.show()
        }

        binding.boxSelectTime.setOnClickListener {
            val timePicker = TimePickerDialog(
                requireContext(),
                { _, hourOfDay, minute ->
                    calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                    calendar.set(Calendar.MINUTE, minute)

                    val format = SimpleDateFormat("h:mm a", Locale.getDefault())
                    selectedTime = format.format(calendar.time)
                    binding.tvSelectedTime.text = selectedTime
                    binding.tvSelectedTime.setTextColor(Color.parseColor("#0F172A"))
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                false
            )
            timePicker.show()
        }
    }

    private fun setupLocation() {
        val defaultLoc = UserLocationManager.getSelectedLocation(requireContext())?.displayName
        if (!defaultLoc.isNullOrBlank()) {
            binding.etEventLocation.setText(defaultLoc)
        }

        binding.btnClearLocation.setOnClickListener {
            binding.etEventLocation.setText("")
            binding.etEventLocation.requestFocus()
        }

        binding.btnUseCurrentLocation.setOnClickListener {
            val loc = UserLocationManager.getSelectedLocation(requireContext())?.displayName
            if (!loc.isNullOrBlank()) {
                binding.etEventLocation.setText(loc)
                Toast.makeText(requireContext(), "📍 Location set to: $loc", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Location not detected yet.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupDescriptionCounter() {
        binding.etEventDescription.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val len = s?.length ?: 0
                binding.tvDescriptionCounter.text = "$len/500"
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupSubmitButton() {
        binding.btnSubmitCreateEvent.setOnClickListener {
            submitCreateEvent()
        }
    }

    private fun submitCreateEvent() {
        val title = binding.etEventTitle.text.toString().trim()
        val location = binding.etEventLocation.text.toString().trim()
        val description = binding.etEventDescription.text.toString().trim()

        if (title.isBlank()) {
            binding.etEventTitle.error = "Please enter an event title"
            binding.etEventTitle.requestFocus()
            return
        }

        val eventDateStr = if (selectedDate.isNotBlank() && selectedTime.isNotBlank()) {
            "$selectedDate · $selectedTime"
        } else if (selectedDate.isNotBlank()) {
            selectedDate
        } else if (selectedTime.isNotBlank()) {
            "Today · $selectedTime"
        } else {
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
            SimpleDateFormat("EEE, MMM d · 6:00 PM", Locale.getDefault()).format(cal.time)
        }

        val fullTitle = if (selectedCategory.isNotBlank() && !title.startsWith("[")) {
            "[$selectedCategory] $title"
        } else {
            title
        }

        setLoading(true)

        lifecycleScope.launch {
            when (val result = eventRepository.createEvent(
                title = fullTitle,
                date = eventDateStr,
                location = location.ifBlank { "Community Center" },
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
        binding.btnSubmitCreateEvent.text = if (isLoading) "" else "Create Event   →"
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
