package com.xuesui.englishapp.dictionary.audio

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PronunciationCoordinatorTest {
    @Test
    fun embeddedAudioStopsFallbackChain() = runBlocking {
        val embedded = FakeEmbeddedPlayer(result = true)
        val https = FakeHttpsPlayer(result = true)
        val tts = FakeTts(result = true)
        val coordinator = PronunciationCoordinator(
            embeddedProvider = EmbeddedAudioProvider { AudioPayload(byteArrayOf(1), "audio/mpeg") },
            embeddedPlayer = embedded,
            httpsPlayer = https,
            speechSynthesizer = tts,
        )

        assertEquals(PronunciationSource.MDD, coordinator.pronounce("word", listOf("https://example.com/a.mp3")))
        assertEquals(1, embedded.calls)
        assertEquals(0, https.calls)
        assertEquals(0, tts.calls)
    }

    @Test
    fun httpsFailureFallsBackToEnglishTtsAndRejectsUnsafeUrls() = runBlocking {
        val embedded = FakeEmbeddedPlayer(result = false)
        val https = FakeHttpsPlayer(result = false)
        val tts = FakeTts(result = true)
        val coordinator = PronunciationCoordinator(
            embeddedProvider = EmbeddedAudioProvider { null },
            embeddedPlayer = embedded,
            httpsPlayer = https,
            speechSynthesizer = tts,
        )

        val source = coordinator.pronounce(
            "word",
            listOf("http://example.com/a.mp3", "file:///a.mp3", "https://example.com/a.mp3"),
        )

        assertEquals(PronunciationSource.TTS, source)
        assertEquals(1, https.calls)
        assertEquals(1, tts.calls)
    }

    @Test
    fun releaseClosesEveryBackendAndPreventsFurtherUse() {
        val embedded = FakeEmbeddedPlayer(false)
        val https = FakeHttpsPlayer(false)
        val tts = FakeTts(false)
        val coordinator = PronunciationCoordinator(EmbeddedAudioProvider { null }, embedded, https, tts)

        coordinator.close()

        assertTrue(embedded.closed)
        assertTrue(https.closed)
        assertTrue(tts.closed)
        assertEquals(PronunciationState.Released, coordinator.state.value)
    }

    @Test
    fun allFailuresReturnNull() = runBlocking {
        val coordinator = PronunciationCoordinator(
            EmbeddedAudioProvider { null },
            FakeEmbeddedPlayer(false),
            FakeHttpsPlayer(false),
            FakeTts(false),
        )
        assertNull(coordinator.pronounce("word", emptyList()))
        assertTrue(coordinator.state.value is PronunciationState.Failed)
    }

    @Test
    fun strictHttpsValidationRejectsRedirectProneOrLocalSchemes() {
        assertTrue(PronunciationCoordinator.isStrictHttpsUrl("https://example.com/audio.mp3"))
        assertFalse(PronunciationCoordinator.isStrictHttpsUrl("https://user@example.com/audio.mp3"))
        assertFalse(PronunciationCoordinator.isStrictHttpsUrl("https://example.com:8443/audio.mp3"))
        assertFalse(PronunciationCoordinator.isStrictHttpsUrl("content://example.com/audio.mp3"))
    }

    @Test
    fun cancellationIsPropagatedInsteadOfStartingFallbacks() {
        val https = FakeHttpsPlayer(true)
        val tts = FakeTts(true)

        assertThrows(CancellationException::class.java) {
            runBlocking {
                PronunciationCoordinator(
                    EmbeddedAudioProvider { throw CancellationException("cancelled") },
                    FakeEmbeddedPlayer(true),
                    https,
                    tts,
                ).pronounce("word", listOf("/word.mp3"), listOf("https://example.com/word.mp3"))
            }
        }
        assertEquals(0, https.calls)
        assertEquals(0, tts.calls)
    }

    @Test
    fun concurrentRequestsAreSerialized() = runBlocking {
        val active = AtomicInteger()
        val maximum = AtomicInteger()
        val coordinator = PronunciationCoordinator(
            EmbeddedAudioProvider {
                val current = active.incrementAndGet()
                maximum.updateAndGet { maxOf(it, current) }
                delay(50)
                active.decrementAndGet()
                null
            },
            FakeEmbeddedPlayer(false),
            FakeHttpsPlayer(false),
            FakeTts(false),
        )

        listOf(
            async(Dispatchers.Default) { coordinator.pronounce("one", emptyList()) },
            async(Dispatchers.Default) { coordinator.pronounce("two", emptyList()) },
        ).awaitAll()

        assertEquals(1, maximum.get())
    }

    private class FakeEmbeddedPlayer(private val result: Boolean) : EmbeddedAudioPlayer {
        var calls = 0
        var closed = false
        override suspend fun play(payload: AudioPayload): Boolean { calls++; return result }
        override fun close() { closed = true }
    }

    private class FakeHttpsPlayer(private val result: Boolean) : HttpsAudioPlayer {
        var calls = 0
        var closed = false
        override suspend fun play(url: String): Boolean { calls++; return result }
        override fun close() { closed = true }
    }

    private class FakeTts(private val result: Boolean) : SpeechSynthesizer {
        var calls = 0
        var closed = false
        override suspend fun speak(word: String): Boolean { calls++; return result }
        override fun close() { closed = true }
    }
}
