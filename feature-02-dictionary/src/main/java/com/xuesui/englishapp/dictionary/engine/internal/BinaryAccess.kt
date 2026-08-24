package com.xuesui.englishapp.dictionary.engine.internal

import com.xuesui.englishapp.dictionary.engine.MdictEngineException
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.charset.Charset

internal class BinaryAccess(
    file: File,
    private val onRead: (Int) -> Unit,
) : Closeable {
    private val randomAccessFile = RandomAccessFile(file, "r")
    private val channel: FileChannel = randomAccessFile.channel

    val size: Long = channel.size()

    fun read(position: Long, byteCount: Long): ByteArray {
        if (position < 0 || byteCount < 0 || position + byteCount > size || byteCount > Int.MAX_VALUE) {
            throw MdictEngineException(
                "Invalid random read: position=$position bytes=$byteCount fileSize=$size",
            )
        }
        val result = ByteArray(byteCount.toInt())
        val buffer = ByteBuffer.wrap(result)
        var offset = position
        while (buffer.hasRemaining()) {
            val count = channel.read(buffer, offset)
            if (count < 0) {
                throw MdictEngineException("Unexpected EOF at position $offset")
            }
            offset += count
        }
        onRead(result.size)
        return result
    }

    override fun close() {
        channel.close()
        randomAccessFile.close()
    }
}

internal class ByteCursor(private val bytes: ByteArray) {
    var position: Int = 0
        private set

    val remaining: Int get() = bytes.size - position

    fun u8(): Int = take(1)[0].toInt() and 0xff

    fun u16be(): Int = ByteBuffer.wrap(take(2)).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xffff

    fun u32be(): Long = ByteBuffer.wrap(take(4)).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xffffffffL

    fun u64be(): Long = ByteBuffer.wrap(take(8)).order(ByteOrder.BIG_ENDIAN).long

    fun number(width: Int): Long = when (width) {
        4 -> u32be()
        8 -> u64be().also { require(it >= 0) { "Unsigned 64-bit value exceeds supported range" } }
        else -> error("Unsupported number width: $width")
    }

    fun smallNumber(width: Int): Int = when (width) {
        1 -> u8()
        2 -> u16be()
        else -> error("Unsupported small-number width: $width")
    }

    fun take(count: Int): ByteArray {
        if (count < 0 || count > remaining) {
            throw MdictEngineException(
                "Binary section truncated: need=$count remaining=$remaining position=$position",
            )
        }
        return bytes.copyOfRange(position, position + count).also { position += count }
    }

    fun skip(count: Int) {
        if (count < 0 || count > remaining) {
            throw MdictEngineException(
                "Binary section truncated while skipping: need=$count remaining=$remaining",
            )
        }
        position += count
    }

    fun skipLengthPrefixedText(smallNumberWidth: Int, unitWidth: Int) {
        val units = smallNumber(smallNumberWidth)
        skip((units + 1) * unitWidth)
    }

    fun nullTerminatedString(charset: Charset, unitWidth: Int): String {
        var end = position
        if (unitWidth == 2) {
            while (end + 1 < bytes.size && !(bytes[end].toInt() == 0 && bytes[end + 1].toInt() == 0)) {
                end += 2
            }
            if (end + 1 >= bytes.size) throw MdictEngineException("Missing UTF-16 null terminator")
        } else {
            while (end < bytes.size && bytes[end].toInt() != 0) end++
            if (end >= bytes.size) throw MdictEngineException("Missing string null terminator")
        }
        val value = String(bytes, position, end - position, charset)
        position = end + unitWidth
        return value
    }
}

internal fun ByteArray.u32le(offset: Int): Long =
    ByteBuffer.wrap(this, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xffffffffL

internal fun ByteArray.u32be(offset: Int): Long =
    ByteBuffer.wrap(this, offset, 4).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xffffffffL
