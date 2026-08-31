package com.sih.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Secure, encrypted storage for device identity credentials.
 *
 * [deviceId] and [deviceJwt] are issued by POST /api/v1/auth/device/register
 * and must be kept secure — stored in EncryptedSharedPreferences (AES256-GCM).
 *
 * Keys:
 *   pref_device_id       — the Android-generated installation UUID confirmed by backend
 *                          (under Model A: always == pref_installation_id)
 *   pref_device_jwt      — the JWT used to authenticate SOS uploads
 *   pref_installation_id — stable local UUID generated on first install; sent to backend
 *                          during registration as the canonical device_id (Model A)
 *
 * ## Device Identity Flow — Model A (resolved contract, §1.2 §1.9)
 * Android generates ONE stable installationId at first launch and persists it.
 * On registration, this UUID is sent to the backend as device_id. The backend
 * upserts the Device row using this UUID as its primary key and echoes it back.
 * Result: pref_device_id == pref_installation_id always. The two-identity split
 * (pre-registration vs post-registration device_id) is eliminated.
 *
 * On 401 responses from any protected endpoint, callers should re-register
 * (call /auth/device/register again) to obtain a fresh jwt and call
 * [saveCredentials] with the new values. Re-registration is idempotent.
 */
@Singleton
class DevicePreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME          = "sih_secure_device_prefs"
        private const val KEY_DEVICE_ID       = "pref_device_id"
        private const val KEY_DEVICE_JWT      = "pref_device_jwt"
        private const val KEY_LAST_SOS_UUID   = "pref_last_sos_uuid"
        private const val KEY_INSTALLATION_ID = "pref_installation_id"
        private const val KEY_ONBOARDING_SKIPPED = "pref_onboarding_skipped"
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

    // ── Stable local installation identity ────────────────────────────────────

    /**
     * Returns the stable local installation UUID.
     *
     * Generated ONCE at first call and persisted permanently in EncryptedSharedPreferences.
     * This UUID is sent to the backend as device_id during registration (Model A).
     * The backend echoes it back, so [getDeviceId()] will always equal [getOrCreateInstallationId()]
     * once registration completes — the fallback path in [SosRepository] resolves to the same value.
     *
     * Contract reference: §1.2 device_id = "installation ID of originating phone"
     * and §1.9 device_id = "UUID, generated on first app install."
     */
    fun getOrCreateInstallationId(): String {
        val existing = prefs.getString(KEY_INSTALLATION_ID, null)
        if (!existing.isNullOrBlank()) return existing

        val generated = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_INSTALLATION_ID, generated).apply()
        return generated
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

    // ── Onboarding ────────────────────────────────────────────────────────────

    fun isOnboardingSkipped(): Boolean = prefs.getBoolean(KEY_ONBOARDING_SKIPPED, false)

    fun setOnboardingSkipped(skipped: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_SKIPPED, skipped).apply()
    }
}
