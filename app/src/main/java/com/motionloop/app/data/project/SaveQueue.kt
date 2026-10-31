package com.motionloop.app.data.project

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Turns a burst of saves (a slider drag, typing) into one write per key: each save waits [delayMs]
 * for a newer one, then only the newest value is written. Writes run on [serial] one at a time,
 * in the order they were queued.
 *
 * [write] must not throw: an exception would end up in [scope]'s handler.
 */
class SaveQueue<T : Any>(
    private val scope: CoroutineScope,
    private val serial: CoroutineDispatcher,
    private val delayMs: Long,
    private val write: (T) -> Unit,
) {
    private val pending = ConcurrentHashMap<String, T>()
    private val timers = ConcurrentHashMap<String, Job>()

    /** Queues [value] for [key], replacing a value that is still waiting. */
    fun submit(key: String, value: T) {
        pending[key] = value
        val timer = scope.launch {
            delay(delayMs)
            // Only this timer's own entry: a newer timer may already have replaced it.
            timers.remove(key, coroutineContext.job)
            queueWrite(key)
        }
        timers.put(key, timer)?.cancel()
    }

    /** Starts every waiting write now, without suspending. Safe from any thread, also while a screen closes. */
    fun writePending() {
        for (key in pending.keys) {
            timers.remove(key)?.cancel()
            queueWrite(key)
        }
    }

    /** Writes everything submitted so far, and returns once it is written. */
    suspend fun flush() {
        writePending()
        // The serial dispatcher runs blocks in order, so this returns after every write queued above.
        withContext(serial) { }
    }

    /** Drops the waiting write for [key]. A write already running still finishes on [serial]. */
    fun cancel(key: String) {
        pending.remove(key)
        timers.remove(key)?.cancel()
    }

    // Its own coroutine in the scope, not a child of the timer: cancelling a timer that already
    // fired must never stop a write. A queued write that finds nothing pending does nothing.
    private fun queueWrite(key: String) {
        scope.launch(serial) {
            val latest = pending.remove(key) ?: return@launch
            write(latest)
        }
    }
}
