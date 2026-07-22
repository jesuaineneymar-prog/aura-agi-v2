package com.jc.aura

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.ContentValues

class AuraMemory(context: Context) {

    private val dbHelper = AuraDatabase(context)

    fun saveFactual(key: String, value: String) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("key_name", key)
            put("value", value)
            put("timestamp", System.currentTimeMillis())
        }
        db.insertWithOnConflict("memory", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getFactual(key: String): String? {
        val db = dbHelper.readableDatabase
        val cursor = db.query("memory", arrayOf("value"), "key_name=?", arrayOf(key), null, null, null)
        return cursor.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }

    fun deleteFactual(key: String) {
        val db = dbHelper.writableDatabase
        db.delete("memory", "key_name=?", arrayOf(key))
    }

    fun getAllByPrefix(prefix: String): Map<String, String> {
        val db = dbHelper.readableDatabase
        val result = mutableMapOf<String, String>()
        val cursor = db.query("memory", arrayOf("key_name", "value"), "key_name LIKE ?", arrayOf("$prefix%"), null, null, "timestamp DESC")
        cursor.use {
            while (it.moveToNext()) {
                result[it.getString(0)] = it.getString(1)
            }
        }
        return result
    }

    fun getAll(): Map<String, String> {
        val db = dbHelper.readableDatabase
        val result = mutableMapOf<String, String>()
        val cursor = db.query("memory", arrayOf("key_name", "value"), null, null, null, null, "timestamp DESC", "200")
        cursor.use {
            while (it.moveToNext()) {
                result[it.getString(0)] = it.getString(1)
            }
        }
        return result
    }

    // Aliases usados por todos os módulos
    fun save(key: String, value: String) = saveFactual(key, value)
    fun get(key: String): String? = getFactual(key)

    fun getTasksForToday(): List<String> {
        val today = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault()).format(java.util.Date())
        val tasks = getAllByPrefix("task_")
        return tasks.entries.filter { it.key.contains(today) }.map { it.value }
    }

    fun saveConversation(role: String, content: String) {
        val key = "conv_${System.currentTimeMillis()}_$role"
        saveFactual(key, content)
    }

    fun getRecentConversation(limit: Int = 10): List<Pair<String, String>> {
        val db = dbHelper.readableDatabase
        val result = mutableListOf<Pair<String, String>>()
        val cursor = db.query("memory", arrayOf("key_name", "value"),
            "key_name LIKE ?", arrayOf("conv_%"), null, null, "timestamp DESC", limit.toString())
        cursor.use {
            while (it.moveToNext()) {
                val key = it.getString(0)
                val role = if (key.contains("_user")) "user" else "assistant"
                result.add(Pair(role, it.getString(1)))
            }
        }
        return result.reversed()
    }
    fun getAllFactual(): Map<String, String> {
        return getAll()
    }

    /**
     * Returns recent conversation as pairs of (userMessage, assistantReply).
     * Used by OpenRouter for context.
     */
    fun getRecentContext(limit: Int = 5): List<Pair<String, String>> {
        val conv = getRecentConversation(limit * 2)
        val result = mutableListOf<Pair<String, String>>()
        var currentUser = ""
        for ((role, content) in conv) {
            if (role == "user") {
                currentUser = content
            } else if (currentUser.isNotBlank()) {
                result.add(Pair(currentUser, content))
                currentUser = ""
            }
        }
        return result.take(limit)
    }

    /** Alias for saveConversation - saves a user+assistant exchange */
    fun saveEpisodic(userMsg: String, assistantReply: String) {
        saveConversation("user", userMsg)
        saveConversation("assistant", assistantReply)
    }
    private class AuraDatabase(context: Context) : SQLiteOpenHelper(context, "AuraBrain.db", null, 2) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS memory (
                    key_name TEXT PRIMARY KEY,
                    value TEXT NOT NULL,
                    timestamp INTEGER NOT NULL
                )
            """)
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            onCreate(db)
        }
    }
}
