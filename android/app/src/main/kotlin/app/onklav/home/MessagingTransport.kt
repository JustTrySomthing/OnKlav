package app.onklav.home

import android.Manifest
import android.app.*
import android.app.role.RoleManager
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Telephony
import android.telephony.*
import app.onklav.shared.NativeCore
import java.io.File
import java.security.MessageDigest
import org.json.*

/** Android carries messages; Rust owns the wire codec and durable outcome ledger. */
object MessagingTransport {
    // This gate remains false until the incoming/outgoing device fixture checks pass.
    const val MMS_READY = false
    /** Pictures are on by default. Android default-SMS approval remains explicit in setup. */
    fun carrierEnabled(c:Context)=MMS_READY||c.getSharedPreferences("messaging_setup",0).getBoolean("carrier_trial",true)
    fun enableCarrierTrial(c:Context){
        val capable=if(android.os.Build.VERSION.SDK_INT>=33)c.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_MESSAGING)
            else c.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
        check(capable){"This device has no SMS/MMS service"}
        check(c.getSharedPreferences("messaging_setup",0).edit().putBoolean("carrier_trial",true).commit())
    }


    fun role(context: Context): Boolean =
        context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_SMS) == true

    fun permission(context: Context, value: String) =
        context.checkSelfPermission(value) == PackageManager.PERMISSION_GRANTED

    fun manager(context: Context, selected: Int? = null): SmsManager {
        val prefs = context.getSharedPreferences("messaging_setup", 0)
        val saved = if (prefs.contains("sim")) prefs.getInt("sim", -1) else null
        val active = if (permission(context, Manifest.permission.READ_PHONE_STATE))
            context.getSystemService(SubscriptionManager::class.java)
                ?.activeSubscriptionInfoList.orEmpty().map { it.subscriptionId }
            else null
        val subscription = SendingSubscription.resolve(
            selected, saved, SubscriptionManager.getDefaultSmsSubscriptionId(), active,
        )
        @Suppress("DEPRECATION")
        return SmsManager.getSmsManagerForSubscriptionId(subscription)
    }

    fun codec(context: Context, value: JSONObject): JSONObject {
        val r =
            JSONObject(
                NativeCore.dispatch(
                    context.filesDir.absolutePath,
                    value.put("op", "mms").toString(),
                )
            )
        check(r.getBoolean("ok")) { r.optString("error", "MMS operation failed") }
        return r.getJSONObject("value")
    }

    private fun callback(context: Context, kind: String, id: String, part: Int = 0): PendingIntent {
        val intent =
            Intent(context, MessageResultReceiver::class.java)
                .setAction("app.onklav.$kind")
                .setData(Uri.parse("onklav-result://$kind/$id/$part"))
                .putExtra("token", id)
                .putExtra("part", part)
                .putExtra("kind", kind)
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    private fun values(vararg pairs: Pair<String, Any?>) =
        ContentValues().apply {
            pairs.forEach { (k, v) ->
                when (v) {
                    null -> putNull(k)
                    is String -> put(k, v)
                    is Int -> put(k, v)
                    is Long -> put(k, v)
                    is Boolean -> put(k, v)
                }
            }
        }

    fun sendSms(context: Context, address: String, body: String): String {
        check(permission(context, Manifest.permission.SEND_SMS)) {
            "Allow text permissions in Setup"
        }
        require(body.isNotBlank()) { "Write a message first" }
        val sm = manager(context)
        val parts = sm.divideMessage(body)
        require(parts.size in 1..20) { "This text is too long. Send it in smaller messages." }
        val id = MessageTransferProvider.id()
        val now = System.currentTimeMillis()
        val store = ChatStore(context)
        val uri =
            if (role(context))
                context.contentResolver.insert(
                    Telephony.Sms.Outbox.CONTENT_URI,
                    values(
                        "address" to address,
                        "body" to body,
                        "date" to now,
                        "read" to 1,
                        "seen" to 1,
                        "sub_id" to sm.subscriptionId,
                        "thread_id" to Telephony.Threads.getOrCreateThreadId(context, address),
                    ),
                )
            else null
        try {
            store.requestSync(
                JSONObject()
                    .put("action", "stage")
                    .put("id", id)
                    .put("address", address)
                    .put("text", body)
                    .put("at", now)
                    .put("parts", parts.size)
                    .put("sub", sm.subscriptionId)
                    .put("kind", "sms")
                    .put("provider_uri", uri?.toString().orEmpty())
            )
        } catch (e: Exception) {
            uri?.let { context.contentResolver.delete(it, null, null) }
            throw e
        }
        try {
            if (parts.size == 1) {
                sm.sendTextMessage(address, null, parts[0],
                    callback(context, "sms_sent", id), callback(context, "sms_delivery", id))
            } else {
                sm.sendMultipartTextMessage(
                    address, null, parts,
                    ArrayList(parts.indices.map { callback(context, "sms_sent", id, it) }),
                    ArrayList(parts.indices.map { callback(context, "sms_delivery", id, it) }),
                )
            }
        } catch (e: Exception) {
            parts.indices.forEach { outcome(context, id, it, false, false, e.message.orEmpty()) }
            throw e
        }
        return id
    }

    /** Explicit, confirmed retry only. Recheck late receipts before creating a new send. */
    fun retryText(context: Context, key: String): String {
        require(key.matches(Regex("out:[a-f0-9]{32}"))) { "Copy this older message to a draft to send it again." }
        recoverResults(context)
        val item = ChatStore(context).requestSync(JSONObject().put("action", "snapshot"))
            .child("outgoing").child(key.removePrefix("out:"))
        check(item.optString("kind") == "sms") { "This action retries plain text messages only." }
        check(item.optString("status") in setOf("Failed", "Status unknown", "Delivery failed")) {
            "This message has already sent or is still sending. Its status has been refreshed."
        }
        return sendSms(context, item.getString("address"), item.getString("text"))
    }

    fun outcome(
        context: Context,
        id: String,
        part: Int,
        success: Boolean,
        delivery: Boolean,
        error: String = "",
    ) {
        val state =
            ChatStore(context)
                .requestSync(
                    JSONObject()
                        .put("action", "outcome")
                        .put("id", id)
                        .put("part", part)
                        .put("success", success)
                        .put("delivery", delivery)
                        .put("error", error)
                )
        val item = state.child("outgoing").child(id)
        val uri = item.optString("provider_uri")
        if (role(context) && uri.startsWith("content://sms/")) {
            val status = item.optString("status")
            val v = ContentValues()
            if (status in listOf("Sent", "Delivered", "Delivery failed")) v.put("type", 2)
            else if (status == "Failed") v.put("type", 5)
            if (status == "Delivered") v.put("status", 0)
            else if (status == "Delivery failed") v.put("status", 64)
            if (v.size() > 0) {check(context.contentResolver.update(Uri.parse(uri), v, null, null)>0){"Send result is retained until its phone message can be updated"};ChatStore(context).requestSync(JSONObject().put("action","provider_synced").put("id",id))}
        } else if (role(context) && uri.startsWith("content://mms/") && !delivery) {
            if (item.optString("status") in listOf("Sent", "Failed")) {
                check(context.contentResolver.update(
                    Uri.parse(uri),
                    values("msg_box" to if (success) 2 else 5),
                    null,
                    null,
                )>0){"Send result is retained until its phone message can be updated"}
                ChatStore(context).requestSync(JSONObject().put("action","provider_synced").put("id",id))
            }
        }
    }

    @Synchronized fun receiveSms(context: Context, intent: Intent, recovery:File?=null) {
        check(role(context)) { "OnKlav is not the default messaging app" }
        // Preserve the protected broadcast before touching the provider. Failed inserts are
        // retryable.
        val rawParts =
            (intent.extras?.get("pdus") as? Array<*>)?.filterIsInstance<ByteArray>().orEmpty()
        require(rawParts.isNotEmpty() && rawParts.size <= 255) { "Missing SMS PDUs" }
        val subscription=intent.getIntExtra("subscription",intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,-1))
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("$subscription:${intent.getStringExtra("format")}:".toByteArray())
        rawParts.forEach {digest.update(java.nio.ByteBuffer.allocate(4).putInt(it.size).array());digest.update(it)}
        val rawId = digest.digest().take(16).joinToString("") { "%02x".format(it) }
        val queue = File(context.filesDir, "incoming-sms").apply { check(mkdirs() || isDirectory) }
        val saved = recovery ?: File(queue, "$rawId.json")
        val replay=saved.exists()
        if (!replay) {
            val value =
                JSONObject()
                    .put("format", intent.getStringExtra("format"))
                    .put("sub", intent.getIntExtra("subscription",intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,-1)))
                    .put("received", System.currentTimeMillis())
                    .put(
                        "pdus",
                        JSONArray(
                            rawParts.map {
                                android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP)
                            }
                        ),
                    )
            val tmp = File(queue, "$rawId.tmp")
            tmp.outputStream().use {
                it.write(value.toString().toByteArray())
                it.fd.sync()
            }
            check(tmp.renameTo(saved))
        }
        val receipt=JSONObject(saved.readText())
        fun persistReceipt(){val tmp=File(queue,"$rawId.tmp");tmp.outputStream().use{it.write(receipt.toString().toByteArray());it.fd.sync()};check(tmp.renameTo(saved))}
        fun phase(name:String){receipt.put(name,true);persistReceipt()}
        val received = receipt.getLong("received")
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        require(!parts.isNullOrEmpty())
        val address = parts.first().originatingAddress ?: error("Missing sender")
        require(parts.all { it.originatingAddress == address }) {
            "Multipart message sender mismatch"
        }
        val body = parts.joinToString("") { it.messageBody.orEmpty() }
        if(RealSocial.receive(context,address,subscription,body)) {check(saved.delete());return}
        runCatching { RealSocial.observeVerification(context,address,body,subscription) }.onFailure { ChatStore.lastError.value=it.message }
        runCatching { SdkWalkieTransport.observeVerification(context,address,subscription,body) }.onFailure { ChatStore.lastError.value=it.message }
        if(FriendGames.receive(context,address,subscription,body)) {check(saved.delete());return}
        if(SdkWalkieTransport.receiveControl(context,address,subscription,body)) {check(saved.delete());return}
        val date = parts.first().timestampMillis
        // The receiver is protected by BROADCAST_SMS. Deduplicate a platform re-delivery.
        val duplicate =
            context.contentResolver
                .query(
                    Telephony.Sms.Inbox.CONTENT_URI,
                    arrayOf("_id","thread_id","date"),
                    "address=? AND body=? AND date_sent=? AND sub_id=?",
                    arrayOf(address, body, date.toString(),subscription.toString()),
                    null,
                )
                ?.use { if(it.moveToFirst()) Triple(it.getLong(0),it.getLong(1),it.getLong(2))else null }
        val thread=duplicate?.second?:Telephony.Threads.getOrCreateThreadId(context,address)
        if (duplicate==null) {
            if(!receipt.has("filing_at")){receipt.put("filing_at",IncomingReceiptOrder.next(context,ChatStore(context).requestSync(JSONObject().put("action","snapshot"))));persistReceipt()}
            checkNotNull(
                context.contentResolver.insert(
                    Telephony.Sms.Inbox.CONTENT_URI,
                    values(
                        "address" to address,
                        "body" to body,
                        "date" to received,
                        "date_sent" to date,
                        "read" to 0,
                        "seen" to 0,
                        "thread_id" to thread,
                        "sub_id" to subscription,
                    ),
                )
            )
        }
        // Only this durable receive attempt owns filing. A later duplicate broadcast
        // must not undo a user's subsequent Archive/Trash action.
        if(duplicate==null||replay&&duplicate.third==received){
            if(!receipt.optBoolean("folder_filed")){ChatStore(context).requestSync(JSONObject().put("action","arrival").put("key",thread.toString()).put("address",address).put("at",receipt.optLong("filing_at",received)));phase("folder_filed")}
            if(!receipt.optBoolean("notified")){MessageNotifications.incoming(context,thread,address,body,fileThread=false);phase("notified")}
        }
        check(saved.delete()) { "Message was saved but its recovery record could not be removed" }
        ChatStore.epoch.value++
    }

    fun retrySms(context: Context) {
        File(context.filesDir, "incoming-sms")
            .listFiles()
            ?.filter { it.name.matches(Regex("[a-f0-9]{32}\\.json")) }
            ?.forEach { file ->
                try {
                require(file.length() < 2000000)
                val j = JSONObject(file.readText())
                val a = j.getJSONArray("pdus")
                val intent =
                    Intent(Telephony.Sms.Intents.SMS_DELIVER_ACTION)
                        .putExtra("format", j.optString("format"))
                        .putExtra("subscription", j.optInt("sub", -1))
                intent.putExtra(
                    "pdus",
                    Array(a.length()) {
                        android.util.Base64.decode(a.getString(it), android.util.Base64.DEFAULT)
                    },
                )
                receiveSms(context, intent,file)
                } catch(e:Exception){ChatStore.lastError.value="Saved SMS recovery needs review: ${e.message}"}
            }
    }

    fun persistMms(
        context: Context,
        pdu: JSONObject,
        box: Int,
        sub: Int,
        existing: Uri? = null,
        receiveId:String? = null,
    ): Uri {
        check(role(context)) { "OnKlav is not the default messaging app" }
        require(existing==null){"Replacing an existing MMS requires an explicit recovery receipt"}
        if(receiveId!=null){
            require(receiveId.matches(Regex("[a-f0-9]{32}"))&&box==1)
            context.contentResolver.query(Telephony.Mms.CONTENT_URI,arrayOf("_id","ct_l"),"ct_l IN (?,?) AND msg_box=1 AND sub_id=?",arrayOf("onklav-received:$receiveId","onklav-pending:$receiveId",sub.toString()),null)?.use{rows->
                while(rows.moveToNext()){val old=android.content.ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI,rows.getLong(0));if(rows.getString(1)=="onklav-received:$receiveId")return old;check(context.contentResolver.delete(old,null,null)>0){"Incomplete picture needs recovery"}}
            }
        }
        val own =
            context
                .getSharedPreferences("profile", 0)
                .getString("phone", "")
                .orEmpty()
                .filter { it.isDigit() }
                .takeLast(10)
        val raw = mutableSetOf<String>()
        pdu.optJSONArray("to")?.let { a -> repeat(a.length()) { raw += a.getString(it) } }
        val sender = pdu.optString("from")
        if (box == 1 && (pdu.optJSONArray("to")?.length() ?: 0) <= 1) raw.clear()
        if (box == 1 && (pdu.optJSONArray("to")?.length() ?: 0) > 1)
            require(own.isNotEmpty()) {
                "Set My Number in Setup to file this group MMS correctly; the downloaded message is retained"
            }
        if (sender.isNotBlank()) raw += sender
        val peers =
            raw.filter {
                    it.isNotBlank() &&
                        it != "insert-address-token" &&
                        (own.isEmpty() || it.filter { c -> c.isDigit() }.takeLast(10) != own)
                }
                .toSet()
                .ifEmpty { setOf(sender).filter { it.isNotBlank() }.toSet() }
        require(peers.isNotEmpty()) { "MMS sender or recipient is missing" }
        val thread = Telephony.Threads.getOrCreateThreadId(context, peers)
        val date =
            pdu.optLong("date", System.currentTimeMillis() / 1000).takeIf { it > 0 }
                ?: System.currentTimeMillis() / 1000
        val v =
            values(
                "thread_id" to thread,
                "date" to date,
                "msg_box" to box,
                "m_type" to if (box == 1) 132 else 128,
                "v" to 18,
                "ct_t" to "application/vnd.wap.multipart.related",
                "read" to if (box == 1) 0 else 1,
                "seen" to if (box == 1) 0 else 1,
                "sub_id" to sub,
                "m_cls" to "personal",
                "pri" to 129,
                "sub_cs" to 106,
                "text_only" to 0,
            )
        if (pdu.optString("subject").isNotBlank()) v.put("sub", pdu.getString("subject"))
        if (pdu.optString("transaction").isNotEmpty()) v.put("tr_id", pdu.getString("transaction"))
        if (pdu.optString("message_id").isNotEmpty()) v.put("m_id", pdu.getString("message_id"))
        if(receiveId!=null)v.put("ct_l","onklav-pending:$receiveId")
        val uri = checkNotNull(context.contentResolver.insert(Telephony.Mms.CONTENT_URI, v))
        try {
            val mid = uri.lastPathSegment!!
            if (sender.isNotBlank())
                checkNotNull(context.contentResolver.insert(
                    Uri.parse("content://mms/$mid/addr"),
                    values("address" to sender, "type" to 137, "charset" to 106),
                )) {"MMS sender could not be saved"}
            pdu.optJSONArray("to")?.let { a ->
                repeat(a.length()) {
                    checkNotNull(context.contentResolver.insert(
                        Uri.parse("content://mms/$mid/addr"),
                        values("address" to a.getString(it), "type" to 151, "charset" to 106),
                    )) {"MMS recipient could not be saved"}
                }
            }
            val parts = pdu.optJSONArray("parts") ?: JSONArray()
            repeat(parts.length()) { i ->
                val part = parts.getJSONObject(i)
                val mime = part.getString("mime")
                val name = part.optString("name", "Attachment")
                val pv =
                    values(
                        "seq" to i,
                        "ct" to mime,
                        "name" to name,
                        "cl" to name,
                        "cid" to "<$name>",
                        "chset" to 106,
                    )
                if (mime == "text/plain") pv.put("text", part.optString("text"))
                val target =
                    checkNotNull(
                        context.contentResolver.insert(Uri.parse("content://mms/$mid/part"), pv)
                    )
                if (mime != "text/plain")
                    MessageTransferProvider.file(context, part.getString("file"))
                        .inputStream()
                        .use { input ->
                            checkNotNull(context.contentResolver.openOutputStream(target)).use {
                                input.copyTo(it)
                            }
                        }
            }
            if(receiveId!=null)check(context.contentResolver.update(uri,values("ct_l" to "onklav-received:$receiveId"),null,null)>0){"Picture receipt could not be saved"}
            return uri
        } catch (e: Exception) {
            runCatching { context.contentResolver.delete(uri, null, null) }
            throw e
        }
    }

    fun sendMms(
        context: Context,
        recipients: List<String>,
        text: String,
        parts: JSONArray,
    ): String {
        check(carrierEnabled(context) && role(context)) { "Complete messaging Setup before sending attachments" }
        val sm = manager(context)
        val id = MessageTransferProvider.id()
        val input =
            JSONObject()
                .put("action", "compose")
                .put("id", id)
                .put("recipients", JSONArray(recipients))
                .put("text", text)
                .put("parts", parts)
                .put("date", System.currentTimeMillis() / 1000)
        val composed = codec(context, input)
        val maximum = CarrierMedia.messageLimit(sm.carrierConfigValues?.getInt(SmsManager.MMS_CONFIG_MAX_MESSAGE_SIZE))
        if (composed.getInt("bytes") > maximum) {
            MessageTransferProvider.file(context,id,"pdu").delete()
            error("This message exceeds the MMS size limit. Remove an attachment or choose a smaller file.")
        }
        val parsed = codec(context, JSONObject().put("action", "parse").put("id", id))
        val uri = try{persistMms(context, parsed, 4, sm.subscriptionId)}finally{clearDecodedParts(context,parsed)}
        try {
        ChatStore(context)
            .requestSync(
                JSONObject()
                    .put("action", "stage")
                    .put("id", id)
                    .put("address", recipients.joinToString(";"))
                    .put("text", text)
                    .put("at", System.currentTimeMillis())
                    .put("parts", 1)
                    .put("sub", sm.subscriptionId)
                    .put("kind", "mms")
                    .put("provider_uri", uri.toString())
            )
        } catch(e:Exception) {
            runCatching{context.contentResolver.delete(uri,null,null)}
            throw e
        }
        val transfer =
            MessageTransferProvider.uri(context, id, "pdu", "application/vnd.wap.mms-message")
        try {
            sm.sendMultimediaMessage(
                context,
                transfer,
                null,
                null,
                callback(context, "mms_sent", id),
            )
        } catch (e: Exception) {
            outcome(context, id, 0, false, false, e.message.orEmpty())
            throw e
        }
        return id
    }

    private fun jobs(context: Context) = context.getSharedPreferences("mms_transfers", 0)

    fun receiveMms(context: Context, push: ByteArray, sub: Int) {
        check(role(context))
        require(push.size <= 1024 * 1024)
        val raw = MessageTransferProvider.id()
        MessageTransferProvider.file(context, raw, "pdu").writeBytes(push)
        val parsed = codec(context, JSONObject().put("action", "parse").put("id", raw))
        if (parsed.getInt("kind") != 130) return
        val location = parsed.getString("location")
        val url = Uri.parse(location)
        require(url.scheme in setOf("http", "https") && !url.host.isNullOrBlank()) {
            "Invalid carrier download location"
        }
        val id =
            MessageDigest.getInstance("SHA-256")
                .digest("$sub:$location".toByteArray())
                .take(16)
                .joinToString("") { "%02x".format(it) }
        val prefs = jobs(context)
        if (
            prefs.getBoolean("$id.complete", false) ||
                incomingMmsInProgress(prefs.getLong("$id.attempt",0),System.currentTimeMillis())
        )
            return
        check(
            prefs
                .edit()
                .putString("$id.location", location)
                .putString("$id.transaction", parsed.optString("transaction"))
                .putString("$id.sender", parsed.optString("from"))
                .putInt("$id.sub", sub)
                .putString("$id.raw", raw)
                .commit()
        )
        download(context, id)
    }

    fun download(context: Context, id: String, automatic:Boolean=false) {
        require(id.matches(Regex("[a-f0-9]{32}")))
        check(role(context)){"Choose OnKlav as the default messaging app in Setup to download this picture. The request is kept."}
        check(carrierEnabled(context)){"Picture downloads are off in OnKlav. Enable picture downloads, then retry. The request is kept."}
        val prefs = jobs(context)
        if(finishReceivedMms(context,id)||prefs.getBoolean("$id.complete",false))return
        val attempt=prefs.getLong("$id.attempt",0)
        if(incomingMmsInProgress(attempt,System.currentTimeMillis()))return
        val url = prefs.getString("$id.location", null) ?: error("Missing carrier location")
        val retained=prefs.getString("$id.transfer",id)!!
        val output = MessageTransferProvider.file(context, retained, "pdu")
        if(output.exists()&&output.length()>0){
            val decoded=runCatching{codec(context,JSONObject().put("action","parse").put("id",retained))}.getOrNull()
            if(decoded?.optInt("kind")==132){downloaded(context,id,true,Activity.RESULT_OK,decoded);return}
            if(decoded!=null)clearDecodedParts(context,decoded)
        }
        val transfer=MessageTransferProvider.id();MessageTransferProvider.file(context,transfer,"pdu").writeBytes(byteArrayOf())
        val now=System.currentTimeMillis()
        check(prefs.edit().putString("$id.transfer",transfer).putString("$transfer.download_job",id).putLong("$id.attempt",now).putLong("$id.retry_due",now+INCOMING_MMS_TIMEOUT_MS).remove("$id.error").apply{if(!automatic)putInt("$id.retries",0)}.commit()){ "Download attempt could not be saved. Check available storage." }
        try {
            manager(
                    context,
                    prefs.getInt("$id.sub", -1).takeIf {
                        SubscriptionManager.isValidSubscriptionId(it)
                    },
                )
                .downloadMultimediaMessage(
                    context,
                    url,
                    MessageTransferProvider.uri(
                        context,
                        transfer,
                        "pdu",
                        "application/vnd.wap.mms-message",
                    ),
                    null,
                    callback(context, "mms_download", transfer),
                )
            MmsDownloadRecovery.schedule(context)
        } catch (e: Exception) {
            check(prefs.edit().putString("$id.error", e.message).putLong("$id.attempt", 0).remove("$id.retry_due").commit()){ "Download failure could not be saved. Check available storage." }
            MessageNotifications.picturePending(context,id,prefs.getString("$id.sender","").orEmpty(),"Open Messages to retry the download.")
            throw e
        }finally{ChatStore.epoch.value++}
    }

    private fun clearDecodedParts(context:Context,pdu:JSONObject){
        pdu.optJSONArray("parts")?.let{parts->repeat(parts.length()){val id=parts.getJSONObject(it).optString("file");if(id.matches(Regex("[a-f0-9]{32}")))MessageTransferProvider.file(context,id).delete()}}
    }
    internal fun finishReceivedMms(context:Context,id:String):Boolean {
        val prefs=jobs(context)
        if(!prefs.getBoolean("$id.file_pending",false))return false
        val raw=prefs.getString("$id.uri",null)?:error("Missing received picture receipt")
        if(raw.isNotEmpty()){
            val uri=Uri.parse(raw)
            val thread=context.contentResolver.query(uri,arrayOf("thread_id"),null,null,null)?.use{if(it.moveToFirst())it.getLong(0)else null}?:error("Received picture needs phone-provider recovery")
            check(thread>0){"Received picture has no conversation"}
            val sender=prefs.getString("$id.sender","").orEmpty()
            if(!prefs.getBoolean("$id.folder_filed",false)){
                val received=prefs.getLong("$id.received",0).takeIf{it>0}?:System.currentTimeMillis().also{check(prefs.edit().putLong("$id.received",it).commit()){ "Picture receive time could not be saved" }}
                ChatStore(context).requestSync(JSONObject().put("action","arrival").put("key",thread.toString()).put("address",sender).put("at",prefs.getLong("$id.filing_at",received)))
                check(prefs.edit().putBoolean("$id.folder_filed",true).commit()){ "Received picture folder receipt could not be saved" }
            }
            MessageNotifications.incoming(context,thread,sender,"Picture message",fileThread=false)
        }
        check(prefs.edit().putBoolean("$id.complete",true).putBoolean("$id.file_pending",false).putLong("$id.attempt",0).remove("$id.error").remove("$id.retry_due").commit()){ "Completed message metadata could not be saved. Check available storage." }
        MessageNotifications.pictureComplete(context,id)
        return true
    }
    fun downloaded(context: Context, id: String, success: Boolean, result: Int, decoded:JSONObject?=null, outputId:String=id) {
        val prefs = jobs(context)
        if(finishReceivedMms(context,id))return
        if (prefs.getBoolean("$id.complete", false)) {
            // A failed commit can update memory without updating disk.
            check(prefs.edit().putBoolean("$id.complete",true).remove("$id.error").remove("$id.retry_due").commit()){ "Completed message metadata could not be saved. Check available storage." }
            return
        }
        try {
            check(success) { incomingMmsFailure(result) }
            val p = decoded ?: codec(context, JSONObject().put("action", "parse").put("id", outputId))
            try {
            check(p.getInt("kind") == 132 && p.optInt("status") in listOf(0, 128)) {
                "Carrier returned an invalid MMS response"
            }
            val walkie=RealSocial.receiveMms(context,p,prefs.getInt("$id.sub",-1)) || SdkWalkieTransport.receiveVoice(context,p,prefs.getInt("$id.sub",-1))
            if(!walkie)IncomingReceiptOrder.persisted(context,prefs,"$id.filing_at",ChatStore(context).requestSync(JSONObject().put("action","snapshot")))
            val uri = if(walkie) Uri.EMPTY else persistMms(context, p, 1, prefs.getInt("$id.sub", -1),receiveId=id)
            check(prefs
                .edit()
                .putBoolean("$id.complete", false).putBoolean("$id.file_pending",true).putString("$id.sender",p.optString("from")).putLong("$id.attempt",0).remove("$id.retry_due")
                .putString("$id.uri", uri.toString())
                .remove("$id.error")
                .commit()){ "Completed message metadata could not be saved. Check available storage." }
            finishReceivedMms(context,id)
            // Filing is complete. A best-effort acknowledgement must never turn a
            // successfully received picture into a disappearing download failure.
            MessageNotifications.pictureComplete(context,id)
            runCatching {
            val ack = MessageTransferProvider.id()
            codec(
                context,
                JSONObject()
                    .put("action", "ack")
                    .put("id", ack)
                    .put("transaction", prefs.getString("$id.transaction", "").orEmpty()),
            )
            runCatching {
                manager(
                        context,
                        prefs.getInt("$id.sub", -1).takeIf {
                            SubscriptionManager.isValidSubscriptionId(it)
                        },
                    )
                    .sendMultimediaMessage(
                        context,
                        MessageTransferProvider.uri(
                            context,
                            ack,
                            "pdu",
                            "application/vnd.wap.mms-message",
                        ),
                        prefs.getString("$id.location", null),
                        null,
                        callback(context, "mms_ack", ack),
                    )
            }
            }

            } finally {clearDecodedParts(context,p)}
        } catch (e: Exception) {
            check(prefs.edit().putString("$id.error", e.message).putLong("$id.attempt", 0).remove("$id.retry_due").commit()){ "Download failure could not be saved. Check available storage." }
            val filing=prefs.getBoolean("$id.file_pending",false)
            val retry=filing||!success&&incomingMmsTransient(result)&&prefs.getInt("$id.retries",0)<3
            if(retry){val retries=prefs.getInt("$id.retries",0)+1;check(prefs.edit().putInt("$id.retries",retries).putLong("$id.retry_due",System.currentTimeMillis()+15000L*(1L shl (retries-1))).commit());MmsDownloadRecovery.schedule(context)}
            MessageNotifications.picturePending(context,id,prefs.getString("$id.sender","").orEmpty(),(e.message?:"Open Messages to retry.")+if(retry)" We’ll retry automatically."else "")
        }
        ChatStore.epoch.value++
    }

    /** Store the private callback before applying it. A crash can replay metadata, never a send. */
    fun receiveResult(context:Context,intent:Intent,code:Int) {
        require(CarrierResults.valid(intent)){"Invalid carrier callback identity"}
        val receipt=MessageTransferProvider.id()
        val row=JSONObject().put("token",intent.getStringExtra("token")).put("kind",intent.getStringExtra("kind"))
            .put("part",intent.getIntExtra("part",0)).put("code",code)
            .put("no_default", intent.getBooleanExtra("noDefault", false))
            .put("radio_error", intent.getIntExtra("errorCode", 0)).put("format",intent.getStringExtra("format")?:"3gpp")
        val bytes=intent.getByteArrayExtra(if(row.getString("kind")=="sms_delivery")"pdu"else SmsManager.EXTRA_MMS_DATA)
        if(bytes!=null){require(bytes.size<=1024*1024);val f=MessageTransferProvider.file(context,receipt,"pdu");f.outputStream().use{it.write(bytes);it.fd.sync()};row.put("payload",receipt)}
        val now=System.currentTimeMillis()
        val delay=if(row.getString("kind")=="sms_sent")smsSettleDelay(code,row.optBoolean("no_default"))else 0L
        row.put("received_at",now).put("settle_at",now+delay)
        CarrierResults(context).save(receipt,row)
        try { applyResult(context,receipt,row) } catch(e:Exception) { TransportRecovery.schedule(context,60_000);throw e }
    }
    internal const val SMS_SETTLE_MS=8_000L
    internal fun smsSettleDelay(code:Int,noDefault:Boolean=false):Long {
        if(code==Activity.RESULT_OK||noDefault||code in setOf(SmsManager.RESULT_ERROR_RADIO_OFF,SmsManager.RESULT_ERROR_NO_SERVICE,SmsManager.RESULT_RADIO_NOT_AVAILABLE,SmsManager.RESULT_ERROR_LIMIT_EXCEEDED,SmsManager.RESULT_ERROR_FDN_CHECK_FAILURE,SmsManager.RESULT_ERROR_SHORT_CODE_NOT_ALLOWED,SmsManager.RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED,SmsManager.RESULT_NO_DEFAULT_SMS_APP))return 0
        return SMS_SETTLE_MS
    }
    internal fun receiptWait(row:JSONObject,now:Long=System.currentTimeMillis()):Long {
        val received=row.optLong("received_at");val due=row.optLong("settle_at")
        return if(received<=0||now<received||due-received !in 0..SMS_SETTLE_MS)0 else (due-now).coerceAtLeast(0)
    }
    private val settling=java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private fun applyResult(context:Context,receipt:String,row:JSONObject) {
        val wait=receiptWait(row)
        if(wait>0){
            TransportRecovery.schedule(context,wait)
            val key=context.filesDir.absolutePath+"/"+receipt
            if(settling.add(key))android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                settling.remove(key)
                ChatStore.executor.execute{try{CarrierResults(context).read(receipt)?.let{applyResult(context,receipt,it)}}catch(e:Exception){TransportRecovery.schedule(context,60_000)}}
            },wait)
            return
        }
        val id=row.getString("token");val part=row.getInt("part");val code=row.getInt("code")
        val kind=row.getString("kind")
        require(id.matches(Regex("[a-f0-9]{32}"))&&kind in CarrierResults.kinds&&part in 0..254)
        val payload=row.optString("payload").takeIf{it.isNotEmpty()}?.let{MessageTransferProvider.file(context,it,"pdu")}
        when(kind){
            "sms_sent"->{
                val error=if(code==Activity.RESULT_OK) "" else {
                    val send=ChatStore(context).requestSync(JSONObject().put("action","snapshot")).child("outgoing").child(id)
                    smsFailure(code,row.optBoolean("no_default"),row.optInt("radio_error"),send.optInt("parts").takeIf{it in 1..20})
                }
                outcome(context,id,part,code==Activity.RESULT_OK,false,error)
            }
            "sms_delivery"->{
                val bytes=payload?.takeIf{it.length()<=1024*1024}?.readBytes()
                val status=bytes?.let{SmsMessage.createFromPdu(it,row.optString("format","3gpp"))?.status}
                if(status!=null&&status !in 32..63)outcome(context,id,part,status<32,true,if(status<32)""else"Delivery failed")
            }
            "mms_sent"->{
                var ok=code==Activity.RESULT_OK
                if(ok&&payload!=null){val response=codec(context,JSONObject().put("action","parse").put("id",row.getString("payload")));ok=response.getInt("kind")==129&&response.optInt("status")==128}
                outcome(context,id,0,ok,false,if(ok)""else"Carrier MMS send failed ($code)")
            }
            "mms_download"->{
                val prefs=jobs(context);val job=prefs.getString("$id.download_job",null)
                if(job!=null){
                    if(prefs.getString("$job.transfer",null)==id)downloaded(context,job,code==Activity.RESULT_OK,code,outputId=id)
                    check(prefs.edit().remove("$id.download_job").commit())
                    if(prefs.getString("$job.transfer",null)!=id||prefs.getBoolean("$job.complete",false))MessageTransferProvider.file(context,id,"pdu").delete()
                }else if(prefs.contains("$id.location")&&!prefs.contains("$id.transfer"))downloaded(context,id,code==Activity.RESULT_OK,code)
            }
            "mms_ack"->MessageTransferProvider.file(context,id,"pdu").delete()
        }
        CarrierResults(context).remove(receipt)
        payload?.delete()
    }
    internal fun smsFailure(code: Int, noDefault: Boolean = false, radioError: Int = 0, partCount: Int? = null): String {
        val reason = if (noDefault) "Choose a sending SIM in OnKlav Setup."
        else when (code) {
            SmsManager.RESULT_ERROR_RADIO_OFF -> "The phone radio is off. Turn off airplane mode."
            SmsManager.RESULT_ERROR_NO_SERVICE, SmsManager.RESULT_RADIO_NOT_AVAILABLE -> "No cellular SMS service is available."
            SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> "The phone's SMS sending limit was reached. Wait before trying again."
            SmsManager.RESULT_ERROR_SHORT_CODE_NOT_ALLOWED, SmsManager.RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED -> "Android has blocked this short-code message."
            SmsManager.RESULT_NO_DEFAULT_SMS_APP -> "Choose OnKlav as the default SMS app in Setup."
            SmsManager.RESULT_NETWORK_REJECT -> "The carrier rejected this text."
            SmsManager.RESULT_NETWORK_ERROR, SmsManager.RESULT_RIL_NETWORK_ERR -> "The mobile network reported a sending error."
            SmsManager.RESULT_MODEM_ERROR, SmsManager.RESULT_RIL_MODEM_ERR -> "The phone modem reported a sending error."
            else -> "The phone or carrier could not send this text."
        }
        return "$reason (SMS $code" + (if (radioError != 0) ", modem $radioError" else "") +
            "). " + if(partCount!=null&&partCount>1) "Check before retrying; other parts may have been sent."
            else "Check with the recipient before sending again."
    }

    /** Update only our known legacy error text for display; retain the original receipt. */
    internal fun smsDisplayError(send:JSONObject):String {
        val error=send.optString("error")
        if(send.optString("kind")!="sms")return error
        val legacy=Regex("""^.* \(SMS (-?\d+)(?:, modem (-?\d+))?\)\. Check before retrying; other parts may have been sent\.$""").matchEntire(error)?:return error
        val code=legacy.groupValues[1].toIntOrNull()?:return error
        val radio=legacy.groupValues[2].takeIf{it.isNotBlank()}?.toIntOrNull()?:0
        return smsFailure(code,error.startsWith("Choose a sending SIM"),radio,send.optInt("parts").takeIf{it in 1..20})
    }

    fun recoverResults(context:Context) {
        val receipts=CarrierResults(context)
        receipts.ids().forEach{id->try{receipts.read(id)?.let{applyResult(context,id,it)}}catch(e:Exception){ChatStore.lastError.value="Carrier result retained for review: ${e.message}"}}
    }

    fun pendingDownloads(context: Context): List<JSONObject> {
        val p = jobs(context)
        return p.all.keys
            .filter { it.endsWith(".location") }
            .map { it.removeSuffix(".location") }
            .filter { !p.getBoolean("$it.complete", false) }
            .map {
                JSONObject()
                    .put("id", it)
                    .put("sender", p.getString("$it.sender", "Picture message"))
                    .put("error",incomingMmsStatus(p.getString("$it.error",null),p.getLong("$it.attempt",0),System.currentTimeMillis()))
                    .put("in_progress",incomingMmsInProgress(p.getLong("$it.attempt",0),System.currentTimeMillis()))
                    .put("retry_scheduled",p.getLong("$it.retry_due",0)>0&&role(context)&&carrierEnabled(context)&&p.getLong("$it.attempt",0)==0L)
            }
    }
}

