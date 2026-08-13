package com.sih.android.ui.status

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sih.data.db.entity.SosRequestEntity
import com.sih.data.model.SosStatus
import com.sih.data.repository.SosRepository
import com.sih.network.api.DisasterApi
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

data class StatusUiState(
    val localSos: SosRequestEntity?   = null,
    val backendStatus: String?        = null,
    val isCheckingBackend: Boolean    = false,
    val backendError: String?         = null,
    val isLoading: Boolean            = true,
    /** Human-readable time, e.g. "11:42 AM". Empty string until loaded. */
    val formattedCreatedAt: String    = "",
    /** Display label for emergency type, e.g. "Flood Rescue". Empty string until loaded. */
    val emergencyLabel: String        = ""
)

@HiltViewModel
class StatusViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sosRepository: SosRepository,
    private val disasterApi: DisasterApi,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val uuid: String = checkNotNull(savedStateHandle["uuid"])

    private val _state = MutableStateFlow(StatusUiState())
    val state: StateFlow<StatusUiState> = _state.asStateFlow()

    private val timeFormatter = DateTimeFormatter
        .ofPattern("h:mm a", Locale.getDefault())
        .withZone(ZoneId.systemDefault())

    /**
     * ConnectivityManager callback — fires checkBackendStatus() automatically
     * when the device transitions from offline → online.
     *
     * This means the status pill can transition 🟡 → 🟢 without the citizen
     * manually tapping the ↻ button.
     */
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            // Device just came online — check if the SOS reached the backend
            checkBackendStatus()
        }
    }

    init {
        observeLocalSos()
        checkBackendStatus()
        registerConnectivityCallback()
    }

    override fun onCleared() {
        super.onCleared()
        runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
    }

    /** Observe the local Room record for real-time status updates. */
    private fun observeLocalSos() {
        viewModelScope.launch {
            sosRepository.observeSos(uuid).collect { sos ->
                _state.update {
                    it.copy(
                        localSos           = sos,
                        isLoading          = false,
                        formattedCreatedAt = sos?.let { e -> formatTime(e.createdAt) } ?: "",
                        emergencyLabel     = sos?.let { e -> formatEmergency(e.emergencyType) } ?: ""
                    )
                }
            }
        }
    }

    /**
     * Register for connectivity events so we auto-check the backend the moment
     * the device comes back online.
     *
     * Uses [ConnectivityManager.registerNetworkCallback] with a request for
     * INTERNET capability — fires [networkCallback.onAvailable] only when a
     * network with real internet access becomes available.
     */
    private fun registerConnectivityCallback() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching {
            connectivityManager.registerNetworkCallback(request, networkCallback)
        }
    }

    /**
     * Check the backend for delivery confirmation.
     * Only makes a network call — local status is always shown regardless.
     *
     * GET /api/v1/sos/{uuid}/status — requires device_jwt via interceptor.
     */
    fun checkBackendStatus() {
        // Avoid overlapping checks
        if (_state.value.isCheckingBackend) return

        _state.update { it.copy(isCheckingBackend = true, backendError = null) }
        viewModelScope.launch {
            runCatching {
                disasterApi.getSosStatus(uuid)
            }.onSuccess { response ->
                when {
                    response.isSuccessful -> {
                        _state.update {
                            it.copy(
                                isCheckingBackend = false,
                                backendStatus = response.body()?.status
                            )
                        }
                    }
                    response.code() == 404 -> {
                        _state.update {
                            it.copy(
                                isCheckingBackend = false,
                                backendStatus = null,
                                backendError = "Not yet received by server — still in relay"
                            )
                        }
                    }
                    else -> {
                        _state.update {
                            it.copy(
                                isCheckingBackend = false,
                                backendError = "Server error: ${response.code()}"
                            )
                        }
                    }
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        isCheckingBackend = false,
                        backendError = if (e is java.net.UnknownHostException || e is java.net.ConnectException) {
                            null // offline — not an error, just no backend status yet
                        } else {
                            e.message
                        }
                    )
                }
            }
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    /**
     * Converts an ISO8601 string to a local time string like "11:42 AM".
     * Falls back to the raw string on parse failure so we never show nothing.
     */
    private fun formatTime(iso8601: String): String = runCatching {
        timeFormatter.format(Instant.parse(iso8601))
    }.getOrDefault(iso8601)

    /**
     * Converts an apiValue like "flood_rescue" to a display label like "Flood Rescue".
     * Shows "Emergency" as a generic fallback for "unspecified".
     */
    private fun formatEmergency(apiValue: String): String {
        if (apiValue == "unspecified") return "Emergency"
        return apiValue
            .split("_")
            .joinToString(" ") { word ->
                word.replaceFirstChar { it.uppercase() }
            }
    }
}
