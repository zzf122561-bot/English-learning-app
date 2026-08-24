package com.xuesui.englishapp.dictionary.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [DictionaryEntity::class, DictionaryResourceEntity::class],
    version = 1,
    exportSchema = true,
)
internal abstract class DictionaryDatabase : RoomDatabase() {
    abstract fun dictionaryDao(): DictionaryDao

    companion object {
        const val DATABASE_NAME = "englishapp_dictionary.db"

        @Volatile private var instance: DictionaryDatabase? = null

        fun get(context: Context): DictionaryDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                DictionaryDatabase::class.java,
                DATABASE_NAME,
            ).build().also { instance = it }
        }
    }
}

