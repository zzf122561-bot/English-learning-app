package com.xuesui.englishapp.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WordMemoryMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        WordMemoryDatabase::class.java,
    )

    @Test
    fun migrationFrom1To2PreservesLearningStateAndDefaultsFontLevel() {
        helper.createDatabase(DATABASE_NAME, 1).apply {
            execSQL(
                """INSERT INTO notebooks
                    (id, title, originalFileName, segmentCount, targetCount, lastSegmentIndex, createdAt, updatedAt)
                    VALUES (1, 'Migration Notebook', 'migration.docx', 1, 1, 0, 100, 200)
                """.trimIndent(),
            )
            execSQL(
                """INSERT INTO study_segments
                    (id, notebookId, position, englishText, chineseText, dictationText, chineseVisible)
                    VALUES (10, 1, 0, 'A0001. lucid', '【中文译文】A0001. 清晰的', '清晰的', 1)
                """.trimIndent(),
            )
            execSQL(
                """INSERT INTO targets
                    (id, notebookId, segmentId, targetKey, english, chinese, englishStart, englishEnd, chineseStart, chineseEnd, position)
                    VALUES (20, 1, 10, 'A0001', 'lucid', '清晰的', 0, 12, 6, 18, 0)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            DATABASE_NAME,
            2,
            true,
            WordMemoryDatabase.MIGRATION_1_2,
        )

        migrated.query(
            "SELECT title, lastSegmentIndex, fontLevel, createdAt, updatedAt FROM notebooks WHERE id = 1",
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("Migration Notebook", cursor.getString(0))
            assertEquals(0, cursor.getInt(1))
            assertEquals(5, cursor.getInt(2))
            assertEquals(100L, cursor.getLong(3))
            assertEquals(200L, cursor.getLong(4))
        }
        migrated.query(
            "SELECT dictationText, chineseVisible FROM study_segments WHERE id = 10",
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("清晰的", cursor.getString(0))
            assertEquals(1, cursor.getInt(1))
        }
        migrated.query(
            "SELECT targetKey, english, chinese FROM targets WHERE id = 20",
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("A0001", cursor.getString(0))
            assertEquals("lucid", cursor.getString(1))
            assertEquals("清晰的", cursor.getString(2))
        }
        migrated.close()
    }

    companion object {
        private const val DATABASE_NAME = "migration-1-to-2-test"
    }
}
