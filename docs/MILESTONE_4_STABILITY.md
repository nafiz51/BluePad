# Milestone 4: measured stability audit and hardware gate

No production code changed. Ubuntu remains the verified baseline; Windows investigation and release packaging are excluded.

## Findings

- Movement is accumulated fractionally and emitted on display frames; empty movement/wheel reports are skipped. Overflow is retained and drained rather than silently clipped. No new high-frequency logging was added.
- HID work runs on one executor, preserving report order. Its queue is unbounded; a slow transport or large typing workload could accumulate pending work. No queue-growth measurement establishes a defect, so no arbitrary queue limit or input-dropping policy is introduced.
- Zoom uses the existing 30 ms pacing/four-step bound. Gesture classification, click sequencing, cancellation and dedicated-button handlers remain unchanged.
- Keyboard strokes publish UI state per tap. This is existing work, not a measured bottleneck. Editor history and composing drafts retain the documented long-session memory risk. Early oversized-commit validation avoids the unnecessary key list, but does not cap text or the executor queue.
- Stop invalidates input immediately, disables controls, cancels touch callbacks and closes the keyboard. Worker cleanup cancels retry/deadline callbacks and unregisters/disconnects HID. Destroy shuts down the executor. Cleanup on the worker still waits behind queued work; session fencing prevents stale input, but a stalled transport could delay cleanup. No such stall is established here.
- No app wake locks, background services, alarms or periodic polling were found. Foreground input/Bluetooth/radio and screen usage still consume power. Existing three-attempt reconnection, deadlines and explicit-disconnect suppression are preserved.

## Read-only Vivo snapshots

First PSS: 64,219 KiB (62.7 MiB), Java-heap PSS 5,468 KiB (5.3 MiB). Follow-up PSS: 46,273 KiB (45.2 MiB), Java-heap PSS 5,540 KiB. Swap PSS was 7,930 then 12,339 KiB; lower resident PSS alone does not prove memory reclamation or absence of leaks. Both snapshots counted two Activities. Without a controlled recreation/GC/reference trace, this is a follow-up risk, not a confirmed leak.

Graphics statistics covered 919 rendered frames with 149 janky frames (16.21%), median 12 ms, 95th percentile 40 ms; these cumulative counters mix navigation, theme and IME activity. Touchpad motion does not redraw every HID frame. These numbers cannot establish cursor or host-delivery latency.

CPU report attributed 13% to BluePad over an earlier approximately 47-second window several minutes before capture; it is not a fresh controlled active-input sample. Phone was USB-powered at 100% charge and reported temperature 35.9°C. No meaningful battery-drain measurement is claimed. Snapshots are excluded from Git in `/tmp/bluepad-stability-*.txt`.

## Deterministic verification

Three new long-session regressions exercise 20,000 fractional movement samples with direction reversal; 200 supported 512-character commits (102,400 strokes/204,800 reports) while preserving Ctrl and release ordering; and 1,000 session invalidation/cleanup cycles. Existing tests cover scrolling, zoom pacing, rapid taps/drags, deadlines, retries and callbacks. No test claims Android editor memory bounds, elapsed hardware time, host delivery, real Bluetooth recovery or battery precision.

## Vivo → Ubuntu hardware plan

Use the normal APK, same 3.5× sensitivity and preferred theme; keep verbose touch/HID tracing disabled. Record the phone/Ubuntu versions, start/end times, brightness, charging state and whether other apps were active.

1. Capture a starting memory snapshot after connecting and settling for two minutes.
2. Use the touchpad for 20–30 minutes: slow circles, fast sweeps, repeated scroll reversals, alternating zoom directions, clicks and both drag methods. Look for increasing delay, stuck buttons, bursts or unexpected disconnects.
3. Type for 10–15 minutes without closing the keyboard; include composition, deletion, modifiers and special keys. Capture memory around 0/5/10/15 minutes. Close/reopen or Release all keys, settle, and capture again. Editor history growth is expected; report whether memory/performance recovers, not just a single PSS change.
4. With input active, toggle Ubuntu Bluetooth off/on five times; verify bounded recovery, clean buttons/modifiers and no replayed input. Repeat with automatic reconnect disabled and an explicit Disconnect during recovery; verify suppression.
5. Lock/unlock and background/reopen the phone app five times each. The existing lifecycle deliberately disconnects when backgrounded; foreground recovery must not replay old clicks or typing.
6. Perform ten manual disconnect/connect cycles, then leave BluePad foreground-idle for five minutes. Watch for persistent busy state, duplicate retries or abnormal idle activity.
7. For battery observations, repeat a consistent 30-minute active session unplugged with fixed brightness and a similar 30-minute foreground-idle control. Record percentage and temperature at start/end. USB ADB charging invalidates a drain test; use manual readings or already-configured wireless ADB. Percent changes are coarse whole-phone observations, not app-only laboratory measurements.

Short Ubuntu measurement commands (use a new filename for each checkpoint):

```sh
adb shell dumpsys meminfo com.bluepad > /tmp/bluepad-mem-start.txt
adb shell 'top -b -n 3 -d 1 -p $(pidof com.bluepad)' > /tmp/bluepad-cpu-active.txt
adb shell dumpsys gfxinfo com.bluepad > /tmp/bluepad-gfx-end.txt
adb shell dumpsys battery > /tmp/bluepad-battery-end.txt
adb shell dumpsys power > /tmp/bluepad-power-end.txt
adb logcat -v threadtime -s BluePadConnection:I BluePadHid:W '*:S' > /tmp/bluepad-recovery.log
```

Run CPU samples during cursor movement, typing and idle separately; the first top sample may reflect prior activity. Stop log capture with Ctrl+C. Frame counters require workload interpretation and do not prove cursor latency. If Activity counts or post-reset heap usage rise across repeated cycles, collect a targeted profiler/reference investigation before optimizing. Do not reset battery statistics, force GC, delete bonds or enable verbose input logging for the main usability run.

The user confirmed automated and Vivo hardware verification passed; final local checkpoint awaits approval. No commit/tag/push, new dependency or Milestone 5 work.

Final gates: 223 unit tests passed, zero failures/errors/skips; lint passed with the existing AndroidGradlePluginVersion warning; debug build and APK v2 signature verification passed (one signer). APK size: 2,983,823 bytes. Gates ran sequentially offline with one worker and 768 MiB heap. Logs: `/tmp/bluepad-stability-testDebugUnitTest.log`, `/tmp/bluepad-stability-lintDebug.log`, `/tmp/bluepad-stability-assembleDebug.log`.
