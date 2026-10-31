package com.motionloop.app

import com.motionloop.app.data.project.SaveQueue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SaveQueueTest {

    // The test scope itself, not backgroundScope: advanceUntilIdle skips work that is only in the background.
    // Every timer ends or is cancelled, so runTest never waits on a leftover child.
    private fun TestScope.queue(written: MutableList<String>) =
        SaveQueue<String>(this, StandardTestDispatcher(testScheduler), DELAY) { written += it }

    @Test
    fun burstOfSavesWritesOnceWithTheNewestValue() = runTest {
        val written = mutableListOf<String>()
        val q = queue(written)
        repeat(10) {
            q.submit("a", "v$it")
            advanceTimeBy(DELAY / 4)
        }
        assertTrue("nothing written while the burst goes on", written.isEmpty())
        advanceTimeBy(DELAY)
        runCurrent()
        assertEquals(listOf("v9"), written)
    }

    @Test
    fun differentKeysAreWrittenSeparately() = runTest {
        val written = mutableListOf<String>()
        val q = queue(written)
        q.submit("a", "a1")
        q.submit("b", "b1")
        advanceUntilIdle()
        assertEquals(setOf("a1", "b1"), written.toSet())
        assertEquals(2, written.size)
    }

    @Test
    fun flushWritesAtOnceAndOnlyOnce() = runTest {
        val written = mutableListOf<String>()
        val q = queue(written)
        q.submit("a", "a1")
        q.submit("b", "b1")
        q.flush()
        assertEquals(setOf("a1", "b1"), written.toSet())
        // The cancelled timers must not write a second time.
        advanceUntilIdle()
        assertEquals(2, written.size)
    }

    @Test
    fun writePendingStartsTheWriteWithoutWaitingForTheDelay() = runTest {
        val written = mutableListOf<String>()
        val q = queue(written)
        q.submit("a", "a1")
        q.writePending()
        runCurrent()
        assertEquals(listOf("a1"), written)
    }

    @Test
    fun cancelDropsTheWaitingWrite() = runTest {
        val written = mutableListOf<String>()
        val q = queue(written)
        q.submit("a", "a1")
        q.cancel("a")
        advanceUntilIdle()
        assertTrue(written.isEmpty())
    }

    @Test
    fun saveAfterAWriteIsWrittenToo() = runTest {
        val written = mutableListOf<String>()
        val q = queue(written)
        q.submit("a", "a1")
        advanceUntilIdle()
        q.submit("a", "a2")
        advanceUntilIdle()
        assertEquals(listOf("a1", "a2"), written)
    }

    private companion object {
        const val DELAY = 400L
    }
}
