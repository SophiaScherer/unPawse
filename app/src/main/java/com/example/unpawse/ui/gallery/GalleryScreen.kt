package com.example.unpawse.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.unpawse.ui.components.AiBadge
import com.example.unpawse.ui.components.CatPhotoPlaceholder
import com.example.unpawse.ui.components.EarnedChip
import com.example.unpawse.ui.components.PawCard
import com.example.unpawse.ui.components.ScreenHeader
import com.example.unpawse.ui.theme.Dimens
import com.example.unpawse.ui.theme.UnPawseTheme
import java.io.File

@Composable
fun GalleryScreen(
    state: GalleryUiState,
    modifier: Modifier = Modifier,
    onFilterSelected: (GalleryFilter) -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
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
                SearchBar(state.searchQuery, state.searchPlaceholder, onSearchQueryChange)
                Spacer(Modifier.height(12.dp))
                FilterRow(state.selectedFilter, onFilterSelected)
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
private fun SearchBar(query: String, placeholder: String, onQueryChange: (String) -> Unit) {
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
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.weight(1f),
            decorationBox = { innerTextField ->
                if (query.isEmpty()) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                innerTextField()
            },
        )
        if (query.isNotEmpty()) {
            Spacer(Modifier.size(8.dp))
            Icon(
                Icons.Filled.Close,
                contentDescription = "Clear search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { onQueryChange("") }
                    .size(20.dp),
            )
        }
    }
}

@Composable
private fun FilterRow(selectedFilter: GalleryFilter, onFilterSelected: (GalleryFilter) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GalleryFilter.entries.forEach { filter ->
            FilterChip(
                label = filter.label,
                selected = filter == selectedFilter,
                onClick = { onFilterSelected(filter) },
            )
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val content = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .background(container)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(6.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = content,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
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
            val imageModifier = Modifier
                .fillMaxWidth()
                .aspectRatio(capture.aspectRatio)
            if (capture.imagePath != null) {
                AsyncImage(
                    model = File(capture.imagePath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = imageModifier,
                )
            } else {
                CatPhotoPlaceholder(seed = capture.id.hashCode(), modifier = imageModifier)
            }
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
