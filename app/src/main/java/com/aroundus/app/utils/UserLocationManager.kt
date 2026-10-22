package com.aroundus.app.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import com.aroundus.app.network.SupabaseClient
import com.aroundus.app.repository.UserRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

data class UserLocationData(
    val displayName: String,
    val locality: String? = null,
    val subLocality: String? = null,
    val state: String? = null,
    val country: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val isManualSelection: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

sealed class LocationState {
    object Idle : LocationState()
    object Loading : LocationState()
    data class Success(val location: UserLocationData) : LocationState()
    object PermissionRequired : LocationState()
    object GpsDisabled : LocationState()
    data class Error(val message: String) : LocationState()
}

/**
 * Production-ready Automatic User Device Location Detection and Management.
 * Manages Google Play Services Fused Location, native LocationManager fallback,
 * reverse geocoding, persistence, and profile synchronization.
 */
object UserLocationManager {

    private const val PREFS_NAME = "aroundus_location_prefs"
    private const val KEY_SELECTED_DISPLAY_NAME = "selected_display_name"
    private const val KEY_SELECTED_LOCALITY = "selected_locality"
    private const val KEY_SELECTED_SUBLOCALITY = "selected_sublocality"
    private const val KEY_SELECTED_STATE = "selected_state"
    private const val KEY_SELECTED_COUNTRY = "selected_country"
    private const val KEY_SELECTED_LAT = "selected_lat"
    private const val KEY_SELECTED_LNG = "selected_lng"
    private const val KEY_IS_MANUAL_SELECTION = "is_manual_selection"

    private const val KEY_DEVICE_DISPLAY_NAME = "device_display_name"
    private const val KEY_DEVICE_LOCALITY = "device_locality"
    private const val KEY_DEVICE_SUBLOCALITY = "device_sublocality"
    private const val KEY_DEVICE_STATE = "device_state"
    private const val KEY_DEVICE_COUNTRY = "device_country"
    private const val KEY_DEVICE_LAT = "device_lat"
    private const val KEY_DEVICE_LNG = "device_lng"
    private const val KEY_DEVICE_TIMESTAMP = "device_timestamp"

    private var fusedClient: FusedLocationProviderClient? = null
    private var prefs: SharedPreferences? = null

    private val _locationState = MutableStateFlow<LocationState>(LocationState.Idle)
    val locationState: StateFlow<LocationState> = _locationState.asStateFlow()

    private val _currentSelectedLocation = MutableStateFlow<UserLocationData?>(null)
    val currentSelectedLocation: StateFlow<UserLocationData?> = _currentSelectedLocation.asStateFlow()

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
        if (fusedClient == null) {
            fusedClient = LocationServices.getFusedLocationProviderClient(context.applicationContext)
        }
        val cached = getSelectedLocation(context)
        _currentSelectedLocation.value = cached
        if (cached != null) {
            _locationState.value = LocationState.Success(cached)
        }
    }

    private fun getPrefs(context: Context): SharedPreferences {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
        return prefs!!
    }

    fun hasLocationPermission(context: Context): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fineGranted || coarseGranted
    }

    fun isGpsEnabled(context: Context): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
               locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    fun getSelectedLocation(context: Context): UserLocationData? {
        val p = getPrefs(context)
        val displayName = p.getString(KEY_SELECTED_DISPLAY_NAME, null) ?: return null
        val lat = if (p.contains(KEY_SELECTED_LAT)) p.getFloat(KEY_SELECTED_LAT, 0f).toDouble() else null
        val lng = if (p.contains(KEY_SELECTED_LNG)) p.getFloat(KEY_SELECTED_LNG, 0f).toDouble() else null
        return UserLocationData(
            displayName = displayName,
            locality = p.getString(KEY_SELECTED_LOCALITY, null),
            subLocality = p.getString(KEY_SELECTED_SUBLOCALITY, null),
            state = p.getString(KEY_SELECTED_STATE, null),
            country = p.getString(KEY_SELECTED_COUNTRY, null),
            latitude = lat,
            longitude = lng,
            isManualSelection = p.getBoolean(KEY_IS_MANUAL_SELECTION, false)
        )
    }

    fun isManualSelection(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_IS_MANUAL_SELECTION, false)
    }

    fun getLastDeviceLocation(context: Context): UserLocationData? {
        val p = getPrefs(context)
        val name = p.getString(KEY_DEVICE_DISPLAY_NAME, null) ?: return null
        val lat = if (p.contains(KEY_DEVICE_LAT)) p.getFloat(KEY_DEVICE_LAT, 0f).toDouble() else null
        val lng = if (p.contains(KEY_DEVICE_LNG)) p.getFloat(KEY_DEVICE_LNG, 0f).toDouble() else null
        val time = p.getLong(KEY_DEVICE_TIMESTAMP, 0L)
        return UserLocationData(
            displayName = name,
            locality = p.getString(KEY_DEVICE_LOCALITY, null) ?: name.substringBefore(",").trim(),
            subLocality = p.getString(KEY_DEVICE_SUBLOCALITY, null),
            state = p.getString(KEY_DEVICE_STATE, null),
            country = p.getString(KEY_DEVICE_COUNTRY, null),
            latitude = lat,
            longitude = lng,
            isManualSelection = false,
            timestamp = time
        )
    }

    /**
     * Instantly toggles back to the user's detected device location in 0ms without waiting for GPS fix.
     */
    fun switchToDeviceLocation(context: Context): UserLocationData? {
        val cached = getLastDeviceLocation(context)
        if (cached != null) {
            saveSelectedLocation(context, cached, isManual = false)
            return cached
        }
        return null
    }

    fun saveSelectedLocation(
        context: Context,
        location: UserLocationData,
        isManual: Boolean
    ) {
        val p = getPrefs(context)
        p.edit().apply {
            putString(KEY_SELECTED_DISPLAY_NAME, location.displayName)
            putString(KEY_SELECTED_LOCALITY, location.locality)
            putString(KEY_SELECTED_SUBLOCALITY, location.subLocality)
            putString(KEY_SELECTED_STATE, location.state)
            putString(KEY_SELECTED_COUNTRY, location.country)
            location.latitude?.let { putFloat(KEY_SELECTED_LAT, it.toFloat()) } ?: remove(KEY_SELECTED_LAT)
            location.longitude?.let { putFloat(KEY_SELECTED_LNG, it.toFloat()) } ?: remove(KEY_SELECTED_LNG)
            putBoolean(KEY_IS_MANUAL_SELECTION, isManual)
            apply()
        }

        val updated = location.copy(isManualSelection = isManual)
        _currentSelectedLocation.value = updated
        _locationState.value = LocationState.Success(updated)

        // Asynchronously sync to Supabase user profile if logged in
        syncLocationToProfileAsync(context, location.displayName)
    }

    fun saveDeviceLocation(context: Context, location: UserLocationData) {
        val p = getPrefs(context)
        p.edit().apply {
            putString(KEY_DEVICE_DISPLAY_NAME, location.displayName)
            putString(KEY_DEVICE_LOCALITY, location.locality)
            putString(KEY_DEVICE_SUBLOCALITY, location.subLocality)
            putString(KEY_DEVICE_STATE, location.state)
            putString(KEY_DEVICE_COUNTRY, location.country)
            location.latitude?.let { putFloat(KEY_DEVICE_LAT, it.toFloat()) } ?: remove(KEY_DEVICE_LAT)
            location.longitude?.let { putFloat(KEY_DEVICE_LNG, it.toFloat()) } ?: remove(KEY_DEVICE_LNG)
            putLong(KEY_DEVICE_TIMESTAMP, System.currentTimeMillis())
            apply()
        }
    }

    /**
     * Attempts to automatically detect the device's location.
     * Respects user's manual selection if forceRefresh is false.
     */
    suspend fun detectLocation(
        context: Context,
        forceRefresh: Boolean = false
    ): LocationState = withContext(Dispatchers.IO) {
        init(context)

        // If user made a manual selection and we aren't forcing GPS, preserve it
        if (!forceRefresh && isManualSelection(context)) {
            val selected = getSelectedLocation(context)
            if (selected != null) {
                _locationState.value = LocationState.Success(selected)
                return@withContext LocationState.Success(selected)
            }
        }

        if (!hasLocationPermission(context)) {
            val state = LocationState.PermissionRequired
            _locationState.value = state
            return@withContext state
        }

        if (!isGpsEnabled(context)) {
            val state = LocationState.GpsDisabled
            _locationState.value = state
            return@withContext state
        }

        // Instant local display if cached device location exists
        val cachedDevice = getLastDeviceLocation(context)
        if (cachedDevice != null && (forceRefresh || !isManualSelection(context))) {
            val instantData = cachedDevice.copy(isManualSelection = false)
            _currentSelectedLocation.value = instantData
            _locationState.value = LocationState.Success(instantData)
        } else {
            _locationState.value = LocationState.Loading
        }

        // Fast GPS coordinate retrieval (checks lastLocation first, then fresh fix with 3.5s timeout)
        val rawLocation = withTimeoutOrNull(4000L) {
            getDeviceCoordinates(context)
        }

        if (rawLocation == null) {
            val fallback = cachedDevice ?: getSelectedLocation(context)
            if (fallback != null) {
                val state = LocationState.Success(fallback.copy(isManualSelection = false))
                _locationState.value = state
                return@withContext state
            }
            val state = LocationState.Error("Unable to acquire GPS signal. Please try again or select manually.")
            _locationState.value = state
            return@withContext state
        }

        // Reverse geocoding to human-readable city/locality name (cached if within 500m)
        val locationData = reverseGeocode(context, rawLocation.latitude, rawLocation.longitude)

        // Save device location
        saveDeviceLocation(context, locationData)

        // If user didn't manually lock another location or is forcing refresh, update the active app location
        if (forceRefresh || !isManualSelection(context)) {
            saveSelectedLocation(context, locationData, isManual = false)
        }

        val state = LocationState.Success(locationData)
        _locationState.value = state
        return@withContext state
    }

    @SuppressLint("MissingPermission")
    private suspend fun getDeviceCoordinates(context: Context): Location? {
        val client = fusedClient ?: LocationServices.getFusedLocationProviderClient(context.applicationContext)

        // 1. Check lastLocation FIRST (fastest, typically < 10ms)
        var cachedLocation: Location? = null
        try {
            cachedLocation = suspendCancellableCoroutine<Location?> { cont ->
                client.lastLocation
                    .addOnSuccessListener { loc -> if (cont.isActive) cont.resume(loc) }
                    .addOnFailureListener { if (cont.isActive) cont.resume(null) }
            }
            // If last location is very fresh (< 10 minutes), return immediately!
            if (cachedLocation != null && (System.currentTimeMillis() - cachedLocation.time) < 10 * 60 * 1000) {
                return cachedLocation
            }
        } catch (_: Exception) {}

        // 2. Request fresh location with 3-second timeout
        try {
            val cts = com.google.android.gms.tasks.CancellationTokenSource()
            val freshLocation = withTimeoutOrNull(3000L) {
                suspendCancellableCoroutine<Location?> { cont ->
                    client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                        .addOnSuccessListener { loc -> if (cont.isActive) cont.resume(loc) }
                        .addOnFailureListener { if (cont.isActive) cont.resume(null) }
                    cont.invokeOnCancellation { cts.cancel() }
                }
            }
            if (freshLocation != null) return freshLocation
            if (cachedLocation != null) return cachedLocation
        } catch (_: Exception) {}

        // 3. Fallback to native Android LocationManager
        return withContext(Dispatchers.Main) {
            try {
                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                if (locationManager != null) {
                    val gps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    val net = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    val passive = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)

                    val candidates = listOfNotNull(gps, net, passive)
                    if (candidates.isNotEmpty()) {
                        return@withContext candidates.maxByOrNull { it.time }
                    }
                }
                null
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * Converts raw latitude and longitude to clean readable city/town/locality name.
     */
    suspend fun reverseGeocode(context: Context, lat: Double, lng: Double): UserLocationData = withContext(Dispatchers.IO) {
        // Fast path: if coordinates are within 500 meters of cached device location, return cached name instantly!
        val cached = getLastDeviceLocation(context)
        if (cached != null && cached.latitude != null && cached.longitude != null) {
            val distance = FloatArray(1)
            Location.distanceBetween(lat, lng, cached.latitude, cached.longitude, distance)
            if (distance[0] < 500f && !cached.displayName.isBlank()) {
                return@withContext cached.copy(
                    latitude = lat,
                    longitude = lng,
                    timestamp = System.currentTimeMillis()
                )
            }
        }

        if (!Geocoder.isPresent()) {
            return@withContext fallbackCoordsData(lat, lng)
        }

        try {
            val geocoder = Geocoder(context, Locale.getDefault())

            val addresses = withTimeoutOrNull(3000L) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    suspendCancellableCoroutine<List<Address>?> { cont ->
                        geocoder.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                if (cont.isActive) cont.resume(addresses)
                            }
                            override fun onError(errorMessage: String?) {
                                if (cont.isActive) cont.resume(null)
                            }
                        })
                    }
                } else {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(lat, lng, 1)
                }
            }

            if (!addresses.isNullOrEmpty()) {
                return@withContext parseAddressToLocationData(addresses[0], lat, lng)
            }
        } catch (_: Exception) {}

        if (cached != null && !cached.displayName.isBlank()) {
            return@withContext cached.copy(latitude = lat, longitude = lng)
        }
        return@withContext fallbackCoordsData(lat, lng)
    }

    private fun parseAddressToLocationData(address: Address, lat: Double, lng: Double): UserLocationData {
        val locality = address.locality ?: address.subAdminArea
        val subLocality = address.subLocality
        val adminArea = address.adminArea // State
        val country = address.countryName

        val cleanLocality = locality?.trim()
        val cleanSubLocality = subLocality?.trim()
        val cleanState = adminArea?.trim()

        val displayName = when {
            !cleanSubLocality.isNullOrBlank() && !cleanLocality.isNullOrBlank() && cleanSubLocality != cleanLocality -> {
                "$cleanSubLocality, $cleanLocality"
            }
            !cleanLocality.isNullOrBlank() && !cleanState.isNullOrBlank() -> {
                "$cleanLocality, $cleanState"
            }
            !cleanLocality.isNullOrBlank() -> {
                cleanLocality
            }
            !cleanSubLocality.isNullOrBlank() -> {
                cleanSubLocality
            }
            !cleanState.isNullOrBlank() -> {
                cleanState
            }
            else -> {
                address.featureName ?: "Nearby Area"
            }
        }

        return UserLocationData(
            displayName = displayName,
            locality = cleanLocality,
            subLocality = cleanSubLocality,
            state = cleanState,
            country = country,
            latitude = lat,
            longitude = lng,
            isManualSelection = false
        )
    }

    private fun fallbackCoordsData(lat: Double, lng: Double): UserLocationData {
        val roundedLat = String.format(Locale.US, "%.3f", lat)
        val roundedLng = String.format(Locale.US, "%.3f", lng)
        return UserLocationData(
            displayName = "Current Location ($roundedLat, $roundedLng)",
            latitude = lat,
            longitude = lng,
            isManualSelection = false
        )
    }

    private fun syncLocationToProfileAsync(context: Context, locationName: String) {
        if (locationName.isBlank()) return
        val currentUserId = SupabaseClient.getCurrentUserId() ?: return
        if (currentUserId.isBlank()) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userRepo = UserRepository()
                val profileRes = userRepo.getUserProfile(currentUserId)
                if (profileRes is com.aroundus.app.repository.NetworkResult.Success) {
                    val user = profileRes.data
                    if (user.location != locationName) {
                        userRepo.updateUserProfile(user.copy(location = locationName))
                        AppRefreshManager.notifyDataChanged(RefreshTarget.PROFILE)
                    }
                }
            } catch (_: Exception) {}
        }
    }
}
