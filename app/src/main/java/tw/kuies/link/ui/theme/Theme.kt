package tw.kuies.link.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = SagePrimary,
    onPrimary = Color.White,
    primaryContainer = SageContainer,
    onPrimaryContainer = SageOnContainer,
    inversePrimary = SageAccent,
    secondary = TextSecondary,
    onSecondary = Color.White,
    secondaryContainer = InfoContainer,
    onSecondaryContainer = InfoContent,
    tertiary = SageAccent,
    onTertiary = Color.White,
    background = WarmBackground,
    onBackground = TextPrimary,
    surface = WarmSurface,
    onSurface = TextPrimary,
    surfaceVariant = WarmSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    surfaceTint = SageAccent,
    surfaceContainerLow = WarmSurfaceMuted,
    outline = OutlineStrong,
    outlineVariant = OutlineSoft,
    error = MutedError,
    onError = Color.White,
    errorContainer = MutedErrorContainer,
    onErrorContainer = MutedError,
    scrim = TextPrimary,
)

private val DarkColors = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    inversePrimary = SageAccent,
    secondary = DarkTextSecondary,
    onSecondary = DarkBackground,
    secondaryContainer = DarkInfoContainer,
    onSecondaryContainer = DarkInfoContent,
    tertiary = DarkPrimary,
    onTertiary = DarkOnPrimary,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    surfaceTint = DarkPrimary,
    surfaceContainerLow = DarkSurfaceMuted,
    outline = DarkOutlineStrong,
    outlineVariant = DarkOutlineSoft,
    error = DarkMutedError,
    onError = DarkBackground,
    errorContainer = DarkMutedErrorContainer,
    onErrorContainer = DarkMutedError,
    scrim = DarkBackground,
)

/**
 * Brand palette is fixed instead of dynamic so the sage/warm-white identity survives Android 12+
 * dynamic theming; the dark scheme is configured independently for comfortable night reading.
 */
@Composable
fun KuiesLinkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        shapes = KuiesShapes,
        content = content,
    )
}
