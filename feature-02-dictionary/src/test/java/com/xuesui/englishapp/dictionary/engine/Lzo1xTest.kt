package com.xuesui.englishapp.dictionary.engine

import com.xuesui.englishapp.dictionary.engine.internal.Lzo1x
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class Lzo1xTest {
    @Test
    fun documentedLzokaySampleExpandsToExpectedSize() {
        val compressed = byteArrayOf(
            0x12, 0x00, 0x20, 0x00, 0xdf.toByte(), 0x00, 0x00, 0x11, 0x00, 0x00,
        )

        assertArrayEquals(ByteArray(512), Lzo1x.decompress(compressed, 512))
    }
}

