package com.xuesui.englishapp.dictionary.engine.internal

/** Project-owned RIPEMD-128 implementation used only for MDict v2 key-index decryption. */
internal object Ripemd128 {
    private val leftOrder = intArrayOf(
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
        7, 4, 13, 1, 10, 6, 15, 3, 12, 0, 9, 5, 2, 14, 11, 8,
        3, 10, 14, 4, 9, 15, 8, 1, 2, 7, 0, 6, 13, 11, 5, 12,
        1, 9, 11, 10, 0, 8, 12, 4, 13, 3, 7, 15, 14, 5, 6, 2,
    )
    private val rightOrder = intArrayOf(
        5, 14, 7, 0, 9, 2, 11, 4, 13, 6, 15, 8, 1, 10, 3, 12,
        6, 11, 3, 7, 0, 13, 5, 10, 14, 15, 8, 12, 4, 9, 1, 2,
        15, 5, 1, 3, 7, 14, 6, 9, 11, 8, 12, 2, 10, 0, 4, 13,
        8, 6, 4, 1, 3, 11, 15, 0, 5, 12, 2, 13, 9, 7, 10, 14,
    )
    private val leftShift = intArrayOf(
        11, 14, 15, 12, 5, 8, 7, 9, 11, 13, 14, 15, 6, 7, 9, 8,
        7, 6, 8, 13, 11, 9, 7, 15, 7, 12, 15, 9, 11, 7, 13, 12,
        11, 13, 6, 7, 14, 9, 13, 15, 14, 8, 13, 6, 5, 12, 7, 5,
        11, 12, 14, 15, 14, 15, 9, 8, 9, 14, 5, 6, 8, 6, 5, 12,
    )
    private val rightShift = intArrayOf(
        8, 9, 9, 11, 13, 15, 15, 5, 7, 7, 8, 11, 14, 14, 12, 6,
        9, 13, 15, 7, 12, 8, 9, 11, 7, 7, 12, 7, 6, 15, 13, 11,
        9, 7, 15, 11, 8, 6, 6, 14, 12, 13, 5, 14, 13, 13, 7, 5,
        15, 5, 8, 11, 14, 14, 6, 14, 6, 9, 12, 9, 12, 5, 15, 8,
    )
    private val leftConstant = intArrayOf(0x00000000, 0x5a827999, 0x6ed9eba1, 0x8f1bbcdc.toInt())
    private val rightConstant = intArrayOf(0x50a28be6, 0x5c4dd124, 0x6d703ef3, 0x00000000)

    fun digest(input: ByteArray): ByteArray {
        val bitLength = input.size.toLong() * 8
        val paddedLength = ((input.size + 9 + 63) / 64) * 64
        val padded = ByteArray(paddedLength)
        input.copyInto(padded)
        padded[input.size] = 0x80.toByte()
        for (i in 0 until 8) padded[paddedLength - 8 + i] = (bitLength ushr (8 * i)).toByte()

        var h0 = 0x67452301
        var h1 = 0xefcdab89.toInt()
        var h2 = 0x98badcfe.toInt()
        var h3 = 0x10325476
        val x = IntArray(16)

        for (block in padded.indices step 64) {
            for (i in 0 until 16) {
                val p = block + i * 4
                x[i] = (padded[p].toInt() and 0xff) or
                    ((padded[p + 1].toInt() and 0xff) shl 8) or
                    ((padded[p + 2].toInt() and 0xff) shl 16) or
                    ((padded[p + 3].toInt() and 0xff) shl 24)
            }
            var a = h0
            var b = h1
            var c = h2
            var d = h3
            var aa = h0
            var bb = h1
            var cc = h2
            var dd = h3
            for (j in 0 until 64) {
                val round = j / 16
                val t = Integer.rotateLeft(a + f(round, b, c, d) + x[leftOrder[j]] + leftConstant[round], leftShift[j])
                a = d
                d = c
                c = b
                b = t

                val tt = Integer.rotateLeft(
                    aa + f(3 - round, bb, cc, dd) + x[rightOrder[j]] + rightConstant[round],
                    rightShift[j],
                )
                aa = dd
                dd = cc
                cc = bb
                bb = tt
            }
            val t = h1 + c + dd
            h1 = h2 + d + aa
            h2 = h3 + a + bb
            h3 = h0 + b + cc
            h0 = t
        }

        val result = ByteArray(16)
        intArrayOf(h0, h1, h2, h3).forEachIndexed { index, value ->
            for (i in 0 until 4) result[index * 4 + i] = (value ushr (8 * i)).toByte()
        }
        return result
    }

    private fun f(round: Int, x: Int, y: Int, z: Int): Int = when (round) {
        0 -> x xor y xor z
        1 -> (x and y) or (x.inv() and z)
        2 -> (x or y.inv()) xor z
        3 -> (x and z) or (y and z.inv())
        else -> error("Invalid RIPEMD-128 round")
    }
}

