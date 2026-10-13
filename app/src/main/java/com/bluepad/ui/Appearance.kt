package com.bluepad.ui

/** Pure preference/state decisions; UI rendering does not participate in touch processing. */
enum class Appearance { SYSTEM, LIGHT, DARK;
    fun isDark(systemDark: Boolean) = this == DARK || this == SYSTEM && systemDark
    companion object {
        fun oppositeDisplayed(dark: Boolean) = if (dark) LIGHT else DARK
        fun restore(value: String?) = entries.firstOrNull { it.name == value } ?: DARK
    }
}

object MainUiPolicy {
    const val TOUCH_TARGET_DP = 48
    const val TOOLBAR_DP = 48
    const val BUTTON_ROW_DP = 48
    fun panelHeight(available: Int, fixedControls: Int) =
        (available.coerceAtLeast(0) / 2).coerceAtMost((available-fixedControls).coerceAtLeast(0))
    fun keyboardHeight(available: Int, fixedControls: Int, content: Int, minimum: Int) =
        content.coerceAtLeast(minimum).coerceAtMost((available-fixedControls).coerceAtLeast(minimum))
    fun hostLabel(connected: Boolean, deviceName: String?, connection: String) =
        if (connected) deviceName?.takeIf { it.isNotBlank() } ?: connection else connection
}
