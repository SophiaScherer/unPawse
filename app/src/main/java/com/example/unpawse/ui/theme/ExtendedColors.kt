package com.example.unpawse.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The colours Material 3 has no slot for. Every M3 role inverts in lightness between the schemes,
 * so a role borrowed to carry *identity* swaps appearance on a theme flip — Social and
 * Entertainment did exactly that as `primary` and `primaryContainer`. A new semantic colour belongs
 * here, not in a spare M3 role.
 */
@Immutable
data class UnPawseExtendedColors(
    /** Usage-category identity. One hue per slot, held across both themes. */
    val categorySocial: Color,
    val categoryProductivity: Color,
    val categoryEntertainment: Color,
    val categoryOther: Color,
    /** "Something was genuinely earned" — a granted permission, a verified cat, time banked. */
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    /** The ground a [com.example.unpawse.ui.components.PawCard] sits on; lifts in both themes. */
    val cardSurface: Color,
)

private val LightExtendedColors = UnPawseExtendedColors(
    categorySocial = Plum,
    categoryProductivity = Sage,
    categoryEntertainment = Coral,
    categoryOther = OnSurfaceVariant,
    success = Sage,
    successContainer = SageContainer,
    onSuccessContainer = OnSecondaryFixedVariant,
    cardSurface = SurfaceContainerLowest,
)

private val DarkExtendedColors = UnPawseExtendedColors(
    categorySocial = InversePrimaryPink,
    categoryProductivity = SecondaryFixedDim,
    categoryEntertainment = BrightCoral,
    categoryOther = DarkOutline,
    success = SecondaryFixedDim,
    successContainer = DarkSuccessContainer,
    onSuccessContainer = SageContainer,
    cardSurface = DarkCardSurface,
)

/** Internal rather than private so the tests can compare the two sets against each other. */
internal fun extendedColorsFor(darkTheme: Boolean): UnPawseExtendedColors =
    if (darkTheme) DarkExtendedColors else LightExtendedColors

/**
 * Defaults to the light set rather than throwing: the block overlay renders outside an Activity,
 * where a crash would cost far more than a wrong shade. Same bargain `colorScheme` already makes.
 */
val LocalUnPawseColors = staticCompositionLocalOf { LightExtendedColors }

/** Reads like `MaterialTheme.colorScheme`, deliberately — same call shape at every use site. */
val MaterialTheme.unPawseColors: UnPawseExtendedColors
    @Composable
    @ReadOnlyComposable
    get() = LocalUnPawseColors.current
