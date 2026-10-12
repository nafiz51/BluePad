package com.bluepad.ui

/** Deterministic recognition in touchpad dp and MotionEvent uptime; no Android or HID calls. */
class TouchpadGestures(
    private val slop: Float,
    private val doubleSlop: Float,
    private val tapMs: Long,
    private val doubleMs: Long,
    private val holdMs: Long,
    private val emit: (ButtonChange) -> Unit
) {
    data class ButtonChange(val mask: Int, val down: Boolean)
    enum class Phase { IDLE, SINGLE_TAP, TWO_TAP, SECOND_TAP, DRAG, MOTION }
    var phase = Phase.IDLE
        private set
    val allowsMotion get() = phase == Phase.MOTION || phase == Phase.DRAG
    val holdDeadline get() = if (phase == Phase.SECOND_TAP) started + holdMs else null
    private var started = 0L
    private var previous = emptyList<TouchpadMotion.Finger>()
    private val origins = mutableMapOf<Int, TouchpadMotion.Finger>()
    private var firstTap: Pair<Long, TouchpadMotion.Finger>? = null
    private var eligible = false
    private fun distance(a: TouchpadMotion.Finger, b: TouchpadMotion.Finger): Float {
        val dx = a.x - b.x; val dy = a.y - b.y
        return dx * dx + dy * dy
    }
    private fun click(mask: Int) { emit(ButtonChange(mask,true)); emit(ButtonChange(mask,false)) }
    private fun startDrag() { phase = Phase.DRAG; firstTap = null; emit(ButtonChange(1,true)) }
    fun sample(now: Long, fingers: List<TouchpadMotion.Finger>, width: Float) {
        // Recognition is finished for this stream: scrolling/movement cannot turn back into a tap.
        if (phase == Phase.MOTION && previous.isNotEmpty()) {
            previous = fingers
            if (fingers.isEmpty()) { phase = Phase.IDLE; origins.clear() }
            return
        }
        if (previous.isEmpty() && fingers.isNotEmpty()) {
            val first = fingers.first()
            eligible = first.x < TouchpadMotion.scrollZoneStart(width)
            val tap = firstTap
            val second = eligible && tap != null && now - tap.first in 0..doubleMs && distance(first,tap.second) <= doubleSlop * doubleSlop
            started = now; origins.clear(); origins[first.id] = first
            phase = if (!eligible) Phase.MOTION else if (second) Phase.SECOND_TAP else Phase.SINGLE_TAP
            firstTap = null
        }
        for (finger in fingers) origins.putIfAbsent(finger.id, finger)
        val moved = fingers.any { distance(it, origins.getValue(it.id)) > slop * slop }
        if (fingers.size > 1 && phase == Phase.DRAG) {
            emit(ButtonChange(1,false)); phase = Phase.MOTION
        }
        if (fingers.size == 2 && previous.size == 1 && phase in listOf(Phase.SINGLE_TAP,Phase.SECOND_TAP)) {
            val inMain = fingers.all { it.x < TouchpadMotion.scrollZoneStart(width) }
            phase = if (eligible && inMain && !moved && now - started <= tapMs) Phase.TWO_TAP else Phase.MOTION
            firstTap = null
        }
        if (fingers.size > 2 || origins.size > 2) { phase = Phase.MOTION; firstTap = null }
        if (phase == Phase.SECOND_TAP && fingers.size == 1 && moved) startDrag()
        if (phase in listOf(Phase.SINGLE_TAP,Phase.TWO_TAP) && (moved || now - started > tapMs)) phase = Phase.MOTION
        if (fingers.isEmpty() && previous.isNotEmpty()) {
            when (phase) {
                Phase.SINGLE_TAP -> if (now - started <= tapMs) {
                    click(1); firstTap = now to origins.values.first()
                }
                Phase.TWO_TAP -> if (now - started <= tapMs) click(2)
                Phase.SECOND_TAP -> click(1)
                Phase.DRAG -> emit(ButtonChange(1,false))
                else -> {}
            }
            phase = Phase.IDLE; origins.clear()
        }
        previous = fingers
        // Only an actual hold with a finger present can acquire the drag button.
        advance(now)
    }
    fun advance(now: Long) {
        if (phase == Phase.SECOND_TAP && previous.size == 1 && now >= started + holdMs) startDrag()
    }
    fun cancel() {
        if (phase == Phase.DRAG) emit(ButtonChange(1,false))
        phase = Phase.IDLE; previous = emptyList(); origins.clear(); firstTap = null
    }
}
