package com.biocatch.poc.scanningbackground.scan

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.os.Process
import com.biocatch.poc.scanningbackground.model.InstalledAppInfo
import java.security.MessageDigest

/** Lists user-installed apps with their package name, first install time and signing-certificate SHA-256. */
class InstalledAppsScanner(private val context: Context) {

    private val packageManager: PackageManager get() = context.packageManager

    /** Below API 30 Android does not filter the package list, so every app is visible without QAP. */
    fun isQapGranted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
            context.checkPermission(
                Manifest.permission.QUERY_ALL_PACKAGES,
                Process.myPid(),
                Process.myUid(),
            ) == PackageManager.PERMISSION_GRANTED

    fun scanUserApps(): List<InstalledAppInfo> =
        installedPackages()
            .filterNot { it.isSystemApp() }
            .map(::toInstalledAppInfo)

    private fun installedPackages(): List<PackageInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledPackages(0)
        }

    /** Preinstalled apps keep FLAG_SYSTEM even after a Play update, so this excludes them too. */
    private fun PackageInfo.isSystemApp(): Boolean =
        ((applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM) != 0

    private fun toInstalledAppInfo(info: PackageInfo): InstalledAppInfo =
        try {
            InstalledAppInfo(info.packageName, info.firstInstallTime, signingCertificatesSha256(info.packageName))
        } catch (e: Exception) {
            InstalledAppInfo(info.packageName, info.firstInstallTime, emptyList(), "${e.javaClass.simpleName}: ${e.message}")
        }

    /**
     * Read per package: requesting signatures for every package in one getInstalledPackages call
     * can exceed the binder transaction limit on devices with many apps.
     */
    private fun signingCertificatesSha256(packageName: String): List<String> {
        val signatures: Array<out Signature> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners.orEmpty()
        } else {
            @Suppress("DEPRECATION")
            packageInfo(packageName, PackageManager.GET_SIGNATURES).signatures.orEmpty()
        }
        return signatures.map(::sha256)
    }

    private fun packageInfo(packageName: String, flags: Int): PackageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, flags)
        }

    private fun sha256(signature: Signature): String =
        MessageDigest.getInstance("SHA-256")
            .digest(signature.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
