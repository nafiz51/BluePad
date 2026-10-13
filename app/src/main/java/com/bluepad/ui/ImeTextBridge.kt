package com.bluepad.ui

import com.bluepad.hid.UsKeyboard

/** Append-oriented US HID proof of concept; composition stays local until committed. */
class ImeTextBridge internal constructor(private val tap: (UsKeyboard.Stroke) -> Unit,
    private val message: (String) -> Unit, private val mapCharacter: (Char) -> UsKeyboard.Stroke?) {
    constructor(tap: (UsKeyboard.Stroke) -> Unit, message: (String) -> Unit) : this(tap,message,UsKeyboard::character)
    var enabled = false
    private var blocked = false
    private var draft: String? = null
    val composing get() = draft != null
    fun compose(text: String) { draft = text }
    fun commit(text: String): Boolean {
        draft = null
        if (!enabled) { message("Not connected; input was not sent."); return false }
        if (blocked) { message("Reset input with Release all keys after unsupported text."); return false }
        // Reject before constructing a list proportional to an unsupported commit's length.
        if (text.length > 512) {
            blocked = true
            message("Only US-mapped text is supported; this commit was not sent."); return false
        }
        val strokes = text.map { mapCharacter(it) }
        if (strokes.any { it == null }) {
            blocked = true
            message("Only US-mapped text is supported; this commit was not sent."); return false
        }
        strokes.forEach { tap(it!!) }
        message("Committed ${strokes.size} key(s) to the input engine.")
        return true
    }
    fun finish(): Boolean { val text = draft ?: return true; return commit(text) }
    fun delete(before: Int, after: Int): Boolean {
        if (composing) return true // Editing local draft must not delete remote committed text.
        if (!enabled || blocked || before !in 0..100 || after != 0) {
            message("Deletion unavailable or unsupported; nothing sent."); return false
        }
        repeat(before) { tap(UsKeyboard.Stroke(0x2a)) }
        message("Backspace events generated: $before")
        return true
    }
    fun enter(): Boolean = commit("\n")
    fun rejectReplacement() { blocked = true; message("IME correction/replacement is unsupported; reset input.") }
    fun reset() { draft = null; blocked = false }
    fun cancel() { draft = null }
}
