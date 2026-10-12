# V5A connection audit

Closeout status: **the user has now confirmed the current V5A version is hardware-verified**. The investigation below records the preceding failure; hardware success does not establish which host-side hypothesis caused it. Local annotated milestone tag: `bluetooth-mouse-keyboard-working-v5a`. V4 commit `d25d17637e638bc699be27d8eed5def2e412c494` and tag `bluetooth-touchpad-working-v4` are unchanged. No pairing data or Ubuntu configuration was changed.

## V4 → V5A protocol comparison

| Property | V4 | Current V5A |
| --- | --- | --- |
| Mouse input | Unnumbered, API ID 0 | ID 2 |
| Mouse payload | Buttons, signed X, signed Y, signed wheel; 4 bytes | Same fields and encoding; 4 bytes |
| Mouse descriptor | Standalone mouse collection | Same bytes with Report ID 2 inserted inside application collection |
| Keyboard input | None | ID 1; modifiers, reserved byte, six usages; 8 bytes |
| Keyboard output | None | ID 1; LED bitmap; 1 byte |
| SDP subclass | Mouse (0x80) | Combo (0xC0) |
| SDP description | Mouse | Mouse and US keyboard |

Both report IDs are passed separately to Android `sendReport`; payloads contain no extra ID byte. Android generates the SDP service record from `BluetoothHidDeviceAppSdpSettings`; BluePad does not supply service handles or L2CAP protocol lists. Descriptor tests verify collection balance, IDs, input/output bit counts, preserved mouse fields, independent axes, routing and cleanup. No concrete descriptor or encoding defect was established in this audit. Boot protocol interoperability remains hardware-unverified.

## Evidence and limits

Registration acceptance and its successful callback prove Android accepted registration, not that Ubuntu discovered a usable input profile. State 1 without state 2 means this attempt never reached connected report transmission. Input cannot work until that changes.

In [BlueZ 5.85 input/device.c](https://github.com/bluez/bluez/blob/5.85/profiles/input/device.c), `extract_hid_record` returns `-ENOENT` when `idev->rec` is null. Malformed/missing HID descriptor data instead follows the `-EINVAL` path. Therefore the reported `Could not parse HID SDP record: No such file or directory (2)` points to an absent active SDP record, before report descriptor parsing. The input server's unknown-device refusal can mean no input-profile object exists for this device; it alone does not establish that the Bluetooth bond is absent.

The supplied disk cache contains the V5A descriptor. That rules out simply asserting it is an old V4 cache, but does not show that BlueZ attached that record to its current input device. Service discovery/profile activation timing, missing live HID service association, or registration lifecycle interaction remain hypotheses. A cached service handle from a previous Android registration is also insufficient evidence of a current live service. No host workaround or descriptor change is justified yet.

An old unnumbered descriptor would explain the earlier vertical-only symptom: treating the numbered mouse packet as unnumbered makes its buttons byte become X and its actual X become Y. This is a conditional explanation, not evidence that the current cache is stale. Obtain the descriptor from the instantiated Linux HID device and actual packet bytes if connection succeeds.

## Narrow lifecycle fix

Previously, every registration callback cancelled the current timeout, including an unrelated connection deadline. Incoming `STATE_CONNECTING` callbacks also did not arm a deadline. These paths could leave the UI at Connecting indefinitely. Deadlines now track their owning operation: registration callbacks cancel only registration deadlines; incoming connections start a 12-second deadline; repeated Connecting callbacks do not extend it. Five tests cover these rules and disconnect/recovery cleanup. This bounds the failed attempt and preserves existing connection/report handling; it does not repair BlueZ's missing active record.

The existing native default-IME interface and 0.5–8× sensitivity are retained. IME support is an append-oriented US HID proof of concept: composition stays local until committed; Bangla/emoji/unmapped Unicode, autocorrection/replacement and local clipboard editing are unsupported. Local field contents are not a synchronized Ubuntu document.

## One controlled hardware retest

Do not remove bonds, delete caches, change BlueZ configuration or repeatedly refresh/connect. Keep BluePad visible while gathering evidence.

1. Install the new APK with `adb install -r /home/nafiz51/dev/BluePad/app/build/outputs/apk/debug/app-debug.apk`. Open BluePad and wait for HID Registered. Use the SDK adb path below if adb is not on PATH.
2. Enable Android diagnostics and capture logs in one terminal:

   ```sh
   /tmp/bluepad-sdk/platform-tools/adb shell setprop log.tag.BluePadHid DEBUG
   /tmp/bluepad-sdk/platform-tools/adb shell setprop log.tag.BluePadKeyboard DEBUG
   /tmp/bluepad-sdk/platform-tools/adb logcat -v threadtime BluePadHid:D BluePadKeyboard:D BluePadUi:D BluePadTouch:D '*:S' > /tmp/bluepad-v5a-retest-android.log
   ```

3. On Ubuntu, record `bluetoothctl info 20:74:54:CC:03:F2`. Capture Paired/Bonded, Connected, ServicesResolved when present, and the HID UUID 00001124-0000-1000-8000-00805f9b34fb. Capture current service discovery with `sdptool browse --xml 20:74:54:CC:03:F2`; retain the entire HID record, including handle, service class, protocol lists and descriptor, plus any errors.
4. Start a passive trace in another terminal: `sudo btmon -w /tmp/bluepad-v5a-retest.btsnoop`. With BluePad registered and visible, make **one** host-initiated attempt: `bluetoothctl connect 20:74:54:CC:03:F2`. Record the time and result. After 15 seconds stop the trace/log capture with Ctrl+C, and collect `journalctl -u bluetooth --since '5 minutes ago' --no-pager`. Packet traces may contain typed HID input; test only disposable text and share the connection section first.
5. If it still fails, stop and provide the logs, current SDP record and bluetoothctl output. Compare live discovery/profile creation with the cache; do not repeat pairing deletion. Confirm BluePad exits Connecting after the deadline. This distinguishes the bounded UI failure from the remaining host/profile failure.
6. If connected, capture the current NBM entries in `/proc/bus/input/devices`, their sysfs HID `report_descriptor`, and the mouse/keyboard `evtest` streams (current nodes, not historical event13). Test horizontal-only and vertical-only movement, then a single ASCII `a` in an Ubuntu editor with US layout. Android logs separate IME generation from keyboard report sending; a successful send means API acceptance, not confirmed host interpretation. btmon and evtest establish host receipt and interpretation.
7. Then retest all V4 gestures, both scroll modes, physical button, sensitivity/persistence, IME deletion/Enter, toolbar modifiers and shortcuts, screen switching and interrupted-drag/key cleanup. Do not certify V5A until these pass.

Disable diagnostic verbosity afterward with `adb shell setprop log.tag.BluePadHid INFO` and `adb shell setprop log.tag.BluePadKeyboard INFO`.

## Automated verification

Sequential offline Gradle runs with one worker, a 768 MiB heap and in-process Kotlin compilation passed: 89 unit tests, zero failures/errors/skips; lint zero errors and one existing `AndroidGradlePluginVersion` warning; debug APK build successful. APK signature verification also passed. Logs: `/tmp/bluepad-v5a-lifecycle-tests.log`, `/tmp/bluepad-v5a-lifecycle-lint.log`, `/tmp/bluepad-v5a-lifecycle-apk.log`. These checks do not verify Android/BlueZ interoperability on hardware.
