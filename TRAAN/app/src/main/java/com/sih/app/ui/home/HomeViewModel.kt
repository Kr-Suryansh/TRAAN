package com.sih.app.ui.home

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.sih.data.model.EmergencyType
import com.sih.data.relay.SosRequestMapper
import com.sih.data.repository.SosRepository
import com.sih.data.repository.UserMedicalProfileRepository
import com.sih.relay.service.RelayDataSourceProvider
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

enum class LocationStatus { IDLE, FETCHING, ACQUIRED, UNAVAILABLE }

data class HomeUiState(
    val isCreatingSos: Boolean               = false,
    val createdSosUuid: String?              = null,
    val errorMessage: String?               = null,
    val locationStatus: LocationStatus      = LocationStatus.IDLE,
    val selectedEmergencyType: EmergencyType = EmergencyType.UNSPECIFIED,
    val customMessage: String               = "",
    val peopleCount: String                = "",
    val contactNumber: String              = "",
    val peopleCountError: String?          = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sosRepository: SosRepository,
    private val profileRepository: UserMedicalProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun triggerSos(isQuickSos: Boolean = true) {
        if (_uiState.value.isCreatingSos) return

        _uiState.update { it.copy(isCreatingSos = true, errorMessage = null, locationStatus = LocationStatus.FETCHING) }

        viewModelScope.launch {
            val (lat, lng, accuracy) = resolveLocation()
            createSosInternal(lat, lng, accuracy, isQuickSos)
        }
    }

    fun triggerSosWithPermissionResult(isQuickSos: Boolean, isGranted: Boolean) {
        if (_uiState.value.isCreatingSos) return

        _uiState.update { it.copy(isCreatingSos = true, errorMessage = null, locationStatus = LocationStatus.FETCHING) }

        viewModelScope.launch {
            val (lat, lng, accuracy) = if (isGranted) resolveLocation() else Triple(0.0, 0.0, null)
            createSosInternal(lat, lng, accuracy, isQuickSos)
        }
    }

    @SuppressLint("MissingPermission")
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

            val countError = if (_uiState.value.peopleCount.isNotBlank()) {
                val n = _uiState.value.peopleCount.toIntOrNull()
                when {
                    n == null       -> "Must be a number"
                    n < 1           -> "Must be at least 1"
                    n > 9999        -> "Value too large"
                    else            -> null
                }
            } else null

            if (countError != null) {
                _uiState.update { it.copy(isCreatingSos = false, peopleCountError = countError) }
                return@runCatching ""
            }

            sosRepository.createSos(
                lat           = lat,
                lng           = lng,
                accuracyM     = accuracyM,
                isQuickSos    = isQuickSos,
                emergencyType = _uiState.value.selectedEmergencyType,
                peopleCount   = count,
                customMessage = msg,
                contactNumber = contact,
                profile       = profile
            )
        }.onSuccess { uuid ->
            _uiState.update {
                it.copy(isCreatingSos = false, createdSosUuid = uuid)
            }
            // Stage 6B-2: push locally created SOS into the relay mesh immediately
            val entity = sosRepository.getSos(uuid)
            if (entity != null) {
                val sos = SosRequestMapper.toSosRequest(entity)
                RelayDataSourceProvider.relayManager?.propagateLocalSos(sos)
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

    fun selectEmergencyType(type: EmergencyType) =
        _uiState.update { it.copy(selectedEmergencyType = type) }

    fun updateCustomMessage(msg: String) =
        _uiState.update { it.copy(customMessage = msg) }

    fun updatePeopleCount(count: String) =
        _uiState.update { it.copy(peopleCount = count, peopleCountError = null) }

    fun updateContactNumber(number: String) =
        _uiState.update { it.copy(contactNumber = number) }

    fun onSosNavigated() =
        _uiState.update { it.copy(createdSosUuid = null) }

    fun clearError() =
        _uiState.update { it.copy(errorMessage = null) }
}
