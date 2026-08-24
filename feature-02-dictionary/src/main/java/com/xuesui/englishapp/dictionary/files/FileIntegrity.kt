package com.xuesui.englishapp.dictionary.files

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

internal object FileIntegrity {
    private const val BUFFER_SIZE = 64 * 1024

    fun copyAndHash(
        input: InputStream,
        target: File,
        maxBytes: Long = Long.MAX_VALUE,
        onChunk: (() -> Unit)? = null,
    ): String {
        require(maxBytes >= 0) { "maxBytes must not be negative" }
        target.parentFile?.mkdirs()
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        input.use { source ->
            target.outputStream().buffered(BUFFER_SIZE).use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    onChunk?.invoke()
                    total += count
                    if (total > maxBytes) throw FileSizeLimitExceededException(maxBytes)
                    digest.update(buffer, 0, count)
                    output.write(buffer, 0, count)
                }
                output.flush()
            }
        }
        return digest.digest().hex()
    }

    fun sha256(file: File): String = file.inputStream().buffered(BUFFER_SIZE).use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count > 0) digest.update(buffer, 0, count)
        }
        digest.digest().hex()
    }

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

internal class FileSizeLimitExceededException(limit: Long) :
    IllegalStateException("File exceeds the $limit byte limit")

internal class AtomicDirectoryPromotion(
    private val target: File,
) {
    private val backup = File(target.parentFile, ".backup-${target.name}-${System.nanoTime()}")
    private var promoted = false

    fun promote(staging: File) {
        target.parentFile?.mkdirs()
        if (target.exists()) check(target.renameTo(backup)) { "Unable to back up ${target.name}" }
        try {
            check(staging.renameTo(target)) { "Unable to atomically promote ${target.name}" }
            promoted = true
        } catch (error: Throwable) {
            if (backup.exists() && !target.exists()) backup.renameTo(target)
            throw error
        }
    }

    fun commit() {
        // The promoted directory and database row are already authoritative. Backup cleanup must
        // never trigger rollback after a successful database commit.
        if (backup.exists()) backup.deleteRecursively()
    }

    fun rollback() {
        if (promoted && target.exists()) target.deleteRecursively()
        if (backup.exists() && !target.exists()) backup.renameTo(target)
    }
}

internal suspend fun <T> withStagingRollback(staging: File, block: suspend () -> T): T = try {
    block()
} catch (error: Throwable) {
    if (staging.exists()) staging.deleteRecursively()
    throw error
}
