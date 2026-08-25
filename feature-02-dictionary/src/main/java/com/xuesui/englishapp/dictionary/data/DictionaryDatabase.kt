package com.xuesui.englishapp.dictionary.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DictionaryEntity::class, DictionaryResourceEntity::class],
    version = 2,
    exportSchema = true,
)
internal abstract class DictionaryDatabase : RoomDatabase() {
    abstract fun dictionaryDao(): DictionaryDao

    companion object {
        const val DATABASE_NAME = "englishapp_dictionary.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE dictionaries ADD COLUMN fontLevel INTEGER NOT NULL DEFAULT 5",
                )
            }
        }

        @Volatile private var instance: DictionaryDatabase? = null

        fun get(context: Context): DictionaryDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                DictionaryDatabase::class.java,
                DATABASE_NAME,
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}
