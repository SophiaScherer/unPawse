# unPawse

A cat-themed screen-time manager for Android. You give the apps that eat your day a daily budget;
when one runs out, unPawse draws a block over it and asks you to go photograph a cat.

**The loop:** pick apps and limits → a foreground service counts the time you actually spend in them
→ hitting a limit blocks the app → photographing a real cat (verified on-device) credits bonus
minutes and lets you back in.

The cat check is real image recognition, not a formality: an ML Kit image labeler runs on the phone
and only a frame it labels "Cat" above your chosen confidence counts. There is no server anywhere in
this app.

---

## Features

**Limits and blocking**

- Per-app daily limits, picked from the apps installed on the phone, in 15-minute steps from 15
  minutes to 8 hours, with an optional separate weekend budget.
- A foreground service polls `UsageStatsManager` about once a second to see which app is in front,
  so a block goes up within roughly a second of you opening an app that is out of budget.
- The block names the app and offers the camera. Back is swallowed; leaving via Home or Recents
  simply re-blocks you when you return.
- Monitoring restarts after a reboot, and a periodic worker re-arms it if the service is ever killed.

**Earning time back**

- A verified cat is worth 15 minutes by default, configurable up to 60.
- The escape hatch is bounded, deliberately: a cat only pays out against a live block (within a
  5-minute window), at most once per 10-minute cooldown, and up to 60 bonus minutes per app per day.
- Detection sensitivity is a slider in Settings, with the confidence it corresponds to shown next to
  it.

**Focus sessions and schedules**

- A focus session hard-blocks every monitored app for a fixed duration, with no cat-photo escape —
  the block lifts when the timer ends.
- Recurring blocking windows ("bedtime") with a name, start and end times, day-of-week chips with
  Weekdays / Weekends / Every day presets, and a scope of either all monitored apps or one. A switch
  pauses a window without deleting it. Scheduled blocks are escape-less for the same reason focus
  sessions are: earned minutes raise a *budget*, so they cannot argue with a rule about *when*.

**Home and Stats**

- Home shows today's used and remaining time across monitored apps, a progress ring, your capture
  streak, today's cat count, and a recent-activity feed. Every figure is gated on the permissions
  actually being granted — when it can't enforce, it says so instead of showing a comforting number.
- Stats shows daily screen time with a week-over-week trend, a category breakdown donut (Social,
  Productivity, Entertainment, Other — seeded from the platform's own app category and overridable),
  blocks raised this week, device unlocks, longest streak, and five achievement badges derived from
  your stored history.
- Stats can switch between your tracked apps and every app on the phone.

**Photos**

- Gallery of your verified cats, grouped by day, with favorites, single-photo delete, and sharing one
  photo out through the system share sheet.
- Photo storage screen: photo count, library size, a retention window (30 days by default, or never),
  and delete-all. Favorites are kept until you delete them yourself.

**Data and settings**

- Export to a ZIP bundle (`export.json` plus `photos/`) and import one back, both through the system
  file picker, so no storage permission is involved. Import replaces everything, and parses the whole
  bundle before erasing anything.
- Notifications: an optional check-in reminder while a limited app is open, a heads-up before an app
  runs out, and an evening recap.
- Light / dark / system theme, a display name, a full "delete all data" reset, and an in-app privacy
  policy.

---

## Permissions, and why each one is load-bearing

unPawse asks for four things. Two are ordinary runtime dialogs; two are *special* permissions that
Android only grants from a page in system Settings, which is why the app deep-links you there and
re-checks when you come back.

| Permission | How it's granted | Why the app can't work without it |
| --- | --- | --- |
| **Usage access** (`PACKAGE_USAGE_STATS`) | System Settings | This is how the app learns which app is currently in the foreground. Without it there is no way to know you opened Instagram, so nothing accrues and nothing blocks. It reports app names and timings only — unPawse cannot see anything *inside* those apps. |
| **Display over other apps** (`SYSTEM_ALERT_WINDOW`) | System Settings | This is what actually draws the block on top of the app that ran out of time. It is also what lets the block open the camera from the background so you can go earn time back. |
| **Camera** (`CAMERA`) | Runtime dialog | To photograph the cat. Requested from inside the camera screen and used only while it is open. There is no Settings row for it on purpose. |
| **Notifications** (`POST_NOTIFICATIONS`) | Runtime dialog | Android requires a visible ongoing notification for the monitoring service. The optional reminders, pre-lock warning and daily recap ride on the same permission. |

Three more are declared but never prompt for anything — they are normal install-time permissions:

- `RECEIVE_BOOT_COMPLETED`, so monitoring resumes after a restart rather than silently stopping. A
  screen-time blocker that quietly stopped enforcing would be worse than none, because you'd still
  believe you were covered.
- `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_SPECIAL_USE`, which Android requires in order to run
  the usage-tracking service in the foreground at all.

Package visibility is requested narrowly: the manifest declares a `LAUNCHER` intent query so the app
picker can list launchable apps, rather than asking for `QUERY_ALL_PACKAGES`.

---

## Privacy

- **No network, at all.** The app does not declare the `INTERNET` permission, and there is no HTTP
  client, analytics SDK or ad SDK in the dependency list. There is no account and no server.
- **Detection runs on the phone.** ML Kit's image labeling model is bundled in the APK. No photo is
  uploaded to be verified.
- **Photos stay in app-private storage.** Captures are written to `filesDir/captures/` and are never
  added to MediaStore or the shared photo library. Sharing one out goes through a `FileProvider`
  content URI granted per-share, to the app you pick.
- **Android's own backup is a real exception, and the app says so.** `allowBackup` is currently
  `true` with the AGP template backup rules, which exclude nothing — so if you have Android Auto
  Backup enabled for your Google account, the system may include unPawse's sandbox (the database
  *and* the cat JPEGs) in your device backup or a device transfer. That is Android's backup rather
  than anything unPawse sends, and it can be turned off in device settings. Narrowing the backup
  rules is a recorded open decision, not an oversight; the in-app privacy policy states the situation
  in the same terms.
