package com.example.unpawse.ui.apppicker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.unpawse.data.usage.UNLIMITED_MINUTES
import com.example.unpawse.ui.components.OptionPickerDialog
import com.example.unpawse.ui.components.ValueStepper
import com.example.unpawse.ui.format.formatMinutes

/**
 * The Saturday/Sunday budget for one app: a mode picker plus, when the mode is
 * [WeekendMode.CUSTOM], the same stepper the everyday limit uses.
 *
 * Three modes rather than a single stepper because "no weekend limit" is a real answer people want
 * ("30m on school nights, whatever you like on Saturday") and no number on a stepper can express
 * it — the enforcement sentinel is negative, which is not something to make a user dial down to.
 *
 * Stateless apart from the dialog toggle; the value round-trips through [onWeekendLimitChange],
 * where `null` means "follow the everyday budget".
 */
@Composable
fun WeekendLimitControl(
    item: AppLimitItem,
    onWeekendLimitChange: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showModePicker by remember { mutableStateOf(false) }

    if (showModePicker) {
        OptionPickerDialog(
            title = "Weekends",
            options = WeekendMode.entries,
            selected = item.weekendMode,
            onSelect = { mode ->
                onWeekendLimitChange(
                    when (mode) {
                        WeekendMode.SAME_AS_WEEKDAYS -> null
                        WeekendMode.UNLIMITED -> UNLIMITED_MINUTES
                        // Seed the custom budget from the everyday one, so the stepper opens
                        // somewhere sensible instead of at the band's floor.
                        WeekendMode.CUSTOM -> item.dailyLimitMinutes
                    },
                )
            },
            onDismiss = { showModePicker = false },
            label = { it.label },
            supporting = { mode ->
                when (mode) {
                    WeekendMode.SAME_AS_WEEKDAYS -> formatMinutes(item.dailyLimitMinutes) + " every day"
                    WeekendMode.UNLIMITED -> "Saturday and Sunday are not limited"
                    WeekendMode.CUSTOM -> "Set a separate Saturday and Sunday budget"
                }
            },
        )
    }

    Column(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable { showModePicker = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Weekends",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = item.weekendMode.label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        if (item.weekendMode == WeekendMode.CUSTOM) {
            Spacer(Modifier.size(4.dp))
            ValueStepper(
                label = "Weekend limit",
                value = item.weekendStepperMinutes,
                onChange = onWeekendLimitChange,
                step = LIMIT_STEP_MINUTES,
                min = MIN_LIMIT_MINUTES,
                max = MAX_LIMIT_MINUTES,
                format = ::formatMinutes,
            )
        }
    }
}
