# Cross-device HID connection recovery (unreleased)

Baseline: published v1.2.0. Realme C30 / reported Android 11 fails HID establishment; Vivo / Ubuntu remains the verified baseline. No Realme logs or hardware were available for this investigation.

## Confirmed code defects

- connectInternal previously treated an already CONNECTED HID profile as an accepted request, then waited for a new callback. Without that callback it closed the working profile after 12 seconds.
- The registration callback ignored its pluggedDevice virtual-cable information. A bonded host whose HID profile is already connected can now be reconciled there.
- At the connection deadline, an established HID profile with a missing/delayed callback was discarded without a final state check.
- An unsolicited DISCONNECTING callback had no deadline of its own; a missing terminal callback could leave busy=true indefinitely.

## Changes and safety

Use the existing connection-state handler for authoritative HID CONNECTED snapshots at registration, before requesting a connection, and at the connection deadline. Require active registration, a matching bonded host, no explicit disconnect suppression and no recovery in progress. Pairing, ACL connectivity and CONNECTING are never interpreted as HID success. Established sessions still neutralize mouse/keyboard state and invalidate the input token; duplicate connected callbacks remain no-ops.

Arm the existing disconnection deadline on DISCONNECTING; preserve report-rejection recovery deadlines. Timers remain 12 seconds, automatic retries remain bounded to three, and generations fence old callbacks. No polling, new services, descriptor changes or timeout increases. Foreground/background ownership remains unchanged: leaving the Activity stops the HID session; reopening reacquires it.

Permission policy remains unchanged: Android 28–30 use manifest Bluetooth permissions; Android 31+ request BLUETOOTH_CONNECT. Its API boundary is now explicitly testable.

A true connection timeout still closes/reacquires the profile through existing recovery. The error explains that pairing alone is insufficient and suggests keeping BluePad visible, enabling host Bluetooth, retrying, or initiating connection from the computer.

## Remaining uncertainty

These defects are reproducible from control flow, but are not proven to explain the Realme failure. A vendor HID stack may remain CONNECTING or DISCONNECTED throughout; registration success proves neither HID channel establishment nor host delivery. SDP negotiation, HID control/interrupt channel failure, vendor foreground policy and adapter coexistence require hardware evidence. No claim of Realme compatibility is made.

## Hardware verification

Install the same debug-signed APK using adb install -r app/build/outputs/apk/debug/app-debug.apk (do not uninstall or clear app data).

On both Vivo and Realme:
1. Keep BluePad visible, enable phone/laptop Bluetooth and connect the existing paired Ubuntu host.
2. Verify mouse X/Y, single/double click, dragging, both scroll methods, zoom and keyboard typing.
3. Disconnect explicitly: confirm no automatic reconnect. Connect manually again without restarting.
4. Toggle laptop Bluetooth off/on; confirm bounded recovery.
5. Background/foreground BluePad and lock/unlock; confirm safe input cleanup and recovery.
6. Try host-initiated connection from Ubuntu Bluetooth settings with BluePad visible. Generic Android connected status alone is not acceptance.

If Realme still fails, one optional developer capture distinguishes app timeout from a stack that never connects (no typed content or high-frequency touch logging):

    adb logcat -v monotonic BluePadConnection:I BluePadHid:W '*:S' > /tmp/bluepad-realme-connection.log

Start before one connection attempt; stop with Ctrl+C after recovery completes. Expected evidence: registration callback, connect acceptance, observed HID states, reconciliation if used, deadline reason and profile cleanup. Missing CONNECTED throughout requires stack-level investigation; do not repeatedly alter timings or reset pairing without evidence.

Automated tests cover the production reconciliation eligibility, deadline policy, API permission boundary and existing callback/retry/session policies. They do not emulate vendor Bluetooth IPC or prove actual callback timing, host delivery, or real-device performance.
