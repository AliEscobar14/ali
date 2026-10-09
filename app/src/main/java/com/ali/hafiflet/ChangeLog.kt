package com.ali.hafiflet

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** [undo] null ise değişiklik geri alınamaz (örneğin önbellek temizliği); geçmişte bilgi olarak durur. */
class Change(val id: Long, val time: Long, val title: String, val undo: String?, val undone: Boolean) {
    val canUndo get() = undo != null && !undone
}

/** Hafiflet'in yaptığı her değişikliği ve onu geri alacak shell komutunu saklar. */
object ChangeLog {
    private const val PREFS = "history"
    private const val KEY = "changes"
    private const val LIMIT = 300

    fun add(context: Context, title: String, undo: String?) = synchronized(this) {
        val list = load(context)
        val id = (list.maxOfOrNull { it.id } ?: 0) + 1
        save(context, (listOf(Change(id, System.currentTimeMillis(), title, undo, false)) + list).take(LIMIT))
    }

    /** En yeniden eskiye sıralı. */
    fun all(context: Context): List<Change> = synchronized(this) { load(context) }

    fun markUndone(context: Context, id: Long) = synchronized(this) {
        save(context, load(context).map { if (it.id == id) Change(it.id, it.time, it.title, it.undo, true) else it })
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun load(context: Context): List<Change> {
        val raw = prefs(context).getString(KEY, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                Change(
                    o.getLong("id"),
                    o.getLong("time"),
                    o.getString("title"),
                    if (o.has("undo")) o.getString("undo") else null,
                    o.optBoolean("undone"),
                )
            }
        } catch (e: JSONException) {
            emptyList()
        }
    }

    private fun save(context: Context, list: List<Change>) {
        val array = JSONArray()
        list.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("time", it.time)
                    .put("title", it.title)
                    .put("undo", it.undo)
                    .put("undone", it.undone)
            )
        }
        prefs(context).edit().putString(KEY, array.toString()).apply()
    }
}
