package app.onklav.shared

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object NativeCore {
    init {
        System.loadLibrary("onklav_core")
    }

    @JvmStatic external fun dispatch(root: String, request: String): String
}

data class Note(
    val id: String,
    val title: String = "",
    val text: String = "",
    val updated: Long = 0,
) {
    val label
        get() =
            title.trim().ifEmpty {
                text.lineSequence().firstOrNull { it.isNotBlank() }?.take(80) ?: "Untitled Note"
            }

    fun json() =
        JSONObject().put("id", id).put("title", title).put("text", text).put("updated", updated)

    companion object {
        fun from(j: JSONObject) =
            Note(j.getString("id"), j.getString("title"), j.getString("text"), j.getLong("updated"))
    }
}

class Core(context: Context) {
    private val root = context.applicationContext.filesDir.absolutePath

    companion object {
        private val lock = Mutex()
    }

    private suspend fun request(j: JSONObject): Any =
        withContext(Dispatchers.IO) {
            lock.withLock {
                val response = JSONObject(NativeCore.dispatch(root, j.toString()))
                check(response.getBoolean("ok")) {
                    response.optString("error", "Storage operation failed")
                }
                response.get("value")
            }
        }

    suspend fun create(): Note = Note.from(request(JSONObject().put("op", "new")) as JSONObject)

    suspend fun save(note: Note): Note =
        Note.from(request(JSONObject().put("op", "save").put("note", note.json())) as JSONObject)

    suspend fun load(id: String): Note =
        Note.from(request(JSONObject().put("op", "load").put("id", id)) as JSONObject)

    suspend fun delete(id: String) {
        request(JSONObject().put("op", "delete").put("id", id))
    }

    suspend fun list(query: String = ""): List<Note> {
        val array = request(JSONObject().put("op", "list").put("query", query)) as JSONArray
        return (0 until array.length()).map { Note.from(array.getJSONObject(it)) }
    }

    suspend fun inspect(): JSONObject =
        request(JSONObject().put("op", "inspect").put("file", "inspection.zip")) as JSONObject
}
