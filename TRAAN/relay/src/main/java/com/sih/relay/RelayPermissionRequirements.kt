package com.sih.relay

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Version-aware permission helper for the relay mesh. Pure logic — no UI.
 *
 * Ownership split (COMPONENT_C_TO_AB_RELAY_HANDOFF §18): Component C owns the
 * foreground-location permission (its one-shot GPS fix). This helper is the
 * A+B-owned set: Bluetooth, Wi-Fi, nearby Wi-Fi devices, and notifications.
 *
 * API-level matrix:
 *  - API 34+ : BLUETOOTH_SCAN, BLUETOOTH_ADVERTISE, BLUETOOTH_CONNECT,
 *              NEARBY_WIFI_DEVICES, POST_NOTIFICATIONS
 *  - API 33  : same as above (NEARBY_WIFI_DEVICES exists, runtime-grantable)
 *  - API 31-32: BLUETOOTH_SCAN, BLUETOOTH_ADVERTISE, BLUETOOTH_CONNECT
 *  - API < 31: ACCESS_FINE_LOCATION (legacy BLE scanning requirement)
 *
 * The helper never launches UIs or dialogs — callers decide how to surface
 * the result (e.g. an Activity permission launcher).
 */
object RelayPermissionRequirements {

    fun requiredPermissions(apiLevel: Int = Build.VERSION.SDK_INT): List<String> = when {
        apiLevel >= Build.VERSION_CODES.TIRAMISU -> listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.NEARBY_WIFI_DEVICES,
            Manifest.permission.POST_NOTIFICATIONS
        )
        apiLevel >= Build.VERSION_CODES.S -> listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT
        )
        else -> listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    /** Permissions in [requiredPermissions] that this app has NOT been granted. */
    fun missingPermissions(
        context: Context,
        apiLevel: Int = Build.VERSION.SDK_INT
    ): List<String> = requiredPermissions(apiLevel).filter { permission ->
        ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
    }

    /** True when every permission in [requiredPermissions] is granted. */
    fun hasRequiredPermissions(
        context: Context,
        apiLevel: Int = Build.VERSION.SDK_INT
    ): Boolean = missingPermissions(context, apiLevel).isEmpty()

    /** True when the Bluetooth radio is on (null adapter → false). */
    fun isBluetoothEnabled(context: Context): Boolean =
        BluetoothAdapter.getDefaultAdapter()?.isEnabled == true

    /** Intent to prompt the user to turn Bluetooth on. Null if no adapter exists. */
    fun bluetoothEnableIntent(): Intent? =
        if (BluetoothAdapter.getDefaultAdapter() != null) {
            Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        } else {
            null
        }

    /** True when the Wi-Fi radio is on. */
    fun isWifiOn(context: Context): Boolean =
        (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
            ?.isWifiEnabled == true

    /** Intent to open the system Wi-Fi settings. */
    fun wifiSettingsIntent(): Intent = Intent(Settings.ACTION_WIFI_SETTINGS)
}