package com.xuesui.englishapp.dictionary.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
internal interface DictionaryDao {
    @Transaction
    @Query("SELECT * FROM dictionaries ORDER BY displayOrder ASC, id ASC")
    fun observeAll(): Flow<List<DictionaryWithResources>>

    @Transaction
    @Query("SELECT * FROM dictionaries ORDER BY displayOrder ASC, id ASC")
    suspend fun getAll(): List<DictionaryWithResources>

    @Transaction
    @Query("SELECT * FROM dictionaries WHERE enabled = 1 ORDER BY displayOrder ASC, id ASC")
    suspend fun getEnabled(): List<DictionaryWithResources>

    @Query("SELECT * FROM dictionaries WHERE id = :id")
    suspend fun getDictionary(id: String): DictionaryEntity?

    @Query("SELECT * FROM dictionaries WHERE mdxSha256 = :sha256 LIMIT 1")
    suspend fun findByMdxSha256(sha256: String): DictionaryEntity?

    @Query("SELECT COALESCE(MAX(displayOrder), -1) + 1 FROM dictionaries")
    suspend fun nextDisplayOrder(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDictionary(dictionary: DictionaryEntity)

    @Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun updateDictionary(dictionary: DictionaryEntity): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertResources(resources: List<DictionaryResourceEntity>)

    @Query("DELETE FROM dictionary_resources WHERE dictionaryId = :dictionaryId")
    suspend fun deleteResources(dictionaryId: String)

    @Query("UPDATE dictionaries SET enabled = :enabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateEnabled(id: String, enabled: Boolean, updatedAt: Long): Int

    @Query("UPDATE dictionaries SET displayOrder = :displayOrder, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateOrder(id: String, displayOrder: Int, updatedAt: Long): Int

    @Query("UPDATE dictionaries SET runtimeStatus = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateRuntimeStatus(id: String, status: String, updatedAt: Long): Int

    /** DAO-level protection: this SQL can never delete a built-in row. */
    @Query("DELETE FROM dictionaries WHERE id = :id AND sourceType = 'IMPORTED'")
    suspend fun deleteImportedOnly(id: String): Int

    @Transaction
    suspend fun replaceDictionary(
        dictionary: DictionaryEntity,
        resources: List<DictionaryResourceEntity>,
    ) {
        if (getDictionary(dictionary.id) == null) {
            insertDictionary(dictionary)
        } else {
            check(updateDictionary(dictionary) == 1) { "Dictionary update failed: ${dictionary.id}" }
        }
        deleteResources(dictionary.id)
        if (resources.isNotEmpty()) insertResources(resources)
    }
}
