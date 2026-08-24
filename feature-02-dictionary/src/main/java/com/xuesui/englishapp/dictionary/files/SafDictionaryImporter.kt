package com.xuesui.englishapp.dictionary.files

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.xuesui.englishapp.dictionary.data.DictionaryEntity
import com.xuesui.englishapp.dictionary.data.DictionaryRepository
import com.xuesui.englishapp.dictionary.data.DictionaryResourceEntity
import com.xuesui.englishapp.dictionary.data.DictionarySourceType
import com.xuesui.englishapp.dictionary.data.DictionaryStatus
import com.xuesui.englishapp.dictionary.engine.MdictSource
import com.xuesui.englishapp.dictionary.engine.PureKotlinMdictEngine
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class SafDocument(
    val documentId: String,
    val displayName: String,
    val uri: Uri?,
    val size: Long?,
)

internal data class DictionaryImportPlan(
    val mdx: SafDocument,
    val resources: List<SafDocument>,
)

internal data class DictionaryImportResult(
    val displayName: String,
    val imported: Boolean,
    val message: String,
)

internal object DictionaryImportPlanner {
    fun plan(documents: List<SafDocument>): List<DictionaryImportPlan> {
        val named = documents.filter { safeName(it.displayName) }
        val mdxDocuments = named.filter { it.displayName.endsWith(".mdx", ignoreCase = true) }
        return mdxDocuments.map { mdx ->
            val base = mdx.displayName.dropLast(4)
            val numbered = Regex("^${Regex.escape(base)}(?:\\.(\\d+))?\\.mdd$", RegexOption.IGNORE_CASE)
            val resources = named.mapNotNull { document ->
                val match = numbered.matchEntire(document.displayName) ?: return@mapNotNull null
                val part = match.groupValues[1].takeIf(String::isNotEmpty)?.toIntOrNull() ?: 0
                part to document
            }.also { parts ->
                val duplicateParts = parts.groupBy { it.first }.filterValues { it.size > 1 }.keys
                require(duplicateParts.isEmpty()) {
                    "重复的 MDD 逻辑分卷: ${duplicateParts.sorted().joinToString()}"
                }
            }.sortedBy { it.first }.map { it.second }
            DictionaryImportPlan(mdx, resources)
        }.sortedBy { it.mdx.displayName.lowercase(Locale.ROOT) }
    }

    private fun safeName(name: String): Boolean =
        name.isNotBlank() && !name.contains('/') && !name.contains('\\') && name != "." && name != ".."
}

internal class SafDirectoryScanner(private val resolver: ContentResolver) {
    fun scan(treeUri: Uri): List<SafDocument> {
        val parentDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        val result = mutableListOf<SafDocument>()
        resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val sizeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
            val mimeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                if (cursor.getString(mimeColumn) == DocumentsContract.Document.MIME_TYPE_DIR) continue
                val id = cursor.getString(idColumn)
                result += SafDocument(
                    documentId = id,
                    displayName = cursor.getString(nameColumn),
                    uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id),
                    size = if (cursor.isNull(sizeColumn)) null else cursor.getLong(sizeColumn),
                )
            }
        }
        return result
    }
}

internal class SafDictionaryImporter(
    private val context: Context,
    private val repository: DictionaryRepository,
) {
    private val resolver = context.contentResolver

    suspend fun importTree(treeUri: Uri): List<DictionaryImportResult> {
        val plans = DictionaryImportPlanner.plan(SafDirectoryScanner(resolver).scan(treeUri))
        return buildList {
            for (plan in plans) {
                currentCoroutineContext().ensureActive()
                try {
                    add(importOne(plan))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    add(
                        DictionaryImportResult(
                            displayName = plan.mdx.displayName,
                            imported = false,
                            message = error.message ?: "导入失败",
                        ),
                    )
                }
            }
        }
    }

    private suspend fun importOne(plan: DictionaryImportPlan): DictionaryImportResult {
        val operationId = UUID.randomUUID().toString()
        val staging = File(context.filesDir, "dictionaries/importing/$operationId")
        staging.mkdirs()
        var promotion: AtomicDirectoryPromotion? = null
        try {
            val mdxTarget = copyDocument(plan.mdx, staging)
            val mdxSha256 = FileIntegrity.sha256(mdxTarget)
            require(repository.findDuplicate(mdxSha256) == null) { "相同 MDX 已导入" }
            val resourceFiles = plan.resources.map { copyDocument(it, staging) }
            val resourceHashes = resourceFiles.map(FileIntegrity::sha256)
            val formatVersion = PureKotlinMdictEngine.open(MdictSource(mdxTarget, resourceFiles)).use {
                it.metadata.formatVersion
            }

            val id = "imported.${mdxSha256.take(24).lowercase(Locale.ROOT)}"
            val target = File(context.filesDir, "dictionaries/imported/$id")
            promotion = AtomicDirectoryPromotion(target).also { it.promote(staging) }
            val timestamp = System.currentTimeMillis()
            repository.replace(
                DictionaryEntity(
                    id = id,
                    displayName = plan.mdx.displayName.dropLast(4),
                    sourceType = DictionarySourceType.IMPORTED,
                    privateMdxPath = File(target, mdxTarget.name).absolutePath,
                    mdxSha256 = mdxSha256,
                    enabled = true,
                    displayOrder = repository.nextDisplayOrder(),
                    formatVersion = formatVersion,
                    runtimeStatus = if (resourceFiles.isEmpty()) {
                        DictionaryStatus.RESOURCE_MISSING
                    } else {
                        DictionaryStatus.READY
                    },
                    createdAt = timestamp,
                    updatedAt = timestamp,
                ),
                resourceFiles.mapIndexed { index, file ->
                    DictionaryResourceEntity(
                        id = "$id.resource.$index",
                        dictionaryId = id,
                        privateMddPath = File(target, file.name).absolutePath,
                        mddSha256 = resourceHashes[index],
                        partOrder = index,
                    )
                },
            )
            promotion.commit()
            return DictionaryImportResult(
                displayName = plan.mdx.displayName,
                imported = true,
                message = if (resourceFiles.isEmpty()) "已导入，未找到 MDD 资源" else "已导入",
            )
        } catch (error: Throwable) {
            promotion?.rollback()
            withStagingRollback(staging) { throw error }
        }
    }

    private suspend fun copyDocument(document: SafDocument, directory: File): File {
        currentCoroutineContext().ensureActive()
        require(!document.displayName.contains('/') && !document.displayName.contains('\\')) {
            "不安全的文件名"
        }
        val target = File(directory, document.displayName)
        val uri = document.uri ?: error("文件缺少 SAF URI")
        val input = resolver.openInputStream(uri) ?: error("无法读取 ${document.displayName}")
        val coroutineContext = currentCoroutineContext()
        FileIntegrity.copyAndHash(input, target) {
            coroutineContext.ensureActive()
        }
        return target
    }
}
