package com.xuesui.englishapp.dictionary.engine

import java.io.Closeable
import java.io.File

/**
 * Project-owned isolation boundary around the audited MDX/MDD parser.
 *
 * Implementations must use random access and on-demand decompression. They must not load a
 * complete dictionary file into memory or mutate the source files.
 */
internal interface MdictEngine : Closeable {
    val metadata: MdictMetadata

    fun exactLookup(query: String): MdictEntry?

    fun prefixLookup(prefix: String, limit: Int): List<MdictHeadword>

    fun readResource(path: String): MdictResource?
}

internal fun interface MdictEngineFactory {
    @Throws(MdictEngineException::class)
    fun open(dictionary: MdictSource): MdictEngine
}

internal data class MdictSource(
    val mdxFile: File,
    val mddFiles: List<File> = emptyList(),
) {
    init {
        require(mdxFile.extension.equals("mdx", ignoreCase = true)) {
            "Dictionary source must be an MDX file: ${mdxFile.name}"
        }
        require(mddFiles.all { it.extension.equals("mdd", ignoreCase = true) }) {
            "Every resource source must be an MDD file"
        }
    }
}

internal data class MdictMetadata(
    val title: String,
    val description: String?,
    val formatVersion: String,
    val encoding: String,
    val encryptedIndex: Boolean,
    val entryCount: Long,
)

internal data class MdictHeadword(
    val value: String,
    val ordinal: Long,
)

internal data class MdictEntry(
    val headword: String,
    val html: String,
)

internal data class MdictResource(
    val canonicalPath: String,
    val bytes: ByteArray,
    val mediaType: String?,
)

internal class MdictEngineException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
