package com.biocatch.poc.scanningbackground.gui

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/** Opens this app's system settings screens, falling back to a more generic screen when one is missing. */
object SystemSettings {

    /** Null below API 23, where there is no battery optimization to be exempt from. */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Not exempt yet: the system dialog for this app only ("let it always run in the background"), so
     * the user does not search a list. Already exempt, or API < 23: this app's App info screen.
     */
    // REQUEST_IGNORE_BATTERY_OPTIMIZATIONS is limited by Google Play policy to accepted use cases.
    @SuppressLint("BatteryLife")
    fun openBatteryOptimization(context: Context): Boolean {
        val candidates = mutableListOf<Intent>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && isIgnoringBatteryOptimizations(context) == false) {
            candidates += Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, appUri(context))
            candidates += Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        }
        candidates += appDetailsIntent(context)
        return openFirst(context, candidates)
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
