# V5B touchpad-first adaptive layout

Status: the user confirmed the current V5B implementation passed real-device testing. Stable local checkpoint: `bluetooth-touchpad-keyboard-working-v5b`.

The application starts with the touchpad filling the space between a 48 dp toolbar and the compact equal-width Left Click/Right Click row. The previous tabs are removed. The toolbar Bluetooth status control opens the existing device selection/connection panel; the settings icon opens persisted 0.5–8× sensitivity. Each panel scrolls and is bounded to preserve touchpad space.

The keyboard icon shows/hides the existing default-IME input field. The touchpad and both physical buttons remain enabled while connected in either layout. Modifier and special-key controls occupy one horizontally scrollable strip rather than a second keyboard. Touchpad and toolbar controls do not request editor focus. Hiding the editor releases input safely; lifecycle interruptions retain existing Bluetooth cleanup behavior.

Android 11+ uses system-bar/cutout and IME insets, reserving the greater bottom inset rather than adding both. Android 9–10 uses `adjustResize`, fitted system windows and a visible-window detector for IME dismissal. The legacy detector uses a relative obscured-area threshold for visibility only; it never sets keyboard height. Floating keyboards and unusual legacy window modes require device testing. No fixed IME height or equal split is imposed. IME animation synchronization is not added.

The existing InputConnection wrapper and US HID mapping are unchanged. Ridmik and other installed keyboards can supply input, but Bangla/emoji/unmapped Unicode commits still are not supported by the HID bridge. This layout change does not introduce Unicode or remote-document synchronization, autocorrect or clipboard support.

The HID descriptor, report IDs, Bluetooth connection lifecycle, gesture recognition, motion/scroll calculations and sensitivity persistence are unchanged. A small physical-button manager entry point reuses the existing MouseButtons ownership/encoding for left bit 1 and right bit 2. Shared button contact handling tracks its pointer, emits ordered down/up events, and releases on cancellation, disabling and detachment. Holding either physical button suppresses conflicting tap gestures; releasing one does not release the other. Four state tests cover default mode, asynchronous show, external dismissal and repeated toggle/lifecycle reset, alongside the existing regression suite. Insets/focus behavior requires real-device verification; these pure tests do not exercise Android windows.

## Hardware checks

1. Install `app/build/outputs/apk/debug/app-debug.apk`. Launch: no IME or tab buttons should appear. Tap the Bluetooth toolbar control, connect using existing pairing and close the panel. Use settings to adjust sensitivity. Confirm the pad occupies nearly all available content space.
2. Tap the keyboard icon. Confirm Ridmik/default IME opens, icon stays above it, and the pad resizes without overlapping system bars. Type supported English text, Backspace and Enter into an Ubuntu editor; use the horizontal strip for modifiers/special keys.
3. While the keyboard remains open, move horizontally/vertically, tap-click, right-click, double-click/drag, hold Left Click while moving a second finger to drag, test Right Click tap/hold, hold both and release separately, and use both scroll methods. Touching the pad must not steal editor focus or dismiss the IME.
4. Close via the same icon, Android Back and the keyboard's own hide control. Each must restore the large pad and clear held modifiers/buttons without disconnecting Bluetooth. Reopen repeatedly; also test rapid open/close and settings access in both layouts.
5. Test different keyboard heights, Ridmik language/layout changes, another installed IME, portrait/landscape, gesture/three-button navigation and Android 9 if available. Rotation/Home/lock retain V5A's foreground-session interruption semantics; reconnect as before.

V5B is committed and tagged locally following user approval. Nothing was pushed. V5A remains recoverable at `bluetooth-mouse-keyboard-working-v5a`; all previous stable checkpoints are preserved.

## Verification results

- Latest toolbar/button update: 100 unit tests passed, with zero failures/errors/skips.
- Lint passed with zero errors and one existing Gradle-version warning. Android 13+ uses the platform Back callback; the legacy Back override was removed after lint identified Android 16 predictive-Back incompatibility.
- Debug APK build and APK signature verification passed.
- Tasks ran sequentially with one worker, a 768 MiB Gradle heap and in-process Kotlin compilation. Logs are `/tmp/bluepad-v5b-controls-tests.log`, `/tmp/bluepad-v5b-controls-lint.log` and `/tmp/bluepad-v5b-controls-apk.log`.
- Earlier adaptive-layout version was installed with app data preserved on the connected Vivo (API 31), before this toolbar/button update. UI hierarchy showed the touchpad bounds changing from `[0,152][720,1504]` to `[0,152][720,625]`, with the keyboard icon still accessible and the InputConnection field focused. Back restored the larger touchpad; same-icon toggling was exercised. This is a layout/focus smoke check, not certification of IME rendering or Bluetooth input. The user subsequently confirmed the final V5B version passed real-device testing. Coverage of other IMEs and API 28 was not separately specified.

The dedicated-button update adds five contact/cancellation/accessibility tests and two physical-button bitmask/ordering tests. The user has confirmed the current V5B implementation passed real-device testing; no application functionality was changed during closeout.
