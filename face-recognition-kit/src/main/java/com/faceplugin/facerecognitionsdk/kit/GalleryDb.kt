package com.faceplugin.facerecognitionsdk.kit

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** One enrolled face. Images live as JPEG files in app-private storage (see [FaceGallery]). */
data class FaceRecord(
    val id: Long,
    val name: String,
    val note: String,
    /** ISO-8601 UTC */
    val createdAt: String,
    val cropPath: String,
    val originalPath: String,
    /** JSON of the [CallReport] from enrollment; empty for faces enrolled before v2. */
    val enrollReport: String = "",
)

/**
 * SQLite gallery: `id INTEGER PRIMARY KEY AUTOINCREMENT` so ids are never reused,
 * even after Delete all.
 */
internal class GalleryDb(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "identity.sqlite", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE faces (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                note TEXT NOT NULL DEFAULT '',
                created_at TEXT NOT NULL,
                feature BLOB NOT NULL,
                crop_path TEXT NOT NULL DEFAULT '',
                original_path TEXT NOT NULL DEFAULT '',
                enroll_report TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_faces_name ON faces(name)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL("ALTER TABLE faces ADD COLUMN enroll_report TEXT NOT NULL DEFAULT ''")
    }

    fun insert(name: String, note: String, createdAt: String, feature: ByteArray): Long {
        val v = ContentValues().apply {
            put("name", name); put("note", note); put("created_at", createdAt); put("feature", feature)
        }
        return writableDatabase.insertOrThrow("faces", null, v)
    }

    fun setPaths(id: Long, crop: String, original: String) {
        val v = ContentValues().apply { put("crop_path", crop); put("original_path", original) }
        writableDatabase.update("faces", v, "id=?", arrayOf(id.toString()))
    }

    fun setReport(id: Long, json: String) {
        val v = ContentValues().apply { put("enroll_report", json) }
        writableDatabase.update("faces", v, "id=?", arrayOf(id.toString()))
    }

    fun update(id: Long, name: String, note: String): Boolean {
        val v = ContentValues().apply { put("name", name); put("note", note) }
        return writableDatabase.update("faces", v, "id=?", arrayOf(id.toString())) > 0
    }

    fun delete(id: Long): Boolean =
        writableDatabase.delete("faces", "id=?", arrayOf(id.toString())) > 0

    /**
     * Empties the gallery and restarts ids at 1. Clearing `sqlite_sequence` is what resets
     * AUTOINCREMENT; without it the next person would carry on from the old maximum, which
     * makes a freshly cleared demo gallery look like it still remembers the last one.
     */
    fun deleteAll(): Int {
        val n = writableDatabase.delete("faces", null, null)
        writableDatabase.delete("sqlite_sequence", "name=?", arrayOf("faces"))
        return n
    }

    fun count(query: String = ""): Int {
        val (where, args) = filter(query)
        readableDatabase.rawQuery("SELECT COUNT(*) FROM faces$where", args).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    fun get(id: Long): FaceRecord? {
        readableDatabase.rawQuery(
            "SELECT $COLS FROM faces WHERE id=?", arrayOf(id.toString()),
        ).use { c -> return if (c.moveToFirst()) record(c) else null }
    }

    /** Newest first. */
    fun page(query: String, limit: Int, offset: Int): List<FaceRecord> {
        val (where, args) = filter(query)
        val out = ArrayList<FaceRecord>(limit)
        readableDatabase.rawQuery(
            "SELECT $COLS FROM faces$where ORDER BY id DESC LIMIT $limit OFFSET $offset", args,
        ).use { c -> while (c.moveToNext()) out += record(c) }
        return out
    }

    /** id, name, note, feature for the in-memory search cache. */
    fun allTemplates(): List<Template> {
        val out = ArrayList<Template>()
        readableDatabase.rawQuery("SELECT id, name, note, feature FROM faces", null).use { c ->
            while (c.moveToNext()) out += Template(c.getLong(0), c.getString(1), c.getString(2), c.getBlob(3))
        }
        return out
    }

    fun allPaths(): List<String> {
        val out = ArrayList<String>()
        readableDatabase.rawQuery("SELECT crop_path, original_path FROM faces", null).use { c ->
            while (c.moveToNext()) { out += c.getString(0); out += c.getString(1) }
        }
        return out.filter { it.isNotEmpty() }
    }

    private fun filter(query: String): Pair<String, Array<String>?> {
        val q = query.trim()
        if (q.isEmpty()) return "" to null
        val like = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        return " WHERE name LIKE ? ESCAPE '\\' OR note LIKE ? ESCAPE '\\'" to arrayOf(like, like)
    }

    private fun record(c: Cursor) = FaceRecord(
        id = c.getLong(0),
        name = c.getString(1),
        note = c.getString(2),
        createdAt = c.getString(3),
        cropPath = c.getString(4),
        originalPath = c.getString(5),
        enrollReport = c.getString(6) ?: "",
    )

    data class Template(val id: Long, val name: String, val note: String, val feature: ByteArray)

    private companion object {
        const val COLS = "id, name, note, created_at, crop_path, original_path, enroll_report"
    }
}
