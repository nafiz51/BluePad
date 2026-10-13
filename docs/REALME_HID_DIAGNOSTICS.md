# Realme HID connection investigation (local diagnostic APK)

The Realme C30 / reported Android 11 failure remains unexplained by available evidence. Registration success and pairing do not prove an established HID session. Previous recovery fixes are preserved; no descriptors, report formats, timeouts, retry limits, version or input behavior are changed here.

## API and architecture findings

BluePad registers the existing 119-byte composite descriptor as SUBCLASS1_COMBO (mouse ID 2 / four bytes, keyboard ID 1 / eight bytes). It selects bonded devices and waits for registration before connecting; connect() acceptance never implies connection success. The serial worker, session generations, profile-state checks and bounded retries fence old operations. Profile CONNECTED reconciliation remains in place. Android 11 uses legacy manifest Bluetooth permissions, not an unnecessary Nearby Devices prompt.

Android handles SDP publication and HID control/interrupt channel negotiation. This is Classic Bluetooth HID Device, not BLE GATT; the phone is the HID device and Ubuntu the HID host. A generic Bluetooth connection can represent a different profile. Automatic unregistration when an app is no longer foreground is part of the platform contract.

No additional defect explaining the Realme failure has been established. Possible SDP discovery/BlueZ negotiation failure, vendor HID implementation behavior, role/profile interactions or foreground-policy interruptions remain hypotheses. Android's public HID callback exposes a connection state, but no underlying SDP/L2CAP error status; third-party applications cannot force hidden connection-policy APIs or inspect the native negotiation failure through this callback.

Sources:
- https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice
- https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice.Callback
- https://android.googlesource.com/platform/frameworks/base/+/android12-release/core/java/android/bluetooth/BluetoothHidDevice.java

## Capture

Install the diagnostic APK with `adb install -r app/build/outputs/apk/debug/app-debug.apk`, preserving existing settings.

1. Open BluePad on Realme and open Bluetooth connection controls.
2. Select the paired Ubuntu laptop and tap Connect. Keep BluePad visible.
3. Wait until the attempt fails (and, preferably, until automatic retries finish).
4. Tap **Copy Diagnostics** in that same panel.
5. Paste the report into your chosen private message or text document for review.

No logcat, pairing reset or host configuration change is required. Copy remains available after the first failure, including during later retries. Recreating the Activity or restarting the app creates a new in-memory timeline; copy before doing so.

## Privacy and bounds

The report contains Android API/release, manufacturer/model, app version, monotonic timestamps and connection metadata. Host names, MAC addresses, UUIDs, keyboard report events and typed text are not recorded. Existing connection-log host address fields were replaced with presence/matching booleans. MAC redaction provides a defensive fallback. Report callbacks are excluded, and high-frequency touch/mouse logging remains disabled.

Storage is in memory only: the newest 200 events, each at most 320 message characters (plus fixed timestamp prefix), and a maximum 512-character environment header. Old entries are discarded with a count. No files, uploads or additional permissions. Only a deliberate Copy action writes the report to Android's clipboard; OS clipboard synchronization/persistence is outside BluePad's control. Android 13+ marks this clipboard entry sensitive to suppress the preview.

## Interpretation

- Registration lost / profile lost / close reason=stop: investigate foreground ownership or service loss.
- connect accepted=false: the framework or stack rejected initiating the operation; this is not proof of a descriptor failure.
- accepted=true and state remains 1 through deadline: the stack did not expose completed HID establishment. Public API cannot distinguish SDP vs control/interrupt-channel failure.
- state=2 but callback ignored: compare callback session, current session, matching host, bond and observed profile state.
- reconciliation finds state=2: missing/delayed callback recovery is exercised.
- state=2 followed by report rejection/recovery or state=0: establishment and post-establishment failure are distinct.

Next steps depend on this evidence; no new speculative transport fix is included. Recheck Vivo connection, cursor/double-click/drag, scrolling/zoom and keyboard after installation. Unit tests validate timeline bounds, event order, privacy filtering and failure availability; real clipboard/UI and vendor stack behavior still require physical testing.
