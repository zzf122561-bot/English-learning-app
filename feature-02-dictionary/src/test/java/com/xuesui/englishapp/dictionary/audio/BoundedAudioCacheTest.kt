package com.xuesui.englishapp.dictionary.audio

import com.xuesui.englishapp.dictionary.files.FileSizeLimitExceededException
import java.io.InputStream
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedAudioCacheTest {
    @Test
    fun unknownLengthStreamStopsAtLimitAndLeavesNoFile() {
        val directory = Files.createTempDirectory("audio-limit-").toFile()
        val target = directory.resolve("download.bin")
        try {
            assertThrows(FileSizeLimitExceededException::class.java) {
                BoundedAudioCache.write(RepeatingInputStream(MAX_AUDIO_BYTES + 1), target)
            }
            assertFalse(target.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun mddPayloadLimitIsCheckedBeforeCacheWrite() {
        assertTrue(isAudioPayloadWithinLimit(MAX_AUDIO_BYTES))
        assertFalse(isAudioPayloadWithinLimit(MAX_AUDIO_BYTES + 1))
    }

    @Test
    fun oldUtteranceCallbackCannotCompleteNewRequest() = runBlocking {
        val registry = UtteranceCompletionRegistry()
        val old = registry.begin("old")
        val current = registry.begin("current")

        registry.complete("old", true)

        assertTrue(old.isCancelled)
        assertFalse(current.isCompleted)
        registry.complete("current", true)
        assertTrue(current.await())
    }

    private class RepeatingInputStream(private var remaining: Long) : InputStream() {
        override fun read(): Int = if (remaining-- > 0) 0 else -1

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (remaining <= 0) return -1
            val count = minOf(length.toLong(), remaining).toInt()
            buffer.fill(0, offset, offset + count)
            remaining -= count
            return count
        }
    }
}
