package com.sih.network.api

import com.sih.network.model.request.DeviceRegistrationRequest
import com.sih.network.model.request.GatewayUploadBatch
import com.sih.network.model.response.BatchUploadResponse
import com.sih.network.model.response.DeviceRegistrationResponse
import com.sih.network.model.response.SosStatusResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Retrofit interface for all endpoints consumed by the Android app.
 *
 * Endpoint paths are exactly as defined in day1-contracts-and-repo-setup.md §2.
 * DO NOT rename, add, or remove endpoints without updating ApiEndpoints.md
 * and flagging the change to all affected agents.
 *
 * Auth: injected automatically by [com.sih.network.interceptor.AuthInterceptor]
 * (skipped for device registration which requires no auth).
 */
interface DisasterApi {

    /**
     * Register this device on first launch.
     * POST /api/v1/auth/device/register
     *
     * No auth header required.
     * Returns device_id + device_jwt — store both securely.
     */
    @POST("auth/device/register")
    suspend fun registerDevice(
        @Body request: DeviceRegistrationRequest
    ): Response<DeviceRegistrationResponse>

    /**
     * Upload the full local relay store as a batch.
     * POST /api/v1/sos/batch
     *
     * Bearer device_jwt injected by AuthInterceptor.
     * Returns 202 + {accepted_uuids, duplicate_uuids}.
     *
     * Idempotent: safe to re-submit UUIDs the backend already has.
     */
    @POST("sos/batch")
    suspend fun uploadBatch(
        @Body batch: GatewayUploadBatch
    ): Response<BatchUploadResponse>

    /**
     * Check delivery status of a specific SOS.
     * GET /api/v1/sos/{uuid}/status
     *
     * Bearer device_jwt injected by AuthInterceptor.
     * Returns {status} — backend-defined value, displayed verbatim.
     * Returns 404 if not yet received.
     */
    @GET("sos/{uuid}/status")
    suspend fun getSosStatus(
        @Path("uuid") uuid: String
    ): Response<SosStatusResponse>
}
