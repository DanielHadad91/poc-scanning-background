package com.biocatch.poc.scanningbackground.network

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

object NetworkState {

    // ACCESS_NETWORK_STATE is declared by the host app's manifest (an SDK declares no permissions).
    @SuppressLint("MissingPermission")
    fun isOnline(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = manager.activeNetwork ?: return false
            val capabilities = manager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
        @Suppress("DEPRECATION")
        return manager.activeNetworkInfo?.isConnected == true
    }
}
