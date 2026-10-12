package com.bluepad.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.view.ViewConfiguration
import android.util.Log
import android.view.MotionEvent
import android.widget.TextView
import com.bluepad.R

class TouchpadView @JvmOverloads constructor(
    context: Context,
    private val move: (Int, Int) -> Unit = { _, _ -> },
    private val scroll: (Int) -> Unit = {},
    private val gestureButton: (Int, Boolean) -> Unit = { _, _ -> }
) : TextView(context) {
    private companion object { const val TAG = "BluePadTouch" }
    private val motion = TouchpadMotion()
    private val configuration = ViewConfiguration.get(context)
    private val gestures = TouchpadGestures(
        configuration.scaledTouchSlop / resources.displayMetrics.density,
        configuration.scaledDoubleTapSlop / resources.displayMetrics.density,
        ViewConfiguration.getLongPressTimeout().coerceAtMost(250).toLong(),
        ViewConfiguration.getDoubleTapTimeout().toLong(),
        ViewConfiguration.getTapTimeout().toLong()
    ) { change ->
        trace("gesture button mask=${change.mask} down=${change.down}")
        if (!change.down) flushMotion()
        gestureButton(change.mask,change.down)
    }
    private var physicalHeld = false
    private var gestureSuppressed = false
    private var sampledPointers = emptySet<Int>()
    private val hold = Runnable {
        if (isEnabled && !physicalHeld && !gestureSuppressed) gestures.advance(SystemClock.uptimeMillis())
    }
    fun setPhysicalButtonHeld(held: Boolean) {
        physicalHeld = held; gestureSuppressed = true
        removeCallbacks(hold); gestures.cancel()
    }
    private fun scheduleHold() {
        removeCallbacks(hold)
        gestures.holdDeadline?.let { postDelayed(hold, (it - SystemClock.uptimeMillis()).coerceAtLeast(0)) }
    }
    private val stripPaint = Paint().apply { color = 0x101565c0 }
    private val separatorPaint = Paint().apply { color = 0x60909090; strokeWidth = resources.displayMetrics.density }
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
    override fun onDraw(canvas: Canvas) {
        val boundary = TouchpadMotion.scrollZoneStart(width.toFloat())
        canvas.drawRect(boundary, 0f, width.toFloat(), height.toFloat(), stripPaint)
        canvas.drawLine(boundary, 0f, boundary, height.toFloat(), separatorPaint)
        super.onDraw(canvas)
    }
    private fun trace(message: String) { if (Log.isLoggable(TAG, Log.DEBUG)) Log.d(TAG, message) }
    private fun scheduleFrame() {
        if (!framePending && motion.hasPendingReport) { framePending = true; postOnAnimation(frame) }
    }
    private fun emitFrame() {
        val delta = motion.drain()
        trace("frame dx=${delta.x} dy=${delta.y} wheel=${delta.wheel} sensitivity=$sensitivity")
        if (delta.x != 0 || delta.y != 0) move(delta.x, delta.y)
        if (delta.wheel != 0) scroll(delta.wheel)
        // Drain any overflow on later frames instead of dropping fast swipes at report bounds.
        if (motion.hasPendingReport) scheduleFrame()
    }
    private fun flushMotion() {
        if (!isEnabled) return
        var delta = motion.drain()
        while (!delta.empty) {
            if (delta.x != 0 || delta.y != 0) move(delta.x,delta.y)
            if (delta.wheel != 0) scroll(delta.wheel)
            delta = motion.drain()
        }
    }
    private fun cancelMotion(resetGestures: Boolean = true) {
        removeCallbacks(frame); removeCallbacks(hold); framePending = false; motion.reset(); sampledPointers = emptySet()
        if (resetGestures) gestures.cancel()
        parent?.requestDisallowInterceptTouchEvent(false)
    }
    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        if (!enabled) { physicalHeld = false; gestureSuppressed = false; cancelMotion() }
    }
    override fun onDetachedFromWindow() { physicalHeld = false; gestureSuppressed = false; cancelMotion(); super.onDetachedFromWindow() }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        trace("event action=${event.actionMasked} enabled=$isEnabled count=${event.pointerCount}")
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_CANCEL -> cancelMotion()
            else -> {
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    cancelMotion(resetGestures = false)
                    if (gestures.phase != TouchpadGestures.Phase.IDLE) gestures.cancel()
                    gestureSuppressed = physicalHeld
                    parent?.requestDisallowInterceptTouchEvent(true)
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
                    val now = history?.let { event.getHistoricalEventTime(it) } ?: event.eventTime
                    val before = gestures.phase
                    if (!physicalHeld && !gestureSuppressed) gestures.sample(now,fingers,width / density)
                    if (before != gestures.phase) trace("gesture $before -> ${gestures.phase} time=$now")
                    val pointers = fingers.map { it.id }.toSet()
                    if (physicalHeld || gestureSuppressed || gestures.allowsMotion || pointers != sampledPointers) {
                        motion.sample(fingers, width / density)
                    }
                    sampledPointers = pointers
                    if (before != gestures.phase) scheduleHold()
                }
                if (event.actionMasked == MotionEvent.ACTION_MOVE) {
                    repeat(event.historySize) { sample(it) }
                }
                sample()
                if (event.actionMasked == MotionEvent.ACTION_POINTER_UP) sample(skipLifted = true)
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    if (!physicalHeld && !gestureSuppressed) gestures.sample(event.eventTime,emptyList(),width / density)
                    motion.sample(emptyList()); sampledPointers = emptySet(); removeCallbacks(hold)
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
