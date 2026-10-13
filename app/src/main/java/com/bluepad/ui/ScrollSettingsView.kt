package com.bluepad.ui

import android.content.Context
import android.widget.*
import com.bluepad.R

class ScrollSettingsView @JvmOverloads constructor(context: Context,
    private val changed: (ScrollSettings) -> Unit = {}) : LinearLayout(context) {
    private val preferences = context.getSharedPreferences("gestures_v8",Context.MODE_PRIVATE)
    private var value = ScrollSettings.restore(preferences.all.mapNotNull { (k,v) -> (v as? String)?.let { k to it } }.toMap())
    init { orientation = VERTICAL; render(); changed(value) }
    private fun save(next: ScrollSettings) {
        if (next == value) return
        value = next
        preferences.edit().apply { value.encode().forEach { (k,v) -> putString(k,v) } }.apply()
        changed(value)
    }
    private fun render() {
        removeAllViews()
        addView(TextView(context).apply { setText(R.string.scroll_controls) })
        addView(Switch(context).apply {
            setText(R.string.natural_scrolling); isChecked = value.natural
            setOnCheckedChangeListener { _,checked -> save(value.copy(natural = checked)) }
        })
        val speed = TextView(context)
        fun showSpeed() { speed.text = context.getString(R.string.scroll_sensitivity,value.sensitivity) }
        showSpeed(); addView(speed)
        addView(SeekBar(context).apply {
            max = 25; progress = kotlin.math.round((value.sensitivity-0.5f)*10).toInt()
            contentDescription = context.getString(R.string.scroll_controls)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) { save(value.copy(sensitivity = 0.5f+progress/10f)); showSpeed() }
                }
                override fun onStartTrackingTouch(bar: SeekBar) {}
                override fun onStopTrackingTouch(bar: SeekBar) {}
            })
        })
        addView(Button(context).apply { setText(R.string.restore_scrolling); setOnClickListener { save(ScrollSettings()); render() } })
    }
}
