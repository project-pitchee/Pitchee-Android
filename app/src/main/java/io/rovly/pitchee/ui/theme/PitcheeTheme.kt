package io.rovly.pitchee.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFFCD5B2E),
    onPrimary = Color(0xFF2E0D00),
    primaryContainer = Color(0xFFFFDCCF),
    onPrimaryContainer = Color(0xFF3A1000),
    inversePrimary = Color(0xFFFFB59A),
    secondary = Color(0xFF3F6C68),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFC0ECE6),
    onSecondaryContainer = Color(0xFF00201E),
    tertiary = Color(0xFF745B18),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE49A),
    onTertiaryContainer = Color(0xFF251A00),
    background = Color(0xFFFFF8F5),
    onBackground = Color(0xFF211A17),
    surface = Color(0xFFFFF8F5),
    onSurface = Color(0xFF211A17),
    surfaceVariant = Color(0xFFF4DED5),
    onSurfaceVariant = Color(0xFF53433D),
    surfaceDim = Color(0xFFE6D7D2),
    surfaceBright = Color(0xFFFFF8F5),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF1EC),
    surfaceContainer = Color(0xFFF8EAE5),
    surfaceContainerHigh = Color(0xFFF2E2DC),
    surfaceContainerHighest = Color(0xFFECDDD6),
    inverseSurface = Color(0xFF382E2B),
    inverseOnSurface = Color(0xFFFFEDE7),
    outline = Color(0xFF85736C),
    outlineVariant = Color(0xFFD8C2BA),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    scrim = Color(0xFF000000),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB59A),
    onPrimary = Color(0xFF5A1B00),
    primaryContainer = Color(0xFF7E2E10),
    onPrimaryContainer = Color(0xFFFFDCCF),
    inversePrimary = Color(0xFFCD5B2E),
    secondary = Color(0xFFA5CCC7),
    onSecondary = Color(0xFF0C3734),
    secondaryContainer = Color(0xFF264F4C),
    onSecondaryContainer = Color(0xFFC0ECE6),
    tertiary = Color(0xFFE3C56D),
    onTertiary = Color(0xFF3D2F00),
    tertiaryContainer = Color(0xFF584700),
    onTertiaryContainer = Color(0xFFFFE49A),
    background = Color(0xFF1B120F),
    onBackground = Color(0xFFF1DFD8),
    surface = Color(0xFF1B120F),
    onSurface = Color(0xFFF1DFD8),
    surfaceVariant = Color(0xFF53433D),
    onSurfaceVariant = Color(0xFFD8C2BA),
    surfaceDim = Color(0xFF1B120F),
    surfaceBright = Color(0xFF443733),
    surfaceContainerLowest = Color(0xFF150D0B),
    surfaceContainerLow = Color(0xFF241916),
    surfaceContainer = Color(0xFF281D1A),
    surfaceContainerHigh = Color(0xFF332724),
    surfaceContainerHighest = Color(0xFF3E312D),
    inverseSurface = Color(0xFFF1DFD8),
    inverseOnSurface = Color(0xFF382E2B),
    outline = Color(0xFFA08C84),
    outlineVariant = Color(0xFF53433D),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color(0xFF000000),
)

private val PitcheeTypography = Typography(
    displayLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp,
    ),
    displayMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp,
    ),
    displaySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp,
    ),
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)

@Composable
fun PitcheeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = PitcheeTypography,
        content = content,
    )
}
