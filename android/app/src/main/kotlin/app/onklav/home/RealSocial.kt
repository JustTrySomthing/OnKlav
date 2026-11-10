package app.onklav.home

import android.app.*
import android.content.*
import android.net.Uri
import android.telephony.SmsManager
import android.util.AtomicFile
import android.util.Base64
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Private, one-recipient carrier channel. A friendship token is bound to the carrier sender. */
internal object RealSocial {
    const val APP = "app.onklav.social"
    private const val PREFIX = "Real Social update. Open OnKlav to view it.\n!OKS1|"
    const val CLEANUP_JOB = 4192
    fun cleanup(c: Context) { if(installed(c)) prune(c) }
    fun clear(c: Context) = synchronized(lock) {
        val folder=File(c.noBackupFilesDir,"real-social")
        check(!folder.exists() || folder.deleteRecursively())
        c.getSystemService(android.app.job.JobScheduler::class.java).cancel(CLEANUP_JOB)
        c.getSystemService(NotificationManager::class.java).cancel(914)
        epoch.value++
    }
    fun scheduleCleanup(c: Context) {
        val jobs = c.getSystemService(android.app.job.JobScheduler::class.java)
        if (jobs.getPendingJob(CLEANUP_JOB) == null) jobs.schedule(
            android.app.job.JobInfo.Builder(CLEANUP_JOB, ComponentName(c, TransportRecoveryService::class.java))
                .setPeriodic(DAY).setPersisted(true).build())
    }
    private const val DAY = 86_400_000L
    private const val HANDSHAKE = 65_000L
    private const val MAX_JOBS = 384
    private const val CONTROL_RESERVE = 48
    private const val MAX_WIRE = 1800
    private const val MAX_REPLIES = 128
    private const val MAX_MEDIA = 1_000_000L
    private const val MEDIA_STORE = 96L*1024*1024
    private val key = Regex("[a-f0-9]{32}")
    private val lock = Any()
    val epoch = MutableStateFlow(0L)

