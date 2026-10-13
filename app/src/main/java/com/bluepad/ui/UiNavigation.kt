package com.bluepad.ui

/** Flat native panels; Spinner/pop-up navigation remains owned by Android. */
class UiNavigation(var panel: Panel = Panel.MAIN) {
    enum class Panel { MAIN, SETTINGS, CONNECTION }
    enum class Back { DISMISS_IME, CLOSE_PANEL, EXIT }
    fun toggle(target: Panel) { panel = if (panel == target) Panel.MAIN else target }
    fun closePanel() { panel = Panel.MAIN }
    fun back(imeOpen: Boolean) = when {
        imeOpen -> Back.DISMISS_IME
        panel != Panel.MAIN -> Back.CLOSE_PANEL
        else -> Back.EXIT
    }
    companion object {
        fun restore(value: String?) = UiNavigation(Panel.entries.firstOrNull { it.name == value } ?: Panel.MAIN)
    }
}
