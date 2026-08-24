package com.xuesui.englishapp.dictionary.engine.internal

import com.xuesui.englishapp.dictionary.engine.MdictEngineException
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.util.zip.Adler32
import java.util.Locale
import kotlin.math.min

internal enum class MdictFileKind { MDX, MDD }

internal data class MdictHeaderData(
    val title: String,
    val description: String?,
    val version: String,
    val encodingName: String,
    val encryptedIndex: Boolean,
)

internal data class KeyRecord(val key: String, val recordOffset: Long)

internal data class BlockMeta(
    val compressedSize: Long,
    val decompressedSize: Long,
    val fileOffset: Long,
    val decompressedOffset: Long,
)

internal class MemoryBoundaryTracker {
    var maxSingleFileReadBytes: Int = 0
        private set
    var maxCompressedBlockBytes: Int = 0
        private set
    var maxDecompressedBlockBytes: Int = 0
        private set
    var maxKeyIndexBytes: Int = 0
        private set

    fun fileRead(bytes: Int) {
        maxSingleFileReadBytes = maxOf(maxSingleFileReadBytes, bytes)
    }

    fun block(compressed: Int, decompressed: Int) {
        maxCompressedBlockBytes = maxOf(maxCompressedBlockBytes, compressed)
        maxDecompressedBlockBytes = maxOf(maxDecompressedBlockBytes, decompressed)
    }

    fun keyIndex(bytes: Int) {
        maxKeyIndexBytes = maxOf(maxKeyIndexBytes, bytes)
    }
}

