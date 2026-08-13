package com.sih.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Secure, encrypted storage for device identity credentials.
 *
 * [deviceId] and [deviceJwt] are issued by POST /api/v1/auth/device/register
 * and must be kept secure — stored in EncryptedSharedPreferences (AES256-GCM).
 *
 * Keys:
 *   pref_device_id  — the UUID assigned by the backend
 *   pref_device_jwt — the JWT used to authenticate SOS uploads
 *
 * On 401 responses from any protected endpoint, callers should re-register
 * (call /auth/device/register again) to obtain a fresh jwt and call
 * [saveCredentials] with the new values.
 */
@Singleton
class DevicePreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME        = "sih_secure_device_prefs"
        private const val KEY_DEVICE_ID     = "pref_device_id"
        private const val KEY_DEVICE_JWT    = "pref_device_jwt"
        private const val KEY_LAST_SOS_UUID = "pref_last_sos_uuid"
    }

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getDeviceId(): String?  = prefs.getString(KEY_DEVICE_ID, null)
    fun getDeviceJwt(): String? = prefs.getString(KEY_DEVICE_JWT, null)

    /** Returns true only when both id and jwt are stored. */
    fun isRegistered(): Boolean =
        !getDeviceId().isNullOrBlank() && !getDeviceJwt().isNullOrBlank()

    /** Persists credentials returned from /auth/device/register. */
    fun saveCredentials(deviceId: String, deviceJwt: String) {
        prefs.edit()
            .putString(KEY_DEVICE_ID, deviceId)
            .putString(KEY_DEVICE_JWT, deviceJwt)
            .apply()
    }

    /** Call on 401 or explicit logout to force re-registration. */
    fun clearCredentials() {
        prefs.edit()
            .remove(KEY_DEVICE_ID)
            .remove(KEY_DEVICE_JWT)
            .apply()
    }

    // ── Last SOS UUID — survive process kill so Status screen persists ────────

    /**
     * Returns the UUID of the most recently created SOS by this citizen.
     * Used on cold start to resume the Status screen instead of showing Home.
     * Null if no SOS has been created yet, or after [clearLastSosUuid] is called.
     */
    fun getLastSosUuid(): String? = prefs.getString(KEY_LAST_SOS_UUID, null)

    /** Call from [com.sih.data.repository.SosRepository] immediately after createSos(). */
    fun saveLastSosUuid(uuid: String) {
        prefs.edit().putString(KEY_LAST_SOS_UUID, uuid).apply()
    }

    /**
     * Call when the citizen explicitly navigates back to Home to create a new SOS,
     * indicating they no longer want to resume the previous Status screen.
     */
    fun clearLastSosUuid() {
        prefs.edit().remove(KEY_LAST_SOS_UUID).apply()
    }
}
