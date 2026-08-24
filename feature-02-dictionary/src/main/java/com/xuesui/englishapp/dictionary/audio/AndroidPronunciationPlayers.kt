package com.xuesui.englishapp.dictionary.audio

import android.content.Context
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.xuesui.englishapp.dictionary.files.FileIntegrity
import com.xuesui.englishapp.dictionary.files.FileSizeLimitExceededException
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.UUID
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

internal class FileMediaPlayer(private val context: Context) : EmbeddedAudioPlayer {
    private var active: MediaPlayer? = null

    override suspend fun play(payload: AudioPayload): Boolean {
        if (!isAudioPayloadWithinLimit(payload.bytes.size.toLong())) return false
        val suffix = when (payload.mediaType) {
            "audio/mpeg" -> ".mp3"
            "audio/ogg" -> ".ogg"
            else -> ".wav"
        }
        val file = File.createTempFile("dictionary-audio-", suffix, context.cacheDir)
        return try {
            file.writeBytes(payload.bytes)
            playFile(file)
        } finally {
            file.delete()
        }
    }

    suspend fun playFile(file: File): Boolean = suspendCancellableCoroutine { continuation ->
        active?.release()
        val player = MediaPlayer()
        active = player
        var resumed = false
        fun finish(value: Boolean) {
            if (!resumed && continuation.isActive) {
                resumed = true
                continuation.resume(value)
            }
        }
        player.setOnPreparedListener {
            it.start()
            finish(true)
        }
        player.setOnErrorListener { _, _, _ ->
            finish(false)
            true
        }
        continuation.invokeOnCancellation {
            player.stopSafely()
            player.release()
        }
        runCatching {
            player.setDataSource(file.absolutePath)
            player.prepareAsync()
        }.onFailure { finish(false) }
    }

    override fun close() {
        stop()
    }

    override fun stop() {
        active?.stopSafely()
        active?.release()
        active = null
    }

    private fun MediaPlayer.stopSafely() {
        runCatching { if (isPlaying) stop() }
    }
}

internal fun isAudioPayloadWithinLimit(size: Long): Boolean = size in 0..MAX_AUDIO_BYTES

internal class StrictHttpsAudioPlayer(
    private val context: Context,
    private val player: FileMediaPlayer,
) : HttpsAudioPlayer {
    override suspend fun play(url: String): Boolean {
        if (!PronunciationCoordinator.isStrictHttpsUrl(url)) return false
        val file = withContext(Dispatchers.IO) { download(url) } ?: return false
        return try {
            player.playFile(file)
        } finally {
            file.delete()
        }
    }

    private fun download(value: String): File? {
        val connection = (URL(value).openConnection() as? HttpsURLConnection) ?: return null
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 8_000
        connection.readTimeout = 12_000
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "audio/*")
        try {
            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) return null
            val length = connection.contentLengthLong
            if (length > MAX_AUDIO_BYTES) return null
            val contentType = connection.contentType.orEmpty().substringBefore(';')
            if (!contentType.startsWith("audio/", ignoreCase = true)) return null
            val target = File.createTempFile("dictionary-https-audio-", ".bin", context.cacheDir)
            return try {
                BoundedAudioCache.write(connection.inputStream, target)
                target
            } catch (_: FileSizeLimitExceededException) {
                target.delete()
                null
            } catch (error: Throwable) {
                target.delete()
                throw error
            }
        } finally {
            connection.disconnect()
        }
    }

    override fun close() = Unit

}

internal object BoundedAudioCache {
    fun write(input: InputStream, target: File) {
        try {
            FileIntegrity.copyAndHash(input, target, maxBytes = MAX_AUDIO_BYTES)
        } catch (error: Throwable) {
            target.delete()
            throw error
        }
    }
}

internal class UtteranceCompletionRegistry {
    private var activeId: String? = null
    private var active: CompletableDeferred<Boolean>? = null

    @Synchronized
    fun begin(id: String): CompletableDeferred<Boolean> {
        active?.cancel()
        return CompletableDeferred<Boolean>().also {
            activeId = id
            active = it
        }
    }

    @Synchronized
    fun complete(id: String?, result: Boolean) {
        if (id != null && id == activeId) {
            active?.complete(result)
            active = null
            activeId = null
        }
    }

    @Synchronized
    fun cancel() {
        active?.cancel()
        active = null
        activeId = null
    }
}

internal class AndroidSpeechSynthesizer(context: Context) : SpeechSynthesizer {
    private val initialized = CompletableDeferred<Boolean>()
    private val completions = UtteranceCompletionRegistry()
    private val tts: TextToSpeech

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val language = tts.setLanguage(Locale.US)
                initialized.complete(
                    language != TextToSpeech.LANG_MISSING_DATA &&
                        language != TextToSpeech.LANG_NOT_SUPPORTED,
                )
            } else {
                initialized.complete(false)
            }
        }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) { completions.complete(utteranceId, true) }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { completions.complete(utteranceId, false) }
        })
    }

    override suspend fun speak(word: String): Boolean {
        if (!initialized.await()) return false
        val utteranceId = UUID.randomUUID().toString()
        val completion = completions.begin(utteranceId)
        val result = tts.speak(word, TextToSpeech.QUEUE_FLUSH, Bundle(), utteranceId)
        if (result == TextToSpeech.ERROR) {
            completions.complete(utteranceId, false)
            return false
        }
        return completion.await()
    }

    override fun close() {
        stop()
        tts.shutdown()
    }

    override fun stop() {
        completions.cancel()
        tts.stop()
    }
}
