package com.sih.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sih.data.db.entity.UserMedicalProfileEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the medical profile singleton row (id = 1).
 *
 * Use [upsertProfile] to both create and update.
 * [getProfile] returns a Flow so the UI stays in sync with changes from Settings.
 */
@Dao
interface UserMedicalProfileDao {

    /** Observe the profile — emits null if onboarding has been skipped. */
    @Query("SELECT * FROM user_medical_profile WHERE id = 1 LIMIT 1")
    fun getProfile(): Flow<UserMedicalProfileEntity?>

    /** Read synchronously (for snapshot at SOS creation time). */
    @Query("SELECT * FROM user_medical_profile WHERE id = 1 LIMIT 1")
    suspend fun getProfileSnapshot(): UserMedicalProfileEntity?

    /** Insert if no row yet; replace on subsequent saves (singleton id = 1). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: UserMedicalProfileEntity)

    /** Clear the profile (user deletes all personal data). */
    @Query("DELETE FROM user_medical_profile WHERE id = 1")
    suspend fun clearProfile()
}