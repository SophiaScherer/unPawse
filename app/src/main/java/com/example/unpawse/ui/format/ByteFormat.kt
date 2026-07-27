package com.example.unpawse.ui.format

import java.util.Locale

private const val UNIT = 1024.0

/**
 * Compact size copy for the Photo storage screen: "0 KB", "812 KB", "38.4 MB", "1.2 GB".
 *
 * Binary units (1 KB = 1024 bytes), matching what Android's own storage settings report, so the two
 * figures agree when a user compares them. Pure, so it's unit-tested without a device.
 */
fun formatBytes(bytes: Long): String {
    val safe = bytes.coerceAtLeast(0L)
    if (safe < UNIT) return "$safe B"

    var value = safe / UNIT
    val units = listOf("KB", "MB", "GB")
    var index = 0
    while (value >= UNIT && index < units.lastIndex) {
        value /= UNIT
        index++
    }

    // Whole numbers below 10 read oddly with a decimal ("9.0 MB"); above it the decimal is noise.
    val pattern = if (value < 10) "%.1f %s" else "%.0f %s"
    return String.format(Locale.US, pattern, value, units[index])
}
