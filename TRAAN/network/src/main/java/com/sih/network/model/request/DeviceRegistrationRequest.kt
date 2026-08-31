package com.sih.network.model.request

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Device registration request body.
 * POST /api/v1/auth/device/register — §2 Auth table.
 * No auth header required.
 *
 * Model A (resolved device_id contract, §1.2 / §1.9):
 * The Android installation UUID is the canonical device_id. The backend
 * accepts it, upserts the Device row with that UUID as the primary key,
 * and echoes the same UUID back in [DeviceRegistrationResponse.deviceId].
 */
@JsonClass(generateAdapter = true)
data class DeviceRegistrationRequest(
    @Json(name = "device_id")    val deviceId: String,   // stable installationId from DevicePreferences
    @Json(name = "device_model") val deviceModel: String,
    @Json(name = "app_version")  val appVersion: String
)
