# AGENTS.md

> **This is a living document.** unPawse is an early-stage Android app. This file describes the current state of the code and should be updated continuously as the app evolves. Whenever you make a structural change or an important decision, update this file to keep it accurate.

## Overview

**unPawse** is a cat-themed **screen-time manager**: when an app's daily time limit is reached, the user must photograph their cat (verified on-device) to earn more time.

**The core loop works end-to-end**: pick apps and limits → a foreground service tracks real usage → hitting a limit draws a block over the offending app → photographing a verified cat credits bonus minutes and lets you back in → Home/Stats/Gallery render that real data. Every screen is backed by a real ViewModel + repository; `SampleData` survives only for the Block Overlay's in-app debug route. Screens stay stateless `XxxScreen(state, callbacks)`, so state can be re-sourced without touching them. What's left is polish and the gaps listed under **Current State**.

The design system ("Warm Minimalist") centers on a blush-pink (`#F5B6C8`) / plum (`#815060`) Material 3 palette over a warm-white surface, Inter-style typography, 24dp rounded cards, pill buttons, and a "squishy" 98%-press-scale feel. Source tokens live in the mockup's `DESIGN.md`.

## Tech Stack

- **Language:** Kotlin
- **UI toolkit:** **Jetpack Compose + Material 3** (`androidx.compose.material3`). The old Views/XML scaffold has been removed.
- **Build system:** Gradle (Kotlin DSL), version catalog at `gradle/libs.versions.toml`.
- **Android Gradle Plugin:** 9.2.1 — **applies Kotlin itself** (built-in). There is intentionally **no** `org.jetbrains.kotlin.android` plugin.
- **Compose compiler:** `org.jetbrains.kotlin.plugin.compose` at **2.2.20**, which must exactly match AGP 9.2.1's built-in Kotlin version. If it ever mismatches, the build fails fast and states the required version — update `kotlin` in the catalog. Fallback: apply explicit KGP to opt out of built-in Kotlin.
- **KSP:** `com.google.devtools.ksp` at `2.2.20-2.0.2` (Room's annotation processor) — **must track the `kotlin` version**.
- **compileSdk / targetSdk:** 36 · **minSdk:** 26 · **Java compatibility:** 11

### Key dependencies (all via the version catalog)
- Compose **BOM** `2025.12.01` → `ui`, `ui-graphics`, `ui-tooling(-preview)`, `material3`
- `material-icons-extended` **pinned 1.7.8** (it left the Compose BOM)
- `activity-compose`, `navigation-compose`, `lifecycle-{viewmodel,runtime}-compose`
- `androidx.core:core-ktx` **pinned 1.17.0** (1.18+ require compileSdk 37; we compile against 36)
- **Room** `2.7.1` (`runtime` + `ktx`, compiler via `ksp`) — capture metadata store
- **CameraX** `1.4.2` (`core`, `camera2`, `lifecycle`, `view`) — capture pipeline
- **ML Kit** `image-labeling 17.0.9` — on-device "Cat" label + confidence; bundled model, no network
- **Coil 3** `3.1.0` — `AsyncImage` for local capture files
- **DataStore** `datastore-preferences 1.1.7` — persists scalar settings toggles
- Testing: JUnit4, AndroidX Test (`ext.junit`), Espresso, Compose UI test

> Removed from the original scaffold: appcompat, Material Components (Views), constraintlayout, activity-ktx.

## Project Structure

```
app/src/main/java/com/example/unpawse/
├── MainActivity.kt            # ComponentActivity → enableEdgeToEdge() → setContent { UnPawseApp() }
├── UnPawseApp.kt              # Root: theme + persisted dark-mode + Scaffold(bottom bar) + NavHost
├── UnPawseApplication.kt      # Application: builds & holds the AppContainer; Context.appContainer()
├── data/
│   ├── AppContainer.kt        # App-scoped manual-DI graph (interface + DefaultAppContainer)
│   ├── SampleData.kt          # Only the Block Overlay's in-app debug entry now
│   ├── apps/                  # InstalledApp + InstalledAppsProvider (PackageManager, behind an iface)
│   ├── capture/               # Capture, CaptureEntity(+toDomain), CaptureDao, CaptureDatabase,
│   │                          #   CaptureRepository, PhotoStorage
│   ├── settings/              # SettingsRepository (Preferences DataStore)
│   └── usage/                 # MonitoredApp(+Entity), DailyUsage(+Entity), UsageDao,
│                              #   UsageRepository, UsageMath (pure limit arithmetic)
├── ml/                        # CatDetector (ML Kit) + DetectionResult; classify()/sensitivity map
├── service/                   # ForegroundAppMonitor(+UsageStats impl), UsageTracker,
│                              #   UsageMonitorService(+Controller), BlockOverlayHost(+Controller),
│                              #   BlockSession (the block↔camera link),
│                              #   UsageAccess / OverlayPermission (special-permission helpers)
└── ui/
    ├── theme/                 # Color, Theme (light + provisional dark), Type, Shape, Dimens
    ├── navigation/            # Routes, TopLevelDestination, UnPawseNavHost, UnPawseBottomBar
    ├── components/            # Stateless reusable UI (cards, charts, chips, timeline, settings rows…)
    ├── format/                # formatMinutes/formatSeconds — shared duration copy ("2h 15m")
    ├── block/                 # BlockOverlayScreen + BlockUiState(.forApp) — no VM; state is pushed in
    ├── home/                  # HomeScreen/Route/ViewModel/UiState + HomeMapper (+ streak helpers)
    ├── stats/                 # StatsScreen/Route/ViewModel/UiState + StatsMapper
    ├── settings/              # SettingsScreen + SettingsUiState + SettingsViewModel + SettingsSummary
    ├── apppicker/             # AppPickerScreen/Route/ViewModel/UiState + AppPickerMapper + AppIcon
    ├── camera/                # CameraScreen/Route/ViewModel + CameraX preview/capture/permission
    └── gallery/               # GalleryScreen/Route/ViewModel/UiState + GalleryMapper
```

- **Application ID / namespace:** `com.example.unpawse` _(placeholder — likely to change before release)._
- **Entry point:** `MainActivity` → `UnPawseApp()`; process entry `UnPawseApplication`.

## Architecture & Conventions

- **Dependency injection: manual, app-scoped.** `UnPawseApplication` builds a single `AppContainer` (`data/AppContainer.kt`) that owns the Room `CaptureDatabase` singleton, the shared `CaptureRepository`/`PhotoStorage`, the `SettingsRepository`, and derived app-wide state (e.g. `catDetectorMinConfidence`). ViewModel `factory(context)` methods read dependencies from it via `context.appContainer()` — they no longer rebuild the graph each time. `AppContainer` is an interface (`DefaultAppContainer` is the production impl) so tests can supply fakes. This is deliberately **not** Hilt/Koin — it keeps the "no extra compiler plugins" stance; the container is the seam a framework would later replace.
- **Data layer / repositories.** Each store follows the `CaptureRepository` pattern: one class exposes `Flow`s + `suspend` writers so callers never touch Room/DataStore directly. Room stack lives per-feature package: `@Entity` + a separate domain model + an `internal fun …toDomain()` mapper + `@Dao` returning `Flow`. There is **one database** — `CaptureDatabase` (historic name; now app-wide, holding `captures` + `monitored_apps` + `daily_usage`), reached via `getInstance()` and owned by the container. Pre-release schema changes bump `version` and rely on `fallbackToDestructiveMigration`; add real `Migration`s + `exportSchema = true` before shipping.
- **Usage tracking.** `UsageRepository` (`data/usage/`) is the source of truth for *what* is monitored (`monitored_apps`: package, label, `dailyLimitMinutes`, `enabled`) and *how much* has been spent (`daily_usage`, composite key `(packageName, date)` — a new day is a new row, so **daily reset is free**). Usage is stored in **seconds** for precision (the foreground monitor accrues sub-minute ticks) and exposed in minutes. Accrual goes through `@Transaction` DAO helpers that insert-if-absent then `+=`, so it's atomic. Limit arithmetic lives in pure, unit-tested functions (`UsageMath.kt`: `remainingSeconds` / `remainingMinutes` / `isLimitReached`). The repository takes an injectable `today: () -> LocalDate` so rollover is testable and midnight-safe (each call re-reads it).
- **UI state:** every screen is a stateless `XxxScreen(state: XxxUiState, …callbacks)` with an `XxxUiState.sample()` kept **for `@Preview` only**. Each live screen adds a stateful `XxxRoute` that owns a `ViewModel` (built by `XxxViewModel.factory(context)`) and collects `uiState` with `collectAsStateWithLifecycle()`. Every screen now follows this except the Block Overlay, whose state is *pushed in* (the service constructs `BlockUiState.forApp(label)`), so it has no ViewModel. Keep non-trivial mapping in pure, testable top-level functions (`toHomeUiState`, `toStatsUiState`, `toGallerySections`, `toAppLimitItems`, `classify`) — the ViewModel should only wire flows together.
  - ViewModels that mostly reshape one screen's data use `XxxUiState.sample().copy(...)` as a base so the not-yet-real fields keep their mockup copy in one place. **This is only OK for *copy*, never for metrics** — see the Stats note below.
- **Don't ship invented numbers.** `StatsMapper` deliberately blanks `preventedCount` (0), `unlocks` ("—") and `achievements` (empty) rather than inheriting `sample()`'s figures: a fabricated "42 interruptions prevented" sitting beside real ones reads as fact. A test pins this. Restore each only when the feature behind it exists (block-events table / unlock tracking / a rules engine). Placeholder *copy* (greeting, banner, next-break countdown) is fine and is flagged in the mappers.
- **Settings persistence.** `SettingsRepository` (Preferences DataStore) persists dark-mode override, cat-detection sensitivity, require-live-photo, and daily-summary. `SettingsViewModel` exposes them as `SettingsUiState` (still-static labels come from `SettingsUiState.sample()` until later phases make them dynamic). **Dark mode is special:** it lives in `UnPawseApp` (resolves a `null` override against `isSystemInDarkTheme()`) so it can drive the whole theme, and persists through the same repository. Sensitivity feeds `CatDetector` via `AppContainer.catDetectorMinConfidence` (a `StateFlow` mapped by `sensitivityToMinConfidence`), so a settings change takes effect without recreating the camera pipeline.
- **Camera / detection.** `CameraViewModel` runs the pipeline *take photo → classify → save-if-cat*; the CameraX controller stays lifecycle-bound in the composable (`CameraRoute` passes a suspend capture lambda in). `CameraEvent` (`Saved` / `NotACat` / `Error`) is a one-shot `Channel`; `CameraRoute` currently **drains it without acting** (hint text already reflects the outcome) — this is the seam for the reward loop (credit minutes + dismiss block on `Saved`). `CatDetector` wraps all ML behind one class; `classify` is a pure gate. Captures persist as app-internal JPEGs via `PhotoStorage` (no media permission).
- **Foreground detection & enforcement (`service/`).** `ForegroundAppMonitor` is the interface; `UsageStatsForegroundAppMonitor` polls `UsageStatsManager.queryEvents` every ~1s, querying only events *since the last tick* and remembering the last resumed package (so sitting still in one app keeps reporting it). It emits `null` when the screen is off — without that, the last app would accrue time all night. `UsageTracker` credits the interval *between* ticks to whichever app was in front, gated on `isMonitoredAndEnabled` so unmonitored apps never get a `daily_usage` row, and clamps each tick (`accrualMillis`, max 5s) so a doze/suspend gap can't silently burn a whole budget. It emits `limitReached` **once per breach** (not per tick), re-arming when the user switches away or earns time back. `UsageMonitorService` is a `specialUse` foreground service (required: tracking must survive backgrounding — exactly when the watched app is in front) started via `UsageMonitorController.startIfPermitted`, which refuses without usage access. `UsageAccess` wraps the `PACKAGE_USAGE_STATS` app-op: **no runtime dialog exists**, so the Settings row deep-links to system Settings and the state is re-read on resume (`LifecycleResumeEffect`), which also auto-starts the service.
- **The block overlay (`BlockOverlayHost`/`BlockOverlayController`).** When `limitReached` fires, the service draws `BlockOverlayScreen` in a `TYPE_APPLICATION_OVERLAY` window over the offending app (needs `SYSTEM_ALERT_WINDOW`, which also grants the background activity launch used by "Open Camera"). The window is intentionally **touchable and focusable** — it must swallow input to the app underneath. Compose normally inherits its lifecycle/saved-state/ViewModel owners from an Activity; there is none here, so `BlockOverlayHost` *is* those owners. Hosts are **single-use** (a `LifecycleRegistry` can't return from `DESTROYED`), so the controller builds a fresh one per show. All of it is **main-thread only** (`WindowManager.addView`), hence the `withContext(Dispatchers.Main)` in the service.
  - **Gotcha:** anything rendered in this window runs *outside an Activity*. `UnPawseTheme` therefore casts with `view.context as? Activity` — an unconditional cast crashed the app with `ClassCastException: UnPawseApplication cannot be cast to Activity` the first time the block fired. Keep Activity assumptions out of composables that the overlay can reach.
  - Both overlay buttons must dismiss first: the overlay sits above *every* app including our own, so leaving it up would cover the camera. "Exit App" goes home rather than back into the blocked app; returning re-triggers the block anyway, so the limit holds.
  - An overlay window **outlives app switches by design**, so the service also hides it whenever the foreground moves off the blocked app (`dismissBlockWhenUserLeaves`). Without that, a home gesture strands the block on top of the launcher. The tracker republishes the foreground app as a `StateFlow` for this — don't collect `ForegroundAppMonitor.foregroundApp()` again, it's a cold flow and would start a second poller.
- **The reward loop.** `BlockSession` is the thread between the block and the camera: the service arms it with the blocked package when raising the overlay, and `CameraViewModel.creditBlockedApp` settles it when ML Kit confirms a cat — crediting `BONUS_MINUTES_PER_CAT` (15) via `addEarnedMinutes`, then clearing the session so a second photo can't be spent on the same debt. **No explicit "unblock" step exists**: crediting lifts the budget above what's been used, so `isLimitReached` goes false and the tracker simply stops signalling. The session deliberately outlives the overlay (the debt survives "Open Camera" taking the block down) but is cleared by "Exit App", so a cat photographed later on a whim can't retroactively pay off an abandoned block. `CameraEvent.Saved` carries the `EarnedTime` so the hint can say "+15 min of Chrome"; crediting is ViewModel work, while `CameraRoute` only takes down the overlay (a window this side owns).
  - Reward captures keep **`isBonus = false`** — that flag means a *streak* bonus in the Gallery (blush card, "Daily streak bonus!", no AI badge). An unblock capture is an ordinary verified cat.
- **App picker + package visibility.** `InstalledAppsProvider` (`data/apps/`) fences off `PackageManager` so ViewModels stay testable. It queries **only LAUNCHER-resolvable apps**, which is the deliberate, restrained answer to Android 11+ package visibility: it needs the `<queries>` intent element in the manifest but **not** `QUERY_ALL_PACKAGES` (which triggers Play Store sensitive-permission review). Consequence: non-launchable apps never appear in the picker — fine, since you can't spend screen time in an app you can't open. App **icons are loaded lazily per visible row** (`ui/apppicker/AppIcon.kt`, `produceState` + IO) rather than eagerly for every app, keeping `Drawable`s out of UI state and off the main thread.
- **Navigation:** single-activity, `navigation-compose` with **plain string routes** (`Routes`), deliberately **not** kotlinx-serialization type-safe routes (avoids a second compiler-plugin/version-matching dependency). Tabs use `popUpTo(start){saveState}; launchSingleTop; restoreState`. Settings rows emit ids (`SettingsRowIds`) that the NavHost maps to destinations — only `APP_LIMITS` → `Routes.APP_PICKER` is wired so far; the rest are inert. The **Block Overlay** is a normal destination (no bottom bar) temporarily reachable from Home's "Pause Protection" card — this stays as a debug/preview entry; the real limit-reached trigger is being built (see Open Decisions + roadmap).
- **Theme:** `UnPawseTheme(darkTheme, content)` maps all `DESIGN.md` tokens to a `lightColorScheme`; no dynamic color (blush-pink brand identity is intentional). The **dark scheme is provisional** (derived from fixed/inverse tokens) — refine against a real dark mockup.
- **Typography:** the type scale matches `DESIGN.md`, but the family is currently the **platform sans-serif**, not Inter. To switch to Inter: drop the four OFL TTFs into `res/font/` and replace `AppFontFamily` in `ui/theme/Type.kt`.
- **Icons:** `material-icons-extended` — the ~40 mockup symbols map nearly 1:1; R8 strips the unused ones. Notable substitutes: `Shield` (Pause Protection), `GridView` (Gallery), `LocalCafe`, `PhoneAndroid`.
- **Insets:** `enableEdgeToEdge()`; `Scaffold` inner padding is applied as lazy-list `contentPadding` so content scrolls under the bars; Camera consumes only the status-bar inset; Block uses `safeDrawingPadding()`.
- Add new dependencies via the version catalog and reference them as `libs.*`. Package everything under `com.example.unpawse`.

## Current State

- All six screens (Home, Stats, Gallery, Settings, Camera, Block Overlay) plus the App Picker are implemented and reachable; bottom-nav tabs work; the dark-mode toggle flips the theme live **and persists**.
- **Camera** captures real photos, verifies them on-device (ML Kit), and stores verified cats; **Gallery** streams them from Room via Coil. **Settings** toggles/sensitivity persist through DataStore.
- Custom Canvas visuals: circular progress ring, smooth line chart, donut chart, mini bar chart, activity timeline.
- **Blocking works end-to-end.** With usage access + overlay permission granted, the service tracks the real foreground app, accrues seconds into `daily_usage` for monitored apps only, and **draws the block over the offending app when its limit is hit** — naming it ("You've reached today's limit for Chrome"). Verified on an emulator by seeding usage to 1s below the limit and opening Chrome: the block appeared, took focus away from Chrome, "Exit App" returned home, reopening Chrome **re-blocked**, and "Open Camera" dismissed the overlay and deep-linked to the viewfinder.
- **The core loop is closed.** Limit reached → block over the app → photograph a cat → +15 min credited → back in. Verified end-to-end on an emulator: with Chrome seeded to 1s below its limit, opening it blocked; "Open Camera" armed the session and opened the viewfinder; ML Kit verified the (virtual) cat at ~0.83 confidence; the hint read "Purrfect! +15 min of Chrome."; Chrome then stayed usable, and the DB showed `used=937s earned=900s limit=900s → remaining=863s, blocked=false` with the capture stored `isBonus=0`.
- **Usage data layer** (`data/usage/`): per-app daily limits + today's used/earned time, with atomic accrual and free daily reset. The app picker writes limits; the monitor writes usage.
- **App picker works end-to-end** (Settings → "Individual app limits"): real installed apps with launcher icons, search, per-app switch + 15-minute-step limit stepper (15m–8h). Choices persist across restarts, and the Settings `appLimitsSummary` is now **derived** ("Chrome", "Instagram, TikTok, 3 others") instead of hardcoded. Switching an app *off* keeps its row with `enabled = false` so its limit survives a re-enable.
- `:app:assembleDebug`, `:app:lintDebug`, and `:app:testDebugUnitTest` pass. Remaining lint items are **warnings only** and intentional: `OldTargetApi` (targetSdk 36) and "newer version available" notices for deliberately-pinned versions (compileSdk 37, core-ktx, compose-bom, nav-compose, kotlin plugin — pinned for the AGP-9 toolchain and compileSdk-36 constraint).
- **Tests (81):** usage limit math + repository rollover (`UsageMathTest`, `UsageRepositoryTest`), tracker accrual/gating/signalling (`UsageTrackerTest`, `AccrualMathTest` — scripted fake monitor + fake clock, no device), the reward guarantee pinned to the real bonus constant (`RewardLoopTest`), block session lifecycle (`BlockSessionTest`), Home/Stats shaping incl. divide-by-zero and over-budget edges (`HomeMapperTest`, `StatsMapperTest`, `StreakTest`), picker join/search (`AppPickerMapperTest`), duration formatting (`LimitFormatTest`), settings summary (`SettingsSummaryTest`), installed-app filtering (`InstalledAppsProviderTest`). Mappers take `today`/`zone` parameters purely so tests can pin them — never call `LocalDate.now()` inside one. Share `data/usage/FakeUsageDao` (test source set) rather than re-rolling a fake — it inherits the real `@Transaction` accrual logic. Tests collecting a `SharedFlow` off that fake must use `Dispatchers.Unconfined`: the fake never really suspends, so a normally-dispatched collector never gets scheduled and emissions are silently missed. Still untested despite being written for it: `classify`, `sensitivityToMinConfidence`, `toGallerySections`.
- **Home and Stats render real data.** Home: today's used/remaining across monitored apps, progress ring, capture streak, today's cat count, active-app count, and an activity feed (currently-blocked apps + today's verified cats). Stats: daily total, vs-yesterday delta, Mon–Sun hours chart, week-over-week trend, budget-left donut, per-app breakdown, longest streak, lifetime captures. Verified on an emulator against real data (26m Chrome usage, 2 captures): Home and Stats agree, the week chart spikes only on today, and an empty history reads "No data for yesterday" instead of dividing by zero.
- **`SampleData` is down to one entry** — the Block Overlay's in-app debug route. Every `.sample()` elsewhere is `@Preview`-only.
- **Known gaps:** the block is escapable (no back/home interception — the user is simply re-blocked on return); Stats' `preventedCount`/`unlocks`/`achievements` are blanked pending real features; Home's next-break countdown is placeholder copy (no break feature) and the user profile is hardcoded ("Sophia"); `dailyLimitLabel`, `breakDurationLabel`, `confidenceLabel`, reminder/warning rows and `versionLabel` in Settings are still static; there's no streak-bonus capture path (`isBonus` is never set true yet); app *categories* don't exist, so the Stats donut shows per-app usage rather than the mockup's Social/Productivity/Entertainment split.
- **Permissions the app now needs at runtime:** `CAMERA` (runtime dialog), plus two **special** permissions granted only in system Settings — usage access (`PACKAGE_USAGE_STATS`) and "display over other apps" (`SYSTEM_ALERT_WINDOW`). Both have a Settings row that deep-links out and re-checks on resume. For emulator testing: `adb shell appops set com.example.unpawse GET_USAGE_STATS allow` and `... SYSTEM_ALERT_WINDOW allow`.

## Open Decisions (record outcomes here once resolved)

- **Fonts:** whether to bundle Inter TTFs vs. downloadable Google Fonts vs. keep platform sans-serif.
- **Dark color scheme:** currently provisional/derived; needs a real dark mockup.
- **Final application ID** (replace the `com.example` placeholder).

### Resolved
- **Foreground-app detection: `UsageStatsManager` polling** (not `AccessibilityService`). A foreground Service polls `queryEvents` ~1s. Cost: `PACKAGE_USAGE_STATS` (granted via a Settings redirect, not a runtime dialog), a foreground service + persistent notification, ~1–2s block latency, and `SYSTEM_ALERT_WINDOW` to draw the block over the offending app. **Why not AccessibilityService:** it would be near-instant, notification-free, and could redirect without an overlay permission — but Play Store review rejects most non-accessibility uses of it, it can read screen content (privacy scrutiny), and the permission ask is far heavier. The latency and notification are an acceptable price for a shippable app. `ForegroundAppMonitor` is an interface, so an Accessibility implementation could be slotted in later without touching callers.
- **Dependency injection:** manual, **app-scoped `AppContainer`** on a custom `Application` (not Hilt/Koin) — preserves the no-extra-compiler-plugins stance.
- **Data/persistence:** **Room** for structured/relational data (captures; per-app limits + usage next) and **Preferences DataStore** for scalar settings. (No remote API.)
- **Camera/detection:** **implemented** — CameraX capture + ML Kit on-device cat verification behind `CatDetector`.
- **UI toolkit:** Jetpack Compose + Material 3 (Views/XML removed).
