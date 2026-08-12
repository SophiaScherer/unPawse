package com.example.unpawse.ui.stats

import com.example.unpawse.data.usage.DailyUsage
import com.example.unpawse.data.usage.MonitoredApp
import com.example.unpawse.data.usage.UNLIMITED_MINUTES
import com.example.unpawse.data.usage.effectiveLimitMinutes
import java.time.LocalDate

/**
 * The badges unPawse can award, and the rules that decide when.
 *
 * **Derived, never stored.** Each rule answers "what is the earliest date this was first true?" by
 * re-reading the history, so there is no achievements table, nothing to migrate, and no way for a
 * stored flag to drift from the data behind it. Delete a run of photos and the streak badge
 * correctly goes away again. The date is what lets "Recent Achievements" sort by genuine recency
 * rather than by catalogue order.
 *
 * Everything here is pure and parameterised on `today` — no rule may call `LocalDate.now()`.
 *
 * The mockup's own three badges ("Deep Focus / 4h Without App Swap", "Night Owl / No Phone After
 * 10PM", "Weekend Warrior") are **not** implemented: `daily_usage` holds per-app daily totals with
 * no session or app-swap history, and `FocusSession` keeps only a live end time, so none of them is
 * derivable from anything the app stores. Inventing them was the alternative, which is the thing
 * this screen exists not to do. These five are backed by data that actually exists.
 */
enum class AchievementId { FIRST_CAT, CAT_COLLECTOR, WEEK_STREAK, CLEAN_DAY, BLOCKS_RESPECTED }

/** How many captures earn [AchievementId.CAT_COLLECTOR]. */
const val CAT_COLLECTOR_TARGET = 10

/** How many consecutive capture days earn [AchievementId.WEEK_STREAK]. */
const val WEEK_STREAK_TARGET = 7

/** How many blocks respected earn [AchievementId.BLOCKS_RESPECTED]. */
const val BLOCKS_RESPECTED_TARGET = 25

/** A badge's fixed presentation. The *earned* half is [evaluateAchievements]' business. */
data class AchievementRule(
    val id: AchievementId,
    val title: String,
    val subtitle: String,
    val color: AchievementColor,
    val icon: AchievementIcon,
)

val ACHIEVEMENT_CATALOGUE: List<AchievementRule> = listOf(
    AchievementRule(
        AchievementId.FIRST_CAT,
        title = "First Cat",
        subtitle = "Your first verified cat",
        color = AchievementColor.CORAL,
        icon = AchievementIcon.TROPHY,
    ),
    AchievementRule(
        AchievementId.WEEK_STREAK,
        title = "7-Day Streak",
        subtitle = "A cat every day for a week",
        color = AchievementColor.SAGE,
        icon = AchievementIcon.STREAK,
    ),
    AchievementRule(
        AchievementId.CAT_COLLECTOR,
        title = "Cat Collector",
        subtitle = "$CAT_COLLECTOR_TARGET verified cats",
        color = AchievementColor.CORAL,
        icon = AchievementIcon.CATS,
    ),
    AchievementRule(
        AchievementId.CLEAN_DAY,
        title = "Under Budget",
        subtitle = "A whole day inside every limit",
        color = AchievementColor.SAGE,
        icon = AchievementIcon.BUDGET,
    ),
    AchievementRule(
        AchievementId.BLOCKS_RESPECTED,
        title = "Well Blocked",
        subtitle = "$BLOCKS_RESPECTED_TARGET limits respected",
        color = AchievementColor.CORAL,
        icon = AchievementIcon.SHIELD,
    ),
)

/**
 * Everything the rules read. [usage] must be the **whole** history, not the Stats chart window — an
 * unlock date derived from a sliding window would silently un-unlock as the window moved past it.
 */
data class AchievementInput(
    /** One entry per capture (not a set), so counting rules and date rules can share it. */
    val captureDates: List<LocalDate>,
    val usage: List<DailyUsage>,
    val monitoredApps: List<MonitoredApp>,
    val today: LocalDate,
)

/** The earliest date each badge was first earned, or `null` if it hasn't been. */
fun evaluateAchievements(input: AchievementInput): Map<AchievementId, LocalDate?> = mapOf(
    AchievementId.FIRST_CAT to nthCaptureDate(input.captureDates, n = 1),
    AchievementId.CAT_COLLECTOR to nthCaptureDate(input.captureDates, n = CAT_COLLECTOR_TARGET),
    AchievementId.WEEK_STREAK to firstStreakDate(input.captureDates, WEEK_STREAK_TARGET),
    AchievementId.CLEAN_DAY to firstCleanDay(input.usage, input.monitoredApps, input.today),
    AchievementId.BLOCKS_RESPECTED to blocksRespectedDate(input.usage, BLOCKS_RESPECTED_TARGET),
)

