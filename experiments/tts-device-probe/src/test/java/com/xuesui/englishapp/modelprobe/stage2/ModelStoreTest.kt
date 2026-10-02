package com.xuesui.englishapp.modelprobe.stage2

import com.xuesui.englishapp.modelprobe.core.model.*
import com.xuesui.englishapp.modelprobe.core.runtime.*
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class ModelStoreTest {
    private val root = Files.createTempDirectory("probe-model-test-").toFile()
    private val descriptor = tinyDescriptor()
    private val store = ModelStore(root) { Long.MAX_VALUE }
    @After fun clean() { root.deleteRecursively() }

    @Test fun exactManifestRejectsUnknownIdentityAndArbitraryAssetPaths() {
        val catalog = ModelCatalog(listOf(descriptor))
        assertEquals(listOf(descriptor), catalog.parseManifest(manifest(descriptor).toString()))
        val unknown = manifest(descriptor)
        unknown.getJSONArray("models").getJSONObject(0).put("modelId", "unregistered")
        rejected { catalog.parseManifest(unknown.toString()) }
        val path = manifest(descriptor)
        path.getJSONArray("models").getJSONObject(0).getJSONArray("files").getJSONObject(0).put("assetPath", "../private.txt")
        rejected { catalog.parseManifest(path.toString()) }
        val missing = manifest(descriptor)
        missing.getJSONArray("models").getJSONObject(0).getJSONArray("files").remove(1)
        rejected { catalog.parseManifest(missing.toString()) }
    }
    @Test fun installVerifiesBothFilesAndActiveLeasePreventsReplacementOrDeletion() {
        install(store, descriptor)
        assertTrue(store.present(descriptor))
        store.acquire(descriptor, WorkToken()).use { lease ->
            assertArrayEquals(tinyBytes.getValue("talker"), lease.files.getValue("talker").readBytes())
            rejected { store.delete(descriptor) }
            rejected { install(store, descriptor) }
        }
        store.delete(descriptor); assertFalse(store.present(descriptor))
    }
    @Test fun wrongHashOrMissingFileCannotReplacePreviousValidPair() {
        install(store, descriptor)
        rejected { store.install(descriptor, WorkToken(), { ByteArray(it.sizeBytes.toInt()) { 42 }.inputStream() }) { _, _ -> } }
        assertTrue(store.present(descriptor))
        store.acquire(descriptor, WorkToken(), true).close()
        rejected { store.install(descriptor, WorkToken(), { error("Synthetic missing source") }) { _, _ -> } }
        assertTrue(store.present(descriptor))
    }
    @Test fun cancelledInstallAndInsufficientSpaceNeverPublishReady() {
        val token = WorkToken()
        rejected { store.install(descriptor, token, { tinyBytes.getValue(it.role).inputStream() }) { _, _ -> token.cancel() } }
        assertFalse(store.present(descriptor))
        assertFalse(File(root, descriptor.modelId).listFiles().orEmpty().any { it.name.startsWith(".pending-") })
        val insufficient = ModelStore(root) { 0 }
        rejected { install(insufficient, descriptor) }
        assertFalse(store.present(descriptor))
    }
    @Test fun corruptionAndRestartForceHashVerification() {
        install(store, descriptor)
        val file = store.acquire(descriptor, WorkToken()).use { it.files.getValue("tokenizer") }
        file.writeBytes(ByteArray(file.length().toInt()))
        rejected { ModelStore(root).acquire(descriptor, WorkToken()) }
        rejected { store.acquire(descriptor, WorkToken(), true) }
    }
    @Test fun stagedRemnantsAreCleanedOnlyWithinOwnedModelDirectory() {
        val home = File(root, descriptor.modelId).also { it.mkdirs() }
        File(home, ".pending-orphan").also { it.mkdir(); File(it, "partial").writeText("small partial") }
        val unrelated = File(root, "not-a-model-record").also { it.writeText("keep") }
        install(store, descriptor)
        assertFalse(File(home, ".pending-orphan").exists()); assertTrue(unrelated.isFile)
    }
    @Test fun aNewControllerCannotDeleteOrReplaceAnOlderControllersLease() {
        install(store, descriptor)
        store.acquire(descriptor, WorkToken()).use {
            val reopened = ModelStore(root) { Long.MAX_VALUE }
            rejected { reopened.delete(descriptor) }
            rejected { install(reopened, descriptor) }
        }
        ModelStore(root).delete(descriptor)
        assertFalse(store.present(descriptor))
    }
}
