# Milestone 3: targeted usability and accessibility

Status: hardware-approved after all seven Vivo checks; final local checkpoint awaits approval. No redesign or Milestone 4 work.

## Confirmed issues and changes

Keyboard modifier/special-key controls previously had exact 64 dp widths; Release all keys had an exact 132 dp width. Those widths could not expand with scaled text. Controls now wrap their measured content, retaining 64/132 dp minimum widths, modest horizontal padding, existing minimum height and the existing horizontally scrollable row. Handlers and focus flags are unchanged.

Bksp, Esc and arrow-only controls now have explicit spoken names (Backspace, Escape, Left/Up/Right/Down arrow). Other controls retain their complete visible labels. Zoom Compatibility now has an explicit selector description. Two unit tests verify abbreviated/arrow name routing and preservation of complete visible labels.

## Audit boundaries

The palettes and style states are retained; existing palette/contrast tests remain. Credits keeps the exact message, native OK/Back dismissal and existing in-place recoloring. Navigation and InputConnection lifecycle are unchanged. Native accessible focus is distinct from ordinary editor focus; no ordinary focus flag is changed to avoid disturbing IME behavior.

Screen-height exhaustion with a tall IME in landscape/large display scaling, toolbar crowding at extreme widths/font sizes, TalkBack traversal/activation, and touchpad usability under touch exploration still require actual Android verification. Rotation uses the existing Activity recreation and Bluetooth cleanup/recovery; seamless Bluetooth retention through rotation is not claimed. No outer scrolling container, focus change, orientation/lifecycle override or touchpad accessibility action was added, because each could affect protected input behavior. Existing dedicated mouse buttons have accessibility activation; touchpad gestures have no newly added alternative actions.

The unit suite checks policies and regressions, not native measurements, TalkBack speech, or IME heights. No Android instrumentation runner or large testing framework has been introduced for this small change.

## Vivo hardware checklist

1. At normal font/display size, compare both themes: touchpad, toolbar, keyboard row, settings and Credits; confirm no visible redesign.
2. Increase font and display scaling. Open the keyboard and scroll horizontally to every modifier, special key and Release all keys; check complete captions, reachability and working shortcuts.
3. Test portrait and landscape with short and tall IME layouts. Check toolbar, both mouse buttons, keyboard toggle and scrollable typing controls; record any clipped/unreachable controls rather than changing input ownership speculatively.
4. With TalkBack, check Backspace/Escape/arrows, Zoom Compatibility, modifier checked states, mouse-button activation and logical traversal. Check Credits announcement and OK/Back dismissal.
5. Repeat keyboard open/type/close/reopen, Settings return and IME-first Back. Confirm no unexpected focus loss or keyboard dismissal when operating controls.
6. Verify cursor smoothness, scrolling, pinch zoom and both drag methods. Check normal theme changes preserve typing and connection; rotation may invoke the existing cleanup/reconnect path.
7. Restore preferred font/display settings and confirm sensitivities, scrolling direction, themes and Credits remain unchanged.

Verification: 220 unit tests passed, zero failures/errors/skips; lint passed with the existing AndroidGradlePluginVersion warning; debug build and APK v2 signature verification passed (one signer). APK size: 2,983,823 bytes. Gates ran sequentially offline with one worker and 768 MiB heap. Logs: `/tmp/bluepad-usability-testDebugUnitTest.log`, `/tmp/bluepad-usability-lintDebug.log`, `/tmp/bluepad-usability-assembleDebug.log`. Native layout, IME and TalkBack checks remain for hardware approval.
