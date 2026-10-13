package com.bluepad.hid

/** Only affects zoom reports; ordinary mouse and keyboard reports have no platform selection. */
enum class ZoomCompatibility {
    STANDARD, MACOS_COMPATIBILITY;
    companion object {
        /** An explicit new value wins. Consult legacy host_os only before migration. */
        fun restore(value: String?, legacyHost: String? = null): ZoomCompatibility {
            if (value != null) return entries.firstOrNull { it.name == value } ?: STANDARD
            return if (legacyHost == "MACOS") MACOS_COMPATIBILITY else STANDARD
        }
    }
}
