# AGENTS.md

> **This is a living document.** unPawse is a brand-new Android app at the scaffold stage. This file describes the current state of the code and will be updated continuously as the app evolves, features are added, and design/architecture decisions are made. Whenever you make a structural change or an important decision, update this file to keep it accurate.

## Overview

**unPawse** is an Android application, currently at its initial project scaffold. The app has been generated from the standard Android Studio "Empty Views Activity" template and does not yet contain any product-specific features. The purpose, feature set, and architecture are still to be defined.

## Tech Stack

- **Language:** Kotlin
- **UI toolkit:** Android Views (XML layouts) with AppCompat + Material Components. _Not_ Jetpack Compose (yet — this is an open decision).
- **Build system:** Gradle (Kotlin DSL, `.gradle.kts`), with a version catalog at `gradle/libs.versions.toml`.
- **Android Gradle Plugin:** 9.2.1
- **compileSdk / targetSdk:** 36
- **minSdk:** 26 (Android 8.0)
- **Java compatibility:** 11

### Key dependencies
- `androidx.core:core-ktx`
- `androidx.appcompat:appcompat`
- `androidx.activity:activity-ktx`
- `androidx.constraintlayout:constraintlayout`
- `com.google.android.material:material`
- Testing: JUnit4, AndroidX Test (`ext.junit`), Espresso

## Project Structure

```
unPawse/
├── build.gradle.kts              # Root Gradle config
├── settings.gradle.kts           # Module includes (:app), repositories
├── gradle.properties
├── gradle/
│   ├── libs.versions.toml         # Version catalog — add dependencies/versions here
│   └── wrapper/                   # Gradle wrapper
├── gradlew / gradlew.bat          # Gradle wrapper scripts
└── app/
    ├── build.gradle.kts           # App module config (SDK levels, deps)
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/com/example/unpawse/
        │   │   └── MainActivity.kt     # Single launcher activity
        │   └── res/
        │       ├── layout/activity_main.xml
        │       ├── values/             # colors, strings, themes
        │       ├── values-night/       # dark theme
        │       ├── drawable/           # launcher icon vectors
        │       ├── mipmap-*/           # launcher icons
        │       └── xml/                # backup & data-extraction rules
        ├── test/                       # Local unit tests (JUnit)
        └── androidTest/                # Instrumented tests (Espresso)
```

- **Application ID / namespace:** `com.example.unpawse` _(placeholder — likely to change before release)._
- **Entry point:** `MainActivity` (`app/src/main/java/com/example/unpawse/MainActivity.kt`), a single `AppCompatActivity` that enables edge-to-edge and applies system-bar insets to the root view. It is the sole launcher activity.

## Current State

Everything present is boilerplate from the project template:
- One activity (`MainActivity`) with an empty ConstraintLayout screen.
- Default theming (light + night), launcher icons, and backup/data-extraction rules.
- Placeholder example unit and instrumented tests.

No app-specific business logic, data layer, navigation, or networking exists yet.

## Conventions & Notes

- Add new dependencies via the version catalog (`gradle/libs.versions.toml`) and reference them as `libs.*`, rather than hardcoding coordinates in `build.gradle.kts`.
- Package everything under `com.example.unpawse`.

## Open Decisions (to be resolved as the app evolves)

These are not yet decided; record the outcome here once they are:
- **UI approach:** stay on Views/XML vs. migrate to Jetpack Compose.
- **Architecture pattern:** MVVM, MVI, etc.
- **Navigation:** single-activity + Navigation component vs. multiple activities.
- **Data/persistence:** Room, DataStore, remote API, etc.
- **Dependency injection:** Hilt/Koin/manual.
- **Final application ID** (replace the `com.example` placeholder).
