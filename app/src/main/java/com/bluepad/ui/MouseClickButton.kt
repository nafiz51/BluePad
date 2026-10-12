package com.bluepad.ui

import android.content.Context
import android.view.MotionEvent
import android.widget.Button

/** Mouse down/up on touch; accessibility activation emits a complete click. */
open class MouseClickButton @JvmOverloads constructor(context: Context,
    press: () -> Unit = {}, release: () -> Unit = {}
) : Button(context) {
    private lateinit var contact: MouseButtonContact
    private var completingTouch = false
    init { contact = MouseButtonContact(press,release); isFocusable = false }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { contact.down(event.getPointerId(event.actionIndex)); isPressed = true }
            MotionEvent.ACTION_CANCEL -> { contact.cancel(); isPressed = false }
            MotionEvent.ACTION_POINTER_UP -> { if (contact.up(event.getPointerId(event.actionIndex))) isPressed = false }
            MotionEvent.ACTION_UP -> {
                val released = contact.up(event.getPointerId(event.actionIndex)); isPressed = false
                if (released) {
                    completingTouch = true
                    try { performClick() } finally { completingTouch = false }
                }
            }
        }
        return true
    }
    override fun performClick(): Boolean {
        if (isEnabled && !completingTouch) contact.activate()
        super.performClick(); return true
    }
    override fun setEnabled(enabled: Boolean) {
        if (!enabled && ::contact.isInitialized) { contact.cancel(); isPressed = false }
        super.setEnabled(enabled)
    }
    override fun onDetachedFromWindow() { contact.cancel(); isPressed = false; super.onDetachedFromWindow() }
}
