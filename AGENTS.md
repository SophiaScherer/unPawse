# AGENTS.md

> **This is a living document.** unPawse is an early-stage Android app. This file describes the current state of the code and should be updated continuously as the app evolves. Whenever you make a structural change or an important decision, update this file to keep it accurate.

## Overview

**unPawse** is a cat-themed **screen-time manager**: when an app's daily time limit is reached, the user must photograph their cat (verified on-device) to earn more time. The app currently implements the **full UI** for six screens from a design mockup, driven by hardcoded sample data. **No backend/business logic exists yet** — screens are stateless and rendered from `UiState` data classes, leaving a clean seam for ViewModels/repositories.

The design system ("Warm Minimalist") centers on a blush-pink (`#F5B6C8`) / plum (`#815060`) Material 3 palette over a warm-white surface, Inter-style typography, 24dp rounded cards, pill buttons, and a "squishy" 98%-press-scale feel. Source tokens live in the mockup's `DESIGN.md`.

## Tech Stack

- **Language:** Kotlin
- **UI toolkit:** **Jetpack Compose + Material 3** (`androidx.compose.material3`). The old Views/XML scaffold has been removed. _(Resolves the former open decision.)_
- **Build system:** Gradle (Kotlin DSL), version catalog at `gradle/libs.versions.toml`.
- **Android Gradle Plugin:** 9.2.1 — **applies Kotlin itself** (built-in). There is intentionally **no** `org.jetbrains.kotlin.android` plugin.
- **Compose compiler:** `org.jetbrains.kotlin.plugin.compose` at **2.2.20**, which must exactly match AGP 9.2.1's built-in Kotlin version. This matched on the first try (verified via `compileDebugKotlin`). If it ever mismatches, the build fails fast and states the required version — update `kotlin` in the catalog. Fallback: apply explicit KGP to opt out of built-in Kotlin.
- **compileSdk / targetSdk:** 36 · **minSdk:** 26 · **Java compatibility:** 11

### Key dependencies (all via the version catalog)
- Compose **BOM** `2025.12.01` → `ui`, `ui-graphics`, `ui-tooling(-preview)`, `material3`
- `material-icons-extended` **pinned 1.7.8** (it left the Compose BOM)
- `activity-compose`, `navigation-compose`
- `androidx.core:core-ktx` **pinned 1.17.0** (1.18+ require compileSdk 37; we compile against 36)
- Testing: JUnit4, AndroidX Test (`ext.junit`), Espresso, Compose UI test

> Removed from the original scaffold: appcompat, Material Components (Views), constraintlayout, activity-ktx.

## Project Structure

```
app/src/main/java/com/example/unpawse/
├── MainActivity.kt            # ComponentActivity → enableEdgeToEdge() → setContent { UnPawseApp() }
├── UnPawseApp.kt              # Root: theme + dark-mode override + Scaffold(bottom bar) + NavHost
├── data/
│   └── SampleData.kt          # Central hardcoded UiState instances (the ViewModel injection seam)
└── ui/
    ├── theme/                 # Color, Theme (light + provisional dark), Type, Shape, Dimens
    ├── navigation/            # Routes, TopLevelDestination, UnPawseNavHost, UnPawseBottomBar
    ├── components/            # Stateless reusable UI (cards, charts, chips, timeline, settings rows…)
    ├── home/  · stats/ · gallery/ · settings/ · camera/ · block/
    │                          # Each: XxxScreen(state, callbacks) + XxxUiState(+ .sample()) + @Preview
```

- **Application ID / namespace:** `com.example.unpawse` _(placeholder — likely to change before release)._
- **Entry point:** `MainActivity` → `UnPawseApp()`.

## Architecture & Conventions

- **UI state:** every screen is a stateless `XxxScreen(state: XxxUiState, …callbacks)`. Concrete mockup data lives in each `XxxUiState.sample()`; `data/SampleData.kt` names them in one place. The `NavHost` injects `SampleData.xxxState` into each screen — replace with `viewModel.uiState.collectAsStateWithLifecycle()` later **without touching the screens**.
- **Navigation:** single-activity, `navigation-compose` with **plain string routes** (`Routes`), deliberately **not** kotlinx-serialization type-safe routes (avoids a second compiler-plugin/version-matching dependency). Tabs use `popUpTo(start){saveState}; launchSingleTop; restoreState`. The **Block Overlay** is a normal destination (no bottom bar) temporarily reachable from Home's "Pause Protection" card — rewire to the real limit-reached trigger when backend exists.
- **Theme:** `UnPawseTheme(darkTheme, content)` maps all `DESIGN.md` tokens to a `lightColorScheme`; no dynamic color (blush-pink brand identity is intentional). The **dark scheme is provisional** (derived from fixed/inverse tokens) — refine against a real dark mockup. The Settings dark-mode toggle **actually flips the theme** but is **session-only** (`rememberSaveable` in `UnPawseApp`, no persistence yet).
- **Typography:** the type scale (sizes/weights/line-heights) matches `DESIGN.md`, but the family is currently the **platform sans-serif**, not Inter. Downloadable Google Fonts needs the GMS provider certificates and bundling needs the OFL TTFs — neither was added in the UI pass. To switch to Inter: drop the four OFL TTFs into `res/font/` and replace `AppFontFamily` in `ui/theme/Type.kt`.
- **Icons:** `material-icons-extended` — the ~40 mockup symbols map nearly 1:1; R8 strips the unused ones. Notable substitutes: `Shield` (Pause Protection), `GridView` (Gallery / AutoAwesomeMosaic), `LocalCafe`, `PhoneAndroid`.
- **Images:** **no mockup photos are committed.** `CatPhotoPlaceholder` renders seeded warm gradients + a faint paw; `InitialsAvatar` stands in for profile photos. Swap for a real image loader (e.g. Coil `AsyncImage`) later.
- **Camera:** `CameraScreen` is a **static placeholder viewfinder** (warm gradient) with the real overlay controls — **no CameraX, no permissions** yet.
- **Insets:** `enableEdgeToEdge()`; `Scaffold` inner padding is applied as lazy-list `contentPadding` so content scrolls under the bars; Camera consumes only the status-bar inset; Block uses `safeDrawingPadding()`.
- Add new dependencies via the version catalog and reference them as `libs.*`. Package everything under `com.example.unpawse`.

## Current State

- All six screens (Home, Stats, Gallery, Settings, Camera, Block Overlay) are implemented and reachable; bottom-nav tabs + the Block route work; the dark-mode toggle flips the theme live.
- Custom Canvas visuals: circular progress ring, smooth line chart, donut chart, mini bar chart, activity timeline.
- `:app:assembleDebug` and `:app:lintDebug` both pass. Remaining lint items are **warnings only** and intentional: `OldTargetApi` (targetSdk 36) and "newer version available" notices for the deliberately-pinned versions (compileSdk 37, core-ktx 1.19, compose-bom, nav-compose, kotlin plugin — pinned for the AGP-9 toolchain and compileSdk-36 constraint).
- No data layer, DI, networking, persistence, or tests beyond the template placeholders.

## Open Decisions (record outcomes here once resolved)

- **Dependency injection:** Hilt / Koin / manual — not chosen.
- **Data/persistence:** Room, DataStore, remote API — not chosen (dark-mode + settings are session-only until this lands).
- **Camera/detection:** CameraX + on-device cat verification — not implemented.
- **Fonts:** whether to bundle Inter TTFs vs. downloadable Google Fonts vs. keep platform sans-serif.
- **Dark color scheme:** currently provisional/derived; needs a real dark mockup.
- **Final application ID** (replace the `com.example` placeholder).
