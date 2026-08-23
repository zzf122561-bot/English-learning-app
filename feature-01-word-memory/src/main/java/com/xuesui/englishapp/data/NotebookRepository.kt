package com.xuesui.englishapp.data

import androidx.room.withTransaction
import com.xuesui.englishapp.docx.ParsedNotebook
import com.xuesui.englishapp.study.StudyFontScale
import kotlinx.coroutines.flow.Flow

class NotebookRepository(
    private val database: WordMemoryDatabase,
) {
    private val dao = database.notebookDao()

    fun observeNotebooks(): Flow<List<NotebookEntity>> = dao.observeNotebooks()
    fun observeNotebook(notebookId: Long): Flow<NotebookEntity?> = dao.observeNotebook(notebookId)
    fun observeSegments(notebookId: Long): Flow<List<StudySegmentWithTargets>> = dao.observeSegments(notebookId)

    suspend fun importNotebook(parsed: ParsedNotebook): Long = database.withTransaction {
        val now = System.currentTimeMillis()
        val notebookId = dao.insertNotebook(
            NotebookEntity(
                title = parsed.title,
                originalFileName = parsed.originalFileName,
                segmentCount = parsed.segments.size,
                targetCount = parsed.targetCount,
                createdAt = now,
                updatedAt = now,
            ),
        )
        parsed.segments.forEach { parsedSegment ->
            val segmentId = dao.insertSegment(
                StudySegmentEntity(
                    notebookId = notebookId,
                    position = parsedSegment.position,
                    englishText = parsedSegment.englishText,
                    chineseText = parsedSegment.chineseText,
                ),
            )
            dao.insertTargets(
                parsedSegment.targets.map { target ->
                    TargetEntity(
                        notebookId = notebookId,
                        segmentId = segmentId,
                        key = target.key,
                        english = target.english,
                        chinese = target.chinese,
                        englishStart = target.englishStart,
                        englishEnd = target.englishEnd,
                        chineseStart = target.chineseStart,
                        chineseEnd = target.chineseEnd,
                        position = target.position,
                    )
                },
            )
        }
        notebookId
    }

    suspend fun renameNotebook(id: Long, title: String) =
        dao.renameNotebook(id, title.trim(), System.currentTimeMillis())

    suspend fun deleteNotebook(id: Long) = dao.deleteNotebook(id)
    suspend fun setChineseVisible(segmentId: Long, visible: Boolean) = dao.setChineseVisible(segmentId, visible)
    suspend fun setDictationText(segmentId: Long, text: String) = dao.setDictationText(segmentId, text)
    suspend fun setLastPosition(notebookId: Long, position: Int) =
        dao.setLastPosition(notebookId, position, System.currentTimeMillis())
    suspend fun setFontLevel(notebookId: Long, fontLevel: Int) =
        dao.setFontLevel(notebookId, StudyFontScale.normalize(fontLevel))
}
