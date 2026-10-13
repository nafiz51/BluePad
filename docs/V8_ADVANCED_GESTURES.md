# V8 scroll controls and cursor responsiveness

Status: stable local checkpoint, annotated tag `bluetooth-touchpad-keyboard-working-v8`. The user completed real-device testing on Vivo Android and Ubuntu and confirmed all four final verification checks passed, including restored cursor smoothness. Stable V7 remains at `fdc11af266eced9b79da90dd26500a53143a238a`, tag `bluetooth-touchpad-keyboard-working-v7`. The accepted V6 limitation with natural very fast double-taps on Ubuntu remains unchanged.

## Final feature set and hardware verification

V8 preserves two-finger tap for right-click, natural/traditional scrolling, independent 0.5×–3× scrolling sensitivity, advanced Zoom Compatibility with automatic migration of previous OS preferences, restored V7-level cursor smoothness, and V7 Bluetooth reliability and existing features. All four final Vivo/Ubuntu verification checks passed as reported by the user. Three-finger gestures and the general OS selector are absent. This result applies to the tested Vivo/Ubuntu combination; Windows and macOS are not hardware-verified.

## Removed functionality

All three-finger recognizers, ownership routing, swipe/tap actions, assignments, desktop presets, custom-shortcut parsing/transmission, haptics and corresponding settings/tests have been removed. Standard two-finger tap still produces right-click through V7's existing paced button queue. There is no gesture action engine retained without a purpose. Touches with more than two fingers follow V7's existing suppression/baseline behavior; they have no host actions.

MouseReports remains byte-for-byte V7. HidManager only renames the zoom compatibility parameter/type; report dispatch and connection handling remain unchanged. No changes to Bluetooth registration, connection/retry handling, keyboard/mouse descriptors, report IDs or formats, input-session fencing or host pairing are needed. MainActivity adds scroll settings and replaces the general OS selector with advanced Zoom Compatibility. Its focus behavior is V7 again; there is no shortcut text editor.

## Cursor pipeline audit

Compared directly with stable V7, the previous V8 revision called ThreeFingerGesture.sample on every touch sample, even when only one finger was present. The view also constructed a temporary list of terminal action types for every sample, and a phase list during eligible initial samples. Historical MOVE samples repeated this work. Ownership routing added branches and pointer-set construction when claimed. These are verified extra operations and allocation opportunities; additional GC/main-thread pressure is a plausible contributor to the perceived lag, not a measured hardware root cause.

There was no added single-finger recognition timeout, movement threshold, frame delay or HID pacing delay in V8. No Android performance trace or synchronized touch/host timing establishes a latency increase in milliseconds, or rules out Bluetooth/host variation.

After removal, single-finger recognition, slop, fractional X/Y accumulation, history processing, pointer baselines, frame coalescing, overflow draining, timer scheduling and HID report dispatch use the V7 path. The one added arbitration condition immediately rejects single-finger input by pointer count; it only suppresses tap candidates after a two-finger scroll is explicitly classified. This retains the V8 fix for a right-click at the scroll/slop boundary without adding cursor delays or changing zoom/drag timing. There is no new per-cursor-sample recognizer, action mapping, timer, preference read, report scheduler or HID report. Cursor X/Y arithmetic in TouchpadMotion is unchanged; scroll scaling executes only in scroll paths.

## Retained controls

Settings contains Scrolling: natural/traditional direction, independent 0.5×–3× sensitivity in 0.1× increments (default 1×), and Restore scroll defaults. Traditional retains V7's finger-up → positive wheel direction; natural reverses it. Host scrolling settings also affect the final perceived direction. Both two-finger vertical scrolling and the locked 12% right-edge strip use these controls, with fractional accumulation and bounded overflow-preserving reports.

Existing installed V8 values are preserved using the same `gestures_v8` preference file and `natural`/`scroll` string keys. Obsolete gesture keys are ignored and never executed; the app does not clear unrelated preferences. No-op settings updates do not reset input. Actual changes cancel an active stream before updating direction/sensitivity to prevent stale accumulated motion. Pointer sensitivity (0.5×–8×), Zoom Compatibility settings, keyboard, dedicated buttons and V7 connection preferences are independent.

Horizontal wheel scrolling remains unavailable because the existing four-byte mouse report has no horizontal wheel usage. No descriptor migration or re-pairing is introduced. Windows/macOS and Android 9 hardware compatibility remain independently unverified.

## Current changed files versus V7

