package com.aroundus.app.fragments

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
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
import com.aroundus.app.utils.AppRefreshManager
import com.aroundus.app.utils.LocationState
import com.aroundus.app.utils.RefreshTarget
import com.aroundus.app.utils.UserLocationManager
import com.aroundus.app.utils.loadUserAvatar
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.io.File

class CreatePostFragment : Fragment() {

    private var _binding: FragmentCreatePostBinding? = null
    private val binding get() = _binding!!

    private var selectedCategory = "General"
    private var selectedAudience = "Public"
    private var selectedLocation = ""
    private var selectedMediaUri: Uri? = null

    private val postRepository = PostRepository()
    private val userRepository = UserRepository()

    // ActivityResult Launchers for Photo & Video selection
    private val pickPhotoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedMediaUri = uri
            binding.imagePreviewContainer.visibility = View.VISIBLE
            binding.ivVideoPreviewPlayIcon.visibility = View.GONE
            binding.badgeVideoAttachment.visibility = View.GONE
            Glide.with(this)
                .load(uri)
                .into(binding.ivSelectedImage)
            Toast.makeText(requireContext(), "Photo attached successfully!", Toast.LENGTH_SHORT).show()
        }
    }

    private val pickVideoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedMediaUri = uri
            binding.imagePreviewContainer.visibility = View.VISIBLE
            binding.ivVideoPreviewPlayIcon.visibility = View.VISIBLE
            binding.badgeVideoAttachment.visibility = View.VISIBLE
            Glide.with(this)
                .asBitmap()
                .load(uri)
                .into(binding.ivSelectedImage)

            val fileSize = try {
                requireContext().contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
            } catch (_: Exception) { 0L }

            val sizeMb = fileSize / (1024 * 1024)
            if (sizeMb > 40) {
                Toast.makeText(requireContext(), "Video attached (${sizeMb}MB). Larger clips may take extra time to upload.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(requireContext(), "Video attached successfully!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ActivityResult Launcher for Automatic Location Permission Detection
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            fetchDeviceLocationAutomatically()
        } else {
            Toast.makeText(requireContext(), "Location permission denied. You can enter location manually.", Toast.LENGTH_SHORT).show()
            showManualLocationFallbackDialog()
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
        val currentUserId = SupabaseClient.getCurrentUserId() ?: ""
        val currentEmail = SupabaseClient.getCurrentUserEmail() ?: ""

        val detectedLocation = UserLocationManager.getSelectedLocation(requireContext())?.displayName
        if (selectedLocation.isBlank() && !detectedLocation.isNullOrBlank()) {
            selectedLocation = detectedLocation
        } else if (selectedLocation.isBlank() && UserLocationManager.hasLocationPermission(requireContext()) && UserLocationManager.isGpsEnabled(requireContext())) {
            fetchDeviceLocationAutomatically(silent = true)
        }

        binding.tvAuthorName.text = userName
        binding.etPostContent.hint = "What's on your mind, $userName?"
        binding.tvAuthorLocation.text = if (selectedLocation.isNotBlank()) selectedLocation else "Select Location"
        binding.tvAuthorLocation.setOnClickListener {
            val sheet = LocationBottomSheetFragment.newInstance()
            sheet.onLocationSelected = { loc ->
                selectedLocation = loc.displayName
                binding.tvAuthorLocation.text = selectedLocation
            }
            sheet.show(parentFragmentManager, LocationBottomSheetFragment.TAG)
        }

        // 1. Instant 0ms local avatar preview
        val localAvatarFile = if (currentUserId.isNotBlank()) {
            File(requireContext().filesDir, "avatar_${currentUserId}.jpg")
        } else null

        val cachedPath = SupabaseClient.getAvatarPath(currentUserId) ?: SupabaseClient.getAvatarPath(currentEmail)
        val localAvatarPath = if (!cachedPath.isNullOrBlank()) {
            cachedPath
        } else if (localAvatarFile != null && localAvatarFile.exists() && localAvatarFile.length() > 0L) {
            localAvatarFile.absolutePath
        } else {
            null
        }
        binding.ivAuthorAvatar.loadUserAvatar(localAvatarPath)

        // 2. Fetch original profile photo & fresh details directly from Supabase database
        if (currentUserId.isNotBlank()) {
            viewLifecycleOwner.lifecycleScope.launch {
                when (val result = userRepository.getUserProfile(currentUserId)) {
                    is NetworkResult.Success -> {
                        val b = _binding ?: return@launch
                        val profile = result.data
                        if (profile.name.isNotBlank()) {
                            b.tvAuthorName.text = profile.name
                            b.etPostContent.hint = "What's on your mind, ${profile.name}?"
                        }
                        if (!profile.location.isNullOrBlank()) {
                            selectedLocation = profile.location
                            b.tvAuthorLocation.text = selectedLocation
                        }
                        if (!profile.profilePhoto.isNullOrBlank()) {
                            b.ivAuthorAvatar.loadUserAvatar(profile.profilePhoto)
                            SupabaseClient.saveAvatarPath(currentUserId, profile.profilePhoto)
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
            val mediaUri = selectedMediaUri
            if (content.isEmpty() && mediaUri == null) {
                Toast.makeText(requireContext(), "Please write something or attach media before posting", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                binding.btnPostHeader.isEnabled = false
                val originalButtonText = binding.btnPostHeader.text
                binding.btnPostHeader.text = "Uploading..."

                var remoteMediaUrl: String? = null

                // 1. If media is attached, upload directly to Supabase Storage bucket 'post-media'
                if (mediaUri != null) {
                    val authorId = userRepository.resolveValidAuthorId()
                    when (val uploadResult = postRepository.uploadPostMediaToStorage(
                        context = requireContext().applicationContext,
                        mediaUri = mediaUri,
                        userId = authorId
                    )) {
                        is NetworkResult.Success -> {
                            remoteMediaUrl = uploadResult.data
                        }
                        is NetworkResult.Error -> {
                            // Safe error handling: Do not insert post, preserve user draft, allow retry
                            binding.btnPostHeader.isEnabled = true
                            binding.btnPostHeader.text = originalButtonText
                            Toast.makeText(requireContext(), "Media upload failed: ${uploadResult.message}", Toast.LENGTH_LONG).show()
                            return@launch
                        }
                    }
                }

                binding.btnPostHeader.text = "Publishing..."

                // 2. Insert post metadata into Supabase posts table with the permanent remote URL
                val result = postRepository.createPost(
                    text = content,
                    type = selectedCategory,
                    media = remoteMediaUrl,
                    location = selectedLocation,
                    context = requireContext().applicationContext
                )

                binding.btnPostHeader.isEnabled = true
                binding.btnPostHeader.text = originalButtonText

                when (result) {
                    is NetworkResult.Success -> {
                        AppRefreshManager.notifyDataChanged(RefreshTarget.HOME_FEED)
                        AppRefreshManager.notifyDataChanged(RefreshTarget.ANNOUNCEMENTS)
                        AppRefreshManager.notifyDataChanged(RefreshTarget.PROFILE)
                        Toast.makeText(requireContext(), "Post shared with your neighborhood!", Toast.LENGTH_SHORT).show()
                        findNavController().popBackStack()
                    }
                    is NetworkResult.Error -> {
                        // Safe error handling: preserve draft, allow retry
                        Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun setupCategorySelectors() {
        arguments?.getString("category")?.let {
            if (it.isNotBlank()) selectedCategory = it
        }

        val categoryViews = listOf(
            Triple(binding.catGeneral, binding.tvCatGeneral, binding.lineCatGeneral) to "General",
            Triple(binding.catAnnouncement, binding.tvCatAnnouncement, binding.lineCatAnnouncement) to "Announcement",
            Triple(binding.catHelpSupport, binding.tvCatHelpSupport, binding.lineCatHelpSupport) to "Help & Support",
            Triple(binding.catForSale, binding.tvCatForSale, binding.lineCatForSale) to "For Sale / Offer",
            Triple(binding.catEvent, binding.tvCatEvent, binding.lineCatEvent) to "Event"
        )

        updateCategorySelection(categoryViews)

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
            selectedMediaUri = null
            binding.imagePreviewContainer.visibility = View.GONE
            binding.ivVideoPreviewPlayIcon.visibility = View.GONE
            binding.badgeVideoAttachment.visibility = View.GONE
            binding.ivSelectedImage.setImageDrawable(null)
        }

        // Video Picker
        binding.btnAddVideo.setOnClickListener {
            pickVideoLauncher.launch("video/*")
        }

        // Location Tagging - Automatically fetch location from device GPS
        binding.btnAddLocation.setOnClickListener {
            fetchDeviceLocationAutomatically()
        }
    }

    /**
     * Automatically fetches the device's current location using GPS and Fused Location Provider.
     */
    private fun fetchDeviceLocationAutomatically(silent: Boolean = false) {
        val ctx = context ?: return

        if (!UserLocationManager.hasLocationPermission(ctx)) {
            if (!silent) {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
            return
        }

        if (!UserLocationManager.isGpsEnabled(ctx)) {
            if (!silent) {
                MaterialAlertDialogBuilder(ctx)
                    .setTitle("Device GPS / Location Disabled")
                    .setMessage("Location services are disabled on your device. Please turn on GPS to automatically detect your neighborhood.")
                    .setPositiveButton("Turn On") { _, _ ->
                        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    }
                    .setNegativeButton("Enter Manually") { _, _ ->
                        showManualLocationFallbackDialog()
                    }
                    .show()
            }
            return
        }

        // Fast path: instant cached device location
        val instantLoc = UserLocationManager.switchToDeviceLocation(ctx)
        if (instantLoc != null && instantLoc.displayName.isNotBlank()) {
            selectedLocation = instantLoc.displayName
            binding.tvAuthorLocation.text = selectedLocation
            if (!silent) {
                Toast.makeText(ctx, "📍 Location detected: $selectedLocation", Toast.LENGTH_SHORT).show()
            }
            return
        }

        if (!silent) {
            Toast.makeText(ctx, "📍 Detecting current location...", Toast.LENGTH_SHORT).show()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = UserLocationManager.detectLocation(ctx, forceRefresh = true)) {
                is LocationState.Success -> {
                    if (isAdded) {
                        val locName = result.location.displayName
                        selectedLocation = locName
                        binding.tvAuthorLocation.text = locName
                        UserLocationManager.saveSelectedLocation(ctx, result.location, isManual = false)
                        if (!silent) {
                            Toast.makeText(ctx, "📍 Location detected: $locName", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                is LocationState.GpsDisabled -> {
                    if (isAdded && !silent) {
                        Toast.makeText(ctx, "GPS is turned off. Please enable device location.", Toast.LENGTH_SHORT).show()
                    }
                }
                is LocationState.PermissionRequired -> {
                    if (isAdded && !silent) {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                }
                is LocationState.Error -> {
                    if (isAdded && !silent) {
                        Toast.makeText(ctx, "Could not detect location: ${result.message}", Toast.LENGTH_SHORT).show()
                        showManualLocationFallbackDialog()
                    }
                }
                else -> {}
            }
        }
    }

    private fun showManualLocationFallbackDialog() {
        val input = EditText(requireContext()).apply {
            setText(selectedLocation)
            hint = "Enter location name"
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Set Location Manually")
            .setView(input)
            .setPositiveButton("Set Location") { _, _ ->
                val loc = input.text.toString().trim()
                if (loc.isNotEmpty()) {
                    selectedLocation = loc
                    binding.tvAuthorLocation.text = selectedLocation
                    Toast.makeText(requireContext(), "Location updated: $selectedLocation", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("Auto-Detect") { _, _ ->
                fetchDeviceLocationAutomatically()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupSettingsRows() {
        binding.btnAudienceDropdown.setOnClickListener { toggleAudience() }
        binding.rowAudience.setOnClickListener { toggleAudience() }

        binding.rowNeighborhood.setOnClickListener {
            fetchDeviceLocationAutomatically()
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
