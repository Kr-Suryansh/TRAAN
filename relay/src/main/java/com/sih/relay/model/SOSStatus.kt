package com.sih.relay.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Device-side lifecycle status of an SOS message.
 *
 * Schema source: day1-contracts-and-repo-setup.md §1.2
 * Contract values: pending_local | in_relay | uploaded
 *
 * These values are device-only tracking — they are carried in the relay payload
 * but the backend has its own record state and does not use these values for
 * server-side logic.
 *
 * @SerialName values match the contract exactly.
 * Do NOT rename or remove values without updating the contract document first.
 */
@Serializable
enum class SOSStatus {
    @SerialName("pending_local") PENDING_LOCAL,
    @SerialName("in_relay")      IN_RELAY,
    @SerialName("uploaded")      UPLOADED
}
