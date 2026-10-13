# BluePad — Android Bluetooth mouse

Native offline Android Bluetooth Classic HID mouse prototype. Android 9/API 28 minimum; compile/target API 36. No Internet permission, runtime libraries, root, or PC companion software. Single-finger pointer movement, adjustable sensitivity, two-finger vertical scrolling, a locked one-finger right-edge scroll strip and left click are supported. V4 adds main-area tap-to-left-click, two-finger tap-to-right-click and double-tap-and-drag (final hardware tests passed on Vivo/Ubuntu). V5A adds composite mouse/keyboard HID support and native default-Android-IME input with US HID mapping, hardware-verified by the user on Vivo/Ubuntu. Universal Unicode support, horizontal scrolling, other gestures and background operation remain outside the current scope.

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
- `hid/MouseReports`: relative mouse descriptor and pure four-byte report/button/wheel encoding with three-byte boot-mode adaptation; JUnit tests.
- `ui/TouchpadView`: density-independent pointer/scroll input with frame batching and fractional accumulation; `TouchpadMotion` holds testable gesture math. `ui/LeftClickButton`: held touch and complete accessibility clicks.

HID uses three button bits (only left is used), five padding bits, signed relative X/Y/wheel in [-127,127], and composite mouse report ID 2. Report protocol uses four bytes; boot protocol uses three-byte buttons/X/Y with the Bluetooth boot mouse ID 2 and disables scrolling. The composite keyboard uses report ID 1 and an eight-byte input payload. Registration success is confirmed by callback and does not imply connection. The Android API permits one registered HID app and unregisters apps that leave the foreground. BluePad releases buttons, disconnects, unregisters and closes the profile on activity stop. Returning requires registration and reconnecting. Rotation also ends the current session.

## Physical-device verification

The user reports successful connection, cursor movement, sensitivity, left click and scrolling on Vivo/Ubuntu after refreshing Bluetooth pairing/service recognition. Other combinations remain unverified; a build or unit test does not prove hardware compatibility. HID Device exposure, Bluetooth firmware, host behavior and policy can prevent registration or connection; rejection or timeout is shown in the UI. A timeout cannot distinguish every unsupported implementation from a temporarily unavailable service.

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

The user has confirmed Bluetooth connection, cursor movement, adjustable sensitivity, left click and two-finger scrolling on a real Vivo phone with Ubuntu. Scrolling required refreshing Bluetooth pairing and HID service recognition; see [touchpad update notes](docs/TOUCHPAD_UPDATE.md) for protocol changes, verification and recovery. Further features require approval.

The user confirmed V3 right-edge one-finger scrolling, normal cursor movement, two-finger scrolling and existing touchpad features on Vivo/Ubuntu, with no additional issues observed; see [scroll strip notes](docs/SCROLL_STRIP.md).

V4 implementation and validation notes: [advanced gestures](docs/V4_GESTURES.md). Final V4 hardware testing passed on Vivo/Ubuntu, including the [scrolling refinement](docs/V4_SCROLL_OPTIMIZATION.md). Scrolling remains less smooth than a laptop precision touchpad; this is an accepted V4 limitation.

V5A is hardware-verified by the user and recorded by the local annotated tag `bluetooth-mouse-keyboard-working-v5a`; see [keyboard foundation](docs/V5A_KEYBOARD_FOUNDATION.md) and [connection audit](docs/V5A_CONNECTION_AUDIT.md). The audit preserves earlier failure evidence without claiming a proven host-side root cause.

Pointer sensitivity now ranges from 0.5× to 8× in 0.1× steps (default 1×), preserving existing saved values; scrolling speed is independent. The current V5A version has been hardware-verified by the user.

V5B replaces tabs with a touchpad-first screen and a keyboard toolbar toggle. The touchpad remains active above the default Android IME; the Bluetooth status control opens device connection controls, while settings contains sensitivity. Compact Left Click/Right Click buttons support holds below the pad. See [adaptive layout and hardware checks](docs/V5B_ADAPTIVE_LAYOUT.md). V5B passed real-device testing as confirmed by the user and is recorded by the local annotated tag `bluetooth-touchpad-keyboard-working-v5b`; the existing US HID text limitations remain.

V6 adds locked pinch/scroll classification and a persistent Host OS setting: Ctrl+wheel zoom for Windows/Linux, Command+Plus/Minus for macOS. Zoom is skipped while user keys/modifiers or mouse buttons are held. V6 is accepted as a stable local checkpoint at `bluetooth-touchpad-keyboard-working-v6`. Natural very fast double-taps may fail on Ubuntu; moderate-speed double-taps and double-tap-and-drag work. This is an accepted V6 limitation. Windows/macOS still require independent hardware testing; see [implementation and exact hardware procedure](docs/V6_PINCH_ZOOM.md).

V7 adds foreground-only reconnection to a callback-confirmed bonded host, bounded retries, explicit-disconnect suppression and session-gated input cleanup. Automatic reconnection is enabled by default; Settings controls it and the connection panel offers Reconnect last host. V7 passed all 10/10 hardware checks on Vivo Android and Ubuntu, including recovery after toggling laptop Bluetooth, and is recorded by the local annotated tag `bluetooth-touchpad-keyboard-working-v7`; see [connection reliability and test procedure](docs/V7_CONNECTION_RELIABILITY.md).
