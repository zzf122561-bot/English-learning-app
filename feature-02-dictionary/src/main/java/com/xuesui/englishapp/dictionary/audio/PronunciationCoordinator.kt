package com.xuesui.englishapp.dictionary.audio

import java.io.Closeable
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal const val MAX_AUDIO_BYTES = 8L * 1024 * 1024

internal enum class PronunciationSource { MDD, HTTPS, TTS }

internal sealed interface PronunciationState {
    data object Idle : PronunciationState
    data class Trying(val source: PronunciationSource) : PronunciationState
    data class Playing(val source: PronunciationSource) : PronunciationState
    data class Failed(val message: String) : PronunciationState
    data object Released : PronunciationState
}

internal data class AudioPayload(val bytes: ByteArray, val mediaType: String?)

internal fun interface EmbeddedAudioProvider {
    suspend fun find(resourcePaths: List<String>): AudioPayload?
}

internal interface EmbeddedAudioPlayer : Closeable {
    suspend fun play(payload: AudioPayload): Boolean
    fun stop() = Unit
}

internal interface HttpsAudioPlayer : Closeable {
    suspend fun play(url: String): Boolean
    fun stop() = Unit
}

internal interface SpeechSynthesizer : Closeable {
    suspend fun speak(word: String): Boolean
    fun stop() = Unit
}

internal class PronunciationCoordinator(
    private val embeddedProvider: EmbeddedAudioProvider,
    private val embeddedPlayer: EmbeddedAudioPlayer,
    private val httpsPlayer: HttpsAudioPlayer,
    private val speechSynthesizer: SpeechSynthesizer,
) : Closeable {
    private val mutableState = MutableStateFlow<PronunciationState>(PronunciationState.Idle)
    val state: StateFlow<PronunciationState> = mutableState.asStateFlow()
    private val pronunciationMutex = Mutex()
    @Volatile private var released = false

    suspend fun pronounce(
        word: String,
        embeddedPaths: List<String>,
        explicitHttpsUrls: List<String>,
    ): PronunciationSource? = pronunciationMutex.withLock {
        check(!released) { "Pronunciation runtime is released" }
        val normalizedWord = word.trim()
        if (normalizedWord.isEmpty()) return@withLock null

        mutableState.value = PronunciationState.Trying(PronunciationSource.MDD)
        val embedded = attempt { embeddedProvider.find(embeddedPaths) }
        if (embedded != null && attempt { embeddedPlayer.play(embedded) } == true) {
            mutableState.value = PronunciationState.Playing(PronunciationSource.MDD)
            return@withLock PronunciationSource.MDD
        }

        val urls = explicitHttpsUrls.filter(::isStrictHttpsUrl)
        for (url in urls) {
            mutableState.value = PronunciationState.Trying(PronunciationSource.HTTPS)
            if (attempt { httpsPlayer.play(url) } == true) {
                mutableState.value = PronunciationState.Playing(PronunciationSource.HTTPS)
                return@withLock PronunciationSource.HTTPS
            }
        }

        mutableState.value = PronunciationState.Trying(PronunciationSource.TTS)
        if (attempt { speechSynthesizer.speak(normalizedWord) } == true) {
            mutableState.value = PronunciationState.Playing(PronunciationSource.TTS)
            return@withLock PronunciationSource.TTS
        }
        mutableState.value = PronunciationState.Failed("没有可用发音")
        null
    }

    suspend fun pronounce(word: String, explicitHttpsUrls: List<String>): PronunciationSource? =
        pronounce(word, emptyList(), explicitHttpsUrls)

    fun stopActive() {
        embeddedPlayer.stop()
        httpsPlayer.stop()
        speechSynthesizer.stop()
        if (!released) mutableState.value = PronunciationState.Idle
    }

    override fun close() {
        if (released) return
        released = true
        stopActive()
        runCatching { embeddedPlayer.close() }
        runCatching { httpsPlayer.close() }
        runCatching { speechSynthesizer.close() }
        mutableState.value = PronunciationState.Released
    }

    companion object {
        fun isStrictHttpsUrl(value: String): Boolean = try {
            val uri = URI(value)
            uri.scheme.equals("https", ignoreCase = true) &&
                !uri.host.isNullOrBlank() && uri.userInfo == null &&
                (uri.port == -1 || uri.port == 443) && uri.fragment == null
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun <T> attempt(block: suspend () -> T): T? = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        null
    }
}
