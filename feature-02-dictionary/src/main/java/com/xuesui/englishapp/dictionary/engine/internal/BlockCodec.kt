package com.xuesui.englishapp.dictionary.engine.internal

import com.xuesui.englishapp.dictionary.engine.MdictEngineException
import java.util.zip.Adler32
import java.util.zip.DataFormatException
import java.util.zip.Inflater

internal object BlockCodec {
    fun decode(raw: ByteArray, expectedSize: Long): ByteArray {
        if (raw.size < 8 || expectedSize > Int.MAX_VALUE) {
            throw MdictEngineException("Invalid compressed block size")
        }
        val info = raw.u32le(0)
        val compression = (info and 0x0f).toInt()
        val encryption = ((info ushr 4) and 0x0f).toInt()
        if (encryption != 0) {
            throw MdictEngineException("Encrypted data blocks require an unsupported passcode")
        }
        val expectedChecksum = raw.u32be(4)
        val payload = raw.copyOfRange(8, raw.size)
        val decoded = when (compression) {
            0 -> payload.also {
                if (it.size != expectedSize.toInt()) {
                    throw MdictEngineException("Uncompressed block size mismatch")
                }
            }
            1 -> Lzo1x.decompress(payload, expectedSize.toInt())
            2 -> inflate(payload, expectedSize.toInt())
            else -> throw MdictEngineException("Unsupported MDict compression type: $compression")
        }
        val checksum = Adler32().apply { update(decoded) }.value
        if (checksum != expectedChecksum) {
            throw MdictEngineException(
                "MDict block checksum mismatch: expected=$expectedChecksum actual=$checksum",
            )
        }
        return decoded
    }

    private fun inflate(payload: ByteArray, expectedSize: Int): ByteArray {
        val result = ByteArray(expectedSize)
        val inflater = Inflater()
        try {
            inflater.setInput(payload)
            var written = 0
            while (!inflater.finished() && written < result.size) {
                val count = inflater.inflate(result, written, result.size - written)
                if (count == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                written += count
            }
            if (!inflater.finished() || written != expectedSize) {
                throw MdictEngineException(
                    "Zlib size mismatch: expected=$expectedSize actual=$written finished=${inflater.finished()}",
                )
            }
            return result
        } catch (error: DataFormatException) {
            throw MdictEngineException("Invalid zlib block", error)
        } finally {
            inflater.end()
        }
    }
}

