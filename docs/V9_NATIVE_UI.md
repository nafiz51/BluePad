# V9 lightweight native interface

## Permanent quiet diagnostics

Verification: 206 unit tests passed, zero failures/errors/skips; lint passed with the existing AndroidGradlePluginVersion warning; debug build and APK v2 signature verification passed (one signer). Normal APK size: 2,814,203 bytes. Gates ran sequentially with one worker and 768 MiB heap. Logs: `/tmp/bluepad-v9-quiet-tests.log`, `/tmp/bluepad-v9-quiet-lint.log`, `/tmp/bluepad-v9-quiet-apk.log`. Final hardware approval is complete: all five Vivo/Ubuntu checks passed, including the permanent quiet-logging fix.

The user confirmed that disabling verbose logging immediately restored smooth cursor movement at 3.5× with the keyboard open and closed. Normal debug and release builds now disable verbose diagnostics at the build level, regardless of saved Android log-tag properties. Lazy inline logging prevents disabled touch/HID/UI messages, timestamp interpolation and report-byte formatting from being evaluated. Normal builds also omit the gesture diagnostic callback and performance-layout logging. Warning/error logs remain enabled. Themes, settings, input algorithms, HID pacing and connection behavior are unchanged.

For troubleshooting only, build a diagnostic debug APK with `./gradlew -PbluepadDiagnostics=true assembleDebug`, install it and explicitly enable the required tags with `adb shell setprop log.tag.BluePadTouch DEBUG`, `adb shell setprop log.tag.BluePadHid DEBUG`, `adb shell setprop log.tag.BluePadUI DEBUG`, `adb shell setprop log.tag.BluePadKeyboard DEBUG` or `adb shell setprop log.tag.BluePadPerf DEBUG`. Earlier capture instructions in this document require this diagnostic build. Verbose tracing can degrade cursor performance. Return to a normal APK using `./gradlew assembleDebug` without the property; release builds never opt in. No ADB commands are needed for quiet everyday behavior.

Final hardware gate: install the normal APK with `adb install -r app/build/outputs/apk/debug/app-debug.apk`, preserving preferences. Check cursor circles/sweeps at 3.5× with keyboard open and closed, repeated keyboard toggling, theme switching while typing, clicking/dragging/scrolling and reconnection. Even previously enabled DEBUG tags must not activate high-frequency logging in this APK.

## Cursor audit history — confirmed logging bottleneck

The prior smooth build also used 3.5× sensitivity. The preserved pre-palette SHA-256 manifest confirms unchanged Bluetooth/HID, motion, gestures, zoom and keyboard lifecycle code. There is no separate committed smooth V9 revision for a complete source comparison. Theme traversal runs on creation or an actual appearance change, not during movement. No demonstrated code defect was found; no speculative source patch was applied.

Read-only diagnostics on the connected Vivo found BluePadTouch and BluePadHid verbose DEBUG tags enabled. The current capture `/tmp/bluepad-v9-cursor-audit-android.log` contains 104 MOVE events with dispatch age 6–23 ms (median 9), queue timings 0–2 ms (median 1), and HID submission times 1–12 ms (median 3). Acceptance does not prove host receipt. Graphics statistics show 23/87 janky rendered frames, but include navigation and do not isolate cursor movement. Per-event logging and formatted report bytes are a plausible overhead source, not a proven root cause.

Compare identical movement at 3.5× with verbose tracing disabled:

```sh
adb shell setprop log.tag.BluePadTouch INFO
adb shell setprop log.tag.BluePadHid INFO
adb shell setprop log.tag.BluePadUI INFO
adb shell setprop log.tag.BluePadPerf INFO
```

Test slow circles and quick horizontal/vertical sweeps for 20 seconds with the keyboard closed, then open. If lag remains, record a short controlled capture with the existing instrumentation:

