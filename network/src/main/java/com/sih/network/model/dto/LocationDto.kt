package com.sih.network.model.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Location snapshot — exactly §1.2 shape.
 * Used inside both [SosRequestDto] and [GatewayUploadBatch].
 */
@JsonClass(generateAdapter = true)
data class LocationDto(
    @Json(name = "lat")        val lat: Double,
    @Json(name = "lng")        val lng: Double,
    @Json(name = "accuracy_m") val accuracyM: Double? = null
)
