package io.rovly.pitchee.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFFFFB6C1),
    onPrimary = Color(0xFF4A1D2B),
    primaryContainer = Color(0xFFFFE4EA),
    onPrimaryContainer = Color(0xFF4A1D2B),
    inversePrimary = Color(0xFFC7506A),
    secondary = Color(0xFF75565F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD9E3),
    onSecondaryContainer = Color(0xFF2B151D),
    tertiary = Color(0xFF7D5260),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD9E6),
    onTertiaryContainer = Color(0xFF31101D),
    background = Color(0xFFFFF8F8),
    onBackground = Color(0xFF201A1B),
    surface = Color(0xFFFFF8F8),
    onSurface = Color(0xFF201A1B),
    surfaceVariant = Color(0xFFF3DDE1),
    onSurfaceVariant = Color(0xFF524347),
    outline = Color(0xFF857377),
    outlineVariant = Color(0xFFD7C1C6),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    scrim = Color(0xFF000000),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB6C1),
    onPrimary = Color(0xFF4A1D2B),
    primaryContainer = Color(0xFF743344),
    onPrimaryContainer = Color(0xFFFFD9E3),
    inversePrimary = Color(0xFFC7506A),
    secondary = Color(0xFFE4BDC7),
    onSecondary = Color(0xFF432A32),
    secondaryContainer = Color(0xFF5B4048),
    onSecondaryContainer = Color(0xFFFFD9E3),
    tertiary = Color(0xFFEFB8CA),
    onTertiary = Color(0xFF482533),
    tertiaryContainer = Color(0xFF613B49),
    onTertiaryContainer = Color(0xFFFFD9E6),
    background = Color(0xFF191113),
    onBackground = Color(0xFFF0DEE1),
    surface = Color(0xFF191113),
    onSurface = Color(0xFFF0DEE1),
    surfaceVariant = Color(0xFF524347),
    onSurfaceVariant = Color(0xFFD7C1C6),
    outline = Color(0xFFA08C91),
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
