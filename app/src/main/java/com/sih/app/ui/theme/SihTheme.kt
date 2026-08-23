package com.sih.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val SosRed      = Color(0xFFD32F2F)
val SosRedLight = Color(0xFFFF6659)
val SosRedDark  = Color(0xFF9A0007)
val OnSosRed    = Color(0xFFFFFFFF)

private val DarkScheme = darkColorScheme(
    primary          = SosRedLight,
    onPrimary        = OnSosRed,
    primaryContainer = SosRedDark,
    background       = Color(0xFF121212),
    surface          = Color(0xFF1E1E1E),
    onBackground     = Color(0xFFE0E0E0),
    onSurface        = Color(0xFFE0E0E0),
    error            = Color(0xFFCF6679)
)

private val LightScheme = lightColorScheme(
    primary          = SosRed,
    onPrimary        = OnSosRed,
    primaryContainer = SosRedLight,
    background       = Color(0xFFFAFAFA),
    surface          = Color(0xFFFFFFFF),
    onBackground     = Color(0xFF212121),
    onSurface        = Color(0xFF212121),
    error            = Color(0xFFB00020)
)

private val SihTypography = Typography(
    displayLarge  = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black,   fontSize = 57.sp, lineHeight = 64.sp),
    displayMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,    fontSize = 45.sp, lineHeight = 52.sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,    fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium= TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,fontSize = 28.sp, lineHeight = 36.sp),
    titleLarge    = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,    fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium   = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall    = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,  fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge     = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,  fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium    = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,  fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall     = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,  fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge    = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,  fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium   = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,  fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall    = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,  fontSize = 11.sp, lineHeight = 16.sp),
)

@Composable
fun SihTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography  = SihTypography,
        content     = content
    )
}
