package com.bluepad.ui

import android.content.Context
import android.text.InputType
import com.bluepad.diagnostics.Diagnostics
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
    private var remoteEnabled = false
    val contentHeight get() = (getChildAt(0)?.measuredHeight ?: 0) + paddingTop + paddingBottom
    private val imeSession = KeyboardImeSession()
    private var focusAllowed = true
    private inline fun imeTrace(message: () -> String) = Diagnostics.debug(TAG,message)
    private fun acceptsInput(version: Int): Boolean {
        val accepted = imeSession.accepts(version)
        if (!accepted) imeTrace { "IME rejected stale/inactive connection captured=$version current=${imeSession.version} open=${imeSession.open}" }
        return accepted
    }
    private val feedback = TextView(context)
    private val bridge = ImeTextBridge({ stroke ->
        if (Diagnostics.isEnabled(TAG)) Log.d(TAG,"IME generated a key tap")
        tap(stroke)
    }) { note ->
        feedback.text = note
        if (Diagnostics.isEnabled(TAG)) Log.d(TAG,"IME result: $note")
    }
    private val field = object : EditText(context) {
        override fun onCreateInputConnection(info: EditorInfo): InputConnection? {
            val base = super.onCreateInputConnection(info) ?: return null
            val version = imeSession.version
            imeTrace { "IME connection created version=$version focused=${hasFocus()} open=${imeSession.open} remote=${bridge.enabled}" }
            return object : InputConnectionWrapper(base,false) {
                override fun setComposingText(text: CharSequence?, position: Int): Boolean {
                    if (!acceptsInput(version)) return false
                    if (Diagnostics.isEnabled(TAG)) Log.d(TAG,"IME composing length=${text?.length ?: 0}; local only")
                    bridge.compose(text?.toString() ?: "")
                    return super.setComposingText(text,position)
                }
                override fun setComposingRegion(start: Int, end: Int): Boolean {
                    if (!acceptsInput(version)) return false
                    if (start != end && !bridge.composing) { bridge.rejectReplacement(); return false }
                    return super.setComposingRegion(start,end)
                }
                override fun commitText(text: CharSequence?, position: Int): Boolean {
                    if (!acceptsInput(version)) return false
                    if (Diagnostics.isEnabled(TAG)) Log.d(TAG,"IME commit received length=${text?.length ?: 0}")
                    imeTrace { "IME commit received remote=${bridge.enabled} focused=${hasFocus()} version=$version" }
                    bridge.commit(text?.toString() ?: "")
                    return super.commitText(text,position)
                }
                override fun finishComposingText(): Boolean {
                    if (!acceptsInput(version)) return false
                    bridge.finish(); return super.finishComposingText()
                }
                override fun deleteSurroundingText(before: Int, after: Int): Boolean {
                    if (!acceptsInput(version)) return false
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
                    if (!acceptsInput(version)) return false
                    val composing = bridge.composing
                    if (!composing) bridge.delete(before,after)
                    val result = super.deleteSurroundingTextInCodePoints(before,after)
                    if (composing) syncDraft()
                    return result
                }
                override fun performEditorAction(action: Int): Boolean {
                    if (!acceptsInput(version)) return false
                    bridge.finish(); bridge.enter(); return true
                }
                override fun sendKeyEvent(event: KeyEvent): Boolean {
                    if (!acceptsInput(version)) return false
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
        val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding((8*resources.displayMetrics.density).toInt(),0,(8*resources.displayMetrics.density).toInt(),(4*resources.displayMetrics.density).toInt()) }
        addView(content)
        field.apply {
            hint = context.getString(R.string.ime_hint)
            textSize = 16f
            setPadding((12*resources.displayMetrics.density).toInt(),(8*resources.displayMetrics.density).toInt(),(12*resources.displayMetrics.density).toInt(),(8*resources.displayMetrics.density).toInt())
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_ACTION_NONE
            isSaveEnabled = false; isLongClickable = false; minLines = 1; maxLines = 1
        }
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        field.minHeight = dp(48)
        content.addView(field,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin = dp(4) })
        feedback.maxLines = 2; feedback.textSize = 12f
        feedback.ellipsize = android.text.TextUtils.TruncateAt.END
        feedback.setPadding(dp(4),0,dp(4),dp(4))
        feedback.accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE
        content.addView(feedback)
        val strip = HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false }
        val modifierRow = LinearLayout(context).apply { gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(0,dp(4),0,0) }
        strip.addView(modifierRow)
        content.addView(strip,LinearLayout.LayoutParams(-1,-2))
        for ((label,bit) in listOf("Ctrl" to KeyboardReports.CTRL,"Alt" to KeyboardReports.ALT,
            "Shift" to KeyboardReports.SHIFT,"Super" to KeyboardReports.SUPER)) {
            val toggle = ToggleButton(context).apply {
                textOn = "$label ●"; textOff = label; isChecked = false
                minWidth = dp(64); minimumWidth = dp(64); setPadding(dp(8),0,dp(8),0); isFocusable = false
                setOnCheckedChangeListener { _,checked -> if (!rendering) modifier(bit,checked) }
            }
            modifiers[bit] = toggle; controls.add(toggle)
            modifierRow.addView(toggle,LinearLayout.LayoutParams(-2,-2).apply { marginEnd = dp(6) })
        }
        for ((label,usage) in UsKeyboard.special) {
            val button = Button(context).apply {
                text = label; isAllCaps = false; minWidth = dp(64); minimumWidth = dp(64); setPadding(dp(8),0,dp(8),0)
                KeyboardControlLabels.special(usage)?.let { contentDescription = context.getString(it) }
                textSize = 13f; isFocusable = false
                setOnClickListener { bridge.finish(); tap(UsKeyboard.Stroke(usage)) }
            }
            controls.add(button)
            modifierRow.addView(button,LinearLayout.LayoutParams(-2,-2).apply { marginEnd = dp(6) })
        }
        val release = Button(context).apply { isFocusable = false; setText(R.string.release_keys); setOnClickListener { resetInput(); clear() } }
        release.minWidth = dp(132); release.minimumWidth = dp(132)
        controls.add(release); modifierRow.addView(release,LinearLayout.LayoutParams(-2,-2).apply { marginEnd = dp(4) })
    }
    private fun activateIme(version: Int) {
        val ready = focusAllowed && visibility == android.view.View.VISIBLE && isAttachedToWindow && hasWindowFocus() && field.width > 0 && field.height > 0
        val manager = context.getSystemService(InputMethodManager::class.java)
        val shown = imeSession.activate(version,ready,
            { field.requestFocus().also { imeTrace { "IME focus acquired=$it fieldFocused=${field.hasFocus()}" } } },
            { manager.restartInput(field); imeTrace { "IME restarted version=$version remote=${bridge.enabled}" } },
            { manager.showSoftInput(field,InputMethodManager.SHOW_IMPLICIT) })
        imeTrace { "IME activation version=$version ready=$ready shown=$shown pending=${imeSession.pending}" }
    }
    private fun postActivation(version: Int) { field.post { activateIme(version) } }
    fun setTypingFocusAllowed(allowed: Boolean) { focusAllowed = allowed }
    fun openIme() {
        val version = imeSession.requestOpen()
        imeTrace { "IME open requested version=$version" }
        requestLayout()
        postActivation(version)
    }
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed,left,top,right,bottom)
        if (imeSession.pending) postActivation(imeSession.version)
    }
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus && focusAllowed && imeSession.open) {
            if (!field.hasFocus() && !imeSession.pending) imeSession.reset()
            if (imeSession.pending) postActivation(imeSession.version)
        }
    }
    fun resetInput() {
        val version = imeSession.reset(); bridge.reset(); field.text.clear()
        if (imeSession.open) postActivation(version)
        else context.getSystemService(InputMethodManager::class.java).restartInput(field)
    }
    fun closeIme() {
        imeSession.close()
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
        if (enabled && !remoteEnabled) feedback.setText(R.string.ime_ready)
        remoteEnabled = enabled
        if (!enabled) { bridge.cancel(); feedback.setText(R.string.ime_disconnected) }
        rendering = true
        controls.forEach { it.isEnabled = enabled }
        modifiers.forEach { (bit,toggle) -> toggle.isChecked = mask and bit != 0 }
        rendering = false
    }
}
