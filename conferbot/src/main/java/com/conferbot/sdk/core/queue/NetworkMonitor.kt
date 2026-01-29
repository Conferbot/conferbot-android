package com.conferbot.sdk.core.queue

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

/**
 * Network connectivity monitor using ConnectivityManager.
 * Provides real-time network status updates via StateFlow.
 */
class NetworkMonitor(private val context: Context) {

    companion object {
        private const val TAG = "NetworkMonitor"
    }

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isOnline = MutableStateFlow(checkCurrentNetworkState())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _networkType = MutableStateFlow(getCurrentNetworkType())
    val networkType: StateFlow<NetworkType> = _networkType.asStateFlow()

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var isMonitoring = false

    /**
     * Start monitoring network connectivity changes
     */
    fun startMonitoring() {
        if (isMonitoring) {
            Log.d(TAG, "Already monitoring network")
            return
        }

        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Network available")
                _isOnline.value = true
                updateNetworkType()
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "Network lost")
                // Check if there are other active networks
                if (!checkCurrentNetworkState()) {
                    _isOnline.value = false
                    _networkType.value = NetworkType.NONE
                }
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                val hasInternet = networkCapabilities.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_INTERNET
                )
                val hasValidated = networkCapabilities.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_VALIDATED
                )

                Log.d(TAG, "Network capabilities changed - Internet: $hasInternet, Validated: $hasValidated")

                if (hasInternet && hasValidated) {
                    _isOnline.value = true
                    updateNetworkType(networkCapabilities)
                }
            }

            override fun onUnavailable() {
                Log.d(TAG, "Network unavailable")
                _isOnline.value = false
                _networkType.value = NetworkType.NONE
            }
        }

        try {
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback!!)
            isMonitoring = true
            Log.d(TAG, "Network monitoring started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback", e)
        }
    }

    /**
     * Stop monitoring network connectivity
     */
    fun stopMonitoring() {
        if (!isMonitoring) {
            return
        }

        networkCallback?.let {
            try {
                connectivityManager.unregisterNetworkCallback(it)
                Log.d(TAG, "Network monitoring stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unregister network callback", e)
            }
        }
        networkCallback = null
        isMonitoring = false
    }

    /**
     * Check current network connectivity state
     */
    private fun checkCurrentNetworkState(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Get the current network type
     */
    private fun getCurrentNetworkType(): NetworkType {
        val activeNetwork = connectivityManager.activeNetwork ?: return NetworkType.NONE
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            ?: return NetworkType.NONE

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
            else -> NetworkType.OTHER
        }
    }

    /**
     * Update network type based on current capabilities
     */
    private fun updateNetworkType(capabilities: NetworkCapabilities? = null) {
        val caps = capabilities ?: run {
            val activeNetwork = connectivityManager.activeNetwork ?: return
            connectivityManager.getNetworkCapabilities(activeNetwork) ?: return
        }

        _networkType.value = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
            else -> NetworkType.OTHER
        }
    }

    /**
     * Check if currently on metered connection (cellular data)
     */
    fun isMeteredConnection(): Boolean {
        return connectivityManager.isActiveNetworkMetered
    }

    /**
     * Get current online status synchronously
     */
    fun isCurrentlyOnline(): Boolean = _isOnline.value
}

/**
 * Network type enumeration
 */
enum class NetworkType {
    NONE,
    WIFI,
    CELLULAR,
    ETHERNET,
    OTHER
}
