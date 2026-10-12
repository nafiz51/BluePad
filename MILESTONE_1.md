# Milestone 1 completion report

Verified on 12 October 2026 (Asia/Dhaka). Scope ends with the native Android mouse prototype.

## Environment

Workspace and newly initialized Git root: `/home/nafiz51/dev/BluePad`. Initial directory contained only `freebuff-chat-2026-10-12T15-19-16.665Z.md`, which is preserved unchanged and excluded from the milestone commit by explicit staging. No initial Git repository, Gradle executable, Android SDK, build tools or Kotlin compiler was found. OpenJDK 21.0.12.1 was available.

With approved network access, installed Gradle 8.13 and Android command-line tools under `/tmp`; installed SDK platform 36, build-tools 35.0.0 and platform-tools. Accepted SDK licenses for installation. Used pinned AGP 8.13.2, Kotlin 2.2.21 and JUnit 4.13.2, Java/Kotlin bytecode target 17. Minimum API 28; compile/target API 36. No application runtime dependencies beyond Kotlin/Android. Temporary tooling is not a durable SDK setup.

Sandbox restrictions blocked downloads, Gradle local socket coordination, ADB server startup and `.git` writes. Approved executions outside the sandbox resolved these environment blockers. No remaining compile/test/lint blocker.

## Implementation and architecture

MainActivity owns visibility, status display, paired-host selection, connect/disconnect, permission results and Bluetooth broadcasts. BluetoothPermissions owns API-version permission rules. HidManager runs Bluetooth operations and callbacks on a serial worker; generation guards discard stale callbacks, and profile/registration/connection timeouts surface unavailable services. Registration and connection are independently tracked from callbacks. One host is allowed at a time.

MouseReports owns a standard three-byte relative mouse descriptor/encoding, report ID zero, signed/clamped X/Y, and button state. TouchpadView handles single-pointer relative movement and cancellation; LeftClickButton handles held touch and complete accessibility clicks. No keyboard, wheel, media controls, custom themes, accounts, analytics or networking.

On stop, report failure, permission loss or service loss: clear local buttons, attempt a neutral release, disconnect/unregister and release the proxy as appropriate. Cleanup continues even when individual cleanup operations fail. Report failures disable input while disconnecting. Foreground registration is intentional; backgrounding or rotation ends the session. Registration never assumes automatic connection.

Android 9–11 use normal legacy Bluetooth permissions capped at API 30. Android 12+ request BLUETOOTH_CONNECT at runtime. No discovery is performed, so no scan/location permission is requested. Unsupported hardware, disabled Bluetooth, permission denial, rejected registration and unavailable/timed-out HID service are displayed. Host pairing occurs in OS settings.

## Files

All project files are newly created; no pre-existing file modified:

- `.gitignore`, `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`.
- `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties` (official Gradle distribution checksum pinned and downloaded archive verified).
- `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`.
- `app/src/main/java/com/bluepad/MainActivity.kt`.
- `app/src/main/java/com/bluepad/bluetooth/BluetoothPermissions.kt`, `HidManager.kt`.
- `app/src/main/java/com/bluepad/hid/MouseReports.kt`.
- `app/src/main/java/com/bluepad/ui/TouchpadView.kt`, `LeftClickButton.kt`.
- `app/src/main/res/values/strings.xml`, `res/drawable/ic_mouse.xml`, `res/xml/data_extraction_rules.xml`.
- `app/src/test/java/com/bluepad/hid/MouseReportsTest.kt`.
- `README.md`, `MILESTONE_1.md`.

Untracked/ignored generated files: local.properties, Gradle caches and build outputs. Debug signing material and downloaded tools are excluded from Git.

## Verification

Final command (using the downloaded Gradle distribution and temporary Gradle cache):

```
GRADLE_USER_HOME=/tmp/bluepad-gradle-home /tmp/bluepad-tools/gradle-8.13/bin/gradle --offline :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --console=plain
```

- Kotlin/Android compilation: PASS.
- JUnit: PASS, 4 tests, no failures/errors/skips. Tests cover neutral reports, signed coordinates/clamping, press/release/reset, repeated press and idempotent release. These are pure state/encoding tests, not Android event or Bluetooth integration tests.
- Lint: PASS, zero errors and one warning that a newer Gradle version exists. Compatible Gradle 8.13 is intentionally pinned. No lint baseline or blanket disabled checks.
- Debug APK: PASS; `app/build/outputs/apk/debug/app-debug.apk`.
- APK signature: PASS, APK Signature Scheme v2.
- APK permission inspection: only BLUETOOTH (max API 30), BLUETOOTH_ADMIN (max API 30) and BLUETOOTH_CONNECT; no Internet permission.
- APK SHA-256: `c650066b3ba92c4dd5d38426e951a3d0002c30ced5626dc1e12e8d6ec9e79cf1`.
- ADB enumeration: no device attached. No emulator or physical UI run performed.

Reports: `app/build/reports/tests/testDebugUnitTest/index.html` and `app/build/reports/lint-results-debug.html`. Build transcript: `/tmp/bluepad-build-final.log`. An earlier lint failure in the enable-Bluetooth permission handling was fixed before final verification.

## Unverified behavior and limitations

**UNVERIFIED on hardware:** HID profile exposure/registration, host pairing/connection, movement, left click, cancellation recovery, permission revocation recovery, disabled-radio handling, lifecycle cleanup and Windows/Linux/macOS compatibility. Software implementation and successful build are not evidence of these real-world behaviors.

Only one Android HID app may register. Foreground requirements, device vendor Bluetooth implementations and computer Bluetooth policies can prevent operation. Pairing may require bond recreation after the host caches a different service description. Service timeouts do not prove permanent lack of HID support. Bluetooth request acceptance does not prove delivery; release cannot be guaranteed after radio loss/process termination, though software clears local state and neutralizes new connections. Large individual movement samples saturate at report bounds. Prototype is English-only. There is no automatic reconnect or background service.

## Hardware instructions and next steps

Follow the numbered physical-device checklist in README.md: pair through system settings, grant permission, confirm Registered, select host, explicitly connect, check cursor/click/cancel, then test Home/lock/rotation/radio disable/permission revoke/reconnect and offline use. Record phone/Android build and host/OS separately for Windows, Linux and macOS. No universal compatibility claim.

Next: install a persistent SDK, execute the hardware matrix and fix observed Milestone 1 failures before considering further features. Milestone 2 has not started.

## Git

Milestone commit is made only after successful applicable verification. Obtain its hash from `git log -1 --format=%H`; the final chat completion includes the hash. Existing unrelated Markdown remains untracked. Generated APK, local paths, caches and signing keys are not committed.
