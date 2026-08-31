package com.sih.relay.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class SosLocation(
    val lat: Float,
    val lng: Float,

    /** GPS accuracy in metres. Null if the device hasn't acquired a GPS fix yet. */
    @SerialName("accuracy_m")
    val accuracyMeters: Float? = null
)
