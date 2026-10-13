package com.bluepad.diagnostics

/** Process-local bounded connection metadata. Never pass host identities or input content. */
class ConnectionTimeline(private val clock: () -> Long, private val capacity: Int = 200) {
    init { require(capacity > 0) }
    private val events = java.util.ArrayDeque<String>()
    private var discarded = 0L
    var failed = false
        private set
    private val addresses = Regex("(?i)(?:[0-9a-f]{2}:){5}[0-9a-f]{2}")
    fun record(message: String) {
        // HID report callbacks are deliberately excluded, including keyboard output events.
        if (message.startsWith("get_report") || message.startsWith("set_report") || message.startsWith("set_protocol")) return
        val safe = addresses.replace(message,"[redacted]").replace('\n',' ').replace('\r',' ').take(320)
        if (events.size == capacity) { events.removeFirst(); discarded++ }
        events.addLast("uptimeMs=${clock()} $safe")
    }
    fun failure() { failed = true }
    fun report(environment: String): String = buildString {
        append("BluePad local connection diagnostics\n")
        append(environment.take(512).replace('\n',' ').replace('\r',' ')); append('\n')
        append("States: 0=Disconnected 1=Connecting 2=Connected 3=Disconnecting\n")
        append("Times are monotonic uptime, not wall-clock. Old events discarded=$discarded\n")
        events.forEach { append(it); append('\n') }
        append("Android HID callbacks expose no SDP/L2CAP failure reason. API acceptance is not connection success.\n")
    }
}
