# Touchpad update — working v2

The user confirmed the updated version works on a real Vivo phone and Ubuntu laptop: Bluetooth connection, cursor movement, adjustable sensitivity, left click and two-finger vertical scrolling. Scrolling worked after refreshing Bluetooth pairing and HID service recognition. These are user-reported physical test results; no broader compatibility claim is made. This update does not assume that the earlier descriptor mismatch persists. Historical Milestone 1 and investigation documents describe their earlier verification state.

## Changes

- Pointer sensitivity: one labeled native slider, 0.5×–3× in 0.1× increments, default 1×. Saved locally after slider release and when the activity stops; restored when reopened. No permissions/dependencies added.
- TouchpadMotion: pure Kotlin, density-independent fractional accumulation, sensitivity scaling and pointer-membership rebasing. Samples accumulate until the display frame; HID-sized chunks retain fast-motion overflow instead of losing it. No artificial smoothing delay, acceleration or inertia.
- TouchpadView: consumes historical motion samples, emits at most one cursor delta and one wheel delta per display frame, cancels pending callbacks/residuals on cancellation, disable or detach. One finger moves the pointer; two fingers scroll using average vertical displacement, one standard wheel tick per 24dp. Finger-up/down transitions rebase positions without jumps. Three or more fingers do not generate motion. Lifting one finger resumes single-finger movement from its current position. LeftClickButton remains unchanged.
- MouseReports: descriptor now includes Generic Desktop Wheel (usage 0x38), signed relative [-127,127], alongside X/Y. No Report ID or keyboard added. Unnumbered report length changes from 3 to 4: `[buttons, X, Y, wheel]`. Neutral/press/release reports include a zero wheel. Scrolling preserves left-button state.
- HidManager: registration/connection/executor/error-recovery lifecycle is preserved. Report-mode reports still go through `sendReport(host, 0, payload)`. GET_REPORT and cleanup use matching lengths. SET_PROTOCOL is tracked: report mode sends four bytes; boot mode sends the three-byte movement/click payload with Bluetooth Classic boot mouse fixed ID 2 and suppresses wheel input. Protocol resets after disconnect/unplug/close.

Frame batching is intended to reduce Bluetooth work backlog while retaining small deltas. It adds at most approximately one display frame before submitting ordinary input, plus any Bluetooth scheduling/delivery latency. Overflow from very fast swipes drains across later frames. Smoothness and responsiveness improvements require subjective physical testing; unit tests do not prove them.

Bluetooth Classic boot framing follows the [Bluetooth HID boot mouse definition](https://www.bluetooth.com/wp-content/uploads/2023/08/HID-Lite-WP-V10.pdf); Android forwards the boot report ID ([stack source](https://android.googlesource.com/platform/packages/modules/Bluetooth/+/refs/heads/main/system/bta/hd/bta_hd_act.cc)).

The wheel layout follows the [Linux HID mouse descriptor example](https://docs.kernel.org/hid/hidintro.html); the existing [Android HID API](https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice) remains the transport.

## Verification

Command using existing temporary tooling:

```
GRADLE_USER_HOME=/tmp/bluepad-gradle-home /tmp/bluepad-tools/gradle-8.13/bin/gradle --offline --rerun-tasks :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --console=plain
```

13 tests: 7 HID tests, 6 pure touchpad tests. They cover descriptor semantics and 32-bit report length, signed/clamped wheel, scroll button preservation, boot/report adaptation, sensitivity/fractional accumulation, frame coalescing/overflow, two-finger direction and cursor suppression, finger lifting, extra fingers and cancellation, and invalid sensitivity. Tests/lint/debug build pass; lint has zero errors and one pre-existing Gradle-version notice. No new runtime dependencies; minimum API remains 28.

APK: `app/build/outputs/apk/debug/app-debug.apk`. Release verification reran all tasks; build transcript: `/tmp/bluepad-v2-verification.log`. The user reported successful physical testing of this update on Vivo/Ubuntu. API 28 hardware, boot-mode hosts and other desktop operating systems remain unverified.

## Reconnect and additional hardware testing

This is an intentional descriptor change. Install the new APK, return to BluePad and reconnect to Ubuntu so registration/connection use the new layout. If Ubuntu caches the preceding descriptor, mouse or scroll decoding can be incorrect; check its descriptor and remove/re-pair the bond on both sides only if needed. Re-pairing may also be required when reverting to the old APK. Keep BluePad visible during input because registration remains foreground-only. No existing pairing is removed automatically.

1. Check pointer movement at default 1×, then 0.5× and 3×; compare slow diagonal/subpixel motion, quick swipes and perceived latency with the preceding version. Reopen to verify the setting persists.
2. Verify normal clicks and click-and-drag using the unchanged Left click button. Check release/cancellation and reconnect recovery.
3. In a long document/browser page, move two fingers up/down: expect vertical scrolling and no pointer movement. Test slow movement below one tick, fast movement, horizontal-only two-finger motion and direction reversal.
4. Add/lift either finger; verify no cursor jumps, then smooth resumed one-finger movement. Cancel/background/rotate/disconnect during a gesture; confirm no movement/scroll continues afterward.
5. Confirm Ubuntu negotiated the four-byte unnumbered report with wheel usage and emits REL_WHEEL events. Existing opt-in logging remains available, now with four-byte report-mode payloads. Boot-mode hosts should retain movement/clicks but cannot scroll.
6. Test API 28 on hardware if the Vivo does not run Android 9. Build and lint establish API compatibility only; Windows/macOS behavior remains unverified.

## Recovery

The preceding source baseline is `70762e045ea8278a04ea089639681f8700a4f2e0`.

Local ignored backups created before edits:

- `artifacts/bluepad-before-touchpad-update.apk`: previous built diagnostic APK (built from the clean baseline source; not an APK extracted from the phone).
- `artifacts/bluepad-before-touchpad-update-source.zip`: archive of the baseline commit.

To revert installed behavior, install the backup APK with `adb install -r artifacts/bluepad-before-touchpad-update.apk`, then reconnect/re-pair if the host retains the newer descriptor. To recover source without discarding review changes, extract the source archive into a separate directory. Backups are ignored by Git; the unrelated Markdown file is preserved. The user approved committing the verified source/tests/documentation and creating the local annotated tag `bluetooth-touchpad-working-v2`. Build artifacts and chat exports are excluded; nothing is pushed.
