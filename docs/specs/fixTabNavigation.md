# fixTabNavigation

Fixes "the X button does nothing" and the rest of the navigation cluster from the audit: UX-15, UX-16, UX-09, UX-08, VIS-01, VIS-02 and the dead route from CQ-08. Stacked on `feat/onboarding` (#29).

## What was built

- **Tab ownership** (`ui/navigation/TabNavigation.kt`). `owningTab(route)` is a pure function naming the one tab each route belongs to: App Picker, Schedules, Photo storage and Privacy belong to Settings; the photo viewer to Gallery; the tour to none.
  - The bottom bar highlights the owner of the current route, so sub-screens highlight their tab.
  - `navigateToTab` pops to the tab's root when the tab is already current (re-tap), and otherwise switches with saved state as before.
  - `navigateWithinTab(route)` opens a sub-screen inside its owner. On a cross-tab jump it switches to the owner and starts from its root, so Home's "Edit Limits" and Stats' "Details" both land on the single App Picker inside Settings.
- **Tab headers.** Settings uses `ScreenHeader` (no back arrow) like the other tabs. Stats, Gallery and Settings are titled by name; Home keeps its greeting. The Gallery icon is filled when selected.
- **Camera.** The gear is gone; the X is "Close camera" and goes Home. The bottom bar is hidden on the camera in landscape (`showsBottomBar`).
- **Deep link.** `MainActivity` is `singleTop`; the service's "Open Camera" intent drops `CLEAR_TOP`. The request is read only when `savedInstanceState == null` or from `onNewIntent`, held as Compose state until `UnPawseApp` has navigated, then cleared. `openDeepLink` steps out of an open tour first without marking it done.
- **Dead code.** `Routes.BLOCK`, its composable and `SampleData` are removed.
- `UnPawseScaffold` (bar + graph) is split out of `UnPawseApp` so tests can drive it with their own controller.

## Key decisions

- **Root cause:** Home is the `popUpTo` root of every tab, and Navigation maps the saved state of whatever is popped above it to Home. App Picker pushed onto Home was therefore restored by every later Home tap and by the Camera's X. Home must never own a sub-screen; ownership routing guarantees that.
- **Flat graph plus an ownership function**, not nested graphs per tab. Nested graphs map the start tab's saved state the same way, so they would not have fixed this on their own, and the function is testable on the JVM.
- **Cross-tab sub-screens open in their owner.** Back from Stats → Details returns to Settings, not Stats. Accepted in exchange for one App Picker and coherent stacks.
- **The camera's X stays on the tab** rather than only appearing after a block. It is the way out of a landscape camera without the bar, and it means what system back means.

## How to verify

```bash
./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest --no-daemon -Pkotlin.compiler.execution.strategy=in-process
./gradlew :app:connectedDebugAndroidTest --no-daemon -Pkotlin.compiler.execution.strategy=in-process   # TabNavigationTest
```

- JVM: `TabOwnershipTest` (every route has an owner, by reflection over `Routes`), `BottomBarVisibilityTest`.
- Instrumented: `TabNavigationTest` drives the real bar and graph with a `TestNavHostController`.

Device steps:

1. Home → Edit Limits (App Picker, Settings highlighted) → Camera tab → X: lands on Home, Home highlighted.
2. Revoke usage access (`adb shell appops set com.example.unpawse GET_USAGE_STATS deny`), Home → Pause Protection card → Settings → system back: lands on Home.
3. Settings → Individual app limits → tap Settings: back at the Settings root.
4. Stats → Details: App Picker inside Settings; back: Settings root.
5. Rotate to landscape on the Camera tab: no bottom bar.
6. `adb shell am start -n com.example.unpawse/.MainActivity --ez com.example.unpawse.extra.OPEN_CAMERA true` while on Stats: lands on Camera without recreating the activity; rotating afterwards stays put.
