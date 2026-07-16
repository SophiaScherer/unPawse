# AGENTS.md

> **This is a living document.** unPawse is an early-stage Android app. This file describes the current state of the code and should be updated continuously as the app evolves. Whenever you make a structural change or an important decision, update this file to keep it accurate.

## Overview

**unPawse** is a cat-themed **screen-time manager**: when an app's daily time limit is reached, the user must photograph their cat (verified on-device) to earn more time. The app implements the **full UI** for six screens from a design mockup, and now has a **real capture pipeline** (CameraX + on-device ML cat detection + Room-backed persistence) plus an **app-scoped dependency container** and **DataStore settings persistence**.

The app is mid-transition from "UI shell driven by sample data" to "functioning blocker." The **Camera** and **Gallery** screens are live (real ViewModels + repositories); **Home**, **Stats**, and **Block** still render from `data/SampleData.kt`; **Settings** now reads/writes persisted values through `SettingsRepository` (with several labels still static). Screens stay stateless `XxxScreen(state, callbacks)`, so swapping sample data for a ViewModel never touches the screen. See the roadmap in `.claude/plans/` for the phased build (data layer → app picker → foreground detection → block trigger → reward loop → live Home/Stats).

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
│   ├── SampleData.kt          # Hardcoded UiState for the still-static screens (Home/Stats/Block)
│   ├── apps/                  # InstalledApp + InstalledAppsProvider (PackageManager, behind an iface)
│   ├── capture/               # Capture, CaptureEntity(+toDomain), CaptureDao, CaptureDatabase,
│   │                          #   CaptureRepository, PhotoStorage
│   ├── settings/              # SettingsRepository (Preferences DataStore)
│   └── usage/                 # MonitoredApp(+Entity), DailyUsage(+Entity), UsageDao,
│                              #   UsageRepository, UsageMath (pure limit arithmetic)
├── ml/                        # CatDetector (ML Kit) + DetectionResult; classify()/sensitivity map
└── ui/
    ├── theme/                 # Color, Theme (light + provisional dark), Type, Shape, Dimens
    ├── navigation/            # Routes, TopLevelDestination, UnPawseNavHost, UnPawseBottomBar
    ├── components/            # Stateless reusable UI (cards, charts, chips, timeline, settings rows…)
    ├── home/ · stats/ · block/        # Static: XxxScreen(state, callbacks) + XxxUiState(.sample())
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
- **UI state:** every screen is a stateless `XxxScreen(state: XxxUiState, …callbacks)` with an `XxxUiState.sample()` for `@Preview`. **Live** screens add a stateful `XxxRoute` that owns a `ViewModel` (built by `XxxViewModel.factory(context)`) and collects `uiState` with `collectAsStateWithLifecycle()` (see Camera/Gallery/Settings). **Static** screens (Home/Stats/Block) are still wired to `SampleData.xxxState` in the `NavHost` — replace with a Route/ViewModel later without touching the screen. Keep non-trivial mapping logic in pure, testable objects (e.g. `GalleryMapper.toGallerySections`, `classify`, `sensitivityToMinConfidence`).
- **Settings persistence.** `SettingsRepository` (Preferences DataStore) persists dark-mode override, cat-detection sensitivity, require-live-photo, and daily-summary. `SettingsViewModel` exposes them as `SettingsUiState` (still-static labels come from `SettingsUiState.sample()` until later phases make them dynamic). **Dark mode is special:** it lives in `UnPawseApp` (resolves a `null` override against `isSystemInDarkTheme()`) so it can drive the whole theme, and persists through the same repository. Sensitivity feeds `CatDetector` via `AppContainer.catDetectorMinConfidence` (a `StateFlow` mapped by `sensitivityToMinConfidence`), so a settings change takes effect without recreating the camera pipeline.
- **Camera / detection.** `CameraViewModel` runs the pipeline *take photo → classify → save-if-cat*; the CameraX controller stays lifecycle-bound in the composable (`CameraRoute` passes a suspend capture lambda in). `CameraEvent` (`Saved` / `NotACat` / `Error`) is a one-shot `Channel`; `CameraRoute` currently **drains it without acting** (hint text already reflects the outcome) — this is the seam for the reward loop (credit minutes + dismiss block on `Saved`). `CatDetector` wraps all ML behind one class; `classify` is a pure gate. Captures persist as app-internal JPEGs via `PhotoStorage` (no media permission).
- **App picker + package visibility.** `InstalledAppsProvider` (`data/apps/`) fences off `PackageManager` so ViewModels stay testable. It queries **only LAUNCHER-resolvable apps**, which is the deliberate, restrained answer to Android 11+ package visibility: it needs the `<queries>` intent element in the manifest but **not** `QUERY_ALL_PACKAGES` (which triggers Play Store sensitive-permission review). Consequence: non-launchable apps never appear in the picker — fine, since you can't spend screen time in an app you can't open. App **icons are loaded lazily per visible row** (`ui/apppicker/AppIcon.kt`, `produceState` + IO) rather than eagerly for every app, keeping `Drawable`s out of UI state and off the main thread.
- **Navigation:** single-activity, `navigation-compose` with **plain string routes** (`Routes`), deliberately **not** kotlinx-serialization type-safe routes (avoids a second compiler-plugin/version-matching dependency). Tabs use `popUpTo(start){saveState}; launchSingleTop; restoreState`. Settings rows emit ids (`SettingsRowIds`) that the NavHost maps to destinations — only `APP_LIMITS` → `Routes.APP_PICKER` is wired so far; the rest are inert. The **Block Overlay** is a normal destination (no bottom bar) temporarily reachable from Home's "Pause Protection" card — this stays as a debug/preview entry; the real limit-reached trigger is being built (see Open Decisions + roadmap).
- **Theme:** `UnPawseTheme(darkTheme, content)` maps all `DESIGN.md` tokens to a `lightColorScheme`; no dynamic color (blush-pink brand identity is intentional). The **dark scheme is provisional** (derived from fixed/inverse tokens) — refine against a real dark mockup.
- **Typography:** the type scale matches `DESIGN.md`, but the family is currently the **platform sans-serif**, not Inter. To switch to Inter: drop the four OFL TTFs into `res/font/` and replace `AppFontFamily` in `ui/theme/Type.kt`.
- **Icons:** `material-icons-extended` — the ~40 mockup symbols map nearly 1:1; R8 strips the unused ones. Notable substitutes: `Shield` (Pause Protection), `GridView` (Gallery), `LocalCafe`, `PhoneAndroid`.
- **Insets:** `enableEdgeToEdge()`; `Scaffold` inner padding is applied as lazy-list `contentPadding` so content scrolls under the bars; Camera consumes only the status-bar inset; Block uses `safeDrawingPadding()`.
- Add new dependencies via the version catalog and reference them as `libs.*`. Package everything under `com.example.unpawse`.

