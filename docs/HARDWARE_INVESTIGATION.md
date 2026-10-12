# Milestone 1 hardware failure investigation

## Findings (observations and source evidence)

User test: Vivo V2027 / NBM, Ubuntu 26.04; registration eventually succeeded, UI showed Connected, but movement produced no cursor motion or observed event13 input. Clicking remains unconfirmed.

During this investigation the local Ubuntu input registry independently showed NBM Keyboard on event12 and NBM Mouse on event13, both backed by `/sys/bus/hid/devices/0005:001D:1200.0009`. Reading its `report_descriptor` returned **142 bytes**. It declares a keyboard (Report ID 1), consumer controls (ID 3), and mouse (ID 2). Its mouse input contains five buttons, padding, X/Y/wheel and consumer pan: **five payload bytes plus the Report ID**. This is not BluePad's descriptor. A snapshot is stored temporarily at `/tmp/bluepad-host-report-descriptor.bin`; sysfs instance numbers can change.

BluePad's unchanged descriptor has **50 bytes**, one Generic Desktop Mouse application, three button bits, five constant padding bits, two signed eight-bit relative axes with range [-127,127], and no Report ID, keyboard, wheel or pan. Its input is exactly **three bytes**. The source/API contract agrees: `sendReport(host, 0, [buttons, X, Y])`, without prepending ID or Bluetooth HID transaction headers. Android documents ID 0 for descriptors without IDs and payloads excluding the ID. Boot and report mouse formats are the same for this descriptor.

Thus there is a confirmed **host descriptor mismatch**, not a demonstrated defect in the app's descriptor/encoder. A host decoding the captured numbered composite descriptor would not interpret an unnumbered three-byte BluePad report according to that layout. Actual outgoing packets and the cause of the host mismatch are not yet captured.

ADB enumerated one attached USB device as **unauthorized**. No Android logs or live app report transmissions could be obtained, and no end-to-end physical success was verified.

### Complete movement pipeline

1. MainActivity sets touchpad enabled only when `visible && state.connected` from an immutable HidManager snapshot.
2. TouchpadView accepts DOWN, saves pointer and coordinates, then handles MOVE for that pointer. It divides pixel deltas by density, accumulates fractional movement, rounds/clamps each axis to [-127,127], and invokes the callback only for a nonzero delta. Missing pointers, disabled views and subpixel-only samples emit no movement report.
3. Activity callback calls `hid.move(dx, dy)`; manager queues work on its serial executor. At execution time `state.connected` must still be true.
4. MouseButtons preserves left-button state and delegates to MouseReports.encode: three bytes, two's-complement signed coordinates, clamped bounds.
5. `send()` requires a host reference and invokes the HID profile's `sendReport(host, 0, report)`. State was set Connected by an active-session connection callback; initial connection also sends a neutral report.
6. Android request acceptance is not proof of over-the-air delivery or Linux decoding. Host descriptor and decoded events must be checked separately.

### Lifecycle and failure handling

Stop/rotation/background, Bluetooth state or bond broadcasts, permission loss, profile loss, unplug and disconnect can disable input or invalidate the session. Broadcasts restart HID registration; lifecycle changes are deliberately foreground-only. UI snapshots are posted asynchronously, so a briefly stale Connected label can outlive a worker transition; the worker still drops disconnected movement. No source evidence establishes a persistent Connected display that systematically suppresses all movement.

A false/null send result clears buttons, attempts neutral release, requests disconnect, publishes input-disabled Disconnecting state and arms a cleanup timeout. Security/runtime exceptions close the session and publish failure. Send success does not detect later radio loss or a host rejecting/misinterpreting a report. Previously these results were invisible in logs. Cleanup attempts continue after individual exceptions; logs now expose them. No error-recovery behavior changed.

## Code changes

Only targeted diagnostics and a descriptor regression test; **no behavioral fix claimed**:

- HidManager: opt-in state/callback/session/queue-delay/descriptor/report-byte/acceptance/protocol logs; explicit warnings/errors for rejection, exceptions and cleanup failures. Existing registration, connection, report and recovery calls retained.
- TouchpadView: opt-in action/enabled/pointer/density/quantized-delta/drop logs.
- MainActivity: opt-in lifecycle/broadcast/render/input-enabled logs.
- MouseReportsTest: independently walks HID short items and verifies unnumbered mouse application, collection balance, field sizes, signed limits, relative X/Y and 24-bit input length.
- MouseReports and HID descriptor unchanged. No dependencies or Milestone 2 features added.

Verbose logs require `log.tag.BluePadHid`, `log.tag.BluePadTouch`, `log.tag.BluePadUi` set to DEBUG. Hex formatting is gated; normal operation does not format report hex. Use short traces to limit diagnostic overhead. Logs omit device names/addresses. Error/cleanup warnings remain available without opting in.

## Verification

`testDebugUnitTest lintDebug assembleDebug`: PASS using Gradle 8.13, SDK 36, offline cached dependencies. Five JUnit tests, zero failures/errors/skips. Lint: zero errors, one pre-existing newer-Gradle notice. Debug APK generated at `app/build/outputs/apk/debug/app-debug.apk`; signature verification passed. Build log: `/tmp/bluepad-investigation-build.log`.

Hardware resolution: **UNVERIFIED**. The test validates the source descriptor, not the descriptor negotiated on the Vivo or the data reaching Ubuntu.

## Remaining hypotheses, ordered by evidence

