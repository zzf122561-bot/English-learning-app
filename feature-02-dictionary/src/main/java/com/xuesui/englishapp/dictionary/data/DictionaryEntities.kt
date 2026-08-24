package com.xuesui.englishapp.dictionary.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

internal object DictionarySourceType {
    const val BUILTIN = "BUILTIN"
    const val IMPORTED = "IMPORTED"
}

internal object DictionaryStatus {
    const val READY = "READY"
    const val RESOURCE_MISSING = "RESOURCE_MISSING"
    const val ERROR = "ERROR"
}

@Entity(
    tableName = "dictionaries",
    indices = [Index(value = ["mdxSha256"], unique = true), Index(value = ["displayOrder"], unique = true)],
)
internal data class DictionaryEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val sourceType: String,
    val privateMdxPath: String,
    val mdxSha256: String,
    val enabled: Boolean,
    val displayOrder: Int,
    val formatVersion: String,
    val runtimeStatus: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "dictionary_resources",
    foreignKeys = [
        ForeignKey(
            entity = DictionaryEntity::class,
            parentColumns = ["id"],
            childColumns = ["dictionaryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("dictionaryId"), Index(value = ["dictionaryId", "partOrder"], unique = true)],
)
internal data class DictionaryResourceEntity(
    @PrimaryKey val id: String,
    val dictionaryId: String,
    val privateMddPath: String,
    val mddSha256: String,
    val partOrder: Int,
)

internal data class DictionaryWithResources(
    @Embedded val dictionary: DictionaryEntity,
    @Relation(parentColumn = "id", entityColumn = "dictionaryId")
    val resources: List<DictionaryResourceEntity>,
)

