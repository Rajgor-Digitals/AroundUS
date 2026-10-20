package com.aroundus.app.fragments

import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.aroundus.app.R
import com.aroundus.app.databinding.FragmentCreatePostBinding
import com.aroundus.app.network.SupabaseClient
import com.aroundus.app.repository.NetworkResult
import com.aroundus.app.repository.PostRepository
import com.aroundus.app.repository.UserRepository
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class CreatePostFragment : Fragment() {

    private var _binding: FragmentCreatePostBinding? = null
    private val binding get() = _binding!!

    private var selectedCategory = "General"
    private var selectedAudience = "Public"
    private var selectedLocation = "Bhujpur, Kutch"
    private var attachedMediaUri: String? = null

    private val postRepository = PostRepository()
    private val userRepository = UserRepository()

    // ActivityResult Launchers for Photo & Video selection
    private val pickPhotoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            attachedMediaUri = uri.toString()
            binding.imagePreviewContainer.visibility = View.VISIBLE
            binding.ivSelectedImage.setImageURI(uri)
            Toast.makeText(requireContext(), "Photo attached successfully!", Toast.LENGTH_SHORT).show()
        }
    }

    private val pickVideoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            attachedMediaUri = uri.toString()
            Toast.makeText(requireContext(), "Video attached successfully!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCreatePostBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUserProfile()
        setupHeader()
        setupCategorySelectors()
        setupInputCounter()
        setupMediaActionButtons()
        setupSettingsRows()
    }

    private fun setupUserProfile() {
        val userName = SupabaseClient.getCurrentUserName() ?: "Neighbor"
        val currentUserId = SupabaseClient.getCurrentUserId()

        binding.tvAuthorName.text = userName
        binding.etPostContent.hint = "What's on your mind, $userName?"
        binding.tvAuthorLocation.text = selectedLocation

        if (!currentUserId.isNullOrBlank()) {
            lifecycleScope.launch {
                when (val result = userRepository.getUserProfile(currentUserId)) {
                    is NetworkResult.Success -> {
                        val profile = result.data
                        if (profile.name.isNotBlank()) {
                            binding.tvAuthorName.text = profile.name
                            binding.etPostContent.hint = "What's on your mind, ${profile.name}?"
                        }
                        if (!profile.location.isNullOrBlank()) {
                            selectedLocation = profile.location
                            binding.tvAuthorLocation.text = selectedLocation
                        }
                    }
                    is NetworkResult.Error -> {}
                }
            }
        }
    }

    private fun setupHeader() {
        binding.btnClose.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnPostHeader.setOnClickListener {
            val content = binding.etPostContent.text.toString().trim()
            if (content.isEmpty() && attachedMediaUri == null) {
                Toast.makeText(requireContext(), "Please write something or attach media before posting", Toast.LENGTH_SHORT).show()
            } else {
                lifecycleScope.launch {
                    binding.btnPostHeader.isEnabled = false
                    val result = postRepository.createPost(
                        text = content,
                        type = selectedCategory,
                        media = attachedMediaUri,
                        location = selectedLocation
                    )
                    binding.btnPostHeader.isEnabled = true
                    when (result) {
                        is NetworkResult.Success -> {
                            Toast.makeText(requireContext(), "Post shared with your neighborhood!", Toast.LENGTH_SHORT).show()
                            findNavController().popBackStack()
                        }
                        is NetworkResult.Error -> {
                            Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
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
        // Photo Picker
        binding.btnAddPhoto.setOnClickListener {
            pickPhotoLauncher.launch("image/*")
        }

        binding.btnRemoveImage.setOnClickListener {
            attachedMediaUri = null
            binding.imagePreviewContainer.visibility = View.GONE
        }

        // Video Picker
        binding.btnAddVideo.setOnClickListener {
            pickVideoLauncher.launch("video/*")
        }

        // Location Tagging Dialog
        binding.btnAddLocation.setOnClickListener {
            showLocationDialog()
        }

        // Poll Creation Dialog
        binding.btnAddPoll.setOnClickListener {
            showPollDialog()
        }

        binding.btnAddMore.setOnClickListener {
            Toast.makeText(requireContext(), "More options: Add Tags or Links", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLocationDialog() {
        val input = EditText(requireContext()).apply {
            setText(selectedLocation)
            hint = "Enter location name"
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Tag Location")
            .setView(input)
            .setPositiveButton("Set Location") { _, _ ->
                val loc = input.text.toString().trim()
                if (loc.isNotEmpty()) {
                    selectedLocation = loc
                    binding.tvAuthorLocation.text = selectedLocation
                    Toast.makeText(requireContext(), "Location updated: $selectedLocation", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPollDialog() {
        val inputQuestion = EditText(requireContext()).apply {
            hint = "Ask a question..."
        }
        val inputOption1 = EditText(requireContext()).apply {
            hint = "Option 1"
        }
        val inputOption2 = EditText(requireContext()).apply {
            hint = "Option 2"
        }

        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
            addView(inputQuestion)
            addView(inputOption1)
            addView(inputOption2)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Create Community Poll")
            .setView(container)
            .setPositiveButton("Add Poll") { _, _ ->
                val q = inputQuestion.text.toString().trim()
                val o1 = inputOption1.text.toString().trim()
                val o2 = inputOption2.text.toString().trim()

                if (q.isNotEmpty()) {
                    val pollText = "\n📊 POLL: $q\n• $o1\n• $o2"
                    val currentText = binding.etPostContent.text.toString()
                    binding.etPostContent.setText("$currentText$pollText")
                    Toast.makeText(requireContext(), "Poll attached to post!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupSettingsRows() {
        binding.btnAudienceDropdown.setOnClickListener { toggleAudience() }
        binding.rowAudience.setOnClickListener { toggleAudience() }

        binding.rowNeighborhood.setOnClickListener {
            showLocationDialog()
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