```sh
adb shell setprop log.tag.BluePadTouch DEBUG
adb shell setprop log.tag.BluePadHid DEBUG
adb shell setprop log.tag.BluePadPerf DEBUG
adb logcat -v threadtime -s BluePadTouch:D BluePadHid:D BluePadPerf:D '*:S' > /tmp/bluepad-v9-cursor-hardware.log
```

Perf logging requires closing/reopening BluePad after enabling its tag. Stop capture with Ctrl+C after 10 seconds of movement, record `adb shell dumpsys gfxinfo com.bluepad > /tmp/bluepad-v9-cursor-gfx.txt`, then restore INFO tags. Compare diagnostic-on against diagnostic-off responsiveness; tracing itself adds work. No keyboard text is logged by these tags.

Verification gates passed: 202 tests, lint with one existing plugin-version warning, debug build and APK v2 signature verification. APK: 2,770,729 bytes. Only this documentation changed during the audit; no phone preferences were changed, and no commit/tag/push occurred.

Status: hardware-verified V9 stable checkpoint. The initial V9 passed Vivo/Ubuntu functionality and performance testing as confirmed by the user. The final refinements exposed a reproducible keyboard close/reopen failure and cursor lag, including with the keyboard closed. The user confirmed all four repair hardware checks passed: 20 keyboard open/close typing cycles, cursor smoothness comparable to V8, tapping/scrolling/dragging, and Settings/Bluetooth Back navigation. The final visual palette and quiet-logging fix passed final hardware approval. Stable V8 remains commit `9e46a4c01a5c2e55aea032a9bcce25157000f62b`, annotated tag `bluetooth-touchpad-keyboard-working-v8`. The user authorized the local V9 commit and annotated tag; no push is authorized.

## Audit and design

V8 used an unlabeled status-button toolbar, a top keyboard toggle, an unsectioned settings panel, a text-labeled gray touch surface and the platform light theme. Its verified debug APK measured 2,730,049 bytes (2.60 MiB). The native Activity already used real IME/system-bar insets on API 30+ and adjustResize plus visible-window observation on API 28–29; these inset mechanisms remain; editor binding renewal is described below.

V9 uses the same native Views. BluePad identity, a small connection indicator, stacked host name/status text, consistent borderless Bluetooth and Settings icons occupy the toolbar. Status opens the existing connection panel. The Bluetooth icon always opens the connection controls, including when connected; explicit Disconnect remains inside that panel. Callback state remains authoritative. Existing manual Connect/Disconnect/Refresh/Reconnect controls remain in that panel.

The touchpad has no decorative text, a subtle rounded boundary and the existing 12% scroll strip. Dedicated left/right buttons remain separate siblings with held-button behavior. The keyboard toggle is beside them at the bottom, above the existing remote input/modifier interface and Android IME when open. It remains reachable in both modes and retains existing focus/Back/dismissal handling. No hidden keyboard interface consumes space when closed.

Controls retain 48 dp touch targets. Default toolbar/button rows each use 48 dp; touchpad margins add 8 dp vertically versus V8, while wrapped toolbar and mouse-button rows can grow with font scaling. The remaining area is allocated to the pad by layout weight. Panels are scrollable and bounded by available window/inset height, the measured keyboard-control height and reserved controls/touchpad space. A global-layout callback changes panel height only when the calculated bounds change, accommodating rotation, IME-height changes and font sizing. No keyboard height or 50/50 keyboard split is assumed.

Settings sections are Connection, Touchpad, Scrolling, advanced Zoom Compatibility and Appearance. Existing preferences and migrations are unchanged. Dark (fresh-install default), System and Light choices persist separately in `appearance.theme`. System follows Android's night-mode configuration. Native framework themes, sans typography, vector icons, solid colors and state drawables provide consistent enabled/disabled/pressed/checked/selected feedback. There are no custom fonts, bitmaps, animation libraries or new dependencies.

Appearance changes recolor existing views in place without Activity recreation or input-session changes. The compact toolbar sun/moon control saves an explicit Light/Dark choice opposite to the currently displayed theme, including when System is selected. Saved appearance preferences remain intact; System follows Android night-mode changes in place. An app-specific theme cannot change the installed IME's theme.

