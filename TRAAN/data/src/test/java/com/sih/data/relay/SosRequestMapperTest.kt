package com.sih.data.relay

import com.sih.data.db.entity.SosRequestEntity
import com.sih.data.model.EmergencyType as DataEmergencyType
import com.sih.data.model.SosStatus as DataSosStatus
import com.sih.relay.model.EmergencyType
import com.sih.relay.model.SOSRequest
import com.sih.relay.model.SOSStatus
import com.sih.relay.model.SosLocation
import com.sih.relay.model.UserMedicalProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SosRequestMapperTest {

    private fun sampleSos(uuid: String = "37cb7ba8-580c-4bdf-81f3-37392d73e2df"): SOSRequest = SOSRequest(
        uuid = uuid,
        deviceId = "installation-id-1",
        createdAt = "2026-08-15T10:00:00Z",
        location = SosLocation(lat = 28.6139f, lng = 77.2090f, accuracyMeters = 12.5f),
        isQuickSos = false,
        emergencyType = EmergencyType.MEDICAL,
        peopleCount = 3,
        medicalSnapshot = UserMedicalProfile(
            name = "Aarti",
            age = 34,
            bloodType = "O+",
            medicalConditions = listOf("diabetic"),
            medications = listOf("metformin"),
            allergies = listOf("penicillin"),
            emergencyContactName = "Ravi",
            emergencyContactNumber = "+91-9000000001"
        ),
        customMessage = "We are on the third floor",
        contactNumber = "+91-9000000002",
        relayHopCount = 2,
        lastRelayedAt = "2026-08-15T10:05:00Z",
        status = SOSStatus.IN_RELAY
    )

    @Test
    fun `toEntity maps every SOSRequest field`() {
        val sos = sampleSos()
        val entity = SosRequestMapper.toEntity(sos)

        assertEquals(sos.uuid, entity.uuid)
        assertEquals(sos.deviceId, entity.deviceId)
        assertEquals(sos.createdAt, entity.createdAt)
        assertEquals(sos.location.lat.toDouble(), entity.locationLat, 0.0)
        assertEquals(sos.location.lng.toDouble(), entity.locationLng, 0.0)
        assertEquals(sos.location.accuracyMeters!!.toDouble(), entity.locationAccuracyM!!, 0.0)
        assertEquals(sos.isQuickSos, entity.isQuickSos)
        assertEquals(DataEmergencyType.MEDICAL.apiValue, entity.emergencyType)
        assertEquals(sos.peopleCount, entity.peopleCount)
        assertEquals(sos.customMessage, entity.customMessage)
        assertEquals(sos.contactNumber, entity.contactNumber)
        assertEquals(sos.relayHopCount, entity.relayHopCount)
        assertEquals(sos.lastRelayedAt, entity.lastRelayedAt)
        assertEquals(DataSosStatus.IN_RELAY.apiValue, entity.status)
        assertTrue("medical snapshot must be a non-null JSON string",
            entity.medicalSnapshot != null && entity.medicalSnapshot!!.contains("\"blood_type\":\"O+\""))
    }

    @Test
    fun `round trip preserves hop metadata, status, location, and snapshot`() {
        val original = sampleSos()
        val roundTripped = SosRequestMapper.toSosRequest(SosRequestMapper.toEntity(original))

        assertEquals(original, roundTripped)
    }

    @Test
    fun `uuid is the identity and never changes across mapping`() {
        val sos = sampleSos()
        val entity = SosRequestMapper.toEntity(sos)
        val roundTripped = SosRequestMapper.toSosRequest(entity)

        assertEquals(sos.uuid, entity.uuid)
        assertEquals(sos.uuid, roundTripped.uuid)
    }

    @Test
    fun `quick sos round trips with null severity and null snapshot`() {
        val quick = SOSRequest(
            uuid = "11111111-1111-4111-8111-111111111111",
            deviceId = "installation-id-2",
            createdAt = "2026-08-15T11:00:00Z",
            location = SosLocation(lat = 20.0f, lng = 77.0f),
            isQuickSos = true,
            emergencyType = EmergencyType.UNSPECIFIED,
            relayHopCount = 0,
            lastRelayedAt = "2026-08-15T11:00:00Z",
            status = SOSStatus.PENDING_LOCAL
        )

        val entity = SosRequestMapper.toEntity(quick)
        assertNull(entity.medicalSnapshot)
        assertEquals(DataEmergencyType.UNSPECIFIED.apiValue, entity.emergencyType)
        assertEquals(DataSosStatus.PENDING_LOCAL.apiValue, entity.status)

        assertEquals(quick, SosRequestMapper.toSosRequest(entity))
    }

    @Test
    fun `entity side degrades unknown api values to contract defaults`() {
        val entity = SosRequestEntity(
            uuid = "22222222-2222-4222-8222-222222222222",
            deviceId = "d",
            createdAt = "2026-08-15T12:00:00Z",
            locationLat = 1.0,
            locationLng = 2.0,
            isQuickSos = true,
            emergencyType = "not_a_real_type",
            relayHopCount = 0,
            lastRelayedAt = "2026-08-15T12:00:00Z",
            status = "not_a_real_status"
        )

        val sos = SosRequestMapper.toSosRequest(entity)
        assertEquals(SOSStatus.PENDING_LOCAL, sos.status)
    }
}