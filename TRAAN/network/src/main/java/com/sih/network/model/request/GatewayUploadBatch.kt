package com.sih.network.model.request

import com.sih.network.model.dto.LocationDto
import com.sih.network.model.dto.SosRequestDto
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * What a gateway device POSTs to the backend — §1.4 GatewayUploadBatch.
 *
 * A gateway can be:
 *  - The victim's own phone once it regains signal
 *  - Any bystander or responder running the app
 *
 * [sosBatch] contains every SOSRequest this device holds (own + relayed
 * from the mesh). The backend handles deduplication by UUID.
 *
 * NOTE: [gatewayLocation] is required by the schema. If the device cannot
 * obtain a fresh location, send the last known location. If no location is
 * available at all, send lat=0.0/lng=0.0 and log a warning — never block
 * the upload for a missing location.
 */
@JsonClass(generateAdapter = true)
data class GatewayUploadBatch(
    @Json(name = "gateway_device_id") val gatewayDeviceId: String,
    @Json(name = "gateway_location")  val gatewayLocation: LocationDto,
    @Json(name = "uploaded_at")       val uploadedAt: String,
    @Json(name = "sos_batch")         val sosBatch: List<SosRequestDto>
)
