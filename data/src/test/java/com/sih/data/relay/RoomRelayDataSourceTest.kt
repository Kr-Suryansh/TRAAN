package com.sih.data.relay

import com.sih.data.db.dao.SosRequestDao
import com.sih.relay.model.EmergencyType
import com.sih.relay.model.SOSRequest
import com.sih.relay.model.SOSStatus
import com.sih.relay.model.SosLocation
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoomRelayDataSourceTest {

    private val dao = mockk<SosRequestDao>(relaxed = true)

    private fun sampleSos(uuid: String): SOSRequest = SOSRequest(
        uuid = uuid,
        deviceId = "installation-id-1",
        createdAt = "2026-08-15T10:00:00Z",
        location = SosLocation(lat = 28.6139f, lng = 77.2090f, accuracyMeters = 12.5f),
        isQuickSos = false,
        emergencyType = EmergencyType.MEDICAL,
        relayHopCount = 2,
        lastRelayedAt = "2026-08-15T10:05:00Z",
        status = SOSStatus.IN_RELAY
    )

    @Test
    fun `saveSosMessages inserts one entity per message via the DAO`() = runTest {
        val dataSource = RoomRelayDataSource(dao)
        val sos = sampleSos("uuid-a")
        coEvery { dao.insertSos(any()) } returns 1L

        dataSource.saveSosMessages(listOf(sos))

        coVerify(exactly = 1) { dao.insertSos(SosRequestMapper.toEntity(sos)) }
    }

    @Test
    fun `saveSosMessages passes each message through the mapper preserving its uuid`() = runTest {
        val dataSource = RoomRelayDataSource(dao)
        val first = sampleSos("uuid-a")
        val second = sampleSos("uuid-b")
        coEvery { dao.insertSos(any()) } returns 1L

        dataSource.saveSosMessages(listOf(first, second))

        coVerify(exactly = 1) { dao.insertSos(SosRequestMapper.toEntity(first)) }
        coVerify(exactly = 1) { dao.insertSos(SosRequestMapper.toEntity(second)) }
        // UUID identity is preserved through the mapping — the DAO's IGNORE
        // conflict strategy deduplicates at the database, not in this class.
        coVerify { dao.insertSos(match { it.uuid == "uuid-a" }) }
        coVerify { dao.insertSos(match { it.uuid == "uuid-b" }) }
    }

    @Test
    fun `getAllSosUuids returns every stored uuid`() = runTest {
        val dataSource = RoomRelayDataSource(dao)
        coEvery { dao.getAllSos() } returns listOf(
            SosRequestMapper.toEntity(sampleSos("uuid-a")),
            SosRequestMapper.toEntity(sampleSos("uuid-b"))
        )

        assertEquals(listOf("uuid-a", "uuid-b"), dataSource.getAllSosUuids())
    }

    @Test
    fun `getMissingSos returns only the records the peer does not hold`() = runTest {
        val dataSource = RoomRelayDataSource(dao)
        coEvery { dao.getAllSos() } returns listOf(
            SosRequestMapper.toEntity(sampleSos("uuid-a")),
            SosRequestMapper.toEntity(sampleSos("uuid-b"))
        )

        val missing = dataSource.getMissingSos(listOf("uuid-a"))

        assertEquals(listOf("uuid-b"), missing.map { it.uuid })
    }

    @Test
    fun `getMissingSos preserves hop metadata and status on mapped records`() = runTest {
        val dataSource = RoomRelayDataSource(dao)
        val entity = SosRequestMapper.toEntity(sampleSos("uuid-a"))
        coEvery { dao.getAllSos() } returns listOf(entity)

        val missing = dataSource.getMissingSos(emptyList())

        assertEquals(1, missing.size)
        assertEquals(2, missing.first().relayHopCount)
        assertEquals(SOSStatus.IN_RELAY, missing.first().status)
        assertEquals(28.6139f, missing.first().location.lat, 0.0f)
    }

    @Test
    fun `observeAllSos emits the full Room contents`() = runTest {
        val dataSource = RoomRelayDataSource(dao)
        val entity = SosRequestMapper.toEntity(sampleSos("uuid-a"))
        coEvery { dao.observeCount() } returns flowOf(1)
        coEvery { dao.getAllSos() } returns listOf(entity)

        val emitted = dataSource.observeAllSos().first()

        assertEquals(listOf("uuid-a"), emitted.map { it.uuid })
        assertEquals(SOSStatus.IN_RELAY, emitted.first().status)
    }
}