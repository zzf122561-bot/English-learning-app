package com.xuesui.englishapp.dictionary

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xuesui.englishapp.dictionary.audio.AndroidSpeechSynthesizer
import com.xuesui.englishapp.dictionary.audio.AudioPayload
import com.xuesui.englishapp.dictionary.audio.EmbeddedAudioProvider
import com.xuesui.englishapp.dictionary.audio.FileMediaPlayer
import com.xuesui.englishapp.dictionary.audio.PronunciationCoordinator
import com.xuesui.englishapp.dictionary.audio.PronunciationState
import com.xuesui.englishapp.dictionary.audio.StrictHttpsAudioPlayer
import com.xuesui.englishapp.dictionary.data.DictionaryDatabase
import com.xuesui.englishapp.dictionary.data.DictionaryRepository
import com.xuesui.englishapp.dictionary.data.DictionaryStatus
import com.xuesui.englishapp.dictionary.data.DictionaryFontScale
import com.xuesui.englishapp.dictionary.data.DictionaryWithResources
import com.xuesui.englishapp.dictionary.engine.MdictResource
import com.xuesui.englishapp.dictionary.files.BuiltinDictionaryInstaller
import com.xuesui.englishapp.dictionary.files.SafDictionaryImporter
import com.xuesui.englishapp.dictionary.query.DictionaryQueryCoordinator
import com.xuesui.englishapp.dictionary.query.DictionaryQuerySnapshot
import com.xuesui.englishapp.dictionary.web.DictionaryAudioAction
import com.xuesui.englishapp.dictionary.web.DictionaryAudioSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class DictionaryUiState(
    val input: String = "",
    val dictionaries: List<DictionaryWithResources> = emptyList(),
    val query: DictionaryQuerySnapshot = DictionaryQuerySnapshot("", "", emptyList(), null, emptyList()),
    val managing: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val pronunciation: PronunciationState = PronunciationState.Idle,
)

/** Coordinates public initialQuery updates with the one-time built-in installation gate. */
internal class InitialQueryGate(initialQuery: String?) {
    private var ready = false
    private var pending = initialQuery.nonBlankOrNull()
    private var lastSubmitted: String? = null

    fun offer(value: String?, currentQuery: String): String? {
        val query = value.nonBlankOrNull() ?: return null
        if (!ready) {
            pending = query
            return null
        }
        return deliver(query, currentQuery)
    }

    fun markReady(currentQuery: String): String? {
        ready = true
        val query = pending ?: return null
        pending = null
        return deliver(query, currentQuery)
    }

    private fun deliver(query: String, currentQuery: String): String? {
        if (query == lastSubmitted && query == currentQuery) return null
        lastSubmitted = query
        return query
    }

    private fun String?.nonBlankOrNull(): String? = this?.takeIf(String::isNotBlank)
}

