package com.bluepad.diagnostics

import android.os.SystemClock
import android.util.Log
import com.bluepad.BuildConfig

/** Normal APKs stay quiet even if old Android DEBUG tag properties remain set. */
object Diagnostics {
    const val enabled = BuildConfig.VERBOSE_DIAGNOSTICS
    fun permitted(buildEnabled: Boolean, tagEnabled: Boolean) = buildEnabled && tagEnabled
    fun isEnabled(tag: String) = enabled && Log.isLoggable(tag, Log.DEBUG)
    inline fun debug(tag: String, message: () -> String) {
        emit(isEnabled(tag), { "uptime=${SystemClock.uptimeMillis()} ${message()}" }) { Log.d(tag,it) }
    }
    inline fun emit(active: Boolean, message: () -> String, sink: (String) -> Unit) {
        if (active) sink(message())
    }
}