    fun installed(c: Context) = AppSdk.isSocialInstalled(c)
    private fun dir(c: Context) = File(c.noBackupFilesDir, "real-social").apply { check(isDirectory || mkdirs()) }
    fun media(c: Context, id: String): File {
        require(key.matches(id))
        return File(dir(c), "$id.media")
    }
    private fun table(state: JSONObject, name: String) =
        state.optJSONObject(name) ?: JSONObject().also { state.put(name, it) }
    private fun values(o: JSONObject) = o.keys().asSequence().map { o.getJSONObject(it) }.toList()
    private fun <T> state(c: Context, edit: (JSONObject) -> T): T = synchronized(lock) {
        val file = AtomicFile(File(dir(c), "state.json"))
        val root = try {
            file.openRead().use {
                check(file.baseFile.length() <= 4_000_000) { "Real Social storage is unavailable" }
                JSONObject(it.bufferedReader().readText())
            }
        } catch (e: java.io.FileNotFoundException) {
            if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()) throw e
            JSONObject()
        }
        val before = root.toString()
        val result = edit(root)
        val after = root.toString()
        if (before != after) {
            val bytes = after.toByteArray()
            check(bytes.size <= 4_000_000) { "Real Social storage is full" }
            val out = file.startWrite()
            try { out.write(bytes); file.finishWrite(out) } catch (e: Exception) { file.failWrite(out); throw e }
            epoch.value++
        }
        result
    }

    private fun id() = MessageTransferProvider.id()
    private fun now() = System.currentTimeMillis()
    private fun displayName(c: Context) = c.getSharedPreferences("profile",0).getString("name","").orEmpty().take(60)
    private fun invitation(c: Context, friend: JSONObject) =
        wire(packet("invite",friend.getString("token")).put("name",displayName(c)))
    private fun packet(kind: String, token: String) =
        JSONObject().put("kind", kind).put("token", token).put("id", id()).put("at", now())
    internal fun wire(row: JSONObject) = PREFIX + Base64.encodeToString(row.toString().toByteArray(), Base64.NO_WRAP or Base64.URL_SAFE)
    internal fun decode(text: String): JSONObject? {
        if (!text.startsWith(PREFIX) || text.length > MAX_WIRE) return null
        return runCatching {
            JSONObject(Base64.decode(text.removePrefix(PREFIX), Base64.URL_SAFE).decodeToString()).also {
                require(key.matches(it.getString("id")) && key.matches(it.getString("token")))
                require(it.getString("kind") in setOf("invite","accept","remove","post","comment","reaction"))
            }
        }.getOrNull()
    }
    private fun queue(s: JSONObject, friend: JSONObject, body: String, attachment: String = "", control: Boolean = false) {
        val jobs = table(s, "jobs")
        val limit = MAX_JOBS + if (control) CONTROL_RESERVE else 0
        check(jobs.length() < limit) { "Too many pending Social updates. Check delivery and try again." }
        require(body.length <= MAX_WIRE) { "Shorten your text or link to fit a carrier update." }
        val job = id()
        jobs.put(job, JSONObject().put("id", job).put("peer", friend.getString("peer"))
            .put("sub", friend.optInt("sub", -1)).put("wire", body).put("media", attachment).put("at", now()))
    }
    private fun kick(c: Context) = TransportRecovery.schedule(c)
    fun snapshot(c: Context): JSONObject {
        prune(c)
        return state(c) { JSONObject(it.toString()) }
    }
    fun invite(c: Context, peer: String, name: String) {
        FriendGames.policy(c, peer)
        val verified = phoneKey(peer) in FriendGames.verifiedPeers(c)
        val sub = MessagingTransport.manager(c).subscriptionId
        state(c) { s ->
            val friends = table(s, "friends")
            val number = phoneKey(peer)
            check(!friends.has(number) && !table(s, "blocked").has(number)) { "This person is already listed or blocked." }
            check(friends.length() < 48) { "Real Social supports up to 48 friends." }
            val f = JSONObject().put("peer", peer).put("name", name.take(100)).put("token", id())
                .put("sub", sub).put("status", if (verified) "fast" else "checking").put("at", 0)
            friends.put(number, f)
            queue(s, f, if (verified) invitation(c,f) else FriendGameWire.QUESTION)
        }
        kick(c)
    }
    fun observeVerification(c: Context, peer: String, body: String) {
        if (!FriendGameWire.appResponse(body)) return
        if (!installed(c) || runCatching { FriendGames.policy(c, peer) }.isFailure) return
        val confirmed = state(c) { s ->
            val f = table(s, "friends").optJSONObject(phoneKey(peer)) ?: return@state false
            val started = f.optLong("at")
            if (f.optString("status") != "checking" || started <= 0 || now() - started !in 0..HANDSHAKE) return@state false
            if (table(s,"jobs").length() < MAX_JOBS) {
                queue(s, f, invitation(c,f))
                f.put("status", "invited")
            } else f.put("status", "verified_waiting")
            f.put("at", now())
            true
        }
        if (confirmed) { kick(c); FriendGames.recordVerified(c, peer) }
    }
    private fun postPacket(token: String, post: JSONObject): JSONObject =
        packet("post", token).put("id", post.getString("id")).put("at", post.getLong("at"))
            .put("name", post.optString("name").take(60)).put("text", post.optString("text"))
            .put("link", post.optString("link")).put("mime", post.optString("mime"))

    private fun history(s: JSONObject, f: JSONObject) {
        val posts = values(table(s, "posts")).filter {
            it.optString("peer").isEmpty() && now()-it.optLong("at") in 0..30*DAY
        }.sortedByDescending { it.optLong("at") }.asSequence().mapNotNull { post ->
            val body = wire(postPacket(f.getString("token"),post))
            if (body.length > MAX_WIRE) null
            else body to if(post.optString("mime").isEmpty()) "" else post.getString("id")
        }.take(3).toList()
        if (posts.isNotEmpty() && table(s,"jobs").length() + posts.size > MAX_JOBS) {
            f.put("history_waiting", true)
            return
        }
        posts.asReversed().forEach { (body, attachment) -> queue(s,f,body,attachment) }
        f.remove("history_waiting")
    }

    fun accept(c: Context, number: String) {
        val peer = state(c) { s ->
            val f = table(s, "friends").getJSONObject(number)
            check(f.optString("status") == "request")
            f.put("status", "friend")
            queue(s, f, wire(packet("accept", f.getString("token")).put("name",displayName(c))), control = true)
            history(s, f)
            f.getString("peer")
        }
        FriendGames.recordVerified(c, peer)
        kick(c)
    }
    fun remove(c: Context, number: String, block: Boolean) {
        state(c) { s ->
            val f = table(s, "friends").optJSONObject(number) ?: return@state
            cancelQueued(s, number)
            if(f.optString("status") in setOf("friend","request","invited","fast"))
                queue(s, f, wire(packet("remove", f.getString("token"))), control = true)
            table(s, "friends").remove(number)
            table(s, "retired").put(f.getString("token"),now())
            if (block) {
                val blocked = table(s, "blocked")
                check(blocked.length() < 512) { "Blocked requests list is full." }
                blocked.put(number, true)
            }
            removePosts(s, number)
        }
        prune(c)
        kick(c)
    }
    private fun cancelQueued(s: JSONObject, number: String) {
        val jobs = table(s, "jobs")
        values(jobs).filter { phoneKey(it.getString("peer")) == number }.forEach { jobs.remove(it.getString("id")) }
    }

    private fun removePosts(s: JSONObject, number: String) {
        val posts = table(s, "posts")
        values(posts).filter { it.optString("peer") == number }.forEach { posts.remove(it.getString("id")) }
        values(posts).forEach { post ->
            val replies = table(post, "replies")
            values(replies).filter { it.optString("peer") == number }.forEach { replies.remove(it.getString("id")) }
        }
    }
    fun mute(c: Context, number: String? = null) {
        state(c) { s ->
            val target = number?.let { table(s, "friends").getJSONObject(it) } ?: s
            target.put("muted", !target.optBoolean("muted"))
        }
        if(number==null)c.getSystemService(NotificationManager::class.java).cancel(914)
    }
    fun post(c: Context, name: String, text: String, link: String, attachment: File?, mime: String) {
        require(text.toByteArray().size <= 650 && link.length <= 350) { "Shorten your text or link to fit a carrier update." }
        require(text.isNotBlank() || link.isNotBlank() || attachment != null)
        if (link.isNotEmpty()) require(safeLink(link))
        val post = JSONObject().put("id", id()).put("at", now()).put("name", name.take(60))
            .put("text", text).put("link", link).put("mime", mime)
        val postId = post.getString("id")
        // Validate the actual encoded packet even before the first friend is added.
        require(wire(postPacket("0".repeat(32),post)).length <= MAX_WIRE) {
            "Shorten your text or link to fit a carrier update."
        }
        if (attachment != null) require(validMime(mime) && attachment.length() in 1..MAX_MEDIA)
        synchronized(lock) {
            try {
                if (attachment != null) {
                    check(mediaUsed(c)+attachment.length() <= MEDIA_STORE) { "Real Social attachment storage is full." }
                    attachment.copyTo(media(c, postId))
                }
                state(c) { s ->
                    val posts = table(s, "posts")
                    check(posts.length() < 512) { "Real Social post storage is full." }
                    values(table(s, "friends")).filter { it.optString("status") == "friend" }.forEach { f ->
                        queue(s, f, wire(postPacket(f.getString("token"), post)), if (attachment == null) "" else postId)
                    }
                    posts.put(postId, post)
                }
            } catch (e: Exception) { media(c, postId).delete(); throw e }
        }
        kick(c)
    }
    fun respond(c: Context, postId: String, target: String, text: String, reaction: Boolean) {
        require(text.isNotBlank() && text.toByteArray().size <= 650)
        if (reaction) require(text in reactions)
        state(c) { s ->
            val post = table(s, "posts").getJSONObject(postId)
            val replies = table(post, "replies")
            val reply = replies.optJSONObject(target)
            val peer = post.optString("peer").ifBlank { reply?.optString("peer").orEmpty().ifBlank { reply?.optString("to").orEmpty() } }
            val recipients = values(table(s, "friends")).filter { it.optString("status") == "friend" &&
                (peer.isBlank() || phoneKey(it.optString("peer")) == peer) }
            check(recipients.isNotEmpty()) { "There is no connected friend for this reply." }
            require(target.isEmpty() || reply?.optString("kind") == "comment") { "That comment is no longer available." }
            val eventId = id()
            recipients.forEach { f ->
                val row = packet(if (reaction) "reaction" else "comment", f.getString("token"))
                    .put("id", eventId).put("post", postId).put("target", target).put("text", text)
                val body = wire(row)
                if (f == recipients.first()) {
                    check(addReply(post, row.put("to",peer))) { "This post has reached its reply limit." }
                }
                queue(s, f, body)
            }
        }
        kick(c)
    }
    private fun addReply(post: JSONObject, row: JSONObject): Boolean {
        val replies = table(post, "replies")
        if (row.optString("kind") == "reaction") {
            values(replies).filter {
                it.optString("kind") == "reaction" && it.optString("peer") == row.optString("peer") &&
                    it.optString("target") == row.optString("target")
            }.forEach { replies.remove(it.getString("id")) }
        }
        if (replies.length() >= MAX_REPLIES) return false
        replies.put(row.getString("id"), row)
        return true
    }

    fun receive(c: Context, peer: String, sub: Int, text: String, file: File? = null, mime: String = ""): Boolean {
        if (!text.startsWith(PREFIX)) return false
        val p = decode(text) ?: return true
        if (!installed(c) || runCatching { FriendGames.policy(c, peer, sub) }.isFailure) return true
        scheduleCleanup(c)
        val number = phoneKey(peer)
        val kind = p.getString("kind")
        if (file != null && kind != "post") return true
        val token = p.getString("token")
        val postId = p.getString("id")
        var verified = false
        val alert = synchronized(lock) {
            var copied = false
            try {
                state(c) { s ->
                    if (table(s, "blocked").has(number)) return@state false
                    val friends = table(s, "friends")
                    val f = friends.optJSONObject(number)
                    if (kind == "invite") {
                        if (table(s,"retired").has(token)) return@state false
                        if ((f != null && (f.optString("status") !in setOf("fast","checking","verified_waiting","invited","unconfirmed") ||
                            f.optString("token") <= token)) || (f == null && friends.length() >= 48) ||
                            now()-p.optLong("at") !in -300_000L..7*DAY) return@state false
                        if (f != null) {
                            table(s,"retired").put(f.getString("token"),now())
                            cancelQueued(s, number)
                        }
                        friends.put(number, JSONObject().put("peer",peer).put("sub",sub).put("token",token).put("status","request").put("at",now()).put("name",p.optString("name").take(60)))
                        return@state !s.optBoolean("muted")
                    }
                    if (f == null || f.optString("token") != token) return@state false
                    if (kind == "remove") {
                        friends.remove(number); table(s,"retired").put(token,now()); removePosts(s, number)
                        cancelQueued(s, number)
                        return@state false
                    }
                    if (kind == "accept") {
                        if (f.optString("status") !in setOf("fast","checking","verified_waiting","invited","unconfirmed")) return@state false
                        f.put("status","friend");if(f.optString("name").isBlank())f.put("name",p.optString("name").take(60));history(s,f);verified=true
                        return@state !s.optBoolean("muted")
                    }
                    val awaitingAccept = f.optString("status") in setOf("fast","checking","verified_waiting","invited","unconfirmed")
                    if (f.optString("status") != "friend" && !(awaitingAccept && kind=="post")) return@state false
                    val posts = table(s, "posts")
                    if (kind == "post") {
                        if (posts.has(postId) || posts.length() >= 512 || now()-p.optLong("at") !in -300_000L..30*DAY) return@state false
                        if (p.optString("text").toByteArray().size > 650 || p.optString("name").length > 60) return@state false
                        val link = p.optString("link")
                        if (link.isNotEmpty() && !safeLink(link)) return@state false
                        if (file == null && link.isEmpty() && p.optString("text").isBlank()) return@state false
                        if (p.optString("mime").isNotEmpty()) {
                            if (file == null || !validMime(mime) || mime.substringBefore('/') != p.optString("mime").substringBefore('/') || file.length() !in 1..MAX_MEDIA) return@state false
                            if (mediaUsed(c)+file.length()>MEDIA_STORE) return@state false
                            copied = true
                            file.copyTo(media(c, postId), overwrite = true)
                            p.put("mime", mime)
                        } else if (file != null) return@state false
                        posts.put(postId, JSONObject().put("id",postId).put("peer",number)
                            .put("at",minOf(now(),p.getLong("at"))).put("name",p.optString("name"))
                            .put("text",p.optString("text")).put("link",link).put("mime",p.optString("mime")))
                        if (awaitingAccept) {
                            f.put("status","friend")
                            history(s,f)
                            verified=true
                        }
                    } else {
                        val post = posts.optJSONObject(p.optString("post")) ?: return@state false
                        if (post.optString("peer").isNotEmpty() && post.optString("peer") != number) return@state false
                        val body = p.optString("text")
                        if (body.isBlank() || body.toByteArray().size > 650 || kind=="reaction" && body !in reactions) return@state false
                        val replies = table(post, "replies")
                        val target = p.optString("target")
                        val seen = table(s,"events")
                        if (seen.has(postId) || replies.has(postId) ||
                            target.isNotEmpty() && replies.optJSONObject(target)?.optString("kind") != "comment") return@state false
                        if (!addReply(post, JSONObject().put("id",postId).put("peer",number).put("at",now())
                                .put("kind",kind).put("target",target).put("text",body))) return@state false
                        if(seen.length()>=8192)seen.keys().asSequence().minByOrNull { seen.optLong(it) }?.let { seen.remove(it) }
                        seen.put(postId,now())
                    }
                    !s.optBoolean("muted") && !f.optBoolean("muted")
                }
            } catch (e: Exception) { if (copied) media(c,postId).delete(); throw e }
        }
        if (verified) { FriendGames.recordVerified(c, peer); kick(c) }
        if (alert) notify(c, if (kind=="invite") "New friend request" else "New Real Social activity")
        return true
    }
    fun receiveMms(c: Context, pdu: JSONObject, sub: Int): Boolean {
        val parts = pdu.nodes("parts")
        val text = parts.filter { it.optString("mime") == "text/plain" }.joinToString("") { it.optString("text") }
        if (!text.startsWith(PREFIX)) return false
        val part = parts.singleOrNull { it.has("file") && validMime(it.optString("mime")) } ?: return true
        return receive(c, pdu.optString("from"), sub, text, MessageTransferProvider.file(c, part.getString("file")), part.getString("mime"))
    }

    private fun notify(c: Context, text: String) = runCatching {
        if (MessageNotifications.quiet(c)) return@runCatching
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("real-social", "Real Social", NotificationManager.IMPORTANCE_DEFAULT))
        val tap = PendingIntent.getActivity(c, 914, Intent(c, MainActivity::class.java).putExtra("open_real_social", true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        nm.notify(914, NotificationCompat.Builder(c,"real-social").setSmallIcon(app.onklav.shared.R.drawable.ref_app_social)
            .setContentTitle("Real Social").setContentText(text).setContentIntent(tap).setAutoCancel(true).build())
    }
    private fun mediaUsed(c: Context) = dir(c).listFiles()?.filter { it.name.endsWith(".media") }?.sumOf(File::length) ?: 0L
    private fun prune(c: Context) = synchronized(lock) {
        val live = state(c) { s ->
            val posts = table(s,"posts")
            val retired = table(s,"retired")
            retired.keys().asSequence().filter { now()-retired.optLong(it)>30*DAY }.toList().forEach { retired.remove(it) }
            values(posts).filter { it.optString("peer").isNotEmpty() && now()-it.optLong("at") > 30*DAY }
                .forEach { posts.remove(it.getString("id")) }
            posts.keys().asSequence().toSet()
        }
        dir(c).listFiles()?.filter { it.name.endsWith(".media") && it.nameWithoutExtension !in live }?.forEach { it.delete() }
    }
    fun recover(c: Context): Long? {
        if (!installed(c)) return null
        prune(c)
        if (!MessagingTransport.role(c) || !MessagingTransport.permission(c,android.Manifest.permission.SEND_SMS)) return null
        val forget = mutableListOf<String>()
        AppSdk.carrierEgress {
            synchronized(lock) {
                if (!installed(c)) return@carrierEgress
                val next = claim(c, forget)
                if (next != null) runCatching { send(c,next) }.onFailure { e ->
                    state(c) { it.put("error", e.message ?: "Could not submit a Real Social update.") }
                }
            }
        }
        forget.forEach { FriendGames.forgetVerified(c,it) }
        return state(c) { s ->
            if (table(s,"jobs").length()>0) 1500L
            else values(table(s,"friends")).filter { it.optLong("at")>0 && it.optString("status") in setOf("fast","checking") }
                .minOfOrNull { (HANDSHAKE-now()+it.optLong("at")).coerceAtLeast(1000) }
        }
    }
    /** Reserve a slot by claiming the next job before adding verification fallbacks. */
    private fun claim(c: Context, forget: MutableList<String>): JSONObject? = state(c) { s ->
        val jobs = table(s,"jobs")
        val friends = table(s,"friends")
        val time = now()
        for (expired in values(jobs).filter { time-it.optLong("at") > DAY }) {
            jobs.remove(expired.getString("id"))
            val friend = friends.optJSONObject(phoneKey(expired.getString("peer")))
            if (friend != null && isInitialVerification(friend, expired)) friend.put("status","unconfirmed")
        }
        // Claim before carrier submission. Never replay an ambiguous send.
        val first = values(jobs).minByOrNull { it.getLong("at") }
        if (first != null) {
            jobs.remove(first.getString("id"))
            val friend = friends.optJSONObject(phoneKey(first.getString("peer")))
            if (friend != null && isInitialVerification(friend, first)) friend.put("at",time)
        }
        for (friend in values(friends).filter { it.optString("status") == "verified_waiting" }) {
            if (jobs.length() >= MAX_JOBS) break
            queue(s,friend,invitation(c,friend))
            friend.put("status","invited").put("at",time)
        }
        for (friend in values(friends).filter { it.optString("status") == "friend" && it.optBoolean("history_waiting") }) {
            history(s,friend)
        }
        for (friend in values(friends).filter { it.optLong("at") > 0 && time-it.optLong("at") >= HANDSHAKE }) {
            when (friend.optString("status")) {
                "fast" -> if (jobs.length() < MAX_JOBS) {
                    friend.put("status","checking").put("at",0)
                    queue(s,friend,FriendGameWire.QUESTION)
                }
                "checking" -> {
                    friend.put("status","unconfirmed")
                    forget += friend.getString("peer")
                }
            }
        }
        first
    }

    private fun isInitialVerification(friend: JSONObject, job: JSONObject): Boolean {
        if (friend.optLong("at") != 0L) return false
        val body = job.getString("wire")
        return when (friend.optString("status")) {
            "checking" -> body == FriendGameWire.QUESTION
            "fast" -> decode(body)?.let {
                it.optString("kind") == "invite" && it.optString("token") == friend.optString("token")
            } == true
            else -> false
        }
    }

    private fun send(c: Context, job: JSONObject) {
        val peer=job.getString("peer")
        val sub=job.optInt("sub",-1)
        FriendGames.policy(c,peer,sub)
        val sm=MessagingTransport.manager(c,sub.takeIf { it>=0 })
        val text=job.getString("wire")
        val attachment=job.optString("media")
        if (attachment.isEmpty()) {
            val parts=sm.divideMessage(text)
            require(parts.size<=12) { "Social update exceeds the carrier message limit." }
            val callbacks=ArrayList(parts.indices.map { index -> resultIntent(c,job.getString("id"),index) })
            if(parts.size==1) sm.sendTextMessage(peer,null,text,callbacks.single(),null)
            else sm.sendMultipartTextMessage(peer,null,parts,callbacks,null)
            return
        }
        val source=media(c,attachment)
        require(source.length() in 1..MAX_MEDIA) { "The shared attachment is unavailable." }
        val transfer=job.getString("id")
        val blob=MessageTransferProvider.file(c,transfer)
        val pdu=MessageTransferProvider.file(c,transfer,"pdu")
        try {
            val mime=decode(text)?.getString("mime") ?: error("Attachment type is missing.")
            source.copyTo(blob,overwrite=true)
            val composed=MessagingTransport.codec(c,JSONObject().put("action","compose").put("id",transfer)
                .put("recipients",JSONArray(listOf(peer))).put("text",text).put("date",now()/1000)
                .put("parts",JSONArray().put(JSONObject().put("file",transfer).put("mime",mime))))
            require(composed.getInt("bytes")<=CarrierMedia.messageLimit(sm.carrierConfigValues?.getInt(SmsManager.MMS_CONFIG_MAX_MESSAGE_SIZE))) { "Attachment exceeds this friend's carrier limit." }
            val result=resultIntent(c,transfer,-1)
            sm.sendMultimediaMessage(c,MessageTransferProvider.uri(c,transfer,"pdu","application/vnd.wap.mms-message"),null,null,result)
        } catch(e: Exception) { pdu.delete(); throw e }
        finally { blob.delete() }
    }
    private fun resultIntent(c: Context, transfer: String, index: Int) =
        PendingIntent.getBroadcast(c,0,Intent(c,RealSocialMmsResult::class.java)
            .setData(Uri.parse("onklav-social://result/$transfer/$index")).putExtra("transfer",transfer)
            .putExtra("media",index<0),PendingIntent.FLAG_IMMUTABLE)
    fun carrierResult(c: Context, id: String, success: Boolean, media: Boolean) {
        if (!key.matches(id)) return
        if (media) MessageTransferProvider.file(c,id,"pdu").delete()
        if (!success && installed(c)) state(c) { it.put("error","The carrier could not send a Real Social update.") }
    }
    fun validMime(mime: String) = mime in setOf("image/jpeg","image/png","image/webp","image/gif","audio/amr","audio/aac","audio/mpeg","audio/mp4","audio/ogg","video/mp4","video/3gpp","video/webm")
    fun safeLink(link: String): Boolean = link.length <= 350 && runCatching {
        val u=java.net.URI(link);u.scheme.equals("https",true)&&u.host!=null&&u.userInfo==null
    }.getOrDefault(false)
    val reactions = listOf("❤️","😂","😮","😢","👍","🙏")
}

class RealSocialMmsResult : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        val transfer=intent.getStringExtra("transfer") ?: return
        val result=resultCode
        val pending=goAsync()
        ChatStore.executor.execute { try { RealSocial.carrierResult(c,transfer,result==Activity.RESULT_OK,intent.getBooleanExtra("media",false)) } finally { pending.finish() } }
    }
}
