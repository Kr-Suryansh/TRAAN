package com.sih.android.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sih.data.db.entity.UserMedicalProfileEntity
import com.sih.data.db.converter.RoomConverters
import com.sih.data.repository.UserMedicalProfileRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val name: String                  = "",
    val age: String                   = "",
    val bloodType: String             = "",
    val medicalConditions: String     = "", // comma-separated in UI
    val medications: String           = "", // comma-separated in UI
    val allergies: String             = "", // comma-separated in UI
    val emergencyContactName: String  = "",
    val emergencyContactNumber: String= "",
    val isSaving: Boolean             = false,
    val savedSuccessfully: Boolean    = false,
    val errorMessage: String?         = null
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val profileRepository: UserMedicalProfileRepository
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val listAdapter = moshi.adapter<List<String>>(
        Types.newParameterizedType(List::class.java, String::class.java)
    )

    init { loadExisting() }

    private fun loadExisting() {
        viewModelScope.launch {
            val profile = profileRepository.getSnapshot() ?: return@launch
            _state.update {
                it.copy(
                    name                   = profile.name ?: "",
                    age                    = profile.age?.toString() ?: "",
                    bloodType              = profile.bloodType ?: "",
                    medicalConditions      = parseToUiString(profile.medicalConditions),
                    medications            = parseToUiString(profile.medications),
                    allergies              = parseToUiString(profile.allergies),
                    emergencyContactName   = profile.emergencyContactName ?: "",
                    emergencyContactNumber = profile.emergencyContactNumber ?: ""
                )
            }
        }
    }

    fun onNameChange(v: String)                   = _state.update { it.copy(name = v) }
    fun onAgeChange(v: String)                    = _state.update { it.copy(age = v) }
    fun onBloodTypeChange(v: String)              = _state.update { it.copy(bloodType = v) }
    fun onMedicalConditionsChange(v: String)      = _state.update { it.copy(medicalConditions = v) }
    fun onMedicationsChange(v: String)            = _state.update { it.copy(medications = v) }
    fun onAllergiesChange(v: String)              = _state.update { it.copy(allergies = v) }
    fun onEmergencyContactNameChange(v: String)   = _state.update { it.copy(emergencyContactName = v) }
    fun onEmergencyContactNumberChange(v: String) = _state.update { it.copy(emergencyContactNumber = v) }

    fun saveProfile() {
        _state.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching {
                val s = _state.value
                profileRepository.saveProfile(
                    UserMedicalProfileEntity(
                        id                     = 1,
                        name                   = s.name.trim().ifBlank { null },
                        age                    = s.age.trim().toIntOrNull(),
                        bloodType              = s.bloodType.trim().ifBlank { null },
                        medicalConditions      = toJsonArray(s.medicalConditions),
                        medications            = toJsonArray(s.medications),
                        allergies              = toJsonArray(s.allergies),
                        emergencyContactName   = s.emergencyContactName.trim().ifBlank { null },
                        emergencyContactNumber = s.emergencyContactNumber.trim().ifBlank { null }
                    )
                )
            }.onSuccess {
                _state.update { it.copy(isSaving = false, savedSuccessfully = true) }
            }.onFailure { e ->
                _state.update { it.copy(isSaving = false, errorMessage = e.message) }
            }
        }
    }

    /** Convert "diabetic, cardiac" → JSON ["diabetic","cardiac"] for Room storage. */
    private fun toJsonArray(csv: String): String =
        listAdapter.toJson(csv.split(",").map { it.trim() }.filter { it.isNotEmpty() })

    /** Convert JSON ["diabetic","cardiac"] → "diabetic, cardiac" for UI display. */
    private fun parseToUiString(json: String): String =
        runCatching {
            listAdapter.fromJson(json)?.joinToString(", ") ?: ""
        }.getOrDefault("")
}
