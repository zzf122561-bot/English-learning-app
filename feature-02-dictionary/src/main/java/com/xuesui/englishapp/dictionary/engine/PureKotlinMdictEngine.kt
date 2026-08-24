package com.xuesui.englishapp.dictionary.engine

import com.xuesui.englishapp.dictionary.engine.internal.MdictFileKind
import com.xuesui.englishapp.dictionary.engine.internal.MdictV2File
import java.util.Locale

/** Pure Kotlin/JVM MDX/MDD 2.x implementation; no Rust, JNI, NDK, or parser dependency. */
internal class PureKotlinMdictEngine private constructor(
    private val mdx: MdictV2File,
    private val resources: List<MdictV2File>,
) : MdictEngine {
    override val metadata = MdictMetadata(
        title = mdx.header.title,
        description = mdx.header.description,
        formatVersion = mdx.header.version,
        encoding = mdx.header.encodingName,
        encryptedIndex = mdx.header.encryptedIndex,
        entryCount = mdx.keys.size.toLong(),
    )

    internal val diagnostics: MdictMemoryDiagnostics
        get() {
            val files = listOf(mdx) + resources
            return MdictMemoryDiagnostics(
                sourceBytes = files.sumOf { it.fileSize },
                indexedKeyCount = files.sumOf { it.keys.size.toLong() },
                maxSingleFileReadBytes = files.maxOf { it.tracker.maxSingleFileReadBytes },
                maxCompressedBlockBytes = files.maxOf { it.tracker.maxCompressedBlockBytes },
                maxDecompressedBlockBytes = files.maxOf { it.tracker.maxDecompressedBlockBytes },
                maxKeyIndexBytes = files.maxOf { it.tracker.maxKeyIndexBytes },
                wholeFileRead = false,
            )
        }

    override fun exactLookup(query: String): MdictEntry? = exactLookup(query, linkedSetOf())

    override fun prefixLookup(prefix: String, limit: Int): List<MdictHeadword> =
        mdx.prefixKeys(prefix, limit).map { (index, key) ->
            MdictHeadword(value = key.key, ordinal = index.toLong())
        }

    override fun readResource(path: String): MdictResource? = readResource(path, RedirectGuard())

    private fun readResource(path: String, redirects: RedirectGuard): MdictResource? {
        redirects.enter(path)
        val candidates = resourcePathCandidates(path)
        resources.forEach { mdd ->
            candidates.forEach { candidate ->
                val index = mdd.exactKey(candidate) ?: return@forEach
                val bytes = mdd.readRecord(index)
                val key = mdd.keys[index].key
                val redirect = decodeMddRedirect(bytes)
                if (redirect != null) return readResource(redirect, redirects)
                return MdictResource(
                    canonicalPath = canonicalResourcePath(key),
                    bytes = bytes,
                    mediaType = mediaType(key),
                )
            }
        }
        return null
    }

    internal fun headwordAt(index: Int): String = mdx.keys[index].key

    internal fun entryBytesAt(index: Int): Int = mdx.readRecord(index).size

    internal fun resourceKeys(): List<String> = resources.flatMap { mdd -> mdd.keys.map { it.key } }

    override fun close() {
        var failure: Throwable? = null
        (listOf(mdx) + resources).forEach {
            try {
                it.close()
            } catch (error: Throwable) {
                if (failure == null) failure = error else failure.addSuppressed(error)
            }
        }
        if (failure != null) throw MdictEngineException("Failed to close dictionary files", failure)
    }

    private fun exactLookup(query: String, redirects: MutableSet<String>): MdictEntry? {
        if (!redirects.add(query.lowercase(Locale.ROOT)) || redirects.size > 16) {
            throw MdictEngineException("MDict redirect cycle detected")
        }
        val index = mdx.exactKey(query) ?: return null
        val key = mdx.keys[index].key
        val html = mdx.decodeText(mdx.readRecord(index))
        val target = html.removePrefix("@@@LINK=").trim().takeIf { html.startsWith("@@@LINK=") }
        return if (target != null) exactLookup(target, redirects) else MdictEntry(key, html)
    }

    private fun resourcePathCandidates(path: String): List<String> {
        val normalized = path.replace('/', '\\').trimStart('\\')
        return listOf("\\$normalized", normalized).distinct()
    }

    private fun canonicalResourcePath(path: String): String =
        "/" + path.replace('\\', '/').trimStart('/')

    private fun decodeMddRedirect(bytes: ByteArray): String? {
        if (bytes.size < 16) return null
        val text = String(bytes, Charsets.UTF_16LE).trimEnd('\u0000')
        return text.removePrefix("@@@LINK=").trim().takeIf { text.startsWith("@@@LINK=") }
    }

    private fun mediaType(path: String): String? = when (path.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
        "css" -> "text/css"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "svg" -> "image/svg+xml"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "ogg" -> "audio/ogg"
        "js" -> "text/javascript"
        "html", "htm" -> "text/html"
        else -> null
    }

    companion object {
        fun open(source: MdictSource): PureKotlinMdictEngine {
            val mdx = MdictV2File.open(source.mdxFile, MdictFileKind.MDX)
            val mdds = mutableListOf<MdictV2File>()
            try {
                source.mddFiles.forEach { mdds += MdictV2File.open(it, MdictFileKind.MDD) }
                return PureKotlinMdictEngine(mdx, mdds)
            } catch (error: Throwable) {
                mdds.forEach { runCatching { it.close() } }
                runCatching { mdx.close() }
                if (error is MdictEngineException) throw error
                throw MdictEngineException("Failed to open dictionary source", error)
            }
        }
    }
}

internal data class MdictMemoryDiagnostics(
    val sourceBytes: Long,
    val indexedKeyCount: Long,
    val maxSingleFileReadBytes: Int,
    val maxCompressedBlockBytes: Int,
    val maxDecompressedBlockBytes: Int,
    val maxKeyIndexBytes: Int,
    val wholeFileRead: Boolean,
)

internal object PureKotlinMdictEngineFactory : MdictEngineFactory {
    override fun open(dictionary: MdictSource): MdictEngine = PureKotlinMdictEngine.open(dictionary)
}
