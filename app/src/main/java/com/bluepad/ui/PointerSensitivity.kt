package com.bluepad.ui

import kotlin.math.roundToInt

object PointerSensitivity {
    const val MIN = 0.5f
    const val MAX = 8f
    const val DEFAULT = 1f
    const val SLIDER_MAX = 75
    fun restore(saved: Float) = if (saved.isFinite()) saved.coerceIn(MIN,MAX) else DEFAULT
    fun progress(value: Float) = ((restore(value) - MIN) * 10).roundToInt()
    fun value(progress: Int) = MIN + progress.coerceIn(0,SLIDER_MAX) / 10f
}
