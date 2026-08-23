package com.sih.network.model.response

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Response from POST /api/v1/auth/device/register — §2 Auth table.
 *
 * Store [deviceId] and [deviceJwt] securely via EncryptedSharedPreferences.
 * [deviceJwt] is the Bearer token for all subsequent device-authenticated calls.
 */
@JsonClass(generateAdapter = true)
data class DeviceRegistrationResponse(
    @Json(name = "device_id")  val deviceId: String,
    @Json(name = "device_jwt") val deviceJwt: String
)
