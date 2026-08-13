package com.sih.android

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.sih.data.di.schedulePeriodicGatewaySync
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application class.
 *
 * Implements [Configuration.Provider] to supply [HiltWorkerFactory] to WorkManager.
 * This is required because we disabled the default WorkManager auto-initializer
 * in AndroidManifest.xml (to allow Hilt to provide the factory).
 *
 * WorkManager's [HiltWorkerFactory] lets us use @HiltWorker + @AssistedInject
 * in [com.sih.data.worker.GatewaySyncWorker].
 */
@HiltAndroidApp
class SihApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var workManager: androidx.work.WorkManager

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // Schedule the periodic gateway sync once on startup.
        // ExistingPeriodicWorkPolicy.KEEP means this is a no-op if already scheduled.
        schedulePeriodicGatewaySync(workManager)
    }
}
