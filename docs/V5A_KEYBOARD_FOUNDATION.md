# V5A — Bluetooth keyboard foundation

Status: **current V5A hardware-verified by the user on Vivo/Ubuntu**, closing out under local annotated tag `bluetooth-mouse-keyboard-working-v5a`. Latest automated verification: 89 tests passed, lint zero errors (one existing warning), debug build and APK signature verification passed. Earlier failure evidence and the controlled diagnostic procedure remain in [V5A_CONNECTION_AUDIT.md](V5A_CONNECTION_AUDIT.md). V4 recovery commit/tag remain unchanged: `d25d17637e638bc699be27d8eed5def2e412c494` / `bluetooth-touchpad-working-v4`.

## Architecture

- `CompositeReports` builds a composite descriptor from the original mouse descriptor and a standard keyboard application; owns active report IDs and report/boot input routing and keyboard LED parsing.
- `KeyboardReports` encodes eight-byte input. `UsKeyboard` maps US physical usages and required Shift. `KeyboardState` tracks ordered unique non-modifier keys and modifier bits, supports down/up, temporary-modifier taps, duplicate suppression and six-key rollover rejection.
- `HidInputState` owns the existing MouseButtons and the keyboard state. Its shared releaseAll contract clears both and returns neutral reports; manager screen-switch, disconnect, unplug, failure and shutdown paths use it.
- `HidManager` retains serial execution, registration/connection callbacks, foreground lifecycle, stale-session guards and timeouts. Mouse sends use ID 2; keyboard sends use ID 1. Registration advertises the combo keyboard/mouse subclass. Report failure attempts neutral reports for both devices, clears local state, disables input and disconnects. Every new connection starts neutral.
- `KeyboardView` opens the installed default Android IME through a native EditText/InputConnection wrapper. There is no custom alphabet grid. A small toolbar retains special keys, arrows, Ctrl/Alt/Shift/Super toggles and Release all keys. `ImeTextBridge` sends supported committed US-mapped text, deletion and Enter; composing text remains local until committed. Unsupported Unicode, replacement/autocorrect and local clipboard editing are not synchronized with the host.
- `MainActivity` hosts Touchpad/Keyboard in the same activity. Switching cancels gestures and releases both devices but does not stop registration or disconnect. The original touchpad code and settings persist. Controls are disabled while disconnected/backgrounded. External keyboard touch cancellation releases input; modifier toggles also clear on tab switch, disconnect and activity stop.

No new dependencies, privileges or permissions. Minimum API remains 28. Mouse payload encoding, descriptor fields, gestures, scroll quantum/precision, sensitivity persistence and physical click ownership are preserved. The only necessary mouse protocol change is assigning a Report ID to coexist with a numbered keyboard.

## Composite descriptor and payloads

| Function | Report ID | Type | Payload (excluding ID) |
| --- | --- | --- | --- |
| Mouse, report protocol | 2 | Input | 4 bytes: buttons, signed relative X, Y, wheel |
| Mouse, boot protocol | 2 | Input | 3 bytes: buttons, X, Y; no wheel |
| Keyboard, either protocol | 1 | Input | 8 bytes: modifier mask, reserved zero, six usage slots |
| Keyboard LEDs | 1 | Output | 1 byte: Num/Caps/Scroll/Compose/Kana bits plus padding |

Both application collections use nonzero IDs; no mixed numbered/unnumbered input is registered. CompositeReports inserts `85 02` into the original mouse application, preserving every other byte. The keyboard declares Generic Desktop Keyboard, Keyboard usage page, eight E0–E7 modifier bits, one reserved byte and six eight-bit array usages. LED output uses five LED bits and three padding bits.

`sendReport(host, id, payload)` supplies the ID separately; no ID or Bluetooth transaction header is prepended to payload. Report mode changes mouse ID from V4's 0 to 2. Boot uses the fixed Classic mouse ID 2 and keyboard ID 1. `MouseReports.reportId()` remains a legacy standalone regression helper; active composite routing uses CompositeReports constants.

