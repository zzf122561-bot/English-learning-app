package com.xuesui.englishapp.dictionary.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DictionaryDatabaseAndroidTest {
    private lateinit var context: Context
    private lateinit var database: DictionaryDatabase
    private lateinit var repository: DictionaryRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, DictionaryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = DictionaryRepository(context, database) { 100L }
    }

    @After
    fun tearDown() {
        database.close()
        File(context.filesDir, "dictionaries/imported/android-test").deleteRecursively()
    }

    @Test
    fun databaseVersionEnabledStateAndStableOrderPersist() = runBlocking {
        repository.replace(entity("one", DictionarySourceType.BUILTIN, 0), emptyList())
        repository.replace(entity("two", DictionarySourceType.IMPORTED, 1), emptyList())

        repository.setEnabled("one", false)
        repository.reorder(listOf("two", "one"))

        val rows = repository.dictionaries.first()
        assertEquals(2, database.openHelper.readableDatabase.version)
        assertEquals(listOf("two", "one"), rows.map { it.dictionary.id })
        assertFalse(rows.last().dictionary.enabled)
    }

    @Test
    fun builtInDeletionIsRejectedByDomainAndDao() = runBlocking {
        repository.replace(entity("builtin", DictionarySourceType.BUILTIN, 0), emptyList())

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.deleteImported("builtin") }
        }
        assertEquals(0, database.dictionaryDao().deleteImportedOnly("builtin"))
        assertNotNull(database.dictionaryDao().getDictionary("builtin"))
    }

    @Test
    fun importedDeletionRemovesOnlyPrivateCopyAndDatabaseRows() = runBlocking {
        val directory = File(context.filesDir, "dictionaries/imported/android-test").apply { mkdirs() }
        val mdx = directory.resolve("test.mdx").apply { writeText("private") }
        repository.replace(
            entity("imported", DictionarySourceType.IMPORTED, 0).copy(privateMdxPath = mdx.absolutePath),
            listOf(DictionaryResourceEntity("resource", "imported", directory.resolve("test.mdd").absolutePath, "b".repeat(64), 0)),
        )

        repository.deleteImported("imported")

        assertFalse(directory.exists())
        assertEquals(null, database.dictionaryDao().getDictionary("imported"))
    }

    @Test
    fun duplicateMdxHashIsDiscoverableBeforeImport() = runBlocking {
        repository.replace(entity("one", DictionarySourceType.IMPORTED, 0), emptyList())
        assertEquals("one", repository.findDuplicate("a".repeat(64))?.id)
    }

    @Test
    fun hashConflictAbortsWithoutDeletingOrOverwritingExistingDictionary() = runBlocking {
        val existing = entity("one", DictionarySourceType.IMPORTED, 0)
        repository.replace(existing, emptyList())

        assertThrows(Exception::class.java) {
            runBlocking { repository.replace(entity("conflict", DictionarySourceType.IMPORTED, 1).copy(mdxSha256 = existing.mdxSha256), emptyList()) }
        }

        assertEquals(existing, database.dictionaryDao().getDictionary("one"))
        assertEquals(null, database.dictionaryDao().getDictionary("conflict"))
    }

    @Test
    fun displayOrderConflictAbortsWithoutDeletingOrOverwritingExistingDictionary() = runBlocking {
        val existing = entity("one", DictionarySourceType.IMPORTED, 0)
        repository.replace(existing, emptyList())

        assertThrows(Exception::class.java) {
            runBlocking { repository.replace(entity("conflict", DictionarySourceType.IMPORTED, 0), emptyList()) }
        }

        assertEquals(existing, database.dictionaryDao().getDictionary("one"))
        assertEquals(null, database.dictionaryDao().getDictionary("conflict"))
    }

    @Test
    fun resourceReplacementRollsBackDictionaryUpdateWhenNewResourcesConflict() = runBlocking {
        val existing = entity("one", DictionarySourceType.IMPORTED, 0)
        val originalResource = DictionaryResourceEntity("old", "one", "old.mdd", "b".repeat(64), 0)
        repository.replace(existing, listOf(originalResource))
        val conflictingResources = listOf(
            DictionaryResourceEntity("new-1", "one", "one.mdd", "c".repeat(64), 0),
            DictionaryResourceEntity("new-2", "one", "two.mdd", "d".repeat(64), 0),
        )

        assertThrows(Exception::class.java) {
            runBlocking {
                repository.replace(existing.copy(displayName = "changed"), conflictingResources)
            }
        }

        val row = database.dictionaryDao().getAll().single()
        assertEquals(existing.displayName, row.dictionary.displayName)
        assertEquals(listOf(originalResource), row.resources)
    }


    @Test
    fun fontUpdatesAreIndependentEvenWhenDictionaryIsDisabledAndPreserveOtherFields() = runBlocking {
        val first = entity("one", DictionarySourceType.BUILTIN, 0).copy(enabled = false, updatedAt = 44)
        val second = entity("two", DictionarySourceType.IMPORTED, 1).copy(fontLevel = 8, updatedAt = 55)
        repository.replace(first, emptyList())
        repository.replace(second, emptyList())

        repository.setFontLevel("one", 99)

        assertEquals(first.copy(fontLevel = 10), database.dictionaryDao().getDictionary("one"))
        assertEquals(second, database.dictionaryDao().getDictionary("two"))
    }

    private fun entity(id: String, source: String, order: Int) = DictionaryEntity(
        id = id,
        displayName = id,
        sourceType = source,
        privateMdxPath = File(context.filesDir, "dictionaries/imported/$id/$id.mdx").absolutePath,
        mdxSha256 = if (id == "one") "a".repeat(64) else id.padEnd(64, '0').take(64),
        enabled = true,
        displayOrder = order,
        formatVersion = "2.0",
        runtimeStatus = DictionaryStatus.READY,
        createdAt = 1,
        updatedAt = 1,
    )
}
