package com.aroundus.app.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Production-ready Network Connectivity Manager and Monitor for AroundUS.
 *
 * Real-world applications require reliable offline detection, reactive network state
 * observation, cache-first resilience, and prevention of unhandled network exceptions
 * from terminating the app process.
 */
object NetworkUtils {

    private const val TAG = "AroundUsNetwork"

    private var appContext: Context? = null
    private val _isOnlineFlow = MutableStateFlow(true)
    val isOnlineFlow: StateFlow<Boolean> = _isOnlineFlow.asStateFlow()

    private var isCallbackRegistered = false

    /**
     * Initializes network monitoring across the application lifecycle.
     */
    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app

        val initialOnline = checkNetworkAvailable(app)
        _isOnlineFlow.value = initialOnline

        registerNetworkCallback(app)
    }

    /**
     * Synchronous check whether active network has Internet connectivity.
     */
    fun isNetworkAvailable(context: Context? = null): Boolean {
        val targetContext = context?.applicationContext ?: appContext ?: return true
        return checkNetworkAvailable(targetContext)
    }

    private fun checkNetworkAvailable(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val activeNetwork = cm.activeNetwork ?: return false
                val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ||
                         capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                         capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                         capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
            } else {
                @Suppress("DEPRECATION")
                val activeNetworkInfo = cm.activeNetworkInfo
                activeNetworkInfo != null && activeNetworkInfo.isConnectedOrConnecting
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking network status: ${e.message}")
            true // Fail open so transient security/permission issues don't permanently block UI
        }
    }

    private fun registerNetworkCallback(context: Context) {
        if (isCallbackRegistered) return

        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return

            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.d(TAG, "Network is available: $network")
                    _isOnlineFlow.value = true
                }

                override fun onLost(network: Network) {
                    Log.d(TAG, "Network was lost: $network")
                    // Double check if another network (e.g. mobile data fallback) is active
                    _isOnlineFlow.value = checkNetworkAvailable(context)
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    _isOnlineFlow.value = hasInternet
                }

                override fun onUnavailable() {
                    Log.d(TAG, "Network is unavailable")
                    _isOnlineFlow.value = false
                }
            }

            cm.registerNetworkCallback(request, callback)
            isCallbackRegistered = true
        } catch (e: Exception) {
            Log.w(TAG, "Could not register NetworkCallback: ${e.message}")
        }
    }

    /**
     * Determines whether a throwable (or its causal chain) is a network-related failure.
     */
    fun isNetworkException(throwable: Throwable?): Boolean {
        var current: Throwable? = throwable
        var depth = 0
        while (current != null && depth < 10) {
            if (current is UnknownHostException ||
                current is SocketTimeoutException ||
                current is ConnectException ||
                current is SocketException ||
                current is SSLException ||
                current is IOException ||
                current.javaClass.name.contains("HttpException", ignoreCase = true) ||
                current.javaClass.name.contains("Network", ignoreCase = true) ||
                current.message?.contains("Unable to resolve host", ignoreCase = true) == true ||
                current.message?.contains("Failed to connect to", ignoreCase = true) == true ||
                current.message?.contains("Software caused connection abort", ignoreCase = true) == true
            ) {
                return true
            }
            current = current.cause
            depth++
        }
        return false
    }
}

