package com.sih.network.model.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Medical profile snapshot — exactly §1.1 shape.
 *
 * Embedded inside [SosRequestDto] as [medical_snapshot].
 * This is a point-in-time copy of the device's UserMedicalProfile at
 * the moment of SOS creation — it is never re-typed during an emergency.
 *
 * NOTE: All fields are nullable as per the contract; a citizen who skipped
 * onboarding will have a null snapshot.
 */
@JsonClass(generateAdapter = true)
data class UserMedicalProfileDto(
    @Json(name = "name")                    val name: String? = null,
    @Json(name = "age")                     val age: Int? = null,
    @Json(name = "blood_type")              val bloodType: String? = null,
    @Json(name = "medical_conditions")      val medicalConditions: List<String> = emptyList(),
    @Json(name = "medications")             val medications: List<String> = emptyList(),
    @Json(name = "allergies")              val allergies: List<String> = emptyList(),
    @Json(name = "emergency_contact_name")  val emergencyContactName: String? = null,
    @Json(name = "emergency_contact_number")val emergencyContactNumber: String? = null
)
