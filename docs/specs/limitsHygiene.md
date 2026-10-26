# limitsHygiene

Audit items UX-24, UX-25, UX-26 and A11Y-05: limits and schedules stay removable, valid and reachable.

## What was built
- **Not on this phone** (UX-24). `missingApps` (pure, in `AppPickerMapper`) lists monitored rows whose package isn't in the installed list, whether the row is enabled or not. The App Picker shows them in a section at the top, each with a Remove button behind a `ConfirmDialog`. `removeMissingMessage` says what goes with the app ("its schedule" / "its N schedules"). Remove calls `UsageRepository.removeMonitoredApp` and `ScheduleRepository.deleteWindowsFor`; `daily_usage` history stays.
- **No-day schedules** (UX-25). `DayOfWeekChips` already refused to clear the last day, so the editor never produced an empty mask; the audit read the save path without that guard. `ScheduleDraft.canSave` backs it up in `SchedulesViewModel.save`. A stored no-day window, for example from an import, counts as neither active (`activeCount`) nor in the Settings summary.
- **Editor scroll** (UX-26). The `ScheduleEditorSheet` column scrolls and has `imePadding()`.
- **Touch targets** (A11Y-05). `ValueStepper` uses the default 48dp `IconButton` instead of a forced 32dp one. The Category, Weekends and Schedules rows get `heightIn(min = 48.dp)`.

## Key decisions
- **No automatic reconciliation.** The installed list is launcher-only (no `QUERY_ALL_PACKAGES`), so an app that merely lost its launcher entry would silently lose its limits. An explicit, confirmed Remove is reversible in the sense that matters: nothing goes without the user seeing it.
- **Scoped schedules go with the app**, because they can't block anything without it. Global windows are untouched.

## How to verify
- JVM: `AppPickerMapperTest` (the missing-app cases and remove copy), `SchedulesMapperTest` (no-day drafts and stored windows).
- Device:
  1. Seed `monitored_apps` with a package that isn't installed, plus a schedule scoped to it, using the `run-as` recipe in AGENTS.md.
  2. Open Home → Edit Limits. Expect "Not on this phone" with the app and Remove.
  3. Remove it. The dialog names the schedule. Afterwards the section is gone, Home's count and remaining time drop, and Schedules keeps only the global window.
  4. At `settings put system font_scale 2.0`, open Add schedule and swipe. Save scrolls into view. Restore the font scale to 1.0 afterwards.

## Known gaps
- Home, Settings and Stats still count a missing app until it's removed. They don't load the installed list.
- At 200% font the "Weekends" preset chip wraps one letter per line. That belongs to the `uiPolish` sweep.
