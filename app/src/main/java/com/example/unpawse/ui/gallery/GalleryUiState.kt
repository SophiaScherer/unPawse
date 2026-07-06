package com.example.unpawse.ui.gallery

/** Immutable UI state for the Gallery screen. [sample] supplies mockup data for previews. */
data class GalleryUiState(
    val searchPlaceholder: String,
    val filters: List<GalleryFilter>,
    val sections: List<GallerySection>,
) {
    companion object {
        fun sample() = GalleryUiState(
            searchPlaceholder = "Search captures...",
            filters = listOf(
                GalleryFilter("This Week", selected = true),
                GalleryFilter("Month"),
                GalleryFilter("Favorites"),
            ),
            sections = listOf(
                GallerySection(
                    title = "Today",
                    items = listOf(
                        CaptureItem("1", "14:32 PM", 98.4f, "+45m", "Verification successful", 1.1f),
                        CaptureItem("2", "11:05 AM", 92.1f, "+30m", "Verification successful", 0.85f),
                        CaptureItem("3", "09:12 AM", null, "+2h Earned", "Daily streak bonus!", 0.8f, isBonus = true),
                        CaptureItem("4", "08:45 AM", 99.9f, "+15m", "Verification successful", 1.25f),
                    ),
                ),
                GallerySection(
                    title = "Yesterday",
                    items = listOf(
                        CaptureItem("5", "19:20 PM", null, "+45m", "Verification successful", 1.0f),
                        CaptureItem("6", "17:15 PM", null, "+30m", "Verification successful", 1.15f),
                    ),
                ),
            ),
        )
    }
}

data class GalleryFilter(val label: String, val selected: Boolean = false)

data class GallerySection(val title: String, val items: List<CaptureItem>)

/**
 * A single captured photo card. [aiConfidence] is null for bonus/streak captures (no AI badge);
 * [isBonus] switches the footer to the pink "streak bonus" treatment. [aspectRatio] varies the
 * placeholder height to produce the staggered masonry look.
 */
data class CaptureItem(
    val id: String,
    val timeLabel: String,
    val aiConfidence: Float?,
    val earnedLabel: String,
    val caption: String,
    val aspectRatio: Float,
    val isBonus: Boolean = false,
)
