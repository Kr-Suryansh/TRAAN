package com.sih.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.sih.data.db.converter.RoomConverters
import com.sih.data.db.dao.SosRequestDao
import com.sih.data.db.dao.UserMedicalProfileDao
import com.sih.data.db.entity.SosRequestEntity
import com.sih.data.db.entity.UserMedicalProfileEntity

/**
 * Root Room database for the sih-android app.
 *
 * Version history:
 *   1 — initial schema (SosRequestEntity + UserMedicalProfileEntity)
 *
 * Migrations:
 *   Add a Migration(from, to) in the DI module when
 *   bumping [version]. Do NOT use fallbackToDestructiveMigration in
 *   production — it would delete locally stored SOS records.
 */
@Database(
    entities = [
        SosRequestEntity::class,
        UserMedicalProfileEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun sosRequestDao(): SosRequestDao
    abstract fun userMedicalProfileDao(): UserMedicalProfileDao

    companion object {
        const val DATABASE_NAME = "sih_local.db"
    }
}