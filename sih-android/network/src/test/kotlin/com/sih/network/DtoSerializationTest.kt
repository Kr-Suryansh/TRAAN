package com.sih.network

import com.sih.network.model.dto.LocationDto
import com.sih.network.model.dto.SosRequestDto
import com.sih.network.model.dto.UserMedicalProfileDto
import com.sih.network.model.request.GatewayUploadBatch
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.*
import org.junit.Test

/**
 * Serialisation tests for network DTOs.
 *
 * Verifies that JSON keys exactly match the field names defined in
 * day1-contracts-and-repo-setup.md §1.2 and §1.4.
 */
class DtoSerializationTest {

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    @Test
    fun `LocationDto serialises with correct JSON keys`() {
        val adapter = moshi.adapter(LocationDto::class.java)
        val dto = LocationDto(lat = 28.6139, lng = 77.2090, accuracyM = 10.0)
        val json = adapter.toJson(dto)

        assertTrue("lat key missing",        json.contains("\"lat\""))
        assertTrue("lng key missing",        json.contains("\"lng\""))
        assertTrue("accuracy_m key missing", json.contains("\"accuracy_m\""))
    }

    @Test
    fun `SosRequestDto excludes status field`() {
        val adapter = moshi.adapter(SosRequestDto::class.java)
        val dto = SosRequestDto(
            uuid          = "test-uuid",
            deviceId      = "device-1",
            createdAt     = "2026-08-13T10:00:00Z",
            location      = LocationDto(0.0, 0.0),
            isQuickSos    = true,
            emergencyType = "unspecified",
            lastRelayedAt = "2026-08-13T10:00:00Z"
        )
        val json = adapter.toJson(dto)
        assertFalse("status must NOT be in network payload", json.contains("\"status\""))
    }

    @Test
    fun `GatewayUploadBatch serialises with correct JSON keys`() {
        val adapter = moshi.adapter(GatewayUploadBatch::class.java)
        val batch = GatewayUploadBatch(
            gatewayDeviceId = "gw-device-1",
            gatewayLocation = LocationDto(lat = 0.0, lng = 0.0),
            uploadedAt      = "2026-08-13T10:00:00Z",
            sosBatch        = emptyList()
        )
        val json = adapter.toJson(batch)

        assertTrue("gateway_device_id key missing", json.contains("\"gateway_device_id\""))
        assertTrue("gateway_location key missing",  json.contains("\"gateway_location\""))
        assertTrue("uploaded_at key missing",       json.contains("\"uploaded_at\""))
        assertTrue("sos_batch key missing",         json.contains("\"sos_batch\""))
    }

    @Test
    fun `UserMedicalProfileDto serialises all §1-1 fields`() {
        val adapter = moshi.adapter(UserMedicalProfileDto::class.java)
        val dto = UserMedicalProfileDto(
            name                   = "Test User",
            bloodType              = "O+",
            medicalConditions      = listOf("diabetic"),
            medications            = listOf("metformin"),
            allergies              = listOf("penicillin"),
            emergencyContactName   = "Parent",
            emergencyContactNumber = "+919999999999"
        )
        val json = adapter.toJson(dto)

        assertTrue(json.contains("\"blood_type\""))
        assertTrue(json.contains("\"medical_conditions\""))
        assertTrue(json.contains("\"emergency_contact_name\""))
        assertTrue(json.contains("\"emergency_contact_number\""))
    }
}