## Preservation and performance

22 Bluetooth/HID/input source files remain byte-for-byte unchanged from V8, including HidManager, report descriptors/encoders, reconnection policies, input-session fencing, TouchpadView, TouchpadGestures, TouchpadMotion, PinchGesture, ZoomPacer, KeyboardLayoutState, MouseClickButton and MouseButtonContact. KeyboardView changes include presentation and explicit IME lifecycle renewal. Text translation, HID dispatch and modifier/special-key behavior remain unchanged; stale InputConnection wrappers are fenced by editor generations. UI styling affects appearance/wiring only. No cursor threshold, fractional accumulation, report frequency/pacing, click timer, scroll/zoom algorithm or keyboard report generation was changed.

Styling runs during construction/settings rebuild. State drawables use native static pressed/disabled feedback; button state animators and elevations are disabled. There are no new MotionEvent callbacks, per-touch allocations, timers, services or background tasks. Status rendering uses existing HID/UI callbacks, not cursor movement. Dependency/build configuration files are unchanged. These source-level checks do not measure Android frame latency or establish hardware smoothness; real-device profiling and testing remain necessary.

The measured sizes are debug APKs, not optimized release APKs. No shrinking/signing/release configuration changes were made solely to meet the size goal.

## Exact files changed

- `README.md`
- `docs/V9_NATIVE_UI.md` (new)
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/bluepad/MainActivity.kt`
- `app/src/main/java/com/bluepad/ui/ScrollSettingsView.kt` (presentation only)
- `app/src/main/java/com/bluepad/ui/Appearance.kt` (new; preference and layout policy)
- `app/src/main/java/com/bluepad/ui/UiStyle.kt` (new)
- `app/src/main/java/com/bluepad/ui/UiNavigation.kt` (new)
- `app/src/main/java/com/bluepad/ui/KeyboardImeSession.kt` (new)
- `app/src/main/java/com/bluepad/ui/KeyboardTogglePresentation.kt` (new)
- `app/src/main/java/com/bluepad/ui/KeyboardView.kt` (presentation/status only)
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values/styles.xml` (new)
- `app/src/main/res/drawable/ic_connection.xml` (new)
- `app/src/main/res/drawable/ic_settings.xml` (new)
- `app/src/main/res/drawable/ic_close.xml` (new)
- `app/src/test/java/com/bluepad/ui/MainUiPolicyTest.kt` (new)
- `app/src/test/java/com/bluepad/ui/UiNavigationTest.kt` (new)
- `app/src/test/java/com/bluepad/ui/KeyboardImeSessionTest.kt` (new)
- `app/src/test/java/com/bluepad/ui/KeyboardTogglePresentationTest.kt` (new)

## Final refinement: navigation and typing

The oversized status button is replaced by a small indicator and separate host/status labels. Both toolbar icons use consistent 24 dp vectors inside 48 dp targets with borderless idle backgrounds and native pressed feedback. Status text and the Bluetooth icon open the same controls; neither disconnects immediately.

Back uses the platform OnBackInvokedDispatcher on API 33+ and Activity key handling on API 28–32. It dismisses visible/pending IME input first, then closes the active Settings or connection panel, then permits normal Activity exit. Panels have one owner and are restored through saved instance state. Back callbacks are registered only while foregrounded with a Back target, and unregistered on stop. No new nested settings screens were introduced; native Spinner pop-ups handle their own dismissal before the parent panel.

The typing field has a neutral bordered surface, 16 sp text, padding and a shorter hint. Feedback has two readable lines and polite accessibility announcements; reconnection/opening updates stale disconnected feedback to Ready without overwriting ongoing input warnings. Modifier/special keys retain their handlers with 6 dp spacing and height that grows for fonts. The horizontal strip remains swipeable. A vertically scrollable viewport is bounded by real available window height while reserving toolbar, mouse controls and touchpad space. The bottom keyboard toggle changes to a close icon immediately and stays outside the viewport. No hidden keyboard panel consumes space.

