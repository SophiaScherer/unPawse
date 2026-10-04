# Onboarding

## What was built

- A first-run tour (`ui/onboarding/`): welcome, how it works, name, cat avatar, usage access, display over other apps, camera, notifications, and a closing recap that lists whatever is still off.
- It is the `NavHost` start destination until `SettingsRepository.onboardingComplete` is set; Settings → "Replay the intro" pushes it again on top of Settings.
- Eight Canvas-drawn cat avatars (`CatAvatar`, `ProfileAvatar`), stored as an `Int` id, shown in the Home, Stats, Gallery and Settings headers. With no cat picked (or an id this build can't draw), the header shows the name's initial.

## Key decisions

- **Every step is skippable.** Permissions only change what a step says and which button it offers, never whether the user can move on. The recap names each missing permission with a one-line consequence.
- **Re-read on every resume.** Usage access and the overlay permission are granted in system Settings, which reports nothing back. The route re-reads all four permissions in `onResume`, and `stepAfterGrant` advances only the step whose own permission just arrived.
- **Saved state, including the grants.** The step, the name draft and the last-seen grants live in a `SavedStateHandle`, so the tour survives Android killing the process while the user is in Settings. Saving the grants means a switch flipped while the process was dead still advances the step.
- **Skip leaves the stored value alone; Continue uses the field.** Once the name or avatar step has an answer, Skip disappears, so the two buttons never differ in a way the user can't see.
- **One `UserProfile` flow** (name + avatar id), so screens already at the five-flow `combine` limit can show the avatar without using another slot.
- **Import keeps the tour done; Delete all data replays it.** Import reuses the full wipe (which clears every preference), then sets the flag again right away. Delete all data opens the tour immediately, since its dialog promises a return to install day.
- **Layout holds at a small screen and 200% font.** Short steps sit vertically centered, the buttons stay above the keyboard, the hero shrinks below a 480dp viewport, a fade marks content that scrolls, and the main button grows taller instead of clipping its label.

## How to verify

```bash
./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest --no-daemon -Pkotlin.compiler.execution.strategy=in-process
```

- Unit tests: `OnboardingViewModelTest` (next/skip/back, resume advance, name draft, saved-state restore including a grant made while the process was dead), `OnboardingStepTest`, `OnboardingMapperTest`, `SettingsRepositoryTest`, and `ImportRepositoryTest` ("an import does not send the user back through onboarding").
- Device steps:
  1. `adb shell pm clear com.example.unpawse`, then launch. The app opens on "Step 1 of 9".
  2. On the name step, type a name and press the keyboard's Done key. The tour moves to the avatar step, and Skip is gone.
  3. Pick a cat and finish the tour. Home, Stats, Gallery and Settings all show that cat.
  4. Process death: `adb shell appops set com.example.unpawse SYSTEM_ALERT_WINDOW deny`, reach the overlay step, then tap "Open Settings". Kill the app with `adb shell run-as com.example.unpawse kill -9 <pid>` (`am kill` did not kill it on API 36). Grant the permission and press back. The tour reopens on the camera step.
  5. Settings → Delete all data → confirm. The tour opens at step 1.
  6. Small screen and large text: run `wm size 720x1280`, `wm density 320` and `settings put system font_scale 2.0`. Every step scrolls, and its buttons stay on screen. Undo all three afterwards.
