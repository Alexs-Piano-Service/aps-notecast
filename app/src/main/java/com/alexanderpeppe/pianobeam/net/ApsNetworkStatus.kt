package com.alexanderpeppe.pianobeam.net

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.alexanderpeppe.pianobeam.R
import androidx.core.content.ContextCompat
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

object ApsNetworkStatus {
    fun hasNetworkPermissions(context: Context): Boolean =
        listOf(
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE
        ).all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }

    fun canReachInternet(context: Context): Boolean {
        if (!hasNetworkPermissions(context)) return false
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun userMessage(context: Context, throwable: Throwable? = null): String {
        if (!hasNetworkPermissions(context)) return context.getString(R.string.network_permission)
        if (!canReachInternet(context)) return context.getString(R.string.network_connectivity)
        return if (throwable?.isNetworkFailure() == true) {
            context.getString(R.string.network_connectivity)
        } else {
            context.getString(R.string.network_service)
        }
    }

    fun isLikelyNetworkFailure(throwable: Throwable?): Boolean = throwable?.isNetworkFailure() == true

    private fun Throwable.isNetworkFailure(): Boolean =
        this is UnknownHostException ||
            this is SocketTimeoutException ||
            this is ConnectException ||
            this is NoRouteToHostException ||
            this is SocketException ||
            this is SSLException ||
            cause?.isNetworkFailure() == true
}
