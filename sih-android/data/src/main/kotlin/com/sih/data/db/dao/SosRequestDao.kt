package com.sih.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sih.data.db.entity.SosRequestEntity
import com.sih.data.model.SosStatus
import kotlinx.coroutines.flow.Flow

/**
 * DAO for SOS requests stored on device.
 *
 * This device stores its own SOS records AND records received via the relay
 * mesh from other phones. The gateway sync worker reads ALL records
 * (not just own) and uploads them in a single batch.
 */
@Dao
interface SosRequestDao {

    // ── Writes ────────────────────────────────────────────────────────────

    /**
     * Insert a new SOS. IGNORE if the UUID already exists (idempotent relay merges).
     * The relay module calls this when it receives a new record from a peer.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSos(sos: SosRequestEntity): Long

    /** Upsert — used when the relay module updates hop metadata. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSos(sos: SosRequestEntity)

    // ── Status updates ─────────────────────────────────────────────────────

    /** Mark a set of UUIDs as uploaded (backend has acknowledged them). */
    @Query(
        "UPDATE sos_request SET status = :uploadedStatus WHERE uuid IN (:uuids)"
    )
    suspend fun markUploaded(
        uuids: List<String>,
        uploadedStatus: String = SosStatus.UPLOADED.apiValue
    )

    /** Update a single SOS's status (e.g. pending_local → in_relay). */
    @Query("UPDATE sos_request SET status = :status WHERE uuid = :uuid")
    suspend fun updateStatus(uuid: String, status: String)

    // ── Reads ─────────────────────────────────────────────────────────────

    /** All SOS records — used by the gateway sync worker for batch upload. */
    @Query("SELECT * FROM sos_request ORDER BY created_at ASC")
    suspend fun getAllSos(): List<SosRequestEntity>

    /** Observe a citizen's own SOS by UUID (for the Status screen). */
    @Query("SELECT * FROM sos_request WHERE uuid = :uuid LIMIT 1")
    fun observeSosByUuid(uuid: String): Flow<SosRequestEntity?>

    /** Get by UUID (suspend, for one-shot reads). */
    @Query("SELECT * FROM sos_request WHERE uuid = :uuid LIMIT 1")
    suspend fun getSosByUuid(uuid: String): SosRequestEntity?

    /** Records not yet uploaded — useful for metrics / debugging. */
    @Query("SELECT * FROM sos_request WHERE status != :uploadedStatus ORDER BY created_at ASC")
    suspend fun getPendingSos(uploadedStatus: String = SosStatus.UPLOADED.apiValue): List<SosRequestEntity>

    /** All SOS records belonging to this device (for the citizen's own status view). */
    @Query("SELECT * FROM sos_request WHERE device_id = :deviceId ORDER BY created_at DESC")
    fun observeOwnSos(deviceId: String): Flow<List<SosRequestEntity>>

    // ── TTL cleanup ────────────────────────────────────────────────────────

    /**
     * Delete uploaded records whose last_relayed_at is older than [cutoffIso].
     * Call this periodically — do NOT delete records immediately after upload
     * since other phones may still need them from the relay mesh.
     * 
     * TODO(Integration with A+B): A+B must provide the routing policy to determine 
     * when it is safe to delete pending_local or in_relay records without disrupting 
     * the epidemic mesh. Currently we only safely delete UPLOADED records.
     */
    @Query(
        "DELETE FROM sos_request WHERE status = :uploadedStatus AND last_relayed_at < :cutoffIso"
    )
    suspend fun deleteExpiredUploaded(
        cutoffIso: String,
        uploadedStatus: String = SosStatus.UPLOADED.apiValue
    )

    /** Count of total stored records — for debugging / battery-mode display. */
    @Query("SELECT COUNT(*) FROM sos_request")
    fun observeCount(): Flow<Int>
}
