# V4 scrolling responsiveness — hardware-tested V4

The user reports all six V4 gesture categories passed on Vivo/Ubuntu, including interruption cleanup and V3 regressions. Scrolling works but feels laggy. The user also confirms final optimized scrolling is smoother in both modes, the speed is comfortable, and small movements/reversals work. Laptop precision-touchpad smoothness remains an accepted non-blocking V4 limitation; higher-resolution scrolling is deferred to a future milestone.

This update changes only scroll accumulation, frame scheduling, recognized-motion processing and optional queue diagnostics; it is approved for V4 closeout.

## Findings

The strongest source-level explanation is wheel quantization shared by V3 and V4: one integer HID wheel tick per 24dp. At 120dp/s this produces approximately one tick every 200ms before frame scheduling, Bluetooth delivery and Ubuntu/application processing. At very slow movement, more delay is unavoidable with integer wheel ticks. No measured Bluetooth trace establishes radio/host latency as the cause.

A second demonstrable issue is sample-dependent Float accumulation: splitting exactly 24dp into ten float-coordinate samples can accumulate 0.99999994 ticks, which truncates to zero; the same distance in one sample emits a tick. This can delay a boundary tick until another event arrives. The defect existed before V4.

V4 adds a touch-slop/time gate while a two-finger tap is possible. It is not a fixed 250ms wait: movement beyond slop immediately cancels the candidate. Typically slop is below a whole wheel quantum, so classification completes before the first wheel tick. Suppressed motion retains its initial baseline; the accepted sample includes the preceding displacement. A new regression test explicitly checks this path. The edge strip bypasses tap classification entirely.

Other costs: historical samples repeatedly invoked tap checks after recognition was complete, and hold callbacks were removed/reposted despite unchanged phase. Frame scheduling queued callbacks even when only fractional input existed, plus one idle frame after draining a report. These are avoidable work, but no device profiling quantifies their share of observed lag.

Bluetooth sends remain on the serial worker. Standard reports carry integer wheel ticks; multi-tick frames may look stepped on the host. Host applications/desktop settings can add their own smoothing or latency. There is no justified descriptor/pairing change or assumed current descriptor mismatch.

## Changes

- Use **20dp per tick**, down from 24dp. At 120dp/s the nominal interval falls from 200ms to approximately 167ms. This deliberately increases scroll distance per finger travel by **20%**, with no acceleration or extra multiplier. It is a conservative tuning change, not speed-neutral interpolation.
- Accumulate **raw dp in Double**, converting to integer ticks only at drain. Subtract only emitted tick distance. Small inputs remain accurate regardless of sample partitioning. Truncation is retained so tiny reversals cannot generate opposite-direction ticks from rounding error.
- Schedule a display-frame callback only when a complete cursor/wheel report is ready; reschedule only for whole-report overflow. No timer loop or zero-wheel Bluetooth traffic is introduced. Normal scrolling still emits at most one wheel report per display frame.
- Once MOTION is recognized, bypass tap-candidate processing for the rest of the stream. Existing pointer rebasing and mode locking remain in TouchpadMotion. Tap/drag thresholds are unchanged.
- Update hold scheduling only on recognizer phase transitions, not every historical sample.
- Add opt-in `BluePadHid` scroll queue-delay logging to separate worker backlog from host/radio lag in a future trace. Payload/ID/send API and registration/pairing are unchanged.

Descriptor, button ownership/order, physical button handling, sensitivity/persistence, permissions, minimum API 28 and dependencies remain unchanged.

## Verification

All **48 tests pass** (18 gestures, 14 motion regressions, 8 HID/button tests and 8 scrolling responsiveness tests). New tests cover equal distance across many tiny samples, cadence, onset distance on both scroll paths, fractional residuals, reversal jitter, overflow readiness, V4 tap-gate distance retention, tap jitter suppression and recognized-scroll finger transitions.

`--rerun-tasks testDebugUnitTest lintDebug assembleDebug`: PASS. Lint: zero errors and one existing Gradle-version notice. Debug APK built; signature verification passes. Build log: `/tmp/bluepad-v4-closeout.log`. APK: `app/build/outputs/apk/debug/app-debug.apk`.

Tests establish deterministic software behavior, not perceived smoothness or over-the-air latency. The user reports optimized scrolling passed on Vivo/Ubuntu. Other device/host combinations remain unverified. Closeout creates a local commit and annotated tag `bluetooth-touchpad-working-v4`; no push is authorized. The stable V3 tag is preserved.

## Vivo / Ubuntu retest

1. Compare right-edge and two-finger scrolling at slow, normal and fast speed in the same long document/browser page. Decide whether the 20% rate increase is comfortable and whether onset/cadence feel better.
2. Check tiny consecutive swipes, brief pauses and small reversals. Expect accumulated scrolling without jitter, incorrect-direction ticks or scrolling after cancellation.
3. Repeat single tap, two-finger right-click, double-click, double-tap-and-drag, physical-button dragging and interruption cleanup. Small two-finger tap jitter must not scroll.
4. Check strip-boundary locking, finger add/lift transitions, cursor sensitivity and persistence. No pairing refresh should be needed because the descriptor is unchanged.
5. For subjective comparison, leave verbose logs off. If lag remains, record a short trace with existing BluePadTouch/BluePadHid DEBUG logging: compare input/phase/frame times, `scroll queueMs`, send acceptance and Ubuntu wheel-event timing. Low queue delay alone does not prove low Bluetooth/host latency.

Standard integer wheel reports cannot provide pixel-level smooth scrolling without protocol/host changes outside this request. These changes improve software onset and consistency but do not promise buttery-smooth behavior in every Ubuntu application.
