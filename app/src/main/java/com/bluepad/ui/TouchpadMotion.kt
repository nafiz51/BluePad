package com.bluepad.ui

import kotlin.math.roundToInt

/** Fractional, density-independent motion accumulated until the next display frame. */
class TouchpadMotion {
    companion object {
        fun scrollZoneStart(width: Float) = width * 0.9f
    }
    data class Finger(val id: Int, val x: Float, val y: Float)
    data class Delta(val x: Int, val y: Int, val wheel: Int) {
        val empty get() = x == 0 && y == 0 && wheel == 0
    }
    var sensitivity = 1f
        set(value) { require(value.isFinite() && value in 0.5f..3f); field = value }
    private var previous = emptyList<Finger>()
    private var edgeScroll = false
    private var x = 0f
    private var y = 0f
    private var wheel = 0f

    fun sample(fingers: List<Finger>, width: Float = Float.POSITIVE_INFINITY) {
        if (previous.isEmpty() && fingers.isNotEmpty()) {
            // Lock to the first contact for the entire gesture, including finger changes.
            edgeScroll = width.isFinite() && width > 0f && fingers[0].x >= scrollZoneStart(width)
        }
        if (fingers.isEmpty()) edgeScroll = false
        // Membership changes establish a new baseline, never an artificial pointer jump.
        if (fingers.map { it.id }.toSet() != previous.map { it.id }.toSet()) {
            previous = fingers
            return
        }
        if (fingers.size == 1) {
            if (edgeScroll) {
                wheel -= (fingers[0].y - previous[0].y) / 24f
            } else {
                x += (fingers[0].x - previous[0].x) * sensitivity
                y += (fingers[0].y - previous[0].y) * sensitivity
            }
        } else if (fingers.size == 2) {
            // Positive wheel scrolls up: moving both fingers upward sends positive ticks.
            wheel -= (fingers.sumOf { it.y.toDouble() } - previous.sumOf { it.y.toDouble() }).toFloat() / 2f / 24f
        }
        previous = fingers
    }
    fun drain(): Delta {
        val delta = Delta(x.roundToInt().coerceIn(-127,127), y.roundToInt().coerceIn(-127,127), wheel.toInt().coerceIn(-127,127))
        x -= delta.x; y -= delta.y; wheel -= delta.wheel
        return delta
    }
    fun reset() { previous = emptyList(); edgeScroll = false; x = 0f; y = 0f; wheel = 0f }
}
