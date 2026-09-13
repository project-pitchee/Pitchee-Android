package io.rovly.pitchee.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFFB84A6B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD9E3),
    onPrimaryContainer = Color(0xFF3E1122),
    inversePrimary = Color(0xFFFFB1C8),
    secondary = Color(0xFF5B6FAF),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE6FF),
    onSecondaryContainer = Color(0xFF18264F),
    tertiary = Color(0xFF7F5F82),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF3D9F1),
    onTertiaryContainer = Color(0xFF321A34),
    background = Color(0xFFFFF8F9),
    onBackground = Color(0xFF211A1C),
    surface = Color(0xFFFFF8F9),
    onSurface = Color(0xFF211A1C),
    surfaceVariant = Color(0xFFF3DDE3),
    onSurfaceVariant = Color(0xFF524347),
    outline = Color(0xFF857377),
    outlineVariant = Color(0xFFD7C1C7),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    scrim = Color(0xFF000000),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB1C8),
    onPrimary = Color(0xFF5E1130),
    primaryContainer = Color(0xFF7D2F4B),
    onPrimaryContainer = Color(0xFFFFD9E3),
    inversePrimary = Color(0xFFB84A6B),
    secondary = Color(0xFFBAC7FF),
    onSecondary = Color(0xFF25336A),
    secondaryContainer = Color(0xFF3B4A80),
    onSecondaryContainer = Color(0xFFDCE6FF),
    tertiary = Color(0xFFE4BDE6),
    onTertiary = Color(0xFF48254B),
    tertiaryContainer = Color(0xFF603D63),
    onTertiaryContainer = Color(0xFFF3D9F1),
    background = Color(0xFF191113),
    onBackground = Color(0xFFF0DEE1),
    surface = Color(0xFF191113),
    onSurface = Color(0xFFF0DEE1),
    surfaceVariant = Color(0xFF524347),
    onSurfaceVariant = Color(0xFFD7C1C7),
    outline = Color(0xFFA08C92),
    outlineVariant = Color(0xFF524347),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color(0xFF000000),
)

@Composable
fun PitcheeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
