package com.xuesui.englishapp.modelprobe.runtime

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.xuesui.englishapp.modelprobe.features.reading.*
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** Private schema 1. No destructive upgrade/downgrade; unsupported versions fail closed. */
internal class SqliteSpeechQueue(context: Context, file: File) : SQLiteOpenHelper(context, file.absolutePath, null, 1), QueuePersistence {
    init { file.parentFile!!.mkdirs(); setWriteAheadLoggingEnabled(true) }
    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true); db.execSQL("PRAGMA synchronous=FULL") }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE batches (id TEXT PRIMARY KEY NOT NULL, queue_order INTEGER NOT NULL UNIQUE, document_id TEXT NOT NULL, state TEXT NOT NULL, payload TEXT NOT NULL)")
        db.execSQL("CREATE TABLE paragraphs (id TEXT PRIMARY KEY NOT NULL, batch_id TEXT NOT NULL REFERENCES batches(id), paragraph_index INTEGER NOT NULL, state TEXT NOT NULL, audio_run TEXT, pending_run TEXT, payload TEXT NOT NULL, UNIQUE(batch_id, paragraph_index))")
        db.execSQL("CREATE INDEX paragraph_state ON paragraphs(batch_id, state, paragraph_index)")
        db.execSQL("CREATE TABLE latest_audio (identity TEXT PRIMARY KEY NOT NULL, audio_run TEXT NOT NULL, payload TEXT NOT NULL)")
        db.execSQL("CREATE TABLE queue_meta (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { error("语音任务库需要显式向前迁移：$oldVersion → $newVersion；原数据保留") }
    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { error("语音任务库不支持降级；原数据保留") }
    override fun load(): LedgerState {
        val db = readableDatabase
        fun payload(table: String): List<JSONObject> = db.rawQuery("SELECT payload FROM $table", null).use { cursor ->
            buildList { while (cursor.moveToNext()) add(JSONObject(cursor.getString(0))) }
        }
        val garbage = db.rawQuery("SELECT value FROM queue_meta WHERE key='garbage'", null).use { cursor ->
            if (!cursor.moveToFirst()) emptySet() else JSONArray(cursor.getString(0)).let { list -> (0 until list.length()).map { list.getString(it) }.toSet() }
        }
        return LedgerState(payload("batches").map(SpeechBatch::read).sortedBy { it.order }, payload("paragraphs").map(ParagraphJob::read),
            payload("latest_audio").map(ParagraphAudio::read).associateBy { it.key }, garbage)
    }
    override fun save(state: LedgerState) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            // Update existing rows instead of REPLACE: deleting a parent would break its children.
            fun upsert(table: String, key: String, id: String, values: ContentValues) {
                if (db.update(table, values, "$key=?", arrayOf(id)) == 0) db.insertOrThrow(table, null, values)
            }
            state.batches.forEach { row -> upsert("batches", "id", row.id, ContentValues().apply {
                put("id", row.id); put("queue_order", row.order); put("document_id", row.document); put("state", row.phase.name); put("payload", row.json().toString())
            }) }
            state.jobs.forEach { row -> upsert("paragraphs", "id", row.id, ContentValues().apply {
                put("id", row.id); put("batch_id", row.batch); put("paragraph_index", row.index); put("state", row.phase.name)
                put("audio_run", row.run); put("pending_run", row.draft); put("payload", row.json().toString())
            }) }
            val existing = db.rawQuery("SELECT identity FROM latest_audio", null).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }
            existing.filter { it !in state.latest }.forEach { db.delete("latest_audio", "identity=?", arrayOf(it)) }
            state.latest.forEach { (key, row) -> upsert("latest_audio", "identity", key, ContentValues().apply {
                put("identity", key); put("audio_run", row.run); put("payload", row.json().toString())
            }) }
            upsert("queue_meta", "key", "garbage", ContentValues().apply { put("key", "garbage"); put("value", JSONArray(state.garbage.toList()).toString()) })
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
}
