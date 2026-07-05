package com.example.unpawse.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Brand palette from the mockup design tokens (DESIGN.md).
 *
 * Named after their Material 3 color roles so the mapping in [UnPawseTheme] reads directly.
 * "Blush pink" primary-container and the "warm white" surface are the signature of the brand;
 * see the Warm Minimalist notes in DESIGN.md.
 */

// Primary — plum / blush
val Plum = Color(0xFF815060)
val OnPrimary = Color(0xFFFFFFFF)
val BlushContainer = Color(0xFFF5B6C8)
val OnBlushContainer = Color(0xFF744554)
val InversePrimaryPink = Color(0xFFF4B5C7)

// Secondary — sage green (health successes / confirmations)
val Sage = Color(0xFF286B33)
val OnSecondary = Color(0xFFFFFFFF)
val SageContainer = Color(0xFFABF4AC)
val OnSageContainer = Color(0xFF2E7238)

// Tertiary — coral (warnings / urgent pet needs)
val Coral = Color(0xFF9F4122)
val OnTertiary = Color(0xFFFFFFFF)
val CoralContainer = Color(0xFFFFB6A0)
val OnCoralContainer = Color(0xFF903618)

// Error
val ErrorRed = Color(0xFFBA1A1A)
val OnError = Color(0xFFFFFFFF)
val ErrorContainer = Color(0xFFFFDAD6)
val OnErrorContainer = Color(0xFF93000A)

// Warm surfaces (light) — the "cozy, not clinical" warm-white ladder
val WarmWhite = Color(0xFFFFF8F8)          // surface / background
val SurfaceDim = Color(0xFFE2D8D9)
val SurfaceBright = Color(0xFFFFF8F8)
val SurfaceContainerLowest = Color(0xFFFFFFFF)
val SurfaceContainerLow = Color(0xFFFCF1F2)
val SurfaceContainer = Color(0xFFF6EBEC)
val SurfaceContainerHigh = Color(0xFFF0E6E7)
val SurfaceContainerHighest = Color(0xFFEAE0E1)

val OnSurface = Color(0xFF1F1A1B)
val OnSurfaceVariant = Color(0xFF504347)
val Outline = Color(0xFF827377)
val OutlineVariant = Color(0xFFD4C2C6)
val SurfaceVariant = Color(0xFFEAE0E1)

val InverseSurface = Color(0xFF352F30)
val InverseOnSurface = Color(0xFFF9EEEF)

// Fixed roles (used to derive the dark scheme; also available for accents)
val PrimaryFixed = Color(0xFFFFD9E2)
val PrimaryFixedDim = Color(0xFFF4B5C7)
val OnPrimaryFixed = Color(0xFF330E1D)
val OnPrimaryFixedVariant = Color(0xFF673948)
val SecondaryFixedDim = Color(0xFF90D792)
val OnSecondaryFixedVariant = Color(0xFF07521D)
val TertiaryFixedDim = Color(0xFFFFB59E)
val OnTertiaryFixedVariant = Color(0xFF7F2A0D)

// Warm surfaces (dark) — provisional, derived to keep the warm tint in dark mode
val DarkSurface = Color(0xFF171213)
val DarkSurfaceContainerLowest = Color(0xFF120D0E)
val DarkSurfaceContainerLow = Color(0xFF1F1A1B)
val DarkSurfaceContainer = Color(0xFF231D1F)
val DarkSurfaceContainerHigh = Color(0xFF2E2729)
val DarkSurfaceContainerHighest = Color(0xFF393234)
val DarkOnSurface = Color(0xFFEAE0E1)
val DarkOnSurfaceVariant = Color(0xFFD4C2C6)
val DarkOutline = Color(0xFF9D8D91)
val DarkOutlineVariant = Color(0xFF504347)
val OnSecondaryFixed = Color(0xFF002107)
val OnTertiaryFixed = Color(0xFF3A0B00)
val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)
val DarkErrorContainer = Color(0xFF93000A)
