package com.xuesui.englishapp.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "notebooks")
data class NotebookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val originalFileName: String,
    val segmentCount: Int,
    val targetCount: Int,
    val lastSegmentIndex: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "study_segments",
    foreignKeys = [
        ForeignKey(
            entity = NotebookEntity::class,
            parentColumns = ["id"],
            childColumns = ["notebookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("notebookId"),
        Index(value = ["notebookId", "position"], unique = true),
    ],
)
data class StudySegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val notebookId: Long,
    val position: Int,
    val englishText: String,
    val chineseText: String,
    val dictationText: String = "",
    val chineseVisible: Boolean = false,
)

@Entity(
    tableName = "targets",
    foreignKeys = [
        ForeignKey(
            entity = StudySegmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["segmentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("segmentId"),
        Index(value = ["notebookId", "targetKey"], unique = true),
    ],
)
data class TargetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val notebookId: Long,
    val segmentId: Long,
    @ColumnInfo(name = "targetKey") val key: String,
    val english: String,
    val chinese: String,
    val englishStart: Int,
    val englishEnd: Int,
    val chineseStart: Int,
    val chineseEnd: Int,
    val position: Int,
)

data class StudySegmentWithTargets(
    @Embedded val segment: StudySegmentEntity,
    @Relation(parentColumn = "id", entityColumn = "segmentId")
    val targets: List<TargetEntity>,
)
