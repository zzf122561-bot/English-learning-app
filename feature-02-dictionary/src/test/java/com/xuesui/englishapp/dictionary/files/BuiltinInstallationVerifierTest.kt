package com.xuesui.englishapp.dictionary.files

import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltinInstallationVerifierTest {
    @Test
    fun matchingHashesMakeExistingInstallIdempotent() {
        val root = Files.createTempDirectory("dictionary-builtin-").toFile()
        try {
            val mdx = root.resolve("example.mdx").apply { writeText("dictionary") }
            val mdd = root.resolve("example.mdd").apply { writeText("resource") }
            val spec = BuiltinDictionarySpec(
                id = "builtin.example",
                displayName = "Example",
                mdx = BuiltinAsset("example/example.mdx", FileIntegrity.sha256(mdx)),
                resources = listOf(BuiltinAsset("example/example.mdd", FileIntegrity.sha256(mdd))),
            )

            assertTrue(BuiltinInstallationVerifier.matches(spec, root))
            mdd.writeText("changed")
            assertFalse(BuiltinInstallationVerifier.matches(spec, root))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsTraversalAndInvalidManifestHash() {
        assertThrows(IllegalArgumentException::class.java) {
            BuiltinInstallationVerifier.validate(BuiltinAsset("../secret.mdx", "0".repeat(64)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            BuiltinInstallationVerifier.validate(BuiltinAsset("safe.mdx", "not-a-hash"))
        }
    }
}

