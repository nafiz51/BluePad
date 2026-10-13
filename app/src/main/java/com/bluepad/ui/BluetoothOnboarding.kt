package com.bluepad.ui

/** One outstanding system request; denial/cancellation requires an explicit retry. */
class BluetoothOnboarding(var blocked: Boolean = false, var pending: Action = Action.NONE) {
    enum class Action { NONE, PERMISSION, ENABLE, APP_SETTINGS, READY }
    fun next(permission: Boolean, enabled: Boolean, available: Boolean, permanent: Boolean, manual: Boolean = false): Action {
        if (pending != Action.NONE || !available) return Action.NONE
        if (permission && enabled) { blocked = false; return Action.READY }
        if (manual) blocked = false
        if (blocked || permanent && !manual) return Action.NONE
        pending = when {
            !permission -> if(permanent) Action.APP_SETTINGS else Action.PERMISSION
            else -> Action.ENABLE
        }
        return pending
    }
    fun complete(success: Boolean) { pending = Action.NONE; blocked = !success }
}
