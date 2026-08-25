package com.xuesui.englishapp.dictionary.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DictionaryMigrationAndroidTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun cleanUp() {
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun migrationOneToTwoPreservesDictionaryAndResourcesAndAddsDefaultFont() {
        createVersionOneDatabase().use { helper ->
            helper.writableDatabase.apply {
                execSQL(
                    "INSERT INTO dictionaries " +
                        "(id,displayName,sourceType,privateMdxPath,mdxSha256,enabled,displayOrder," +
                        "formatVersion,runtimeStatus,createdAt,updatedAt) VALUES " +
                        "('old','Old dictionary','IMPORTED','old.mdx','${"a".repeat(64)}',0,3,'2.0','READY',11,22)",
                )
                execSQL(
                    "INSERT INTO dictionary_resources " +
                        "(id,dictionaryId,privateMddPath,mddSha256,partOrder) VALUES " +
                        "('resource','old','old.mdd','${"b".repeat(64)}',0)",
                )
            }
        }

        val migrated = Room.databaseBuilder(context, DictionaryDatabase::class.java, TEST_DATABASE)
            .addMigrations(DictionaryDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        try {
            val database = migrated.openHelper.writableDatabase
            assertDictionaryRow(database)
            database.query("SELECT dictionaryId, privateMddPath, partOrder FROM dictionary_resources").use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals("old", cursor.getString(0))
                assertEquals("old.mdd", cursor.getString(1))
                assertEquals(0, cursor.getInt(2))
            }
        } finally {
            migrated.close()
        }
    }

    private fun createVersionOneDatabase(): SupportSQLiteOpenHelper =
        FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(TEST_DATABASE)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS dictionaries (" +
                                "id TEXT NOT NULL, displayName TEXT NOT NULL, sourceType TEXT NOT NULL," +
                                "privateMdxPath TEXT NOT NULL, mdxSha256 TEXT NOT NULL, enabled INTEGER NOT NULL," +
                                "displayOrder INTEGER NOT NULL, formatVersion TEXT NOT NULL," +
                                "runtimeStatus TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL," +
                                "PRIMARY KEY(id))",
                        )
                        db.execSQL(
                            "CREATE UNIQUE INDEX IF NOT EXISTS index_dictionaries_mdxSha256 ON dictionaries (mdxSha256)",
                        )
                        db.execSQL(
                            "CREATE UNIQUE INDEX IF NOT EXISTS index_dictionaries_displayOrder ON dictionaries (displayOrder)",
                        )
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS dictionary_resources (" +
                                "id TEXT NOT NULL, dictionaryId TEXT NOT NULL, privateMddPath TEXT NOT NULL," +
                                "mddSha256 TEXT NOT NULL, partOrder INTEGER NOT NULL, PRIMARY KEY(id)," +
                                "FOREIGN KEY(dictionaryId) REFERENCES dictionaries(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
                        )
                        db.execSQL(
                            "CREATE INDEX IF NOT EXISTS index_dictionary_resources_dictionaryId ON dictionary_resources (dictionaryId)",
                        )
                        db.execSQL(
                            "CREATE UNIQUE INDEX IF NOT EXISTS index_dictionary_resources_dictionaryId_partOrder " +
                                "ON dictionary_resources (dictionaryId, partOrder)",
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )

    private fun assertDictionaryRow(database: SupportSQLiteDatabase) {
        database.query(
            "SELECT displayName,sourceType,privateMdxPath,enabled,displayOrder,formatVersion," +
                "runtimeStatus,createdAt,updatedAt,fontLevel FROM dictionaries WHERE id='old'",
        ).use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("Old dictionary", cursor.getString(0))
            assertEquals("IMPORTED", cursor.getString(1))
            assertEquals("old.mdx", cursor.getString(2))
            assertEquals(0, cursor.getInt(3))
            assertEquals(3, cursor.getInt(4))
            assertEquals("2.0", cursor.getString(5))
            assertEquals("READY", cursor.getString(6))
            assertEquals(11, cursor.getLong(7))
            assertEquals(22, cursor.getLong(8))
            assertEquals(5, cursor.getInt(9))
        }
    }

    private companion object {
        const val TEST_DATABASE = "dictionary-migration-test"
    }
}
