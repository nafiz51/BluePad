package com.bluepad.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.view.ViewConfiguration
import com.bluepad.diagnostics.Diagnostics
import android.util.Log
import android.view.MotionEvent
import android.widget.TextView
import com.bluepad.R

class TouchpadView @JvmOverloads constructor(
    context: Context,
    private val move: (Int, Int) -> Unit = { _, _ -> },
    private val scroll: (Int) -> Unit = {},
    private val gestureButton: (Int, Boolean) -> Unit = { _, _ -> },
    private val zoom: (Int) -> Unit = {}
) : TextView(context) {
    private companion object { const val TAG = "BluePadTouch" }
    private val debugTrace = Diagnostics.enabled
    private val motion = TouchpadMotion()
    var scrollSettings = ScrollSettings().also { motion.scrollSensitivity = it.sensitivity; motion.naturalScrolling = it.natural }
        set(value) {
            if (field == value) return
            cancelMotion(); field = value
            motion.naturalScrolling = value.natural; motion.scrollSensitivity = value.sensitivity
        }
    private val pinch = PinchGesture()
    private var pinchSession = false
    private val zoomPacer = ZoomPacer()
    private val configuration = ViewConfiguration.get(context)
    private val gestures = TouchpadGestures(
        configuration.scaledTouchSlop / resources.displayMetrics.density,
        configuration.scaledDoubleTapSlop / resources.displayMetrics.density,
        ViewConfiguration.getLongPressTimeout().coerceAtMost(250).toLong(),
        ViewConfiguration.getDoubleTapTimeout().toLong(),
        ViewConfiguration.getTapTimeout().coerceAtLeast(180).toLong(),
        clickGapMs = 20,
        reportClock = { SystemClock.uptimeMillis() },
        diagnostic = if (debugTrace) { message -> if (Diagnostics.isEnabled(TAG)) trace { message() } } else null
    ) { change ->
        trace { "gesture button mask=${change.mask} down=${change.down}" }
        if (!change.down) flushMotion()
        gestureButton(change.mask,change.down)
    }
    private var physicalHeld = false
    private var gestureSuppressed = false
    private var sampledPointers = emptySet<Int>()
    private val hold = Runnable {
        if (isEnabled && !physicalHeld && !gestureSuppressed) { gestures.advance(SystemClock.uptimeMillis()); scheduleHold() }
    }
    fun setPhysicalButtonHeld(held: Boolean) {
        physicalHeld = held; gestureSuppressed = true
        pinch.reset(); pinchSession = false; zoomPacer.cancel()
        removeCallbacks(hold); gestures.cancel()
    }
    private fun scheduleHold() {
        trace { "timer_reschedule due=${gestures.holdDeadline}" }
        removeCallbacks(hold)
        gestures.holdDeadline?.let { postDelayed(hold, (it - SystemClock.uptimeMillis()).coerceAtLeast(0)) }
    }
    private val stripPaint = Paint().apply { color = 0x101565c0 }
    private val separatorPaint = Paint().apply { color = 0x60909090; strokeWidth = resources.displayMetrics.density }
    /** Theme paints are configured once; gesture geometry and event processing are unchanged. */
    fun setVisualColors(strip: Int, separator: Int) { stripPaint.color = strip; separatorPaint.color = separator; invalidate() }
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
    private inline fun trace(message: () -> String) = Diagnostics.debug(TAG,message)
    private fun scheduleFrame() {
        if (!framePending && (motion.hasPendingReport || zoomPacer.pending)) {
            framePending = true
            val delay = if (motion.hasPendingReport) 0L else zoomPacer.delay(SystemClock.uptimeMillis())
            if (delay == 0L) postOnAnimation(frame) else postDelayed(frame,delay)
        }
    }
    private fun emitFrame() {
        val zoomSteps = zoomPacer.poll(SystemClock.uptimeMillis())
        if (zoomSteps != 0) zoom(zoomSteps)
        val delta = motion.drain()
        trace { "frame dx=${delta.x} dy=${delta.y} wheel=${delta.wheel} sensitivity=$sensitivity" }
        if (delta.x != 0 || delta.y != 0) move(delta.x, delta.y)
        if (delta.wheel != 0) scroll(delta.wheel)
        // Drain any overflow on later frames instead of dropping fast swipes at report bounds.
        if (motion.hasPendingReport || zoomPacer.pending) scheduleFrame()
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
        trace { "motion_cancel resetGestures=$resetGestures timerDue=${gestures.holdDeadline}" }
        removeCallbacks(frame); removeCallbacks(hold); framePending = false; motion.reset(); sampledPointers = emptySet()
        pinch.reset(); pinchSession = false; zoomPacer.cancel()
        if (resetGestures) gestures.cancel()
        parent?.requestDisallowInterceptTouchEvent(false)
    }
    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        if (!enabled) { physicalHeld = false; gestureSuppressed = false; cancelMotion() }
    }
    override fun onDetachedFromWindow() { physicalHeld = false; gestureSuppressed = false; cancelMotion(); super.onDetachedFromWindow() }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        trace { "event action=${MotionEvent.actionToString(event.action)} eventTime=${event.eventTime} downTime=${event.downTime} history=${event.historySize} enabled=$isEnabled count=${event.pointerCount}" }
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
                    val steps = if (!physicalHeld && !gestureSuppressed && (!pinchSession || pinch.mode == PinchGesture.Mode.PINCH)) pinch.sample(fingers) else 0
                    if (pinch.mode == PinchGesture.Mode.PINCH && !pinchSession) {
                        pinchSession = true; gestures.cancel(); motion.reset(); removeCallbacks(hold)
                    }
                    if (pinchSession) { if (fingers.size == 2) zoomPacer.submit(steps) else zoomPacer.cancel() }
                    if (fingers.size == 2 && pinch.mode == PinchGesture.Mode.SCROLL && !pinchSession && !physicalHeld && !gestureSuppressed) gestures.suppressTaps()
                    val ending = history == null && event.actionMasked == MotionEvent.ACTION_UP
                    if (!physicalHeld && !gestureSuppressed && !pinchSession) gestures.sample(now,fingers,width / density,ending)
                    if (ending) trace { "terminal UP gesture before=$before after=${gestures.phase}" }
                    if (before != gestures.phase) trace { "gesture $before -> ${gestures.phase} time=$now" }
                    val pointers = fingers.map { it.id }.toSet()
                    val classifiedMotion = fingers.size != 2 || pinch.mode == PinchGesture.Mode.SCROLL || physicalHeld || gestureSuppressed
                    if (!pinchSession && ((classifiedMotion && (physicalHeld || gestureSuppressed || gestures.allowsMotion)) || pointers != sampledPointers)) {
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
                    if (!physicalHeld && !gestureSuppressed && !pinchSession) gestures.sample(event.eventTime,emptyList(),width / density)
                    if (pinchSession) { zoomPacer.cancel(); pinch.reset() }
                    motion.sample(emptyList()); sampledPointers = emptySet(); removeCallbacks(hold)
                    parent?.requestDisallowInterceptTouchEvent(false)
                    performClick()
                    scheduleHold()
                }
                scheduleFrame()
            }
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}
