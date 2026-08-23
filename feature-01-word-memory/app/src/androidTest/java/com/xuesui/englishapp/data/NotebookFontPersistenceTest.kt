package com.xuesui.englishapp.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xuesui.englishapp.docx.ParsedNotebook
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotebookFontPersistenceTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DATABASE_NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun fontLevelSurvivesDatabaseCloseAndReopen() = runBlocking {
        var database = openDatabase()
        var repository = NotebookRepository(database)
        val notebookId = repository.importNotebook(
            ParsedNotebook(
                title = "Persistent Font",
                originalFileName = "persistent.docx",
                segments = emptyList(),
            ),
        )
        repository.setFontLevel(notebookId, 8)
        assertEquals(8, repository.observeNotebook(notebookId).first { it?.fontLevel == 8 }!!.fontLevel)
        database.close()

        database = openDatabase()
        repository = NotebookRepository(database)
        assertEquals(8, repository.observeNotebook(notebookId).first { it != null }!!.fontLevel)
        database.close()
    }

    private fun openDatabase(): WordMemoryDatabase =
        Room.databaseBuilder(context, WordMemoryDatabase::class.java, DATABASE_NAME)
            .addMigrations(WordMemoryDatabase.MIGRATION_1_2)
            .build()

    companion object {
        private const val DATABASE_NAME = "font-persistence-test.db"
    }
}
