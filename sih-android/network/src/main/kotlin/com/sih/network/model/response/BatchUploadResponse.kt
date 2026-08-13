package com.sih.network.model.response

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Response from POST /api/v1/sos/batch — §2 SOS Ingestion table.
 *
 * HTTP 202 Accepted.
 *
 * After receiving this:
 *  - Mark all UUIDs in [acceptedUuids] as status=uploaded in Room
 *  - Mark all UUIDs in [duplicateUuids] as status=uploaded in Room
 *    (backend already had them; they are safely received)
 *  - Do NOT delete records from Room — they may still be needed for relay
 */
@JsonClass(generateAdapter = true)
data class BatchUploadResponse(
    @Json(name = "accepted_uuids")   val acceptedUuids: List<String> = emptyList(),
    @Json(name = "duplicate_uuids")  val duplicateUuids: List<String> = emptyList()
)
