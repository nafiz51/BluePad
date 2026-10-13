# BluePad

BluePad turns an Android phone into an offline Bluetooth Classic HID touchpad and keyboard. **Made by Nafiz51** — Settings → Credits displays the developer credit.

Version **1.3.0**, Android version code **5**. Android 9/API 28 minimum; compile/target API 36. No Internet permission, companion desktop application, root, or runtime library dependencies.

## Supported configuration

The user has hardware-verified Vivo V2027 → Ubuntu, including cursor movement, keyboard, gestures, reconnection, both themes, Credits, and finalization milestones 1–4. Realme C30 (RMX3581), Android 11 → Ubuntu is also hardware-verified. Its initial failure cleared after restarting Ubuntu Bluetooth, and subsequent reconnection works without repeating that procedure. The original cause remains unconfirmed; other devices and host configurations need independent testing.

**Windows 11 HID connection remains unresolved and compatibility is deferred.** Successful pairing or file transfer does not prove HID support. macOS is not hardware-verified. Do not advertise general cross-platform compatibility.

## Features and defaults

- Single-finger movement; tap-to-left-click; two-finger right-click; moderate double-click; double-tap-and-hold dragging.
- Dedicated left/right buttons with holds, two-finger vertical scrolling and a locked 12% right-edge scroll strip.
- Pinch zoom: Standard uses Ctrl+wheel; advanced macOS compatibility uses Command+plus/minus. Both are application-defined shortcuts, not native multitouch HID.
- Default Android keyboard/IME, modifiers and special-key controls; keyboard remains above the IME with the touchpad still usable.
- Midnight Blue and Soft Blue, toolbar theme toggle, System/Light/Dark settings, IME-first Back navigation and Credits.
- Foreground-only, bonded-host reconnection with bounded retries and input-session fencing.
- v1.3.0 adds HID connection-state reconciliation, recovery from missing/delayed callbacks, bounded disconnection handling and local Copy Diagnostics after failure.

Fresh defaults: Dark appearance, cursor sensitivity 3.5× (range 0.5×–8×), scroll sensitivity 2.5× (range 0.5×–3×), Natural Scrolling ON and automatic reconnect ON. Saved valid preferences survive updates. Zoom Compatibility defaults to Standard.

## Installation and updates

The current final verification artifact is the **debug-signed APK**, not a production-key-signed distribution:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Enable USB debugging and authorize the computer first. `-r` updates the same package while retaining its data. Do not uninstall, clear app storage or delete pairing data during an update. A matching signing identity is required; a certificate mismatch must be resolved through the existing key, not by recommending data-erasing installation steps. Reopen BluePad after the update and reconnect if necessary.

APK: `app/build/outputs/apk/debug/app-debug.apk`. Installation from a copied APK requires allowing installation from that source on Android.

## Bluetooth pairing and connection

1. Enable Bluetooth on the phone and Ubuntu. Pair through their system Bluetooth settings and confirm the displayed pairing code. BluePad lists bonded devices; it does not scan or pair hosts itself.
2. Open BluePad and keep it foregrounded. Tap the toolbar Bluetooth control: missing Nearby devices permission (Android 12+) and Bluetooth enablement are requested through Android dialogs automatically. Denial/cancellation does not loop; use Retry or the explained App Settings action.
3. Once permission and Bluetooth are available, view paired devices, wait for HID registration, select Ubuntu and tap Connect. An empty list explains how to pair in Android Settings and return/Refresh. Only an actual HID Connected callback or eligible HID-profile CONNECTED snapshot establishes an input session; pairing alone is insufficient.
4. Automatic reconnect uses the last successfully connected bonded host. Explicit Disconnect pauses it; manual Connect/Reconnect or re-enabling automatic reconnect resumes it.
5. If registration or connection fails, inspect the displayed error and use the existing Refresh/Connect controls. Do not routinely delete bonds. Previous descriptor changes sometimes required pairing/service refresh; no descriptor change is included in v1.3.0. After a failure, Copy Diagnostics copies a bounded connection timeline for private troubleshooting; it contains no host identifiers or typed text and is never uploaded automatically.

Backgrounding, locking and rotation can end the foreground HID session; reopening uses the existing registration/recovery path. No background connection guarantee is made. Neutral releases are best-effort when radio/transport is available; software also clears local state and prevents stale queued input from crossing sessions.

## Keyboard limitations

The IME generates **US physical HID key usages**; the computer's active keyboard layout determines resulting characters. Supported commits include US-mapped letters, numbers, punctuation, space, Enter and Tab. Special-key controls include Backspace, Escape, Delete and arrows, with Ctrl/Alt/Shift/Super modifiers.

- Bangla, emoji and arbitrary Unicode are not supported by this mapping, regardless of which Android keyboard is installed.
- Composition remains local until committed; unsupported correction/replacement and local clipboard operations are restricted. Use host-side keyboard shortcuts for supported copy/paste operations.
- Final commits above 512 UTF-16 code units or containing unsupported characters are rejected atomically. Oversized commits are checked before key-list construction. Use Release all keys to reset blocked input.
- Editor history and an active composing draft can grow in unusually long continuous sessions. Closing/reopening the keyboard or Release all keys clears local text through the existing reset path. No history trimming or composition cap is imposed.
- v1.2.0 improves touchpad double-click reliability with 40 ms synthetic click edges, verified in all eight Vivo-to-Ubuntu hardware checks. The recognition window and dedicated-button timing are unchanged; broader device timing behavior remains unverified. Scrolling is not equivalent to a laptop precision touchpad. Zoom is suppressed while user keys/modifiers or mouse buttons are held.

