package com.sih.app.ui.home

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sih.app.ui.theme.SihTheme
import com.sih.data.model.EmergencyType

private val SosRed         = Color(0xFFD32F2F)
private val SosRedDeep     = Color(0xFF9A0007)
private val SosRedGlow     = Color(0xFFFF6659)
private val DimWhite       = Color(0xCCFFFFFF)
private val LocationGreen  = Color(0xFF4CAF50)
private val LocationAmber  = Color(0xFFFFC107)
private val LocationRed    = Color(0xFFEF5350)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToOnboarding: () -> Unit,
    onNavigateToStatus: (uuid: String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    val context = androidx.compose.ui.platform.LocalContext.current

    var pendingSosQuick by remember { mutableStateOf<Boolean?>(null) }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        pendingSosQuick?.let { isQuick ->
            viewModel.triggerSosWithPermissionResult(isQuick, isGranted)
            pendingSosQuick = null
        }
    }

    LaunchedEffect(state.createdSosUuid) {
        state.createdSosUuid?.let { uuid ->
            viewModel.onSosNavigated()
            onNavigateToStatus(uuid)
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(
                message  = msg,
                duration = SnackbarDuration.Short
            )
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    IconButton(onClick = onNavigateToOnboarding) {
                        Icon(Icons.Filled.Person, contentDescription = "Medical Profile")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f  to Color(0xFF1A0000),
                            0.45f to Color(0xFF2D0000),
                            1f  to Color(0xFF121212)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(24.dp))

                Text(
                    text       = "DISASTER SOS",
                    fontSize   = 28.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 4.sp,
                    color      = SosRedGlow,
                    textAlign  = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text      = "Emergency Alert System",
                    fontSize  = 13.sp,
                    color     = DimWhite,
                    letterSpacing = 1.sp
                )

                Spacer(Modifier.height(40.dp))

                PulsingSosButton(
                    isLoading = state.isCreatingSos,
                    onClick   = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                            context, Manifest.permission.ACCESS_FINE_LOCATION
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                        if (hasPermission) {
                            viewModel.triggerSos(isQuickSos = true)
                        } else {
                            pendingSosQuick = true
                            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        }
                    }
                )

                Spacer(Modifier.height(16.dp))

                LocationStatusBadge(status = state.locationStatus)

                Spacer(Modifier.height(40.dp))

                HorizontalDivider(
                    modifier  = Modifier.padding(horizontal = 32.dp),
                    color     = Color.White.copy(alpha = 0.08f)
                )

                Spacer(Modifier.height(24.dp))

                Text(
                    text       = "Add details (optional)",
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color      = DimWhite,
                    modifier   = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                )

                Spacer(Modifier.height(16.dp))

                EmergencyTypePicker(
                    selected   = state.selectedEmergencyType,
                    onSelected = viewModel::selectEmergencyType,
                    modifier   = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value         = state.customMessage,
                    onValueChange = viewModel::updateCustomMessage,
                    label         = { Text("Describe the situation") },
                    placeholder   = { Text("e.g. 3 people trapped on 2nd floor") },
                    maxLines      = 3,
                    colors        = sosTextFieldColors(),
                    shape         = RoundedCornerShape(12.dp),
                    modifier      = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                )

                Spacer(Modifier.height(12.dp))

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        OutlinedTextField(
                            value           = state.peopleCount,
                            onValueChange   = { newValue -> viewModel.updatePeopleCount(newValue.filter { it.isDigit() }) },
                            label           = { Text("People") },
                            placeholder     = { Text("Count") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError         = state.peopleCountError != null,
                            supportingText  = state.peopleCountError?.let { { Text(it, color = SosRed) } },
                            colors          = sosTextFieldColors(),
                            shape           = RoundedCornerShape(12.dp),
                            modifier        = Modifier.fillMaxWidth()
                        )
                    }
                    OutlinedTextField(
                        value           = state.contactNumber,
                        onValueChange   = { newValue -> viewModel.updateContactNumber(newValue.filter { it.isDigit() || it == '+' }) },
                        label           = { Text("Contact number") },
                        placeholder     = { Text("+91\u2026") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        colors          = sosTextFieldColors(),
                        shape           = RoundedCornerShape(12.dp),
                        modifier        = Modifier.weight(2f)
                    )
                }

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick  = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                            context, Manifest.permission.ACCESS_FINE_LOCATION
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                        if (hasPermission) {
                            viewModel.triggerSos(isQuickSos = false)
                        } else {
                            pendingSosQuick = false
                            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        }
                    },
                    enabled  = !state.isCreatingSos,
                    shape    = RoundedCornerShape(14.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = SosRed,
                        disabledContainerColor = SosRed.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(horizontal = 24.dp)
                ) {
                    Text(
                        text       = "Send with details",
                        fontWeight = FontWeight.Bold,
                        fontSize   = 16.sp
                    )
                }

                Spacer(Modifier.height(48.dp))
            }
        }
    }
}

