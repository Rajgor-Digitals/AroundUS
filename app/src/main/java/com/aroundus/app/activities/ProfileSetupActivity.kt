package com.aroundus.app.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.AnimationSet
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.ScaleAnimation
import android.view.animation.TranslateAnimation
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import androidx.lifecycle.lifecycleScope
import com.aroundus.app.R
import com.aroundus.app.databinding.ActivityProfileSetupBinding
import com.aroundus.app.models.UserEntity
import com.aroundus.app.network.SupabaseClient
import com.aroundus.app.repository.NetworkResult
import com.aroundus.app.repository.UserRepository
import com.bumptech.glide.Glide
import com.google.android.material.chip.Chip
import kotlinx.coroutines.launch

class ProfileSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileSetupBinding
    private val userRepository = UserRepository()

    private var selectedImageUri: Uri? = null
    private var selectedPresetResId: Int? = null

    private var targetEmail: String = ""
    private var targetName: String = ""
    private var targetUserId: String = ""

    // Modern photo picker launcher
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            selectedPresetResId = null

            // Immediately display preview in CircleImageView
            Glide.with(this)
                .load(uri)
                .centerCrop()
                .into(binding.ivAvatar)

            // Clear any active preset avatar rings
            clearPresetRings()

            // Visual feedback badge
            binding.badgePhotoSelected.visibility = View.VISIBLE
            binding.tvAddPhoto.text = "Change Selected Photo"
        }
    }

    private val profileLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            detectLocationForProfile()
        } else {
            Toast.makeText(this, "Permission denied. You can enter your neighborhood manually.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        resolveInitialUserData()
        setupEntranceAnimations()
        populateDefaults()
        setupPhotoPickerListeners()
        setupPresetAvatarListeners()
        setupLocationSuggestions()
        setupBioHelperTags()
        setupTextWatchers()
        setupInterestsTracker()
        setupActionButtons()
    }

    private fun resolveInitialUserData() {
        targetEmail = intent.getStringExtra("email")
            ?: SupabaseClient.getCurrentUserEmail()
            ?: "neighbor@aroundus.app"

        targetName = intent.getStringExtra("name")
            ?: SupabaseClient.getCurrentUserName()
            ?: SupabaseClient.getAccountName(targetEmail)
            ?: targetEmail.substringBefore("@").replace(".", " ").split(" ")
                .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }

        targetUserId = SupabaseClient.getCurrentUserId()
            ?: SupabaseClient.getAccountUserId(targetEmail)
            ?: ("usr_" + Math.abs(targetEmail.hashCode()))
    }

    private fun setupEntranceAnimations() {
        // Step tracker spring in
        val headerAnim = AnimationSet(true).apply {
            addAnimation(AlphaAnimation(0f, 1f).apply { duration = 400 })
            addAnimation(TranslateAnimation(0f, 0f, -30f, 0f).apply {
                duration = 450
                interpolator = DecelerateInterpolator()
            })
        }
        binding.llStepHeader.startAnimation(headerAnim)

        // Avatar card overshoot bounce
        val avatarAnim = AnimationSet(true).apply {
            addAnimation(AlphaAnimation(0f, 1f).apply { duration = 500; startOffset = 80 })
            addAnimation(ScaleAnimation(0.9f, 1f, 0.9f, 1f, ScaleAnimation.RELATIVE_TO_SELF, 0.5f, ScaleAnimation.RELATIVE_TO_SELF, 0.5f).apply {
                duration = 550
                startOffset = 80
                interpolator = OvershootInterpolator(1.1f)
            })
        }
        binding.cardAvatar.startAnimation(avatarAnim)

        // Details card slide up
        val cardAnim = AnimationSet(true).apply {
            addAnimation(AlphaAnimation(0f, 1f).apply { duration = 550; startOffset = 160 })
            addAnimation(TranslateAnimation(0f, 0f, 50f, 0f).apply {
                duration = 550
                startOffset = 160
                interpolator = DecelerateInterpolator()
            })
        }
        binding.cardForm.startAnimation(cardAnim)
    }

    private fun populateDefaults() {
        if (targetName.isNotBlank()) {
            binding.etFullName.setText(targetName)
        }

        // Auto-generate clean username
        val rawUsername = targetName.lowercase().replace(" ", "_").replace("[^a-z0-9_]".toRegex(), "")
        val generatedUsername = if (rawUsername.isNotBlank()) rawUsername else targetEmail.substringBefore("@")
        binding.etUsername.setText(generatedUsername)

        // Pre-fill location from detected location if available
        val detected = com.aroundus.app.utils.UserLocationManager.getSelectedLocation(this)?.displayName
        if (!detected.isNullOrBlank()) {
            binding.etNeighborhood.setText(detected)
        } else {
            binding.etNeighborhood.setText("")
        }
    }

    private fun setupPhotoPickerListeners() {
        val openPicker = View.OnClickListener {
            pickImageLauncher.launch("image/*")
        }

        binding.avatarContainer.setOnClickListener(openPicker)
        binding.ivAvatar.setOnClickListener(openPicker)
        binding.ivAddPhoto.setOnClickListener(openPicker)
        binding.tvAddPhoto.setOnClickListener(openPicker)
    }

    private fun setupPresetAvatarListeners() {
        binding.framePreset1.setOnClickListener {
            applyPresetAvatar(R.drawable.img_avatar_gaurav_rajgor, 1)
        }
        binding.framePreset2.setOnClickListener {
            applyPresetAvatar(R.drawable.img_avatar_priya_sharma, 2)
        }
        binding.framePreset3.setOnClickListener {
            applyPresetAvatar(R.drawable.img_avatar_amit_joshi, 3)
        }
        binding.framePreset4.setOnClickListener {
            applyPresetAvatar(R.drawable.avatar_cultural_club, 4)
        }
    }

    private fun applyPresetAvatar(drawableRes: Int, presetIndex: Int) {
        selectedPresetResId = drawableRes
        selectedImageUri = null

        Glide.with(this)
            .load(drawableRes)
            .centerCrop()
            .into(binding.ivAvatar)

        clearPresetRings()
        when (presetIndex) {
            1 -> binding.ringPreset1.visibility = View.VISIBLE
            2 -> binding.ringPreset2.visibility = View.VISIBLE
            3 -> binding.ringPreset3.visibility = View.VISIBLE
            4 -> binding.ringPreset4.visibility = View.VISIBLE
        }

        binding.badgePhotoSelected.visibility = View.VISIBLE
        binding.tvAddPhoto.text = "Custom Community Avatar Selected"
    }

    private fun clearPresetRings() {
        binding.ringPreset1.visibility = View.GONE
        binding.ringPreset2.visibility = View.GONE
        binding.ringPreset3.visibility = View.GONE
        binding.ringPreset4.visibility = View.GONE
    }

    private fun setupLocationSuggestions() {
        binding.chipAutoDetectLocation.setOnClickListener {
            checkPermissionsAndDetectForProfile()
        }
        binding.chipLocMundra.setOnClickListener {
            binding.etNeighborhood.setText("Mundra, Gujarat")
        }
        binding.chipLocKutch.setOnClickListener {
            binding.etNeighborhood.setText("Kutch West Community")
        }
        binding.chipLocMundraPort.setOnClickListener {
            binding.etNeighborhood.setText("Mundra Port Road")
        }
    }

    private fun checkPermissionsAndDetectForProfile() {
        if (!com.aroundus.app.utils.UserLocationManager.hasLocationPermission(this)) {
            profileLocationPermissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }
        detectLocationForProfile()
    }

    private fun detectLocationForProfile() {
        Toast.makeText(this, "Detecting your location via GPS...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            when (val res = com.aroundus.app.utils.UserLocationManager.detectLocation(this@ProfileSetupActivity, forceRefresh = true)) {
                is com.aroundus.app.utils.LocationState.Success -> {
                    binding.etNeighborhood.setText(res.location.displayName)
                    Toast.makeText(this@ProfileSetupActivity, "📍 Location detected: ${res.location.displayName}", Toast.LENGTH_SHORT).show()
                }
                is com.aroundus.app.utils.LocationState.GpsDisabled -> {
                    Toast.makeText(this@ProfileSetupActivity, "GPS is turned off. Please enable location in device settings.", Toast.LENGTH_LONG).show()
                }
                is com.aroundus.app.utils.LocationState.Error -> {
                    Toast.makeText(this@ProfileSetupActivity, res.message, Toast.LENGTH_SHORT).show()
                }
                else -> {}
            }
        }
    }

    private fun setupBioHelperTags() {
        binding.tagBioNeighbor.setOnClickListener { appendBioTag("Friendly neighbor") }
        binding.tagBioPets.setOnClickListener { appendBioTag("🐕 Pet lover") }
        binding.tagBioGarden.setOnClickListener { appendBioTag("🌱 Gardening fan") }
        binding.tagBioHelp.setOnClickListener { appendBioTag("🤝 Happy to help") }
    }

    private fun appendBioTag(tag: String) {
        val currentBio = binding.etBio.text.toString().trim()
        val newBio = if (currentBio.isEmpty()) {
            tag
        } else if (!currentBio.contains(tag)) {
            "$currentBio • $tag"
        } else {
            currentBio
        }

        if (newBio.length <= 150) {
            binding.etBio.setText(newBio)
            binding.etBio.setSelection(newBio.length)
        }
    }

    private fun setupTextWatchers() {
        binding.etBio.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                val len = s?.length ?: 0
                binding.tvBioCounter.text = "$len/150"
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupInterestsTracker() {
        val updateCount = {
            var selected = 0
            for (i in 0 until binding.chipGroupInterests.childCount) {
                val child = binding.chipGroupInterests.getChildAt(i)
                if (child is Chip && child.isChecked) {
                    selected++
                }
            }
            binding.tvInterestsCount.text = "$selected selected"
        }

        for (i in 0 until binding.chipGroupInterests.childCount) {
            val child = binding.chipGroupInterests.getChildAt(i)
            if (child is Chip) {
                child.setOnCheckedChangeListener { _, _ -> updateCount() }
            }
        }
        updateCount()
    }

    private fun setupActionButtons() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        val skipAction = View.OnClickListener {
            proceedToMain(showToast = false)
        }
        binding.btnSkip.setOnClickListener(skipAction)
        binding.btnSkipBottom.setOnClickListener(skipAction)

        binding.btnSaveProfile.setOnClickListener {
            saveAndCompleteProfile()
        }
    }

    private fun saveAndCompleteProfile() {
        val fullName = binding.etFullName.text.toString().trim()
        val username = binding.etUsername.text.toString().trim()
        val neighborhood = binding.etNeighborhood.text.toString().trim()
        val bio = binding.etBio.text.toString().trim()

        if (fullName.isEmpty()) {
            binding.etFullName.error = "Display name is required"
            binding.etFullName.requestFocus()
            return
        }

        if (username.isEmpty()) {
            binding.etUsername.error = "Username is required"
            binding.etUsername.requestFocus()
            return
        }

        if (neighborhood.isEmpty()) {
            binding.etNeighborhood.error = "Neighborhood / location is required"
            binding.etNeighborhood.requestFocus()
            return
        }

        // Show loading spinner on button
        binding.btnSaveProfile.isEnabled = false
        binding.btnSaveProfile.text = ""
        binding.progressBarSaving.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                var remoteAvatarUrl: String? = null

                // 1. Upload photo if custom image was selected
                val uri = selectedImageUri
                if (uri != null) {
                    when (val uploadRes = userRepository.uploadProfilePhotoToStorage(this@ProfileSetupActivity, uri, targetUserId)) {
                        is NetworkResult.Success -> {
                            remoteAvatarUrl = uploadRes.data
                            SupabaseClient.saveAvatarPath(targetUserId, remoteAvatarUrl)
                            if (targetEmail.isNotBlank()) SupabaseClient.saveAvatarPath(targetEmail, remoteAvatarUrl)
                            userRepository.updateProfilePhotoUrl(targetUserId, targetEmail, remoteAvatarUrl)
                        }
                        is NetworkResult.Error -> {
                            // Non-fatal, continue saving profile data
                        }
                    }
                } else if (selectedPresetResId != null) {
                    // Save preset avatar key locally
                    SupabaseClient.saveAvatarPath(targetUserId, "res:${selectedPresetResId}")
                }

                // 2. Build UserEntity
                val userEntity = UserEntity(
                    userId = targetUserId,
                    name = fullName,
                    email = targetEmail,
                    location = neighborhood,
                    bio = if (bio.isNotEmpty()) bio else "Resident of $neighborhood.",
                    profilePhoto = remoteAvatarUrl
                )

                // 3. Save profile to Supabase & local cache
                userRepository.updateUserProfile(userEntity)

                // Save to UserLocationManager
                com.aroundus.app.utils.UserLocationManager.saveSelectedLocation(
                    this@ProfileSetupActivity,
                    com.aroundus.app.utils.UserLocationData(
                        displayName = neighborhood,
                        locality = neighborhood,
                        isManualSelection = true
                    ),
                    isManual = true
                )

                // 4. Update session
                SupabaseClient.saveSession(
                    accessToken = SupabaseClient.getAccessToken() ?: "session_$targetUserId",
                    userId = targetUserId,
                    email = targetEmail,
                    name = fullName
                )

                proceedToMain(showToast = true, userName = fullName)
            } catch (e: Exception) {
                Toast.makeText(this@ProfileSetupActivity, "Setup completed locally: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                proceedToMain(showToast = true, userName = fullName)
            } finally {
                binding.btnSaveProfile.isEnabled = true
                binding.btnSaveProfile.text = "Complete Setup & Enter AroundUS"
                binding.progressBarSaving.visibility = View.GONE
            }
        }
    }

    private fun proceedToMain(showToast: Boolean, userName: String = "") {
        if (showToast) {
            val displayName = if (userName.isNotBlank()) userName else "Neighbor"
            Toast.makeText(this, "Welcome to AroundUS, $displayName!", Toast.LENGTH_SHORT).show()
        }
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finishAffinity()
    }
}
