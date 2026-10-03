package com.xuesui.englishapp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import java.util.UUID

/** Rotation retains the request; process death safely returns to the persisted learning page. */
internal class ReadingNavigationModel : ViewModel() {
    var request by mutableStateOf<ReadingRoute?>(null)
        private set

    fun open(title: String, paragraphs: List<String>, index: Int?, sentence: String?) {
        createReadingRoute(UUID.randomUUID().toString(), title, paragraphs, index, sentence)?.let { request = it }
    }

    fun close(requestId: String) { request = closeReadingRoute(request, requestId) }
}
