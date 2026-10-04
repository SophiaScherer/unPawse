# Spec: privacyTruth (DEP-01, VIS-04; DEP-05 left open)

## What was built

- **No network permissions.** `INTERNET` and `ACCESS_NETWORK_STATE` came in through ML Kit's transitive `com.google.android.datatransport` libraries. `AndroidManifest.xml` now removes both with `tools:node="remove"`.
- **ML Kit's own telemetry backend removed.** The manifest also removes `com.google.android.datatransport.runtime.backends.TransportBackendDiscovery`, which is where the `cct` backend (Firebase logging) registers. With no backend registered, `DefaultScheduler` drops every `FIREBASE_ML_SDK` event before it is stored ("Transport backend 'cct' is not registered", a warning). No events are queued and no upload jobs are scheduled.
- **Privacy policy matches the app.** `PrivacyPolicyContent.kt` now covers: the telemetry switch-off; everything that is actually stored (block counts, unlock counts, categories, weekend budgets, schedules, photo dimensions, earned minutes, favorites); Export data as a user-started way for data to leave the device; usage access also feeding the App Picker and Stats' "All apps" view; the reminder, warning and summary notifications; background running; and Delete all data. The backup paragraph is unchanged and still accurate.
- **Status-bar icon.** `res/drawable/ic_stat_cat.xml` is a single-fill vector: the launcher's monochrome cat head, cropped to 24dp with about 1dp of padding. `Notifications.builder` uses it, and every notification goes through that builder.

## Merged manifest diff (debug)

```
-    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
-    <uses-permission android:name="android.permission.INTERNET" />
-        <service android:name="com.google.android.datatransport.runtime.backends.TransportBackendDiscovery" android:exported="false">
-            <meta-data android:name="backend:com.google.android.datatransport.cct.CctBackendFactory" android:value="cct" />
-        </service>
```

## Key decisions

- **The claim was made true rather than the policy rewritten to disclose telemetry.** This was the audit's preferred fix. Sophia has not decided explicitly, so it is flagged as a judgment call.
- **Only the backend discovery service is removed.** `JobInfoSchedulerService` and `AlarmManagerSchedulerBroadcastReceiver` stay. If they were removed, an old install's leftover upload job, or the runtime's `ensureContextsScheduled`, would point JobScheduler at a component that no longer exists. Removing the backend is enough to stop events from being stored at all.
- **ML Kit's component registrars are untouched.** Detection depends on them.
- **The events database file is still created, empty.** `TransportRuntime.initialize` opens it. Avoiding that would mean keeping ML Kit's logger from being constructed, and there is no clean switch for that.
- **No migration for installs upgraded from older builds.** Their queued events stay. When the leftover upload job runs, it logs a `SecurityException` (from `getActiveNetworkInfo`), which `SafeLoggingExecutor` catches, so there is no crash. `pm clear` or a reinstall removes the queue. The app has never shipped, so this only affects dev installs.
- **ML Kit keeps a 184-byte local file**, `files/com.google.mlkit.acceleration/…acceleration_analytics_storage_v2`, with model ID and benchmark numbers. It is on-device only, which is why the policy says reporting is switched off rather than "nothing is recorded".
- **DEP-05 (Auto Backup template rules) is unchanged.** It is an open decision for Sophia.

## How to verify

1. `./gradlew :app:assembleDebug --no-daemon -Pkotlin.compiler.execution.strategy=in-process`, then check that `grep -E "INTERNET|NETWORK_STATE|TransportBackendDiscovery" app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml` prints nothing.
2. Install, then `adb shell dumpsys package com.example.unpawse | grep -E "INTERNET|NETWORK_STATE"` should print nothing.
3. `pm clear`, grant CAMERA, open Camera, press the shutter. The hint should read "Hmm, that's not a cat — try again." and logcat should show no `CameraViewModel` failure and no crash.
4. Pull `databases/com.google.android.datatransport.events` with `run-as` and check that `events` and `transport_contexts` have 0 rows. `dumpsys jobscheduler` should list no `JobInfoSchedulerService` job.
5. Grant usage access and the overlay permission, launch the app, and open the shade. The "unPawse is watching your limits" row should show the cat-head silhouette.

All five were done on the API-36 emulator (2026-10-04). Steps 1 and 2 were done before and after the change. The upgrade path was also exercised: install the old build, capture offline to queue events, `install -r` the new build, then `cmd jobscheduler run -f` the leftover jobs. The result was a caught `SecurityException` and no crash.

## Review follow-up

- An independent review found a second ML Kit telemetry path: the labeller logs anonymous call counts through Google Play services (`TelemetryLoggingClient`, IPC to GmsCore), which uploads with its own network access. There is no clean way to disable it from the app, so the policy now discloses it (counts and timing only, never the photo or its result) instead of claiming reporting is off. Removing it entirely would mean dropping or replacing ML Kit, which is Sophia's call.
- Also corrected: warnings are on by default (only reminders and the summary are opt-in), device-to-device transfer copies data too, and the manifest comment about leftover event queues.
