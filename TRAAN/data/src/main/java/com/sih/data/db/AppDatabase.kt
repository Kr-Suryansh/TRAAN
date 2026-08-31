package com.sih.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
 *   2 — removed severity_hint column from sos_request
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
    version = 2,
    exportSchema = true
)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun sosRequestDao(): SosRequestDao
    abstract fun userMedicalProfileDao(): UserMedicalProfileDao

    companion object {
        const val DATABASE_NAME = "sih_local.db"

        /**
         * Migration 1→2: Remove the severity_hint column from sos_request.
         *
         * SQLite (pre-3.35) does not support ALTER TABLE … DROP COLUMN,
         * so we recreate the table without the column and copy data over.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sos_request_new (
                        uuid TEXT NOT NULL PRIMARY KEY,
                        device_id TEXT NOT NULL,
                        created_at TEXT NOT NULL,
                        location_lat REAL NOT NULL,
                        location_lng REAL NOT NULL,
                        location_accuracy_m REAL,
                        is_quick_sos INTEGER NOT NULL,
                        emergency_type TEXT NOT NULL DEFAULT 'unspecified',
                        people_count INTEGER,
                        medical_snapshot TEXT,
                        custom_message TEXT,
                        contact_number TEXT,
                        relay_hop_count INTEGER NOT NULL DEFAULT 0,
                        last_relayed_at TEXT NOT NULL,
                        status TEXT NOT NULL DEFAULT 'pending_local'
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO sos_request_new (
                        uuid, device_id, created_at,
                        location_lat, location_lng, location_accuracy_m,
                        is_quick_sos, emergency_type,
                        people_count, medical_snapshot, custom_message, contact_number,
                        relay_hop_count, last_relayed_at, status
                    )
                    SELECT
                        uuid, device_id, created_at,
                        location_lat, location_lng, location_accuracy_m,
                        is_quick_sos, emergency_type,
                        people_count, medical_snapshot, custom_message, contact_number,
                        relay_hop_count, last_relayed_at, status
                    FROM sos_request
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE sos_request")
                db.execSQL("ALTER TABLE sos_request_new RENAME TO sos_request")
            }
        }
    }
}