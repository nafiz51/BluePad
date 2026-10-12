package com.bluepad.ui

import android.content.Context
import android.view.MotionEvent
import android.widget.Button

/** Touch holds the left button; accessibility/keyboard activation sends a complete click. */
class LeftClickButton @JvmOverloads constructor(context: Context, private val press: () -> Unit = {}, private val release: () -> Unit = {}) : Button(context) {
    private var completingTouch = false
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { isPressed = true; press() }
            MotionEvent.ACTION_CANCEL -> { isPressed = false; release() }
            MotionEvent.ACTION_UP -> {
                isPressed = false; release(); completingTouch = true
                try { performClick() } finally { completingTouch = false }
            }
        }
        return true
    }
    override fun performClick(): Boolean {
        if (isEnabled && !completingTouch) { press(); release() }
        super.performClick()
        return true
    }
}
