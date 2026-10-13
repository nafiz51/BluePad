package com.bluepad.bluetooth

/** Order recovery effects before invoking transport; existing manager owns its deadline/state. */
internal object ReportRejectionRecovery {
    inline fun run(alreadyRecovering: Boolean, prepare: () -> Unit, release: () -> Unit,
        disconnect: () -> Boolean, rejected: () -> Unit) {
        if (alreadyRecovering) return
        prepare()
        release()
        if (!disconnect()) rejected()
    }
}
