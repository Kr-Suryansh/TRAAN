package com.sih.data.model

/**
 * Device-side status of a SOSRequest — §1.2 status enum.
 *
 * This field is NEVER serialised to the network. It exists only in Room
 * to let the UI show the citizen their SOS's local lifecycle.
 *
 * Values (exact contract strings, lowercase):
 *   pending_local  — stored on device, not yet sent anywhere
 *   in_relay       — being propagated via the offline mesh
 *   uploaded       — successfully received by the backend
 */
enum class SosStatus(val apiValue: String) {
    PENDING_LOCAL("pending_local"),
    IN_RELAY("in_relay"),
    UPLOADED("uploaded");

    companion object {
        fun fromApiValue(value: String): SosStatus =
            entries.find { it.apiValue == value } ?: PENDING_LOCAL
    }
}