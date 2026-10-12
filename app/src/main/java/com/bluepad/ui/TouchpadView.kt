package com.bluepad.ui

import android.content.Context
import android.util.Log
import android.view.MotionEvent
import android.widget.TextView
import com.bluepad.R

class TouchpadView @JvmOverloads constructor(
    context: Context,
    private val move: (Int, Int) -> Unit = { _, _ -> },
    private val scroll: (Int) -> Unit = {}
) : TextView(context) {
    private companion object { const val TAG = "BluePadTouch" }
    private val motion = TouchpadMotion()
    var sensitivity: Float
        get() = motion.sensitivity
        set(value) { motion.sensitivity = value }
    private var framePending = false
    private val frame = Runnable {
        framePending = false
        if (isEnabled) emitFrame() else cancelMotion()
    }
    init {
        setText(R.string.touchpad)
        gravity = android.view.Gravity.CENTER
        setBackgroundColor(0xffdddddd.toInt())
        contentDescription = context.getString(R.string.touchpad)
    }
    private fun trace(message: String) { if (Log.isLoggable(TAG, Log.DEBUG)) Log.d(TAG, message) }
    private fun scheduleFrame() {
        if (!framePending) { framePending = true; postOnAnimation(frame) }
    }
    private fun emitFrame() {
        val delta = motion.drain()
        trace("frame dx=${delta.x} dy=${delta.y} wheel=${delta.wheel} sensitivity=$sensitivity")
        if (delta.x != 0 || delta.y != 0) move(delta.x, delta.y)
        if (delta.wheel != 0) scroll(delta.wheel)
        // Drain any overflow on later frames instead of dropping fast swipes at report bounds.
        if (!delta.empty) scheduleFrame()
    }
    private fun cancelMotion() {
        removeCallbacks(frame); framePending = false; motion.reset()
        parent?.requestDisallowInterceptTouchEvent(false)
    }
    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        if (!enabled) cancelMotion()
    }
    override fun onDetachedFromWindow() { cancelMotion(); super.onDetachedFromWindow() }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        trace("event action=${event.actionMasked} enabled=$isEnabled count=${event.pointerCount}")
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_CANCEL -> cancelMotion()
            else -> {
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    cancelMotion(); parent?.requestDisallowInterceptTouchEvent(true)
                }
                val density = resources.displayMetrics.density
                fun sample(history: Int? = null, skipLifted: Boolean = false) {
                    val fingers = (0 until event.pointerCount).filter {
                        !(skipLifted && it == event.actionIndex)
                    }.map { index ->
                        TouchpadMotion.Finger(event.getPointerId(index),
                            (history?.let { event.getHistoricalX(index, it) } ?: event.getX(index)) / density,
                            (history?.let { event.getHistoricalY(index, it) } ?: event.getY(index)) / density)
                    }
                    motion.sample(fingers)
                }
                if (event.actionMasked == MotionEvent.ACTION_MOVE) {
                    repeat(event.historySize) { sample(it) }
                }
                sample()
                if (event.actionMasked == MotionEvent.ACTION_POINTER_UP) sample(skipLifted = true)
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    motion.sample(emptyList())
                    parent?.requestDisallowInterceptTouchEvent(false)
                    performClick()
                }
                scheduleFrame()
            }
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}
