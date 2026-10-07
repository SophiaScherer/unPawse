# internetGuard

## What was built
A Gradle check, `checkNoNetwork<Variant>`, registered for every variant in `app/build.gradle.kts`. It parses the variant's merged manifest (`SingleArtifact.MERGED_MANIFEST`) and fails if any of these come back:
- `android.permission.INTERNET` or `android.permission.ACCESS_NETWORK_STATE` (as `uses-permission` or `uses-permission-sdk-23`);
- the `com.google.android.datatransport.runtime.backends.TransportBackendDiscovery` service.

`assemble<Variant>` and `lint<Variant>` depend on it, so the local gate (`assembleDebug lintDebug testDebugUnitTest`) and CI (`build`) both run it. `check` isn't used, because the gate never runs it.

## Why
The privacy policy (#31) promises no network access. ML Kit pulls both permissions and the telemetry backend in transitively, and only `tools:node="remove"` entries in `AndroidManifest.xml` keep them out. A dependency bump that renames or adds a component would silently break the promise, and nothing visible would change.

## Key decisions
- It parses XML instead of matching substrings, so a permission name in a comment or another attribute can't trip it or hide from it.
- It checks the merged manifest, not the source manifest, because the merged one is what ships.
- The check writes a small report file as its output, so it's up to date when the manifest hasn't changed.

## How to verify
- `./gradlew :app:checkNoNetworkDebug :app:checkNoNetworkRelease --no-daemon`: both pass and write `app/build/reports/noNetwork/<variant>.txt`.
- Negative check (done once by hand, not committed): delete the `tools:node="remove"` from the INTERNET entry and from the `TransportBackendDiscovery` service, then run `:app:assembleDebug`. It fails with `Merged manifest reintroduces network access: android.permission.INTERNET, com.google.android.datatransport.runtime.backends.TransportBackendDiscovery`.
