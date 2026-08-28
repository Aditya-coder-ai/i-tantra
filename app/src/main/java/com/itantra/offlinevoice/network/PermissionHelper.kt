package com.itantra.offlinevoice.network

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Manages runtime Android permissions required for offline device-to-device communications
 * across Wi-Fi Direct, Bluetooth, and microphone audio capturing for API 23 through 35.
 */
object PermissionHelper {

    /**
     * Returns the complete array of permissions required for full app functionality
     * tailored to the running Android SDK version.
     */
    fun getRequiredPermissions(): Array<String> {
        val permissions = mutableListOf<String>()

        // 1. Microphone
        permissions.add(Manifest.permission.RECORD_AUDIO)

        // 2. Wi-Fi Direct / Nearby Devices
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }

        // 3. Bluetooth
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        } else {
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }

        // 4. Location (Required for Wi-Fi P2P and Bluetooth discovery on Android <= 12)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        return permissions.toTypedArray()
    }

    /**
     * Checks if all required permissions are granted.
     */
    fun hasAllPermissions(context: Context): Boolean {
        return getRequiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Checks if Wi-Fi Direct permissions are granted.
     */
    fun hasWifiDirectPermissions(context: Context): Boolean {
        val required = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            required.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            required.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return required.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    }

    /**
     * Checks if Bluetooth permissions are granted.
     */
    fun hasBluetoothPermissions(context: Context): Boolean {
        val required = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            required.add(Manifest.permission.BLUETOOTH_SCAN)
            required.add(Manifest.permission.BLUETOOTH_CONNECT)
            required.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        } else {
            required.add(Manifest.permission.BLUETOOTH)
            required.add(Manifest.permission.BLUETOOTH_ADMIN)
            required.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return required.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    }

    /**
     * Checks if audio recording permission is granted.
     */
    fun hasAudioPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Returns a list of missing permissions.
     */
    fun getMissingPermissions(context: Context): List<String> {
        return getRequiredPermissions().filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
    }
}
