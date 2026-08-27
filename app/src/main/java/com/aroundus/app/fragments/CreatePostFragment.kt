package com.aroundus.app.fragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aroundus.app.R
import com.aroundus.app.databinding.FragmentCreatePostBinding

class CreatePostFragment : Fragment() {

    private var _binding: FragmentCreatePostBinding? = null
    private val binding get() = _binding!!

    private var selectedCategory = "General"
    private var selectedAudience = "Public"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCreatePostBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupHeader()
        setupCategorySelectors()
        setupInputCounter()
        setupMediaActionButtons()
        setupSettingsRows()
    }

    private fun setupHeader() {
        binding.btnClose.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnPostHeader.setOnClickListener {
            val content = binding.etPostContent.text.toString().trim()
            if (content.isEmpty()) {
                Toast.makeText(requireContext(), "Please write something before posting", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Post shared with your neighborhood!", Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()
            }
        }
    }

    private fun setupCategorySelectors() {
        val categoryViews = listOf(
            Triple(binding.catGeneral, binding.tvCatGeneral, binding.lineCatGeneral) to "General",
            Triple(binding.catAnnouncement, binding.tvCatAnnouncement, binding.lineCatAnnouncement) to "Announcement",
            Triple(binding.catHelpSupport, binding.tvCatHelpSupport, binding.lineCatHelpSupport) to "Help & Support",
            Triple(binding.catForSale, binding.tvCatForSale, binding.lineCatForSale) to "For Sale / Offer",
            Triple(binding.catEvent, binding.tvCatEvent, binding.lineCatEvent) to "Event"
        )

        categoryViews.forEach { (views, categoryName) ->
            views.first.setOnClickListener {
                selectedCategory = categoryName
                updateCategorySelection(categoryViews)
            }
        }
    }

    private fun updateCategorySelection(categoryViews: List<Pair<Triple<View, TextView, View>, String>>) {
        val greenColor = ContextCompat.getColor(requireContext(), R.color.app_green)
        val defaultTextColor = android.graphics.Color.parseColor("#334155")

        categoryViews.forEach { (views, categoryName) ->
            val isSelected = categoryName == selectedCategory
            val (container, textView, line) = views
            if (isSelected) {
                container.setBackgroundResource(R.drawable.bg_post_type_selected)
                textView.setTextColor(greenColor)
                line.visibility = View.VISIBLE
            } else {
                container.setBackgroundResource(R.drawable.bg_group_category_unselected)
                textView.setTextColor(defaultTextColor)
                line.visibility = View.GONE
            }
        }
    }


    private fun setupInputCounter() {
        binding.etPostContent.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val len = s?.length ?: 0
                binding.tvCharCounter.text = "$len/1000"
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupMediaActionButtons() {
        binding.btnAddPhoto.setOnClickListener {
            binding.imagePreviewContainer.visibility = View.VISIBLE
            binding.ivSelectedImage.setImageResource(R.drawable.img_onboarding_events_activities)
            Toast.makeText(requireContext(), "Photo added", Toast.LENGTH_SHORT).show()
        }

        binding.btnRemoveImage.setOnClickListener {
            binding.imagePreviewContainer.visibility = View.GONE
        }

        binding.btnAddVideo.setOnClickListener {
            Toast.makeText(requireContext(), "Video attachment clicked", Toast.LENGTH_SHORT).show()
        }

        binding.btnAddLocation.setOnClickListener {
            Toast.makeText(requireContext(), "Tagging current location", Toast.LENGTH_SHORT).show()
        }

        binding.btnAddPoll.setOnClickListener {
            Toast.makeText(requireContext(), "Poll creator opened", Toast.LENGTH_SHORT).show()
        }

        binding.btnAddMore.setOnClickListener {
            Toast.makeText(requireContext(), "More options", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSettingsRows() {
        binding.btnAudienceDropdown.setOnClickListener { toggleAudience() }
        binding.rowAudience.setOnClickListener { toggleAudience() }

        binding.rowNeighborhood.setOnClickListener {
            Toast.makeText(requireContext(), "Select Neighborhood", Toast.LENGTH_SHORT).show()
        }

        binding.switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            val status = if (isChecked) "Enabled" else "Muted"
            Toast.makeText(requireContext(), "Post notifications $status", Toast.LENGTH_SHORT).show()
        }

        binding.switchSchedule.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.tvScheduleSubtitle.text = "Schedule for later"
                Toast.makeText(requireContext(), "Pick date & time to schedule", Toast.LENGTH_SHORT).show()
            } else {
                binding.tvScheduleSubtitle.text = "Post now"
            }
        }
    }

    private fun toggleAudience() {
        selectedAudience = if (selectedAudience == "Public") "Neighborhood Only" else "Public"
        binding.tvAudience.text = selectedAudience
        binding.tvRowAudienceValue.text = selectedAudience
        Toast.makeText(requireContext(), "Audience set to $selectedAudience", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
