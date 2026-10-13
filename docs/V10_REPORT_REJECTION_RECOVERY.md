# V10 phase 2: report-rejection recovery

Status: all six V10 Vivo/Ubuntu hardware checks passed as reported by the user; final local checkpoint awaits approval. V9 remains the stable checkpoint.

## Corrected audit finding

V9 already published Connection failed and armed a 12-second RECOVERY deadline after report rejection. The Phase 1 audit incorrectly stated those were absent. The confirmed narrower gaps were ignoring the disconnect return value and performing neutral sends/disconnect before publishing and arming recovery.

## Targeted changes

HidManager now fences the input session, clears mouse/keyboard state, marks recovery in progress, publishes failure and arms the existing deadline before invoking the transport. Neutral reports remain best-effort direct sends, avoiding recursive recovery. Normal report submission returns immediately if disconnected or already disconnecting. A small internal ordering helper permits deterministic tests without Android transport mocks.

- Accepted disconnect plus callback: existing callback cancels the deadline, clears the host and enters the existing reconnect policy.
- Accepted disconnect without callback: the existing 12-second RECOVERY deadline closes registration/profile and enters bounded retry handling.
- Rejected or throwing disconnect: close registration/profile immediately, invalidate the callback generation and enter bounded retry handling; do not repeat the rejected disconnect request in that cleanup.
- Repeated rejection: no duplicate normal report or recovery sequence while disconnected/disconnecting.
- Profile loss: existing generation-scoped service-loss cleanup cancels recovery and retries only when eligible.
- Explicit disconnect: existing persistent pause suppresses retries, including after a recovery timeout; the outstanding recovery remains bounded.
- Bluetooth off, permission unavailable or Activity stop: existing eligibility/session cleanup cancels or prevents retry.

Automatic reconnect remains capped at three attempts per existing policy series. Recovery itself uses the existing deadline, not an extra timer. Each automatic attempt has its existing bounded operation deadlines; recovery does not guarantee a reachable host. Old queued input is rejected by the existing session token and connected-state checks. No descriptor, report format, input timing, keyboard lifecycle, UI/theme or logging-default change is included.

## Verification scope and hardware checks

Regression tests exercise effect ordering, immediate local input fencing/clear, accepted and rejected disconnect outcomes, duplicate suppression, retained deadline ownership, cleanup fencing, explicit pause and retry exhaustion. These are deterministic helper/policy tests, not Android Bluetooth callback integration or host-delivery proof.

Install the normal debug APK with `adb install -r app/build/outputs/apk/debug/app-debug.apk`, retaining bonds/settings. Check movement, typing, clicking, dragging, scrolling and zoom first. While moving or dragging, toggle laptop Bluetooth off/on; confirm controls stop accepting input during recovery, no held buttons/modifiers remain, and eligible reconnection resumes without old input. Repeat with automatic reconnection off and with an explicit Disconnect during recovery; neither should reconnect automatically. Test phone Bluetooth off/on and Activity background/foreground cleanup.

Capture warnings/errors with `adb logcat -v threadtime -s BluePadHid:W BluePadUi:W '*:S'`. A Bluetooth interruption does not guarantee Android will reject sendReport or disconnect; the specific rejection/absent-callback branches remain deterministically tested until observed on hardware. No fault-injection feature is added to the application.

Verification results: 213 unit tests passed, zero failures/errors/skips (206 existing plus seven recovery tests). Android lint passed with zero errors and the existing AndroidGradlePluginVersion warning. Debug build and APK v2 signature verification passed (one signer). APK size: 2,816,147 bytes. Gates ran sequentially offline with one worker and 768 MiB heap. Logs: `/tmp/bluepad-v10-testDebugUnitTest.log`, `/tmp/bluepad-v10-lintDebug.log`, `/tmp/bluepad-v10-assembleDebug.log`. No commit, tag or push performed.
