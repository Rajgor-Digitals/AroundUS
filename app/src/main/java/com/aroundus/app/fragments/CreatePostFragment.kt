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
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import com.aroundus.app.repository.MarketplaceRepository
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
    private var selectedAnnouncementSubtype = "Community" // Community, Events, Alert
    private var selectedHelpSubtype = "General Help" // General Help, Lost & Found, Recommendations, Emergency, Services
    private var selectedAudience = "Public"
    private var selectedLocation = ""
    private var selectedMediaUri: Uri? = null

    private val postRepository = PostRepository()
    private val userRepository = UserRepository()
    private val marketplaceRepository = MarketplaceRepository()

    private val selectedBuySellPhotoUris = mutableListOf<Uri>()
    private var selectedBuySellCategory = "Furniture"
    private var selectedBuySellCondition = "Like New"
    private var isPublishingBuySell = false

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

    private val pickBuySellPhotosLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val remaining = 5 - selectedBuySellPhotoUris.size
            val toAdd = uris.take(remaining)
            selectedBuySellPhotoUris.addAll(toAdd)
            renderBuySellPhotoThumbnails()
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
        setupBuySellForm()
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
            if (selectedCategory.contains("Buy", ignoreCase = true) ||
                selectedCategory.equals("Sale", ignoreCase = true)) {
                handleBuySellPublish()
                return@setOnClickListener
            }

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

                val finalCategory = when {
                    selectedCategory.equals("Announcement", ignoreCase = true) -> selectedAnnouncementSubtype
                    selectedCategory.equals("Help & Support", ignoreCase = true) -> selectedHelpSubtype
                    else -> selectedCategory
                }

                // 2. Insert post metadata into Supabase posts table with the permanent remote URL
                val result = postRepository.createPost(
                    text = content,
                    type = finalCategory,
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
        arguments?.getString("category")?.let { incoming ->
            if (incoming.isNotBlank()) {
                when {
                    incoming.equals("Announcement", ignoreCase = true) ||
                    incoming.equals("Announcements", ignoreCase = true) -> {
                        selectedCategory = "Announcement"
                    }
                    incoming.equals("Community", ignoreCase = true) -> {
                        selectedCategory = "Announcement"
                        selectedAnnouncementSubtype = "Community"
                    }
                    incoming.equals("Event", ignoreCase = true) ||
                    incoming.equals("Events", ignoreCase = true) -> {
                        selectedCategory = "Announcement"
                        selectedAnnouncementSubtype = "Events"
                    }
                    incoming.equals("Alert", ignoreCase = true) ||
                    incoming.equals("Alerts", ignoreCase = true) -> {
                        selectedCategory = "Announcement"
                        selectedAnnouncementSubtype = "Alert"
                    }
                    incoming.equals("Help & Support", ignoreCase = true) ||
                    incoming.equals("Help", ignoreCase = true) -> {
                        selectedCategory = "Help & Support"
                    }
                    incoming.equals("General Help", ignoreCase = true) -> {
                        selectedCategory = "Help & Support"
                        selectedHelpSubtype = "General Help"
                    }
                    incoming.equals("Lost & Found", ignoreCase = true) ||
                    incoming.equals("Lost and Found", ignoreCase = true) -> {
                        selectedCategory = "Help & Support"
                        selectedHelpSubtype = "Lost & Found"
                    }
                    incoming.equals("Recommendations", ignoreCase = true) ||
                    incoming.equals("Recommendation", ignoreCase = true) -> {
                        selectedCategory = "Help & Support"
                        selectedHelpSubtype = "Recommendations"
                    }
                    incoming.equals("Services", ignoreCase = true) ||
                    incoming.equals("Service", ignoreCase = true) -> {
                        selectedCategory = "Help & Support"
                        selectedHelpSubtype = "Services"
                    }
                    incoming.equals("Buy & Sell", ignoreCase = true) ||
                    incoming.equals("Buy/Sell", ignoreCase = true) ||
                    incoming.equals("Sale", ignoreCase = true) ||
                    incoming.equals("Marketplace", ignoreCase = true) -> {
                        selectedCategory = "Buy & Sell"
                    }
                    else -> {
                        selectedCategory = "General"
                    }
                }
            }
        }

        val categoryViews = listOf(
            Triple(binding.catGeneral, binding.tvCatGeneral, binding.lineCatGeneral) to "General",
            Triple(binding.catAnnouncement, binding.tvCatAnnouncement, binding.lineCatAnnouncement) to "Announcement",
            Triple(binding.catHelpSupport, binding.tvCatHelpSupport, binding.lineCatHelpSupport) to "Help & Support",
            Triple(binding.catBuySell, binding.tvCatBuySell, binding.lineCatBuySell) to "Buy / Sell"
        )

        updateCategorySelection(categoryViews)

        categoryViews.forEach { (views, categoryName) ->
            views.first.setOnClickListener {
                selectedCategory = categoryName
                updateCategorySelection(categoryViews)
            }
        }

        setupAnnouncementSubtypeChips()
        setupHelpSubtypeChips()
    }

    private fun setupAnnouncementSubtypeChips() {
        fun updateSubtypeChips(selected: String) {
            val isCommunity = selected.equals("Community", ignoreCase = true)
            val isEvents = selected.equals("Events", ignoreCase = true) || selected.equals("Event", ignoreCase = true)
            val isAlert = selected.equals("Alert", ignoreCase = true) || selected.equals("Alerts", ignoreCase = true)

            // 1. Community Chip
            if (isCommunity) {
                binding.chipAnnounceCommunity.setBackgroundResource(R.drawable.bg_announcement_chip_community_selected)
                binding.tvAnnounceSubtypeCommunity.setTextColor(android.graphics.Color.parseColor("#15803D"))
                binding.ivAnnounceSubtypeCommunity.setColorFilter(android.graphics.Color.parseColor("#15803D"))
                binding.tvAnnounceSubtypeCommunity.typeface = android.graphics.Typeface.DEFAULT_BOLD
            } else {
                binding.chipAnnounceCommunity.setBackgroundResource(R.drawable.bg_announcement_chip_subtype_unselected)
                binding.tvAnnounceSubtypeCommunity.setTextColor(android.graphics.Color.parseColor("#475569"))
                binding.ivAnnounceSubtypeCommunity.setColorFilter(android.graphics.Color.parseColor("#64748B"))
                binding.tvAnnounceSubtypeCommunity.typeface = android.graphics.Typeface.DEFAULT
            }

            // 2. Events Chip
            if (isEvents) {
                binding.chipAnnounceEvent.setBackgroundResource(R.drawable.bg_announcement_chip_events_selected)
                binding.tvAnnounceSubtypeEvent.setTextColor(android.graphics.Color.parseColor("#0F766E"))
                binding.ivAnnounceSubtypeEvent.setColorFilter(android.graphics.Color.parseColor("#0F766E"))
                binding.tvAnnounceSubtypeEvent.typeface = android.graphics.Typeface.DEFAULT_BOLD
            } else {
                binding.chipAnnounceEvent.setBackgroundResource(R.drawable.bg_announcement_chip_subtype_unselected)
                binding.tvAnnounceSubtypeEvent.setTextColor(android.graphics.Color.parseColor("#475569"))
                binding.ivAnnounceSubtypeEvent.setColorFilter(android.graphics.Color.parseColor("#64748B"))
                binding.tvAnnounceSubtypeEvent.typeface = android.graphics.Typeface.DEFAULT
            }

            // 3. Alert Chip
            if (isAlert) {
                binding.chipAnnounceAlert.setBackgroundResource(R.drawable.bg_announcement_chip_alerts_selected)
                binding.tvAnnounceSubtypeAlert.setTextColor(android.graphics.Color.parseColor("#B91C1C"))
                binding.ivAnnounceSubtypeAlert.setColorFilter(android.graphics.Color.parseColor("#B91C1C"))
                binding.tvAnnounceSubtypeAlert.typeface = android.graphics.Typeface.DEFAULT_BOLD
            } else {
                binding.chipAnnounceAlert.setBackgroundResource(R.drawable.bg_announcement_chip_subtype_unselected)
                binding.tvAnnounceSubtypeAlert.setTextColor(android.graphics.Color.parseColor("#475569"))
                binding.ivAnnounceSubtypeAlert.setColorFilter(android.graphics.Color.parseColor("#64748B"))
                binding.tvAnnounceSubtypeAlert.typeface = android.graphics.Typeface.DEFAULT
            }
        }

        updateSubtypeChips(selectedAnnouncementSubtype)

        binding.chipAnnounceCommunity.setOnClickListener {
            selectedAnnouncementSubtype = "Community"
            updateSubtypeChips("Community")
        }

        binding.chipAnnounceEvent.setOnClickListener {
            selectedAnnouncementSubtype = "Events"
            updateSubtypeChips("Events")
        }

        binding.chipAnnounceAlert.setOnClickListener {
            selectedAnnouncementSubtype = "Alert"
            updateSubtypeChips("Alert")
        }
    }

    private fun updateCategorySelection(categoryViews: List<Pair<Triple<View, TextView, View>, String>>) {
        val greenColor = ContextCompat.getColor(requireContext(), R.color.app_green)
        val defaultTextColor = android.graphics.Color.parseColor("#334155")

        categoryViews.forEach { (views, categoryName) ->
            val isSelected = categoryName.equals(selectedCategory, ignoreCase = true)
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

        val isBuySell = selectedCategory.contains("Buy", ignoreCase = true) ||
                selectedCategory.equals("Sale", ignoreCase = true)
        val isAnnouncement = selectedCategory.equals("Announcement", ignoreCase = true)
        val isHelp = selectedCategory.equals("Help & Support", ignoreCase = true)

        if (isBuySell) {
            binding.layoutStandardPostForm.visibility = View.GONE
            binding.layoutBuySellForm.visibility = View.VISIBLE
            binding.layoutAnnouncementSubtype.visibility = View.GONE
            binding.layoutHelpSubtype.visibility = View.GONE
            binding.btnPostHeader.text = "List Item"
            if (binding.etBuySellLocality.text.isNullOrBlank() && selectedLocation.isNotBlank()) {
                binding.etBuySellLocality.setText(selectedLocation)
            }
        } else {
            binding.layoutStandardPostForm.visibility = View.VISIBLE
            binding.layoutBuySellForm.visibility = View.GONE
            binding.btnPostHeader.text = "Post"
            binding.layoutAnnouncementSubtype.visibility = if (isAnnouncement) View.VISIBLE else View.GONE
            binding.layoutHelpSubtype.visibility = if (isHelp) View.VISIBLE else View.GONE
        }
    }

    private fun setupHelpSubtypeChips() {
        val unselectedBg = R.drawable.bg_announcement_chip_subtype_unselected
        val unselectedTextColor = android.graphics.Color.parseColor("#475569")
        val unselectedIconColor = android.graphics.Color.parseColor("#64748B")

        fun updateHelpChips(selected: String) {
            val isGeneral = selected.equals("General Help", ignoreCase = true)
            val isLost = selected.equals("Lost & Found", ignoreCase = true) || selected.contains("Lost", ignoreCase = true)
            val isRec = selected.equals("Recommendations", ignoreCase = true) || selected.contains("Recommend", ignoreCase = true)
            val isEmergency = selected.equals("Emergency", ignoreCase = true)
            val isServices = selected.equals("Services", ignoreCase = true) || selected.equals("Service", ignoreCase = true)

            // 1. General Help
            if (isGeneral) {
                binding.chipHelpGeneral.setBackgroundResource(R.drawable.bg_help_chip_general_selected)
                binding.tvHelpSubtypeGeneral.setTextColor(android.graphics.Color.parseColor("#059669"))
                binding.ivHelpSubtypeGeneral.setColorFilter(android.graphics.Color.parseColor("#059669"))
                binding.tvHelpSubtypeGeneral.typeface = android.graphics.Typeface.DEFAULT_BOLD
            } else {
                binding.chipHelpGeneral.setBackgroundResource(unselectedBg)
                binding.tvHelpSubtypeGeneral.setTextColor(unselectedTextColor)
                binding.ivHelpSubtypeGeneral.setColorFilter(unselectedIconColor)
                binding.tvHelpSubtypeGeneral.typeface = android.graphics.Typeface.DEFAULT
            }

            // 2. Lost & Found
            if (isLost) {
                binding.chipHelpLostFound.setBackgroundResource(R.drawable.bg_help_chip_lost_found_selected)
                binding.tvHelpSubtypeLostFound.setTextColor(android.graphics.Color.parseColor("#D97706"))
                binding.ivHelpSubtypeLostFound.setColorFilter(android.graphics.Color.parseColor("#D97706"))
                binding.tvHelpSubtypeLostFound.typeface = android.graphics.Typeface.DEFAULT_BOLD
            } else {
                binding.chipHelpLostFound.setBackgroundResource(unselectedBg)
                binding.tvHelpSubtypeLostFound.setTextColor(unselectedTextColor)
                binding.ivHelpSubtypeLostFound.setColorFilter(unselectedIconColor)
                binding.tvHelpSubtypeLostFound.typeface = android.graphics.Typeface.DEFAULT
            }

            // 3. Recommendations
            if (isRec) {
                binding.chipHelpRecommendations.setBackgroundResource(R.drawable.bg_help_chip_recommendations_selected)
                binding.tvHelpSubtypeRecommendations.setTextColor(android.graphics.Color.parseColor("#2563EB"))
                binding.ivHelpSubtypeRecommendations.setColorFilter(android.graphics.Color.parseColor("#2563EB"))
                binding.tvHelpSubtypeRecommendations.typeface = android.graphics.Typeface.DEFAULT_BOLD
            } else {
                binding.chipHelpRecommendations.setBackgroundResource(unselectedBg)
                binding.tvHelpSubtypeRecommendations.setTextColor(unselectedTextColor)
                binding.ivHelpSubtypeRecommendations.setColorFilter(unselectedIconColor)
                binding.tvHelpSubtypeRecommendations.typeface = android.graphics.Typeface.DEFAULT
            }

            // 4. Emergency
            if (isEmergency) {
                binding.chipHelpEmergency.setBackgroundResource(R.drawable.bg_help_chip_emergency_selected)
                binding.tvHelpSubtypeEmergency.setTextColor(android.graphics.Color.parseColor("#DC2626"))
                binding.ivHelpSubtypeEmergency.setColorFilter(android.graphics.Color.parseColor("#DC2626"))
                binding.tvHelpSubtypeEmergency.typeface = android.graphics.Typeface.DEFAULT_BOLD
            } else {
                binding.chipHelpEmergency.setBackgroundResource(unselectedBg)
                binding.tvHelpSubtypeEmergency.setTextColor(unselectedTextColor)
                binding.ivHelpSubtypeEmergency.setColorFilter(unselectedIconColor)
                binding.tvHelpSubtypeEmergency.typeface = android.graphics.Typeface.DEFAULT
            }

            // 5. Services
            if (isServices) {
                binding.chipHelpServices.setBackgroundResource(R.drawable.bg_help_chip_services_selected)
                binding.tvHelpSubtypeServices.setTextColor(android.graphics.Color.parseColor("#7C3AED"))
                binding.ivHelpSubtypeServices.setColorFilter(android.graphics.Color.parseColor("#7C3AED"))
                binding.tvHelpSubtypeServices.typeface = android.graphics.Typeface.DEFAULT_BOLD
            } else {
                binding.chipHelpServices.setBackgroundResource(unselectedBg)
                binding.tvHelpSubtypeServices.setTextColor(unselectedTextColor)
                binding.ivHelpSubtypeServices.setColorFilter(unselectedIconColor)
                binding.tvHelpSubtypeServices.typeface = android.graphics.Typeface.DEFAULT
            }
        }

        updateHelpChips(selectedHelpSubtype)

        binding.chipHelpGeneral.setOnClickListener {
            selectedHelpSubtype = "General Help"
            updateHelpChips("General Help")
        }

        binding.chipHelpLostFound.setOnClickListener {
            selectedHelpSubtype = "Lost & Found"
            updateHelpChips("Lost & Found")
        }

        binding.chipHelpRecommendations.setOnClickListener {
            selectedHelpSubtype = "Recommendations"
            updateHelpChips("Recommendations")
        }

        binding.chipHelpEmergency.setOnClickListener {
            selectedHelpSubtype = "Emergency"
            updateHelpChips("Emergency")
        }

        binding.chipHelpServices.setOnClickListener {
            selectedHelpSubtype = "Services"
            updateHelpChips("Services")
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
    }

    private fun toggleAudience() {
        selectedAudience = if (selectedAudience == "Public") "Neighborhood Only" else "Public"
        binding.tvAudience.text = selectedAudience
        binding.tvRowAudienceValue.text = selectedAudience
        Toast.makeText(requireContext(), "Audience set to $selectedAudience", Toast.LENGTH_SHORT).show()
    }

    private fun setupBuySellForm() {
        binding.cardAddBuySellPhoto.setOnClickListener {
            if (selectedBuySellPhotoUris.size >= 5) {
                Toast.makeText(requireContext(), "Maximum 5 photos allowed.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            pickBuySellPhotosLauncher.launch("image/*")
        }

        setupBuySellCategoryChips()
        setupBuySellConditionChips()

        binding.etBuySellDescription.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.tvBuySellCharCounter.text = "${s?.length ?: 0}/1000"
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnBuySellPublish.setOnClickListener {
            handleBuySellPublish()
        }
    }

    private fun setupBuySellCategoryChips() {
        val categories = listOf(
            binding.chipBuySellCatFurniture to "Furniture",
            binding.chipBuySellCatVehicles to "Vehicles",
            binding.chipBuySellCatElectronics to "Electronics",
            binding.chipBuySellCatMobilePhones to "Mobile Phones",
            binding.chipBuySellCatHomeKitchen to "Home & Kitchen",
            binding.chipBuySellCatFashion to "Fashion",
            binding.chipBuySellCatBooks to "Books",
            binding.chipBuySellCatSports to "Sports",
            binding.chipBuySellCatOther to "Other"
        )

        categories.forEach { (view, catName) ->
            view.setOnClickListener {
                selectedBuySellCategory = catName
                categories.forEach { (v, _) ->
                    if (v == view) {
                        v.setBackgroundResource(R.drawable.bg_category_chip_active)
                        v.setTextColor(Color.WHITE)
                    } else {
                        v.setBackgroundResource(R.drawable.bg_category_chip_inactive)
                        v.setTextColor(Color.parseColor("#475569"))
                    }
                }
            }
        }
    }

    private fun setupBuySellConditionChips() {
        val conditions = listOf(
            binding.chipBuySellCondBrandNew to "Brand New",
            binding.chipBuySellCondLikeNew to "Like New",
            binding.chipBuySellCondExcellent to "Excellent",
            binding.chipBuySellCondGood to "Good",
            binding.chipBuySellCondFair to "Fair"
        )

        conditions.forEach { (view, condName) ->
            view.setOnClickListener {
                selectedBuySellCondition = condName
                conditions.forEach { (v, _) ->
                    if (v == view) {
                        v.setBackgroundResource(R.drawable.bg_category_chip_active)
                        v.setTextColor(Color.WHITE)
                    } else {
                        v.setBackgroundResource(R.drawable.bg_category_chip_inactive)
                        v.setTextColor(Color.parseColor("#475569"))
                    }
                }
            }
        }
    }

    private fun renderBuySellPhotoThumbnails() {
        val container = binding.layoutBuySellPhotosContainer
        val childrenToRemove = mutableListOf<View>()
        for (i in 1 until container.childCount) {
            childrenToRemove.add(container.getChildAt(i))
        }
        childrenToRemove.forEach { container.removeView(it) }

        binding.tvBuySellAddPhotoCount.text = "${selectedBuySellPhotoUris.size}/5 photos"

        selectedBuySellPhotoUris.forEachIndexed { index, uri ->
            val density = resources.displayMetrics.density
            val card = com.google.android.material.card.MaterialCardView(requireContext()).apply {
                layoutParams = ViewGroup.MarginLayoutParams(
                    (96 * density).toInt(),
                    (96 * density).toInt()
                ).apply {
                    marginEnd = (10 * density).toInt()
                }
                radius = 14 * density
                cardElevation = 2 * density
                strokeColor = Color.parseColor("#E2E8F0")
                strokeWidth = (1 * density).toInt()
            }

            val frame = android.widget.FrameLayout(requireContext()).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            val iv = android.widget.ImageView(requireContext()).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            }
            Glide.with(this).load(uri).centerCrop().into(iv)
            frame.addView(iv)

            val btnDelete = android.widget.ImageView(requireContext()).apply {
                val size = (26 * density).toInt()
                layoutParams = android.widget.FrameLayout.LayoutParams(size, size).apply {
                    gravity = Gravity.TOP or Gravity.END
                    topMargin = (6 * density).toInt()
                    rightMargin = (6 * density).toInt()
                }
                setImageResource(R.drawable.ic_close)
                setBackgroundResource(R.drawable.bg_circle_close_btn)
                val pad = (5 * density).toInt()
                setPadding(pad, pad, pad, pad)
                setColorFilter(Color.WHITE)
                setOnClickListener {
                    selectedBuySellPhotoUris.removeAt(index)
                    renderBuySellPhotoThumbnails()
                }
            }
            frame.addView(btnDelete)

            card.addView(frame)
            container.addView(card)
        }
    }

    private fun handleBuySellPublish() {
        if (isPublishingBuySell) return

        val title = binding.etBuySellTitle.text.toString().trim()
        val priceStr = binding.etBuySellPrice.text.toString().trim()
        val description = binding.etBuySellDescription.text.toString().trim()
        val locality = binding.etBuySellLocality.text.toString().trim().ifBlank {
            selectedLocation.ifBlank { "Bhujpur Village, Kutch" }
        }
        val brand = binding.etBuySellBrand.text.toString().trim()

        if (title.isBlank()) {
            binding.etBuySellTitle.error = "Please enter an item title"
            binding.etBuySellTitle.requestFocus()
            Toast.makeText(requireContext(), "Please enter an item title", Toast.LENGTH_SHORT).show()
            return
        }

        val price = priceStr.toDoubleOrNull()
        if (price == null || price < 0.0) {
            binding.etBuySellPrice.error = "Please enter a valid price (₹)"
            binding.etBuySellPrice.requestFocus()
            Toast.makeText(requireContext(), "Please enter a valid price", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedBuySellPhotoUris.isEmpty() && description.isBlank()) {
            Toast.makeText(requireContext(), "Please add at least one photo or description", Toast.LENGTH_SHORT).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            isPublishingBuySell = true
            binding.btnPostHeader.isEnabled = false
            binding.btnBuySellPublish.isEnabled = false
            binding.btnPostHeader.text = "Uploading..."
            binding.btnBuySellPublish.text = "Uploading..."

            val sellerId = userRepository.resolveValidAuthorId().ifBlank {
                SupabaseClient.getCurrentUserId() ?: ""
            }

            // 1. Upload photos to Supabase Storage
            val uploadedUrls = mutableListOf<String>()
            for (uri in selectedBuySellPhotoUris) {
                when (val uploadRes = marketplaceRepository.uploadListingMediaToStorage(
                    context = requireContext().applicationContext,
                    mediaUri = uri,
                    userId = sellerId
                )) {
                    is NetworkResult.Success -> {
                        uploadedUrls.add(uploadRes.data)
                    }
                    is NetworkResult.Error -> {}
                }
            }

            binding.btnPostHeader.text = "Publishing..."
            binding.btnBuySellPublish.text = "Publishing Listing..."

            // 2. Create Listing in Supabase Marketplace
            val listingResult = marketplaceRepository.createListing(
                context = requireContext().applicationContext,
                title = title,
                description = description,
                price = price,
                category = selectedBuySellCategory,
                condition = selectedBuySellCondition,
                brand = brand.ifBlank { null },
                locality = locality,
                imageUrls = uploadedUrls,
                isNegotiable = binding.switchBuySellNegotiable.isChecked
            )

            // 3. Announce in Community Feed as a Sale Post
            val primaryPhoto = uploadedUrls.firstOrNull()
            val feedText = buildString {
                append("🏷️ For Sale: ").append(title).append("\n")
                append("💰 Price: ₹").append(String.format(java.util.Locale.US, "%,.0f", price))
                if (binding.switchBuySellNegotiable.isChecked) append(" (Negotiable)")
                append("\n📍 Location: ").append(locality)
                if (description.isNotBlank()) {
                    append("\n\n").append(description)
                }
            }

            postRepository.createPost(
                text = feedText,
                type = "Sale",
                media = primaryPhoto,
                location = locality,
                context = requireContext().applicationContext
            )

            isPublishingBuySell = false
            binding.btnPostHeader.isEnabled = true
            binding.btnBuySellPublish.isEnabled = true
            binding.btnPostHeader.text = "List Item"
            binding.btnBuySellPublish.text = "Publish Listing in Marketplace"

            AppRefreshManager.notifyDataChanged(RefreshTarget.MARKETPLACE)
            AppRefreshManager.notifyDataChanged(RefreshTarget.HOME_FEED)
            AppRefreshManager.notifyDataChanged(RefreshTarget.PROFILE)

            Toast.makeText(requireContext(), "🎉 Item listed successfully in Marketplace!", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
