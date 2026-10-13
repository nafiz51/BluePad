package com.bluepad.ui

import kotlin.math.hypot
import kotlin.math.abs

/** Conservative classification, locked for a two-pointer contact; coordinates are dp. */
class PinchGesture {
    enum class Mode { UNDECIDED, SCROLL, PINCH }
    var mode = Mode.UNDECIDED
        private set
    private var origin = emptyList<TouchpadMotion.Finger>()
    private var lastSpan = 0.0
    private var remainder = 0.0
    private fun span(f: List<TouchpadMotion.Finger>) = hypot((f[1].x-f[0].x).toDouble(),(f[1].y-f[0].y).toDouble())
    fun sample(fingers: List<TouchpadMotion.Finger>): Int {
        if (fingers.size != 2) { reset(); return 0 }
        val f = fingers.sortedBy { it.id }
        if (origin.map { it.id } != f.map { it.id }) {
            reset(); origin = f; lastSpan = span(f); return 0
        }
        val distance = span(f)
        val change = distance - span(origin)
        val ax = (f[0].x-origin[0].x).toDouble(); val ay = (f[0].y-origin[0].y).toDouble()
        val bx = (f[1].x-origin[1].x).toDouble(); val by = (f[1].y-origin[1].y).toDouble()
        val translation = hypot((ax+bx)/2,(ay+by)/2)
        if (mode == Mode.UNDECIDED) {
            if (abs(change) >= 12 && abs(change) > translation * 1.8 && ax*bx+ay*by <= 0) {
                mode = Mode.PINCH
                remainder += change
            } else if (translation >= 8 && translation * 1.8 > abs(change)) mode = Mode.SCROLL
        } else if (mode == Mode.PINCH) remainder += distance-lastSpan
        lastSpan = distance
        if (mode != Mode.PINCH) return 0
        val steps = (remainder / 32).toInt()
        remainder -= steps * 32
        return steps
    }
    fun reset() { mode = Mode.UNDECIDED; origin = emptyList(); lastSpan = 0.0; remainder = 0.0 }
}
