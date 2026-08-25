package com.xuesui.englishapp.dictionary.query

import com.xuesui.englishapp.dictionary.data.DictionaryEntity
import com.xuesui.englishapp.dictionary.data.DictionarySourceType
import com.xuesui.englishapp.dictionary.data.DictionaryStatus
import com.xuesui.englishapp.dictionary.data.DictionaryWithResources
import com.xuesui.englishapp.dictionary.engine.MdictEngine
import com.xuesui.englishapp.dictionary.engine.MdictEntry
import com.xuesui.englishapp.dictionary.engine.MdictHeadword
import com.xuesui.englishapp.dictionary.engine.MdictMetadata
import com.xuesui.englishapp.dictionary.engine.MdictResource
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DictionaryQueryCoordinatorTest {
    @Test
    fun usesLocaleRootAndPreservesDictionaryOrderWithSingleSelectedHtml() = runBlocking {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("tr-TR"))
        try {
            val dictionaries = listOf(dictionary("second", 0), dictionary("first", 1))
            val engines = mapOf(
                "second" to FakeEngine(entries = mapOf("INDEX" to "second html")),
                "first" to FakeEngine(entries = mapOf("INDEX" to "first html")),
            )
            val coordinator = DictionaryQueryCoordinator(
                enabledProvider = { dictionaries },
                engineOpener = { engines.getValue(it.dictionary.id) },
            )

            val result = coordinator.query("INDEX")

            assertEquals("index", result.normalizedQuery)
            assertEquals(listOf("second", "first"), result.tabs.map { it.dictionaryId })
            assertEquals("second", result.selectedDictionaryId)
            assertEquals(2, result.tabs.count { it.html != null })
            assertEquals("second html", result.selectedResult?.html)
            coordinator.close()
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun deduplicatesPrefixSuggestionsAcrossDictionaries() = runBlocking {
        val coordinator = DictionaryQueryCoordinator(
            enabledProvider = { listOf(dictionary("one", 0), dictionary("two", 1)) },
            engineOpener = {
                if (it.dictionary.id == "one") FakeEngine(suggestions = listOf("Apple", "apply"))
                else FakeEngine(suggestions = listOf("APPLE", "appetite"))
            },
        )

        val result = coordinator.query("app")

        assertEquals(listOf("Apple", "apply", "appetite"), result.suggestions)
        assertNull(result.selectedResult?.html)
        coordinator.close()
    }

    @Test
    fun internalLinkReplacesCurrentQueryWithoutBackStack() = runBlocking {
        val engine = FakeEngine(entries = mapOf("one" to "1", "two" to "2"))
        val coordinator = DictionaryQueryCoordinator(
            enabledProvider = { listOf(dictionary("only", 0)) },
            engineOpener = { engine },
        )
        coordinator.query("one")

        val replaced = coordinator.openInternalLink("two")

        assertEquals("two", replaced.displayQuery)
        assertEquals("2", replaced.selectedResult?.html)
        assertEquals(replaced, coordinator.snapshot)
        coordinator.close()
    }

    @Test
    fun parserFailureIsIsolatedToOneDictionary() = runBlocking {
        val coordinator = DictionaryQueryCoordinator(
            enabledProvider = { listOf(dictionary("bad", 0), dictionary("good", 1)) },
            engineOpener = {
                if (it.dictionary.id == "bad") error("broken dictionary")
                FakeEngine(entries = mapOf("word" to "definition"))
            },
        )

        val result = coordinator.query("word")

        assertTrue(result.tabs.first().error!!.contains("broken"))
        assertEquals("definition", result.tabs.last().html)
        coordinator.close()
    }

    @Test
    fun eachResultTabCarriesOnlyItsDictionaryFontLevel() = runBlocking {
        val dictionaries = listOf(
            dictionary("small", 0).copy(dictionary = dictionary("small", 0).dictionary.copy(fontLevel = 2)),
            dictionary("large", 1).copy(dictionary = dictionary("large", 1).dictionary.copy(fontLevel = 9)),
        )
        val coordinator = DictionaryQueryCoordinator(
            enabledProvider = { dictionaries },
            engineOpener = { FakeEngine(entries = mapOf("word" to "definition")) },
        )

        val result = coordinator.query("word")

        assertEquals(listOf(2, 9), result.tabs.map { it.fontLevel })
        assertEquals(2, result.selectedResult?.fontLevel)
        assertEquals(9, coordinator.selectDictionary("large").selectedResult?.fontLevel)
        coordinator.close()
    }

    @Test
    fun queryResourceReadAndCloseShareOneEngineSynchronizationBoundary() = runBlocking {
        val engine = BlockingEngine()
        val coordinator = DictionaryQueryCoordinator(
            enabledProvider = { listOf(dictionary("only", 0)) },
            engineOpener = { engine },
        )
        coordinator.query("initial")
        engine.blockNextLookup.set(true)

        val query = async(Dispatchers.Default) { coordinator.query("next") }
        assertTrue(engine.lookupStarted.await(5, TimeUnit.SECONDS))
        val resource = async(Dispatchers.Default) { coordinator.readSelectedResource("/image.png") }
        delay(50)
        assertTrue("resource read must wait for active query", !resource.isCompleted)
        engine.allowLookup.countDown()

        query.await()
        assertEquals("image/png", resource.await()?.mediaType)
        assertTrue("engine calls overlapped", !engine.concurrentCallDetected.get())
        coordinator.close()
    }

    private fun dictionary(id: String, order: Int) = DictionaryWithResources(
        dictionary = DictionaryEntity(
            id = id,
            displayName = id,
            sourceType = DictionarySourceType.IMPORTED,
            privateMdxPath = "$id.mdx",
            mdxSha256 = id.padEnd(64, '0'),
            enabled = true,
            displayOrder = order,
            formatVersion = "2.0",
            runtimeStatus = DictionaryStatus.READY,
            createdAt = 1,
            updatedAt = 1,
        ),
        resources = emptyList(),
    )

    private class FakeEngine(
        private val entries: Map<String, String> = emptyMap(),
        private val suggestions: List<String> = emptyList(),
    ) : MdictEngine {
        override val metadata = MdictMetadata("fake", null, "2.0", "UTF-8", true, entries.size.toLong())
        override fun exactLookup(query: String): MdictEntry? = entries.entries
            .firstOrNull { it.key.equals(query, ignoreCase = true) }
            ?.let { MdictEntry(it.key, it.value) }
        override fun prefixLookup(prefix: String, limit: Int): List<MdictHeadword> =
            suggestions.take(limit).mapIndexed { index, value -> MdictHeadword(value, index.toLong()) }
        override fun readResource(path: String): MdictResource? = null
        override fun close() = Unit
    }

    private class BlockingEngine : MdictEngine {
        override val metadata = MdictMetadata("blocking", null, "2.0", "UTF-8", false, 2)
        val blockNextLookup = AtomicBoolean(false)
        val lookupStarted = CountDownLatch(1)
        val allowLookup = CountDownLatch(1)
        val concurrentCallDetected = AtomicBoolean(false)
        private val inCall = AtomicBoolean(false)

        override fun exactLookup(query: String): MdictEntry {
            enter()
            try {
                if (blockNextLookup.compareAndSet(true, false)) {
                    lookupStarted.countDown()
                    check(allowLookup.await(5, TimeUnit.SECONDS))
                }
                return MdictEntry(query, "<p>$query</p>")
            } finally {
                inCall.set(false)
            }
        }

        override fun prefixLookup(prefix: String, limit: Int): List<MdictHeadword> = emptyList()

        override fun readResource(path: String): MdictResource {
            enter()
            return try {
                MdictResource(path, byteArrayOf(1), "image/png")
            } finally {
                inCall.set(false)
            }
        }

        override fun close() {
            enter()
            inCall.set(false)
        }

        private fun enter() {
            if (!inCall.compareAndSet(false, true)) concurrentCallDetected.set(true)
        }
    }
}
