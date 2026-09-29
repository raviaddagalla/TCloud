package com.tcloud.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitors network state for backup decisions.
 */
@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    private val _networkState = MutableStateFlow(getCurrentNetworkState())
    val networkState: StateFlow<NetworkState> = _networkState.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _networkState.value = getCurrentNetworkState()
        }

        override fun onLost(network: Network) {
            _networkState.value = getCurrentNetworkState()
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            _networkState.value = getCurrentNetworkState()
        }
    }

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)
    }

    private fun getCurrentNetworkState(): NetworkState {
        val activeNetwork = connectivityManager.activeNetwork
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
            ?: return NetworkState.Disconnected

        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkState.WiFi
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)) {
                    NetworkState.MobileData
                } else {
                    NetworkState.Roaming
                }
            }
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkState.WiFi // Treat as Wi-Fi equivalent
            else -> NetworkState.Disconnected
        }
    }

    fun isConnected(): Boolean = _networkState.value != NetworkState.Disconnected

    fun isWiFi(): Boolean = _networkState.value == NetworkState.WiFi
}

enum class NetworkState {
    WiFi,
    MobileData,
    Roaming,
    Disconnected
}
