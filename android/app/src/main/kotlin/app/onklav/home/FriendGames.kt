package app.onklav.home

import android.app.Activity
import android.app.PendingIntent
import android.content.*
import android.net.Uri
import android.provider.BlockedNumberContract
import android.util.AtomicFile
import org.json.*
import java.io.*
import java.util.UUID
import java.util.concurrent.Executors

/** Host-owned SMS turn channel. Every command is scoped to the activity's accepted match.
 * Atomic transactions/OS locks cross main/:games; carrier sends occur after durable staging.
 */
internal object FriendGames {
    private val monitor=Any()
    private val worker=Executors.newSingleThreadExecutor()
    private const val LIMIT=512*1024
    internal const val INVITE_TIMEOUT = 65_000L
    private const val RETAIN_CONTROL = 7 * 86_400_000L
    fun work(action:()->Unit){worker.execute(action)}
    private fun <T> transaction(c:Context,write:Boolean=true,action:(JSONObject)->T):T = synchronized(monitor) {
        val dir=File(c.noBackupFilesDir,"friend-games").apply{check(isDirectory||mkdirs())}
        FileOutputStream(File(dir,"state.lock"),true).channel.use{channel->channel.lock().use{
            val file=AtomicFile(File(dir,"state.json"))
            val root=if(file.baseFile.exists()){check(file.baseFile.length()<=LIMIT);file.openRead().use{JSONObject(it.bufferedReader().readText())}}else JSONObject().put("matches",JSONObject()).put("answers",JSONObject())
            val before=root.toString()
            val result=action(root)
            if(write && (root.toString()!=before || !file.baseFile.exists())){val bytes=root.toString().toByteArray();check(bytes.size<=LIMIT){"Game history is full. Remove a completed match first."};val stream=file.startWrite();try{stream.write(bytes);file.finishWrite(stream)}catch(e:Exception){file.failWrite(stream);throw e}}
            result
        }}
    }
    private fun now()=System.currentTimeMillis()
    private fun id()=UUID.randomUUID().toString().replace("-","")
    fun token(c:Context,game:String):String=when(game){"hockey","dominos","panda",GamePackages.SHROOM_ID,GamePackages.LAST_LIGHT_ID->error("This game is single player only");"naval"->"nb1";"four"->"fc1";"blackjack"->"bj3";else->{val g=GamePackages.open(c).installed().firstOrNull{it.id==game}?:error("Install this game first");check(g.manifest.optString("profile")=="turn_based" && g.manifest.optJSONArray("play_modes")?.toString()?.contains("turn_sms")==true){"This game is single player only"};"p"+g.hash}}
    private fun game(c:Context,token:String)=when(token){"pt1","pt2","pt3"->null;"nb1"->"naval";"fc1","fd1"->"four";"bj3"->"blackjack";else->GamePackages.open(c).installed().firstOrNull{"p"+it.hash==token && it.manifest.optString("profile")=="turn_based" && it.manifest.optJSONArray("play_modes")?.toString()?.contains("turn_sms")==true}?.id}
    private fun rememberVerified(root:JSONObject,peer:String) {
        val peers=root.optJSONObject("verified_peers")?:JSONObject().also{root.put("verified_peers",it)}
        peers.put(phoneKey(peer),now())
        if(peers.length()>64)peers.keys().asSequence().minByOrNull{peers.getLong(it)}?.let{peers.remove(it)}
    }
    fun recordVerified(c:Context,peer:String) { transaction(c){rememberVerified(it,peer)} }
    fun forgetVerified(c:Context,peer:String) { transaction(c){it.optJSONObject("verified_peers")?.remove(phoneKey(peer))} }
    fun verifiedPeers(c:Context):Set<String> = transaction(c,false){root->root.optJSONObject("verified_peers")?.keys()?.asSequence()?.toSet()?:emptySet()}
    internal fun sendVerificationQuestion(c: Context, peer: String, sub: Int) = raw(c, peer, sub, FriendGameWire.QUESTION, null, null)

