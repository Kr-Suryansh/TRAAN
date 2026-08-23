package com.sih.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp interceptor that injects the device JWT as a Bearer token on every
 * request that requires device authentication.
 *
 * Registration endpoint (/auth/device/register) is explicitly excluded —
 * it has no auth requirement and calling it with an invalid token would fail.
 *
 * [getDeviceJwt] is a lambda so the interceptor always reads the latest stored
 * token (handles the case where registration happens after construction).
 */
class AuthInterceptor(
    private val getDeviceJwt: () -> String?
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()

        // Skip auth for the registration endpoint
        if (original.url.encodedPath.endsWith("/auth/device/register")) {
            return chain.proceed(original)
        }

        val jwt = getDeviceJwt()
        val request = if (!jwt.isNullOrBlank()) {
            original.newBuilder()
                .header("Authorization", "Bearer $jwt")
                .build()
        } else {
            original // proceed without auth; caller handles 401
        }

        return chain.proceed(request)
    }
}
