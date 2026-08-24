package com.xuesui.englishapp.dictionary.engine.internal

import com.xuesui.englishapp.dictionary.engine.MdictEngineException

/**
 * Pure Kotlin LZO1X decompressor.
 *
 * Ported and adapted from encounter/lzokay-rs 2.0.1 src/decompress.rs (MIT). Only the
 * decompression state machine is used; no Rust code or dependency is included in the build.
 */
internal object Lzo1x {
    fun decompress(source: ByteArray, expectedSize: Int): ByteArray {
        if (source.size < 3) fail("input overrun")
        val output = ByteArray(expectedSize)
        var input = 0
        var out = 0
        var state = 0
        var instruction = source.u8(input++)
        var matchLength = 0

        fun byte(): Int = if (input < source.size) source.u8(input++) else fail("input overrun")
        fun copyLiteral(length: Int) {
            if (length < 0 || input + length > source.size) fail("input overrun")
            if (out + length > output.size) fail("output overrun")
            source.copyInto(output, out, input, input + length)
            input += length
            out += length
        }
        fun zeroRun(): Int {
            val start = input
            while (input < source.size && source[input].toInt() == 0) input++
            if (input == source.size) fail("input overrun")
            return input - start
        }

        if (instruction >= 22) {
            copyLiteral(instruction - 17)
            state = 4
        } else if (instruction >= 18) {
            state = instruction - 17
            copyLiteral(state)
        }

        while (true) {
            if (input > 1 || state > 0) instruction = byte()
            val lookBehind: Int
            val nextState: Int
            when {
                instruction and 0xc0 != 0 -> {
                    val next = byte()
                    val distance = (next shl 3) + ((instruction ushr 2) and 7) + 1
                    lookBehind = out - distance
                    matchLength = (instruction ushr 5) + 1
                    nextState = instruction and 3
                }
                instruction and 0x20 != 0 -> {
                    matchLength = (instruction and 0x1f) + 2
                    if (matchLength == 2) matchLength += zeroRun() * 255 + 31 + byte()
                    val raw = byte() or (byte() shl 8)
                    lookBehind = out - (raw ushr 2) - 1
                    nextState = raw and 3
                }
                instruction and 0x10 != 0 -> {
                    matchLength = (instruction and 7) + 2
                    if (matchLength == 2) matchLength += zeroRun() * 255 + 7 + byte()
                    val raw = byte() or (byte() shl 8)
                    val baseDistance = ((instruction and 8) shl 11) + (raw ushr 2)
                    if (baseDistance == 0) break
                    lookBehind = out - baseDistance - 16384
                    nextState = raw and 3
                }
                state == 0 -> {
                    var length = instruction + 3
                    if (length == 3) length += zeroRun() * 255 + 15 + byte()
                    copyLiteral(length)
                    state = 4
                    continue
                }
                state != 4 -> {
                    val distance = (instruction ushr 2) + (byte() shl 2) + 1
                    lookBehind = out - distance
                    matchLength = 2
                    nextState = instruction and 3
                }
                else -> {
                    val distance = (instruction ushr 2) + (byte() shl 2) + 2049
                    lookBehind = out - distance
                    matchLength = 3
                    nextState = instruction and 3
                }
            }
            if (lookBehind < 0) fail("lookbehind overrun")
            if (out + matchLength > output.size || lookBehind + matchLength > output.size) {
                fail("output overrun")
            }
            for (i in 0 until matchLength) output[out + i] = output[lookBehind + i]
            out += matchLength
            copyLiteral(nextState)
            state = nextState
        }
        if (matchLength != 3) fail("invalid terminator")
        if (input != source.size) fail("input not consumed")
        if (out != expectedSize) fail("size mismatch: expected=$expectedSize actual=$out")
        return output
    }

    private fun ByteArray.u8(index: Int): Int = this[index].toInt() and 0xff

    private fun fail(message: String): Nothing = throw MdictEngineException("LZO1X: $message")
}

