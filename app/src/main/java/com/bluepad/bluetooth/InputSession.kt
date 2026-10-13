package com.bluepad.bluetooth

import java.util.concurrent.atomic.AtomicLong

/** Captured before queueing input; connection/lifecycle transitions invalidate old work. */
class InputSession {
    private val counter = AtomicLong()
    private val acknowledged = AtomicLong(-1)
    val token get() = counter.get()
    fun capture() = acknowledged.get()
    fun acknowledge(token: Long) { if (counter.get() == token) acknowledged.set(token) }
    fun invalidate() { counter.incrementAndGet() }
    fun accepts(captured: Long) = captured == token
}
