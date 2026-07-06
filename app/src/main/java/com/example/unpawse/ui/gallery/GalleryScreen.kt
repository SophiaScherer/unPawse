package com.example.unpawse.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.unpawse.ui.components.AiBadge
import com.example.unpawse.ui.components.CatPhotoPlaceholder
import com.example.unpawse.ui.components.EarnedChip
import com.example.unpawse.ui.components.PawCard
import com.example.unpawse.ui.components.ScreenHeader
import com.example.unpawse.ui.theme.Dimens
import com.example.unpawse.ui.theme.UnPawseTheme

@Composable
fun GalleryScreen(
    state: GalleryUiState,
    modifier: Modifier = Modifier,
    onFilterSelected: (GalleryFilter) -> Unit = {},
    onCaptureClick: (CaptureItem) -> Unit = {},
) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = Dimens.ScreenHMargin,
            end = Dimens.ScreenHMargin,
            top = 8.dp,
            bottom = 24.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(Dimens.Gutter),
        verticalItemSpacing = Dimens.StackGap,
    ) {
        // Header + search + filters span the full width so they don't get pulled into the masonry.
        item(span = StaggeredGridItemSpan.FullLine) {
            Column {
                ScreenHeader(title = "unPawse")
                Spacer(Modifier.height(12.dp))
                SearchBar(state.searchPlaceholder)
                Spacer(Modifier.height(12.dp))
                FilterRow(state.filters, onFilterSelected)
                Spacer(Modifier.height(8.dp))
            }
        }

        state.sections.forEach { section ->
            item(span = StaggeredGridItemSpan.FullLine) {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            items(section.items, key = { it.id }) { capture ->
                CaptureCard(capture, onClick = { onCaptureClick(capture) })
            }
        }
    }
}

@Composable
private fun SearchBar(placeholder: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(12.dp))
        // Decorative only — search wiring is a backend concern left for later.
        Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FilterRow(filters: List<GalleryFilter>, onFilterSelected: (GalleryFilter) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        filters.forEach { filter ->
            FilterChip(filter, onClick = { onFilterSelected(filter) }, modifier = Modifier.weight(1f, fill = false))
        }
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainerLowest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.GridView, contentDescription = "Grid view",
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun FilterChip(filter: GalleryFilter, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val container = if (filter.selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val content = if (filter.selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (filter.selected) {
            Icon(Icons.Filled.CalendarToday, contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
            Spacer(Modifier.size(6.dp))
        }
        Text(filter.label, style = MaterialTheme.typography.labelLarge, color = content)
    }
}

@Composable
private fun CaptureCard(capture: CaptureItem, onClick: () -> Unit) {
    val footerColor = if (capture.isBonus) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest
    PawCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        containerColor = footerColor,
        contentPadding = 0.dp,
    ) {
        Box {
            CatPhotoPlaceholder(
                seed = capture.id.hashCode(),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(capture.aspectRatio),
            )
            capture.aiConfidence?.let { confidence ->
                AiBadge(
                    confidenceText = "%.1f%% AI".format(confidence),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp),
                )
            }
        }
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(capture.timeLabel, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                EarnedChip(capture.earnedLabel)
            }
            Spacer(Modifier.height(4.dp))
            if (capture.isBonus) {
                Text(capture.caption, style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Icon(Icons.Filled.Timer, contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.size(4.dp))
                    Text(capture.earnedLabel, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary)
                }
            } else {
                Text(capture.caption, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFF8F8, heightDp = 1400)
@Composable
private fun GalleryScreenPreview() {
    UnPawseTheme {
        GalleryScreen(state = GalleryUiState.sample())
    }
}
