# Windows 11 HID connection capture

Diagnostics-only addition; no changes to HID descriptors, connection/retry strategy, input behavior, or the hardware-tested V10 rejection recovery. Windows 11/QCA9377 compatibility remains unverified. Pairing and file transfer success alone do not establish a HID session.

Normal debug APKs now emit low-frequency lifecycle INFO logs under `BluePadConnection`, using uptime milliseconds and the registration-session generation. Release builds omit them. Existing verbose touch, report-byte and mouse diagnostics remain build-disabled. No diagnostic build flag or log-tag property changes are required for this capture. No keyboard text or report contents are logged.

Instrumented events: profile acquisition/request result; registration request/result and callback; host connect result including pre-request profile state; connection callback, sampled profile state, bonding/pause eligibility and ignored callback; GET_REPORT request/reply/error result; SET_REPORT metadata/response result; SET_PROTOCOL; virtual cable unplug; retry scheduling; deadline arm/expiry; cleanup reason; disconnect/unregister result and profile proxy close. Existing Bluetooth warnings/errors stay under `BluePadHid`. Connection and registration booleans reflect Android request acceptance, not remote completion.

On Ubuntu, with the Vivo connected by USB and authorized for ADB, install the APK using `adb install -r app/build/outputs/apk/debug/app-debug.apk`. Start capture before opening BluePad or requesting the Windows connection:

```sh
adb logcat -v threadtime -s BluePadConnection:I BluePadHid:W BluetoothHidDevice:I '*:S' | tee /tmp/bluepad-windows-hid.log
```

Keep BluePad foregrounded, select the bonded Windows host and request connection once. Allow the existing bounded retries to finish, then stop capture with Ctrl+C. Do not refresh repeatedly during capture. If multiple ADB devices are attached, add `-s <Vivo serial>` immediately after `adb`.

Expected evidence: profile/registration acceptance and registered callback; connect request/result; whether callback state 1 or 2 arrives; whether callback is filtered because it disagrees with the sampled profile state; whether Windows requests reports/protocol; whether the connection deadline expires before profile close. `deadline_expired operation=CONNECTION` followed by `profile_close reason=Connection timed out` establishes that BluePad's timeout causes that unbind. `profile_lost` or an unregistered callback before expiry indicates a different lifecycle path. Missing report/protocol callbacks alone do not prove host rejection; Android-side logs cannot establish Windows driver/SDP failure without corresponding Windows evidence.

The user reports all six V10 recovery hardware checks passed on Ubuntu. Preserve that working configuration. This capture is intended to locate the Windows failure stage, not assert a Qualcomm driver or descriptor defect.

Verification: all 213 unit tests passed, zero failures/errors/skips; lint passed with the existing AndroidGradlePluginVersion warning; debug build and APK v2 signature verification passed (one signer). APK size: 2,831,051 bytes. Gates ran sequentially offline with one worker and a 768 MiB heap. Logs: `/tmp/bluepad-v10-windows-testDebugUnitTest.log`, `/tmp/bluepad-v10-windows-lintDebug.log`, `/tmp/bluepad-v10-windows-assembleDebug.log`. No commit, tag or push.