- Uninstalling removes everything the app stored on the device.

The full policy ships in-app under Settings → Privacy Policy, and lives in source at
`app/src/main/java/com/example/unpawse/ui/about/PrivacyPolicyContent.kt`. It is written as data so it
can be reviewed against the code; if a change adds a network call or a new stored field, that file is
part of the change.

---

## Requirements

- **minSdk 26** (Android 8.0 Oreo)
- **targetSdk / compileSdk 36**
- **JDK 21** to build (see below)
- Android SDK, with `sdk.dir` set in `local.properties`

---

## Building

### Java

This is the part most likely to bite you. Gradle 9 will not start on Java 8 — a machine whose `java`
on `PATH` is an old JRE fails before any Gradle task is even configured, with an error about the
class file version rather than anything about this project. The build also declares a **Java 21**
daemon toolchain (`gradle/gradle-daemon-jvm.properties`), and CI builds on Temurin 21.

So: install a JDK 21 and point `JAVA_HOME` at it.

```bash
# macOS / Linux
export JAVA_HOME=/path/to/jdk-21
```

```powershell
# Windows (PowerShell)
$env:JAVA_HOME = "C:\path\to\jdk-21"
```

Android Studio ships a bundled JDK — on a recent version it is new enough, and the IDE uses it
regardless of what is on your `PATH`. Command-line builds are the ones that need `JAVA_HOME` set.

### local.properties

Gradle needs to know where the Android SDK is:

```properties
sdk.dir=/path/to/Android/Sdk
```

Android Studio generates this file the first time it opens the project. It is not checked in.

### Commands

Use the Gradle wrapper — it pins the Gradle version this project expects.

```bash
./gradlew :app:assembleDebug      # build the debug APK
./gradlew :app:lintDebug          # Android Lint
./gradlew :app:testDebugUnitTest  # JVM unit tests
```

On Windows, use `gradlew.bat` in place of `./gradlew`.

The debug APK lands at:

```
app/build/outputs/apk/debug/app-debug.apk
```

Those three tasks are the project's build gate; a change is not done until all three pass. Anything
`Context`-bound — the service, the overlay, permissions, notifications, workers — is verified on a
device or emulator as well, since JVM tests can't reach it.

---

## Tests

622 JVM unit tests, run with:

```bash
./gradlew :app:testDebugUnitTest
```

They are plain JUnit4 with hand-written fake DAOs — no Robolectric, no MockK, no instrumentation
required. That is a deliberate constraint, and it shapes the code: the rules worth testing are pulled
out into pure functions (`UsageMath`, `RewardPolicy`, `ScheduleMath`, `Achievements`, the per-screen
mappers, the export/import document handling) so they can be exercised without Android. Shared fakes
live in the test source set next to the DAOs they stand in for.

The instrumentation source set (`app/src/androidTest`) is wired up — Espresso and the Compose UI test
artifacts are on the classpath, and `:app:connectedDebugAndroidTest` runs it on a device — but it
still holds only the AGP template test. Device verification happens by hand today.

---

## Project layout

```
app/src/main/java/com/example/unpawse/
├── MainActivity.kt / UnPawseApp.kt / UnPawseApplication.kt
├── data/        # Room + DataStore repositories: usage, capture, schedule, unlocks,
│                #   settings, export/import, installed-apps and device-usage providers
├── ml/          # CatDetector (ML Kit image labeling) + DetectionResult
├── service/     # Foreground monitor, usage tracker, block overlay host, block/focus
│                #   sessions, schedule gate, notifications, workers, permission helpers
└── ui/          # theme, navigation, shared components, and one package per screen:
                 #   home, stats, gallery, settings, camera, block, apppicker,
                 #   schedules, photos, about
```

Dependency injection is manual and app-scoped: `UnPawseApplication` builds a single `AppContainer`
(`data/AppContainer.kt`) that ViewModels read from. No Hilt, no Koin, no extra compiler plugins
beyond Compose and KSP.

**[`AGENTS.md`](AGENTS.md) is the detailed design document** — architecture and conventions, why each
decision was made and what was rejected, the current state of every feature including its known gaps,
and the open decisions. Read it before making a structural change, and update it in the same commit
as the change it describes.

---

## License

MIT. See [`LICENSE`](LICENSE).
