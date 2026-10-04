# Block overlay layout

## What was built

- The block overlay (`ui/block/BlockOverlayScreen.kt`) keeps "Open Camera" and "Exit App" whole and tappable on any screen size, orientation and font scale (audit UX-06 / A11Y-01). Back is swallowed on this window, so those buttons are the only way out and the only way to earn time back.
- **Portrait** (`StackedCard`): the chip, photo, copy and reward pills scroll in a `weight(1f, fill = false)` region; the buttons are measured outside it. Below 640dp of height the footer joins the scroll and the gap above the buttons halves.
- **Wider than tall** (`TwoPaneCard`): copy on the left, starting at the headline, scrolling with the pills and footer; photo and buttons on the right. The photo takes what the buttons leave, capped at 180dp, and is dropped below 64dp. The card is capped at 720dp wide.
- The photo in portrait is `blockHeroSize(availableHeight, fontScale)`: the height the rest of the card leaves, clamped to 96–180dp. A 411×914dp phone at 100% renders exactly as before.
- The scroll region fades out at any edge it can still scroll past; each fade is at most a quarter of the viewport so they never meet.
- The scroll state is keyed on the portrait/landscape switch: a rotation reopens at the headline, but inset changes from transient system bars don't reset it.

## Key decisions

- **Buttons outside the scroll region**, not a fully scrolling card: a whole-card scroll would let the only escape scroll off-screen.
- **Two panes rather than a shorter stack in landscape**: with ~300dp of height a stack leaves the copy 0–75dp, so the user can't read why they are blocked over a fullscreen video.
- **Footer pinned on tall portrait screens** so the normal phone layout is unchanged; it scrolls only where height is short.
- **The photo is the element that gives up room** before the copy has to scroll; it is decorative, the copy is not.
- `blockHeroSize` scales its text reserve linearly with `fontScale`. Android 14+ scales large fonts non-linearly, so at 1.3 the photo comes out ~156dp where 180 would fit. That errs toward more room for the copy, so it was left as is.
- The dead in-app `Routes.BLOCK` debug destination was left in place for the navigation cleanup (audit CQ-08).

## How to verify

- `./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest --no-daemon -Pkotlin.compiler.execution.strategy=in-process` (`BlockHeroSizeTest`).
- `./gradlew :app:connectedDebugAndroidTest --no-daemon -Pkotlin.compiler.execution.strategy=in-process` on a device (`BlockOverlayLayoutTest`: 360×592dp at 2×, 640×300dp at 1× and 2×). CI only compiles androidTest (`:app:assembleDebugAndroidTest`); it has no emulator.
- Device:
  - Grant `GET_USAGE_STATS` and `SYSTEM_ALERT_WINDOW` via `appops`.
  - Seed Chrome past a 30m limit with the AGENTS.md `run-as` recipe, launch unPawse once, then open Chrome with `am start`. `monkey` thaws a rotation lock, so don't launch with it.
  - With the overlay up, change settings live and screenshot each:
    - `wm user-rotation -d 0 lock 0|1`
    - `wm size 720x1280` + `wm density 320`
    - `settings put system font_scale 2.0`
    - `cmd uimode night yes|no`
  - Check:
    - Both buttons are whole.
    - The headline is visible.
    - Swiping the copy reaches the pills and the footer.
    - "Exit App" goes home and "Open Camera" opens the camera.
    - Back stays swallowed.
- Restore: `wm size reset`, `wm density reset`, `font_scale 1.0`, `wm user-rotation -d 0 free`, `user_rotation 0`, `accelerometer_rotation 1`.