internal class DictionaryViewModel(
    context: Context,
    initialQuery: String?,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val appContext = context.applicationContext
    private val database = DictionaryDatabase.get(appContext)
    private val repository = DictionaryRepository(appContext, database)
    private val installer = BuiltinDictionaryInstaller(appContext, repository)
    private val importer = SafDictionaryImporter(appContext, repository)
    private val queryCoordinator = DictionaryQueryCoordinator(repository::enabledDictionaries)
    private val filePlayer = FileMediaPlayer(appContext)
    private val pronunciation = PronunciationCoordinator(
        embeddedProvider = EmbeddedAudioProvider(::findEmbeddedAudio),
        embeddedPlayer = filePlayer,
        httpsPlayer = StrictHttpsAudioPlayer(appContext, filePlayer),
        speechSynthesizer = AndroidSpeechSynthesizer(appContext),
    )

    private val mutableState = MutableStateFlow(DictionaryUiState(input = initialQuery.orEmpty()))
    val state: StateFlow<DictionaryUiState> = mutableState.asStateFlow()
    private val initialQueryGate = InitialQueryGate(initialQuery)
    private var queryJob: Job? = null
    private var pronunciationJob: Job? = null

    init {
        viewModelScope.launch {
            pronunciation.state.collectLatest { audioState ->
                mutableState.value = mutableState.value.copy(pronunciation = audioState)
            }
        }
        viewModelScope.launch {
            repository.dictionaries.collectLatest { dictionaries ->
                mutableState.value = mutableState.value.copy(dictionaries = dictionaries)
            }
        }
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(busy = true)
            try {
                val summary = withContext(ioDispatcher) { installer.installAll() }
                val message = summary.errors.takeIf(List<String>::isNotEmpty)?.joinToString("；")
                mutableState.value = mutableState.value.copy(busy = false, message = message)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                mutableState.value = mutableState.value.copy(busy = false, message = error.message ?: "内置词典安装失败")
            }
            initialQueryGate.markReady(mutableState.value.query.displayQuery)?.let(::submitQuery)
        }
    }

    fun enterPresentation(presentation: DictionaryPresentation, value: String?) {
        if (presentation == DictionaryPresentation.QUICK_LOOKUP && mutableState.value.managing) {
            mutableState.value = mutableState.value.copy(managing = false)
        }
        initialQueryGate.offer(value, mutableState.value.query.displayQuery)?.let(::submitQuery)
    }

    fun setInput(value: String) {
        mutableState.value = mutableState.value.copy(input = value)
    }

    fun submitQuery(value: String = mutableState.value.input) {
        mutableState.value = mutableState.value.copy(input = value)
        queryJob?.cancel()
        queryJob = viewModelScope.launch {
            try {
                val snapshot = withContext(ioDispatcher) { queryCoordinator.query(value) }
                snapshot.tabs.filter { it.error != null }.forEach { tab ->
                    withContext(ioDispatcher) {
                        repository.setRuntimeStatus(tab.dictionaryId, DictionaryStatus.ERROR)
                    }
                }
                mutableState.value = mutableState.value.copy(query = snapshot, message = null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                mutableState.value = mutableState.value.copy(message = error.message ?: "查询失败")
            }
        }
    }

    fun selectDictionary(id: String) {
        mutableState.value = mutableState.value.copy(query = queryCoordinator.selectDictionary(id))
    }

    fun openInternalLink(query: String) = submitQuery(query)

    fun readSelectedResource(path: String): MdictResource? = queryCoordinator.readSelectedResource(path)

    fun showManagement(show: Boolean) {
        mutableState.value = mutableState.value.copy(managing = show)
        if (!show && mutableState.value.input.isNotBlank()) submitQuery()
    }

    fun reportMessage(message: String) {
        mutableState.value = mutableState.value.copy(message = message)
    }

    fun reportResourceError(message: String) {
        viewModelScope.launch {
            val id = mutableState.value.query.selectedDictionaryId
            mutableState.value = mutableState.value.copy(message = message)
            if (id != null) withContext(ioDispatcher) {
                repository.setRuntimeStatus(id, DictionaryStatus.ERROR)
            }
        }
    }

    fun setEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) { repository.setEnabled(id, enabled) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                mutableState.value = mutableState.value.copy(message = error.message)
            }
        }
    }

    fun setFontLevel(id: String, level: Int) {
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) { repository.setFontLevel(id, DictionaryFontScale.normalize(level)) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                mutableState.value = mutableState.value.copy(message = error.message ?: "正文字号保存失败")
            }
        }
    }

    fun moveDictionary(from: Int, to: Int) {
        val ids = mutableState.value.dictionaries.map { it.dictionary.id }.toMutableList()
        if (from !in ids.indices || to !in ids.indices || from == to) return
        val moved = ids.removeAt(from)
        ids.add(to, moved)
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) { repository.reorder(ids) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                mutableState.value = mutableState.value.copy(message = error.message)
            }
        }
    }

    fun importDirectory(uri: Uri) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(busy = true, message = null)
            try {
                val items = withContext(ioDispatcher) { importer.importTree(uri) }
                mutableState.value = mutableState.value.copy(
                    busy = false,
                    message = if (items.isEmpty()) "所选文件夹中没有 MDX" else items.joinToString("；") { it.message },
                )
            } catch (cancelled: CancellationException) {
                mutableState.value = mutableState.value.copy(busy = false)
                throw cancelled
            } catch (error: Throwable) {
                mutableState.value = mutableState.value.copy(busy = false, message = error.message ?: "导入失败")
            }
        }
    }

    fun deleteImported(id: String) {
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) { repository.deleteImported(id) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                mutableState.value = mutableState.value.copy(message = error.message)
            }
        }
    }

    fun pronounce() {
        startPronunciation(null)
    }

    fun pronounce(action: DictionaryAudioAction) {
        startPronunciation(action)
    }

    private fun startPronunciation(action: DictionaryAudioAction?) {
        val query = mutableState.value.query.displayQuery.takeIf(String::isNotBlank) ?: return
        val result = mutableState.value.query.selectedResult ?: return
        val embeddedPaths = prioritizeCandidate(
            result.embeddedAudioPaths,
            action?.takeIf { it.source == DictionaryAudioSource.MDD }?.value,
        )
        val httpsUrls = prioritizeCandidate(
            result.httpsAudioUrls,
            action?.takeIf { it.source == DictionaryAudioSource.HTTPS }?.value,
        )
        pronunciationJob?.cancel()
        pronunciation.stopActive()
        pronunciationJob = viewModelScope.launch {
            try {
                val source = withContext(ioDispatcher) {
                    pronunciation.pronounce(query, embeddedPaths, httpsUrls)
                }
                if (source == null) {
                    mutableState.value = mutableState.value.copy(message = "没有可用发音，已尝试词典资源、HTTPS 和系统语音")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                mutableState.value = mutableState.value.copy(message = error.message ?: "发音失败")
            }
        }
    }

    override fun onCleared() {
        queryJob?.cancel()
        pronunciationJob?.cancel()
        pronunciation.close()
        // Closing can wait for an in-flight random-access read; never make ViewModel clearing block Main.
        CoroutineScope(ioDispatcher).launch { queryCoordinator.close() }
    }

    private suspend fun findEmbeddedAudio(resourcePaths: List<String>): AudioPayload? =
        withContext(ioDispatcher) {
            resourcePaths.forEach { path ->
                queryCoordinator.readSelectedResource(path)?.let {
                    if (it.bytes.size.toLong() <= com.xuesui.englishapp.dictionary.audio.MAX_AUDIO_BYTES) {
                        return@withContext AudioPayload(it.bytes, it.mediaType)
                    }
                }
            }
            null
        }

    companion object {
        internal fun <T> prioritizeCandidate(candidates: List<T>, preferred: T?): List<T> =
            (listOfNotNull(preferred) + candidates).distinct()

        fun factory(context: Context, initialQuery: String?) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                DictionaryViewModel(context, initialQuery) as T
        }
    }
}
