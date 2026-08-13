package com.sih.android.ui.home

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.sih.data.model.EmergencyType
import com.sih.data.repository.SosRepository
import com.sih.data.repository.UserMedicalProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume

/** Device-side location resolution state shown beneath the SOS button. */
enum class LocationStatus { IDLE, FETCHING, ACQUIRED, UNAVAILABLE }

data class HomeUiState(
    val isCreatingSos: Boolean              = false,
    val createdSosUuid: String?             = null,
    val errorMessage: String?              = null,
    val locationStatus: LocationStatus     = LocationStatus.IDLE,
    // Optional field selections
    val selectedEmergencyType: EmergencyType = EmergencyType.UNSPECIFIED,
    val customMessage: String              = "",
    val peopleCount: String               = "",
    val contactNumber: String             = "",
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sosRepository: SosRepository,
    private val profileRepository: UserMedicalProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // ── Public entry-point called from the SOS button ──────────────────────────

    /**
     * Entry point for an SOS tap.
     *
     * 1. Requests a one-shot GPS fix from FusedLocationProviderClient.
     * 2. Falls back to (0.0, 0.0) if location is unavailable or permission denied.
     * 3. NEVER blocks the SOS — no network call is made here.
     *
     * [isQuickSos] = true for the big red button; false for "Send with details".
     */
    fun triggerSos(isQuickSos: Boolean = true) {
        if (_uiState.value.isCreatingSos) return // guard against double-tap

        _uiState.update { it.copy(isCreatingSos = true, errorMessage = null, locationStatus = LocationStatus.FETCHING) }

        viewModelScope.launch {
            val (lat, lng, accuracy) = resolveLocation()
            createSosInternal(lat, lng, accuracy, isQuickSos)
        }
    }

    // ── Internal helpers ───────────────────────────────────────────────────────

    /**
     * Attempts a one-shot GPS fetch.
     * Returns (lat, lng, accuracy) or (0.0, 0.0, null) on any failure.
     *
     * FusedLocationProviderClient.getCurrentLocation is a one-shot call —
     * it does NOT register a continuous listener.
     */
    @SuppressLint("MissingPermission") // permission check is done before the call
    private suspend fun resolveLocation(): Triple<Double, Double, Double?> {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            _uiState.update { it.copy(locationStatus = LocationStatus.UNAVAILABLE) }
            return Triple(0.0, 0.0, null)
        }

        return suspendCancellableCoroutine { cont ->
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            val cts = com.google.android.gms.tasks.CancellationTokenSource()

            cont.invokeOnCancellation { cts.cancel() }

            fusedClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                cts.token
            ).addOnSuccessListener { location ->
                if (location != null) {
                    _uiState.update { it.copy(locationStatus = LocationStatus.ACQUIRED) }
                    cont.resume(Triple(location.latitude, location.longitude, location.accuracy.toDouble()))
                } else {
                    // No fix available (GPS off, no cached fix) — fall back, never block SOS
                    _uiState.update { it.copy(locationStatus = LocationStatus.UNAVAILABLE) }
                    cont.resume(Triple(0.0, 0.0, null))
                }
            }.addOnFailureListener {
                _uiState.update { it.copy(locationStatus = LocationStatus.UNAVAILABLE) }
                cont.resume(Triple(0.0, 0.0, null))
            }
        }
    }

    private suspend fun createSosInternal(
        lat: Double,
        lng: Double,
        accuracyM: Double?,
        isQuickSos: Boolean
    ) {
        runCatching {
            val profile = profileRepository.getSnapshot()
            val count   = _uiState.value.peopleCount.toIntOrNull()
            val msg     = _uiState.value.customMessage.trim().ifBlank { null }
            val contact = _uiState.value.contactNumber.trim().ifBlank { null }

            sosRepository.createSos(
                lat           = lat,
                lng           = lng,
                accuracyM     = accuracyM,
                isQuickSos    = isQuickSos,
                emergencyType = _uiState.value.selectedEmergencyType,
                severityHint  = null,
                peopleCount   = count,
                customMessage = msg,
                contactNumber = contact,
                profile       = profile
            )
        }.onSuccess { uuid ->
            _uiState.update {
                it.copy(isCreatingSos = false, createdSosUuid = uuid)
            }
        }.onFailure { e ->
            _uiState.update {
                it.copy(
                    isCreatingSos  = false,
                    locationStatus = LocationStatus.IDLE,
                    errorMessage   = e.message ?: "Failed to save SOS"
                )
            }
        }
    }

    // ── UI event handlers ──────────────────────────────────────────────────────

    fun selectEmergencyType(type: EmergencyType) =
        _uiState.update { it.copy(selectedEmergencyType = type) }

    fun updateCustomMessage(msg: String) =
        _uiState.update { it.copy(customMessage = msg) }

    fun updatePeopleCount(count: String) =
        _uiState.update { it.copy(peopleCount = count) }

    fun updateContactNumber(number: String) =
        _uiState.update { it.copy(contactNumber = number) }

    /** Called after navigation to Status screen so we don't re-navigate. */
    fun onSosNavigated() =
        _uiState.update { it.copy(createdSosUuid = null) }

    fun clearError() =
        _uiState.update { it.copy(errorMessage = null) }
}
