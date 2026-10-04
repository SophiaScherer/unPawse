# Day rollover and Stats accuracy

## What was built

- **One source of "today"**: `DayClock` (`data/time/DayClock.kt`), owned by `AppContainer`. Stores and the tracker read `today()`/`nowMillis()`/`zone()`; screens collect `clockTicks`, which emits now, at each local midnight, at least once a minute, and at once on `TIME_SET`/`TIMEZONE_CHANGED`/`DATE_CHANGED` (`clockChanges`).
- **Date-keyed queries take the date**: `observeUsageForDate(date)`, `observeRecentUsage(days, endingOn)`, `observeRecentUnlocks(days, endingOn)`, `dailySecondsByDate(days, endingOn)`. Home, Stats and the Gallery `flatMapLatest` them on the clock and hand the queried date to the mapper (audit UX-12, CQ-06).
- **Clock-safe foreground polling**: `pollWindow(cursor, tick)` restarts the query window when the wall clock moves back and caps a forward gap at 24h (UX-28).
- **Focus restore yields**: `FocusSession.restore` never overwrites a start/stop made before the async DataStore read landed (CQ-14).
- **Stats over time**: the trend compares completed days only and names them ("VS LAST MON–SAT"; "NO FULL DAY YET" on Mondays); `UsageSeries.measuredSince` keeps days before measuring began off the chart and out of baselines; vs-yesterday claims only a rise; badges read the live 14-day window over the one-shot history; the all-apps read runs once per visit (PERF-06).
- **Stats visuals**: paired cards share a height (VIS-03); the line chart is zero-based with points centered over their labels; donut arcs no longer overlap their neighbors' round caps; the Prevented card mirrors the Trend card's header icon.

## Key decisions

- **The tick is capped at a minute** because coroutine delays use a monotonic clock that stops in deep sleep; one delay aimed at midnight lands late after a night in a drawer.
- **The date travels with its rows** (`HomeDay`, `StatsHistory.day`), so a screen can't render one day's rows under another day's labels. Mapper clock defaults were removed so a caller must pass the day.
- **The foreground stack survives a clock change.** Emptying it (the audit's suggestion) reads as "the user left the blocked app" and takes the overlay down, turning a clock change into an escape.
- **Completed days only for the trend**, because today against a whole day last week read as a drop every morning.
- **Only a rise gets an arrow** on vs-yesterday: today is still running, so being under is "so far", not an improvement.
- **Tracked scope is measured from the first `daily_usage` row ever**; all-apps from the first non-empty platform day. Unknown days render as gaps, never zero.
- `kotlinx-coroutines-test` added as a test-only dependency for the first ViewModel tests.

## How to verify

- `./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest --no-daemon -Pkotlin.compiler.execution.strategy=in-process`
- Key tests: `DayClockTest`, `HomeViewModelTest`, `StatsViewModelTest`, `PollWindowTest`, `FocusSessionTest`, `StatsMapperTest`, `UsageSeriesTest`.
- Device, rollover: seed today's usage (AGENTS.md `run-as` recipe), open Home or Stats, `adb shell settings put global auto_time 0`, `adb shell cmd alarm set-time <23:59:30 local, ms>`, and watch the figures move to the new day at midnight without leaving the screen. Restore with `settings put global auto_time 1`.
- Device, Stats: seed three weeks of `daily_usage` with gaps, blocks and scattered captures; check each card against hand sums (chart hours per day, trend = completed days this week minus the same days last week, Prevented = this week's `blockedCount`), in light and dark.
- Device, clock back: with a block overlay up, go Home and move the clock back ~40 minutes; the overlay should come down and the monitor keep tracking.
