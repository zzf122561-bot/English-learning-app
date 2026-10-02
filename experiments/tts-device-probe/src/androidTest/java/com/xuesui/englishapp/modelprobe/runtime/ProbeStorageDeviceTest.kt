package com.xuesui.englishapp.modelprobe.runtime

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xuesui.englishapp.modelprobe.core.model.*
import com.xuesui.englishapp.modelprobe.core.runtime.WorkToken
import com.xuesui.englishapp.modelprobe.core.storage.*
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic data only; exercises actual Android private storage/ATOMIC_MOVE when manager runs it. */
@RunWith(AndroidJUnit4::class)
class ProbeStorageDeviceTest {
    @Test fun privateCopyVerificationAtomicActivationAndLeaseIsolation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "probe-synthetic-${UUID.randomUUID()}").also { it.mkdir() }
        try {
            val bytes = "Synthetic device fixture, no model weights.".toByteArray()
            val spec = ModelFile("data", "fixture.bin", "models/fixture.bin", bytes.size.toLong(), bytes.sha256())
            val d = ModelDescriptor("synthetic", "fixture", "1", "test.storage.v1", "1", "test-data", "Fixture", "test", "test", emptySet(), listOf(spec))
            val store = ModelStore(root)
            store.install(d, WorkToken(), { bytes.inputStream() }) { _, _ -> }
            store.acquire(d, WorkToken(), true).use { lease ->
                assertArrayEquals(bytes, lease.files.getValue("data").readBytes())
                var rejected = false
                try { ModelStore(root).delete(d) } catch (_: IllegalStateException) { rejected = true }
                assertTrue(rejected)
            }
            store.delete(d); assertFalse(store.present(d))
        } finally { removeOwned(context.cacheDir, root) }
    }
}