    private fun rememberControl(root:JSONObject, table:String, key:String, time:Long, limit:Int):Boolean {
        val entries=root.optJSONObject(table)?:JSONObject().also{root.put(table,it)}
        entries.keys().asSequence().filter{time-entries.optLong(it)>RETAIN_CONTROL}.toList().forEach{entries.remove(it)}
        if(entries.has(key))return false
        if(entries.length()>=limit)entries.keys().asSequence().minByOrNull{entries.optLong(it)}?.let{entries.remove(it)}
        entries.put(key,time)
        return true
    }

    /** Claim verification before dispatch so one carrier delivery can't answer or advance two attempts. */
    internal fun claimVerificationSms(c:Context,id:String,received:Long):Boolean {
        val time=now()
        if(time-received !in 0..INVITE_TIMEOUT)return false
        return transaction(c){rememberControl(it,"verification_receipts",id,time,512)}
    }

    private fun retiredKey(id:String,peer:String,sub:Int)="$id:${phoneKey(peer)}:$sub"
    private fun wasRetired(root:JSONObject,id:String,peer:String,sub:Int):Boolean {
        val entries=root.optJSONObject("retired_matches")?:return false
        return listOf(sub,-1).any{now()-entries.optLong(retiredKey(id,peer,it)) in 0..RETAIN_CONTROL}
    }
    fun verify(c:Context,peer:String) {
        policy(c,peer)
        val sub = MessagingTransport.manager(c).subscriptionId
        transaction(c){root->root.put("verification",JSONObject().put("peer",phoneKey(peer)).put("sub",sub).put("status","checking").put("at",now()))}
        sendVerificationQuestion(c,peer,sub)
    }
    fun verification(c:Context,peer:String):String = transaction(c){root->
        val check=root.optJSONObject("verification")
        if(check==null||check.optString("peer")!=phoneKey(peer))return@transaction "idle"
        if(check.optString("status")=="checking"&&now()-check.optLong("at")>65000)check.put("status","expired")
        check.getString("status")
    }
    fun cancelVerification(c:Context,peer:String) {transaction(c){root->if(root.optJSONObject("verification")?.optString("peer")==phoneKey(peer))root.remove("verification")}}
    fun validNumber(peer:String)=peer.matches(Regex("[+0-9 ().-]{3,40}")) && phoneKey(peer).length in 3..20
    fun blocking(c:Context)=list(c).firstOrNull{it.optString("status") in setOf("active","checking","outgoing","invite")}
    /** Cancel only an invitation. Acceptance racing dismissal must preserve the live game. */
    fun cancelInvitation(c:Context,id:String) {
        transaction(c){root->
            val m=root.getJSONObject("matches").optJSONObject(id)?:return@transaction
            if(m.optString("status")=="active")return@transaction
            if(m.optString("status") in setOf("ended","declined"))return@transaction
            expire(m)
            val status=m.optString("status")
            val incoming=status=="invite"
            m.put("status",if(incoming)"declined"else"ended")
            val jobs=m.getJSONObject("jobs")
            jobs.keys().forEach{jobs.getJSONObject(it).put("state","cancelled")}
            if (incoming || status=="outgoing") enqueue(m,FriendGameWire(if(incoming)"D"else"Q",id,if(incoming)0 else 9999,if(incoming)m.getString("token")else"quit").encode(),"cancel")
        };flush(c,id)
    }
    fun restartInvitation(c:Context,id:String?,peer:String,game:String,dogs:Boolean):String {
        val old = id?.let { find(c,it) }
        val selectedDogs = if (game == "four" && old != null) old.optString("token") == "fd1" else dogs
        if(id!=null){
            if(old?.optString("status")=="active")return id
            if(old?.optString("status")=="expired")forgetVerified(c,peer)
            cancelInvitation(c,id)
            // Old attempts must not exhaust the bounded match history on repeated retries.
            if(find(c,id)?.optString("status") in setOf("ended","declined"))remove(c,id)
        }
        return invite(c,peer,game,selectedDogs)
    }
    fun policy(c:Context,peer:String,sub:Int=-1) {
        check(MessagingTransport.role(c)&&MessagingTransport.permission(c,android.Manifest.permission.SEND_SMS)&&MessagingTransport.permission(c,android.Manifest.permission.RECEIVE_SMS)){"Complete messaging Setup before inviting a friend."}
        check(validNumber(peer)){"Enter a mobile number."}
        val blocked=ChatStore(c).requestSync(JSONObject().put("action","snapshot")).optJSONArray("blocked")?:JSONArray()
        check((0 until blocked.length()).none{phoneKey(blocked.getString(it))==phoneKey(peer)} && !BlockedNumberContract.isBlocked(c,peer)){"This recipient is blocked."}
        MessagingTransport.manager(c,sub.takeIf{it>=0})
    }
    fun list(c:Context):List<JSONObject> = transaction(c){root->val all=root.getJSONObject("matches");all.keys().forEach{expire(all.getJSONObject(it))};all.keys().asSequence().map{JSONObject(all.getJSONObject(it).toString())}.sortedByDescending{it.optLong("at")}.toList()}
    fun find(c:Context,id:String)=list(c).firstOrNull{it.getString("id")==id}
    fun markCurrent(c:Context,id:String?){transaction(c){root->root.put("current_match",id.orEmpty())}}
    fun resume(c:Context,game:String):String?=if(game in singlePlayerOnlyGames)null else list(c).firstOrNull{it.optString("game")==game && it.optString("status")=="active"}?.getString("id")
    fun active(c:Context,game:String)=if(game in singlePlayerOnlyGames)null else list(c).firstOrNull{it.optString("game")==game && it.optString("status")in setOf("active","outgoing","invite","checking")}
    private fun enqueue(m:JSONObject,body:String,key:String){
        val jobs=m.getJSONObject("jobs");if(jobs.has(key))return
        jobs.put(key,JSONObject().put("wire",body).put("state","queued").put("at",now()))
    }
    private fun expire(m:JSONObject) {
        val status=m.optString("status")
        if(status in setOf("checking","outgoing","invite") && now()-m.optLong("at") !in 0..INVITE_TIMEOUT) {
            m.put("status","expired").put("expired_from",status)
            val jobs=m.getJSONObject("jobs")
            for (key in listOf("question","invite")) jobs.optJSONObject(key)?.put("state","cancelled")
        }
    }
    fun invite(c:Context,peer:String,game:String,dogs:Boolean=false):String {
        policy(c,peer);val code=if(game=="four"&&dogs)"fd1"else token(c,game)
        check(active(c,game)==null){"This game already has an invitation or match."}
        val match=id();val sub=MessagingTransport.manager(c).subscriptionId
        transaction(c){root->val all=root.getJSONObject("matches");all.keys().forEach{expire(all.getJSONObject(it))};check(all.keys().asSequence().none{all.getJSONObject(it).optString("game")==game && all.getJSONObject(it).optString("status")in setOf("checking","outgoing","invite","active")}){"A game is already in progress."};check(all.length()<32){"Remove a completed match first."}
            val m=JSONObject().put("id",match).put("peer",peer).put("game",game).put("token",code).put("host",true).put("status","checking").put("at",now()).put("sub",sub).put("tx",0).put("rx",0).put("events",JSONArray()).put("buffer",JSONObject()).put("jobs",JSONObject())
            if(root.optJSONObject("verified_peers")?.has(phoneKey(peer))==true) {
                m.put("status","outgoing")
                enqueue(m,FriendGameWire("I",match,0,code).encode(),"invite")
            } else enqueue(m,FriendGameWire.QUESTION,"question")
            all.put(match,m)
        };flush(c,match);TransportRecovery.schedule(c,INVITE_TIMEOUT);return match
    }
    fun accept(c:Context,id:String,yes:Boolean){
        val row=find(c,id)?:return;check(!yes || row.optString("game") !in singlePlayerOnlyGames){"This game is single player only"};policy(c,row.getString("peer"),row.optInt("sub",-1))
        transaction(c){root->val m=root.getJSONObject("matches").getJSONObject(id);expire(m);check(m.getString("status")=="invite"){"This invitation has expired."};m.put("status",if(yes)"active"else"declined");enqueue(m,FriendGameWire(if(yes)"A"else"D",id,0,m.getString("token")).encode(),"answer")};flush(c,id)
        if(yes)markCurrent(c,id)
    }
    fun move(c:Context,id:String,game:String,data:String):Int {
        check(game !in singlePlayerOnlyGames){"This game is single player only"};val row=find(c,id)?:error("Match not found");policy(c,row.getString("peer"),row.optInt("sub",-1));check(row.getString("game")==game);check(!game.contains(".")||row.getString("token")==token(c,game)){"Game changed or was removed; start a fresh invitation."}
        val sequence=transaction(c){root->val m=root.getJSONObject("matches").getJSONObject(id);check(m.getString("status")=="active"){"Wait for the invitation to be accepted."};check(m.getJSONObject("jobs").keys().asSequence().none{it.startsWith("move:")&&m.getJSONObject("jobs").getJSONObject(it).optString("state")!="ack"}){"Waiting for delivery. Use Retry if needed."};val window=now()/60000;if(m.optLong("send_window")!=window)m.put("send_window",window).put("send_count",0);check(m.optInt("send_count")<90){"Too many text updates; wait a minute."};m.put("send_count",m.optInt("send_count")+1);val seq=m.getInt("tx")+1;val frame=FriendGameWire("X",id,seq,data);check(FriendGameWire.parse(frame.encode())!=null){"Invalid turn update"};m.put("tx",seq);enqueue(m,frame.encode(),"move:$seq");m.put("order",m.optLong("order")+1);m.getJSONObject("jobs").getJSONObject("move:$seq").put("order",m.getLong("order"));seq};flush(c,id);return sequence
    }
    fun quit(c:Context,id:String){val row=find(c,id)?:return;if(row.optString("status")!="active"){cancelInvitation(c,id);return};transaction(c){root->val m=root.getJSONObject("matches").getJSONObject(id);m.put("status","ended");if(root.optString("current_match")==id)root.put("current_match","");enqueue(m,FriendGameWire("Q",id,9999,"quit").encode(),"quit")};flush(c,id)}
    fun retry(c:Context,id:String){
        val row=find(c,id)?:return
        check(row.optString("game") !in singlePlayerOnlyGames){"This game is single player only"}
        policy(c,row.getString("peer"),row.optInt("sub",-1))
        transaction(c){root->
            val m=root.getJSONObject("matches").getJSONObject(id)
            expire(m)
            val jobs=m.getJSONObject("jobs")
            for(key in jobs.keys()) {
                val job=jobs.getJSONObject(key)
                val state=job.optString("state")
                val allowed=when(m.optString("status")) {
                    "checking"->key=="question"
                    "outgoing"->key=="invite"
                    "active"->key.startsWith("move:") || key.startsWith("ack:") || key=="answer"&&state!="sent"
                    else->false
                }
                if(allowed&&state in setOf("failed","sent","sending"))job.put("state","queued")
            }
        }
        flush(c,id)
    }
    fun remove(c:Context,id:String){val game=find(c,id)?.optString("game");transaction(c){root->val m=root.getJSONObject("matches").optJSONObject(id)?:return@transaction;check(m.optString("status")in setOf("expired","ended","declined","needs_game"));rememberControl(root,"retired_matches",retiredKey(id,m.getString("peer"),m.optInt("sub",-1)),now(),256);root.getJSONObject("matches").remove(id);if(root.optString("current_match")==id)root.put("current_match","")};if(game!=null)GameSaveStore.removeFriend(c,game,id)}
    fun state(c:Context,id:String,game:String):JSONObject {
        val row=transaction(c){root->val m=root.getJSONObject("matches").optJSONObject(id)?:error("Match not found");check(m.getString("game")==game);expire(m);JSONObject(m.toString())}
        val jobs=row.getJSONObject("jobs");val pending=jobs.keys().asSequence().any{it.startsWith("move:")&&jobs.getJSONObject(it).optString("state")!="ack"}
        val own=JSONArray();jobs.keys().asSequence().filter{it.startsWith("move:")}.sortedBy{it.substringAfter(':').toInt()}.forEach{key->val frame=FriendGameWire.parse(jobs.getJSONObject(key).getString("wire"))!!;own.put(JSONObject().put("sequence",frame.sequence).put("data",frame.data))}
        val timeline=mutableListOf<JSONObject>()
        jobs.keys().asSequence().filter{it.startsWith("move:")}.forEach{key->val j=jobs.getJSONObject(key);val f=FriendGameWire.parse(j.getString("wire"))!!;timeline+=JSONObject().put("own",true).put("sequence",f.sequence).put("data",f.data).put("order",j.optLong("order"))}
        val events=row.getJSONArray("events");for(i in 0 until events.length()){val e=JSONObject(events.getJSONObject(i).toString());e.put("own",false);timeline+=e}
        val ordered=JSONArray();timeline.sortedBy{it.optLong("order")}.forEach{ordered.put(it)}
        return JSONObject().put("timeline",ordered).put("own",own).put("id",id).put("host",row.getBoolean("host")).put("status",row.getString("status")).put("events",row.getJSONArray("events")).put("pending",pending).put("error",row.optString("error"))
    }
    /** Protected SMS receiver calls this before ordinary-message filing. */
    fun receive(c:Context,peer:String,sub:Int,body:String):Boolean {
        val frame=FriendGameWire.parse(body)
        val app=FriendGameWire.appResponse(body)
        val question=FriendGameWire.isQuestion(body)
        if(!question && !app && frame==null)return false
        try {
            policy(c,peer,sub)
            val send=mutableSetOf<String>();var reply=false;val alerts=mutableSetOf<String>();var unavailable:String?=null
            transaction(c){root->val all=root.getJSONObject("matches")
                if(question){val a=root.getJSONObject("answers");val key=phoneKey(peer);val day=now()/86400000;val count=if(a.optLong("day")==day)a.optInt("count")else 0
                    if(now()-a.optLong(key)>2000 && count<120){a.put(key,now()).put("day",day).put("count",count+1);reply=true};return@transaction}
                if(app){
                    val verification=root.optJSONObject("verification")
                    if(verification?.optString("peer")==phoneKey(peer)&&sameSim(verification,sub)&&verification.optString("status")=="checking"&&now()-verification.optLong("at") in 0..INVITE_TIMEOUT){verification.put("status","verified");rememberVerified(root,peer)}
                    val waiting=all.keys().asSequence().map{all.getJSONObject(it)}.filter{
                        phoneKey(it.getString("peer"))==phoneKey(peer)&&sameSim(it,sub)&&it.optString("status")=="checking"&&
                            now()-it.optLong("at") in 0..INVITE_TIMEOUT&&it.optString("game") !in singlePlayerOnlyGames
                    }.toList()
                    if(waiting.isNotEmpty())rememberVerified(root,peer)
                    waiting.forEach { m ->
                        m.put("status","outgoing").put("at",now())
                        enqueue(m,FriendGameWire("I",m.getString("id"),0,m.getString("token")).encode(),"invite")
                        send+=m.getString("id")
                    }
                    return@transaction}
                val f=frame?:return@transaction
                var m=all.optJSONObject(f.id)
                if(m==null && f.type=="Q" && knowsPeer(root,peer)) {
                    rememberControl(root,"retired_matches",retiredKey(f.id,peer,sub),now(),256)
                    return@transaction
                }
                if(m==null && f.type=="I"){
                    if(!knowsPeer(root,peer)||wasRetired(root,f.id,peer,sub))return@transaction
                    val game=game(c,f.data) ?: run {val answers=root.getJSONObject("answers");val key="missing:"+phoneKey(peer);if(now()-answers.optLong(key)>60000){answers.put(key,now());unavailable=FriendGameWire("N",f.id,0,f.data).encode()};return@transaction}
                    all.keys().forEach{expire(all.getJSONObject(it))}
                    if(all.keys().asSequence().any{all.getJSONObject(it).optString("game")==game && all.getJSONObject(it).optString("status")in setOf("active","checking","outgoing","invite")})return@transaction
                    if(all.length()>=32)return@transaction
                    rememberVerified(root,peer)
                    alerts+=f.id
                    m=JSONObject().put("id",f.id).put("game",game).put("peer",peer).put("token",f.data).put("host",false).put("status","invite").put("at",now()).put("sub",sub).put("tx",0).put("rx",0).put("events",JSONArray()).put("buffer",JSONObject()).put("jobs",JSONObject());all.put(f.id,m)
                }
                if(m==null || phoneKey(m.getString("peer"))!=phoneKey(peer) || !sameSim(m,sub))return@transaction
                if(m.optString("game") in singlePlayerOnlyGames)return@transaction
                expire(m)
                when(f.type){
                    "I"->if(!m.getBoolean("host") && m.optString("status")=="active" && f.data==m.optString("token")){m.getJSONObject("jobs").optJSONObject("answer")?.put("state","queued");send+=f.id}
                    "A"->if(m.optString("status")=="outgoing" && f.data==m.getString("token")){m.put("status","active");root.put("current_match",f.id);alerts+=f.id}
                    "N"->if(m.optString("status")=="outgoing"&&f.data==m.getString("token"))m.put("status","needs_game").put("error","Share this exact game package with your friend, then send a new invitation.")
                    "D"->if(m.optString("status")=="outgoing"&&f.data==m.getString("token")){m.put("status","declined");alerts+=f.id}
                    "Q"->if(m.optString("status") in setOf("outgoing","invite","active"))m.put("status","ended")
                    "K"->m.getJSONObject("jobs").optJSONObject("move:${f.sequence}")?.put("state","ack")
                    "X"->{if(m.getString("status")=="outgoing" && m.getBoolean("host") && f.sequence==1)m.put("status","active")
                        if(m.getString("status")!="active")return@transaction
                        val minute=now()/60000;if(m.optLong("receive_window")!=minute)m.put("receive_window",minute).put("receive_count",0);if(m.optInt("receive_count")>=90)return@transaction;m.put("receive_count",m.optInt("receive_count")+1)
                        val rx=m.getInt("rx");if(f.sequence>rx+8)return@transaction
                        if(f.sequence>rx){val old=m.getJSONObject("buffer").optString(f.sequence.toString());if(old.isNotEmpty()&&old!=f.data)return@transaction;m.getJSONObject("buffer").put(f.sequence.toString(),f.data)}
                        var next=m.getInt("rx")+1;val buffer=m.getJSONObject("buffer");val events=m.getJSONArray("events")
                        while(buffer.has(next.toString())){if(events.length()>=512){m.put("error","Match event limit reached");break};m.put("order",m.optLong("order")+1);events.put(JSONObject().put("order",m.getLong("order")).put("sequence",next).put("data",buffer.getString(next.toString())));val ack="ack:$next";m.getJSONObject("jobs").remove(ack);enqueue(m,FriendGameWire("K",f.id,next,"ack").encode(),ack);send+=f.id;buffer.remove(next.toString());m.put("rx",next);next++}
                        if(m.getInt("rx")>rx)alerts+=f.id
                        if(f.sequence<=m.getInt("rx")){val k="ack:${f.sequence}";m.getJSONObject("jobs").remove(k);enqueue(m,FriendGameWire("K",f.id,f.sequence,"ack").encode(),k);send+=f.id}
                    }
                }
            }
            unavailable?.let{raw(c,peer,sub,it,null,null)}
            if(reply)raw(c,peer,sub,FriendGameWire.APP+id()+"|t1",null,null)
            send.forEach{flush(c,it)}
            if(app && send.isNotEmpty())TransportRecovery.schedule(c,INVITE_TIMEOUT)
            alerts.forEach{id->notify(c,id)}
        }catch(_:Exception){/* Recognized frames never become ordinary chat or execute guest code. */}
        return true
    }
    private fun knowsPeer(root:JSONObject,peer:String):Boolean {
        val key=phoneKey(peer)
        return root.optJSONObject("verified_peers")?.has(key)==true || root.getJSONObject("answers").optLong(key)>0
    }
    private fun sameSim(row:JSONObject,sub:Int)=row.optInt("sub",-1).let{it<0||sub<0||it==sub}
    fun recoverInvites(c:Context):Long? {
        val expired=mutableListOf<String>()
        val rows=list(c)
        transaction(c){root->
            val matches=root.getJSONObject("matches")
            for(row in rows.filter{it.optBoolean("host")&&it.optString("status")=="expired"}) {
                val match=matches.optJSONObject(row.getString("id")) ?: continue
                if(match.optString("status")=="expired"&&!match.optBoolean("timeout_notified")) {
                    match.put("timeout_notified",true)
                    expired+=match.getString("id")
                }
            }
        }
        expired.forEach{notify(c,it)}
        return rows.filter{it.optBoolean("host")&&it.optString("status") in setOf("checking","outgoing")}
            .minOfOrNull{(it.optLong("at")+INVITE_TIMEOUT-now()).coerceAtLeast(1000L)}
    }
    fun result(c:Context,id:String,key:String,ok:Boolean){transaction(c){root->root.getJSONObject("matches").optJSONObject(id)?.let{m->m.getJSONObject("jobs").optJSONObject(key)?.let{job->if(job.optString("state")=="sending"){job.put("state",if(ok)"sent"else"failed");if(!ok)m.put("error","Text update failed. Use Retry to try again.")}}}}}
    private fun notify(c:Context,id:String){
        val row=find(c,id)?:return
        if(row.optString("game") in singlePlayerOnlyGames||MessageNotifications.quiet(c))return
        val manager=c.getSystemService(android.app.NotificationManager::class.java)?:return
        manager.createNotificationChannel(android.app.NotificationChannel("friend_games","Friend games",android.app.NotificationManager.IMPORTANCE_DEFAULT))
        val open=PendingIntent.getActivity(c,0,Intent(c,MainActivity::class.java).setData(Uri.parse("onklav-game://match/$id")).putExtra("open_friend_games",true).putExtra("friend_game_match",id).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val name=MessageRepository(c).contactName(row.getString("peer"))
        val text=when(row.optString("status")) {
            "invite"->"$name wants to play. Tap to play or decline."
            "declined"->"$name declined to play."
            "expired"->if(row.optString("expired_from")=="checking")"Could not verify OnKlav for $name." else "$name wasn't available to play."
            else->"Open Games to play with $name."
        }
        runCatching{manager.notify("friend-game-$id",420,androidx.core.app.NotificationCompat.Builder(c,"friend_games").setSmallIcon(app.onklav.shared.R.drawable.ref_nav_apps).setContentTitle(platformGames.firstOrNull{it.id==row.getString("game")}?.title?:"OnKlav game").setContentText(text).setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true).setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_PRIVATE).build())}
    }
    private fun flush(c:Context,id:String){
        val batch=transaction(c){root->
            val m=root.getJSONObject("matches").optJSONObject(id)?:return@transaction emptyList()
            expire(m)
            val jobs=m.getJSONObject("jobs")
            jobs.keys().asSequence().filter{jobs.getJSONObject(it).optString("state")=="queued" && canSubmit(m,it)}
                .map{key->jobs.getJSONObject(key).put("state","sending");key}.toList()
        }
        for(key in batch)try {
            val row=find(c,id)?:continue
            policy(c,row.getString("peer"),row.optInt("sub",-1))
            transaction(c){root->
                val m=root.getJSONObject("matches").optJSONObject(id)?:return@transaction
                expire(m)
                val job=m.getJSONObject("jobs").optJSONObject(key)?:return@transaction
                if(job.optString("state")=="sending" && canSubmit(m,key))
                    raw(c,m.getString("peer"),m.optInt("sub",-1),job.getString("wire"),id,key)
            }
        }catch(_:Exception){result(c,id,key,false)}
    }
    private fun canSubmit(row:JSONObject,key:String):Boolean = when(key) {
        "question"->row.optString("status")=="checking"
        "invite"->row.optString("status")=="outgoing"
        "answer"->row.optString("status") in setOf("active","declined")
        "cancel","quit"->row.optString("status") in setOf("ended","declined")
        else->row.optString("status")=="active"
    }
    private fun raw(c:Context,peer:String,sub:Int,body:String,id:String?,key:String?){
        val sm=MessagingTransport.manager(c,sub.takeIf{it>=0});check(sm.divideMessage(body).size==1){"Game update exceeded one SMS"}
        val callback=if(id==null)null else PendingIntent.getBroadcast(c,0,Intent(c,FriendGameResultReceiver::class.java).setAction("app.onklav.GAME_SENT").setData(Uri.parse("onklav-game-result://sent/$id/${Uri.encode(key)}")).putExtra("match",id).putExtra("job",key),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        sm.sendTextMessage(peer,null,body,callback,null)
    }
}
class FriendGameResultReceiver:BroadcastReceiver(){override fun onReceive(c:Context,i:Intent){val id=i.getStringExtra("match")?:return;val key=i.getStringExtra("job")?:return;val ok=resultCode==Activity.RESULT_OK;val pending=goAsync();FriendGames.work{try{FriendGames.result(c,id,key,ok)}finally{pending.finish()}}}}
