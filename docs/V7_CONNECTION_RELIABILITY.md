# V7 connection reliability

Status: hardware-verified stable local checkpoint, annotated tag `bluetooth-touchpad-keyboard-working-v7`. V6 remains at `cb52e78528015c250a0cb89ffe2c61deda12b38f`, annotated tag `bluetooth-touchpad-keyboard-working-v6`. Its accepted very-fast-double-tap limitation remains unchanged.

Verification: 158 unit tests passed (zero failures/errors/skips), lint zero errors and one existing Gradle-version warning, debug APK build and APK signature verification passed. Gates ran sequentially offline with one worker, a 768 MiB Gradle heap and in-process Kotlin compilation. Logs: `/tmp/bluepad-v7-tests.log`, `/tmp/bluepad-v7-lint.log`, `/tmp/bluepad-v7-apk.log`. APK: `/home/nafiz51/dev/BluePad/app/build/outputs/apk/debug/app-debug.apk`. Tests validate policy, callback eligibility, stale deadline/input fences and V6 regressions; hardware verification on the Vivo Android phone and Ubuntu laptop is recorded below. Other devices and operating systems remain unverified.

## Hardware verification

The user confirmed all 10/10 hardware tests passed on the Vivo Android phone and Ubuntu laptop, including connection recovery after toggling laptop Bluetooth. This verifies the V7 milestone on that device/host combination; it does not guarantee reconnection on every Android device or host operating system. The accepted V6 limitation remains: natural very fast double-taps may fail on Ubuntu, while moderate-speed double-taps and double-tap-and-drag work.

## Files changed

- `README.md`
- `app/src/main/java/com/bluepad/MainActivity.kt`
- `app/src/main/java/com/bluepad/bluetooth/HidManager.kt`
- `app/src/main/java/com/bluepad/bluetooth/HidDeadline.kt`
- `app/src/main/java/com/bluepad/bluetooth/ReconnectPolicy.kt` (new)
- `app/src/main/java/com/bluepad/bluetooth/ConnectionCallbacks.kt` (new)
- `app/src/main/java/com/bluepad/bluetooth/InputSession.kt` (new)
- `app/src/main/java/com/bluepad/ui/KeyboardView.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/com/bluepad/bluetooth/HidDeadlineTest.kt`
- `app/src/test/java/com/bluepad/bluetooth/ReconnectPolicyTest.kt` (new)
- `app/src/test/java/com/bluepad/bluetooth/ConnectionCallbacksTest.kt` (new)
- `app/src/test/java/com/bluepad/bluetooth/InputSessionTest.kt` (new)
- `docs/V7_CONNECTION_RELIABILITY.md` (new)

## Architecture and audited weaknesses

V6 already used one serial HID worker, registration/connection callbacks, session-generation guards, operation-owned 12-second deadlines and mouse/keyboard neutral cleanup. It did not persist the successful host or retry failed connections. Its timeout closed registration and required manual refresh. Every bond broadcast restarted registration, even unrelated bond changes. Input queued against an earlier connection could run against whichever connection existed when the task executed. A queued old timeout could also share the operation name of a new deadline.

V7 keeps the same Activity ownership and HID executor. No service, dependency, permission, descriptor, SDP metadata or payload change is introduced. Mouse ID 2 and keyboard ID 1 retain V6 semantics. Touchpad gesture math, zoom, click timing, 12% strip and sensitivity/Host OS preferences are unchanged.

## Reconnection policy

`ReconnectPolicy` permits one pending or in-flight automatic attempt. It checks enabled/paused state and an eligibility supplied from foreground visibility, current Bluetooth permission, Bluetooth enabled and current bonded-device lookup. The successful host address is saved only from a current CONNECTED callback. On first V7 launch there may be no saved host from V6: connect manually once. Multiple paired hosts do not trigger guessed selection.

- Opening a foreground session after registration: at most three outgoing attempts, immediate then 2 and 4 seconds after failures.
- Temporary disconnect/manual request failure: at most three automatic attempts after 2, 4 and 8 seconds.
- Each profile acquisition, registration or connection retains its 12-second timeout. Failure can close/recreate the HID session through the same retry budget. Registration failure consumes an already reserved retry rather than starting an infinite separate loop.
- A Connected callback followed by accepted initial neutral reports resets the budget for a later disconnect episode. Rejected initial reports do not reset it into a reconnect loop. Exhaustion shows Connection failed. Manual Reconnect last host or a new foreground session starts a fresh bounded episode.
- A manually selected bonded host can be retried within that episode; it becomes the persisted host only upon success. Future foreground sessions use the last successful host.
- Automatic reconnection is enabled by default. Turning it off cancels waiting retries; it does not disconnect an already connected host or undo a connection request already submitted.
- Explicit Disconnect persists suppression across background/foreground and restarts. Manual Connect/Reconnect, or explicitly enabling automatic reconnection again, clears suppression. A virtual-cable unplug also suppresses automatic reconnection. This respects intentional disconnects rather than immediately undoing them.
- The profile state is checked before an outgoing Connect request so an already connecting/connected host is not requested again. Input still waits for the callback. Incoming host connections are accepted for a bonded host when not explicitly paused. They cancel a waiting outgoing retry. An enabled automatic retry limit does not block the host from independently initiating a connection after the budget is exhausted.

