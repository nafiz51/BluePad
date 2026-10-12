# BluePad — Milestone 1

Native offline Android Bluetooth Classic HID mouse prototype. Android 9/API 28 minimum; compile/target API 36. No Internet permission, runtime libraries, root, or PC companion software. Keyboard, scrolling, gestures and background operation are outside this milestone.

## Build

Install JDK 17 or 21, Android SDK platform 36 and build-tools 35.0.0. Set `sdk.dir` in untracked `local.properties`, or `ANDROID_HOME`. Run:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The wrapper resolves Gradle 8.13; pinned AGP 8.13.2 and Kotlin 2.2.21 resolve on the first build. Build dependency downloads require networking; the installed app operates offline. Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. Temporary SDK/Gradle installations used for milestone verification live under `/tmp` and may be deleted by the OS; configure a persistent SDK for subsequent development.

## Structure

- `MainActivity`: native UI, activity visibility, Bluetooth state/bond broadcasts and permission result handling.
- `bluetooth/BluetoothPermissions`: Android 12+ Nearby devices (`BLUETOOTH_CONNECT`); legacy Bluetooth permissions end at API 30. No scanning, discoverability request or location permission.
- `bluetooth/HidManager`: serial worker, HID profile/registration callbacks, paired devices, one host, explicit connection/disconnection, 12-second failure timeouts, cleanup and stale-session guards.
- `hid/MouseReports`: relative mouse descriptor and pure three-byte report/button encoding; JUnit tests.
- `ui/TouchpadView`: single-pointer relative motion, density normalization and subpixel accumulation. `ui/LeftClickButton`: held touch and complete accessibility clicks.

HID uses three button bits (only left is used), five padding bits, signed relative X/Y in [-127,127], and no report ID. Report and boot mouse protocols use the same three-byte format. No wheel or keyboard fields. Registration success is confirmed by callback and does not imply connection. The Android API permits one registered HID app and unregisters apps that leave the foreground. BluePad releases buttons, disconnects, unregisters and closes the profile on activity stop. Returning requires registration and reconnecting. Rotation also ends the current session.

## Physical-device verification — UNVERIFIED

No phone/computer compatibility claim is verified by a build or unit test. HID Device exposure, Bluetooth firmware, host behavior and policy can prevent registration or connection; rejection or timeout is shown in the UI. A timeout cannot distinguish every unsupported implementation from a temporarily unavailable service.

1. Install the debug APK on a real Android 9+ phone. Test an Android 12+ device as well for runtime permission behavior.
2. Enable Bluetooth on phone and computer. Pair using Android/computer Bluetooth settings and confirm the same pairing code. Keep BluePad visible during HID use. If pairing happened before HID registration, a host may require removing the bond and pairing again while BluePad is registered; switching to Settings stops the foreground session, so return and refresh/reconnect afterward.
3. Open BluePad; grant Nearby devices when prompted through the permission button. Confirm Bluetooth Enabled and HID Registered. Refresh paired devices, select the computer, tap Connect, and wait for Connected.
4. Drag the touchpad: check cursor direction and motion. Hold/release Left click and check normal clicking and dragging. Cancel a click by interrupting the touch sequence; verify the computer sees release.
5. Test Disconnect, Home, screen lock, rotation, Bluetooth disable, host disconnect, permission denial/revocation and reopening. Confirm no stuck button or unintended connection survives; reconnect deliberately.
6. Test missing/unsupported Bluetooth hardware/profile and conflicting HID apps. Confirm clear failure and retry behavior.
7. Repeat independently on Windows, Linux and macOS; record phone model, Android build, host OS/version, registration, pairing, connection, movement, click/release and recovery results. Do not infer one host's result applies to another.
8. Enable airplane mode with Bluetooth manually enabled, disable Wi-Fi, and repeat motion/click to demonstrate offline operation.

Bluetooth send success means the request was accepted, not proof that the host received it. Neutral release is attempted before disconnect; radio loss or OS process termination cannot guarantee delivery. Software clears local button state and neutralizes each new connection. Report failures stop input and trigger disconnect/timeout cleanup.

## References

- [BluetoothHidDevice API and foreground limitations](https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice)
- [Android Bluetooth permissions](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions)
- [AGP 8.13 compatibility](https://developer.android.com/build/releases/agp-8-13-0-release-notes)

Stop at Milestone 1. Next work: execute and record the hardware matrix, fix observed interoperability issues within this scope, then seek approval for a later milestone.
