package com.sih.app.ui.status

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
    val formattedCreatedAt: String    = "",
    val emergencyLabel: String        = ""
)

@HiltViewModel
class StatusViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sosRepository: SosRepository,
    private val disasterApi: DisasterApi,
    private val devicePreferences: com.sih.data.prefs.DevicePreferences,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val uuid: String = checkNotNull(savedStateHandle["uuid"])

    private val _state = MutableStateFlow(StatusUiState())
    val state: StateFlow<StatusUiState> = _state.asStateFlow()

    private val timeFormatter = DateTimeFormatter
        .ofPattern("h:mm a", Locale.getDefault())
        .withZone(ZoneId.systemDefault())

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
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

    private fun registerConnectivityCallback() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()
        runCatching {
            connectivityManager.registerNetworkCallback(request, networkCallback)
        }
    }

    fun checkBackendStatus() {
        if (_state.value.isCheckingBackend) return

        if (!devicePreferences.isRegistered()) return

        val activeNetwork = connectivityManager.activeNetwork
        if (activeNetwork == null) return

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
                                backendError = "Not yet received by server \u2014 still in relay"
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
                            null
                        } else {
                            e.message
                        }
                    )
                }
            }
        }
    }

    private fun formatTime(iso8601: String): String = runCatching {
        timeFormatter.format(Instant.parse(iso8601))
    }.getOrDefault(iso8601)

    private fun formatEmergency(apiValue: String): String {
        if (apiValue == "unspecified") return "Emergency"
        return apiValue
            .split("_")
            .joinToString(" ") { word ->
                word.replaceFirstChar { it.uppercase() }
            }
    }
}
