package com.sih.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.UUID

/**
 * Secure, encrypted storage for device identity credentials.
 *
 * [deviceId] and [deviceJwt] are issued by POST /api/v1/auth/device/register
 * and must be kept secure — stored in EncryptedSharedPreferences (AES256-GCM).
 *
 * Keys:
 *   pref_device_id       — the UUID assigned by the backend after registration
 *   pref_device_jwt      — the JWT used to authenticate SOS uploads
 *   pref_installation_id — stable local UUID generated on first install (used as
 *                          device_id fallback for offline SOS before registration)
 *
 * ## Device Identity Flow (P0.4.6)
 * The Day 1 contract §1.2 defines device_id as "installation ID of originating phone"
 * and §1.9 says it's "generated on first app install." To prevent multiple offline SOS
 * messages from using different random IDs, we generate ONE stable installationId at
 * first launch and persist it. After backend registration succeeds, SosRepository uses
 * the backend-assigned device_id (pref_device_id) instead; the installationId is kept
 * as a fallback for offline SOS created before registration completes.
 *
 * On 401 responses from any protected endpoint, callers should re-register
 * (call /auth/device/register again) to obtain a fresh jwt and call
 * [saveCredentials] with the new values.
 *
 * NOTE: Ported from the Component C repo with the Hilt injection removed (this
 * module does not use Hilt). All keys, behaviour, and storage format are identical
 * to Component C's DevicePreferences so identities are interoperable.
 */
class DevicePreferences(private val context: Context) {
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
     * Used as [device_id] in SOS records created before backend registration completes,
     * ensuring that multiple offline SOS messages from the same installation always
     * carry the same device identity (rule P0.4.6 — critical invariant).
     *
     * After registration succeeds, [getDeviceId()] (backend-assigned) should be
     * preferred. This is kept as a permanent stable fallback.
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

    /** Call from SosRepository immediately after createSos(). */
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