- `README.md`
- `app/src/main/java/com/bluepad/MainActivity.kt`
- `app/src/main/java/com/bluepad/bluetooth/HidManager.kt`
- `app/src/main/java/com/bluepad/hid/ZoomReports.kt`
- `app/src/main/java/com/bluepad/hid/ZoomCompatibility.kt` (new)
- `app/src/test/java/com/bluepad/hid/ZoomReportsTest.kt`
- `app/src/test/java/com/bluepad/hid/ZoomCompatibilityTest.kt` (new)
- `app/src/main/java/com/bluepad/ui/TouchpadGestures.kt`
- `app/src/main/java/com/bluepad/ui/TouchpadMotion.kt`
- `app/src/main/java/com/bluepad/ui/TouchpadView.kt`
- `app/src/main/java/com/bluepad/ui/ScrollSettings.kt` (new)
- `app/src/main/java/com/bluepad/ui/ScrollSettingsView.kt` (new)
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/com/bluepad/ui/V8GestureRegressionTest.kt` (new)
- `docs/V8_ADVANCED_GESTURES.md` (new)

## Zoom Compatibility simplification

Basic touchpad and keyboard functions use standard HID reports with no OS selection. Settings now contains **Advanced: Zoom Compatibility**:

- **Standard** (default): Ctrl + mouse wheel.
- **macOS compatibility**: Command + plus/minus (US HID mapping).

This setting changes only the existing zoom report choice; gesture recognition, cursor processing, scrolling, button timing, zoom pacing and transport sequencing are untouched. The cursor pipeline files were hash-compared before and after this change and are identical.

Migration uses `touchpad.zoom_compatibility`. If absent, the old `host_os=MACOS` selects macOS compatibility; old Windows/Linux, missing or invalid legacy values select Standard. The resolved new value is saved and the obsolete `host_os` key removed in the same SharedPreferences edit. An explicit new setting takes precedence, including Standard on a previously macOS-configured install. Invalid new values fall back to Standard. Both choices persist across restarts. No settings/data clearing or Bluetooth re-pairing is required.

Application zoom shortcuts and host keyboard layouts may differ; macOS compatibility does not imply macOS hardware verification. Older milestone documentation describes the former selector historically, rather than current controls.

## Hardware regression procedure

```sh
/tmp/bluepad-sdk/platform-tools/adb install -r /home/nafiz51/dev/BluePad/app/build/outputs/apk/debug/app-debug.apk
```

Retain existing data and bonds. Confirm the former OS selector is absent, Standard zoom works on Ubuntu, the selected compatibility persists after restart, and existing macOS-configured installs migrate to macOS compatibility. Verify no change in cursor smoothness. At the same pointer sensitivity used for V7, compare slow diagonals, tiny movements, fast circles and long swipes, with IME hidden and visible. Look for cursor delay, stutter and loss of horizontal/vertical precision. Automated tests cannot certify perceived smoothness.

Test two-finger right-click, single tap, moderate double-click, double-tap hold/drag, browser-tab dragging and both dedicated button holds. Three-finger contacts must produce no overview, shortcut or click actions. Verify vertical scrolling versus pinch classification and no right-click on scroll completion.

Test both scroll methods at 0.5×/1×/3×, both directions, tiny movement, reversals and continuous swipes. Confirm pointer/zoom sensitivity is independent, the edge strip still locks at 12%, settings survive restart, and Restore scroll defaults preserves pointer sensitivity and connection settings.

Cancel/background/lock/disconnect during drag, pinch, queued clicks and typing. Reconnect, including toggling Ubuntu Bluetooth: no stale clicks/keys/modifiers and no stuck buttons. V7 recovery and input-session fencing must remain functional. The final Vivo/Ubuntu hardware verification passed; this procedure remains available for future regression testing.

## Automated verification

170 unit tests passed (158 baseline tests plus eight V8 scroll/cursor regressions and four zoom-migration tests), with zero failures, errors or skips. Migration tests cover saved macOS/Windows-Linux selections, missing/invalid values, new-setting precedence, restart round-trips and exact zoom reports. Removed tests covered only removed V8 gesture infrastructure. Retained baseline tests cover pinch/zoom, double-tap dragging, cancellation, dedicated buttons, Bluetooth recovery and input-session fencing. Added checks cover first-slop-crossing cursor motion without a tap-timer wait, fractional/batched cursor invariance across all scroll preferences, standard right-click ordering, scroll-versus-tap/zoom arbitration, direction/sensitivity and saved preference recovery.

Lint passed with zero errors and only the existing AndroidGradlePluginVersion warning. Debug APK build passed, and apksigner verified the v2 signature with one signer. All gates ran sequentially offline, with one worker, a 768 MiB Gradle heap and in-process Kotlin compilation. Logs: `/tmp/bluepad-v8-compat-tests.log`, `/tmp/bluepad-v8-compat-lint.log`, `/tmp/bluepad-v8-compat-apk.log`.

APK: `/home/nafiz51/dev/BluePad/app/build/outputs/apk/debug/app-debug.apk`. V7 and earlier checkpoints are preserved. No generated artifacts or unrelated chat exports are included in the V8 checkpoint. Hardware verification is recorded above; no Windows or macOS hardware verification is claimed.
