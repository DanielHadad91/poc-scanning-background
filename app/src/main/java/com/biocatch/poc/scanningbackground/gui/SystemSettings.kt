package com.biocatch.poc.scanningbackground.gui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import com.biocatch.poc.scanningbackground.R

/** Opens this app's system settings screens, falling back to a more generic screen when one is missing. */
object SystemSettings {

    /** Null below API 23, where there is no battery optimization to be exempt from. */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Opens this app's own App info screen, where the battery item -> "Unrestricted" is one tap away,
     * and says so in a toast. Falls back to the system-wide battery optimization list.
     *
     * Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS would show a per-app "always run in the
     * background" dialog directly, which is the most reliable fix for OEM and Doze delays, but it
     * requires declaring REQUEST_IGNORE_BATTERY_OPTIMIZATIONS. This app must not add permissions
     * beyond its original set, so that action is deliberately not used.
     */
    fun openBatteryOptimization(context: Context): Boolean {
        val candidates = mutableListOf(appDetailsIntent(context))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            candidates += Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        }
        val hint = when (isIgnoringBatteryOptimizations(context)) {
            true -> R.string.toast_battery_already_exempt
            false -> R.string.toast_battery_how_to
            null -> null
        }
        val opened = openFirst(context, candidates)
        if (opened && hint != null) Toast.makeText(context, hint, Toast.LENGTH_LONG).show()
        return opened
    }

    fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, appUri(context))

    fun appUri(context: Context): Uri = Uri.fromParts("package", context.packageName, null)

    /** Starts the first intent the device can handle; false if none can. */
    fun openFirst(context: Context, intents: List<Intent>): Boolean {
        for (intent in intents) {
            try {
                context.startActivity(intent)
                return true
            } catch (e: ActivityNotFoundException) {
                // Try the next, more generic screen.
            }
        }
        return false
    }
}
