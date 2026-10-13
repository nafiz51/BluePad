package com.bluepad.ui

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.*

/** Static native styling, applied at view creation and explicit appearance changes; no drawing/motion callbacks. */
class UiStyle(val dark: Boolean, private val density: Float) {
    val palette = BluePalette(dark)
    val background = palette.background
    val surface = palette.surface
    val foreground = palette.foreground
    val muted = palette.muted
    val accent = palette.accent
    private val pressed = palette.pressed
    private val border = palette.border
    fun card(color: Int = surface) = GradientDrawable().apply {
        setColor(color); cornerRadius = 12*density; setStroke(density.toInt().coerceAtLeast(1),border)
    }
    fun toolbarIconBackground() = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed),card(pressed))
        addState(intArrayOf(android.R.attr.state_selected),card(palette.primary))
        addState(intArrayOf(),GradientDrawable().apply { setColor(android.graphics.Color.TRANSPARENT) })
    }
    private fun states() = StateListDrawable().apply {
        addState(intArrayOf(-android.R.attr.state_enabled),card(background))
        addState(intArrayOf(android.R.attr.state_pressed),card(pressed))
        addState(intArrayOf(android.R.attr.state_checked),card(palette.primary))
        addState(intArrayOf(android.R.attr.state_selected),card(palette.primary))
        addState(intArrayOf(),card())
    }
    private fun textStates() = ColorStateList(
        arrayOf(intArrayOf(-android.R.attr.state_enabled),intArrayOf(android.R.attr.state_pressed),intArrayOf(android.R.attr.state_checked),intArrayOf(android.R.attr.state_selected),intArrayOf()),
        intArrayOf(muted,android.graphics.Color.WHITE,android.graphics.Color.WHITE,android.graphics.Color.WHITE,accent))
    fun applyTree(view: View) {
        when(view) {
            is EditText -> { view.setTextColor(foreground); view.setHintTextColor(muted); view.backgroundTintList = null; view.background = card() }
            is Switch -> { view.setTextColor(foreground); view.thumbTintList = ColorStateList.valueOf(accent) }
            is Button -> {
                view.setTextColor(textStates()); view.background = states(); view.isAllCaps = false
                view.minHeight = (48*density).toInt(); view.minimumHeight = view.minHeight
                view.elevation = 0f; view.stateListAnimator = null
            }
            is TextView -> view.setTextColor(foreground)
            is ImageButton -> { view.imageTintList = textStates(); view.background = states(); view.elevation = 0f; view.stateListAnimator = null }
            is Spinner -> view.setPopupBackgroundDrawable(card())
            is ScrollView -> view.background = card()
            is SeekBar -> { view.progressTintList = ColorStateList.valueOf(accent); view.thumbTintList = ColorStateList.valueOf(accent) }
        }
        if(view is ViewGroup) for(i in 0 until view.childCount) applyTree(view.getChildAt(i))
    }
}
