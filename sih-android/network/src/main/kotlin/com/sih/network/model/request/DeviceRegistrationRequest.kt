package com.sih.network.model.request

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Device registration request body.
 * POST /api/v1/auth/device/register — §2 Auth table.
 * No auth header required.
 */
@JsonClass(generateAdapter = true)
data class DeviceRegistrationRequest(
    @Json(name = "device_model")  val deviceModel: String,
    @Json(name = "app_version")   val appVersion: String
)
