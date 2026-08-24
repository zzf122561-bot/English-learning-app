package com.xuesui.englishapp.dictionary.query

import com.xuesui.englishapp.dictionary.data.DictionaryWithResources
import com.xuesui.englishapp.dictionary.engine.MdictEngine
import com.xuesui.englishapp.dictionary.engine.MdictResource
import com.xuesui.englishapp.dictionary.engine.MdictSource
import com.xuesui.englishapp.dictionary.engine.PureKotlinMdictEngine
import com.xuesui.englishapp.dictionary.web.DictionaryHtmlRewriter
import java.io.Closeable
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class DictionaryResultTab(
    val dictionaryId: String,
    val displayName: String,
    val html: String?,
    val error: String?,
    val embeddedAudioPaths: List<String> = emptyList(),
    val httpsAudioUrls: List<String> = emptyList(),
)

internal data class DictionaryQuerySnapshot(
    val displayQuery: String,
    val normalizedQuery: String,
    val tabs: List<DictionaryResultTab>,
    val selectedDictionaryId: String?,
    val suggestions: List<String>,
) {
    val selectedResult: DictionaryResultTab?
        get() = tabs.firstOrNull { it.dictionaryId == selectedDictionaryId }
}

internal class DictionaryQueryCoordinator(
    private val enabledProvider: suspend () -> List<DictionaryWithResources>,
    private val engineOpener: (DictionaryWithResources) -> MdictEngine = { dictionary ->
        PureKotlinMdictEngine.open(
            MdictSource(
                mdxFile = File(dictionary.dictionary.privateMdxPath),
                mddFiles = dictionary.resources.sortedBy { it.partOrder }.map { File(it.privateMddPath) },
            ),
        )
    },
) : Closeable {
    private val engineLock = Any()
    private val snapshotLock = Any()
    private val engines = linkedMapOf<String, MdictEngine>()
    private val openErrors = linkedMapOf<String, String>()
    private val engineSignatures = linkedMapOf<String, String>()
    @Volatile var snapshot = DictionaryQuerySnapshot("", "", emptyList(), null, emptyList())
        private set

    suspend fun query(rawQuery: String): DictionaryQuerySnapshot {
        val displayQuery = rawQuery.trim()
        val normalized = displayQuery.lowercase(Locale.ROOT)
        val dictionaries = enabledProvider()
        val coroutineContext = currentCoroutineContext()
        return synchronized(engineLock) {
            coroutineContext.ensureActive()
            synchronizeEngines(dictionaries, coroutineContext)
            if (displayQuery.isEmpty()) {
                return@synchronized DictionaryQuerySnapshot(displayQuery, normalized, emptyList(), null, emptyList())
                    .also(::publishSnapshot)
            }

            val suggestions = linkedMapOf<String, String>()
            val tabs = dictionaries.map { dictionary ->
                coroutineContext.ensureActive()
                val id = dictionary.dictionary.id
                val engine = engines[id]
                if (engine == null) {
                    DictionaryResultTab(
                        id,
                        dictionary.dictionary.displayName,
                        null,
                        openErrors[id] ?: "词典无法打开",
                    )
                } else {
                    try {
                        val entry = engine.exactLookup(displayQuery)
                        if (entry == null) {
                            engine.prefixLookup(displayQuery, 24).forEach { suggestion ->
                                suggestions.putIfAbsent(suggestion.value.lowercase(Locale.ROOT), suggestion.value)
                            }
                        }
                        val rewritten = entry?.let { DictionaryHtmlRewriter.rewrite(it.html) }
                        DictionaryResultTab(
                            id,
                            dictionary.dictionary.displayName,
                            rewritten?.html,
                            null,
                            rewritten?.embeddedAudioPaths.orEmpty(),
                            rewritten?.httpsAudioUrls.orEmpty(),
                        )
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Throwable) {
                        DictionaryResultTab(
                            id,
                            dictionary.dictionary.displayName,
                            null,
                            error.message ?: "解析失败",
                        )
                    }
                }
            }
            val selected = tabs.firstOrNull { it.html != null }?.dictionaryId
                ?: tabs.firstOrNull()?.dictionaryId
            DictionaryQuerySnapshot(
                displayQuery = displayQuery,
                normalizedQuery = normalized,
                tabs = tabs,
                selectedDictionaryId = selected,
                suggestions = if (tabs.any { it.html != null }) emptyList() else suggestions.values.toList(),
            ).also(::publishSnapshot)
        }
    }

    fun selectDictionary(dictionaryId: String): DictionaryQuerySnapshot = synchronized(snapshotLock) {
        require(snapshot.tabs.any { it.dictionaryId == dictionaryId }) { "Unknown dictionary tab" }
        snapshot.copy(selectedDictionaryId = dictionaryId).also { snapshot = it }
    }

    /** Internal links replace the current query; no module-local back stack is created. */
    suspend fun openInternalLink(query: String): DictionaryQuerySnapshot = query(query)

    fun readSelectedResource(path: String): MdictResource? {
        val id = synchronized(snapshotLock) { snapshot.selectedDictionaryId } ?: return null
        return synchronized(engineLock) { engines[id]?.readResource(path) }
    }

    override fun close() = synchronized(engineLock) {
        engines.values.forEach { runCatching { it.close() } }
        engines.clear()
        openErrors.clear()
        engineSignatures.clear()
    }

    private fun synchronizeEngines(
        dictionaries: List<DictionaryWithResources>,
        coroutineContext: kotlin.coroutines.CoroutineContext,
    ) {
        val enabledIds = dictionaries.mapTo(linkedSetOf()) { it.dictionary.id }
        engines.keys.filterNot(enabledIds::contains).toList().forEach { id ->
            runCatching { engines.remove(id)?.close() }
            openErrors.remove(id)
            engineSignatures.remove(id)
        }
        dictionaries.forEach { dictionary ->
            coroutineContext.ensureActive()
            val id = dictionary.dictionary.id
            val signature = buildString {
                append(dictionary.dictionary.mdxSha256)
                dictionary.resources.sortedBy { it.partOrder }.forEach { append(':').append(it.mddSha256) }
            }
            if (engineSignatures[id] != null && engineSignatures[id] != signature) {
                runCatching { engines.remove(id)?.close() }
                openErrors.remove(id)
                engineSignatures.remove(id)
            }
            if (engines[id] == null && openErrors[id] == null) {
                try {
                    engines[id] = engineOpener(dictionary)
                    engineSignatures[id] = signature
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    openErrors[id] = error.message ?: "词典无法打开"
                    engineSignatures[id] = signature
                }
            }
        }
    }

    private fun publishSnapshot(value: DictionaryQuerySnapshot) {
        synchronized(snapshotLock) { snapshot = value }
    }
}
