package app.nimble.tools

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Note(val text: String, val createdAt: Long)

/** Notes saved by the agent, stored as a small JSON file in app storage. */
class NotesStore(context: Context) {
    private val file = File(context.filesDir, "notes.json")

    @Synchronized
    fun all(): List<Note> {
        if (!file.isFile) return emptyList()
        return try {
            val array = JSONArray(file.readText())
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                Note(o.getString("text"), o.optLong("createdAt"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun add(text: String) = write(all() + Note(text, System.currentTimeMillis()))

    @Synchronized
    fun clear() = write(emptyList())

    private fun write(notes: List<Note>) {
        val array = JSONArray()
        notes.forEach { array.put(JSONObject().put("text", it.text).put("createdAt", it.createdAt)) }
        file.writeText(array.toString())
    }
}
