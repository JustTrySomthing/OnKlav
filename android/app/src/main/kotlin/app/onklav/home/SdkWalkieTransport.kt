package app.onklav.home

import android.Manifest
import android.app.*
import android.content.*
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.BlockedNumberContract
import android.telephony.SmsManager
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Host-owned carrier I/O. No phone number, URI or ready flag from Wasm is trusted. */
internal object SdkWalkieTransport {
    val epoch=MutableStateFlow(0L)
    private val worker=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    private val queued=AtomicBoolean(false)
    private val jobLock=Any()
    private var visible=0
    private const val BUILTIN = "app.onklav.walkie"
    private fun verification(c:Context)=c.getSharedPreferences("walkie-invite-verification",Context.MODE_PRIVATE)

    fun cancelVerification(c:Context) = AppSdk.carrierEgress {
        val prefs=verification(c)
        if(prefs.contains("status"))check(prefs.edit().remove("peer").remove("number").remove("sub").remove("at").remove("status").commit())
    }

    private fun trackCaller(c:Context,session:JSONObject):JSONObject {
        check(verification(c).edit().putString("caller_session",session.getString("id")).commit())
        return session
    }

    private fun verificationRemaining(c:Context):Long? {
        val prefs=verification(c)
        if(prefs.getString("status","")!="checking")return null
        val elapsed=System.currentTimeMillis()-prefs.getLong("at",0)
        return (FriendGames.INVITE_TIMEOUT-elapsed).takeIf{elapsed in 0 until FriendGames.INVITE_TIMEOUT}
    }

    private fun beginBuiltin(c:Context,args:JSONObject,owner:JSONObject):JSONObject {
        val peer=args.getString("peer")
        val sub=args.getInt("subscription")
        facts(c,peer,sub,owner)
        val current=state(c,owner)
        check(current.optString("phase") in setOf("offline","ended")){"End the current Walkie session first."}
        check(verificationRemaining(c)==null){"Already checking for OnKlav."}
        val pending=verification(c)
        val stale=current.optString("reason") in setOf("timeout","uncertain","failed") && phoneKey(current.optString("peer"))==phoneKey(peer)
        if(!stale&&phoneKey(peer) in FriendGames.verifiedPeers(c) && pending.getString("peer","")!=phoneKey(peer)) {
            cancelVerification(c)
            return trackCaller(c,command(c,args,owner))
        }
        check(pending.edit().putString("peer",phoneKey(peer)).putString("number",peer)
            .putInt("sub",sub).putLong("at",System.currentTimeMillis()).putString("status","checking").remove("caller_session").commit())
        try { FriendGames.sendVerificationQuestion(c,peer,sub) }
        catch(e:Exception) { cancelVerification(c);throw e }
        TransportRecovery.schedule(c,FriendGames.INVITE_TIMEOUT)
        return JSONObject().put("phase","checking").put("peer",peer).put("subscription",sub)
    }

    fun observeVerification(c:Context,peer:String,sub:Int,body:String) {
        if(!FriendGameWire.appResponse(body))return
        AppSdk.carrierEgress {
            val pending=verification(c)
            val expected=pending.getInt("sub",-1)
            if(pending.getString("peer","")!=phoneKey(peer)||expected!=sub||verificationRemaining(c)==null)return@carrierEgress
            val number=pending.getString("number","").orEmpty()
            val started=runCatching {
                val owner=SdkCarrierRoutes.builtin(c)
                trackCaller(c,command(c,JSONObject().put("operation","begin").put("peer",number).put("subscription",expected),owner))
            }.isSuccess
            if(started) {cancelVerification(c);FriendGames.recordVerified(c,peer)}
            else check(pending.edit().putString("status","failed").commit())
            epoch.value++
            kick(c)
        }
    }

