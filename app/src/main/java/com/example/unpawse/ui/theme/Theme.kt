package com.example.unpawse.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Light scheme mapped 1:1 from the DESIGN.md tokens. Dynamic color is intentionally NOT used —
 * the blush-pink brand identity is the whole point.
 */
private val LightColors = lightColorScheme(
    primary = Plum,
    onPrimary = OnPrimary,
    primaryContainer = BlushContainer,
    onPrimaryContainer = OnBlushContainer,
    inversePrimary = InversePrimaryPink,
    secondary = Sage,
    onSecondary = OnSecondary,
    secondaryContainer = SageContainer,
    onSecondaryContainer = OnSageContainer,
    tertiary = Coral,
    onTertiary = OnTertiary,
    tertiaryContainer = CoralContainer,
    onTertiaryContainer = OnCoralContainer,
    error = ErrorRed,
    onError = OnError,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
    background = WarmWhite,
    onBackground = OnSurface,
    surface = WarmWhite,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    surfaceDim = SurfaceDim,
    surfaceBright = SurfaceBright,
    surfaceContainerLowest = SurfaceContainerLowest,
    surfaceContainerLow = SurfaceContainerLow,
    surfaceContainer = SurfaceContainer,
    surfaceContainerHigh = SurfaceContainerHigh,
    surfaceContainerHighest = SurfaceContainerHighest,
    outline = Outline,
    outlineVariant = OutlineVariant,
    inverseSurface = InverseSurface,
    inverseOnSurface = InverseOnSurface,
)

/**
 * Dark scheme — provisional. Derived from the fixed/inverse tokens to preserve the warm tint;
 * refine against a proper dark mockup later. The Settings dark-mode toggle actually flips to this.
 */
private val DarkColors = darkColorScheme(
    primary = InversePrimaryPink,
    onPrimary = OnPrimaryFixed,
    primaryContainer = OnPrimaryFixedVariant,
    onPrimaryContainer = PrimaryFixed,
    inversePrimary = Plum,
    secondary = SecondaryFixedDim,
    onSecondary = OnSecondaryFixed,
    secondaryContainer = OnSecondaryFixedVariant,
    onSecondaryContainer = SageContainer,
    tertiary = TertiaryFixedDim,
    onTertiary = OnTertiaryFixed,
    tertiaryContainer = OnTertiaryFixedVariant,
    onTertiaryContainer = CoralContainer,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = ErrorContainer,
    background = DarkSurface,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkOutlineVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceDim = DarkSurfaceContainerLowest,
    surfaceBright = DarkSurfaceContainerHighest,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    inverseSurface = DarkOnSurface,
    inverseOnSurface = DarkSurface,
)

@Composable
fun UnPawseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Icons in the status/nav bars should contrast the warm background.
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = UnPawseTypography,
        shapes = UnPawseShapes,
        content = content,
    )
}
