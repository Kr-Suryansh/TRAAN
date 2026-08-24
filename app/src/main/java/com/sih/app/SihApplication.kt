package com.sih.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.sih.data.di.schedulePeriodicGatewaySync
import com.sih.relay.api.RelayDataSource
import com.sih.relay.service.RelayDataSourceProvider
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SihApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var workManager: androidx.work.WorkManager

    @Inject
    lateinit var relayDataSource: RelayDataSource

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        schedulePeriodicGatewaySync(workManager)
        RelayDataSourceProvider.dataSource = relayDataSource
    }
}
