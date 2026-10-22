package com.aroundus.app.fragments

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.children
import androidx.lifecycle.lifecycleScope
import com.aroundus.app.databinding.BottomSheetLocationPickerBinding
import com.aroundus.app.utils.AppRefreshManager
import com.aroundus.app.utils.LocationState
import com.aroundus.app.utils.RefreshTarget
import com.aroundus.app.utils.UserLocationData
import com.aroundus.app.utils.UserLocationManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LocationBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: BottomSheetLocationPickerBinding? = null
    private val binding get() = _binding!!

    var onLocationSelected: ((UserLocationData) -> Unit)? = null

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineGranted || coarseGranted) {
            triggerGpsDetection()
        } else {
            val shouldShowRationale = shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) ||
                                      shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (!shouldShowRationale) {
                showPermanentlyDeniedAlert()
            } else {
                showPermissionDeniedAlert()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetLocationPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        updateCurrentLocationDisplay()
        setupListeners()
        setupSuggestionChips()
        observeLocationState()
    }

    private fun updateCurrentLocationDisplay() {
        val current = UserLocationManager.getSelectedLocation(requireContext())
        if (current != null && current.displayName.isNotBlank()) {
            binding.tvActiveLocationName.text = current.displayName
            binding.tvLocationSourceTag.text = if (current.isManualSelection) "Manual" else "GPS"
        } else {
            binding.tvActiveLocationName.text = "No location set"
            binding.tvLocationSourceTag.text = "None"
        }
    }

    private fun setupListeners() {
        binding.btnCloseSheet.setOnClickListener {
            dismiss()
        }

        binding.cardUseCurrentLocation.setOnClickListener {
            checkPermissionsAndDetect()
        }

        binding.btnApplyManual.setOnClickListener {
            applyManualInput()
        }

        binding.etManualLocation.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                applyManualInput()
                true
            } else {
                false
            }
        }
    }

    private fun applyManualInput() {
        val input = binding.etManualLocation.text.toString().trim()
        if (input.isBlank()) {
            binding.etManualLocation.error = "Please enter a location name"
            return
        }

        val locationData = UserLocationData(
            displayName = input,
            locality = input,
            isManualSelection = true
        )

        UserLocationManager.saveSelectedLocation(requireContext(), locationData, isManual = true)
        AppRefreshManager.notifyDataChanged(RefreshTarget.HOME_FEED)
        AppRefreshManager.notifyDataChanged(RefreshTarget.MARKETPLACE)
        onLocationSelected?.invoke(locationData)
        Toast.makeText(requireContext(), "Location set to: $input", Toast.LENGTH_SHORT).show()
        dismiss()
    }

    private fun setupSuggestionChips() {
        binding.chipGroupSuggestions.children.forEach { child ->
            if (child is Chip) {
                child.setOnClickListener {
                    val locationName = child.text.toString().trim()
                    val locationData = UserLocationData(
                        displayName = locationName,
                        locality = locationName.substringBefore(",").trim(),
                        isManualSelection = true
                    )
                    UserLocationManager.saveSelectedLocation(requireContext(), locationData, isManual = true)
                    AppRefreshManager.notifyDataChanged(RefreshTarget.HOME_FEED)
                    AppRefreshManager.notifyDataChanged(RefreshTarget.MARKETPLACE)
                    onLocationSelected?.invoke(locationData)
                    Toast.makeText(requireContext(), "Location set to: $locationName", Toast.LENGTH_SHORT).show()
                    dismiss()
                }
            }
        }
    }

    private fun checkPermissionsAndDetect() {
        binding.layoutAlertBox.visibility = View.GONE
        if (!UserLocationManager.hasLocationPermission(requireContext())) {
            showPermissionRationaleDialog()
            return
        }

        if (!UserLocationManager.isGpsEnabled(requireContext())) {
            showGpsDisabledAlert()
            return
        }

        triggerGpsDetection()
    }

    private fun showPermissionRationaleDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Location Permission Needed")
            .setMessage(
                "AroundUS connects you with verified neighbors, local alerts, and community marketplace deals in your actual area. Please grant location access to detect your neighborhood."
            )
            .setPositiveButton("Allow Access") { _, _ ->
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
            .setNegativeButton("Choose Manually") { dialog, _ ->
                dialog.dismiss()
                binding.etManualLocation.requestFocus()
            }
            .show()
    }

    private fun showPermissionDeniedAlert() {
        binding.layoutAlertBox.visibility = View.VISIBLE
        binding.tvAlertMessage.text = "Location permission denied. Tap to grant access or select manually below."
        binding.btnAlertAction.text = "GRANT"
        binding.btnAlertAction.setOnClickListener {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun showPermanentlyDeniedAlert() {
        binding.layoutAlertBox.visibility = View.VISIBLE
        binding.tvAlertMessage.text = "Location permission permanently disabled. Tap Settings to enable."
        binding.btnAlertAction.text = "SETTINGS"
        binding.btnAlertAction.setOnClickListener {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", requireContext().packageName, null)
            }
            startActivity(intent)
        }
    }

    private fun showGpsDisabledAlert() {
        binding.layoutAlertBox.visibility = View.VISIBLE
        binding.tvAlertMessage.text = "Device location / GPS is turned off. Tap to turn on."
        binding.btnAlertAction.text = "ENABLE"
        binding.btnAlertAction.setOnClickListener {
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }
    }

    private fun triggerGpsDetection() {
        val b = _binding ?: return
        val ctx = context ?: return

        if (!UserLocationManager.hasLocationPermission(ctx)) {
            showPermissionRationaleDialog()
            return
        }

        if (!UserLocationManager.isGpsEnabled(ctx)) {
            showGpsDisabledAlert()
            return
        }

        // Instant toggle: if cached device location exists, switch and dismiss in 0ms!
        val instantLoc = UserLocationManager.switchToDeviceLocation(ctx)
        if (instantLoc != null) {
            Toast.makeText(ctx, "📍 Current location: ${instantLoc.displayName}", Toast.LENGTH_SHORT).show()
            AppRefreshManager.notifyDataChanged(RefreshTarget.HOME_FEED)
            AppRefreshManager.notifyDataChanged(RefreshTarget.MARKETPLACE)
            onLocationSelected?.invoke(instantLoc)
            dismiss()

            // Run silent background refresh to update coordinates if user moved
            lifecycleScope.launch(Dispatchers.IO) {
                UserLocationManager.detectLocation(ctx, forceRefresh = true)
            }
            return
        }

        // Only show spinner if device was never located yet
        b.pbGpsLoading.visibility = View.VISIBLE
        b.ivGpsActionArrow.visibility = View.GONE
        b.tvGpsStatus.text = "Detecting current coordinates & locality..."
        b.cardUseCurrentLocation.isEnabled = false

        lifecycleScope.launch {
            try {
                when (val result = UserLocationManager.detectLocation(ctx, forceRefresh = true)) {
                    is LocationState.Success -> {
                        val loc = result.location
                        if (isAdded) {
                            Toast.makeText(ctx, "📍 Detected: ${loc.displayName}", Toast.LENGTH_SHORT).show()
                            AppRefreshManager.notifyDataChanged(RefreshTarget.HOME_FEED)
                            AppRefreshManager.notifyDataChanged(RefreshTarget.MARKETPLACE)
                            onLocationSelected?.invoke(loc)
                            dismiss()
                        }
                    }
                    is LocationState.GpsDisabled -> {
                        if (isAdded) showGpsDisabledAlert()
                    }
                    is LocationState.PermissionRequired -> {
                        if (isAdded) showPermissionDeniedAlert()
                    }
                    is LocationState.Error -> {
                        val currentBinding = _binding
                        if (isAdded && currentBinding != null) {
                            currentBinding.layoutAlertBox.visibility = View.VISIBLE
                            currentBinding.tvAlertMessage.text = result.message
                            currentBinding.btnAlertAction.text = "RETRY"
                            currentBinding.btnAlertAction.setOnClickListener { triggerGpsDetection() }
                        }
                    }
                    else -> {}
                }
            } finally {
                _binding?.apply {
                    pbGpsLoading.visibility = View.GONE
                    ivGpsActionArrow.visibility = View.VISIBLE
                    tvGpsStatus.text = "Fetch live location using device GPS"
                    cardUseCurrentLocation.isEnabled = true
                }
            }
        }
    }

    private fun observeLocationState() {
        viewLifecycleOwner.lifecycleScope.launch {
            UserLocationManager.currentSelectedLocation.collect { loc ->
                if (loc != null && _binding != null) {
                    binding.tvActiveLocationName.text = loc.displayName
                    binding.tvLocationSourceTag.text = if (loc.isManualSelection) "Manual" else "GPS"
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "LocationBottomSheet"
        fun newInstance(): LocationBottomSheetFragment = LocationBottomSheetFragment()
    }
}
