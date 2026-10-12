package com.bluepad.ui

import android.content.Context
import com.bluepad.R
import android.view.MotionEvent
import android.widget.TextView
import kotlin.math.roundToInt

class TouchpadView @JvmOverloads constructor(context: Context, private val move: (Int, Int) -> Unit = { _, _ -> }) : TextView(context) {
    private var pointer = -1
    private var x = 0f
    private var y = 0f
    private var remainderX = 0f
    private var remainderY = 0f
    init { setText(R.string.touchpad); gravity = android.view.Gravity.CENTER; setBackgroundColor(0xffdddddd.toInt()); contentDescription = "Touchpad" }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { pointer = event.getPointerId(0); x = event.x; y = event.y; remainderX = 0f; remainderY = 0f; parent.requestDisallowInterceptTouchEvent(true) }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(pointer)
                if (index >= 0) {
                    remainderX += (event.getX(index) - x) / resources.displayMetrics.density
                    remainderY += (event.getY(index) - y) / resources.displayMetrics.density
                    x = event.getX(index); y = event.getY(index)
                    val dx = remainderX.roundToInt().coerceIn(-127,127); val dy = remainderY.roundToInt().coerceIn(-127,127)
                    remainderX -= dx; remainderY -= dy
                    if (dx != 0 || dy != 0) move(dx,dy)
                }
            }
            MotionEvent.ACTION_POINTER_UP -> if (event.getPointerId(event.actionIndex) == pointer) { pointer = -1; remainderX = 0f; remainderY = 0f }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { pointer = -1; remainderX = 0f; remainderY = 0f; parent.requestDisallowInterceptTouchEvent(false); if (event.actionMasked == MotionEvent.ACTION_UP) performClick() }
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}
