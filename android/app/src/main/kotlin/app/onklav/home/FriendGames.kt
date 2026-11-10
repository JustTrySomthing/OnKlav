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
    fun verify(c:Context,peer:String) {
        policy(c,peer)
        transaction(c){root->root.put("verification",JSONObject().put("peer",phoneKey(peer)).put("status","checking").put("at",now()))}
        raw(c,peer,MessagingTransport.manager(c).subscriptionId,FriendGameWire.QUESTION,null,null)
    }
    fun verification(c:Context,peer:String):String = transaction(c){root->
        val check=root.optJSONObject("verification")
        if(check==null||check.optString("peer")!=phoneKey(peer))return@transaction "idle"
        if(check.optString("status")=="checking"&&now()-check.optLong("at")>65000)check.put("status","expired")
        check.getString("status")
    }
    fun cancelVerification(c:Context,peer:String) {transaction(c){root->if(root.optJSONObject("verification")?.optString("peer")==phoneKey(peer))root.remove("verification")}}
    fun validNumber(peer:String)=peer.matches(Regex("[+0-9 ()-]{3,40}")) && phoneKey(peer).length in 3..20
    fun blocking(c:Context)=list(c).firstOrNull{it.optString("status") in setOf("active","checking","outgoing","invite")}
    /** Cancel only an invitation. Acceptance racing dismissal must preserve the live game. */
    fun cancelInvitation(c:Context,id:String) {
        transaction(c){root->
            val m=root.getJSONObject("matches").optJSONObject(id)?:return@transaction
            if(m.optString("status")=="active")return@transaction
            if(m.optString("status") in setOf("ended","declined"))return@transaction
            val incoming=m.optString("status")=="invite"
            m.put("status",if(incoming)"declined"else"ended")
            val jobs=m.getJSONObject("jobs")
            jobs.keys().forEach{jobs.getJSONObject(it).put("state","cancelled")}
            enqueue(m,FriendGameWire(if(incoming)"D"else"Q",id,if(incoming)0 else 9999,if(incoming)m.getString("token")else"quit").encode(),"cancel")
        };flush(c,id)
    }
    fun restartInvitation(c:Context,id:String?,peer:String,game:String,dogs:Boolean):String {
        val old = id?.let { find(c,it) }
        val selectedDogs = if (game == "four" && old != null) old.optString("token") == "fd1" else dogs
        if(id!=null){
            if(old?.optString("status")=="active")return id
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
    private fun expire(m:JSONObject){if(m.optString("status")in setOf("checking","outgoing","invite") && now()-m.optLong("at")>65000)m.put("status","expired")}
    fun invite(c:Context,peer:String,game:String,dogs:Boolean=false):String {
        policy(c,peer);val code=if(game=="four"&&dogs)"fd1"else token(c,game)
        check(active(c,game)==null){"This game already has an invitation or match."}
        val match=id();val sub=MessagingTransport.manager(c).subscriptionId
        transaction(c){root->val all=root.getJSONObject("matches");all.keys().forEach{expire(all.getJSONObject(it))};check(all.keys().asSequence().none{all.getJSONObject(it).optString("game")==game && all.getJSONObject(it).optString("status")in setOf("checking","outgoing","invite","active")}){"A game is already in progress."};check(all.length()<32){"Remove a completed match first."}
            val m=JSONObject().put("id",match).put("peer",peer).put("game",game).put("token",code).put("host",true).put("status","checking").put("at",now()).put("sub",sub).put("tx",0).put("rx",0).put("events",JSONArray()).put("buffer",JSONObject()).put("jobs",JSONObject())
            enqueue(m,FriendGameWire.QUESTION,"question");all.put(match,m)
        };flush(c,match);return match
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
    fun quit(c:Context,id:String){val row=find(c,id)?:return;transaction(c){root->val m=root.getJSONObject("matches").getJSONObject(id);m.put("status","ended");if(root.optString("current_match")==id)root.put("current_match","");enqueue(m,FriendGameWire("Q",id,9999,"quit").encode(),"quit")};flush(c,id)}
    fun retry(c:Context,id:String){val row=find(c,id)?:return;check(row.optString("game") !in singlePlayerOnlyGames){"This game is single player only"};policy(c,row.getString("peer"),row.optInt("sub",-1));transaction(c){root->val m=root.getJSONObject("matches").getJSONObject(id);m.getJSONObject("jobs").keys().forEach{k->val j=m.getJSONObject("jobs").getJSONObject(k);if(j.optString("state")in setOf("failed","sent","sending"))j.put("state","queued")}};flush(c,id)}
    fun remove(c:Context,id:String){val game=find(c,id)?.optString("game");transaction(c){root->val m=root.getJSONObject("matches").optJSONObject(id)?:return@transaction;check(m.optString("status")in setOf("expired","ended","declined","needs_game"));root.getJSONObject("matches").remove(id);if(root.optString("current_match")==id)root.put("current_match","")};if(game!=null)GameSaveStore.removeFriend(c,game,id)}
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
        if(body!=FriendGameWire.QUESTION && !app && frame==null)return false
        try {
            policy(c,peer,sub)
            val send=mutableSetOf<String>();var reply=false;val alerts=mutableSetOf<String>();var unavailable:String?=null
            transaction(c){root->val all=root.getJSONObject("matches")
                if(body==FriendGameWire.QUESTION){val a=root.getJSONObject("answers");val key=phoneKey(peer);val day=now()/86400000;val count=if(a.optLong("day")==day)a.optInt("count")else 0
                    if(now()-a.optLong(key)>2000 && count<120){a.put(key,now()).put("day",day).put("count",count+1);reply=true};return@transaction}
                if(app){
                    val verification=root.optJSONObject("verification")
                    if(verification?.optString("peer")==phoneKey(peer)&&verification.optString("status")=="checking"&&now()-verification.optLong("at") in 0L..65000L){verification.put("status","verified");rememberVerified(root,peer)}
                    val m=all.keys().asSequence().map{all.getJSONObject(it)}.firstOrNull{phoneKey(it.getString("peer"))==phoneKey(peer)&&it.optString("status")=="checking"&&now()-it.optLong("at")in 0L..65000L}?:return@transaction
                    if(m.optString("game") in singlePlayerOnlyGames)return@transaction
                    rememberVerified(root,peer)
                    m.put("status","outgoing");enqueue(m,FriendGameWire("I",m.getString("id"),0,m.getString("token")).encode(),"invite");send+=m.getString("id");return@transaction}
                val f=frame?:return@transaction
                var m=all.optJSONObject(f.id)
                if(m==null && f.type=="I"){
                    if(now()-root.getJSONObject("answers").optLong(phoneKey(peer)) !in 0L..65000L)return@transaction
                    val game=game(c,f.data) ?: run {val answers=root.getJSONObject("answers");val key="missing:"+phoneKey(peer);if(now()-answers.optLong(key)>60000){answers.put(key,now());unavailable=FriendGameWire("N",f.id,0,f.data).encode()};return@transaction}
                    if(all.keys().asSequence().any{all.getJSONObject(it).optString("game")==game && all.getJSONObject(it).optString("status")in setOf("active","checking","outgoing","invite")})return@transaction
                    if(all.length()>=32)return@transaction
                    alerts+=f.id
                    m=JSONObject().put("id",f.id).put("game",game).put("peer",peer).put("token",f.data).put("host",false).put("status","invite").put("at",now()).put("sub",sub).put("tx",0).put("rx",0).put("events",JSONArray()).put("buffer",JSONObject()).put("jobs",JSONObject());all.put(f.id,m)
                }
                if(m==null || phoneKey(m.getString("peer"))!=phoneKey(peer))return@transaction
                if(m.optString("game") in singlePlayerOnlyGames)return@transaction
                expire(m)
                when(f.type){
                    "I"->if(!m.getBoolean("host") && m.optString("status")=="active"){m.getJSONObject("jobs").optJSONObject("answer")?.put("state","queued");send+=f.id}
                    "A"->if(m.optString("status")=="outgoing" && f.data==m.getString("token")){m.put("status","active");root.put("current_match",f.id);alerts+=f.id}
                    "N"->if(m.optString("status")=="outgoing"&&f.data==m.getString("token"))m.put("status","needs_game").put("error","Share this exact game package with your friend, then send a new invitation.")
                    "D","Q"->m.put("status",if(f.type=="D")"declined"else"ended")
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
            alerts.forEach{id->notify(c,id)}
        }catch(_:Exception){/* Recognized frames never become ordinary chat or execute guest code. */}
        return true
    }
    fun result(c:Context,id:String,key:String,ok:Boolean){transaction(c){root->root.getJSONObject("matches").optJSONObject(id)?.let{m->m.getJSONObject("jobs").optJSONObject(key)?.let{job->if(job.optString("state")!="ack"){job.put("state",if(ok)"sent"else"failed");if(!ok)m.put("error","Text update failed. Use Retry; the same move will be resent safely.")}}}}}
    private fun notify(c:Context,id:String){
        val row=find(c,id)?:return
        if(row.optString("game") in singlePlayerOnlyGames)return
        val manager=c.getSystemService(android.app.NotificationManager::class.java)?:return
        manager.createNotificationChannel(android.app.NotificationChannel("friend_games","Friend games",android.app.NotificationManager.IMPORTANCE_DEFAULT))
        val open=PendingIntent.getActivity(c,0,Intent(c,MainActivity::class.java).setData(Uri.parse("onklav-game://match/$id")).putExtra("open_friend_games",true).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val text=if(row.optString("status")=="invite")"Game invitation · open Games to accept or decline"else"New turn update · open Games to play"
        runCatching{manager.notify("friend-game-$id",420,androidx.core.app.NotificationCompat.Builder(c,"friend_games").setSmallIcon(app.onklav.shared.R.drawable.ref_nav_apps).setContentTitle(platformGames.firstOrNull{it.id==row.getString("game")}?.title?:"OnKlav game").setContentText(text).setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true).setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_PRIVATE).build())}
    }
    private fun flush(c:Context,id:String){
        val batch=transaction(c){root->val m=root.getJSONObject("matches").getJSONObject(id);m.getJSONObject("jobs").keys().asSequence().toList().filter{m.getJSONObject("jobs").getJSONObject(it).optString("state")=="queued"}.map{k->m.getJSONObject("jobs").getJSONObject(k).put("state","sending");Triple(k,m.getJSONObject("jobs").getJSONObject(k).getString("wire"),JSONObject(m.toString()))}}
        for((key,body,m)in batch)try{policy(c,m.getString("peer"),m.optInt("sub",-1));raw(c,m.getString("peer"),m.optInt("sub",-1),body,id,key)}catch(_:Exception){result(c,id,key,false)}
    }
    private fun raw(c:Context,peer:String,sub:Int,body:String,id:String?,key:String?){
        val sm=MessagingTransport.manager(c,sub.takeIf{it>=0});check(sm.divideMessage(body).size==1){"Game update exceeded one SMS"}
        val callback=if(id==null)null else PendingIntent.getBroadcast(c,0,Intent(c,FriendGameResultReceiver::class.java).setAction("app.onklav.GAME_SENT").setData(Uri.parse("onklav-game-result://sent/$id/${Uri.encode(key)}")).putExtra("match",id).putExtra("job",key),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        sm.sendTextMessage(peer,null,body,callback,null)
    }
}
class FriendGameResultReceiver:BroadcastReceiver(){override fun onReceive(c:Context,i:Intent){val id=i.getStringExtra("match")?:return;val key=i.getStringExtra("job")?:return;val ok=resultCode==Activity.RESULT_OK;val pending=goAsync();FriendGames.work{try{FriendGames.result(c,id,key,ok)}finally{pending.finish()}}}}