No Bluetooth bonds, caches or system settings are changed automatically. The connection panel retains manual Connect, Disconnect and Refresh and adds Reconnect last host; Settings adds the reconnection switch alongside existing sensitivity and Host OS.

## Callback-driven state and input safety

Only a current Connected callback enables input. Request acceptance displays Connecting/Reconnecting; it never proves success. Reconnecting also represents a scheduled backoff. Disconnected, Connecting, Connected, Reconnecting and Connection failed are the connection labels. The existing busy flag indicates whether an operation is still being completed. The toolbar continues showing the connection label, while the connection panel holds errors and controls.

`ConnectionCallbacks` checks session generation, active ownership, host identity, bond/pause eligibility and agreement with the profile's current connection state. Duplicate Connected callbacks do not neutralize a live drag again; duplicate Disconnected callbacks cannot overwrite a pending retry. GET/SET/interrupt/unplug callbacks are restricted to the current host/session. Callback ordering and profile-state queries still depend on vendor Android implementations; these checks require hardware validation.

Deadline revisions reject an old timeout even when a newer deadline has the same operation name. Retry tickets reject removed or superseded timer callbacks. Stop cancels retry/deadline work and closes registration. Bluetooth OFF/turning OFF stops; ON while visible starts again. Bond broadcasts refresh the paired list and close an unbonded active host without restarting healthy registration for unrelated changes.

`InputSession` invalidates work at connection, report-failure, explicit disconnect and lifecycle boundaries. Queued mouse/keyboard/zoom tasks carry the UI-acknowledged input token; they cannot run in a newer connection. A new connection remains gated until MainActivity cancels pad gestures and physical holds, clears IME draft/modifier feedback and acknowledges the new token. KeyboardView invalidates old InputConnection wrappers before clearing/restarting the field, rejecting late commits/deletes/composition/editor callbacks from the old generation. This closes the interval between worker reconnection and main-thread UI cleanup.

Local mouse/keyboard owners are cleared on disconnect/close/failure. Neutral mouse and keyboard reports are attempted before an explicit disconnect/stop, and on disconnecting while transport may still permit them. New connections start neutral. Radio loss or process termination cannot guarantee that release reports arrive at the host.

## Hardware test procedure

1. Install `/home/nafiz51/dev/BluePad/app/build/outputs/apk/debug/app-debug.apk` using `adb install -r`. Keep existing bonds. Connect to Ubuntu manually once, confirm both devices work, then background and reopen BluePad. Confirm HID registration precedes automatic reconnection and the saved host is selected.
2. Close/reopen repeatedly; test screen lock/unlock and rotation. V7 remains foreground-only: backgrounding stops registration, clears input and cancels retries. Returning attempts connection if enabled and not explicitly paused.
3. With BluePad visible, turn host Bluetooth off/on or sleep/wake the host. Observe Reconnecting with bounded attempts; leave it unreachable until Connection failed, then restore it and use Reconnect last host. Also test a host-initiated connection after exhaustion. Do not infer automatic success on every OS.
4. Test phone Bluetooth OFF/ON and permission denial/regrant. No retry should run while disabled/denied. On API 31+ grant Nearby devices before expecting automatic connection. Reopen after revoking permission externally.
5. Press Disconnect while connected and while waiting for a retry. Wait and reopen the app: no automatic reconnection should override it. Reconnect manually. Turn the automatic switch off during backoff; confirm waiting attempts stop. A request already submitted may still finish. Re-enable intentionally and confirm recovery.
6. Disconnect during a gesture drag, dedicated left/right hold, pending tap, pinch, IME composing text and active Ctrl/Shift/Alt/Super. Reconnect: no old click, keystroke, zoom or held modifier should be replayed. New input must work. Test supported typing/deletion/Enter, both scroll modes, sensitivity persistence, Host OS persistence and all V6 controls.
7. Test multiple paired hosts and loss of the remembered bond only when deliberately conducting that test; V7 itself never removes bonds. Verify unknown/unbonded devices are not selected automatically, while the last successful host persists.
8. Repeat on other hosts/phones and API 28 if available. Record Android version, host OS, callback states, retry count, foreground lifecycle and failures. Unit tests cannot certify Android radio, BlueZ discovery or host sleep/wake behavior.

Optional short diagnostics:
```sh
/tmp/bluepad-sdk/platform-tools/adb shell setprop log.tag.BluePadHid DEBUG
/tmp/bluepad-sdk/platform-tools/adb logcat -v threadtime BluePadHid:D BluePadUi:D '*:S' > /tmp/bluepad-v7-hardware.log
```
Stop with Ctrl+C and restore `adb shell setprop log.tag.BluePadHid INFO`. Keyboard contents are not logged.

Android documents foreground HID registration restrictions and asynchronous connection callbacks: [BluetoothHidDevice](https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice), [callbacks](https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice.Callback). No background reconnection guarantee is made.
