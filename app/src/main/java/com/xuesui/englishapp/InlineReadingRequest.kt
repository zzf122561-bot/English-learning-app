package com.xuesui.englishapp

import com.xuesui.englishapp.modelprobe.runtime.SpeechReadingRequest
import com.xuesui.englishapp.modelprobe.runtime.SpeechReadingScope
import java.nio.ByteBuffer
import java.security.MessageDigest

/** Stable across recomposition/rotation. Only a digest, never article text, enters the request ID.
 * Audio retention identity remains the module's document + logical range, not this content ID. */
internal fun createInlineReadingRequest(
    documentId: String,
    title: String,
    paragraphs: List<String>,
    paragraphIndex: Int?,
    sentenceText: String?,
): SpeechReadingRequest? {
    if (documentId.isBlank() || documentId.length > 1024 || '\u0000' in documentId) return null
    val route = createReadingRoute("inline", title, paragraphs, paragraphIndex, sentenceText) ?: return null
    val digest = MessageDigest.getInstance("SHA-256")
    // Length framing distinguishes paragraph boundaries and literal delimiters in user text.
    fun add(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        digest.update(ByteBuffer.allocate(4).putInt(bytes.size).array())
        digest.update(bytes)
    }
    add("inline-reading-v1")
    add(documentId)
    add(route.scope.name)
    add(paragraphIndex?.toString().orEmpty())
    add(sentenceText.orEmpty())
    add(route.englishParagraphs.size.toString())
    route.englishParagraphs.forEach(::add)
    val id = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    return SpeechReadingRequest(
        requestId = "inline-$id",
        title = title,
        englishParagraphs = route.englishParagraphs,
        scope = when (route.scope) {
            ReadingScope.FULL -> SpeechReadingScope.FULL
            ReadingScope.PARAGRAPH -> SpeechReadingScope.PARAGRAPH
            ReadingScope.SENTENCE -> SpeechReadingScope.SENTENCE
        },
        paragraphIndex = paragraphIndex,
        sentenceText = sentenceText,
    )
}

/** Direct playback has no selected-sentence state and cannot silently widen a bad index. */
internal fun createPlaybackReadingRequest(
    documentId: String,
    title: String,
    paragraphs: List<String>,
    paragraphIndex: Int?,
): SpeechReadingRequest? = createInlineReadingRequest(
    documentId, title, paragraphs, paragraphIndex, sentenceText = null,
)
