package com.sih.app.di

import com.sih.network.api.DisasterApi
import com.sih.network.client.RetrofitClientFactory
import com.sih.network.interceptor.AuthInterceptor
import com.sih.data.db.dao.SosRequestDao
import com.sih.data.prefs.DevicePreferences
import com.sih.data.relay.RoomRelayDataSource
import com.sih.data.relay.RoomRelayRepository
import com.sih.relay.RelayRepository
import com.sih.relay.api.RelayDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

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
        baseUrl         = com.sih.app.BuildConfig.BASE_URL,
        authInterceptor = authInterceptor,
        isDebug         = com.sih.app.BuildConfig.DEBUG
    )

    @Provides
    @Singleton
    fun provideRelayDataSource(
        sosRequestDao: SosRequestDao
    ): RelayDataSource = RoomRelayDataSource(sosRequestDao)

    @Provides
    @Singleton
    fun provideRelayRepository(
        sosRequestDao: SosRequestDao
    ): RelayRepository = RoomRelayRepository(sosRequestDao)
}
