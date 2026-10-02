package com.xuesui.englishapp.modelprobe.stage2

import com.xuesui.englishapp.modelprobe.core.runtime.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.*
import org.junit.Test

class TaskRunnerTest {
    @Test fun cancellationRemainsBusyUntilWorkerActuallyReturnsAndCleanupWaits() {
        val entered = CountDownLatch(1); val exit = CountDownLatch(1); val cleaned = CountDownLatch(1)
        val busy = AtomicBoolean(false); val cancelling = AtomicBoolean(false); val hookCalled = AtomicBoolean(false)
        val runner = TaskRunner { b, c -> busy.set(b); cancelling.set(c) }
        assertTrue(runner.submit { token -> token.attach { hookCalled.set(true) }.use { entered.countDown(); assertTrue(exit.await(5, TimeUnit.SECONDS)) } })
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS)); runner.cancel()
            assertTrue(hookCalled.get()); assertTrue(busy.get()); assertTrue(cancelling.get())
            assertFalse(runner.submit { error("Must not overlap") })
            runner.closeAfterExit { cleaned.countDown() }
            assertEquals(1, cleaned.count)
        } finally { exit.countDown() }
        assertTrue(cleaned.await(5, TimeUnit.SECONDS)); assertFalse(busy.get())
    }
    @Test fun preCancellationIsDeliveredWhenNativeOperationRegistersLater() {
        val token = WorkToken(); token.cancel(); var called = false
        token.attach { called = true }.use { assertTrue(called) }
        rejected { token.check() }
    }
}
