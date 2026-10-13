# v1.1.0 Bluetooth onboarding

The old Bluetooth panel only requested permission/enablement from its extra button. Opening the panel now evaluates prerequisites and makes one supported native request at a time. Android 12+ needs BLUETOOTH_CONNECT; older supported versions use the existing manifest permissions. No scan/location permission, silent enablement or new dependency is added.

Missing permission triggers Android's permission dialog, followed by ACTION_REQUEST_ENABLE if Bluetooth is off. Already-authorized/enabled users get the paired-device list. An empty bonded list is hidden and a message explains system pairing and Refresh. No new automatic Connect call is introduced; the existing saved-host automatic-reconnect setting/engine remains unchanged.

Denial or enable cancellation blocks further automatic prompts, including later resumes/reopens. A manual retry remains available. An earlier request plus Android's rationale status identifies likely permanent denial and offers an explained App Settings action; Android ultimately decides whether a permission request can show. Returning from the app's Settings action rechecks permission and can continue to enablement. Returning from other system Settings rechecks readiness without retrying denied/cancelled prompts. Existing settings/defaults are untouched; bookkeeping lives in a separate onboarding preferences file.

The outstanding request is saved in Activity state so rotation does not launch a duplicate. Completion persists denial/cancellation even if the user has left the connection panel. Requests are not launched while the Activity is stopped or while another request is outstanding. Lifecycle/HID ownership remains the existing foreground-only behavior; system screens may trigger stop/re-registration as before. Native callback delivery after rotation and OEM-specific permission/enable UI require physical verification.

Version name 1.1.0/code 3, application ID com.bluepad; publication approved following successful Vivo hardware testing. v1.0.0 remains the previous stable checkpoint.

## Vivo checks

1. On a separate fresh test state or deliberately revoked Nearby devices permission, open the app: no prompt on the touchpad. Tap Bluetooth: one permission prompt; grant and see the paired list.
2. Start with Bluetooth off; opening the panel should show the native enable dialog. Accept, select Ubuntu and Connect.
3. Deny permission/cancel enablement; reopen/resume repeatedly and confirm no prompt loop. Use explicit Retry.
4. Test repeated permission denial: App Settings explanation/action, return without granting (no loop), then allow and return (continue).
5. Rotate during permission/enable requests; complete them and confirm the panel remains usable without duplicate prompts.
6. With permissions/Bluetooth ready, go directly to the paired list; preserve existing paired hosts. An empty list can be tested on a separate test device; do not erase normal pairing data merely to test it.
7. Verify Ubuntu connection/recovery, cursor/gestures, keyboard open/close, theme switching, Credits and saved sensitivities. Saved-host auto-reconnect remains governed by the existing preference.

Unit tests exercise the deterministic onboarding model; they do not certify native Android dialogs or IME interaction. No Windows investigation or HID/input changes are included.

Verification: 231 unit tests passed, zero failures/errors/skips (223 existing plus eight onboarding state tests); lint passed with the existing AndroidGradlePluginVersion warning; debug build and APK v2 signature verification passed (one signer). Candidate APK: 2,809,422 bytes, version 1.1.0/code 3, existing debug certificate SHA-256 `b0fdbbac3bb5a0dfb0dd0b81cb686aefdd58ea0b106f8a14fc5f91f04248bdae`. Gates ran sequentially with one worker and a 768 MiB heap; existing pinned dependencies were retrieved where caches were missing. A missing temporary API 36 platform was restored under the existing configured /tmp SDK path. No repository toolchain configuration or signing key changed. Logs: `/tmp/bluepad-onboarding-testDebugUnitTest.log`, `/tmp/bluepad-onboarding-lintDebug.log`, `/tmp/bluepad-onboarding-assembleDebug.log`. Vivo hardware testing passed and publication is approved.