internal class MdictV2File private constructor(
    val source: File,
    val kind: MdictFileKind,
    val header: MdictHeaderData,
    val keys: List<KeyRecord>,
    private val recordBlocks: List<BlockMeta>,
    private val totalRecordBytes: Long,
    val tracker: MemoryBoundaryTracker,
    private val access: BinaryAccess,
    private val charset: Charset,
) : Closeable {
    val fileSize: Long get() = access.size

    fun exactKey(query: String): Int? {
        val canonical = canonicalKey(query)
        return keys.indices.firstOrNull { canonicalKey(keys[it].key) == canonical }
    }

    fun prefixKeys(prefix: String, limit: Int): List<Pair<Int, KeyRecord>> {
        if (limit <= 0) return emptyList()
        val canonical = canonicalKey(prefix)
        return keys.withIndex()
            .asSequence()
            .filter { canonicalKey(it.value.key).startsWith(canonical) }
            .take(limit)
            .map { it.index to it.value }
            .toList()
    }

    fun readRecord(index: Int): ByteArray {
        val start = keys.getOrNull(index)?.recordOffset
            ?: throw MdictEngineException("Key index out of range: $index")
        val end = keys.getOrNull(index + 1)?.recordOffset ?: totalRecordBytes
        if (start > end || end > totalRecordBytes) {
            throw MdictEngineException("Invalid record range: $start..$end")
        }
        if (start == end) return ByteArray(0)
        val resultSize = end - start
        if (resultSize > Int.MAX_VALUE) throw MdictEngineException("Record is too large")
        val output = ByteArrayOutputStream(resultSize.toInt())
        var cursor = start
        while (cursor < end) {
            val block = findRecordBlock(cursor)
            val decoded = decodeBlock(block)
            val localStart = (cursor - block.decompressedOffset).toInt()
            val blockEnd = block.decompressedOffset + block.decompressedSize
            val localEnd = (min(end, blockEnd) - block.decompressedOffset).toInt()
            output.write(decoded, localStart, localEnd - localStart)
            cursor = block.decompressedOffset + localEnd
        }
        return output.toByteArray()
    }

    fun decodeText(bytes: ByteArray): String = String(bytes, charset).trimEnd('\u0000')

    override fun close() = access.close()

    private fun decodeBlock(block: BlockMeta): ByteArray {
        val raw = access.read(block.fileOffset, block.compressedSize)
        val decoded = BlockCodec.decode(raw, block.decompressedSize)
        tracker.block(raw.size, decoded.size)
        return decoded
    }

    private fun findRecordBlock(offset: Long): BlockMeta {
        var low = 0
        var high = recordBlocks.lastIndex
        while (low <= high) {
            val mid = (low + high) ushr 1
            val block = recordBlocks[mid]
            when {
                offset < block.decompressedOffset -> high = mid - 1
                offset >= block.decompressedOffset + block.decompressedSize -> low = mid + 1
                else -> return block
            }
        }
        throw MdictEngineException("No record block contains offset $offset")
    }

    private fun canonicalKey(value: String): String = value.replace('/', '\\').lowercase(Locale.ROOT)

    companion object {
        fun open(file: File, kind: MdictFileKind): MdictV2File {
            val tracker = MemoryBoundaryTracker()
            val access = BinaryAccess(file, tracker::fileRead)
            try {
                val parsedHeader = parseHeader(access, kind)
                val charset = charset(parsedHeader.encodingName, kind)
                val unitWidth = if (charset.name().startsWith("UTF-16", ignoreCase = true)) 2 else 1
                var position = 0L
                val headerLengthBytes = access.read(position, 4)
                val headerLength = headerLengthBytes.u32be(0)
                position += 4 + headerLength + 4

                val infoBytes = access.read(position, 40)
                position += 40
                val expectedInfoChecksum = access.read(position, 4).u32be(0)
                position += 4
                val actualInfoChecksum = Adler32().apply { update(infoBytes) }.value
                if (expectedInfoChecksum != actualInfoChecksum) {
                    throw MdictEngineException("Key block info checksum mismatch")
                }
                val info = ByteCursor(infoBytes)
                val keyBlockCount = info.u64be().nonNegative("key block count")
                val entryCount = info.u64be().nonNegative("entry count")
                val keyIndexDecompressedSize = info.u64be().nonNegative("key index decompressed size")
                val keyIndexCompressedSize = info.u64be().nonNegative("key index compressed size")
                val keyBlocksSize = info.u64be().nonNegative("key blocks size")

                val compressedIndex = access.read(position, keyIndexCompressedSize)
                position += keyIndexCompressedSize
                if (parsedHeader.encryptedIndex) decryptKeyIndex(compressedIndex)
                val keyIndex = BlockCodec.decode(compressedIndex, keyIndexDecompressedSize)
                tracker.keyIndex(keyIndex.size)
                val keyBlockDataOffset = position
                val keyBlocks = parseKeyBlockIndex(
                    keyIndex = keyIndex,
                    count = keyBlockCount,
                    dataOffset = keyBlockDataOffset,
                    unitWidth = unitWidth,
                )
                val indexedKeyBytes = keyBlocks.sumOf { it.compressedSize }
                if (indexedKeyBytes != keyBlocksSize) {
                    throw MdictEngineException(
                        "Key block byte count mismatch: expected=$keyBlocksSize actual=$indexedKeyBytes",
                    )
                }

                position = keyBlockDataOffset + keyBlocksSize
                val recordInfoBytes = access.read(position, 32)
                position += 32
                val recordInfo = ByteCursor(recordInfoBytes)
                val recordBlockCount = recordInfo.u64be().nonNegative("record block count")
                val recordEntryCount = recordInfo.u64be().nonNegative("record entry count")
                val recordIndexSize = recordInfo.u64be().nonNegative("record index size")
                val recordBlocksSize = recordInfo.u64be().nonNegative("record blocks size")
                if (recordEntryCount != entryCount) {
                    throw MdictEngineException(
                        "Record entry count mismatch: keys=$entryCount records=$recordEntryCount",
                    )
                }
                val recordIndex = access.read(position, recordIndexSize)
                position += recordIndexSize
                val recordBlocks = parseRecordBlockIndex(recordIndex, recordBlockCount, position)
                if (recordBlocks.sumOf { it.compressedSize } != recordBlocksSize) {
                    throw MdictEngineException("Record block byte count mismatch")
                }

                val keys = ArrayList<KeyRecord>(entryCount.checkedInt("entry count"))
                keyBlocks.forEach { block ->
                    val decoded = BlockCodec.decode(access.read(block.fileOffset, block.compressedSize), block.decompressedSize)
                    tracker.block(block.compressedSize.checkedInt("compressed block"), decoded.size)
                    val cursor = ByteCursor(decoded)
                    while (cursor.remaining > 0) {
                        val recordOffset = cursor.u64be().nonNegative("record offset")
                        val key = cursor.nullTerminatedString(charset, unitWidth)
                        keys += KeyRecord(key, recordOffset)
                    }
                }
                if (keys.size.toLong() != entryCount) {
                    throw MdictEngineException(
                        "Parsed key count mismatch: expected=$entryCount actual=${keys.size}",
                    )
                }
                val totalRecordBytes = recordBlocks.sumOf { it.decompressedSize }
                return MdictV2File(
                    source = file,
                    kind = kind,
                    header = parsedHeader,
                    keys = keys,
                    recordBlocks = recordBlocks,
                    totalRecordBytes = totalRecordBytes,
                    tracker = tracker,
                    access = access,
                    charset = charset,
                )
            } catch (error: Throwable) {
                access.close()
                if (error is MdictEngineException) throw error
                throw MdictEngineException("Failed to open ${file.name}", error)
            }
        }

        private fun parseHeader(access: BinaryAccess, kind: MdictFileKind): MdictHeaderData {
            val headerLength = access.read(0, 4).u32be(0)
            if (headerLength > 1_048_576) throw MdictEngineException("MDict header is too large")
            val headerBytes = access.read(4, headerLength)
            val checksum = ByteBuffer.wrap(access.read(4 + headerLength, 4))
                .order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xffffffffL
            val actual = Adler32().apply { update(headerBytes) }.value
            if (checksum != actual) throw MdictEngineException("MDict header checksum mismatch")
            val xml = if (headerBytes.size >= 2 && headerBytes.takeLast(2) == listOf<Byte>(0, 0)) {
                String(headerBytes, 0, headerBytes.size - 2, Charsets.UTF_16LE)
            } else {
                String(headerBytes, Charsets.UTF_8)
            }
            val attributes = Regex(
                "([A-Za-z][A-Za-z0-9_]*)\\s*=\\s*[\\\"'](.*?)[\\\"']",
                setOf(RegexOption.DOT_MATCHES_ALL),
            ).findAll(xml).associate { it.groupValues[1] to xmlUnescape(it.groupValues[2]) }
            val version = attributes["GeneratedByEngineVersion"] ?: "1.0"
            if (!version.startsWith("2.")) {
                throw MdictEngineException("Only MDict 2.x is enabled for milestone 1B: $version")
            }
            val encrypted = attributes["Encrypted"]?.toIntOrNull() ?: 0
            if (encrypted and 1 != 0) {
                throw MdictEngineException("Encrypted record blocks require a passcode")
            }
            return MdictHeaderData(
                title = attributes["Title"] ?: access.size.toString(),
                description = attributes["Description"],
                version = version,
                encodingName = if (kind == MdictFileKind.MDD) "UTF-16LE" else attributes["Encoding"] ?: "UTF-8",
                encryptedIndex = encrypted and 2 != 0,
            )
        }

        private fun parseKeyBlockIndex(
            keyIndex: ByteArray,
            count: Long,
            dataOffset: Long,
            unitWidth: Int,
        ): List<BlockMeta> {
            val cursor = ByteCursor(keyIndex)
            val result = ArrayList<BlockMeta>(count.checkedInt("key block count"))
            var fileOffset = dataOffset
            var decompressedOffset = 0L
            while (cursor.remaining > 0) {
                cursor.u64be().nonNegative("entries in key block")
                cursor.skipLengthPrefixedText(2, unitWidth)
                cursor.skipLengthPrefixedText(2, unitWidth)
                val compressed = cursor.u64be().nonNegative("key block compressed size")
                val decompressed = cursor.u64be().nonNegative("key block decompressed size")
                result += BlockMeta(compressed, decompressed, fileOffset, decompressedOffset)
                fileOffset += compressed
                decompressedOffset += decompressed
            }
            if (result.size.toLong() != count) {
                throw MdictEngineException("Key block count mismatch")
            }
            return result
        }

        private fun parseRecordBlockIndex(
            recordIndex: ByteArray,
            count: Long,
            dataOffset: Long,
        ): List<BlockMeta> {
            val cursor = ByteCursor(recordIndex)
            val result = ArrayList<BlockMeta>(count.checkedInt("record block count"))
            var fileOffset = dataOffset
            var decompressedOffset = 0L
            repeat(count.checkedInt("record block count")) {
                val compressed = cursor.u64be().nonNegative("record block compressed size")
                val decompressed = cursor.u64be().nonNegative("record block decompressed size")
                result += BlockMeta(compressed, decompressed, fileOffset, decompressedOffset)
                fileOffset += compressed
                decompressedOffset += decompressed
            }
            if (cursor.remaining != 0) throw MdictEngineException("Trailing record index bytes")
            return result
        }

        private fun decryptKeyIndex(block: ByteArray) {
            if (block.size < 8) throw MdictEngineException("Encrypted key index is truncated")
            val keySeed = block.copyOfRange(4, 8) + byteArrayOf(0x95.toByte(), 0x36, 0, 0)
            val key = Ripemd128.digest(keySeed)
            var previous = 0x36
            for (index in 8 until block.size) {
                val current = block[index].toInt() and 0xff
                val rotated = ((current shl 4) or (current ushr 4)) and 0xff
                block[index] = (rotated xor previous xor (index - 8) xor (key[(index - 8) % key.size].toInt() and 0xff)).toByte()
                previous = current
            }
        }

        private fun charset(label: String, kind: MdictFileKind): Charset {
            if (kind == MdictFileKind.MDD) return Charsets.UTF_16LE
            val normalized = when {
                label.equals("GBK", true) || label.equals("GB2312", true) -> "GB18030"
                else -> label
            }
            return try {
                Charset.forName(normalized)
            } catch (error: Exception) {
                throw MdictEngineException("Unsupported dictionary encoding: $label", error)
            }
        }

        private fun xmlUnescape(value: String): String = value
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")

        private fun Long.nonNegative(label: String): Long {
            if (this < 0) throw MdictEngineException("$label exceeds signed 64-bit range")
            return this
        }

        private fun Long.checkedInt(label: String): Int {
            if (this < 0 || this > Int.MAX_VALUE) throw MdictEngineException("$label exceeds JVM array limit")
            return toInt()
        }
    }
}
