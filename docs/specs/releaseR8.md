# releaseR8

## What was built
- R8 code shrinking and resource shrinking for the `release` build type (`isMinifyEnabled = true`, `isShrinkResources = true`, `proguard-android-optimize.txt` plus `app/proguard-rules.pro`).
- One app-level keep block, for ML Kit (`com.google.mlkit.**`, `com.google.firebase.components.**`).
- No signing config, application ID or baseline profile changes. Sophia approved R8 plus keep rules only.

## Key decisions
- **Classic DSL, not AGP 9's `optimization {}`.** `optimization { enable = true }` fails configuration with "Cannot use optimization.enable=true without setting android.r8.gradual.support flag". Setting an experimental flag to use the new DSL wasn't worth it.
- **The ML Kit keep rule is required, not defensive.** Without it, opening the camera crashed with an NPE in `MultiFlavorDetectorCreator.create` (retraced from `ImageLabeling.getClient` ← `CatDetector.defaultLabeler`). The detector registry resolved to null under R8 full mode. It's kept broadly because detection is the app's core feature, and the size cost is small (47.66 → 47.89 MB).
- **Full mode is kept** (the AGP default) rather than falling back to compat mode for the whole app.

## Numbers (API-36 emulator, debug-key-signed release APKs)
- APK: 61.6 MB without R8 → 47.9 MB with R8 (−22%). Most of what's left is the bundled ML Kit model and native libs.
- Cold start (`am start -W` TotalTime): without R8, 536–582 ms over 5 runs. With R8, 403–684 ms over 10 runs, with one outlier at 1358 ms. Two Gradle builds were running on the same host during the R8 runs, so treat cold start as **not measurably different** here. A quiet host or a baseline profile measurement is a follow-up.

## How to verify
1. `./gradlew :app:assembleRelease --no-daemon`
2. Sign it locally without touching the build config:
   - `zipalign -f -p 4 app-release-unsigned.apk aligned.apk`
   - `apksigner sign --ks ~/.android/debug.keystore --ks-pass pass:android --ks-key-alias androiddebugkey --out r8.apk aligned.apk`
3. Uninstall any debug build (signatures match, but start fresh), then `adb install r8.apk`.
4. Walk the app and check `adb logcat -b crash` stays empty:
   - App Picker toggle;
   - a focus session, then open Chrome (overlay);
   - Stats and Gallery;
   - Camera: the shutter gives a "not a cat" verdict on the virtual scene, which proves ML Kit ran;
   - Settings → Schedules;
   - Export, then Import of that file.
5. To retrace a crash, run R8's `com.android.tools.r8.retrace.Retrace`, which ships inside AGP's `builder-9.2.1.jar`, against `app/build/outputs/mapping/release/mapping.txt`.

## Not verified
- The limit block's "Open Camera" path and an earned unblock: a release build isn't debuggable, so the `run-as` seeding recipe can't push usage near a limit. The overlay itself was verified through a focus block, and the camera through the Camera tab.
- `DailySummaryWorker` firing (only its WorkManager scheduling was seen in `dumpsys jobscheduler`), and `BOOT_COMPLETED` on a release build.
