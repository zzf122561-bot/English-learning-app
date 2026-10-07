package com.xuesui.englishapp.modelprobe.engines.qwen3tts

import org.junit.Assert.*
import org.junit.Test

/** Must execute on ARM64 Android before claiming JNI runtime/device evidence. No model required. */
class QwenJniContractTest {
    @Test fun nativeHandleAndCancellationContracts() {
        val h = QwenNativeBindings.create()
        try {
            assertTrue(QwenNativeBindings.maximumThreads() >= 1)
            val op = QwenNativeBindings.prepare(h)
            assertTrue(QwenNativeBindings.cancel(h, op))
            val result = QwenNativeBindings.nativeLoad(h, op, "must-not-be-read", "must-not-be-read", 1)
            assertEquals(1, result.status)
            assertEquals(0, result.effectiveThreads)
            val next = QwenNativeBindings.prepare(h)
            assertFalse(QwenNativeBindings.cancel(h, op))
            // Bypass Kotlin validate to exercise independent C++ validation.
            val bad = QwenNativeBindings.nativeSynthesize(h, next, "Hello.", QwenNativeParameters(threads = 1, topP = Float.NaN))
            assertEquals(2, bad.status)
            assertTrue(bad.audio.isEmpty())
            assertNull(bad.effective)
            assertEquals("failed", bad.termination)
            assertEquals(0, bad.generatedFrames)
            assertEquals(0, bad.generationSteps)
            assertEquals(0L, bad.prefillMillis)
        } finally { QwenNativeBindings.close(h) }
    }
}
