package com.example.unpawse.ui.gallery

/** Immutable UI state for the Gallery screen. [sample] supplies mockup data for previews. */
data class GalleryUiState(
    val searchQuery: String,
    val searchPlaceholder: String,
    val selectedFilter: GalleryFilter,
    val sections: List<GallerySection>,
) {
    companion object {
        const val SEARCH_PLACEHOLDER = "Search by date or time..."

        /** Empty real-data state: no captures yet. Used as the ViewModel's initial value. */
        fun empty() = GalleryUiState(
            searchQuery = "",
            searchPlaceholder = SEARCH_PLACEHOLDER,
            selectedFilter = GalleryFilter.ALL,
            sections = emptyList(),
        )

        fun sample() = GalleryUiState(
            searchQuery = "",
            searchPlaceholder = SEARCH_PLACEHOLDER,
            selectedFilter = GalleryFilter.ALL,
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

/**
 * The Gallery's filter chips. [THIS_WEEK] and [ALL] are age windows (last 7 / last 30 days);
 * [FAVORITES] shows starred captures of any age — the only way older-than-a-month favorites surface.
 * The window logic lives in [matchingFilter]; [label] is the chip text.
 */
enum class GalleryFilter(val label: String) {
    THIS_WEEK("This Week"),
    ALL("All"),
    FAVORITES("Favorites"),
}

data class GallerySection(val title: String, val items: List<CaptureItem>)

/**
 * A single captured photo card. [aiConfidence] is null for bonus/streak captures (no AI badge);
 * [isBonus] switches the footer to the pink "streak bonus" treatment. [aspectRatio] varies the
 * card height to produce the staggered masonry look. [imagePath] is the absolute path to a real
 * captured JPEG; when null (sample/preview data) the card falls back to [CatPhotoPlaceholder].
 */
data class CaptureItem(
    val id: String,
    val timeLabel: String,
    val aiConfidence: Float?,
    val earnedLabel: String,
    val caption: String,
    val aspectRatio: Float,
    val isBonus: Boolean = false,
    val imagePath: String? = null,
    val isFavorite: Boolean = false,
)
