package com.bluepad.bluetooth

/** Platform states are 0=disconnected, 1=connecting, 2=connected, 3=disconnecting. */
object ConnectionCallbacks {
    /** Only a current, bonded HID profile snapshot may compensate for a missing callback. */
    fun canReconcile(active: Boolean, registered: Boolean, disconnecting: Boolean, paused: Boolean,
        host: String?, device: String, bonded: Boolean, profileState: Int): Boolean =
        active && registered && !disconnecting && !paused && bonded &&
            (host == null || host == device) && profileState == 2

    fun accepts(session: Int, currentSession: Int, active: Boolean,
        host: String?, device: String, state: Int, profileState: Int,
        paused: Boolean, bonded: Boolean): Boolean =
        session == currentSession && active && (host == null || host == device) &&
            state == profileState && (state == 0 || state == 3 || (!paused && bonded))
}
