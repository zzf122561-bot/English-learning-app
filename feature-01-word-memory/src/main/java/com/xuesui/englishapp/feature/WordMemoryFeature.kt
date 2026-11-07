package com.xuesui.englishapp.feature

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xuesui.englishapp.WordMemoryViewModel
import com.xuesui.englishapp.data.NotebookRepository
import com.xuesui.englishapp.data.WordMemoryDatabase
import com.xuesui.englishapp.ui.WordMemoryApp

@Composable
fun WordMemoryFeature(
    onLookupRequested: (String) -> Unit = {},
    onReadingRequested: (title: String, englishParagraphs: List<String>, paragraphIndex: Int?, sentenceText: String?) -> Unit = { _, _, _, _ -> },
    readingContent: (@Composable (
        documentId: String,
        title: String,
        englishParagraphs: List<String>,
        paragraphIndex: Int?,
        sentenceText: String?,
    ) -> Unit)? = null,
    playbackControl: (@Composable (
        documentId: String,
        title: String,
        englishParagraphs: List<String>,
        paragraphIndex: Int?,
    ) -> Unit)? = null,
) {
    val context = LocalContext.current.applicationContext
    val repository = NotebookRepository(WordMemoryDatabase.getInstance(context))
    val model: WordMemoryViewModel = viewModel(factory = WordMemoryViewModel.Factory(repository))
    WordMemoryApp(model, onLookupRequested, onReadingRequested, readingContent, playbackControl)
}
