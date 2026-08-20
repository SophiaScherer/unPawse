package com.example.unpawse.ui.stats

import com.example.unpawse.ui.format.DEFAULT_AVATAR_INITIAL

/** Immutable UI state for the Statistics screen. [sample] supplies mockup data for previews. */
data class StatsUiState(
    /** Header avatar letter; see [com.example.unpawse.ui.format.avatarInitialFor]. */
    val avatarInitial: Char = DEFAULT_AVATAR_INITIAL,
    val dailyTotal: String,
    val deltaText: String,
    val deltaIsPositive: Boolean,
    /**
     * Whether [deltaText] is a real comparison. False on a first day, when there is no yesterday to
     * measure against — the screen then draws no arrow at all, because a direction with no baseline
     * is a claim about nothing. ("No data for yesterday" rendered beside a red upward arrow, since
     * any usage at all is technically greater than zero.)
     */
    val deltaHasBaseline: Boolean,
    /**
     * Hours per day across the Mon–Sun week, `null` for a day still to come. A past day with no
     * usage is a real `0f`; one numeric slot cannot say both that and "this day hasn't happened",
     * which is the same reason [deltaHasBaseline] exists.
     */
    val weeklyPoints: List<Float?>,
    val weekdayLabels: List<String>,
    val highlightDayIndex: Int,
    val preventedCount: Int,
    val trendLabel: String,
    /** Whether week-over-week usage rose. Drives the arrow direction, which used to be hardcoded. */
    val trendIsUp: Boolean,
    /**
     * Whether [trendLabel] is a real comparison. False until there is a last week to measure
     * against — a fresh install summed zero for it and rendered "+0.5h" beside an upward arrow, a
     * week-over-week change against a week that never happened. Same shape as [deltaHasBaseline].
     */
    val trendHasBaseline: Boolean,
    /** The trend's period, stated on the card's face because week-to-date isn't guessable. */
    val trendCaption: String,
    /** One per day of the same week [trendLabel] compares; `null` for a day still to come. */
    val trendBars: List<Float?>,
    /**
     * The figure in the middle of the donut: the total of [breakdown], formatted.
     *
     * Carried alongside the slices rather than recomputed from the raw usage, because the centre and
     * the ring have to be the same measurement. This used to hold budget-left, which shares nothing
     * with the arcs around it — the chart read as one metric while showing two.
     */
    val breakdownTotal: String,
    val breakdown: List<UsageCategory>,
    /** Budget headroom, formatted, or "—" when no monitored app has a cap to report on. */
    val budgetLeftLabel: String,
    val longestStreak: String,
    val unlocks: String,
    val capturedPhotos: String,
    val achievements: List<Achievement>,
) {
    companion object {
        fun sample() = StatsUiState(
            avatarInitial = 'S',
            dailyTotal = "3h 24m",
            deltaText = "12% from yesterday",
            deltaIsPositive = false,
            deltaHasBaseline = true,
            weeklyPoints = listOf(2.1f, 2.6f, 2.9f, 3.4f, 3.8f, null, null),
            weekdayLabels = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"),
            highlightDayIndex = 4,
            preventedCount = 42,
            trendLabel = "-5.2h",
            trendIsUp = false,
            trendHasBaseline = true,
            trendCaption = "VS LAST WEEK, SAME DAYS",
            // highlightDayIndex is Friday, so the weekend has not happened yet.
            trendBars = listOf(0.6f, 0.4f, 0.8f, 0.5f, 1f, null, null),
            breakdownTotal = "2h 37m",
            breakdown = listOf(
                UsageCategory("Social", "1h 12m", 72 * 60L, UsageColor.SOCIAL),
                UsageCategory("Productivity", "45m", 45 * 60L, UsageColor.PRODUCTIVITY),
                UsageCategory("Entertainment", "32m", 32 * 60L, UsageColor.ENTERTAINMENT),
                UsageCategory("Other", "8m", 8 * 60L, UsageColor.OTHER),
            ),
            budgetLeftLabel = "75%",
            longestStreak = "12 Days",
            unlocks = "24/day",
            capturedPhotos = "1,204 Photos",
            // One of each so the preview covers both branches of the card.
            achievements = listOf(
                Achievement("First Cat", "Your first verified cat", AchievementColor.CORAL,
                    AchievementIcon.TROPHY, unlocked = true),
                Achievement("7-Day Streak", LOCKED_SUBTITLE, AchievementColor.SAGE,
                    AchievementIcon.LOCKED, unlocked = false),
            ),
        )
    }
}

/**
 * One slice of the usage breakdown. [duration] is the formatted text beside the legend dot;
 * [seconds] is the same quantity unformatted, and is what the donut sizes its arc from.
 *
 * The two are deliberately both here. The screen used to hold a `durationWeight()` table returning
 * the mockup's literal 72/45/32 keyed off the palette slot, so every arc was drawn at a constant
 * proportion regardless of the real duration printed next to it.
 */
data class UsageCategory(
    val label: String,
    val duration: String,
    val seconds: Long,
    val color: UsageColor,
)
/** One slot per [com.example.unpawse.data.usage.AppCategory]; the screen maps each to a theme colour. */
enum class UsageColor { SOCIAL, PRODUCTIVITY, ENTERTAINMENT, OTHER }

/**
 * One badge on the "Recent Achievements" rail. [unlocked] is false for a badge the user hasn't
 * earned yet, which is rendered greyed rather than omitted — see [toAchievements].
 */
data class Achievement(
    val title: String,
    val subtitle: String,
    val color: AchievementColor,
    val icon: AchievementIcon,
    val unlocked: Boolean = true,
)

enum class AchievementColor { CORAL, SAGE }

/**
 * Which glyph a badge shows. An enum rather than an `ImageVector` so UI state stays free of Compose
 * types; `StatsScreen` maps it. This replaces a `if (color == CORAL) MilitaryTech else Nightlight`
 * hack that only ever worked because there were exactly two hardcoded achievements.
 */
enum class AchievementIcon { TROPHY, CATS, STREAK, BUDGET, SHIELD, LOCKED }
