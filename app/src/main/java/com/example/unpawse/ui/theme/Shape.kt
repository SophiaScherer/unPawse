package com.example.unpawse.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Corner radii from DESIGN.md. The brand leans "extra-rounded": surface cards use 24dp
 * (extraLarge), while buttons/chips go fully pill-shaped at their call sites.
 */
val UnPawseShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/** Convenience alias for the signature 24dp card corner. */
val CardShape = RoundedCornerShape(24.dp)

/** Input fields / icon tiles use a 16dp radius per DESIGN.md. */
val FieldShape = RoundedCornerShape(16.dp)
