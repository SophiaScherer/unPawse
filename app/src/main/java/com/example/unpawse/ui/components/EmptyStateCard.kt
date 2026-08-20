package com.example.unpawse.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.unpawse.ui.theme.UnPawseTheme

/**
 * The card an empty list gets instead of blank ground: what the feature is, in the user's terms.
 *
 * Lifted out of SchedulesScreen so Gallery renders the same shape rather than a second copy of it.
 * Copy is the caller's — this only fixes how an absence is presented.
 */
@Composable
fun EmptyStateCard(title: String, body: String, modifier: Modifier = Modifier) {
    PawCard(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFF8F8)
@Composable
private fun EmptyStateCardPreview() {
    UnPawseTheme {
        EmptyStateCard(
            title = "No photos yet",
            body = "Every cat you photograph lands here, grouped by the day you took it.",
        )
    }
}