GET_REPORT returns current keyboard/mouse input or cached keyboard LED output, respecting buffer size. SET_REPORT accepts a valid one-byte keyboard OUTPUT report and acknowledges success, or reports an error. Interrupt LED output updates the cached LED mask; SET_PROTOCOL retains boot/report adaptation. No feature reports are declared. Keyboard contents are not printed by transport logs.

## Extended pointer sensitivity

PointerSensitivity defines 0.5×–8× in 0.1× slider steps, default 1×. The existing `touchpad` preferences and `sensitivity` Float key are retained; all saved 0.5×–3× values remain valid. Restore uses rounding for slider position so floating-point representations do not select the preceding step. The label continues to show one decimal place. Saving on slider release and activity stop is unchanged.

TouchpadMotion accepts the new maximum while retaining fractional accumulation, per-report [-127,127] bounds and overflow carried to subsequent frames. Large fast swipes can require several frames to drain at 8×; no distance is intentionally clipped, but real-device responsiveness at high sensitivity needs testing. Scroll tick distance remains 20dp, independent of pointer sensitivity. No composite descriptor, report ID, keyboard engine or Bluetooth connection changes were made for this sensitivity extension.

Seven new tests cover every slider step/default, legacy saved values/invalid saved Float recovery, fractional 8× movement, large signed motion bounded to report limits with exact total-distance preservation, cancellation of overflow, scrolling independence and range rejection. Add 0.5×/1×/3×/8× movement and restart-persistence checks to the Vivo/Ubuntu test.

## Keys and state rules

Supported: A–Z, 0–9; US `- = [ ] \\ ; ' backtick , . /` and their shifted symbols; `! @ # $ % ^ & * ( )`; Space, Enter, Backspace (Bksp), Tab, Escape, Delete, four arrows. UI exposes left Ctrl, Alt, Shift and Super/Windows. The reusable engine supports all eight left/right modifier bits and simultaneous combinations.

Use Ctrl toggle then C/V/A for shortcuts; turn Ctrl off afterward. Alt + Tab works similarly; release Alt to complete the host switcher action. Multiple toggles combine their bits. Keys in the test grid are atomic press/release taps. Temporary Shift needed for a symbol is combined with existing modifiers and removed after the tap, restoring them exactly.

Duplicate key-down and unmatched key-up are no-ops. Up to six unique non-modifier keys can be held through the engine's keyDown/keyUp API. A seventh is rejected without changing the first six (no rollover error flood); release a held key before trying another. A tap of an already held key, or a tap when all six slots are occupied, is rejected to prevent releasing an unrelated hold. The UI grid sends individual taps, not simultaneous six-key holds or a custom repeat timer; rollover is covered by automated engine tests.

US mappings are physical usages, not Unicode text. The host's layout, Caps Lock and application shortcuts determine resulting characters. Non-US layouts, dead keys, composition, emoji, full Unicode and Gboard integration are not supported in V5A. Sticky modifier toggles are intentional holds until turned off/released/interrupted. Ctrl-plus-mouse chords across tabs are not supported by this UI, because tab switching deliberately releases modifiers.

## Verification and changed files

All **74 unit tests pass**: original 48 V4 tests, 14 keyboard tests, five composite/input-state tests and seven sensitivity tests. Tests verify descriptor collection/ID/bit-length consistency, preservation of mouse bytes, boot lengths, LED validation, all mappings/modifiers, ordered/repeated key taps, six-key rejection, duplicate states, temporary Shift restoration and shared cancellation/disconnect/tab-switch reset. Existing gesture/scroll tests remain unchanged and passing.

Tests exercise pure engine/routing and the manager's shared release contract; they do not automate real Android view interaction or Bluetooth callback delivery. Physical composite interoperability and UI usability remain UNVERIFIED.

```
GRADLE_USER_HOME=/tmp/bluepad-gradle-home /tmp/bluepad-tools/gradle-8.13/bin/gradle --offline :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --console=plain
/tmp/bluepad-sdk/build-tools/35.0.0/apksigner verify --verbose app/build/outputs/apk/debug/app-debug.apk
```