class MessageResultReceiver : BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        if(!CarrierResults.valid(intent))return
        // goAsync clears BroadcastReceiver.mPendingResult; capture the carrier result first.
        val code=resultCode;val pending=goAsync()
        ChatStore.executor.execute {try{MessagingTransport.receiveResult(context,intent,code)}catch(e:Exception){ChatStore.lastError.value=e.message}finally{pending.finish()}}
    }
}

class IncomingSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (
            intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION ||
                !MessagingTransport.role(context)
        )
            return
        val pending = goAsync()
        ChatStore.executor.execute {
            try {
                MessagingTransport.receiveSms(context, intent)
            } catch (e: Exception) {
                MessageNotifications.attention(
                    context,
                    "Text message needs attention",
                    e.message ?: "Open OnKlav",
                )
            } finally {
                pending.finish()
            }
        }
    }
}

class IncomingMmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (
            intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION ||
                !MessagingTransport.role(context)
        )
            return
        val data = intent.getByteArrayExtra("data") ?: return
        val pending = goAsync()
        ChatStore.executor.execute {
            try {
                MessagingTransport.receiveMms(
                    context,
                    data,
                    intent.getIntExtra(
                        "subscription",
                        intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, -1),
                    ),
                )
            } catch (e: Exception) {
                MessageNotifications.attention(
                    context,
                    "Picture message needs attention",
                    e.message ?: "Open OnKlav",
                )
            } finally {
                pending.finish()
            }
        }
    }
}

class RespondViaMessageService : Service() {
    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val address = intent?.data?.schemeSpecificPart?.substringBefore('?')
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)
        if (address != null && !text.isNullOrBlank())
            ChatStore.executor.execute {
                try {
                    MessagingTransport.sendSms(this, address, text)
                } catch (e: Exception) {
                    MessageNotifications.attention(
                        this,
                        "Reply not sent",
                        e.message ?: "Open OnKlav",
                    )
                } finally {
                    stopSelf(startId)
                }
            }
        else stopSelf(startId)
        return START_NOT_STICKY
    }
}
