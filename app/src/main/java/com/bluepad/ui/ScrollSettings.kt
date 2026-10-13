package com.bluepad.ui

/** Reuses V8's existing preference keys so installed scroll settings remain valid. */
data class ScrollSettings(val natural: Boolean = false, val sensitivity: Float = 1f) {
    init { require(sensitivity.isFinite() && sensitivity in 0.5f..3f) }
    fun encode() = mapOf("natural" to natural.toString(),"scroll" to sensitivity.toString())
    companion object {
        fun restore(values: Map<String,String>) = ScrollSettings(
            values["natural"]?.toBooleanStrictOrNull() ?: false,
            values["scroll"]?.toFloatOrNull()?.takeIf { it.isFinite() && it in 0.5f..3f } ?: 1f)
    }
}
