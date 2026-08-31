package com.sih.relay.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A frozen, read-only snapshot of the victim's medical profile, embedded in [SOSRequest].
 *
 * Schema source: day1-contracts-and-repo-setup.md §1.1 (UserMedicalProfile)
 * All JSON field names match §1.1 exactly for wire-format compatibility.
 *
 * The profile is auto-attached when the SOS is created (auto-filled from the stored profile —
 * never re-typed by the user under duress). Null if the user hasn't set up a profile.
 */
@Serializable
data class UserMedicalProfile(
    val name: String? = null,
    val age: Int? = null,

    @SerialName("blood_type")
    val bloodType: String? = null,

    @SerialName("medical_conditions")
    val medicalConditions: List<String> = emptyList(),

    val medications: List<String> = emptyList(),

    val allergies: List<String> = emptyList(),

    @SerialName("emergency_contact_name")
    val emergencyContactName: String? = null,

    @SerialName("emergency_contact_number")
    val emergencyContactNumber: String? = null
)