1. Host is using a stale/different HID service descriptor from an earlier app, registration or cached bond/service record. Strongest evidence: the actual 142-byte composite descriptor contradicts BluePad's 50-byte mouse-only descriptor. Determine provenance with a fresh registration trace and SDP/host capture before changing pairing state.
2. Vivo's HID stack could expose/substitute a different descriptor or mishandle the unnumbered report. Needs fresh registration, host descriptor comparison and packet evidence; do not switch to ID 2 just to match unexplained host state.
3. Touch delivery/UI enabled state/worker session gating could prevent nonzero reports. Falsify by matching touch delta -> worker move -> send bytes -> accepted result in the new trace.
4. Android could accept reports that never reach the host, or Linux could receive bytes and discard them. Separate with simultaneous Bluetooth capture and Linux input capture.

Event13 currently is NBM Mouse, so the original node choice is consistent with current mapping; recheck after every reconnect. A plain terminal read of binary evdev data is weaker evidence than decoded events or hex capture.

## Next physical test: exact Android commands

Enable Developer options and USB debugging on the phone; unlock it and accept the computer's RSA authorization prompt. Use the temporary ADB installation below, or substitute a persistent ADB executable if /tmp was cleared. If multiple phones are attached, add `-s SERIAL` to each ADB invocation.

From a terminal:

```sh
BLUEPAD_ADB=/tmp/bluepad-sdk/platform-tools/adb
"$BLUEPAD_ADB" devices -l
"$BLUEPAD_ADB" install -r /home/nafiz51/dev/BluePad/app/build/outputs/apk/debug/app-debug.apk
"$BLUEPAD_ADB" shell setprop log.tag.BluePadHid DEBUG
"$BLUEPAD_ADB" shell setprop log.tag.BluePadTouch DEBUG
"$BLUEPAD_ADB" shell setprop log.tag.BluePadUi DEBUG
"$BLUEPAD_ADB" logcat -T 1 -v threadtime 'BluePadHid:D' 'BluePadTouch:D' 'BluePadUi:D' 'BluetoothHidDevice:V' 'HidDeviceService:V' 'AndroidRuntime:E' '*:S' | tee /tmp/bluepad-android.log
```

Wait for `device` (not `unauthorized`) before install/log capture. Leave logcat running. In a second terminal:

```sh
BLUEPAD_ADB=/tmp/bluepad-sdk/platform-tools/adb
"$BLUEPAD_ADB" shell getprop ro.product.model
"$BLUEPAD_ADB" shell getprop ro.build.version.release
"$BLUEPAD_ADB" shell getprop ro.build.version.sdk
"$BLUEPAD_ADB" shell am start -W -n com.bluepad/.MainActivity
```

Keep BluePad visible/unlocked. Refresh once, wait for Registered, select Ubuntu and explicitly Connect. Make three obvious drags separated by pauses, then press/hold/release Left click once. Note wall-clock test times. Do not open Settings during the movement trace; that stops this foreground-only app. Finish with Disconnect, then Ctrl+C in the log terminal.

Expected nonzero movement trace: `BluePadTouch delta dx=... dy=...` -> `BluePadHid move ... connected=true` -> `send ... id=0 length=3 bytes=00 ... ...` -> `accepted=true`. Left click should send `01 00 00` then `00 00 00`. A neutral connection report alone is not proof that touch movement reached the manager.

If platform errors are absent from filtered logs, collect all buffers in another terminal during a short repeat:

```sh
BLUEPAD_ADB=/tmp/bluepad-sdk/platform-tools/adb
"$BLUEPAD_ADB" logcat -b all -T 1 -v threadtime > /tmp/bluepad-android-full.log
```

Disable verbose tracing after testing:

```sh
BLUEPAD_ADB=/tmp/bluepad-sdk/platform-tools/adb
"$BLUEPAD_ADB" shell setprop log.tag.BluePadHid INFO
"$BLUEPAD_ADB" shell setprop log.tag.BluePadTouch INFO
"$BLUEPAD_ADB" shell setprop log.tag.BluePadUi INFO
```

## Simultaneous Ubuntu capture

In separate terminals, start Bluetooth and input capture before connecting/dragging:

```sh
sudo btmon -w /tmp/bluepad-host.btsnoop
```

Inspect device mapping and all currently named NBM HID descriptors after connecting:

```sh
cat /proc/bus/input/devices
for BLUEPAD_HID in /sys/bus/hid/devices/*; do
    if rg -q '^HID_NAME=NBM$' "$BLUEPAD_HID/uevent"; then
        printf '%s\n' "$BLUEPAD_HID"
        od -An -tx1 "$BLUEPAD_HID/report_descriptor"
    fi
done
```

Select the current NBM Mouse event handler, not a historical event number. If `evtest` is installed:

```sh
sudo evtest /dev/input/event13
```

Otherwise inspect binary events without installing anything:

```sh
sudo od -An -tx1 /dev/input/event13
```

Replace event13 if the mapping changed. Stop captures with Ctrl+C. Collect the new host descriptor, Android log, btmon capture and event output together. If fresh Android registration logs show BluePad's 50-byte descriptor but Ubuntu still has 142 bytes, preserve this evidence, disable other HID apps, remove the pairing on both sides through Settings and re-pair with BluePad registered/visible; repeat descriptor comparison and transmission capture. This is a conditional diagnostic procedure, not an app descriptor change or a guaranteed fix.

## References

- [Android BluetoothHidDevice contract](https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice)
- [Linux HID descriptor and event diagnostics](https://docs.kernel.org/hid/hidintro.html)
- [HID 1.11 specification, mouse boot format](https://www.usb.org/sites/default/files/hid1_11.pdf)
