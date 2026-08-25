package com.xuesui.englishapp.dictionary.data

import android.content.Context
import androidx.room.withTransaction
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.Flow

internal class DictionaryRepository(
    private val context: Context,
    private val database: DictionaryDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val dao = database.dictionaryDao()

    val dictionaries: Flow<List<DictionaryWithResources>> = dao.observeAll()

    suspend fun enabledDictionaries(): List<DictionaryWithResources> = dao.getEnabled()

    suspend fun findDuplicate(sha256: String): DictionaryEntity? = dao.findByMdxSha256(sha256)

    suspend fun findById(id: String): DictionaryEntity? = dao.getDictionary(id)

    suspend fun nextDisplayOrder(): Int = dao.nextDisplayOrder()

    suspend fun setEnabled(id: String, enabled: Boolean) {
        check(dao.updateEnabled(id, enabled, now()) == 1) { "Dictionary not found: $id" }
    }

    suspend fun setRuntimeStatus(id: String, status: String) {
        check(dao.updateRuntimeStatus(id, status, now()) == 1) { "Dictionary not found: $id" }
    }

    suspend fun setFontLevel(id: String, level: Int) {
        check(dao.updateFontLevel(id, DictionaryFontScale.normalize(level)) == 1) {
            "Dictionary not found: $id"
        }
    }

    suspend fun reorder(orderedIds: List<String>) {
        require(orderedIds.distinct().size == orderedIds.size) { "Dictionary order contains duplicates" }
        database.withTransaction {
            val current = dao.getAll().map { it.dictionary.id }
            require(current.toSet() == orderedIds.toSet() && current.size == orderedIds.size) {
                "Dictionary order must contain every dictionary exactly once"
            }
            val timestamp = now()
            // Move rows through a disjoint range so the unique order index remains valid.
            orderedIds.forEachIndexed { index, id ->
                check(dao.updateOrder(id, -1_000_000 - index, timestamp) == 1)
            }
            orderedIds.forEachIndexed { index, id ->
                check(dao.updateOrder(id, index, timestamp) == 1)
            }
        }
    }

    suspend fun replace(
        dictionary: DictionaryEntity,
        resources: List<DictionaryResourceEntity>,
    ) = database.withTransaction {
        dao.replaceDictionary(dictionary, resources)
    }

    suspend fun deleteImported(id: String) {
        val dictionary = dao.getDictionary(id) ?: return
        require(dictionary.sourceType == DictionarySourceType.IMPORTED) {
            "Built-in dictionaries cannot be deleted"
        }
        val directory = File(dictionary.privateMdxPath).parentFile
            ?: error("Imported dictionary has no private directory")
        val importedRoot = File(context.filesDir, "dictionaries/imported").canonicalFile
        val canonicalDirectory = directory.canonicalFile
        require(ImportedDeletionGuard.isControlledDictionaryDirectory(importedRoot, canonicalDirectory)) {
            "Refusing to delete outside the imported dictionary root"
        }

        val quarantine = File(importedRoot, ".deleting-${UUID.randomUUID()}")
        if (canonicalDirectory.exists()) {
            check(canonicalDirectory.renameTo(quarantine)) { "Unable to quarantine imported dictionary" }
        }
        try {
            database.withTransaction {
                check(dao.deleteImportedOnly(id) == 1) { "DAO refused imported dictionary deletion" }
            }
            if (quarantine.exists()) check(quarantine.deleteRecursively()) { "Unable to remove imported files" }
        } catch (error: Throwable) {
            if (quarantine.exists() && !canonicalDirectory.exists()) quarantine.renameTo(canonicalDirectory)
            throw error
        }
    }
}

internal object DictionaryFontScale {
    const val DEFAULT_LEVEL = 5
    private val percentages = intArrayOf(71, 82, 88, 94, 100, 112, 124, 135, 153, 176)

    fun normalize(level: Int): Int = level.coerceIn(1, percentages.size)

    fun textZoom(level: Int): Int = percentages[normalize(level) - 1]
}

internal object ImportedDeletionGuard {
    fun isControlledDictionaryDirectory(importedRoot: File, candidate: File): Boolean {
        val root = importedRoot.canonicalFile
        val directory = candidate.canonicalFile
        return directory != root && directory.parentFile?.canonicalFile == root &&
            directory.name.isNotBlank() && directory.name != "." && directory.name != ".." &&
            !directory.name.startsWith(".")
    }
}
