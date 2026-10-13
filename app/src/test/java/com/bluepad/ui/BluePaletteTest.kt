package com.bluepad.ui

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.pow
import kotlin.math.max
import kotlin.math.min

class BluePaletteTest {
    private fun luminance(color: Int): Double {
        fun channel(shift: Int): Double {
            val c = ((color ushr shift) and 255)/255.0
            return if(c <= 0.04045) c/12.92 else ((c+0.055)/1.055).pow(2.4)
        }
        return 0.2126*channel(16)+0.7152*channel(8)+0.0722*channel(0)
    }
    private fun contrast(a: Int,b: Int): Double {
        val x = luminance(a); val y = luminance(b)
        return (max(x,y)+0.05)/(min(x,y)+0.05)
    }
    @Test fun textAndControlsHaveAccessibleContrastInBothThemes() {
        for(dark in listOf(false,true)) {
            val p = BluePalette(dark)
            for(bg in listOf(p.background,p.surface,p.touchpad)) {
                assertTrue("Primary text",contrast(p.foreground,bg) >= 4.5)
                assertTrue("Secondary text",contrast(p.muted,bg) >= 4.5)
                assertTrue("Blue control labels",contrast(p.accent,bg) >= 4.5)
            }
            assertTrue(contrast(-1,p.primary) >= 4.5)
            assertTrue(contrast(-1,p.pressed) >= 4.5)
        }
    }
    @Test fun midnightIdentityUsesRequestedSolidPalette() {
        val p = BluePalette(true)
        assertEquals(0xff101b2b.toInt(),p.background)
        assertEquals(0xff152338.toInt(),p.touchpad)
        assertEquals(0xff1b2a40.toInt(),p.surface)
        assertEquals(0xff2563eb.toInt(),p.primary)
        assertEquals(0xff60a5fa.toInt(),p.accent)
        assertTrue(p.strip ushr 24 < 32) // Subtle strip, without opaque decoration.
    }
}
