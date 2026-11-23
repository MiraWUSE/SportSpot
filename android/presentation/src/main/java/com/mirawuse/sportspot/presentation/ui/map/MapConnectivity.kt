package com.mirawuse.sportspot.presentation.ui.map

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

internal enum class MapNetworkStatus { NO_NETWORK, UNVERIFIED, AVAILABLE }

internal fun mapNetworkStatus(
    internet: Boolean?,
    validated: Boolean,
): MapNetworkStatus =
    when {
        internet == false -> MapNetworkStatus.NO_NETWORK
        internet == true && validated -> MapNetworkStatus.AVAILABLE
        else -> MapNetworkStatus.UNVERIFIED
    }

// Only events for the current default network may change its capabilities or lose it.
internal data class MapConnection(
    val handle: Long? = null,
    val status: MapNetworkStatus = MapNetworkStatus.NO_NETWORK,
) {
    fun available(handle: Long): MapConnection = MapConnection(handle, MapNetworkStatus.UNVERIFIED)

    fun capabilities(
        handle: Long,
        internet: Boolean,
        validated: Boolean,
    ): MapConnection = if (handle == this.handle) copy(status = mapNetworkStatus(internet, validated)) else this

    fun lost(handle: Long): MapConnection = if (handle == this.handle) MapConnection() else this
}

@Composable
internal fun rememberMapNetworkStatus(): MapNetworkStatus {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(ConnectivityManager::class.java) }

    fun snapshot(): MapConnection {
        val service = manager ?: return MapConnection(status = MapNetworkStatus.UNVERIFIED)
        val network = service.activeNetwork ?: return MapConnection()
        val capabilities = service.getNetworkCapabilities(network)
        return MapConnection(
            network.networkHandle,
            mapNetworkStatus(
                capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
            ),
        )
    }
    var connection by remember(manager) { mutableStateOf(snapshot()) }
    DisposableEffect(manager) {
        var observing = true
        val handler = Handler(Looper.getMainLooper())
        val callback =
            object : ConnectivityManager.NetworkCallback() {
                private fun update(event: (MapConnection) -> MapConnection) {
                    handler.post { if (observing) connection = event(connection) }
                }

                override fun onAvailable(network: Network) = update { it.available(network.networkHandle) }

                override fun onLost(network: Network) = update { it.lost(network.networkHandle) }

                override fun onCapabilitiesChanged(
                    network: Network,
                    capabilities: NetworkCapabilities,
                ) = update {
                    it.capabilities(
                        network.networkHandle,
                        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                    )
                }
            }
        manager?.registerDefaultNetworkCallback(callback)
        // Close the gap between the initial snapshot and registering the callback.
        // No synchronous capability reads are made from network callbacks.
        connection = snapshot()
        onDispose {
            observing = false
            manager?.unregisterNetworkCallback(callback)
        }
    }
    return connection.status
}
