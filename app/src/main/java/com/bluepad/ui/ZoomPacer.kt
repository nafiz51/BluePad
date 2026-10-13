package com.bluepad.ui

/** One discrete application zoom step per 30 ms, with at most four waiting steps. */
class ZoomPacer {
    private var queued = 0
    private var next = 0L
    val pending get() = queued != 0
    fun submit(steps: Int) {
        if (steps == 0) return
        if (queued != 0 && (queued > 0) != (steps > 0)) queued = 0
        queued = (queued.toLong() + steps).coerceIn(-4,4).toInt()
    }
    fun delay(now: Long) = if (pending) (next-now).coerceAtLeast(0) else 0
    fun poll(now: Long): Int {
        if (!pending || now < next) return 0
        val step = if (queued > 0) 1 else -1
        queued -= step; next = now + 30
        return step
    }
    fun cancel() { queued = 0; next = 0 }
}
