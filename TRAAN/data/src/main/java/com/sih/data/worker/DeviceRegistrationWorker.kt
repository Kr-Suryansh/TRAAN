package com.sih.data.worker

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sih.data.prefs.DevicePreferences
import com.sih.network.api.DisasterApi
import com.sih.network.model.request.DeviceRegistrationRequest
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class DeviceRegistrationWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val disasterApi: DisasterApi,
    private val devicePreferences: DevicePreferences
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val TAG = "DeviceRegistrationWorker"
        const val WORK_NAME = "device_registration_worker"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting device registration")

        if (devicePreferences.isRegistered()) {
            Log.d(TAG, "Device is already registered, skipping")
            return Result.success()
        }

        return try {
            // Get app version
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val appVersion = pInfo.versionName ?: "1.0.0"

            // Model A: send the stable installation UUID so the backend uses it
            // as the canonical device_id (primary key). After this fix,
            // pref_device_id == pref_installation_id always.
            val installationId = devicePreferences.getOrCreateInstallationId()

            val request = DeviceRegistrationRequest(
                deviceId    = installationId,
                deviceModel = Build.MODEL,
                appVersion  = appVersion
            )

            val response = disasterApi.registerDevice(request)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    // Model A invariant: the backend must echo the same UUID we sent.
                    // A mismatch would indicate a backend regression — log it clearly.
                    if (body.deviceId != installationId) {
                        Log.w(
                            TAG,
                            "Model A violated: sent device_id=$installationId " +
                            "but backend returned device_id=${body.deviceId}. " +
                            "Storing backend value — investigate immediately."
                        )
                    }
                    devicePreferences.saveCredentials(
                        deviceId = body.deviceId,
                        deviceJwt = body.deviceJwt
                    )
                    Log.i(TAG, "Device registered successfully: ${body.deviceId}")
                    Result.success()
                } else {
                    Log.e(TAG, "Successful response but body is null")
                    Result.retry()
                }
            } else {
                Log.w(TAG, "Server error during registration: ${response.code()}")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network exception during registration", e)
            Result.retry() // Applies exponential backoff
        }
    }
}
