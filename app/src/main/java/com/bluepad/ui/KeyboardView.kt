package com.bluepad.ui

import android.content.Context
import android.text.InputType
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.*
import android.widget.*
import com.bluepad.R
import com.bluepad.hid.KeyboardReports
import com.bluepad.hid.UsKeyboard

/** Native default-IME input, with only modifier/special-key controls beneath the field. */
class KeyboardView @JvmOverloads constructor(context: Context,
    private val tap: (UsKeyboard.Stroke) -> Unit = {},
    modifier: (Int, Boolean) -> Unit = { _, _ -> },
    private val clear: () -> Unit = {}
) : ScrollView(context) {
    private companion object { const val TAG = "BluePadKeyboard" }
    private val controls = mutableListOf<android.view.View>()
    private val modifiers = linkedMapOf<Int,ToggleButton>()
    private var rendering = false
    private val feedback = TextView(context)
    private val bridge = ImeTextBridge({ stroke ->
        if (Log.isLoggable(TAG,Log.DEBUG)) Log.d(TAG,"IME generated a key tap")
        tap(stroke)
    }) { note ->
        feedback.text = note
        if (Log.isLoggable(TAG,Log.DEBUG)) Log.d(TAG,"IME result: $note")
    }
    private val field = object : EditText(context) {
        override fun onCreateInputConnection(info: EditorInfo): InputConnection? {
            val base = super.onCreateInputConnection(info) ?: return null
            return object : InputConnectionWrapper(base,false) {
                override fun setComposingText(text: CharSequence?, position: Int): Boolean {
                    if (Log.isLoggable(TAG,Log.DEBUG)) Log.d(TAG,"IME composing length=${text?.length ?: 0}; local only")
                    bridge.compose(text?.toString() ?: "")
                    return super.setComposingText(text,position)
                }
                override fun setComposingRegion(start: Int, end: Int): Boolean {
                    if (start != end && !bridge.composing) { bridge.rejectReplacement(); return false }
                    return super.setComposingRegion(start,end)
                }
                override fun commitText(text: CharSequence?, position: Int): Boolean {
                    if (Log.isLoggable(TAG,Log.DEBUG)) Log.d(TAG,"IME commit received length=${text?.length ?: 0}")
                    bridge.commit(text?.toString() ?: "")
                    return super.commitText(text,position)
                }
                override fun finishComposingText(): Boolean {
                    bridge.finish(); return super.finishComposingText()
                }
                override fun deleteSurroundingText(before: Int, after: Int): Boolean {
                    val composing = bridge.composing
                    if (!composing) bridge.delete(before,after)
                    val result = super.deleteSurroundingText(before,after)
                    if (composing) {
                        val start = BaseInputConnection.getComposingSpanStart(editableText)
                        val end = BaseInputConnection.getComposingSpanEnd(editableText)
                        bridge.compose(if (start >= 0 && end >= start) editableText.subSequence(start,end).toString() else "")
                    }
                    return result
                }
                override fun deleteSurroundingTextInCodePoints(before: Int, after: Int): Boolean {
                    val composing = bridge.composing
                    if (!composing) bridge.delete(before,after)
                    val result = super.deleteSurroundingTextInCodePoints(before,after)
                    if (composing) syncDraft()
                    return result
                }
                override fun performEditorAction(action: Int): Boolean {
                    bridge.finish(); bridge.enter(); return true
                }
                override fun sendKeyEvent(event: KeyEvent): Boolean {
                    if (event.action == KeyEvent.ACTION_DOWN && remoteEvent(event)) return true
                    if (event.action == KeyEvent.ACTION_UP && (event.keyCode in handledKeys || event.unicodeChar > 0)) return true
                    return super.sendKeyEvent(event)
                }
            }
        }
        override fun onKeyDown(code: Int, event: KeyEvent): Boolean {
            if (code == KeyEvent.KEYCODE_DEL && bridge.composing) {
                val result = super.onKeyDown(code,event); syncDraft(); return result
            }
            if (remoteEvent(event)) return true
            return super.onKeyDown(code,event)
        }
        override fun onKeyUp(code: Int, event: KeyEvent): Boolean = if (code in handledKeys || event.unicodeChar > 0) true else super.onKeyUp(code,event)
        override fun onTextContextMenuItem(id: Int): Boolean {
            feedback.setText(R.string.ime_clipboard_limit)
            return false // Local clipboard/selection edits are not synchronized with the computer.
        }
    }
    private val handledKeys = setOf(KeyEvent.KEYCODE_DEL,KeyEvent.KEYCODE_ENTER,KeyEvent.KEYCODE_TAB,KeyEvent.KEYCODE_ESCAPE)
    private fun syncDraft() {
        val text = field.editableText
        val start = BaseInputConnection.getComposingSpanStart(text)
        val end = BaseInputConnection.getComposingSpanEnd(text)
        bridge.compose(if (start >= 0 && end >= start) text.subSequence(start,end).toString() else "")
    }
    private fun remoteEvent(event: KeyEvent): Boolean {
        if (remoteKey(event.keyCode)) return true
        val unicode = event.unicodeChar
        if (unicode > 0 && Character.isValidCodePoint(unicode)) {
            bridge.commit(String(Character.toChars(unicode))); return true
        }
        return false
    }
    private fun remoteKey(code: Int): Boolean = when (code) {
        KeyEvent.KEYCODE_DEL -> if (bridge.composing) false else { bridge.delete(1,0); true }
        KeyEvent.KEYCODE_ENTER -> { bridge.finish(); bridge.enter(); true }
        KeyEvent.KEYCODE_TAB -> { bridge.commit("\t"); true }
        KeyEvent.KEYCODE_ESCAPE -> { if (bridge.enabled) tap(UsKeyboard.Stroke(0x29)); true }
        else -> false
    }
    init {
        val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        addView(content)
        content.addView(TextView(context).apply { setText(R.string.keyboard_help) })
        field.apply {
            hint = context.getString(R.string.ime_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_ACTION_NONE
            isSaveEnabled = false; isLongClickable = false; minLines = 2
        }
        content.addView(field); content.addView(feedback)
        val modifierRow = LinearLayout(context); content.addView(modifierRow)
        for ((label,bit) in listOf("Ctrl" to KeyboardReports.CTRL,"Alt" to KeyboardReports.ALT,
            "Shift" to KeyboardReports.SHIFT,"Super" to KeyboardReports.SUPER)) {
            val toggle = ToggleButton(context).apply {
                textOn = "$label ●"; textOff = label; isChecked = false
                minWidth = 0; minimumWidth = 0; setPadding(0,0,0,0)
                setOnCheckedChangeListener { _,checked -> if (!rendering) modifier(bit,checked) }
            }
            modifiers[bit] = toggle; controls.add(toggle)
            modifierRow.addView(toggle,LinearLayout.LayoutParams(0,-2,1f))
        }
        for (group in UsKeyboard.special.entries.chunked(5)) {
            val row = LinearLayout(context); content.addView(row)
            for ((label,usage) in group) {
                val button = Button(context).apply {
                    text = label; isAllCaps = false; minWidth = 0; minimumWidth = 0; setPadding(0,0,0,0)
                    textSize = 13f; setOnClickListener { bridge.finish(); tap(UsKeyboard.Stroke(usage)) }
                }
                controls.add(button); row.addView(button,LinearLayout.LayoutParams(0,-2,1f))
            }
        }
        val release = Button(context).apply { setText(R.string.release_keys); setOnClickListener { bridge.reset(); field.text.clear(); context.getSystemService(InputMethodManager::class.java).restartInput(field); clear() } }
        controls.add(release); content.addView(release)
    }
    fun openIme() {
        field.requestFocus()
        field.post {
            if (visibility == android.view.View.VISIBLE && field.hasFocus()) {
                val shown = context.getSystemService(InputMethodManager::class.java).showSoftInput(field,InputMethodManager.SHOW_IMPLICIT)
                Log.i(TAG,"Default IME show request accepted=$shown")
            }
        }
    }
    fun closeIme() {
        bridge.enabled = false; bridge.reset()
        field.text.clear(); field.clearFocus()
        context.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(field.windowToken,0)
    }
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_CANCEL) { bridge.cancel(); clear() }
        return super.dispatchTouchEvent(event)
    }
    fun render(enabled: Boolean, mask: Int) {
        bridge.enabled = enabled
        if (!enabled) { bridge.cancel(); feedback.setText(R.string.ime_disconnected) }
        rendering = true
        controls.forEach { it.isEnabled = enabled }
        modifiers.forEach { (bit,toggle) -> toggle.isChecked = mask and bit != 0 }
        rendering = false
    }
}