Test Back from both panels, Back with IME plus a panel open (keyboard closes first, panel on the next Back), Back from Spinner pop-ups, rotation/backgrounding with panels open, and normal exit from the main screen. Test typing/feedback/modifiers, horizontal and vertical key-panel scrolling on short windows, IME height changes, and the bottom close control. Very short windows still require scrolling or hiding the IME to access all controls.

## Vivo / Ubuntu checklist

Install without clearing data or pairing:

```sh
/tmp/bluepad-sdk/platform-tools/adb install -r /home/nafiz51/dev/BluePad/app/build/outputs/apk/debug/app-debug.apk
```

1. Check portrait and landscape, system bars/cutouts, small windows and enlarged Android fonts. The toolbar must show actual status and host name (long names ellipsize); all icon controls must remain accessible. Open/close connection and Settings panels and confirm scrollable contents and remaining touchpad space.
2. Connect through status/Bluetooth controls, open controls with the Bluetooth icon and use Disconnect there, reconnect manually and test V7 auto recovery including laptop Bluetooth OFF/ON. Explicit disconnect must remain respected. No state should claim Connected before the callback.
3. Compare cursor smoothness with V8 at the same sensitivity: tiny/diagonal movements, fast circles and continuous swipes. Recheck single tap, moderate double-click, double-tap hold/drag, dedicated left/right holds, browser-tab drag, both scroll paths and 12% boundary locking. Toolbar and button contacts must never move the cursor.
4. Use the bottom keyboard icon with IME hidden/visible, including Ridmik if installed. Type supported text, delete, Enter, modifiers and shortcuts; close with the icon and Android Back. Test IME height changes and opening panels while typing; the touchpad/buttons/toggle must remain usable when space permits.
5. Confirm every old setting survives update/restart: auto reconnect, pointer sensitivity, scroll direction/speed and Zoom Compatibility. Verify Light, Dark and System selection, Android system theme changes and appearance persistence. Theme switching must preserve the connection, editor focus and active input.
6. Check contrast, focus announcements, disabled controls and pressed/checked feedback with accessibility services. Very short windows or exceptionally large fonts can require hiding the IME or panels to regain usable touchpad space. Review the final appearance on the Vivo before approving V9.

Known limits: automated tests cover state/policy and core regressions, not actual Android widget rendering or HID delivery. No on-device screenshots, frame-time profile or Windows/macOS hardware verification are claimed. Existing very-fast-double-tap, US HID/IME text and application-defined zoom limitations remain. Earlier verification notes below are historical; this closeout creates the approved local checkpoint.

## Reopen failure and cursor lag repair

Hardware reproduction: first open/types correctly, close with the BluePad toggle, reopen, then no remote typing. KeyboardView is reused; there is no TextWatcher to attach/remove. In V8 and initial V9, closeIme invalidated inputVersion and cleared focus, while openIme only requested focus and posted showSoftInput. That depended on Android implicitly replacing the served InputConnection. A cached wrapper remains invalid after close and its commit calls return false before ImeTextBridge or HID dispatch. The same vulnerability existed before the refinement; viewport/focus changes plausibly exposed it. Device logs are still needed to establish the exact IME timing rather than claiming it was a Bluetooth failure.

KeyboardImeSession now owns open/closed generations and pending activation. Opening waits for an attached, laid-out, window-focused editor, acquires field focus, explicitly calls restartInput and then requests IME display. Only actual layout/window readiness events can resume pending activation; there are no arbitrary delays or polling. Duplicate/stale posted activations cannot reopen a closed/newer editor. Close, input reset and recreation invalidate old wrappers. Returning from a panel renews the editor when keyboard mode remains open; panel focus is not stolen. Reconnection invalidates the editor and rebinds after the existing UI/input-session acknowledgement. Modifier/special-key handlers and Bluetooth input-session logic are untouched.

