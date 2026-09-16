# unPawse

A cat-themed screen-time manager for Android. You give the apps that eat your day a daily budget;
when one runs out, unPawse draws a block over it and asks you to go photograph a cat.

**The loop:** pick apps and limits → a foreground service counts the time you actually spend in them
→ hitting a limit blocks the app → photographing a real cat (verified on-device) credits bonus
minutes and lets you back in.

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

---

## License

MIT. See [`LICENSE`](LICENSE).
