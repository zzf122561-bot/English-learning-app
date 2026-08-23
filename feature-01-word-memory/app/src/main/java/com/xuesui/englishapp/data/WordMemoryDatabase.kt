package com.xuesui.englishapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [NotebookEntity::class, StudySegmentEntity::class, TargetEntity::class],
    version = 1,
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
                ).build().also { instance = it }
            }
    }
}