/**
 * The catalogue as UI rows: earned badges first, most recently earned at the front, then the locked
 * ones in catalogue order.
 *
 * Locked badges are **rendered, not hidden**. A fresh install showing five greyed "Coming Soon"
 * cards is content — it tells the user what there is to earn — whereas an empty section under a
 * heading reads as content that failed to load.
 */
fun toAchievements(unlockedOn: Map<AchievementId, LocalDate?>): List<Achievement> {
    val (earned, locked) = ACHIEVEMENT_CATALOGUE.partition { unlockedOn[it.id] != null }

    val earnedRows = earned
        .sortedByDescending { unlockedOn.getValue(it.id) }
        .map { Achievement(it.title, it.subtitle, it.color, it.icon, unlocked = true) }

    val lockedRows = locked.map {
        Achievement(it.title, LOCKED_SUBTITLE, it.color, AchievementIcon.LOCKED, unlocked = false)
    }

    return earnedRows + lockedRows
}

/** The mockup's copy for a badge that hasn't been earned. */
const val LOCKED_SUBTITLE = "Coming Soon"

/** The date of the [n]th capture ever, or null if there haven't been that many. */
private fun nthCaptureDate(captureDates: List<LocalDate>, n: Int): LocalDate? =
    captureDates.sorted().getOrNull(n - 1)

/**
 * The date a run of [target] consecutive capture days first *completed* — the target-th day of the
 * earliest qualifying run, not the last day of a longer one.
 */
private fun firstStreakDate(captureDates: List<LocalDate>, target: Int): LocalDate? {
    val days = captureDates.distinct().sorted()
    if (days.size < target) return null

    var run = 1
    for (i in 1 until days.size) {
        run = if (days[i] == days[i - 1].plusDays(1)) run + 1 else 1
        if (run >= target) return days[i]
    }
    return if (target == 1) days.first() else null
}

/**
 * The earliest day the user stayed inside every enabled app's limit.
 *
 * Two guards carry the meaning. **Today is excluded** because it is still in progress — a badge
 * awarded at 9am and revoked by lunchtime is worse than one awarded a day late. And the day must
 * have **some** usage: without that, every day the phone sat in a drawer would qualify and the badge
 * would mean nothing. Uncapped days can't be exceeded, so they don't block the award, but they can't
 * satisfy the "some usage" guard on their own either.
 */
private fun firstCleanDay(
    usage: List<DailyUsage>,
    monitoredApps: List<MonitoredApp>,
    today: LocalDate,
): LocalDate? {
    val enabled = monitoredApps.filter { it.enabled }
    if (enabled.isEmpty()) return null

    val byDate = usage.groupBy { it.date }

    return byDate.keys
        .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
        .filter { it < today }
        .sorted()
        .firstOrNull { date ->
            val rows = byDate[date.toString()].orEmpty().associateBy { it.packageName }
            var sawUsage = false

            val withinEveryLimit = enabled.all { app ->
                val used = rows[app.packageName]?.usedSeconds ?: 0L
                if (used > 0L) sawUsage = true

                val limit = effectiveLimitMinutes(
                    app.dailyLimitMinutes,
                    app.weekendLimitMinutes,
                    date.dayOfWeek,
                )
                // An uncapped day cannot be exceeded.
                if (limit == UNLIMITED_MINUTES) return@all true

                val earned = rows[app.packageName]?.earnedSeconds ?: 0L
                used <= limit.toLong() * SECONDS_PER_MINUTE + earned
            }

            withinEveryLimit && sawUsage
        }
}

private const val SECONDS_PER_MINUTE = 60L

/**
 * The date the running total of blocks first reached [target].
 *
 * Can never predate the release that started recording blocks, which is honest rather than a bug:
 * the app genuinely doesn't know how many interruptions it prevented before it counted them.
 */
private fun blocksRespectedDate(usage: List<DailyUsage>, target: Int): LocalDate? {
    if (target <= 0) return null

    val perDay = usage
        .filter { it.blockedCount > 0 }
        .groupBy({ it.date }, { it.blockedCount })
        .mapValues { (_, counts) -> counts.sum() }

    var running = 0
    for (date in perDay.keys.sorted()) {
        running += perDay.getValue(date)
        if (running >= target) return runCatching { LocalDate.parse(date) }.getOrNull()
    }
    return null
}