Cursor audit: Bluetooth/HID, TouchpadView, motion/gesture/zoom algorithms, thresholds, sensitivity and frame/report pacing are byte-identical to V8. Normal move/scroll dispatch does not publish UI state. The initial V9 is not a separate Git checkpoint; its implementation is recorded in this session and its build logs. The refinement introduced unconditional setImageResource calls in render and IME/inset observation even when the toggle was unchanged. AOSP ImageView explicitly reloads the resource and invalidates on every such call, even for the same resource ID: [Android ImageView source](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/widget/ImageView.java). This is verified unnecessary allocation/draw work on the UI thread, including closed-mode layout notifications; it is not proof that it explains every millisecond of hardware lag.

KeyboardTogglePresentation now applies icon/selection/description only at a real open/close transition. A regression test subjects it to 1,000 closed-state notifications and verifies only the initial drawable update. The panel-sizing global listener immediately returns when keyboard and panels are closed. No cursor/HID tuning or new theme is applied. If lag remains, measure layout/render and touch-to-submission timing on-device before further edits.

## Focused real-device checks and diagnostics

Install with adb install -r, retaining data/bonds. Reproduce the exact first-open/type/close/reopen/type sequence at least 20 times without restarting or reconnecting. Test rapid cycles, Back, Settings/Bluetooth panel return, theme switching and typing after Bluetooth recovery. Confirm Ctrl/Alt/Shift/Super and special keys still work.

Test cursor motion first with keyboard fully closed, then open, at the same pointer sensitivity as V8. Record whether lag differs with all diagnostics disabled. Both repairs passed the four reported hardware checks; automated tests do not certify the actual Android editor, host receipt or perceived latency.

For a short typing capture (no typed contents are added to the diagnostics):

```sh
/tmp/bluepad-sdk/platform-tools/adb shell setprop log.tag.BluePadKeyboard DEBUG
/tmp/bluepad-sdk/platform-tools/adb shell setprop log.tag.BluePadUi DEBUG
/tmp/bluepad-sdk/platform-tools/adb shell setprop log.tag.BluePadHid DEBUG
/tmp/bluepad-sdk/platform-tools/adb logcat -v threadtime BluePadKeyboard:D BluePadUi:D BluePadHid:D '*:S' > /tmp/bluepad-v9-keyboard-retest.log
```

Start capture before the first open and stop with Ctrl+C after the failure/retest. A fresh connection-created version must follow restart; commit remote=false indicates local/disabled capture, stale/inactive rejection indicates editor lifecycle failure, route/engine/send messages locate later dispatch. sendReport acceptance does not establish Ubuntu delivery.

For cursor/render diagnosis, disable high-volume traces before measuring. Enable only the opt-in aggregate layout counter, close/reopen the app normally to apply it, then move continuously for 10–15 seconds with the keyboard closed:

```sh
/tmp/bluepad-sdk/platform-tools/adb shell setprop log.tag.BluePadTouch INFO
/tmp/bluepad-sdk/platform-tools/adb shell setprop log.tag.BluePadHid INFO
/tmp/bluepad-sdk/platform-tools/adb shell setprop log.tag.BluePadUi INFO
/tmp/bluepad-sdk/platform-tools/adb shell setprop log.tag.BluePadKeyboard INFO
/tmp/bluepad-sdk/platform-tools/adb shell setprop log.tag.BluePadPerf DEBUG
/tmp/bluepad-sdk/platform-tools/adb shell dumpsys gfxinfo com.bluepad reset
/tmp/bluepad-sdk/platform-tools/adb logcat -v threadtime BluePadPerf:D '*:S' > /tmp/bluepad-v9-layout.log
```

Stop recording with Ctrl+C, then capture native render statistics:

```sh
/tmp/bluepad-sdk/platform-tools/adb shell dumpsys gfxinfo com.bluepad framestats > /tmp/bluepad-v9-frames.txt
/tmp/bluepad-sdk/platform-tools/adb shell setprop log.tag.BluePadPerf INFO
```

