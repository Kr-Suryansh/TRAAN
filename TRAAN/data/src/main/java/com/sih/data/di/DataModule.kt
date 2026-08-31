package com.sih.data.di

import android.content.Context
import androidx.room.Room
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.sih.data.db.AppDatabase
import com.sih.data.db.dao.SosRequestDao
import com.sih.data.db.dao.UserMedicalProfileDao
import com.sih.data.worker.GatewaySyncWorker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        AppDatabase.DATABASE_NAME
    )
        // DO NOT use fallbackToDestructiveMigration — it would wipe stored SOS records.
        // Add explicit migrations here as schema evolves.
        .addMigrations(AppDatabase.MIGRATION_1_2)
        .build()

    @Provides
    @Singleton
    fun provideSosRequestDao(db: AppDatabase): SosRequestDao = db.sosRequestDao()

    @Provides
    @Singleton
    fun provideUserMedicalProfileDao(db: AppDatabase): UserMedicalProfileDao =
        db.userMedicalProfileDao()

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)
}

/**
 * Call this from [com.sih.android.SihApplication] or the first Activity after
 * [WorkManager] is initialised to schedule the periodic gateway sync.
 *
 * Constraints: NetworkType.CONNECTED — only runs when internet is available.
 * Period: 15 minutes (minimum allowed by WorkManager).
 */
fun schedulePeriodicGatewaySync(workManager: WorkManager) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val syncRequest = PeriodicWorkRequestBuilder<GatewaySyncWorker>(
        repeatInterval = 15,
        repeatIntervalTimeUnit = TimeUnit.MINUTES
    )
        .setConstraints(constraints)
        .addTag(GatewaySyncWorker.TAG)
        .build()

    workManager.enqueueUniquePeriodicWork(
        GatewaySyncWorker.WORK_NAME_PERIODIC,
        ExistingPeriodicWorkPolicy.KEEP, // don't reset the timer if already scheduled
        syncRequest
    )
}

/**
 * Enqueues a one-time work request to register the device with the backend.
 * Only runs when network is connected.
 */
fun enqueueDeviceRegistration(workManager: WorkManager) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val request = androidx.work.OneTimeWorkRequestBuilder<com.sih.data.worker.DeviceRegistrationWorker>()
        .setConstraints(constraints)
        .addTag(com.sih.data.worker.DeviceRegistrationWorker.TAG)
        .build()

    workManager.enqueueUniqueWork(
        com.sih.data.worker.DeviceRegistrationWorker.WORK_NAME,
        androidx.work.ExistingWorkPolicy.KEEP,
        request
    )
}

/**
 * Enqueues an immediate one-shot [GatewaySyncWorker] run.
 *
 * Called right after an SOS is created so that if the device already has
 * connectivity, the upload happens in seconds rather than waiting for the
 * next 15-minute periodic window.
 *
 * ExistingWorkPolicy.KEEP: if a sync is already running/queued, don't
 * interrupt or duplicate it — let it finish naturally.
 */
fun enqueueImmediateGatewaySync(workManager: WorkManager) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val request = androidx.work.OneTimeWorkRequestBuilder<GatewaySyncWorker>()
        .setConstraints(constraints)
        .addTag(GatewaySyncWorker.TAG)
        .build()

    workManager.enqueueUniqueWork(
        GatewaySyncWorker.WORK_NAME_IMMEDIATE,
        androidx.work.ExistingWorkPolicy.KEEP,
        request
    )
}
