# Keyboard session memory and validation

Milestone 2 makes only the existing final-commit length validation earlier. Commits above 512 UTF-16 code units are rejected before constructing the key-mapping list. Accepted mapping, all-or-nothing unsupported-character rejection, rejection feedback and blocked-until-reset behavior remain unchanged. The normal public bridge constructor still uses the US HID mapper; an internal constructor lets tests count mapper calls through the actual commit path.

No editor-history trimming, composition cap, selection restriction, offset translation or InputConnection change is implemented. Arbitrarily long composing drafts may still be shortened to a supported final commit. The 512-unit limit is per final commit, not a session or composing-draft limit.

Known limitation: EditText retains committed history throughout a continuous session. Its text and the bridge's active draft can grow; this change does not establish a global memory bound. Unsupported large commits may also be retained locally by the existing editor delegation even though the bridge emits no HID input. The caller/IME may already hold large input strings; early validation removes only the unnecessary mapped-key list for rejected oversized commits.

Existing cleanup: reset clears editor text, the bridge draft and blocked state, and renews the editor generation. Close invalidates the session, disables remote input, clears text/draft, clears focus and hides the IME. Existing commit/composition/deletion callbacks reject stale generations. Closing/reopening or Release all keys provides cleanup; input cancellation alone is not a full editor-history reset. None of those behaviors is changed here.

Tests use the actual bridge with a counting mapper to verify no mapping occurs for oversized commits, check empty/1/511/512/513 boundaries, atomic rejection of an unsupported final character, long-draft shortening, and HID down/up ordering. Existing tests continue covering composition, deletion, unsupported replacements, reset and session lifecycle. Native Android editor behavior is not newly tested because it is unchanged.

Vivo/Ubuntu hardware checks: ordinary typing and modifiers/special keys; long composition shortened before commit; backspace during and after composition; oversized commit rejection with no remote prefix; Release all keys followed by typing; repeated keyboard close/reopen; themes and Credits remain functional. Long composing text and unsupported Unicode retain existing limitations.

Status: hardware-approved after all seven Vivo checks; final local checkpoint awaits approval. No Milestone 3 work.

Verification: 218 unit tests passed, zero failures/errors/skips; lint passed with one existing AndroidGradlePluginVersion warning; debug build and APK v2 signature verification passed (one signer). APK size: 3,046,919 bytes. Gates ran sequentially offline with one worker and 768 MiB heap. Logs: `/tmp/bluepad-keyboard-validation-testDebugUnitTest.log`, `/tmp/bluepad-keyboard-validation-lintDebug.log`, `/tmp/bluepad-keyboard-validation-assembleDebug.log`.
