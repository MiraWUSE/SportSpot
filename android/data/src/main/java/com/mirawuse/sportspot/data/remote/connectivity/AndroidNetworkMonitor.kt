package com.mirawuse.sportspot.data.remote.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AndroidNetworkMonitor
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : NetworkMonitor {
        private val manager = context.getSystemService(ConnectivityManager::class.java)

        override fun isOnline(): Boolean {
            val network = manager?.activeNetwork ?: return false
            val capabilities = manager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
    }
