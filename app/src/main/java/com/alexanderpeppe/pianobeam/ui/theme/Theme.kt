package com.alexanderpeppe.pianobeam.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.alexanderpeppe.pianobeam.data.AppThemeMode

private val PianoBeamLightScheme = lightColorScheme(
    primary = Color(0xFF0B6FA4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8F0FF),
    onPrimaryContainer = Color(0xFF00263A),
    secondary = Color(0xFF12805C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFF8E7),
    onSecondaryContainer = Color(0xFF003826),
    tertiary = Color(0xFF9061C2),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFECDDFF),
    onTertiaryContainer = Color(0xFF301455),
    background = Color(0xFFFBFDFF),
    onBackground = Color(0xFF172026),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF172026),
    surfaceVariant = Color(0xFFE6F0F6),
    onSurfaceVariant = Color(0xFF44525B),
    outline = Color(0xFF7D8D98),
    error = Color(0xFFB3261E)
)

private val PianoBeamDarkScheme = darkColorScheme(
    primary = Color(0xFF94DDFF),
    onPrimary = Color(0xFF00283B),
    primaryContainer = Color(0xFF126184),
    onPrimaryContainer = Color(0xFFD8F2FF),
    secondary = Color(0xFF9EF6D0),
    onSecondary = Color(0xFF003B27),
    secondaryContainer = Color(0xFF147355),
    onSecondaryContainer = Color(0xFFCFFBE8),
    tertiary = Color(0xFFD8C3FF),
    onTertiary = Color(0xFF32145F),
    tertiaryContainer = Color(0xFF684BA3),
    onTertiaryContainer = Color(0xFFF0E5FF),
    background = Color(0xFF10212C),
    onBackground = Color(0xFFECF7FF),
    surface = Color(0xFF162B38),
    onSurface = Color(0xFFECF7FF),
    surfaceVariant = Color(0xFF2F4655),
    onSurfaceVariant = Color(0xFFD0E0EA),
    outline = Color(0xFF9AB2C2),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF7A271A),
    onErrorContainer = Color(0xFFFFDAD4),
    inverseSurface = Color(0xFFECF7FF),
    inverseOnSurface = Color(0xFF17303F)
)

private val PianoBeamShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(3.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(6.dp),
    extraLarge = RoundedCornerShape(8.dp)
)

@Composable
fun PianoBeamTheme(
    themeMode: AppThemeMode = AppThemeMode.System,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        AppThemeMode.System -> isSystemInDarkTheme()
        AppThemeMode.Light -> false
        AppThemeMode.Dark -> true
    }

    MaterialTheme(
        colorScheme = if (darkTheme) PianoBeamDarkScheme else PianoBeamLightScheme,
        typography = Typography(),
        shapes = PianoBeamShapes,
        content = content
    )
}
