package app.onklav.home

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.Telephony
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import org.json.JSONObject

/** Phone formatting is normalized; opaque sender IDs never collapse to an empty number. */
fun phoneKey(s: String):String {
    val value=s.trim()
    if(value.isEmpty())return ""
    if(value.startsWith("sender:"))return value.lowercase(java.util.Locale.ROOT)
    if(value.any{it !in "0123456789+ ()-."})return "sender:"+value.lowercase(java.util.Locale.ROOT)
    return value.filter{it in '0'..'9'}.let{if(it.length==11&&it.startsWith("1"))it.drop(1)else it}
}
fun sameRecipients(a:String,b:String):Boolean {
    val left=a.split(';',',').map(::phoneKey).filter{it.isNotEmpty()}.toSet()
    val right=b.split(';',',').map(::phoneKey).filter{it.isNotEmpty()}.toSet()
    return left.isNotEmpty()&&left==right
}

data class Conversation(
    val id: Long,
    val address: String,
    val name: String,
    val snippet: String,
    val date: Long,
    val unread: Boolean,
    val count: Int = 0,
)

data class MessagePart(val uri: String, val mime: String, val name: String)

data class Message(
    val id: Long,
    val text: String,
    val date: Long,
    val sent: Boolean,
    val key: String = "sms:$id",
    val uri: String = "content://sms/$id",
    val status: String = "",
    val parts: List<MessagePart> = emptyList(),
    val sender: String = "",
    val pending: Boolean = false,
    val suspicious: Boolean = false,
    val error: String = "",
)

class MessageRepository(private val context: Context) {
    private val resolver = context.contentResolver

