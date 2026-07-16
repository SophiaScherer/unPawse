package com.example.unpawse.ui.stats

/** Immutable UI state for the Statistics screen. [sample] supplies mockup data for previews. */
data class StatsUiState(
    val dailyTotal: String,
    val deltaText: String,
    val deltaIsPositive: Boolean,
    val weeklyPoints: List<Float>,
    val weekdayLabels: List<String>,
    val highlightDayIndex: Int,
    val preventedCount: Int,
    val trendLabel: String,
    val trendBars: List<Float>,
    val productivePercent: Int,
    /**
     * Caption under [productivePercent] in the donut. State-driven because there's no app-category
     * data to back the mockup's literal "Productive" — the real number we can show is budget left.
     */
    val productiveLabel: String,
    val breakdown: List<UsageCategory>,
    val longestStreak: String,
    val unlocks: String,
    val capturedPhotos: String,
    val achievements: List<Achievement>,
) {
    companion object {
        fun sample() = StatsUiState(
            dailyTotal = "3h 24m",
            deltaText = "12% from yesterday",
            deltaIsPositive = false,
            weeklyPoints = listOf(2.1f, 2.6f, 2.9f, 3.4f, 3.8f, 2.2f, 3.4f),
            weekdayLabels = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"),
            highlightDayIndex = 4,
            preventedCount = 42,
            trendLabel = "-5.2h",
            trendBars = listOf(0.6f, 0.4f, 0.8f, 0.5f, 1f),
            productivePercent = 75,
            productiveLabel = "Budget left",
            breakdown = listOf(
                UsageCategory("Social Media", "1h 12m", UsageColor.SOCIAL),
                UsageCategory("Productivity", "45m", UsageColor.PRODUCTIVITY),
                UsageCategory("Entertainment", "32m", UsageColor.ENTERTAINMENT),
            ),
            longestStreak = "12 Days",
            unlocks = "24/day",
            capturedPhotos = "1,204 Photos",
            achievements = listOf(
                Achievement("Deep Focus", "4h Without App Swap", AchievementColor.CORAL),
                Achievement("Night Owl", "No Phone After 10PM", AchievementColor.SAGE),
            ),
        )
    }
}

data class UsageCategory(val label: String, val duration: String, val color: UsageColor)
enum class UsageColor { SOCIAL, PRODUCTIVITY, ENTERTAINMENT }

data class Achievement(val title: String, val subtitle: String, val color: AchievementColor)
enum class AchievementColor { CORAL, SAGE }
