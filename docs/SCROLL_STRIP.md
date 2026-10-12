# Right-edge scrolling strip — working V3

The far-right 10% of TouchpadView is now a one-finger vertical scrolling zone, shown with a faint tint and a one-dp separator. The exact 90%-width boundary belongs to the strip. This is the physical right edge, independent of layout direction.

The first finger's initial position selects one-finger scroll or pointer mode for the whole gesture. Crossing the separator never switches modes. Two fingers always scroll anywhere. When one finger remains, the original gesture's one-finger mode resumes, even if the original finger lifted. Finger membership changes rebase position to avoid jumps. Mode unlocks only when all fingers lift or on cancellation/disable/detach.

Strip scrolling uses the same density-independent 24dp per wheel tick, fractional accumulation and display-frame batching as two-finger scrolling. Upward swipes send positive wheel ticks (scroll up); downward swipes send negative ticks. Horizontal strip motion is ignored. Pointer sensitivity affects cursor movement only.

Changed implementation: TouchpadMotion zone/mode logic, TouchpadView drawing and width input, touchpad instruction string. Existing left-click control is unchanged. Bluetooth/HidManager, HID descriptor and report encoding are unchanged; no re-pairing is expected for this UI-only change. Android 9/API 28 minimum remains unchanged; no dependency added.

## Hardware results

The user reports successful V3 testing on a real Vivo phone and Ubuntu laptop: right-edge one-finger scrolling, normal cursor movement, two-finger scrolling and existing touchpad features work, with no additional issues observed. These are user-reported results for this combination; other devices/hosts remain unverified.

## Verification

`--rerun-tasks testDebugUnitTest lintDebug assembleDebug` passed using the existing offline Gradle/SDK setup. All 21 tests passed (14 touchpad, 7 HID), with no failures/errors/skips. Eight new tests cover both scrolling directions, exact boundary inclusion at multiple widths, both boundary crossing directions, two-finger transitions and original-finger lift, fractional scrolling independent of sensitivity, cancellation and new-gesture mode selection. Lint: zero errors, one existing newer-Gradle notice. Debug APK signature verified. No physical-device test of the strip has been performed by the agent.

APK: `app/build/outputs/apk/debug/app-debug.apk`. Closeout build log: `/tmp/bluepad-v3-verification.log`. The working v2 remains recoverable via local annotated tag `bluetooth-touchpad-working-v2`. The user approved the V3 source/tests/documentation commit and local annotated tag `bluetooth-touchpad-working-v3`. Chat exports, generated APKs and build artifacts are excluded. Nothing is pushed.

## Vivo / Ubuntu checklist

1. Install the new debug APK, reconnect normally, and confirm the strip and separator appear along the far-right 10% of the pad.
2. Start inside the strip; swipe up/down slowly and quickly. Confirm vertical scrolling in the correct direction without cursor movement; horizontal-only swipes should produce neither cursor movement nor scrolling.
3. Start inside the strip and cross leftward while swiping vertically; confirm scrolling continues. Start outside and cross rightward; confirm cursor movement continues without scrolling.
4. Test starts immediately on both sides of the separator. The boundary itself selects scrolling. Lift fully and start again on the opposite side to confirm mode selection resets.
5. Scroll with two fingers in the center and across the strip. Add/lift either finger; verify no cursor jumps and that one-finger behavior returns to the original touch-down mode.
6. Test tiny successive vertical movements and reversals, then larger swipes. Confirm fractional accumulation feels predictable and changing pointer sensitivity does not alter scroll speed.
7. Cancel/background/disconnect during an edge gesture and reconnect. Verify no delayed wheel/cursor movement remains. Test normal pointer movement, left click and click-and-drag outside the strip.
8. Repeat in portrait/landscape if rotation is enabled. Confirm width adapts, the separator matches the active zone, and the normal foreground reconnect behavior is preserved.
