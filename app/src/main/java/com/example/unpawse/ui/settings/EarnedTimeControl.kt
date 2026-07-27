package com.example.unpawse.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.unpawse.data.settings.SettingsRepository
import com.example.unpawse.ui.components.SettingsRow
import com.example.unpawse.ui.components.ValueStepper
import com.example.unpawse.ui.format.formatMinutes

/**
 * The reward grant: how much time one verified cat buys back.
 *
 * This row used to read "Break duration — 15 minutes every hour", describing a periodic-break
 * feature that does not exist anywhere in the app. It now controls the one number the reward loop
 * actually spends, which was hardwired as `BONUS_MINUTES_PER_CAT`.
 *
 * Laid out as a [SettingsRow] with the stepper beneath rather than in the trailing slot, matching
 * how the App Picker presents its per-app limit stepper.
 */
@Composable
fun EarnedTimeControl(
    minutesPerCat: Int,
    onMinutesChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        SettingsRow(
            title = "Time earned per cat",
            subtitle = earnedTimeSummary(minutesPerCat),
            leadingIcon = Icons.Filled.LocalCafe,
            iconTint = MaterialTheme.colorScheme.tertiary,
            iconBackground = MaterialTheme.colorScheme.tertiaryContainer,
        )
        Spacer(Modifier.height(4.dp))
        ValueStepper(
            label = "Earned time",
            value = minutesPerCat,
            onChange = onMinutesChange,
            step = SettingsRepository.EARNED_MINUTES_STEP,
            min = SettingsRepository.MIN_EARNED_MINUTES_PER_CAT,
            max = SettingsRepository.MAX_EARNED_MINUTES_PER_CAT,
            format = ::formatMinutes,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}
