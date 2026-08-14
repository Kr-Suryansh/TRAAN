package com.sih.android.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Onboarding — Medical Profile setup.
 *
 * One-time screen, skippable, accessible from the settings icon on HomeScreen.
 * ALL fields are optional — the screen must never feel mandatory or blocking.
 *
 * Contract: filled BEFORE any emergency, never during one.
 * The profile is auto-attached to every SOS without re-entering anything.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Navigate away once saved
    LaunchedEffect(state.savedSuccessfully) {
        if (state.savedSuccessfully) onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Medical Profile") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            Text(
                "This information is stored only on your device and attached automatically when you send an SOS. All fields are optional.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            Spacer(Modifier.height(8.dp))

            // ── Personal ────────────────────────────────────────────────────
            SectionLabel("Personal")

            ProfileTextField(
                value    = state.name,
                onChange = viewModel::onNameChange,
                label    = "Full name"
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ProfileTextField(
                    value    = state.age,
                    onChange = { newValue -> viewModel.onAgeChange(newValue.filter { it.isDigit() }) },
                    label    = "Age",
                    keyboard = KeyboardType.Number,
                    modifier = Modifier.weight(1f)
                )
                ProfileTextField(
                    value    = state.bloodType,
                    onChange = viewModel::onBloodTypeChange,
                    label    = "Blood type",
                    placeholder = "e.g. O+",
                    modifier = Modifier.weight(1f),
                    isError  = state.validationErrors.containsKey("bloodType"),
                    supportingText = state.validationErrors["bloodType"]
                )
            }

            // ── Medical ─────────────────────────────────────────────────────
            SectionLabel("Medical")

            ProfileTextField(
                value    = state.medicalConditions,
                onChange = viewModel::onMedicalConditionsChange,
                label    = "Conditions",
                placeholder = "e.g. diabetic, cardiac (comma-separated)"
            )
            ProfileTextField(
                value    = state.medications,
                onChange = viewModel::onMedicationsChange,
                label    = "Medications",
                placeholder = "e.g. metformin, aspirin"
            )
            ProfileTextField(
                value    = state.allergies,
                onChange = viewModel::onAllergiesChange,
                label    = "Allergies",
                placeholder = "e.g. penicillin, latex"
            )

            // ── Emergency Contact ────────────────────────────────────────────
            SectionLabel("Emergency Contact")

            ProfileTextField(
                value    = state.emergencyContactName,
                onChange = viewModel::onEmergencyContactNameChange,
                label    = "Contact name"
            )
            ProfileTextField(
                value    = state.emergencyContactNumber,
                onChange = { newValue -> viewModel.onEmergencyContactNumberChange(newValue.filter { it.isDigit() || it == '+' }) },
                label    = "Contact phone",
                keyboard = KeyboardType.Phone,
                placeholder = "+91...",
                isError  = state.validationErrors.containsKey("emergencyContactNumber"),
                supportingText = state.validationErrors["emergencyContactNumber"]
            )

            Spacer(Modifier.height(16.dp))

            // ── Actions ──────────────────────────────────────────────────────
            Button(
                onClick  = viewModel::saveProfile,
                enabled  = !state.isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier  = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Save profile")
                }
            }

            TextButton(
                onClick  = viewModel::skipOnboarding,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Skip for now")
            }

            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text  = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun ProfileTextField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    keyboard: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier.fillMaxWidth(),
    isError: Boolean = false,
    supportingText: String? = null
) {
    OutlinedTextField(
        value         = value,
        onValueChange = onChange,
        label         = { Text(label) },
        placeholder   = { Text(placeholder) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        singleLine    = true,
        modifier      = modifier,
        isError       = isError,
        supportingText = supportingText?.let { { Text(it) } }
    )
}
