package com.sih.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted user medical profile — §1.1 UserMedicalProfile.
 *
 * SINGLETON: always id = 1. Use upsert to update.
 * Filled once during onboarding, never during an emergency.
 * Accessible from Settings to edit later.
 *
 * List<String> fields (medical_conditions, medications, allergies) are
 * stored as JSON strings via [com.sih.data.db.converter.RoomConverters].
 */
@Entity(tableName = "user_medical_profile")
data class UserMedicalProfileEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int = 1, // Singleton row

    @ColumnInfo(name = "name")
    val name: String? = null,

    @ColumnInfo(name = "age")
    val age: Int? = null,

    @ColumnInfo(name = "blood_type")
    val bloodType: String? = null,

    // Stored as JSON array string: e.g. ["diabetic","cardiac"]
    @ColumnInfo(name = "medical_conditions")
    val medicalConditions: String = "[]",

    // Stored as JSON array string: e.g. ["metformin"]
    @ColumnInfo(name = "medications")
    val medications: String = "[]",

    // Stored as JSON array string: e.g. ["penicillin"]
    @ColumnInfo(name = "allergies")
    val allergies: String = "[]",

    @ColumnInfo(name = "emergency_contact_name")
    val emergencyContactName: String? = null,

    @ColumnInfo(name = "emergency_contact_number")
    val emergencyContactNumber: String? = null
)