Compilation/tests/lint/debug APK/signature: PASS. Lint: zero errors, one existing newer-Gradle notice. Build transcript: `/tmp/bluepad-v5a-sensitivity-build.log`. Exact APK path: `/home/nafiz51/dev/BluePad/app/build/outputs/apk/debug/app-debug.apk`.

Created: `hid/CompositeReports.kt`, `hid/KeyboardReports.kt`, `hid/HidInputState.kt`, `ui/KeyboardView.kt`, `test/.../hid/KeyboardReportsTest.kt`, `CompositeReportsTest.kt`, this document.

Additional sensitivity files: `ui/PointerSensitivity.kt`, `test/.../ui/PointerSensitivityTest.kt`; TouchpadMotion validation and MainActivity slider/restore updated.

Modified: MainActivity, HidManager, MouseReports comments, resource strings, README. TouchpadView, TouchpadGestures, LeftClickButton, permissions and build dependencies are unchanged. Generated APK/caches/local SDK configuration remain ignored; the unrelated chat export is untouched/untracked. No commit, tag, push or V5B feature is included.

## Ubuntu pairing and hardware procedure

This is an intentional descriptor change. The latest supplied Ubuntu cache already contains V5A, so an old V4 cache must not be assumed. Follow the connection audit's single controlled retest; do not delete pairing data or modify Ubuntu configuration. Keep BluePad registered/visible during input. Changing to Android Settings stops foreground registration. Host-side connection can be initiated while BluePad stays visible.

1. Enable USB debugging and authorize the computer. Install with:

   ```sh
   /tmp/bluepad-sdk/platform-tools/adb install -r /home/nafiz51/dev/BluePad/app/build/outputs/apk/debug/app-debug.apk
   ```

2. Follow the connection audit before feature testing. If connected, confirm both mouse and keyboard are recognized using `/proc/bus/input/devices` and the current NBM HID sysfs report_descriptor to verify IDs 2 and 1. Do not reuse a historical event number.
3. Select Keyboard, focus a plain text editor on Ubuntu, set the host layout to US, and test alphabet/digits, spaces, Enter, Backspace, Tab/Escape/Delete and arrows. Test punctuation and shifted symbols, Shift+A, multiple modifiers and repeated taps. Keyboard screen scrolling should not send mouse wheel movement or characters by itself.
4. Test Ctrl+A/C/V in an editor, Alt+Tab and Super according to Ubuntu bindings. Check modifier feedback, toggle-off behavior and Release all keys. Host shortcuts/layout affect results; no companion PC program is required.
5. Switch repeatedly between Keyboard and Touchpad while connected. Verify each switch releases modifiers and any ongoing mouse drag, registration remains Registered, connection remains Connected, and both functions work afterward.
6. Retest all V4 features: cursor/sensitivity persistence, single tap, right-click, double-click/drag, physical-button dragging, both scrolling modes, small movements/reversals and strip locking.
7. With Ctrl/Alt/Shift/Super selected, interrupt via Home, lock, rotation, Bluetooth disable or host disconnect. Reconnect and type plain letters; verify no sticky modifiers/keys. Test external touch cancellation and app reopening. Repeat drag interruption on Touchpad.
8. If feasible, test boot mode separately and an actual API 28 phone. Otherwise mark those hardware cases unverified. Capture Android logs with the existing diagnostics procedure; report tags/lengths/results are logged, not typed content.

Radio loss or OS termination cannot guarantee that a neutral release reaches the computer. Local state is cleared and new connections start neutral. Composite-profile support depends on device vendor/host Bluetooth implementations; Windows/macOS and other phones are unverified. The V4 accepted precision-scrolling limitation remains.

## Future V5B integration

V5B must extend the current minimal IME proof of concept with deliberate composition, replacement and Unicode/layout handling. The current screen invokes the installed default IME; it does not create an IME service or request new permissions. Preserve the engine's six-key/duplicate rules and shared releaseAll contract. Do not infer Unicode support from US character mapping.

References: [Android BluetoothHidDevice](https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice), [callback API](https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice.Callback).
