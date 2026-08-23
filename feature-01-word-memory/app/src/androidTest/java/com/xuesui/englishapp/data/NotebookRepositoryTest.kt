package com.xuesui.englishapp.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xuesui.englishapp.docx.ParsedNotebook
import com.xuesui.englishapp.docx.ParsedStudySegment
import com.xuesui.englishapp.docx.ParsedTarget
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotebookRepositoryTest {
    private lateinit var database: WordMemoryDatabase
    private lateinit var repository: NotebookRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WordMemoryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = NotebookRepository(database)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun importAndLearningStateSurviveRepositoryReads() = runBlocking {
        val notebookId = repository.importNotebook(sampleNotebook())
        val created = repository.observeNotebooks().first().single()
        val initialSegment = repository.observeSegments(notebookId).first().single()

        assertEquals("Local Context", created.title)
        assertEquals(1, created.segmentCount)
        assertEquals(1, created.targetCount)
        assertEquals(5, created.fontLevel)
        assertFalse(initialSegment.segment.chineseVisible)
        assertEquals("A0001", initialSegment.targets.single().key)

        repository.setChineseVisible(initialSegment.segment.id, true)
        repository.setDictationText(initialSegment.segment.id, "清晰的")
        repository.setLastPosition(notebookId, 0)
        repository.renameNotebook(notebookId, "Renamed Context")

        val savedNotebook = repository.observeNotebook(notebookId).first { it?.title == "Renamed Context" }!!
        val savedSegment = repository.observeSegments(notebookId).first {
            it.single().segment.dictationText == "清晰的"
        }.single()
        assertEquals(0, savedNotebook.lastSegmentIndex)
        assertTrue(savedSegment.segment.chineseVisible)
        assertEquals("清晰的", savedSegment.segment.dictationText)

        repository.deleteNotebook(notebookId)
        assertTrue(repository.observeNotebooks().first { it.isEmpty() }.isEmpty())
    }

    @Test
    fun fontLevelIsIndependentPerNotebookAndDoesNotChangeListOrderingTime() = runBlocking {
        val firstId = repository.importNotebook(sampleNotebook())
        val secondId = repository.importNotebook(sampleNotebook())
        val firstBefore = repository.observeNotebook(firstId).first { it != null }!!

        repository.setFontLevel(firstId, 9)

        val firstAfter = repository.observeNotebook(firstId).first { it?.fontLevel == 9 }!!
        val second = repository.observeNotebook(secondId).first { it != null }!!
        assertEquals(9, firstAfter.fontLevel)
        assertEquals(5, second.fontLevel)
        assertEquals(firstBefore.updatedAt, firstAfter.updatedAt)

        repository.setFontLevel(firstId, 99)
        assertEquals(10, repository.observeNotebook(firstId).first { it?.fontLevel == 10 }!!.fontLevel)
    }

    private fun sampleNotebook() = ParsedNotebook(
        title = "Local Context",
        originalFileName = "local.docx",
        segments = listOf(
            ParsedStudySegment(
                position = 0,
                englishText = "Keep A0001. lucid in context.",
                chineseText = "【中文译文】把 A0001. 清晰的 放在语境中。",
                targets = listOf(
                    ParsedTarget(
                        key = "A0001",
                        english = "lucid",
                        chinese = "清晰的",
                        englishStart = 5,
                        englishEnd = 17,
                        chineseStart = 8,
                        chineseEnd = 19,
                        position = 0,
                    ),
                ),
            ),
        ),
    )
}
