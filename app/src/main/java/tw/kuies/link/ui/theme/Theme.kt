package tw.kuies.link.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = SagePrimary,
    onPrimary = SageOnPrimary,
    primaryContainer = SagePrimaryContainer,
    onPrimaryContainer = SageOnPrimaryContainer,
    inversePrimary = SagePrimaryDark,
    secondary = ApricotSecondary,
    onSecondary = ApricotOnSecondary,
    secondaryContainer = ApricotContainer,
    onSecondaryContainer = ApricotOnContainer,
    tertiary = ApricotSecondary,
    onTertiary = ApricotOnSecondary,
    background = WarmBackground,
    onBackground = InkOnBackground,
    surface = WarmSurface,
    onSurface = InkOnBackground,
    surfaceVariant = WarmSurfaceVariant,
    onSurfaceVariant = InkOnSurfaceVariant,
    surfaceTint = SagePrimary,
    outline = WarmOutline,
    outlineVariant = WarmOutlineVariant,
    error = SoftRed,
    onError = WarmSurface,
    errorContainer = SoftRedContainer,
    onErrorContainer = OnSoftRedContainer,
    scrim = InkOnBackground,
)

private val DarkColors = darkColorScheme(
    primary = SagePrimaryDark,
    onPrimary = SageOnPrimaryDark,
    primaryContainer = SagePrimaryContainerDark,
    onPrimaryContainer = SageOnPrimaryContainerDark,
    secondary = ApricotSecondaryDark,
    onSecondary = ApricotOnSecondaryDark,
    secondaryContainer = ApricotContainerDark,
    onSecondaryContainer = ApricotOnContainerDark,
    tertiary = ApricotSecondaryDark,
    onTertiary = ApricotOnSecondaryDark,
    background = WarmBackgroundDark,
    onBackground = InkOnBackgroundDark,
    surface = WarmSurfaceDark,
    onSurface = InkOnBackgroundDark,
    surfaceVariant = WarmSurfaceVariantDark,
    onSurfaceVariant = InkOnSurfaceVariantDark,
    surfaceTint = SagePrimaryDark,
    outline = WarmOutlineDark,
    outlineVariant = WarmOutlineVariantDark,
    error = SoftRedDark,
    onError = WarmBackgroundDark,
    errorContainer = SoftRedContainerDark,
    onErrorContainer = OnSoftRedContainerDark,
    scrim = WarmBackgroundDark,
)

/**
 * Brand palette is fixed instead of dynamic so the sage/warm-white identity survives Android 12+
 * dynamic theming; light and dark schemes both meet the contrast needed for body text.
 */
@Composable
fun KuiesLinkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content,
    )
}
