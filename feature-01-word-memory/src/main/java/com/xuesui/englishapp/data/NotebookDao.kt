package com.xuesui.englishapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface NotebookDao {
    @Query("SELECT * FROM notebooks ORDER BY updatedAt DESC")
    fun observeNotebooks(): Flow<List<NotebookEntity>>

    @Query("SELECT * FROM notebooks WHERE id = :notebookId")
    fun observeNotebook(notebookId: Long): Flow<NotebookEntity?>

    @Transaction
    @Query("SELECT * FROM study_segments WHERE notebookId = :notebookId ORDER BY position")
    fun observeSegments(notebookId: Long): Flow<List<StudySegmentWithTargets>>

    @Insert
    suspend fun insertNotebook(notebook: NotebookEntity): Long

    @Insert
    suspend fun insertSegment(segment: StudySegmentEntity): Long

    @Insert
    suspend fun insertTargets(targets: List<TargetEntity>)

    @Query("UPDATE notebooks SET title = :title, updatedAt = :updatedAt WHERE id = :notebookId")
    suspend fun renameNotebook(notebookId: Long, title: String, updatedAt: Long)

    @Query("DELETE FROM notebooks WHERE id = :notebookId")
    suspend fun deleteNotebook(notebookId: Long)

    @Query("UPDATE study_segments SET chineseVisible = :visible WHERE id = :segmentId")
    suspend fun setChineseVisible(segmentId: Long, visible: Boolean)

    @Query("UPDATE study_segments SET dictationText = :text WHERE id = :segmentId")
    suspend fun setDictationText(segmentId: Long, text: String)

    @Query("UPDATE notebooks SET lastSegmentIndex = :position, updatedAt = :updatedAt WHERE id = :notebookId")
    suspend fun setLastPosition(notebookId: Long, position: Int, updatedAt: Long)

    @Query("UPDATE notebooks SET fontLevel = :fontLevel WHERE id = :notebookId")
    suspend fun setFontLevel(notebookId: Long, fontLevel: Int)
}
