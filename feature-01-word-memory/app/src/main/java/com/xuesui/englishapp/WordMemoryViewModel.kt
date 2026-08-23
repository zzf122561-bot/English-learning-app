package com.xuesui.englishapp

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xuesui.englishapp.data.NotebookEntity
import com.xuesui.englishapp.data.NotebookRepository
import com.xuesui.englishapp.data.StudySegmentWithTargets
import com.xuesui.englishapp.docx.DocxFormatException
import com.xuesui.englishapp.docx.DocxNotebookParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import java.io.FilterInputStream
import java.io.InputStream

sealed interface ImportState {
    data object Idle : ImportState
    data class Reading(val fileName: String, val percent: Int?) : ImportState
    data class Failed(val message: String) : ImportState
}

@OptIn(ExperimentalCoroutinesApi::class)
class WordMemoryViewModel(
    private val repository: NotebookRepository,
    private val parser: DocxNotebookParser = DocxNotebookParser(),
) : ViewModel() {
    val notebooks: Flow<List<NotebookEntity>> = repository.observeNotebooks()

    private val activeNotebookId = MutableStateFlow<Long?>(null)
    val activeNotebook: Flow<NotebookEntity?> = activeNotebookId.flatMapLatest { id ->
        if (id == null) flowOf(null) else repository.observeNotebook(id)
    }
    val activeSegments: Flow<List<StudySegmentWithTargets>> = activeNotebookId.flatMapLatest { id ->
        if (id == null) emptyFlow() else repository.observeSegments(id)
    }

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()
    private var importJob: Job? = null
    private val dictationJobs = mutableMapOf<Long, Job>()
    private var positionJob: Job? = null

    fun openNotebook(id: Long) { activeNotebookId.value = id }
    fun closeNotebook() { activeNotebookId.value = null }

    fun importDocx(contentResolver: ContentResolver, uri: Uri) {
        importJob?.cancel()
        importJob = viewModelScope.launch {
            val metadata = queryFileMetadata(contentResolver, uri)
            val fileName = metadata.name ?: "导入的学习文档.docx"
            _importState.value = ImportState.Reading(fileName, metadata.size?.let { 0 })
            try {
                val parsed = withContext(Dispatchers.IO) {
                    val parseJob = coroutineContext[Job]
                    contentResolver.openInputStream(uri)?.use { source ->
                        val input = ProgressInputStream(source, metadata.size) { percent ->
                            _importState.value = ImportState.Reading(fileName, percent)
                        }
                        parser.parse(input, fileName) { parseJob?.ensureActive() }
                    }
                        ?: error("无法打开所选文件。")
                }
                val notebookId = withContext(Dispatchers.IO) { repository.importNotebook(parsed) }
                _importState.value = ImportState.Idle
                openNotebook(notebookId)
            } catch (_: kotlinx.coroutines.CancellationException) {
                _importState.value = ImportState.Idle
            } catch (error: DocxFormatException) {
                _importState.value = ImportState.Failed(error.problems.joinToString("\n"))
            } catch (error: Exception) {
                _importState.value = ImportState.Failed(error.message ?: "导入失败，请检查文件是否为有效的 DOCX。")
            }
        }
    }

    fun cancelImport() { importJob?.cancel() }
    fun clearImportError() { _importState.value = ImportState.Idle }
    fun renameNotebook(id: Long, title: String) {
        if (title.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) { repository.renameNotebook(id, title) }
    }
    fun deleteNotebook(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { repository.deleteNotebook(id) }
    }
    fun setChineseVisible(segmentId: Long, visible: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { repository.setChineseVisible(segmentId, visible) }
    }
    fun updateDictation(segmentId: Long, text: String) {
        dictationJobs.remove(segmentId)?.cancel()
        dictationJobs[segmentId] = viewModelScope.launch(Dispatchers.IO) {
            delay(350)
            repository.setDictationText(segmentId, text)
        }
    }
    fun savePosition(position: Int) {
        val notebookId = activeNotebookId.value ?: return
        positionJob?.cancel()
        positionJob = viewModelScope.launch(Dispatchers.IO) {
            delay(400)
            repository.setLastPosition(notebookId, position)
        }
    }

    private suspend fun queryFileMetadata(resolver: ContentResolver, uri: Uri): DocumentMetadata = withContext(Dispatchers.IO) {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@withContext DocumentMetadata(null, null)
            val name = cursor.getString(0)
            val size = cursor.takeUnless { it.isNull(1) }?.getLong(1)?.takeIf { it > 0 }
            DocumentMetadata(name, size)
        } ?: DocumentMetadata(null, null)
    }

    private data class DocumentMetadata(val name: String?, val size: Long?)

    private class ProgressInputStream(
        input: InputStream,
        private val totalBytes: Long?,
        private val onProgress: (Int) -> Unit,
    ) : FilterInputStream(input) {
        private var bytesRead = 0L
        private var lastPercent = -1

        override fun read(): Int = super.read().also { if (it >= 0) report(1) }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            super.read(buffer, offset, length).also { if (it > 0) report(it.toLong()) }

        private fun report(delta: Long) {
            bytesRead += delta
            val total = totalBytes ?: return
            val percent = ((bytesRead * 100L) / total).coerceIn(0L, 100L).toInt()
            if (percent != lastPercent) {
                lastPercent = percent
                onProgress(percent)
            }
        }
    }

    class Factory(private val repository: NotebookRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            WordMemoryViewModel(repository) as T
    }
}
