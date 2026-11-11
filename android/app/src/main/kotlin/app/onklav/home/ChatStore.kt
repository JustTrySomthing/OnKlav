package app.onklav.home

import android.content.Context
import app.onklav.shared.NativeCore
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Every messenger preference and send outcome passes through the actual Rust core. */
class ChatStore(context: Context) {
    private val app = context.applicationContext
    private val root = app.filesDir.absolutePath

    private fun migrateOwnFolders() =
        synchronized(migrationLock) {
            val prefs = app.getSharedPreferences("messenger_migration", 0)
            if (!prefs.getBoolean("folders", false)) {
                app.getSharedPreferences("conversation_folders", 0).all.forEach { (key, value) ->
                    if (
                        key.matches(Regex("[0-9]+")) &&
                            value is String &&
                            value in listOf("Inbox", "Archive", "Trash")
                    ) {
                        val req =
                            JSONObject()
                                .put("op", "chat")
                                .put("action", "thread")
                                .put("key", key)
                                .put("folder", value)
                        val result = JSONObject(NativeCore.dispatch(root, req.toString()))
                        check(result.getBoolean("ok")) { result.optString("error") }
                    }
                }
                check(prefs.edit().putBoolean("folders", true).commit())
            }
        }

    fun requestSync(value: JSONObject): JSONObject = synchronized(migrationLock) {
        migrateOwnFolders()
        if(value.optString("action")=="thread"&&value.has("unread")&&value.has("at"))value.put("at",IncomingReceiptOrder.readTime(app,value.getLong("at")))
        value.put("op", "chat")
        val response = JSONObject(NativeCore.dispatch(root, value.toString()))
        check(response.getBoolean("ok")) {
            response.optString("error", "Could not save messenger state")
        }
        if (value.optString("action") !in setOf("snapshot", "draft", "draft_media")) epoch.update { it+1 }
        val state=response.getJSONObject("value")
        if(value.optString("action")=="arrival") {
            val thread=value.optString("key").toLongOrNull()?:0L
            if(thread>0)runCatching{MessageRepository(app).providerConversation(thread)}.getOrNull()?.let{return@synchronized registerIncomingGroup(app,it)}
        }
        state
    }

    internal fun groupIdentityLock(block:()->JSONObject):JSONObject=synchronized(migrationLock){block()}

    /** Every new unblocked SMS/MMS resurfaces Archive or Trash, even when muted. */
    fun incomingThread(thread: Long, address: String): JSONObject = synchronized(migrationLock) {
        var state = requestSync(JSONObject().put("action", "snapshot"))
        val sender = phoneKey(address)
        val blocked = state.optJSONArray("blocked")?.let { entries ->
            (0 until entries.length()).any { phoneKey(entries.getString(it)) == sender }
        } == true
        val folder = state.thread(thread).optString("folder", "Inbox").ifEmpty { "Inbox" }
        // Blocking must keep a fresh arrival out of Inbox, not just silence its alert.
        val target = if (blocked) "Trash" else "Inbox"
        if (thread > 0 && target != folder) {
            state = requestSync(JSONObject().put("action", "thread")
                .put("key", thread.toString()).put("folder", target))
        }
        if(thread>0){
            runCatching{MessageRepository(app).providerConversation(thread)}.getOrNull()?.let{state=registerIncomingGroup(app,it)}
        }
        state
    }

    suspend fun request(value: JSONObject): JSONObject =
        withContext(dispatcher) { requestSync(value) }

    suspend fun snapshot(): JSONObject = request(JSONObject().put("action", "snapshot"))

    internal suspend fun setBlocked(addresses: Collection<String>, blocked: Boolean, threads: Collection<String> = emptyList()): JSONObject {
        val peers = addresses.map(String::trim).filter(String::isNotEmpty).distinctBy(::phoneKey)
        require(peers.isNotEmpty()) { "Enter a sender to block or unblock." }
        val targets = if (blocked) threads.toSet() + MessageRepository(app).directThreadKeys(peers) else emptySet()
        val state = request(JSONObject().put("action", "blocked").put("blocked", blocked)
            .put("addresses", JSONArray(peers)).put("threads", JSONArray(targets)))
        targets.mapNotNull(String::toLongOrNull).forEach { MessageNotifications.cancel(app, it) }
        return state
    }

    fun saveLater(value: JSONObject) {
        executor.execute {
            runCatching { requestSync(value) }.onFailure { lastError.value = it.message }
        }
    }

    companion object {
        private val migrationLock = Any()
        val executor = Executors.newSingleThreadExecutor()
        val dispatcher = executor.asCoroutineDispatcher()
        val epoch = MutableStateFlow(0L)
        val lastError = MutableStateFlow<String?>(null)
    }
}

fun JSONObject.child(key: String): JSONObject = optJSONObject(key) ?: JSONObject()

fun JSONObject.keysList(): List<String> = keys().asSequence().toList()

fun JSONObject.thread(id: Long): JSONObject = child("threads").child(id.toString())

internal fun JSONObject.directTrashIds(): List<Long> {
    val groupThreads = savedGroups().map { it.thread }.toSet()
    return child("threads").keysList().mapNotNull(String::toLongOrNull)
        .filter { it > 0 && it !in groupThreads && thread(it).optString("folder") == "Trash" }
}

fun JSONObject.flags(key: String): JSONObject = child("messages").child(key)
