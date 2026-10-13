# Changelog

## 1.3.0 — release candidate: connection stability and diagnostics

- Reconcile eligible HID-profile CONNECTED snapshots after registration, before connecting and at timeout, recovering from missing or delayed callbacks.
- Bound disconnection handling and preserve controlled retries, explicit-disconnect suppression and input-session fencing.
- Add local, bounded connection diagnostics with Copy Diagnostics after failure; omit host identities and keyboard input, with no automatic uploads.
- Expand HID descriptor, registration, Android permission-boundary and connection recovery regression coverage: 263 tests.
- Hardware verified on Vivo → Ubuntu and Realme C30 / Android 11 → Ubuntu. Realme’s initial failure cleared after restarting Ubuntu Bluetooth; later reconnections work without repeating it. The initial cause remains unconfirmed.
- Version 1.3.0/code 5; preserve package, signing identity, settings, descriptor/report formats, 12-second deadlines, three automatic retry attempts and 40 ms synthetic click spacing.
- Windows HID compatibility remains unresolved; macOS and universal Android-device compatibility are not verified.
- Prepared locally only; commit, tag and publication require separate approval.

## 1.2.0 — touchpad double-click reliability

- Increase synthetic touchpad click-edge spacing from 20 to 40 ms; recognized double-click edges are nominally at 0/40/80/120 ms.
- Preserve the 120 ms first-click delivery deadline, recognition window, immediate dedicated buttons and dragging behavior. Synthetic release completes 20 ms later.
- Add eight timing/ordering/cancellation regressions; 239 tests and all eight Vivo-to-Ubuntu hardware checks passed.
- Version code 4; package, existing signing identity, saved preferences and HID engine remain unchanged.
- Actual host delivery spacing under transport backlog is not guaranteed. Broader compatibility, keyboard mapping and long-session limits remain.
- Published as a GitHub pre-release; preserved as the previous stable checkpoint.

## 1.1.0 — Bluetooth onboarding

- Opening the Bluetooth panel requests missing permission and Bluetooth enablement using native Android dialogs.
- Continue to paired devices once ready; explain empty lists and retain explicit Connect. Existing saved-host auto-reconnect is unchanged.
- Prevent prompt loops after denial/cancellation; provide manual Retry and App Settings guidance for blocked permission.
- Restore outstanding requests across Activity recreation.
- Preserve HID, gestures, keyboard, themes, Credits, preferences and the existing signing identity.
- Version code 3; 231 automated tests and successful Vivo hardware approval. Broader compatibility limitations remain.

## 1.0.0 — finalization candidate

Based on hardware-verified V9 with approved V10 and milestones 1–4:

- Harden HID report-rejection recovery: publish/fence before disconnect, handle rejected disconnect immediately and preserve bounded retry/timeout cleanup.
- Add low-frequency debug-only connection diagnostics while keeping verbose input logging disabled.
- Add Settings → Credits with the exact message “Made by Nafiz51”, native OK/Back dismissal and both themes.
- Reject oversized keyboard commits before allocating mapped key lists, preserving the existing 512-unit final-commit policy and atomic output.
- Let existing keyboard controls expand for scaled text; add explicit special-key accessibility labels and Zoom Compatibility description.
- Add deterministic long-session movement, keyboard report ordering and input-session cleanup regressions; 223 tests at the approved milestone baseline.
- Document keyboard memory limits, usability checks and measured stability/hardware procedures.
- Set Android version name 1.0.0 and version code 2; retain application ID, signing identity and saved preferences.

Vivo-to-Ubuntu is hardware-verified. Windows 11 compatibility is unresolved/deferred; macOS is unverified. Very-fast double-tap, US HID layout, continuous editor-history growth and discrete zoom/scroll limitations remain. This candidate is debug-signed; production signing and publication are not configured.

No Bluetooth descriptors, working input algorithms, approved themes or preferences are changed by finalization.
