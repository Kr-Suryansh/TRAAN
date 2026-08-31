package com.sih.network.client

import com.sih.network.api.DisasterApi
import com.sih.network.interceptor.AuthInterceptor
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Factory for the Retrofit / OkHttp client.
 *
 * Not a singleton here — the Hilt [com.sih.network.di.NetworkModule] provides
 * the singleton instance. This keeps the client testable in isolation.
 *
 * Security (7.1): SOS payloads contain sensitive medical information (conditions,
 * medications, allergies, emergency contacts). HTTP logging is capped at HEADERS
 * even in debug mode to prevent medical data and device JWTs from appearing in
 * logcat. Never set to BODY in any build variant.
 */
object RetrofitClientFactory {

    fun create(
        baseUrl: String,
        authInterceptor: AuthInterceptor,
        isDebug: Boolean = false
    ): DisasterApi {

        val logging = HttpLoggingInterceptor().apply {
            // HEADERS only — BODY would expose medical conditions, medications,
            // allergies, emergency contacts, and device JWTs in logcat.
            level = if (isDebug) {
                HttpLoggingInterceptor.Level.HEADERS
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        val okHttp = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory()) // fallback reflection adapter
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttp)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(DisasterApi::class.java)
    }
}
