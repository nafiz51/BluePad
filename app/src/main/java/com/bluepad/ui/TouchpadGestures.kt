package com.bluepad.ui

/** Deterministic recognition in touchpad dp and MotionEvent uptime; no Android or HID calls. */
class TouchpadGestures(
    private val slop: Float,
    private val doubleSlop: Float,
    private val tapMs: Long,
    private val doubleMs: Long,
    private val holdMs: Long,
    private val clickGapMs: Long = 0,
    private val reportClock: (() -> Long)? = null,
    private val diagnostic: (((() -> String)) -> Unit)? = null,
    private val emit: (ButtonChange) -> Unit
) {
    data class ButtonChange(val mask: Int, val down: Boolean)
    enum class Phase { IDLE, SINGLE_TAP, TWO_TAP, SECOND_TAP, DRAG, MOTION }
    var phase = Phase.IDLE
        private set
    val allowsMotion get() = phase == Phase.MOTION || phase == Phase.DRAG
    private val recognitionMs get() = doubleMs.coerceAtMost(120)
    private val clicks = ArrayDeque<ButtonChange>()
    private var clickDue: Long? = null
    private var clock = 0L
    private var clickHeld = false
    val holdDeadline get() = listOfNotNull(clickDue,if (phase == Phase.SECOND_TAP) started + holdMs else if (previous.isEmpty()) firstTap?.first?.plus(recognitionMs) else null).minOrNull()
    private var started = 0L
    private var previous = emptyList<TouchpadMotion.Finger>()
    private val origins = mutableMapOf<Int, TouchpadMotion.Finger>()
    private var firstTap: Pair<Long, TouchpadMotion.Finger>? = null
    private var eligible = false
    private fun distance(a: TouchpadMotion.Finger, b: TouchpadMotion.Finger): Float {
        val dx = a.x - b.x; val dy = a.y - b.y
        return dx * dx + dy * dy
    }
    // A second tap tolerates incidental finger motion beyond normal pointer/tap slop.
    // Use net displacement from this contact's DOWN, never cumulative path length.
    private val dragSlop get() = slop * 2f
    private fun click(mask: Int) {
        diagnostic?.invoke { "click_schedule t=$clock mask=$mask queued=${clicks.size} due=$clickDue" }
        clicks.addLast(ButtonChange(mask,true)); clicks.addLast(ButtonChange(mask,false))
        if (clickDue == null) clickDue = reportClock?.invoke() ?: clock
    }
    private fun startDrag() { diagnostic?.invoke { "drag_classified t=$clock phase=$phase elapsed=${clock-started}" }; cancelClicks(); phase = Phase.DRAG; firstTap = null; emit(ButtonChange(1,true)) }
    fun sample(now: Long, fingers: List<TouchpadMotion.Finger>, width: Float, ending: Boolean = false) {
        diagnostic?.invoke { "sample event=$now count=${fingers.size} phase=$phase ending=$ending" }
        advance(now)
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
            val second = eligible && tap != null && now - tap.first in 0 until recognitionMs && distance(first,tap.second) <= doubleSlop * doubleSlop
            diagnostic?.invoke { "contact_begin event=$now second=$second eligible=$eligible gap=${tap?.let { now-it.first }} window=$recognitionMs" }
            started = now; origins.clear(); origins[first.id] = first
            phase = if (!eligible) Phase.MOTION else if (second) Phase.SECOND_TAP else Phase.SINGLE_TAP
            firstTap = null
        }
        for (finger in fingers) origins.putIfAbsent(finger.id, finger)
        val moved = fingers.any { distance(it, origins.getValue(it.id)) > slop * slop }
        val dragMoved = fingers.any { distance(it, origins.getValue(it.id)) > dragSlop * dragSlop }
        if (moved) diagnostic?.invoke { "slop_crossed event=$now phase=$phase ending=$ending slop=$slop maxDistanceSquared=${fingers.maxOf { distance(it,origins.getValue(it.id)) }}" }
        if (fingers.size > 1 && phase == Phase.DRAG) {
            emit(ButtonChange(1,false)); phase = Phase.MOTION
        }
        if (fingers.size == 2 && previous.size == 1 && phase in listOf(Phase.SINGLE_TAP,Phase.SECOND_TAP)) {
            val inMain = fingers.all { it.x < TouchpadMotion.scrollZoneStart(width) }
            phase = if (eligible && inMain && !moved && now - started <= tapMs) Phase.TWO_TAP else Phase.MOTION
            firstTap = null
        }
        if (fingers.size > 2 || origins.size > 2) { phase = Phase.MOTION; firstTap = null }
        // A lifted finger's terminal coordinate can jitter; only live movement starts a drag.
        if (phase == Phase.SECOND_TAP && fingers.size == 1 && dragMoved && !ending) {
            diagnostic?.invoke { "drag_slop_crossed event=$now threshold=$dragSlop" }
            startDrag()
        }
        if (phase in listOf(Phase.SINGLE_TAP,Phase.TWO_TAP) && (moved || now - started > tapMs)) phase = Phase.MOTION
        if (fingers.isEmpty() && previous.isNotEmpty()) {
            when (phase) {
                Phase.SINGLE_TAP -> if (now - started <= tapMs) {
                    diagnostic?.invoke { "single_pending event=$now due=${now+recognitionMs}" }
                    firstTap = now to origins.values.first()
                }
                Phase.TWO_TAP -> if (now - started <= tapMs) click(2)
                Phase.SECOND_TAP -> { diagnostic?.invoke { "double_recognized event=$now duration=${now-started}" }; click(1); click(1) }
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
        clock = now
        if (previous.isEmpty() && firstTap != null && now >= firstTap!!.first + recognitionMs) { diagnostic?.invoke { "single_expired t=$now due=${firstTap!!.first+recognitionMs}" }; firstTap = null; click(1) }
        if (phase == Phase.SECOND_TAP && previous.size == 1 && now >= started + holdMs) startDrag()
        var dispatchedAt = reportClock?.invoke() ?: now
        while (clickDue != null && dispatchedAt >= clickDue!!) {
            diagnostic?.invoke { "click_edge t=$dispatchedAt event=$now due=$clickDue lateMs=${dispatchedAt-clickDue!!} queued=${clicks.size} edge=${clicks.first()}" }
            val event = clicks.removeFirst(); clickHeld = event.down; emit(event)
            // Gesture decisions use event time; output spacing uses actual emission time.
            dispatchedAt = reportClock?.invoke() ?: now
            clickDue = if (clicks.isEmpty()) null else dispatchedAt + clickGapMs
            if (clickGapMs > 0) break
        }
    }
    private fun cancelClicks() {
        diagnostic?.invoke { "click_cancel t=$clock queued=${clicks.size} held=$clickHeld due=$clickDue" }
        if (clickHeld) { val mask = clicks.firstOrNull()?.mask ?: 1; emit(ButtonChange(mask,false)) }
        clickHeld = false; clicks.clear(); clickDue = null
    }
    fun suppressTaps() {
        if (phase == Phase.DRAG) emit(ButtonChange(1,false))
        firstTap = null; phase = Phase.MOTION
    }
    fun cancel() {
        diagnostic?.invoke { "gesture_cancel t=$clock phase=$phase pendingSingle=${firstTap != null}" }
        cancelClicks()
        if (phase == Phase.DRAG) emit(ButtonChange(1,false))
        phase = Phase.IDLE; previous = emptyList(); origins.clear(); firstTap = null
    }
}