Restore the other debug tags to INFO after the typing experiment. Layout counters run only when explicitly opted into a debug build and log at most once per second of layout activity. gfxinfo measures rendered frames; it does not directly measure every nondrawing cursor/report callback or host Bluetooth delay. If needed, a separate short BluePadTouch/BluePadHid trace contains existing event/frame/queue/submission timestamps, at the cost of diagnostic overhead. No diagnostic background service or cursor callback is added.

## Repair verification

192 unit tests passed with zero failures/errors/skips: all 180 pre-repair tests plus ten IME lifecycle/reopen tests and two transition-only toggle tests. The reused-editor reproduction covers 50 close/reopen/type cycles, with fresh connection binding and distinct key press/release reports. Other tests cover focus readiness/failure, stale posted requests, recreation, panel return, reconnection/input-session fencing, show rejection without retry loops, and 1,000 unchanged closed-state notifications without icon reloads. These are deterministic unit models, not instrumented Android IME tests.

Lint passed with zero errors and only the existing AndroidGradlePluginVersion warning. Debug build and APK v2 signature verification passed (one signer). Gates ran sequentially offline, one worker, 768 MiB Gradle heap, in-process Kotlin. Logs: `/tmp/bluepad-v9-repair-tests.log`, `/tmp/bluepad-v9-repair-lint.log`, `/tmp/bluepad-v9-repair-apk.log`.

APK: `/home/nafiz51/dev/BluePad/app/build/outputs/apk/debug/app-debug.apk` (2,763,906 bytes, 2.64 MiB). No dependencies or new theme. Exact repair files: MainActivity.kt, ui/KeyboardView.kt, new ui/KeyboardImeSession.kt, new ui/KeyboardTogglePresentation.kt, their two new test classes, README.md and this document. No commit/tag/push; V8 and earlier checkpoints remain intact. The user subsequently confirmed the four repair hardware checks passed; the original full latency attribution remains unmeasured. The final visual palette has subsequently passed user approval.

## Final visual identity: Midnight Blue / Soft Blue

The behavior verified in the four repair hardware tests is preserved. This change adds only solid colors, native state colors and paint configuration; there are no new dependencies, animations, fonts, bitmaps or themes beyond the existing System/Light/Dark choices.

| Role | Midnight Blue | Soft Blue |
| --- | --- | --- |
| Background | #101B2B | #F2F7FF |
| Surface | #1B2A40 | #EDF4FF |
| Touchpad | #152338 | #F8FBFF |
| Primary | #2563EB | #2563EB |
| Accent | #60A5FA | #2563EB |
| Text | #EAF2FF | #142C4B |
| Secondary text | #98ACC7 | #4C6585 |

The toolbar/status indicator, native buttons and icons, typing field, keyboard key row, settings/connection panels, window/system bars and native floating menus use matching blue tones. Checked/selected controls use royal-blue fill and white text/icons; pressed controls use a deeper royal blue. Borders and the unchanged 12% strip remain subtle. The touchpad paint setter runs during creation only; its onDraw, event pipeline and gesture geometry are unchanged. Existing 12 dp radii, spacing and minimum 48 dp targets are preserved.

Palette tests verify at least 4.5:1 contrast for primary/secondary text and blue control labels on all three backgrounds, plus white on selected/pressed fills. These checks cover solid colors; actual native widget rendering, system overrides and the installed IME's own theme still need visual review. Android IME appearance is controlled by the IME, not BluePad.

Exact visual-change files: `ui/BluePalette.kt` (new), `ui/UiStyle.kt`, `MainActivity.kt` (touchpad colors only), `ui/TouchpadView.kt` (paint setter only), `res/values/styles.xml`, `res/drawable/ic_keyboard.xml`, `ic_settings.xml`, `ic_connection.xml`, `ic_close.xml`, `ic_mouse.xml`, `test/.../ui/BluePaletteTest.kt` (new), README.md and this document. Bluetooth/HID, IME lifecycle, keyboard/navigation behavior and motion algorithms are unchanged.

