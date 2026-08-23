package com.xuesui.englishapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [NotebookEntity::class, StudySegmentEntity::class, TargetEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class WordMemoryDatabase : RoomDatabase() {
    abstract fun notebookDao(): NotebookDao

    companion object {
        @Volatile private var instance: WordMemoryDatabase? = null

        fun getInstance(context: Context): WordMemoryDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    WordMemoryDatabase::class.java,
                    "word-memory.db",
                ).addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE notebooks ADD COLUMN fontLevel INTEGER NOT NULL DEFAULT 5",
                )
            }
        }
    }
}
