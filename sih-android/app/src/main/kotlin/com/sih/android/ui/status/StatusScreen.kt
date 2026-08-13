package com.sih.android.ui.status

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sih.data.model.SosStatus

// ─── Palette (mirrors HomeScreen) ────────────────────────────────────────────
private val BgTop       = Color(0xFF1A0000)
private val BgMid       = Color(0xFF2D0000)
private val BgBottom    = Color(0xFF121212)
private val DimWhite    = Color(0xCCFFFFFF)
private val SosRed      = Color(0xFFD32F2F)

// Status pill colours
private val PillAmber   = Color(0xFFFFC107)
private val PillBlue    = Color(0xFF2196F3)
private val PillGreen   = Color(0xFF43A047)

// Surface card
private val CardSurface = Color(0xFF1E1E1E)

/**
 * My SOS status screen.
 *
 * Shows the citizen's own SOS with:
 *   - Emergency type + creation time (from local Room)
 *   - One status pill: 🟡 Waiting to relay / 🔵 Relaying / 🟢 Uploaded
 *   - Backend delivery check via GET /api/v1/sos/{uuid}/status (when online)
 */
@Composable
fun StatusScreen(
    uuid: String,
    onBack: () -> Unit,
    viewModel: StatusViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f    to BgTop,
                        0.45f to BgMid,
                        1f    to BgBottom
                    )
                )
            )
    ) {
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SosRed)
            }
            return@Box
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ── Top bar ───────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint               = DimWhite
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick  = viewModel::checkBackendStatus,
                    enabled  = !state.isCheckingBackend
                ) {
                    Icon(
                        imageVector        = Icons.Filled.Refresh,
                        contentDescription = "Check delivery",
                        tint               = if (state.isCheckingBackend) DimWhite.copy(alpha = 0.3f) else DimWhite
                    )
                }
            }

            // ── Title ─────────────────────────────────────────────────────────
            Spacer(Modifier.height(8.dp))
            Text(
                text          = "My SOS",
                fontSize      = 30.sp,
                fontWeight    = FontWeight.Black,
                color         = Color.White,
                letterSpacing = 0.5.sp
            )
            Text(
                text     = "Alert sent from this device",
                fontSize = 13.sp,
                color    = DimWhite.copy(alpha = 0.55f),
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(Modifier.height(32.dp))

            // ── SOS info card ─────────────────────────────────────────────────
            val localSos    = state.localSos
            val localStatus = localSos?.status ?: SosStatus.PENDING_LOCAL.apiValue

            Surface(
                modifier      = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                shape         = RoundedCornerShape(16.dp),
                color         = CardSurface,
                tonalElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SosInfoRow(
                        label = "Emergency",
                        value = state.emergencyLabel.ifBlank { "Emergency" }
                    )
                    SosInfoRow(
                        label = "Created",
                        value = state.formattedCreatedAt.ifBlank { "—" }
                    )
                }
            }

            Spacer(Modifier.height(36.dp))

            // ── Status pill ───────────────────────────────────────────────────
            AnimatedContent(
                targetState = localStatus,
                label       = "status_pill"
            ) { status ->
                StatusPill(status = status)
            }

            Spacer(Modifier.height(14.dp))

            // Subtitle beneath pill
            val pillSubtitle = when (localStatus) {
                SosStatus.UPLOADED.apiValue    ->
                    "Your SOS reached the server successfully."
                SosStatus.IN_RELAY.apiValue    ->
                    "Being relayed via nearby devices to reach the server."
                else                           ->
                    "Stored safely on device. Will upload automatically\nwhen connectivity returns."
            }
            Text(
                text      = pillSubtitle,
                fontSize  = 13.sp,
                color     = DimWhite.copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier  = Modifier.padding(horizontal = 32.dp)
            )

            Spacer(Modifier.height(40.dp))

            // ── Backend delivery section ──────────────────────────────────────
            HorizontalDivider(
                modifier  = Modifier.padding(horizontal = 32.dp),
                color     = Color.White.copy(alpha = 0.08f)
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text      = "BACKEND DELIVERY",
                fontSize  = 11.sp,
                color     = DimWhite.copy(alpha = 0.4f),
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(12.dp))

            when {
                state.isCheckingBackend -> {
                    Row(
                        verticalAlignment    = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color       = DimWhite.copy(alpha = 0.7f)
                        )
                        Text(
                            text     = "Checking with server…",
                            fontSize = 14.sp,
                            color    = DimWhite.copy(alpha = 0.7f)
                        )
                    }
                }

                state.backendStatus != null -> {
                    Row(
                        verticalAlignment    = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(PillGreen)
                        )
                        Text(
                            text       = "Confirmed by server — ${state.backendStatus}",
                            fontSize   = 14.sp,
                            color      = PillGreen,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                state.backendError != null -> {
                    Text(
                        text      = state.backendError!!,
                        fontSize  = 14.sp,
                        color     = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier  = Modifier.padding(horizontal = 32.dp)
                    )
                }

                else -> {
                    Text(
                        text     = "Tap ↻ to check when online",
                        fontSize = 14.sp,
                        color    = DimWhite.copy(alpha = 0.35f)
                    )
                }
            }

            Spacer(Modifier.height(40.dp))

            // ── UUID (low prominence) ─────────────────────────────────────────
            Text(
                text       = "SOS ID",
                fontSize   = 10.sp,
                color      = DimWhite.copy(alpha = 0.25f),
                letterSpacing = 1.sp
            )
            Text(
                text       = uuid,
                fontSize   = 10.sp,
                fontFamily = FontFamily.Monospace,
                color      = DimWhite.copy(alpha = 0.20f),
                modifier   = Modifier.padding(top = 4.dp, bottom = 32.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Status Pill
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StatusPill(status: String) {
    val (emoji, label, pillColor) = when (status) {
        SosStatus.UPLOADED.apiValue -> Triple("🟢", "Uploaded",         PillGreen)
        SosStatus.IN_RELAY.apiValue -> Triple("🔵", "Relaying",         PillBlue)
        else                        -> Triple("🟡", "Waiting to relay",  PillAmber)
    }

    Row(
        modifier              = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(pillColor.copy(alpha = 0.12f))
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text     = emoji,
            fontSize = 22.sp
        )
        Text(
            text       = label,
            fontSize   = 18.sp,
            fontWeight = FontWeight.Bold,
            color      = pillColor
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SOS info row (label + value)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SosInfoRow(label: String, value: String) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(
            text     = label,
            fontSize = 13.sp,
            color    = DimWhite.copy(alpha = 0.5f)
        )
        Text(
            text       = value,
            fontSize   = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color      = Color.White
        )
    }
}