@Composable
private fun PulsingSosButton(
    isLoading: Boolean,
    onClick: () -> Unit,
    buttonSize: Dp = 200.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sos_pulse")

    val ring1Scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue  = 1.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring1Scale"
    )
    val ring1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue  = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring1Alpha"
    )
    val ring2Scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue  = 1.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, delayMillis = 600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring2Scale"
    )
    val ring2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue  = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, delayMillis = 600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring2Alpha"
    )

    var pressed by remember { mutableStateOf(false) }
    val tapScale by animateFloatAsState(
        targetValue  = if (pressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "tapScale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(buttonSize * 1.7f)
    ) {
        if (!isLoading) {
            Box(
                modifier = Modifier
                    .size(buttonSize)
                    .scale(ring1Scale)
                    .background(SosRed.copy(alpha = ring1Alpha), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(buttonSize)
                    .scale(ring2Scale)
                    .background(SosRed.copy(alpha = ring2Alpha), CircleShape)
            )
        }

        Box(
            modifier = Modifier
                .size(buttonSize)
                .scale(tapScale)
                .shadow(elevation = 24.dp, shape = CircleShape, ambientColor = SosRed, spotColor = SosRed)
                .background(
                    Brush.radialGradient(listOf(SosRedGlow, SosRedDeep)),
                    CircleShape
                )
                .pointerInput(isLoading) {
                    if (!isLoading) {
                        detectTapGestures(
                            onPress = {
                                pressed = true
                                tryAwaitRelease()
                                pressed = false
                            },
                            onTap = { onClick() }
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color       = Color.White,
                    strokeWidth = 4.dp,
                    modifier    = Modifier.size(48.dp)
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text       = "SOS",
                        fontSize   = 54.sp,
                        fontWeight = FontWeight.Black,
                        color      = Color.White,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text      = "TAP FOR HELP",
                        fontSize  = 11.sp,
                        color     = Color.White.copy(alpha = 0.80f),
                        letterSpacing = 2.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun LocationStatusBadge(status: LocationStatus) {
    val (dot, text, color) = when (status) {
        LocationStatus.FETCHING     -> Triple("\u23F3", "Acquiring location\u2026", LocationAmber)
        LocationStatus.ACQUIRED     -> Triple("\u25CF", "Location acquired", LocationGreen)
        LocationStatus.UNAVAILABLE  -> Triple("\u25CF", "Location unavailable \u2014 SOS still sent", LocationRed)
        LocationStatus.IDLE         -> Triple("\u25CF", "Tap SOS to send alert", Color.White.copy(alpha = 0.35f))
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(dot, color = color, fontSize = 10.sp)
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmergencyTypePicker(
    selected: EmergencyType,
    onSelected: (EmergencyType) -> Unit,
    modifier: Modifier = Modifier
) {
    val types = EmergencyType.entries.filter { it != EmergencyType.UNSPECIFIED }

    Column(modifier) {
        Text(
            text     = "Emergency type",
            fontSize = 12.sp,
            color    = Color.White.copy(alpha = 0.55f),
            modifier = Modifier.padding(bottom = 10.dp, start = 4.dp)
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement   = Arrangement.spacedBy(8.dp)
        ) {
            types.forEach { type ->
                val isSelected = selected == type
                FilterChip(
                    selected = isSelected,
                    onClick  = { onSelected(type) },
                    label    = {
                        Text(
                            text       = type.apiValue.replace("_", " ").replaceFirstChar { it.uppercase() },
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize   = 13.sp
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor   = SosRed,
                        selectedLabelColor       = Color.White,
                        containerColor           = Color.White.copy(alpha = 0.07f),
                        labelColor               = Color.White.copy(alpha = 0.75f)
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled             = true,
                        selected            = isSelected,
                        selectedBorderColor = SosRed,
                        borderColor         = Color.White.copy(alpha = 0.15f)
                    )
                )
            }
        }
    }
}

@Composable
private fun sosTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = SosRed,
    unfocusedBorderColor = Color.White.copy(alpha = 0.18f),
    focusedLabelColor    = SosRed,
    unfocusedLabelColor  = Color.White.copy(alpha = 0.45f),
    focusedTextColor     = Color.White,
    unfocusedTextColor   = Color.White.copy(alpha = 0.85f),
    cursorColor          = SosRed,
    focusedPlaceholderColor   = Color.White.copy(alpha = 0.25f),
    unfocusedPlaceholderColor = Color.White.copy(alpha = 0.25f)
)
