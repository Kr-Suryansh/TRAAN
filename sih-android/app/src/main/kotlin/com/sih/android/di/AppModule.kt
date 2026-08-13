package com.sih.android.di

import com.sih.network.api.DisasterApi
import com.sih.network.client.RetrofitClientFactory
import com.sih.network.interceptor.AuthInterceptor
import com.sih.data.prefs.DevicePreferences
import com.sih.relay.RelayRepository
import com.sih.relay.StubRelayRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * App-level Hilt module.
 *
 * Wires :network and :relay dependencies.
 * The BASE_URL comes from BuildConfig so it can differ between debug and release.
 *
 * Note: DataModule (in :data) provides Room DB and WorkManager.
 * This module provides the network layer + relay stub.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAuthInterceptor(
        devicePreferences: DevicePreferences
    ): AuthInterceptor = AuthInterceptor { devicePreferences.getDeviceJwt() }

    @Provides
    @Singleton
    fun provideDisasterApi(
        authInterceptor: AuthInterceptor
    ): DisasterApi = RetrofitClientFactory.create(
        baseUrl         = com.sih.android.BuildConfig.BASE_URL,
        authInterceptor = authInterceptor,
        isDebug         = com.sih.android.BuildConfig.DEBUG
    )

    /**
     * Binds the stub relay implementation.
     * Agent A+B will replace StubRelayRepository with the real Nearby Connections impl.
     * Change ONLY this binding — the interface and all callers remain the same.
     */
    @Provides
    @Singleton
    fun provideRelayRepository(): RelayRepository = StubRelayRepository()
}