    /** Use the existing signed controller's Accept and offline recorder actions. */
    fun presentBuiltin(c:Context,frame:JSONObject) {
        val state=frame.child("state")
        val session=state.child("meta").child("session")
        val body=frame.optJSONArray("body") ?: return
        val pending=verification(c)
        val peer=state.optString("peer")
        val samePeer=peer.isNotBlank()&&pending.getString("peer","")==phoneKey(peer)
        val checking=samePeer&&verificationRemaining(c)!=null
        val callerEnded=session.optString("phase")=="ended"&&!session.optBoolean("incoming")&&
            phoneKey(session.optString("peer"))==phoneKey(peer)&&session.optString("reason") in setOf("timeout","declined","uncertain","failed")
        val unavailable=!checking&&(samePeer||callerEnded)
        fun nodes(key:String)=frame.optJSONArray(key)?.let{array->(0 until array.length()).mapNotNull{array.optJSONObject(it)}}.orEmpty()
        if(session.optString("phase")=="incoming") {
            val caller=MessageRepository(c).contactName(session.optString("peer"))
            nodes("menu").firstOrNull{it.optString("id")=="invite.help"}?.put("text","$caller wants to talk on Walkie-Talkie.")
            nodes("menu").firstOrNull{it.optString("id")=="invite.accept"}?.put("text","Accept call")
        }
        if(!checking&&!unavailable)return
        val name=state.optString("label").ifBlank{peer}.ifBlank{"Your contact"}
        val text=when {
            checking->"Checking whether $name has OnKlav…"
            callerEnded&&session.optString("reason")=="declined"->"$name declined your call. You can leave a voice message."
            else->"$name wasn't available to take your call. You can leave a voice message."
        }
        val status=nodes("body").firstOrNull{it.optString("id")=="status"}
            ?: JSONObject().put("kind","label").put("id","invite.host.status").put("center",true).also{body.put(it)}
        status.put("text",text)
        nodes("body").firstOrNull{it.optString("id")=="connect"}?.put("enabled",!checking)
            ?.put("text",if(checking)"Checking OnKlav…" else "Try Again")
        if(unavailable&&!state.optBoolean("record_panel")) {
            // A body button keeps the existing signed action discoverable without opening the toolbar.
            body.put(JSONObject().put("kind","button").put("id","record.toggle").put("text","Leave a message"))
        }
    }
    fun checkSetup(c:Context) {
        check(MessagingTransport.carrierEnabled(c) && MessagingTransport.role(c) &&
            MessagingTransport.permission(c,Manifest.permission.SEND_SMS) &&
            MessagingTransport.permission(c,Manifest.permission.RECEIVE_SMS)) {
            "Complete messaging Setup and carrier verification before using Walkie. No invitation was sent."
        }
    }
    fun facts(c:Context,peer:String,sub:Int,owner:JSONObject?=null):JSONObject {
        check(owner==null||!c.getSharedPreferences("sdk-native-data",0).getStringSet("transport_cleanup",emptySet()).orEmpty().contains(owner.getString("id"))){"App transport cleanup is pending"}
        checkSetup(c)
        check((owner!=null&&owner.optString("id")!="app.onklav.walkie")||!c.getSharedPreferences("sdk-native-data",0).getBoolean("walkie_cleanup",false)){"Walkie removal is still being completed"}
        val sm=MessagingTransport.manager(c,sub.takeIf {it>=0})
        // Failure to read either policy fails closed; no automatic reply is sent.
        val state=ChatStore(c).requestSync(JSONObject().put("action","snapshot"))
        val blocked=state.optJSONArray("blocked") ?: JSONArray()
        val key=phoneKey(peer)
        check((0 until blocked.length()).none {phoneKey(blocked.getString(it))==key} &&
            !BlockedNumberContract.isBlocked(c,peer)) {"This recipient is blocked"}
        return JSONObject().put("mms_verified",MessagingTransport.MMS_READY).put("carrier_test_enabled",MessagingTransport.carrierEnabled(c))
            .put("default_role",MessagingTransport.role(c))
            .put("send_sms",MessagingTransport.permission(c,Manifest.permission.SEND_SMS))
            .put("receive_sms",MessagingTransport.permission(c,Manifest.permission.RECEIVE_SMS))
            .put("allowed",true).put("peer",peer).put("subscription",sm.subscriptionId)
    }
    private fun call(c:Context,args:JSONObject,owner:JSONObject?=null):Any = AppSdk.sync(c,JSONObject().put("action","walkie_carrier").put("owner",owner).put("args",args))
    fun state(c:Context,owner:JSONObject?=null)=call(c,JSONObject().put("operation","state"),owner) as JSONObject
    fun command(c:Context,args:JSONObject,owner:JSONObject?=null):JSONObject {
        val copy=JSONObject(args.toString())
        // Carrier sender formatting may add +1 to a selected ten-digit US number.
        // Resolve only inbound packets against this owner's existing session and SIM;
        // native session IDs, frame validation and authorization still apply.
        if(copy.optString("operation") in setOf("receive","clip.receive")) {
            val current=state(c,owner)
            val saved=current.optString("peer")
            if(saved.isNotEmpty()&&current.optInt("subscription",-1)==copy.optInt("subscription",-1)&&
                phoneKey(saved)==phoneKey(copy.getString("peer")))copy.put("peer",saved)
        }
        copy.put("facts",facts(c,copy.getString("peer"),copy.optInt("subscription",-1),owner))
        return call(c,copy,owner) as JSONObject
    }
    fun connect(c:Context,authorized:JSONObject,owner:JSONObject?=null):JSONObject {
        val out=if(owner!=null&&owner.optString("id")==BUILTIN&&authorized.optString("operation")=="begin") AppSdk.carrierEgress{beginBuiltin(c,authorized,owner)} else command(c,authorized,owner)
        epoch.value++;kick(c);return out
    }
    /** Reauthorize and commit under the same lock as install/removal/grant changes. */
    fun confirm(c:Context,authorize:()->JSONObject):JSONObject=confirmedRequest(authorize){request->
        if(request.getString("operation")=="retry"){sendQueued(c,request.getString("clip"),request.getJSONObject("_owner"));JSONObject().put("queued",true)}else connect(c,request,request.getJSONObject("_owner"))
    }
    internal fun confirmedRequest(authorize:()->JSONObject,execute:(JSONObject)->JSONObject):JSONObject=AppSdk.carrierEgress{
        val authorized=authorize()
        check(authorized.getString("service")=="communications.walkie"){"Not a carrier communication request"}
        val owner=authorized.getJSONObject("owner")
        SdkCarrierRoutes.route(owner)
        val args=JSONObject(authorized.getJSONObject("args").toString())
        check(args.getString("operation")in setOf("begin","accept","retry")){"Unsupported confirmed carrier operation"}
        args.put("_owner",JSONObject(owner.toString()));execute(args)
    }
    fun watch(c:Context):()->Unit {
        visible++
        val task=object:Runnable {override fun run(){if(visible>0){kick(c);main.postDelayed(this,1000)}}}
        main.post(task)
        return {visible=(visible-1).coerceAtLeast(0);main.removeCallbacks(task)}
    }
    private fun dir(c:Context)=File(c.filesDir,"walkie-carrier-jobs").apply {check(mkdirs()||isDirectory)}
    private fun id()=UUID.randomUUID().toString().replace("-","")
    private fun file(c:Context,id:String):File {require(id.matches(Regex("[a-f0-9]{32}")));return File(dir(c),"$id.json")}
    private fun store(c:Context,id:String,j:JSONObject) {
        check(file(c,id).exists() || dir(c).listFiles().orEmpty().count {it.extension=="json"}<64) {"Too many unconfirmed Walkie sends"}
        val temp=File(dir(c),"$id.tmp")
        temp.outputStream().use {it.write(j.toString().toByteArray());it.fd.sync()}
        check(temp.renameTo(file(c,id)))
        TransportRecovery.schedule(c,90_000)
    }
    private fun callback(c:Context,job:String,part:Int=-1):PendingIntent=PendingIntent.getBroadcast(c,0,
        Intent(c,SdkWalkieResultReceiver::class.java).setAction("app.onklav.WALKIE_RESULT")
            .setData(Uri.parse("onklav-walkie://result/$job"+if(part>=0)"/$part"else"")).putExtra("job",job).putExtra("part",part),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
    fun kick(context:Context) {
        if(!MessagingTransport.carrierEnabled(context) || !queued.compareAndSet(false,true))return
        val c=context.applicationContext
        worker.execute {try{recover(c)?.let{TransportRecovery.schedule(c,it)}}catch(e:Exception){ChatStore.lastError.value=e.message}finally{queued.set(false)}}
    }
    /** One pass: at most16 control sends and4 clips across all eligible app owners. */
    fun recover(c:Context):Long? {
        if(!MessagingTransport.carrierEnabled(c)||runCatching{checkSetup(c)}.isFailure)return null
        val now=System.currentTimeMillis();val next=mutableListOf<Long>()
        verificationRemaining(c)?.let{next.add(now+it)}
        dir(c).listFiles().orEmpty().filter{it.extension=="json"}.take(64).forEach{f->runCatching{
            require(f.length()<=8192);val row=JSONObject(f.readText())
            if(row.has("callback_ok")||now-row.optLong("at")>=90000)finish(c,f.nameWithoutExtension,false,null,"uncertain")
            else next.add(row.getLong("at")+90000)
        }.onFailure{ChatStore.lastError.value="A carrier receipt remains pending";next.add(now+900000)}}
        val owners=SdkCarrierRoutes.owners(c);val prefs=c.getSharedPreferences("sdk-carrier",0);val start=prefs.getInt("cursor",0).let{if(owners.isEmpty())0 else Math.floorMod(it,owners.size)}
        var controls=0;var clipsSent=0
        for(offset in owners.indices){val owner=owners[(start+offset)%owners.size]
            runCatching{
                for(turn in 0 until 16){val target=call(c,JSONObject().put("operation","peek"),owner);if(target !is JSONObject)break
                    if(controls>=16){next.add(now+1000);break}
                    val packet=call(c,JSONObject().put("operation","next").put("facts",facts(c,target.getString("peer"),target.getInt("subscription"),owner)),owner)
                    if(packet is JSONObject){controls++;sendControl(c,packet,owner)}
                }
                val current=state(c,owner);current.optLong("next_wakeup").takeIf{it>0}?.let{next.add(it)}
                if(owner.optString("id")==BUILTIN) reportUnavailable(c,owner,current)
                if(current.optString("phase")=="connected"){
                    val clips=AppSdk.sync(c,JSONObject().put("action","walkie_archive_list").put("owner",owner))as JSONArray
                    for(n in 0 until clips.length()){val clip=clips.getJSONObject(n)
                        if(clip.optBoolean("outgoing")&&clip.optString("state")=="queued"&&clip.optString("session")==current.optString("id")&&clip.optString("peer")==current.optString("peer")){
                            if(clipsSent>=4){next.add(now+1000);break};clipsSent++;sendClip(c,clip,owner)
                        }
                    }
                }
            }.onFailure{ChatStore.lastError.value=it.message}
        }
        if(owners.isNotEmpty())prefs.edit().putInt("cursor",(start+1)%owners.size).apply()
        epoch.value++;return next.minOrNull()?.let{(it-now).coerceIn(1000L,15*60*1000L)}
    }
    private fun reportUnavailable(c:Context,owner:JSONObject,current:JSONObject) {
        val outcome=AppSdk.carrierEgress {
            val prefs=verification(c)
            if(prefs.getString("status","")=="checking" && verificationRemaining(c)==null) {
                check(prefs.edit().putString("status","expired").commit())
                return@carrierEgress prefs.getString("number","").orEmpty() to "timeout"
            }
            val id=current.optString("id")
            if(id.isEmpty()||prefs.getString("caller_session","")!=id||current.optString("phase")!="ended")return@carrierEgress null
            check(prefs.edit().remove("caller_session").commit())
            current.optString("reason").takeIf{it in setOf("timeout","declined","failed","uncertain")}
                ?.let{current.optString("peer") to it}
        } ?: return
        val name=MessageRepository(c).contactName(outcome.first)
        val text=if(outcome.second=="declined") "$name declined your call." else "$name wasn't available to take your call."
        notify(c,"Walkie-Talkie", "$text Tap to leave a message.",owner)
    }
    private fun sendControl(c:Context,p:JSONObject,owner:JSONObject) {
        val peer=p.getString("peer");val sub=p.getInt("subscription");val wire=SdkCarrierRoutes.encode(owner,p.getString("wire"))
        require(wire.length<=220 && (wire.startsWith("!OKW1|")||wire.startsWith("!OKW2|")))
        val job=id();val request=p.getJSONObject("callback").put("attempt",p.getString("attempt"))
        val entry=JSONObject().put("owner",owner).put("kind","control").put("request",request).put("peer",peer).put("sub",sub).put("at",System.currentTimeMillis())
        try {AppSdk.carrierEgress {
            call(c,JSONObject(p.toString()).put("operation","authorize.send").put("facts",facts(c,peer,sub,owner)),owner)
            store(c,job,entry)
            val sm=MessagingTransport.manager(c,sub);val parts=sm.divideMessage(wire)
            if(parts.size==1)sm.sendTextMessage(peer,null,wire,callback(c,job),null)
            else{entry.put("parts",JSONArray().apply{repeat(parts.size){put(JSONObject.NULL)}});store(c,job,entry);sm.sendMultipartTextMessage(peer,null,parts,ArrayList(parts.indices.map{callback(c,job,it)}),null)}
        }}
        catch(e:Exception){finish(c,job,false,null,"uncertain");throw e}
    }
    fun sendQueued(context:Context,clip:String,owner:JSONObject?=null) {
        val bound=owner?:SdkCarrierRoutes.builtin(context)
        val c=context.applicationContext
        worker.execute {try {
            val info=AppSdk.sync(c,JSONObject().put("action","walkie_clip_info").put("owner",bound).put("clip",clip)) as JSONObject
            val binding=info.getJSONObject("binding")
            sendClip(c,JSONObject(binding.toString()),bound)
        }catch(e:Exception){ChatStore.lastError.value=e.message}}
    }
    private fun sendClip(c:Context,clip:JSONObject,owner:JSONObject) {
        val peer=clip.getString("peer");val sub=clip.getInt("subscription")
        val job=id()
        try { AppSdk.carrierEgress {
        val p=call(c,JSONObject().put("operation","clip.send").put("clip",clip.getString("id")).put("facts",facts(c,peer,sub,owner)),owner) as JSONObject
        val data=VoiceAudio.from(p)
        MessageTransferProvider.file(c,job,"blob").writeBytes(data.bytes)
        val request=JSONObject().put("operation","clip.result").put("binding",p.getJSONObject("binding")).put("attempt",p.getString("attempt"))
        val entry=JSONObject().put("owner",owner).put("kind","clip").put("request",request).put("peer",peer).put("sub",sub).put("at",System.currentTimeMillis())
        store(c,job,entry)
            val composed=MessagingTransport.codec(c,JSONObject().put("action","compose").put("id",job)
                .put("recipients",JSONArray(listOf(peer))).put("text",if(p.getString("text").startsWith("!OKV1|"))SdkCarrierRoutes.encode(owner,p.getString("text"))else p.getString("text"))
                .put("parts",JSONArray().put(JSONObject().put("file",job).put("mime",data.mime)))
                .put("date",System.currentTimeMillis()/1000))
            val sm=MessagingTransport.manager(c,sub)
            val max=sm.carrierConfigValues?.getInt(SmsManager.MMS_CONFIG_MAX_MESSAGE_SIZE,300000) ?: 300000
            require(composed.getInt("bytes")<=max) {"Voice clip exceeds this carrier's MMS limit"}
                call(c,JSONObject().put("operation","authorize.send").put("kind","clip").put("clip",clip.getString("id")).put("attempt",p.getString("attempt")).put("facts",facts(c,peer,sub,owner)),owner)
                sm.sendMultimediaMessage(c,MessageTransferProvider.uri(c,job,"pdu","application/vnd.wap.mms-message"),null,null,callback(c,job))
        }}catch(e:Exception){finish(c,job,false,null,"uncertain");throw e}
    }
    fun result(c:Context,intent:Intent,code:Int) {
        val job=intent.getStringExtra("job") ?: return
        val part=intent.getIntExtra("part",-1)
        if(!job.matches(Regex("[a-f0-9]{32}")) || intent.action!="app.onklav.WALKIE_RESULT" || intent.data!=Uri.parse("onklav-walkie://result/$job"+if(part>=0)"/$part"else""))return
        try{finish(c,job,code==Activity.RESULT_OK,intent.getByteArrayExtra(SmsManager.EXTRA_MMS_DATA),null,part)}finally{TransportRecovery.schedule(c,60_000);kick(c)}
    }
    private fun finish(c:Context,job:String,accepted:Boolean,response:ByteArray?,forced:String?,part:Int=-1) = synchronized(jobLock) {
        val f=file(c,job);if(!f.exists())return@synchronized
        require(f.length()<=8192)
        val j=JSONObject(f.readText())
        if(j.has("parts")){
            val parts=j.getJSONArray("parts")
            if(forced==null){
                if(part !in 0 until parts.length())return@synchronized
                if(!parts.isNull(part)&&parts.getBoolean(part)!=accepted)return@synchronized
                if(j.has("callback_ok"))return@synchronized
                if(parts.isNull(part)){parts.put(part,accepted);store(c,job,j)}
            }
            if((0 until parts.length()).any{parts.isNull(it)}){if(forced==null)return@synchronized}
            else if(!j.has("callback_ok")){j.put("callback_ok",(0 until parts.length()).all{parts.getBoolean(it)});store(c,job,j)}
        }
        // Receipt is durable even when permission/SIM/policy has changed. Never delete it on failure.
        if(forced==null){
            if(!j.has("parts"))j.put("callback_ok",accepted)
            if(response!=null){require(response.size<=1024*1024);val payload=File(dir(c),"$job.response");payload.outputStream().use{it.write(response);it.fd.sync()};j.put("callback_response",true)}
            store(c,job,j)
        }
        var ok=j.optBoolean("callback_ok",accepted)
        val payload=File(dir(c),"$job.response")
        if(ok&&j.optBoolean("callback_response")&&j.optString("kind")=="clip") {
            require(payload.length() in 1..1024*1024)
            val temp=id();val parsedFile=MessageTransferProvider.file(c,temp,"pdu")
            try{parsedFile.writeBytes(payload.readBytes());val parsed=MessagingTransport.codec(c,JSONObject().put("action","parse").put("id",temp));ok=parsed.getInt("kind")==129&&parsed.optInt("status")==128}finally{parsedFile.delete()}
        }
        val request=j.getJSONObject("request").put("peer",j.getString("peer")).put("subscription",j.getInt("sub"))
        val result=if(j.has("callback_ok")){if(ok)"carrier"else"failed"}else forced?:"uncertain"
        if(j.getString("kind")=="clip")request.put("result",result)
        else request.put("outcome",if(result=="carrier")"accepted"else result)
        val owner=j.optJSONObject("owner")?:SdkCarrierRoutes.legacyOwner(c)
        request.put("facts",facts(c,j.getString("peer"),j.getInt("sub"),owner))
        val resultState=call(c,request,owner) as? JSONObject
        if(j.optString("kind")=="control"&&request.optString("operation")=="sent"&&resultState?.optString("phase")=="connected")
            runCatching{FriendGames.recordVerified(c,j.getString("peer"))}
        check(f.delete())
        payload.delete();MessageTransferProvider.file(c,job,"blob").delete();MessageTransferProvider.file(c,job,"pdu").delete();epoch.value++
    }
    fun packageRemoved(c:Context,appId:String="app.onklav.walkie")=synchronized(jobLock){
        dir(c).listFiles().orEmpty().filter{it.extension=="json"}.forEach{record->
            val job=record.nameWithoutExtension
            val owner=runCatching{require(record.length()<=8192);JSONObject(record.readText()).optJSONObject("owner")?.optString("id")?:"app.onklav.walkie"}.getOrNull()
            if(owner!=appId)return@forEach
            c.revokeUriPermission(MessageTransferProvider.uri(c,job,"pdu"),Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            for(file in listOf(MessageTransferProvider.file(c,job,"blob"),MessageTransferProvider.file(c,job,"pdu"),File(dir(c),"$job.response"),File(dir(c),"$job.tmp"),record))check(!file.exists()||file.delete()){"App carrier cleanup remains pending"}
        }
        c.getSystemService(NotificationManager::class.java).cancel("sdk-carrier-$appId",410)
    }
    fun receiveControl(c:Context,peer:String,sub:Int,wire:String):Boolean {
        val routed=runCatching{SdkCarrierRoutes.decode(wire,SdkCarrierRoutes.owners(c))}.getOrNull()?:return false
        val owner=routed.first;val inner=routed.second
        if(!inner.startsWith("!OKW1|"))return false
        return try {
            val out=command(c,JSONObject().put("operation","receive").put("peer",peer).put("subscription",sub).put("wire",inner),owner)
            if(out.optString("phase")=="connected")runCatching{FriendGames.recordVerified(c,peer)}
            if(out.optString("phase")=="incoming")notify(c,"Walkie Talkie invitation",MessageRepository(c).contactName(peer)+" wants to talk. Tap to accept or decline.",owner)
            epoch.value++;kick(c);true
        }catch(e:Exception){
            runCatching {checkSetup(c);val a=JSONObject().put("operation","classify").put("wire",inner).put("facts",facts(c,peer,sub,owner));(call(c,a,owner) as JSONObject).optBoolean("valid")}.getOrDefault(false)
        }
    }
    fun receiveVoice(c:Context,pdu:JSONObject,sub:Int):Boolean {
        val parts=pdu.optJSONArray("parts") ?: return false
        val rows=List(parts.length()){parts.getJSONObject(it)}
        val text=rows.filter {it.optString("mime")=="text/plain"}.joinToString(""){it.optString("text")}
        val routed=runCatching{SdkCarrierRoutes.decode(text,SdkCarrierRoutes.owners(c))}.getOrNull()?:return false
        val owner=routed.first;val inner=routed.second
        if(!inner.startsWith("!OKV1|"))return false
        return try {
            require(rows.size==2 && rows.count {it.optString("mime") in setOf("audio/amr","audio/amr-wb")}==1)
            val audio=rows.first {it.has("file")};val f=MessageTransferProvider.file(c,audio.getString("file"),"blob")
            require(f.length() in 1..65536)
            val a=VoiceAudio(f.readBytes(),audio.getString("mime")).json().put("operation","clip.receive")
                .put("text",inner).put("peer",pdu.getString("from")).put("subscription",sub)
            val out=command(c,a,owner)
            if(out.optBoolean("fallback")){rows.filter{it.optString("mime")=="text/plain"}.forEach{it.put("text","Voice message")};return false}
            if(!out.optBoolean("duplicate"))notify(c,"Voice message ready","Open the app to listen",owner)
            f.delete();epoch.value++;kick(c);true
        }catch(e:Exception){false}
    }
    private fun notify(c:Context,title:String,text:String,owner:JSONObject) {
        if(MessageNotifications.quiet(c))return
        val manager=c.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("walkie","Walkie Talkie",NotificationManager.IMPORTANCE_HIGH))
        val open=PendingIntent.getActivity(c,410,Intent(c,MainActivity::class.java).setData(Uri.parse("onklav://carrier/"+SdkCarrierRoutes.route(owner))).putExtra("open_sdk_carrier",SdkCarrierRoutes.route(owner))
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        runCatching {manager.notify("sdk-carrier-"+owner.getString("id"),410,androidx.core.app.NotificationCompat.Builder(c,"walkie").setSmallIcon(app.onklav.shared.R.drawable.ref_setting_walkie_talkie).setContentTitle(title).setContentText(text).setContentIntent(open).setAutoCancel(true).build())}
    }
}
class SdkWalkieResultReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        val code=resultCode;val pending=goAsync()
        ChatStore.executor.execute {try {SdkWalkieTransport.result(context,intent,code)}catch(e:Exception){ChatStore.lastError.value=e.message}finally{pending.finish()}}
    }
}
