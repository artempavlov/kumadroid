package org.kumadroid.app.ui

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

object KumaColors {
    val Up = Color(0xFF4ADE80)
    val UpDim = Color(0xFF1F7A45)
    val Down = Color(0xFFF87171)
    val DownDim = Color(0xFF7F1D1D)
    val Pending = Color(0xFFFBBF24)
    val PendingDim = Color(0xFF78350F)
    val Maintenance = Color(0xFF60A5FA)
    val MaintenanceDim = Color(0xFF1E3A8A)
    val Brand = Color(0xFF5CDD8B)
    val BrandDark = Color(0xFF142A1D)
}

private val DarkScheme = darkColorScheme(
    primary = KumaColors.Brand,
    onPrimary = Color(0xFF07240F),
    primaryContainer = Color(0xFF12351F),
    onPrimaryContainer = Color(0xFFB8F5CE),
    secondary = Color(0xFF9CC7FF),
    onSecondary = Color(0xFF062039),
    secondaryContainer = Color(0xFF17324D),
    onSecondaryContainer = Color(0xFFD1E4FF),
    tertiary = Color(0xFFF6C177),
    background = Color(0xFF0B0F14),
    onBackground = Color(0xFFE6EAF0),
    surface = Color(0xFF111821),
    onSurface = Color(0xFFE6EAF0),
    surfaceVariant = Color(0xFF1A2430),
    onSurfaceVariant = Color(0xFFA9B7C6),
    surfaceContainer = Color(0xFF131B25),
    surfaceContainerHigh = Color(0xFF1A2430),
    surfaceContainerHighest = Color(0xFF223040),
    outline = Color(0xFF3A4A5C),
    outlineVariant = Color(0xFF26333F),
    error = KumaColors.Down,
    onError = Color(0xFF2A0505),
    errorContainer = Color(0xFF4A1111),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF137A45),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC5F2D6),
    onPrimaryContainer = Color(0xFF03301A),
    secondary = Color(0xFF2E5C8A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD4E5FF),
    onSecondaryContainer = Color(0xFF0A223A),
    tertiary = Color(0xFF8A5A00),
    background = Color(0xFFF6F8F7),
    onBackground = Color(0xFF121A16),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF121A16),
    surfaceVariant = Color(0xFFEDF2EF),
    onSurfaceVariant = Color(0xFF4F5F56),
    surfaceContainer = Color(0xFFF0F4F2),
    surfaceContainerHigh = Color(0xFFE7EDEA),
    surfaceContainerHighest = Color(0xFFDDE5E1),
    outline = Color(0xFF7A8B81),
    outlineVariant = Color(0xFFD3DDD7),
    error = Color(0xFFB3261E),
    onError = Color.White,
)

private val KumaTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 27.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontSize = 23.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.5.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
)

@Composable
fun KumaDroidTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val scheme = if (darkTheme) DarkScheme else LightScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }
    MaterialTheme(colorScheme = scheme, typography = KumaTypography, content = content)
}
