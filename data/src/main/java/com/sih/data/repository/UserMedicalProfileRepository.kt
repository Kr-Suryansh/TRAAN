package com.sih.data.repository

import com.sih.data.db.dao.UserMedicalProfileDao
import com.sih.data.db.entity.UserMedicalProfileEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for the user's medical profile.
 *
 * Singleton row in Room (id = 1). Filled during onboarding;
 * editable from Settings at any time.
 *
 * The profile is NEVER synced independently — it is only included as a
 * snapshot inside an SOSRequest at the moment of SOS creation.
 */
@Singleton
class UserMedicalProfileRepository @Inject constructor(
    private val profileDao: UserMedicalProfileDao
) {
    /** Observe the profile — null means the user has skipped onboarding. */
    fun observeProfile(): Flow<UserMedicalProfileEntity?> = profileDao.getProfile()

    /** Read the current profile synchronously (for SOS creation snapshot). */
    suspend fun getSnapshot(): UserMedicalProfileEntity? = profileDao.getProfileSnapshot()

    /**
     * Save or update the profile.
     * id is always 1 — [UserMedicalProfileEntity] enforces singleton semantics.
     */
    suspend fun saveProfile(profile: UserMedicalProfileEntity) =
        profileDao.upsertProfile(profile)

    /** Delete the profile (data erasure request). */
    suspend fun clearProfile() = profileDao.clearProfile()
}
