package com.example.unpawse.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.unpawse.data.schedule.EVERY_DAY_MASK
import com.example.unpawse.data.schedule.WEEKDAYS_MASK
import com.example.unpawse.data.schedule.WEEKENDS_MASK
import com.example.unpawse.data.schedule.dayBit
import com.example.unpawse.data.schedule.maskCovers
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * Day-of-week selector over a bitmask: seven toggle chips plus the three presets people actually
 * reach for. Stateless — it renders [daysMask] and reports the next mask through [onMaskChange].
 *
 * Lives in `components/` rather than beside the schedules screen because the mask is a data-layer
 * concept (`ScheduleMath`) that any future day-scoped setting would want to edit the same way.
 */
@Composable
fun DayOfWeekChips(
    daysMask: Int,
    onMaskChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    locale: Locale = Locale.getDefault(),
) {
    Column(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DayOfWeek.values().forEach { day ->
                val selected = maskCovers(daysMask, day)
                FilterChip(
                    selected = selected,
                    // Toggling the last remaining day would leave a window that can never fire, so
                    // the mask is left alone rather than the chip being disabled — the tap simply
                    // does nothing, and the chip stays where the user expects it.
                    onClick = {
                        val next = daysMask xor dayBit(day)
                        if (next != 0) onMaskChange(next)
                    },
                    label = {
                        Text(
                            text = day.getDisplayName(TextStyle.NARROW, locale),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    },
                    shape = FilterChipDefaults.shape,
                    colors = selectedChipColors(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** The "Weekdays / Weekends / Every day" shortcuts, shown above the per-day chips. */
@Composable
fun DayPresetChips(
    daysMask: Int,
    onMaskChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PRESETS.forEach { (label, mask) ->
            FilterChip(
                selected = daysMask == mask,
                onClick = { onMaskChange(mask) },
                label = { Text(label, style = MaterialTheme.typography.labelLarge) },
                colors = selectedChipColors(),
            )
        }
    }
}

/**
 * Selection is the plum accent. M3's default picks `secondaryContainer`, which in this palette is
 * the sage green — selection state is not a success, and the Gallery's filter row already reads
 * plum, so the two chip rows would otherwise disagree.
 */
@Composable
private fun selectedChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
)

private val PRESETS = listOf(
    "Every day" to EVERY_DAY_MASK,
    "Weekdays" to WEEKDAYS_MASK,
    "Weekends" to WEEKENDS_MASK,
)