## Build and verification

Use JDK 17 or 21, Android SDK platform 36 and build-tools 35.0.0. Configure a persistent SDK using untracked `local.properties` or `ANDROID_HOME`. Gradle 8.13, AGP 8.13.2 and Kotlin 2.2.21 are pinned. Temporary verification toolchains under `/tmp` are not a permanent setup.

Run the gates sequentially with conservative memory:

```sh
./gradlew --no-daemon --max-workers=1 -Dorg.gradle.jvmargs='-Xmx768m -Dfile.encoding=UTF-8' -Pkotlin.compiler.execution.strategy=in-process testDebugUnitTest
./gradlew --no-daemon --max-workers=1 -Dorg.gradle.jvmargs='-Xmx768m -Dfile.encoding=UTF-8' -Pkotlin.compiler.execution.strategy=in-process lintDebug
./gradlew --no-daemon --max-workers=1 -Dorg.gradle.jvmargs='-Xmx768m -Dfile.encoding=UTF-8' -Pkotlin.compiler.execution.strategy=in-process assembleDebug
apksigner verify --verbose --print-certs app/build/outputs/apk/debug/app-debug.apk
```

Normal builds disable verbose touch/HID diagnostics even if Android DEBUG properties remain enabled. Debug APKs contain low-frequency connection logcat diagnostics. Both build types retain the local connection timeline and Copy Diagnostics feature. Diagnostic-only builds can explicitly opt in with `-PbluepadDiagnostics=true`; do not use them to judge normal cursor performance. Logs do not contain typed keyboard text. Connection lifecycle logs and the copied report omit host names and MAC addresses. The report includes Android version, manufacturer/model, adapter state and the newest 200 connection events. It is kept in memory only; restarting/recreating the Activity clears it. Copy explicitly writes to Android’s clipboard, whose system-level retention or synchronization is outside BluePad’s control.

## Signing and release boundary

The existing debug certificate supports updates to the current verified installation. Preserve the existing keystore securely outside Git; regenerating the usual debug keystore produces a different identity. The finalization process does not generate keys, configure Google Play or configure production signing.

A broadly distributed release should use an explicitly approved durable signing identity and a tested migration/distribution plan. Switching to a different certificate is not an in-place update to the current installation. Do not ship private keys or passwords in source control. Future updates must preserve the application ID/certificate and increase version code above 5.

## Final verification

All 263 automated tests passed for v1.3.0 release preparation, including expanded descriptor, registration, connection reconciliation, deadline and diagnostic regression coverage. Lint, debug build and APK signature verification passed. Lint has one existing AndroidGradlePluginVersion warning. The APK package is com.bluepad, version 1.3.0/code 5; its signing certificate matches the published v1.2.0 APK. Vivo → Ubuntu and Realme C30 / Android 11 → Ubuntu are hardware-verified; this does not establish universal Bluetooth HID compatibility.

v1.2.0’s 40 ms synthetic click pacing remains unchanged. For v1.3.0, package/signing/version checks establish eligibility for an in-place update, but do not independently prove actual device data retention. During v1.0.0 finalization, Vivo was updated from code 1 to 2 using `adb install -r` with identical before/after preference-file hashes; that historical test is not a v1.3.0 update test.

Existing signer: Android Debug, RSA 2048-bit. Certificate SHA-256: `b0fdbbac3bb5a0dfb0dd0b81cb686aefdd58ea0b106f8a14fc5f91f04248bdae`. The existing keystore is `/home/nafiz51/.android/debug.keystore`; keep a secure backup outside Git. No key was generated or replaced.

## Documentation

- [Cross-device connection recovery](docs/CROSS_DEVICE_HID_RECOVERY.md)
- [Local connection diagnostics](docs/REALME_HID_DIAGNOSTICS.md)
- [Descriptor/SDP compatibility audit](docs/HID_PROTOCOL_COMPATIBILITY_AUDIT.md)
- [Double-click pacing and hardware checks](docs/V1_2_DOUBLE_TAP_EXPERIMENT.md)
- [Bluetooth onboarding](docs/V1_1_BLUETOOTH_ONBOARDING.md)
- [Final changelog](CHANGELOG.md)
- [HID rejection recovery](docs/V10_REPORT_REJECTION_RECOVERY.md)
- [Keyboard memory limits](docs/KEYBOARD_SESSION_MEMORY.md)
- [Usability/accessibility](docs/MILESTONE_3_USABILITY.md)
- [Performance/stability and hardware plan](docs/MILESTONE_4_STABILITY.md)
- [V9 design and quiet diagnostics](docs/V9_NATIVE_UI.md)
- [Historical Windows diagnostic capture](docs/V10_WINDOWS_HID_DIAGNOSTICS.md)

Earlier local checkpoints remain intact. Source, tests and documentation are intended for the final checkpoint; chat exports, captures, build artifacts and keys are excluded. v1.2.0 remains the published stable checkpoint. v1.3.0 is prepared following Vivo and Realme hardware testing; commit, tag and publication await explicit approval.
