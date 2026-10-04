# unPawse

A cat-themed screen-time manager for Android. You give the apps that eat your day a daily budget;
when one runs out, unPawse draws a block over it and asks you to go photograph a cat.

**The loop:** pick apps and limits → a foreground service counts the time you actually spend in them
→ hitting a limit blocks the app → photographing a real cat (verified on-device by ML Kit) credits
bonus minutes and lets you back in.

---

## Getting it running on a phone

Two of the permissions unPawse needs can only be granted from system Settings, not from a dialog:

- **Usage access**, so it can tell which app is in front.
- **Display over other apps**, so it can draw the block.

Settings in the app links to both pages. Then add limits under Settings → Individual app limits.
Without those two permissions the app tracks and blocks nothing, and Home says so.

---

## Requirements

- **minSdk 26** (Android 8.0), **targetSdk / compileSdk 36**
- **JDK 21** to build
- The Android SDK, with `sdk.dir` set in `local.properties` (Android Studio writes this for you)

---

## Building

Gradle 9 won't start on an old `java` from `PATH`, and the build pins a Java 21 daemon toolchain
(`gradle/gradle-daemon-jvm.properties`). For command-line builds, point `JAVA_HOME` at a JDK 21:

```bash
export JAVA_HOME=/path/to/jdk-21          # macOS / Linux
```

```powershell
$env:JAVA_HOME = "C:\path\to\jdk-21"      # Windows (PowerShell)
```

Android Studio's bundled JDK works as-is.

```bash
./gradlew :app:assembleDebug      # debug APK → app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:lintDebug          # Android Lint
./gradlew :app:testDebugUnitTest  # JVM unit tests
```

On Windows, use `gradlew.bat`. Those three tasks are the build gate. The service, the overlay and
the permission flows are verified on a device or emulator as well, since JVM tests can't reach them.

---

## Contributing

[`AGENTS.md`](AGENTS.md) is the design doc. It covers the architecture and conventions, the reasoning
behind the non-obvious decisions, and the app's current state.

---

## License

MIT. See [`LICENSE`](LICENSE).
