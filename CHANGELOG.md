# Changelog

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
