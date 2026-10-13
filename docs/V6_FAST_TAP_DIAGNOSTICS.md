# Remaining fast double-tap investigation

Closeout: the user accepts natural very fast double-tap failure on Ubuntu as a V6 limitation. Moderate-speed double-taps and double-tap-and-drag work. Stable local tag: `bluetooth-touchpad-keyboard-working-v6`. The investigation below is retained as evidence; it does not claim the remaining failure is resolved. Diagnostic captures and temporary capture scripts are excluded from the checkpoint.

Latest update: captured-log analysis demonstrated compressed initial click pulses from historical event-time scheduling; output now uses actual dispatch uptime without changing the 20 ms value. See V6_PINCH_ZOOM.md for exact timestamps and uncertainty.

Update: the subsequent natural-tap refinement uses separate second-tap drag slop at twice normal slop. This document preserves the preceding investigation; `slop_crossed` alone no longer proves drag classification. Look for `drag_slop_crossed` or the hold deadline. See V6_PINCH_ZOOM.md for the current behavior.

No behavior or timing fix is applied in this diagnostic iteration. Recognition remains 120 ms, stationary hold 180 ms, nominal click edges 20 ms; zoom and HID descriptor/IDs are unchanged. The V5B checkpoint and earlier V6 work are preserved.

The production-configuration timing matrix covers 30/50/70/95/120 ms gaps with 20 ms contacts and 1–3 dp jitter. All emit four ordered, paced edges; the exact 120 ms boundary follows two-single-click recognition. Existing tests cover immediate next movement, delayed timers, terminal-UP jitter and cancellation. Live MOVE displacement above slop produces DRAG and one click pair, even in a short second tap; this is a possible explanation needing device evidence, not a newly proven bug. No host receipt is inferred from these tests.

Gesture recognition owns an ordered edge deque. A delayed timer emits one edge and spaces the next relative to execution; a new contact preserves queued clicks unless cancellation or drag acquisition clears them. TouchpadView removes its timer on DOWN but reschedules when the new phase changes. HID work uses a serial executor and MouseButtons ownership; it does not coalesce button reports. However, the UI's nominal 20 ms spacing is not enforced again on the Bluetooth worker: a blocked worker could submit queued edges closer together. That hypothesis needs queue/submission and host timestamps.

Instrumentation is opt-in and restricted to debuggable application builds. Pure gesture diagnostics use lazy messages; the view evaluates them only with its DEBUG tag enabled. Android traces include uptime; event samples include MotionEvent uptime, down-time, history count and pointer count. Gesture traces report second-contact decisions, slop crossings, drag/double recognition, pending single/edge deadlines and cancellations. HID traces report queue age, resulting mouse payload, submission sequence, uptime, API acceptance and call duration. Keyboard payloads and typed text are never logged. Gesture timestamps and Android uptime share a monotonic domain; Ubuntu event times belong to the host clock and must be compared within the host stream, not directly subtracted from Android uptime.

## Capture one failing fast tap and one passing moderate tap

Install the diagnostic build (preserving data), enable tags, then start capture in terminal A:

```sh
/tmp/bluepad-sdk/platform-tools/adb install -r /home/nafiz51/dev/BluePad/app/build/outputs/apk/debug/app-debug.apk
/tmp/bluepad-sdk/platform-tools/adb shell setprop log.tag.BluePadTouch DEBUG
/tmp/bluepad-sdk/platform-tools/adb shell setprop log.tag.BluePadHid DEBUG
/tmp/bluepad-sdk/platform-tools/adb logcat -v threadtime BluePadTouch:D BluePadHid:D '*:S' > /tmp/bluepad-fast-tap-android.log
```

In terminal B launch BluePad, connect using the existing toolbar and keep the app foregrounded:

```sh
/tmp/bluepad-sdk/platform-tools/adb shell am start -W -n com.bluepad/.MainActivity
```

On Ubuntu identify the **mouse**, not keyboard, input node:

```sh
rg -n -A8 'Name=".*(NBM|BluePad).*' /proc/bus/input/devices
```

Read its `Handlers=... eventX` line. Replace `eventX` below with that current node; do not reuse historical event13. If evtest is already available:

```sh
sudo evtest /dev/input/eventX | tee /tmp/bluepad-fast-tap-ubuntu.log
```

Do not use `--grab`: the application should continue receiving events. evtest prints event timestamps, BTN_LEFT values and SYN_REPORT boundaries. If unavailable, report that rather than changing system configuration. Optional, if already installed, `sudo libinput debug-events --device /dev/input/eventX` provides a second view of processed input.

1. Position the host cursor on a window title bar. Wait one second between trials. Perform five natural fast double-taps, then five moderate double-taps; record which trial failed and whether the window maximized/restored.
2. Repeat one fast double-tap followed immediately by pointer movement, then a deliberate double-tap-and-hold drag. Confirm the passed drag behavior remains unchanged.
3. Stop captures with Ctrl+C. Supply both files and the trial order/results. They contain pad motion/button data and connection metadata, not typed text; avoid typing during this capture.
4. Restore tags afterward:
   ```sh
   /tmp/bluepad-sdk/platform-tools/adb shell setprop log.tag.BluePadTouch INFO
   /tmp/bluepad-sdk/platform-tools/adb shell setprop log.tag.BluePadHid INFO
   ```

## Interpret evidence before changing behavior

- No `double_recognized`, plus `slop_crossed`/`drag_classified`: recognition selected movement/hold. Compare actual MOVE displacement, event age and durations.
- `double_recognized` but fewer than four `click_edge` entries: inspect `click_cancel`, `gesture_cancel` and `timer_reschedule` for interruption or scheduling failure.
- Four edges but fewer HID submissions: inspect `gesture_submit allowed=false`, connection state or physical ownership.
- Four HID mouse transitions (buttons 1,0,1,0), accepted, but missing host events: transport/host input handling remains suspect. API acceptance is insufficient proof of delivery.
- Four host BTN_LEFT transitions with timestamps, but no application double-click: inspect spacing, intervening relative motion and application/desktop interpretation. Only that evidence justifies host-specific timing conclusions.

No timeout changes, pairing deletion, system configuration changes, commit, tag or push are part of this diagnostic pass.
