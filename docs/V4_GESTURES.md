# V4 advanced gestures — hardware-tested V4

## Implementation

TouchpadGestures is a deterministic pure Kotlin state machine: IDLE, SINGLE_TAP, TWO_TAP, SECOND_TAP, DRAG and MOTION. It consumes pointer-ID snapshots and monotonic event times; it emits only ordered button transitions. TouchpadView supplies Android MotionEvents and schedules the second-touch hold deadline. Bluetooth transmission remains on HidManager's existing serial executor.

- A single stationary main-area tap sends left press/release on lift.
- A stationary two-finger main-area tap sends right press/release only after all fingers lift. Staggered lifts are supported. Crossing touch slop, adding a third distinct finger, elapsed tap time or adding a finger in the edge strip cancels the tap candidate; two-finger scrolling remains available.
- A qualifying first tap clicks immediately. A nearby second touch within Android's double-tap interval presses left on hold, or when movement exceeds touch slop. Motion then carries left held until lift. A quickly released stationary second touch sends the second ordinary click. Adding another finger during drag releases left before transitioning to motion/scrolling.
- A touch starting in the right-edge strip never becomes a tap or double-tap drag. V3 edge-mode locking remains unchanged; scroll accumulation/scheduling are refined in the scroll optimization notes.

Thresholds: device ViewConfiguration scaled touch slop and double-tap slop converted to dp; tap duration capped at 250ms (or the shorter Android long-press timeout); double-tap interval from Android; second-touch hold threshold from Android tap timeout. Tests use explicit deterministic values. No inertia, drag lock after lift, horizontal scrolling or extra controls added.

Tap candidates suppress pointer and wheel output until recognized or rejected, including tiny finger jitter. On transition to movement, accumulated displacement starts from the prior motion baseline. Historical samples and pointer membership changes are handled. Pending drag movement is flushed before button-up; cancellation first discards pending movement and cancels the hold timer. Disable, detach and activity stop cancel gestures; disconnected/failed HID sessions clear all local button owners and neutralize new connections.

## Buttons and compatibility

The existing descriptor, report length, report IDs, protocol adaptation, registration and pairing/connection logic are unchanged. Left is bit 0 (mask 1), right is bit 1 (mask 2), as already declared by the descriptor. MouseButtons now tracks physical-button and gesture ownership independently. Releasing one owner preserves another owner's held button. Manager disconnect/shutdown/report-failure reset clears all owners.

The physical left-click button cancels/suppresses gesture recognition for the current touch stream; pointer and scrolling input continue so existing button-and-touchpad dragging still works. The manager also suppresses gesture presses while the physical button is held. A new touch stream can recognize taps again after the physical button is released. No native touchpad click listener sends duplicate clicks.

No dependencies, permissions, minimum SDK or HID descriptor changes; Android 9/API 28 remains supported. No re-pairing is expected for this update. Stable V3 is recoverable via local annotated tag `bluetooth-touchpad-working-v3`; this work is approved for a local commit and annotated tag `bluetooth-touchpad-working-v4`, with no push.

## Hardware status

The user reports all six V4 hardware test categories passed on Vivo/Ubuntu. The user also reports final scroll optimization passed: both scrolling methods are smoother, speed is comfortable, and small movements/reversals work. Precision-touchpad smoothness remains an accepted non-blocking limitation; see [scroll optimization](V4_SCROLL_OPTIMIZATION.md).

## Verification

`testDebugUnitTest lintDebug assembleDebug`: PASS. All **48 tests** pass: 18 recognizer tests, 14 existing motion/scrolling regression tests, 8 HID/button tests and 8 scrolling responsiveness tests. Coverage includes single tap versus movement/duration, exact slop/time boundaries, strip exclusion, two-finger tap versus scroll, staggered lifts, extra/distinct fingers, double-click, timed and moving double-tap drag, distant/late taps, cancellation and stale timers, button ownership/order and simulated disconnect while dragging.

Lint: zero errors, one existing newer-Gradle notice. Debug APK build and signature verification pass. Build log: `/tmp/bluepad-v4-closeout.log`. APK: `app/build/outputs/apk/debug/app-debug.apk`.

Automated disconnect testing exercises the local button-reset and recognizer-cancellation contract; it is not an Android Bluetooth integration test. Tests do not prove real MotionEvent delivery, UI timer scheduling, Bluetooth delivery, desktop double-click interpretation or device compatibility.

## Risks and limitations

The user reports final V4 gestures and optimized scrolling passed on Vivo/Ubuntu. Other hardware combinations remain unverified. Tap classification deliberately holds sub-slop motion briefly; compare perceived responsiveness with V3. Host double-click settings and Bluetooth latency affect whether two ordinary clicks are interpreted as a double-click. Thresholds need physical validation, especially slow scrolling and staggered two-finger taps. Radio loss/process termination cannot guarantee release delivery, though local state is cleared and reconnections start neutral. Boot-mode hosts still lack wheel scrolling, as in V3.

## Vivo / Ubuntu hardware checklist

1. Install the debug APK, reconnect, and compare normal movement/sensitivity and slow/fast motion against V3.
2. Tap once in the main area: one left click. Move beyond touch slop or hold without movement: no tap click. Repeat near the strip boundary; strip taps must not click, and crossing the boundary must retain V3 mode locking.
3. Make simultaneous and slightly staggered two-finger taps: one right-click/context menu, without pointer movement, scrolling or an extra left click. Repeat near edges and with either finger lifting first.
4. Turn the two-finger candidate into a vertical swipe: scrolling, no click. Check small/reversed movements, edge-strip scrolling and three-finger cancellation.
5. Double-tap quickly: ordinary double-click. Tap then hold the second touch: drag selection/window movement; release stops dragging. Also test moving promptly on the second touch and adding another finger mid-drag.
6. During a drag, interrupt with Home, screen lock, rotation, Bluetooth disable, host disconnect or touch cancellation. Reopen/reconnect and verify no stuck button, delayed click, movement or stale timer.
7. Hold the physical Left click button and move the touchpad: existing drag still works without duplicate gesture clicks. Release normally and via interruption, then verify gestures work on a new touch stream.
8. Verify sensitivity persistence and normal reconnect behavior. No pairing reset should be needed because the descriptor is unchanged.

Use the existing Android log capture in HARDWARE_INVESTIGATION.md. BluePadTouch diagnostics include recognizer phase changes and button mask/down transitions; BluePadHid logs report bytes and send acceptance. Preserve short traces if a regression appears. Record phone Android version and Ubuntu click settings with results.