Review Light/Dark/System, selected modifiers, pressed/disabled buttons, host indicator, all panels/pop-ups and the strip in both modes. Repeat keyboard cycles and basic cursor/gesture checks after installation to confirm the visual build retains the accepted performance. Final hardware and visual approval has now been completed.

### Visual build verification

194 unit tests passed (all 192 behavior tests and two palette/contrast tests), zero failures/errors/skips. Lint passed with zero errors and only the existing AndroidGradlePluginVersion warning. Debug build and APK v2 signature verification passed (one signer). All gates ran sequentially offline with one worker, a 768 MiB Gradle heap and in-process Kotlin compilation. Logs: `/tmp/bluepad-v9-blue-tests.log`, `/tmp/bluepad-v9-blue-lint.log`, `/tmp/bluepad-v9-blue-apk.log`.

Exact debug APK size: 2,766,062 bytes (2.64 MiB). Previous hardware-verified repair APK: 2,763,906 bytes. Original V8 debug APK: 2,730,049 bytes. This is not an optimized release APK measurement. Dependencies added: zero. Protected Bluetooth/input/navigation/lifecycle files were hash-verified unchanged; removing only the paint setter makes TouchpadView identical to V8.

APK: `/home/nafiz51/dev/BluePad/app/build/outputs/apk/debug/app-debug.apk`. This visual build subsequently passed hardware/visual approval.

## V9 defaults and quick appearance toggle

Fresh installations default to Midnight Blue Dark, cursor sensitivity 3.5×, scroll sensitivity 2.5× and Natural Scrolling ON. Missing preference keys use these defaults; any saved valid value, including previous defaults and natural scrolling OFF, is preserved. Existing ranges remain cursor 0.5×–8× and scroll 0.5×–3×, with 0.1× steps. Slider positions reflect the active values. Only changed sensitivity/direction preferences are saved; theme changes write only appearance preferences. Restore Scrolling restores the new defaults.

Older versions sometimes automatically saved default sensitivity values. Those cannot be distinguished from deliberate user choices and are retained. Midnight Blue palette values and input algorithms are unchanged. No Bluetooth, HID, gesture, cursor pacing or keyboard lifecycle changes accompany these settings.

Hardware approval: check fresh defaults on a separate clean installation, preserved saved values after update/restart, natural ON/OFF toggle, and repeated toolbar theme switching with the keyboard open and Bluetooth connected. Preserve the existing installation data during normal upgrade testing.

### Defaults/toggle verification

202 unit tests passed, zero failures/errors/skips. Final lint passed with zero errors and the existing AndroidGradlePluginVersion warning. Debug APK build and v2 signature verification passed (one signer). Gates ran sequentially offline with one worker and a 768 MiB heap. APK: `app/build/outputs/apk/debug/app-debug.apk`, 2,770,729 bytes. Logs: `/tmp/bluepad-v9-defaults-tests.log`, `/tmp/bluepad-v9-defaults-lint.log`, `/tmp/bluepad-v9-defaults-apk.log`. Preference tests cover pure restoration/encoding and slider values; Android IME/theme continuity still requires physical verification. No commit, tag or push was performed.

## Final V9 hardware approval and checkpoint

The user reports all five final checks passed on Vivo Android and Ubuntu: smooth cursor at 3.5× with keyboard closed; smooth cursor with keyboard open; reliable typing after repeated open/close cycles; tapping, dragging, scrolling and zoom; Light/Dark switching and saved settings. The permanent quiet-logging fix is hardware-verified. V9 is approved for a local commit and annotated tag `bluetooth-touchpad-keyboard-working-v9`. V8 and all earlier checkpoints are retained; no push is authorized.

Final closeout verification: 206 tests passed on a fresh rerun, zero failures/errors/skips; lint passed with the existing plugin-version warning; debug build and APK v2 signature verification passed (one signer). APK size: 2,814,203 bytes. Verification ran sequentially offline with one worker and a 768 MiB heap. Closeout logs are in `/tmp/bluepad-v9-closeout-*.log` and are excluded from Git.