    fun allowed() =
        context.checkSelfPermission(Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED

    fun changes() = callbackFlow {
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    inboxCache=null; messageCache.clear()
                    trySend(Unit)
                }
                override fun onChange(selfChange:Boolean,uri:Uri?){
                    if(uri?.authority==ContactsContract.AUTHORITY){names.clear();namesWarm=false}
                    onChange(selfChange)
                }
            }
        listOf(
                Telephony.Sms.CONTENT_URI,
                Telephony.Mms.CONTENT_URI,
                ContactsContract.Contacts.CONTENT_URI,
            )
            .forEach { runCatching { resolver.registerContentObserver(it, true, observer) } }
        awaitClose { resolver.unregisterContentObserver(observer) }
    }

    fun contactName(number: String): String {
        if (number.isBlank()) return "Unknown"
        if (
            context.checkSelfPermission(Manifest.permission.READ_CONTACTS) !=
                PackageManager.PERMISSION_GRANTED
        )
            return number
        return (names[number] ?: names[phoneKey(number)])
            ?: runCatching {
                    resolver
                        .query(
                            Uri.withAppendedPath(
                                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                                Uri.encode(number),
                            ),
                            arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                            null,
                            null,
                            null,
                        )
                        ?.use { if (it.moveToFirst()) it.getString(0) else number } ?: number
                }
                .getOrDefault(number)
                .also {
                    if (names.size > 2000) names.clear()
                    names[number] = it
                }
    }

    internal suspend fun contactGivenName(number: String): String? = withContext(Dispatchers.IO) {
        if (number.isBlank() || number.any { it == ';' || it == ',' } ||
            context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED
        ) return@withContext null
        val lookup = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        val givenName = runCatching {
            val contactId = resolver.query(lookup, arrayOf(ContactsContract.PhoneLookup.CONTACT_ID), null, null, null)
                ?.use { if (it.moveToFirst()) it.getLong(0) else null } ?: return@runCatching null
            resolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME),
                "contact_id=? AND mimetype=?",
                arrayOf(contactId.toString(), ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE),
                "is_super_primary DESC, is_primary DESC",
            )?.use { cursor ->
                generateSequence { if (cursor.moveToNext()) cursor.getString(0).orEmpty().trim() else null }
                    .firstOrNull { it.isNotEmpty() }
            }
        }.getOrNull()
        givenName ?: firstNameFromDisplayName(contactName(number), number)
    }

    private fun warmCanonicalAddresses() {
        if(canonical.isEmpty())runCatching {
            resolver.query(Uri.parse("content://mms-sms/canonical-addresses"),arrayOf("_id","address"),null,null,null)?.use { c ->
                var count=0;while(c.moveToNext() && count++<4000)canonical[c.getString(0)]=c.getString(1).orEmpty()
            }
        }
    }

    /** Warm directories once, avoiding one Binder query per inbox row on cold entry. */
    private fun warmDirectories() {
        warmCanonicalAddresses()
        if(!namesWarm && context.checkSelfPermission(Manifest.permission.READ_CONTACTS)==PackageManager.PERMISSION_GRANTED)runCatching {
            resolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER,ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME),null,null,null)?.use{c->
                var count=0;while(c.moveToNext() && count++<2000){val number=c.getString(0).orEmpty();val name=c.getString(1).orEmpty();if(name.isNotBlank())names[phoneKey(number)]=name}
                namesWarm=true
            }
        }
    }

    private fun addresses(ids: String): String =
        ids.trim()
            .split(Regex("\\s+"))
            .take(256)
            .mapNotNull { id ->
                if (!id.matches(Regex("[0-9]+"))) null
                else
                    canonical[id] ?: resolver
                        .query(
                            Uri.parse("content://mms-sms/canonical-address/$id"),
                            arrayOf("address"),
                            null,
                            null,
                            null,
                        )
                        ?.use { if (it.moveToFirst()) it.getString(0) else null }?.also { if(canonical.size>4000)canonical.clear();canonical[id]=it }
            }
            .joinToString(";")

    internal suspend fun directThreadKeys(peers: Collection<String>): Set<String> = withContext(Dispatchers.IO) {
        if (!allowed()) return@withContext emptySet()
        val wanted = peers.map(::phoneKey).toSet()
        warmCanonicalAddresses()
        val matches = runCatching {
            resolver.query(
                Telephony.Threads.CONTENT_URI.buildUpon().appendQueryParameter("simple", "true").build(),
                arrayOf("_id", "recipient_ids"), "recipient_ids NOT LIKE ?", arrayOf("% %"), null,
            )?.use { cursor ->
                buildSet {
                    while (cursor.moveToNext()) {
                        currentCoroutineContext().ensureActive()
                        val recipient = cursor.getString(1).orEmpty().trim()
                        if (recipient.none(Char::isWhitespace) && phoneKey(addresses(recipient)) in wanted) {
                            cursor.getLong(0).takeIf { it > 0 }?.let { add(it.toString()) }
                        }
                    }
                }
            }
        }.getOrElse { if (it is CancellationException) throw it else null }
        if (matches != null) return@withContext matches
        // OEM fallback: verify that a real conversation exists before filing the resolved ID.
        peers.mapNotNull { peer ->
            val id = threadFor(peer)
            if (id > 0 && runCatching { providerConversation(id)?.let { sameRecipients(it.address, peer) } }.getOrNull() == true) id.toString()
            else null
        }.toSet()
    }

    /** Search all provider pages independently of the currently rendered page. */
    suspend fun searchConversations(snapshot:JSONObject,query:String):List<Conversation> {
        val matches=mutableListOf<Conversation>();var date=Long.MAX_VALUE;var id=Long.MAX_VALUE
        repeat(400){
            currentCoroutineContext().ensureActive()
            val page=conversations(256,snapshot,date,id)
            matches+=page.filter{it.name.contains(query,true)||it.address.contains(query,true)||it.snippet.contains(query,true)}
            if(page.size<256)return matches.distinctBy{it.id}
            val last=page.last();check(last.date<date||last.date==date&&last.id<id){"Phone conversation search could not advance"}
            date=last.date;id=last.id
        }
        error("Search could not finish. Narrow the query or review older conversations.")
    }

    suspend fun conversations(limit: Int = 40, snapshot: JSONObject? = null,beforeDate:Long=Long.MAX_VALUE,beforeId:Long=Long.MAX_VALUE,directOnly:Boolean=false): List<Conversation> =
        withContext(Dispatchers.IO) {
            if (!allowed()) return@withContext emptyList()
            val state = snapshot ?: ChatStore(context).snapshot()
            val excluded=if(directOnly)state.savedGroups().map{it.thread}.filter{it>0}.distinct()else emptyList()
            fun selection(idColumn:String,fallback:Boolean=false):Pair<String?,Array<String>?> {
                val terms=mutableListOf<String>();val args=mutableListOf<String>()
                if(beforeDate!=Long.MAX_VALUE){if(fallback){terms+="date<=?";args+=beforeDate.toString()}else{terms+="(date<? OR (date=? AND _id<?))";args+=listOf(beforeDate.toString(),beforeDate.toString(),beforeId.toString())}}
                if(excluded.isNotEmpty()){terms+="$idColumn NOT IN (${excluded.joinToString{"?"}})";args+=excluded.map{it.toString()}}
                return terms.takeIf{it.isNotEmpty()}?.joinToString(" AND ") to args.takeIf{it.isNotEmpty()}?.toTypedArray()
            }
            val cached=inboxCache?.takeIf{!directOnly&&beforeDate==Long.MAX_VALUE && android.os.SystemClock.elapsedRealtime()-it.first<30000 && limit<=inboxLimit}
            val result = cached?.second?.take(limit)?.toMutableList() ?: mutableListOf<Conversation>()
            if(cached==null) { warmDirectories();try {
                resolver
                    .query(
                        Telephony.Threads.CONTENT_URI.buildUpon()
                            .appendQueryParameter("simple", "true")
                            .build(),
                        arrayOf("_id", "date", "snippet", "read", "recipient_ids", "message_count"),
                        selection("_id").first,selection("_id").second,
                        "date DESC, _id DESC",
                    )
                    ?.use { c ->
                        while (c.moveToNext() && result.size < limit) {
                            val peers = addresses(c.getString(4).orEmpty())
                            result +=
                                Conversation(
                                    c.getLong(0),
                                    peers,
                                    peers
                                        .split(';')
                                        .filter { it.isNotBlank() }
                                        .joinToString(", ") { contactName(it) }
                                        .ifBlank { "Unknown" },
                                    c.getString(2).orEmpty(),
                                    c.getLong(1),
                                    c.getInt(3) == 0,
                                    c.getInt(5),
                                )
                        }
                    }
            } catch (e: Exception) {
                // Some OEMs omit the simple-threads endpoint. Preserve SMS access via a bounded
                // scan.
                val seen = mutableSetOf<Long>()
                resolver
                    .query(
                        Telephony.Sms.CONTENT_URI,
                        arrayOf("thread_id", "address", "body", "date", "read"),
                        selection("thread_id",true).first,selection("thread_id",true).second,
                        "date DESC",
                    )
                    ?.use { c ->
                        var scanned = 0
                        while (c.moveToNext() && result.size < limit && scanned++ < 100000) {
                            val id = c.getLong(0)
                            if (seen.add(id)) {
                                val address = c.getString(1).orEmpty()
                                result +=
                                    Conversation(
                                        id,
                                        address,
                                        contactName(address),
                                        c.getString(2).orEmpty(),
                                        c.getLong(3),
                                        c.getInt(4) == 0,
                                    )
                            }
                        }
                    }
                if (result.isEmpty()) throw e
            }
            if(!directOnly&&beforeDate==Long.MAX_VALUE){inboxLimit=limit;inboxCache=android.os.SystemClock.elapsedRealtime() to result.toList()}
            }
            result
                .map { row -> conversationReadState(row,state) }
                .sortedWith(
                    compareByDescending<Conversation> { state.thread(it.id).optBoolean("pinned") }
                        .thenByDescending { it.date }
                )
        }

    internal fun providerConversation(id:Long):Conversation? {
        if (id <= 0 || !allowed()) return null
        warmDirectories()
        try {
            resolver.query(
                Telephony.Threads.CONTENT_URI.buildUpon().appendQueryParameter("simple", "true").build(),
                arrayOf("_id", "date", "snippet", "read", "recipient_ids", "message_count"),
                "_id=?", arrayOf(id.toString()), null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val peers = addresses(cursor.getString(4).orEmpty())
                    return Conversation(cursor.getLong(0), peers,
                        peers.split(';').filter { it.isNotBlank() }.joinToString(", ") { contactName(it) }.ifBlank { "Unknown" },
                        cursor.getString(2).orEmpty(), cursor.getLong(1), cursor.getInt(3) == 0, cursor.getInt(5))
                }
            }
        } catch (_: Exception) {
            // OEM fallback stays constrained to the requested thread.
            resolver.query(Telephony.Sms.CONTENT_URI,
                arrayOf("thread_id", "address", "body", "date", "read"),
                "thread_id=?", arrayOf(id.toString()), "date DESC")?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val peer = cursor.getString(1).orEmpty()
                    return Conversation(id, peer, contactName(peer), cursor.getString(2).orEmpty(),
                        cursor.getLong(3), cursor.getInt(4) == 0)
                }
            }
        }
        return null
    }

    suspend fun conversation(id:Long):Conversation?=withContext(Dispatchers.IO){providerConversation(id)?.let{conversationReadState(it,ChatStore(context).snapshot())}}
    suspend fun reconcileGroups():JSONObject=withContext(Dispatchers.IO){
        val store=ChatStore(context);var state=store.snapshot()
        if(!allowed())return@withContext state
        val known=state.savedGroups().map{it.thread}.toMutableSet();val ids=mutableListOf<Long>()
        try{resolver.query(Telephony.Threads.CONTENT_URI.buildUpon().appendQueryParameter("simple","true").build(),arrayOf("_id","recipient_ids"),"recipient_ids LIKE ?",arrayOf("% %"),"date DESC")?.use{rows->
            while(rows.moveToNext()&&ids.size<500){if(rows.getString(1).orEmpty().trim().split(Regex("\\s+")).size>1)ids+=rows.getLong(0)}
        }}catch(_:Exception){ids+=conversations(5000,state).filter{incomingGroupMembers(context,it.address).size>1}.map{it.id}}
        for(id in ids.distinct()){kotlinx.coroutines.currentCoroutineContext().ensureActive();if(id !in known){providerConversation(id)?.let{state=store.registerIncomingGroup(context,it)};known+=id}}
        state
    }

    suspend fun threadFor(address: String): Long =
        withContext(Dispatchers.IO) {
            runCatching {
                    Telephony.Threads.getOrCreateThreadId(
                        context,
                        address.split(';').filter { it.isNotBlank() }.toSet(),
                    )
                }
                .getOrDefault(0)
        }

    private fun parts(id: Long): Pair<String, List<MessagePart>> {
        val text = StringBuilder()
        val parts = mutableListOf<MessagePart>()
        resolver
            .query(
                Uri.parse("content://mms/$id/part"),
                arrayOf("_id", "ct", "text", "name", "cl"),
                null,
                null,
                "seq ASC",
            )
            ?.use { c ->
                var count = 0
                while (c.moveToNext() && count++ < 64) {
                    val type = c.getString(1).orEmpty().lowercase()
                    val uri = "content://mms/part/${c.getLong(0)}"
                    when (type) {
                        "application/smil" -> {}
                        "text/plain" -> {
                            val value =
                                c.getString(2)
                                    ?: runCatching {
                                            resolver.openInputStream(Uri.parse(uri))?.use {
                                                String(readBounded(it, 100000), Charsets.UTF_8)
                                            }
                                        }
                                        .getOrNull()
                                        .orEmpty()
                            if (text.isNotEmpty() && value.isNotBlank()) text.append('\n')
                            text.append(value.take(10000))
                        }
                        else ->
                            parts +=
                                MessagePart(
                                    uri,
                                    type.ifBlank { "application/octet-stream" },
                                    c.getString(3) ?: c.getString(4) ?: "Attachment",
                                )
                    }
                }
            }
        return text.toString() to parts
    }

    private fun mmsSender(id:Long):String = resolver.query(Uri.parse("content://mms/$id/addr"),arrayOf("address","type"),"type=?",arrayOf("137"),null)?.use{c->if(c.moveToFirst())c.getString(0).orEmpty().takeUnless{it=="insert-address-token"}.orEmpty()else ""}.orEmpty()

    /** Resolve only requested envelope parents; duplicate targets are retained for Rust to reject ambiguity. */
    suspend fun groupParents(thread:Long,targets:List<String>):List<Message> = withContext(Dispatchers.IO) {
        if(!allowed()||thread<=0)return@withContext emptyList()
        require(targets.size<=64&&targets.all{it.matches(Regex("[a-f0-9]{32}"))})
        val found=linkedMapOf<String,Message>()
        for(target in targets){
            coroutineContext.ensureActive()
            val patterns=arrayOf("/\"0\"1:P:$target\n%","/\"0\"1:C:$target:%")
            resolver.query(Telephony.Sms.CONTENT_URI,arrayOf("_id","body","date","type","address"),"thread_id=? AND type IN (1,2) AND (body LIKE ? OR body LIKE ?)",arrayOf(thread.toString(),*patterns),null)?.use{c->
                var count=0;while(c.moveToNext()&&count++<2){val m=Message(c.getLong(0),c.getString(1).orEmpty(),c.getLong(2),c.getInt(3)!=1,status=if(c.getInt(3)==2)"Sent"else "",sender=c.getString(4).orEmpty());found[m.key]=m}
            }
            resolver.query(Uri.parse("content://mms/part"),arrayOf("mid"),"ct=? AND (text LIKE ? OR text LIKE ?)",arrayOf("text/plain",*patterns),null)?.use{partsCursor->
                var scanned=0;var count=0
                while(partsCursor.moveToNext()&&scanned++<512&&count<2){
                    coroutineContext.ensureActive();val id=partsCursor.getLong(0)
                    resolver.query(Uri.parse("content://mms/$id"),arrayOf("_id","date","msg_box"),"thread_id=? AND msg_box IN (1,2)",arrayOf(thread.toString()),null)?.use{c->if(c.moveToFirst()){
                        val body=parts(id);val m=Message(id,body.first,c.getLong(1)*1000,c.getInt(2)!=1,key="mms:$id",uri="content://mms/$id",status=if(c.getInt(2)==2)"Sent"else "",parts=body.second,sender=mmsSender(id));found[m.key]=m;count++
                    }}
                }
            }
        }
        found.values.toList()
    }

    data class HistorySearch(val threads:Set<Long>,val truncated:Boolean)
    /** Provider-side body search includes history outside the currently displayed snippets. */
    suspend fun searchThreads(query:String,candidates:Set<Long>):HistorySearch=withContext(Dispatchers.IO){
        if(!allowed()||query.isBlank()||candidates.isEmpty())return@withContext HistorySearch(emptySet(),false)
        val pattern="%"+query.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%"
        val result=mutableSetOf<Long>();var count=0;var truncated=false
        resolver.query(Telephony.Sms.CONTENT_URI,arrayOf("thread_id"),"body LIKE ? ESCAPE '\\'",arrayOf(pattern),null)?.use{c->
            while(c.moveToNext()){coroutineContext.ensureActive();if(count++>=20000){truncated=true;break};val id=c.getLong(0);if(id in candidates)result+=id}
        }
        val visited=mutableSetOf<Long>()
        resolver.query(Uri.parse("content://mms/part"),arrayOf("mid"),"ct=? AND text LIKE ? ESCAPE '\\'",arrayOf("text/plain",pattern),null)?.use{c->
            while(c.moveToNext()){coroutineContext.ensureActive();if(count++>=20000){truncated=true;break};val mid=c.getLong(0);if(!visited.add(mid))continue
                resolver.query(Uri.parse("content://mms/$mid"),arrayOf("thread_id"),null,null,null)?.use{m->if(m.moveToFirst()){val id=m.getLong(0);if(id in candidates)result+=id}}
            }
        }
        HistorySearch(result,truncated)
    }

    /** Bounded keyset media page: at most 48 MMS envelopes or 24 media messages. */
    internal suspend fun mediaPage(thread:Long,snapshot:JSONObject,before:MediaCursor?=null):MediaPage=withContext(Dispatchers.IO){
        check(allowed()){ "Allow SMS access in Setup to read conversation media" }
        if(thread<=0)return@withContext MediaPage(emptyList(),null,false)
        val result=mutableListOf<Message>();var next:MediaCursor?=null;var more=false
        val outgoing=snapshot.child("outgoing");val tokens=outgoing.keysList().associateBy{outgoing.child(it).optString("provider_uri")}
        val selection="thread_id=? AND msg_box<>3"+if(before!=null)" AND (date<? OR (date=? AND _id<?))"else""
        val args=if(before==null)arrayOf(thread.toString())else arrayOf(thread.toString(),(before.date/1000).toString(),(before.date/1000).toString(),before.id.toString())
        val cursor=resolver.query(Telephony.Mms.CONTENT_URI,arrayOf("_id","date","msg_box"),selection,args,"date DESC, _id DESC")?:error("Conversation media could not be read")
        cursor.use{c->var scanned=0;while(c.moveToNext()){
            coroutineContext.ensureActive();val id=c.getLong(0);val date=c.getLong(1)*1000;next=MediaCursor(date,id);scanned++
            val media=parts(id).second.filter{it.mime.startsWith("image/")||it.mime.startsWith("audio/")||it.mime.startsWith("video/")}
            if(media.isNotEmpty()){val uri="content://mms/$id";val token=tokens[uri];result+=Message(id,"",date,c.getInt(2)!=1,key=token?.let{"out:$it"}?:"mms:$id",uri=uri,parts=media)}
            if(scanned>=48||result.size>=24){more=!c.isLast;break}
        }}
        MediaPage(result,next,more)
    }
    suspend fun mediaMessages(thread:Long,snapshot:JSONObject):List<Message> = mediaPage(thread,snapshot).messages

    /** Search provider bodies, including history outside the visible feed. No attachment decoding. */
    internal suspend fun findConversationMessages(thread:Long,query:String):ConversationSearchResult=withContext(Dispatchers.IO){
        check(allowed()){ "Allow SMS access in Setup to search messages" }
        if(thread<=0 || query.isBlank())return@withContext ConversationSearchResult(emptyList())
        val pattern="%"+query.trim().take(200).replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%"
        val hits=linkedMapOf<String,ConversationSearchHit>();var limited=false
        resolver.query(Telephony.Sms.CONTENT_URI,arrayOf("_id","body","date"),"thread_id=? AND type<>3 AND body LIKE ? ESCAPE '\\'",arrayOf(thread.toString(),pattern),"date DESC, _id DESC")?.use{c->
            var count=0;while(c.moveToNext()){
                coroutineContext.ensureActive();if(count++>=200){limited=true;break}
                val id=c.getLong(0);hits["sms:$id"]=ConversationSearchHit("sms:$id",c.getLong(2),c.getString(1).orEmpty())
            }
        }
        val dates=linkedMapOf<Long,Long>()
        resolver.query(Telephony.Mms.CONTENT_URI,arrayOf("_id","date"),"thread_id=? AND msg_box<>3",arrayOf(thread.toString()),"date DESC, _id DESC")?.use{c->
            while(c.moveToNext()){coroutineContext.ensureActive();if(dates.size>=20000){limited=true;break};dates[c.getLong(0)]=c.getLong(1)*1000}
        }
        var found=0
        for(batch in dates.keys.chunked(200)){
            coroutineContext.ensureActive()
            val slots=batch.joinToString(","){"?"}
            val args=(batch.map{it.toString()}+listOf("text/plain",pattern)).toTypedArray()
            resolver.query(Uri.parse("content://mms/part"),arrayOf("mid","text"),"mid IN ($slots) AND ct=? AND text LIKE ? ESCAPE '\\'",args,null)?.use{c->
                while(c.moveToNext()){coroutineContext.ensureActive();val id=c.getLong(0);val key="mms:$id";if(key !in hits){found++;hits[key]=ConversationSearchHit(key,dates.getValue(id),c.getString(1).orEmpty())}}
            }
            if(found>=200){limited=true;break}
        }
        val ordered=hits.values.sortedByDescending{it.date}
        ConversationSearchResult(ordered.take(200),limited||ordered.size>200)
    }

    suspend fun messages(
        thread: Long,
        limit: Int = 100,
        snapshot: JSONObject? = null,
        address: String = "",
        beforeDate:Long=Long.MAX_VALUE,
        beforeSmsId:Long=Long.MAX_VALUE,
        beforeMmsId:Long=Long.MAX_VALUE,
    ): List<Message> =
        withContext(Dispatchers.IO) {
            val state = snapshot ?: ChatStore(context).snapshot()
            val cacheKey="$thread:$limit:$beforeDate:$beforeSmsId:$beforeMmsId"
            val cached=if(allowed())messageCache[cacheKey]?.takeIf{android.os.SystemClock.elapsedRealtime()-it.first<30000} else null
            val result = cached?.second?.toMutableList() ?: mutableListOf<Message>()
            if (cached==null && allowed() && thread > 0) {
                resolver
                    .query(
                        Telephony.Sms.CONTENT_URI,
                        arrayOf("_id", "body", "date", "type", "status", "address"),
                        "thread_id=? AND type<>3 AND "+if(beforeSmsId==Long.MAX_VALUE)"date<=?"else"(date<? OR (date=? AND _id<?))",
                        if(beforeSmsId==Long.MAX_VALUE)arrayOf(thread.toString(),beforeDate.toString())else arrayOf(thread.toString(),beforeDate.toString(),beforeDate.toString(),beforeSmsId.toString()),
                        "date DESC, _id DESC",
                    )
                    ?.use { c ->
                        while (c.moveToNext() && result.size < limit) {
                            val type = c.getInt(3)
                            val status =
                                when {
                                    type == 5 -> "Failed"
                                    type == 6 -> "Queued"
                                    type == 4 -> "Sending"
                                    type == 2 && c.getInt(4) == 0 -> "Delivered"
                                    type == 2 -> "Sent"
                                    else -> ""
                                }
                            result +=
                                Message(
                                    c.getLong(0),
                                    c.getString(1).orEmpty(),
                                    c.getLong(2),
                                    type != 1,
                                    status = status,
                                    sender = c.getString(5).orEmpty(),
                                )
                        }
                    }
                resolver
                    .query(
                        Telephony.Mms.CONTENT_URI,
                        arrayOf("_id", "date", "msg_box", "sub", "m_type"),
                        "thread_id=? AND msg_box<>3 AND "+if(beforeMmsId==Long.MAX_VALUE)"date<=?"else"(date<? OR (date=? AND _id<?))",
                        if(beforeMmsId==Long.MAX_VALUE)arrayOf(thread.toString(),(beforeDate/1000).toString())else arrayOf(thread.toString(),(beforeDate/1000).toString(),(beforeDate/1000).toString(),beforeMmsId.toString()),
                        "date DESC, _id DESC",
                    )
                    ?.use { c ->
                        var count = 0
                        while (c.moveToNext() && count++ < limit) {
                            val id = c.getLong(0)
                            val type = c.getInt(2)
                            val body = parts(id)
                            result +=
                                Message(
                                    id,
                                    body.first.ifBlank { c.getString(3).orEmpty() },
                                    c.getLong(1) * 1000,
                                    type != 1,
                                    key = "mms:$id",
                                    uri = "content://mms/$id",
                                    status =
                                        when (type) {
                                            2 -> "Sent"
                                            4 -> "Sending"
                                            5 -> "Failed"
                                            else -> ""
                                        },
                                    parts = body.second,
                                    pending = c.getInt(4) == 130,
                                    sender = mmsSender(id),
                                )
                        }
                    }
            }
            if(cached==null && allowed() && thread>0){if(messageCache.size>=12)messageCache.clear();messageCache[cacheKey]=android.os.SystemClock.elapsedRealtime() to result.toList()}
            val outgoing = state.child("outgoing")
            val consumed = mutableSetOf<String>()
            val combined =
                result
                    .sortedBy { it.date }
                    .map { message ->
                        val token =
                            outgoing
                                .keysList()
                                .filterNot { it in consumed }
                                .filter { key ->
                                    val o = outgoing.child(key)
                                    val uri = o.optString("provider_uri")
                                    uri == message.uri ||
                                        (uri.isEmpty() &&
                                            o.optString("kind") != "mms" &&
                                            message.sent &&
                                            sameRecipients(
                                                o.optString("address"),
                                                message.sender,
                                            ) &&
                                            o.optString("text") == message.text &&
                                            kotlin.math.abs(o.optLong("started") - message.date) <
                                                60000)
                                }
                                .minByOrNull {
                                    kotlin.math.abs(
                                        outgoing.child(it).optLong("started") - message.date
                                    )
                                }
                        if (token == null) message
                        else {
                            consumed += token
                            message.copy(
                                key = "out:$token",
                                status = outgoing.child(token).optString("status"),
                                error = MessagingTransport.smsDisplayError(outgoing.child(token)),
                            )
                        }
                    }
                    .toMutableList()
            if (address.isNotBlank())
                for (token in outgoing.keysList().filterNot { it in consumed }) {
                    val o = outgoing.child(token)
                    if (
                        sameRecipients(address, o.optString("address")) &&
                            o.optString("provider_uri").isEmpty()
                    )
                        combined +=
                            Message(
                                -1,
                                o.optString("text"),
                                o.optLong("started"),
                                true,
                                key = "out:$token",
                                uri = "",
                                status = o.optString("status"),
                                error = MessagingTransport.smsDisplayError(o),
                            )
                }
            combined.sortedWith(compareBy<Message> { it.date }.thenBy { it.key }).takeLast(limit).map { m ->
                if(m.sent) m else m.copy(suspicious=SpamPolicy.assess(context,m.sender.ifBlank { address },m.text))
            }
        }

    suspend fun markRead(thread: Long, unread: Boolean = false) =
        withContext(Dispatchers.IO) {
            require(thread > 0) { "Choose a conversation" }
            val provider = MessagingTransport.role(context)
            fun access() {
                check(MessagingTransport.role(context) && context.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
                    "Messaging access changed. Check Setup and try again."
                }
            }
            if (provider) {
                val values = ContentValues().apply { put("read", if(unread)0 else 1); put("seen", if(unread)0 else 1) }
                for (uri in listOf(Telephony.Sms.CONTENT_URI, Telephony.Mms.CONTENT_URI)) {
                    access()
                    resolver.update(uri, values, "thread_id=? AND (read<>? OR seen<>?)",
                        arrayOf(thread.toString(), if(unread)"0"else"1", if(unread)"0"else"1"))
                }
                access()
            }
            // Preserve the previous local state if a provider write or live access check failed.
            ChatStore(context).request(JSONObject().put("action", "thread").put("key", thread.toString())
                .put("unread", unread).put("at", System.currentTimeMillis()))
            if (!unread) MessageNotifications.cancel(context, thread)
        }

    /** Recheck durable folder/lock state, and never bypass the host's phone-write gate. */
    suspend fun emptyTrash(ids:List<Long>) = withContext(Dispatchers.IO) {
        check(MessagingTransport.role(context)) { "Complete messaging Setup before deleting phone messages" }
        val store=ChatStore(context)
        for(id in ids.distinct()) {
            val state=store.snapshot()
            if(state.thread(id).optString("folder")!="Trash") continue
            val loaded=messages(id,10001,state)
            check(loaded.size<=10000){"This conversation is too large to empty in one operation"}
            for(message in loaded) {
                val current=store.snapshot()
                if(current.thread(id).optString("folder")!="Trash") break
                if(!current.messageFlags(message).optBoolean("locked") && message.uri.matches(Regex("content://(sms|mms)/[0-9]+"))) delete(message,current)
            }
        }
    }

    suspend fun delete(message: Message, state: JSONObject) =
        withContext(Dispatchers.IO) {
            check(!state.messageFlags(message).optBoolean("locked")) {
                "Unlock this message before deleting it"
            }
            check(MessagingTransport.role(context)) {
                "Complete Setup and select OnKlav as your default messaging app before deleting phone messages"
            }
            require(message.uri.matches(Regex("content://(sms|mms)/[0-9]+"))) {
                "This pending message cannot be deleted yet"
            }
            check(resolver.delete(Uri.parse(message.uri), null, null) > 0) {
                "This message was already removed"
            }
            ChatStore(context)
                .request(JSONObject().put("action", "remove_message").put("key", message.key))
        }

    suspend fun send(address: String, body: String) =
        withContext(ChatStore.dispatcher) { MessagingTransport.sendSms(context, address, body) }

    companion object {
        private val names = ConcurrentHashMap<String, String>()
        @Volatile private var namesWarm=false
        private val canonical=ConcurrentHashMap<String,String>()
        @Volatile private var inboxCache:Pair<Long,List<Conversation>>?=null
        @Volatile private var inboxLimit=0
        private val messageCache=ConcurrentHashMap<String,Pair<Long,List<Message>>>()

        fun readBounded(input: java.io.InputStream, maximum: Int): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                require(out.size() + n <= maximum) { "This file exceeds the size limit" }
                out.write(buffer, 0, n)
            }
            return out.toByteArray()
        }
    }
}
