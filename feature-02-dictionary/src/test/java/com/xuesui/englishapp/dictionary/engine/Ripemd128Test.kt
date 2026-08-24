package com.xuesui.englishapp.dictionary.engine

import com.xuesui.englishapp.dictionary.engine.internal.Ripemd128
import org.junit.Assert.assertEquals
import org.junit.Test

class Ripemd128Test {
    @Test
    fun standardVectors() {
        assertEquals("cdf26213a150dc3ecb610f18f6b38b46", Ripemd128.digest(byteArrayOf()).hex())
        assertEquals("86be7afa339d0fc7cfc785e72f578d33", Ripemd128.digest("a".encodeToByteArray()).hex())
        assertEquals(
            "c14a12199c66e4ba84636b0f69144c77",
            Ripemd128.digest("abc".encodeToByteArray()).hex(),
        )
    }

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

