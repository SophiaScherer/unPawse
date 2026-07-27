package com.example.unpawse.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.unpawse.ui.components.BackHeader
import com.example.unpawse.ui.components.PawCard
import com.example.unpawse.ui.theme.Dimens
import com.example.unpawse.ui.theme.UnPawseTheme

/**
 * The privacy policy, in-app rather than behind a link. The Settings row used to show an
 * "open in new" icon and go nowhere; there is no hosted policy to point at, and an on-device policy
 * is also readable by an app that has no internet permission to fetch one with.
 *
 * Pure layout — the words live in [privacyPolicySections].
 */
@Composable
fun PrivacyPolicyScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    sections: List<PolicySection> = privacyPolicySections,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = Dimens.ScreenHMargin,
            end = Dimens.ScreenHMargin,
            top = 8.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(Dimens.StackGap),
    ) {
        item {
            BackHeader(title = "Privacy Policy", subtitle = PRIVACY_POLICY_UPDATED, onBack = onBack)
        }

        items(sections.size) { index ->
            PolicyCard(sections[index])
        }
    }
}

@Composable
private fun PolicyCard(section: PolicySection) {
    PawCard {
        Text(
            text = section.heading,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = section.body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(name = "Privacy Policy", showBackground = true, backgroundColor = 0xFFFFF8F8, heightDp = 1600)
@Composable
private fun PrivacyPolicyScreenPreview() {
    UnPawseTheme { PrivacyPolicyScreen() }
}

@Preview(name = "Privacy Policy · dark", showBackground = true, backgroundColor = 0xFF171213, heightDp = 1600)
@Composable
private fun PrivacyPolicyScreenDarkPreview() {
    UnPawseTheme(darkTheme = true) { PrivacyPolicyScreen() }
}