## Current State

- All six screens (Home, Stats, Gallery, Settings, Camera, Block Overlay) are implemented and reachable; bottom-nav tabs + the Block route work; the dark-mode toggle flips the theme live **and persists**.
- **Camera** captures real photos, verifies them on-device (ML Kit), and stores verified cats; **Gallery** streams them from Room via Coil. **Settings** toggles/sensitivity persist through DataStore.
- Custom Canvas visuals: circular progress ring, smooth line chart, donut chart, mini bar chart, activity timeline.
- **Usage data layer exists** (`data/usage/`): per-app daily limits + today's used/earned time, with atomic accrual and free daily reset. The **app picker writes limits** to it; nothing writes *usage* yet — that's the foreground monitor.
- **App picker works end-to-end** (Settings → "Individual app limits"): real installed apps with launcher icons, search, per-app switch + 15-minute-step limit stepper (15m–8h). Choices persist across restarts, and the Settings `appLimitsSummary` is now **derived** ("Chrome", "Instagram, TikTok, 3 others") instead of hardcoded. Switching an app *off* keeps its row with `enabled = false` so its limit survives a re-enable.
- `:app:assembleDebug`, `:app:lintDebug`, and `:app:testDebugUnitTest` pass. Remaining lint items are **warnings only** and intentional: `OldTargetApi` (targetSdk 36) and "newer version available" notices for deliberately-pinned versions (compileSdk 37, core-ktx, compose-bom, nav-compose, kotlin plugin — pinned for the AGP-9 toolchain and compileSdk-36 constraint).
- **Tests (33):** usage limit math + repository rollover (`UsageMathTest`, `UsageRepositoryTest` — in-memory fake `UsageDao` + injected clock), picker join/search (`AppPickerMapperTest`), limit formatting/stepping (`LimitFormatTest`), settings summary (`SettingsSummaryTest`), installed-app filtering (`InstalledAppsProviderTest`). Still untested despite being written for it: `classify`, `sensitivityToMinConfidence`, `toGallerySections`.
- **Not yet built:** foreground-app detection, the real limit-reached block trigger, the capture→earn-time reward loop, and live Home/Stats.

## Open Decisions (record outcomes here once resolved)

- **Foreground-app detection — `UsageStatsManager` polling vs. `AccessibilityService`.** Needed to know which app is in the foreground and enforce limits. Trade-offs:
  - *`UsageStatsManager` (poll `queryEvents` ~1s from a foreground Service):* lighter, more familiar permission (`PACKAGE_USAGE_STATS`, granted via a Settings redirect); ~1s detection latency; needs `SYSTEM_ALERT_WINDOW` to draw the block over the offending app; a foreground service + notification; generally smoother Play Store review.
  - *`AccessibilityService` (listen for `TYPE_WINDOW_STATE_CHANGED`):* near-instant detection and can redirect without `SYSTEM_ALERT_WINDOW`; but a heavier permission ask, stricter Play Store review/justification, and more privacy scrutiny.
  - **Status: undecided** — to be chosen at the start of the foreground-detection phase and recorded here.
- **Fonts:** whether to bundle Inter TTFs vs. downloadable Google Fonts vs. keep platform sans-serif.
- **Dark color scheme:** currently provisional/derived; needs a real dark mockup.
- **Final application ID** (replace the `com.example` placeholder).

### Resolved
- **Dependency injection:** manual, **app-scoped `AppContainer`** on a custom `Application` (not Hilt/Koin) — preserves the no-extra-compiler-plugins stance.
- **Data/persistence:** **Room** for structured/relational data (captures; per-app limits + usage next) and **Preferences DataStore** for scalar settings. (No remote API.)
- **Camera/detection:** **implemented** — CameraX capture + ML Kit on-device cat verification behind `CatDetector`.
- **UI toolkit:** Jetpack Compose + Material 3 (Views/XML removed).
