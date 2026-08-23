package com.sih.network.model.response

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Response from GET /api/v1/sos/{uuid}/status — §2 SOS Ingestion table.
 *
 * [status] is a backend-defined string. The contract specifies {status}
 * without enumerating the backend values. Agent C treats any non-null value
 * as "reached the server" and displays it verbatim.
 *
 * OPEN QUESTION (flagged to Agent D): enumerate backend status values.
 * Once confirmed, Agent C will add a mapping to user-friendly display strings.
 */
@JsonClass(generateAdapter = true)
data class SosStatusResponse(
    @Json(name = "status") val status: String
)
