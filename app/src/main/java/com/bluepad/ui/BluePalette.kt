package com.bluepad.ui

/** Solid native colors only; selected controls use white on royal blue in both themes. */
class BluePalette(dark: Boolean) {
    val background = if(dark) 0xff101b2b.toInt() else 0xfff2f7ff.toInt()
    val surface = if(dark) 0xff1b2a40.toInt() else 0xffedf4ff.toInt()
    val touchpad = if(dark) 0xff152338.toInt() else 0xfff8fbff.toInt()
    val primary = 0xff2563eb.toInt()
    val accent = if(dark) 0xff60a5fa.toInt() else primary
    val foreground = if(dark) 0xffeaf2ff.toInt() else 0xff142c4b.toInt()
    val muted = if(dark) 0xff98acc7.toInt() else 0xff4c6585.toInt()
    val pressed = 0xff1d4ed8.toInt()
    val border = if(dark) 0xff324d70.toInt() else 0xffbdcfe9.toInt()
    val strip = if(dark) 0x182563eb else 0x122563eb
    val separator = if(dark) 0xff365d8b.toInt() else 0xffafc7eb.toInt()
}
