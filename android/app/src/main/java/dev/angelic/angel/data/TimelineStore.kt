package dev.angelic.angel.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import dev.angelic.angel.model.HypeDetector
import dev.angelic.angel.model.Post
import dev.angelic.angel.model.normalizeHandle

/** Ingestion, classification and hiding share one durable transaction. */
class TimelineStore(context: Context) : SQLiteOpenHelper(context, "timeline.db", null, 1) {
    data class Block(val handle: String, val reason: String)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE tabs (handle TEXT PRIMARY KEY, position INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE posts (id TEXT PRIMARY KEY, handle TEXT NOT NULL, name TEXT NOT NULL, body TEXT NOT NULL, received INTEGER NOT NULL, url TEXT NOT NULL)")
        db.execSQL("CREATE INDEX posts_author ON posts(handle, received DESC)")
        db.execSQL("CREATE TABLE blocks (handle TEXT PRIMARY KEY, reason TEXT NOT NULL)")
        db.execSQL("CREATE TABLE allowed (handle TEXT PRIMARY KEY)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun tabs(): List<String> = readableDatabase.rawQuery("SELECT handle FROM tabs ORDER BY position, handle", null).use { cursor ->
        buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
    }

    fun addTab(value: String): Boolean {
        val handle = normalizeHandle(value) ?: return false
        if (tabs().size >= 30) return false
        val values = ContentValues().apply {
            put("handle", handle)
            put("position", System.currentTimeMillis())
        }
        return writableDatabase.insertWithOnConflict("tabs", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L
    }

    fun removeTab(handle: String) { writableDatabase.delete("tabs", "handle = ?", arrayOf(handle)) }

    fun posts(handle: String? = null, includeBlocked: Boolean = false, limit: Int = 200): List<Post> {
        val author = if (handle == null) "1 = 1" else "p.handle = ?"
        val visibility = if (includeBlocked) "1 = 1" else "NOT EXISTS (SELECT 1 FROM blocks b WHERE b.handle = p.handle)"
        return readableDatabase.rawQuery(
            "SELECT p.id,p.handle,p.name,p.body,p.received,p.url FROM posts p WHERE $author AND $visibility ORDER BY p.received DESC,p.id DESC LIMIT ?",
            (listOfNotNull(handle) + limit.coerceIn(1, 10000).toString()).toTypedArray(),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(Post(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getLong(4), cursor.getString(5)))
            }
        }
    }

    fun ingest(post: Post, automatic: Boolean): Boolean {
        val handle = normalizeHandle(post.handle) ?: return false
        if (post.id.isBlank() || post.id.length > 256 || post.text.isBlank()) return false
        val db = writableDatabase
        db.beginTransaction()
        try {
            val values = ContentValues().apply {
                put("id", post.id); put("handle", handle); put("name", post.displayName.take(200))
                put("body", post.text.take(10000)); put("received", post.receivedAt); put("url", post.url.take(2048))
            }
            val inserted = db.insertWithOnConflict("posts", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L
            if (inserted && automatic && !isAllowed(handle) && HypeDetector.shouldBlock(posts(handle, true, 10))) {
                block(handle, "直近10件以内に煽り表現が3件以上、かつ半数以上。単語ルールによる推定。")
            }
            // Bound storage during long-running streams, without deleting block decisions.
            db.execSQL("DELETE FROM posts WHERE id NOT IN (SELECT id FROM posts ORDER BY received DESC,id DESC LIMIT 10000)")
            db.setTransactionSuccessful()
            return inserted
        } finally { db.endTransaction() }
    }

    fun block(handle: String, reason: String) {
        val normalized = normalizeHandle(handle) ?: return
        writableDatabase.insertWithOnConflict("blocks", null, ContentValues().apply {
            put("handle", normalized); put("reason", reason)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun blocks(): List<Block> = readableDatabase.rawQuery("SELECT handle,reason FROM blocks ORDER BY handle", null).use { cursor ->
        buildList { while (cursor.moveToNext()) add(Block(cursor.getString(0), cursor.getString(1))) }
    }

    fun isAllowed(handle: String): Boolean = readableDatabase.rawQuery("SELECT 1 FROM allowed WHERE handle = ?", arrayOf(handle)).use { it.moveToFirst() }

    /** Undo exempts the author so the next notification cannot immediately reblock them. */
    fun unblock(handle: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("blocks", "handle = ?", arrayOf(handle))
            db.insertWithOnConflict("allowed", null, ContentValues().apply { put("handle", handle) }, SQLiteDatabase.CONFLICT_IGNORE)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun reset() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            listOf("tabs", "posts", "blocks", "allowed").forEach { db.delete(it, null, null) }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
}
