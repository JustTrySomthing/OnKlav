package app.onklav.home

import android.content.Context
import app.onklav.shared.NativeCore
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Wire parsing and cross-device reaction association are implemented by Rust. */
class GroupProtocol(context: Context) {
    private val root = context.applicationContext.filesDir.absolutePath
    private suspend fun request(j: JSONObject): Any = withContext(ChatStore.dispatcher) {
        j.put("op", "group_wire")
        val r = JSONObject(NativeCore.dispatch(root, j.toString()))
        check(r.getBoolean("ok")) { r.optString("error", "Group event could not be read") }
        r.get("value")
    }
    suspend fun post(text: String) = request(JSONObject().put("action", "post").put("text", text)) as String
    suspend fun reaction(target: String, emoji: String) = request(JSONObject().put("action", "reaction").put("target", target).put("emoji", emoji)) as String
    suspend fun targets(rows:List<Message>):List<String> {
        val data=JSONArray();rows.forEach{data.put(JSONObject().put("text",it.text))}
        val found=request(JSONObject().put("action","targets").put("rows",data)) as JSONArray
        return List(found.length()){found.getString(it)}
    }
    suspend fun feed(rows: List<Message>): JSONObject {
        val data = JSONArray()
        rows.forEach { m -> data.put(JSONObject().put("id", m.key).put("date", m.date).put("text", m.text)
            .put("sent", m.sent).put("sender", m.sender).put("media", m.parts.isNotEmpty())
            .put("settled", !m.pending && (!m.sent || m.status in setOf("Sent", "Delivered")))) }
        return request(JSONObject().put("action", "feed").put("rows", data)) as JSONObject
    }
}
