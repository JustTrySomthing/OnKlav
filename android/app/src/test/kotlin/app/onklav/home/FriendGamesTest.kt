package app.onklav.home
import android.Manifest
import android.app.Application
import android.app.role.RoleManager
import android.content.*
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.BlockedNumberContract
import android.telephony.*
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import org.robolectric.shadows.*
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class FriendGamesTest {
    private val one="+15550100001";private val two="+15550100002"
    class BlockPolicy:ContentProvider(){override fun onCreate()=true;override fun call(m:String,a:String?,e:Bundle?)=Bundle().apply{putBoolean("blocked",false)};override fun query(u:Uri,p:Array<out String>?,s:String?,a:Array<out String>?,o:String?):Cursor?=null;override fun insert(u:Uri,v:ContentValues?):Uri?=null;override fun delete(u:Uri,s:String?,a:Array<out String>?)=0;override fun update(u:Uri,v:ContentValues?,s:String?,a:Array<out String>?)=0;override fun getType(u:Uri):String?=null}
    private fun device():Context {
        val base=ApplicationProvider.getApplicationContext<Application>();Shadows.shadowOf(base).grantPermissions(Manifest.permission.SEND_SMS,Manifest.permission.RECEIVE_SMS)
        Shadows.shadowOf(base.getSystemService(RoleManager::class.java)).addHeldRole(RoleManager.ROLE_SMS)
        ShadowSubscriptionManager.setDefaultSmsSubscriptionId(1)
        ShadowContentResolver.registerProviderInternal(BlockedNumberContract.AUTHORITY,BlockPolicy())
        val dir=File(base.cacheDir,"friend-${java.util.UUID.randomUUID()}").apply{mkdirs()}
        return object:ContextWrapper(base){override fun getFilesDir()=File(dir,"files").apply{mkdirs()};override fun getNoBackupFilesDir()=File(dir,"private").apply{mkdirs()};override fun getApplicationContext():Context=this}
    }
    private fun sent()=Shadows.shadowOf(SmsManager.getSmsManagerForSubscriptionId(1)).lastSentTextMessageParams.text
    private fun pair(game:String="naval"):Triple<Context,Context,String> {
        val a=device();val b=device();val id=FriendGames.invite(a,two,game)
        assertEquals(FriendGameWire.QUESTION,sent());assertTrue(FriendGames.receive(b,one,1,sent()));val app=sent();assertTrue(app.startsWith(FriendGameWire.APP))
        FriendGames.receive(a,two,1,app);val invite=sent();assertEquals("I",FriendGameWire.parse(invite)!!.type)
        FriendGames.receive(b,one,1,invite);assertEquals("invite",FriendGames.find(b,id)!!.getString("status"))
        FriendGames.accept(b,id,true);val answer=sent();FriendGames.receive(a,two,1,answer);assertEquals("active",FriendGames.find(a,id)!!.getString("status"));return Triple(a,b,id)
    }
    @Test fun oneVerificationAdvancesAllPendingGamesForTheSameFriend() {
        val a=device();val b=device()
        val naval=FriendGames.invite(a,two,"naval")
        val blackjack=FriendGames.invite(a,two,"blackjack")
        FriendGames.receive(b,one,1,FriendGameWire.QUESTION)
        val proof=sent()
        FriendGames.receive(a,two,1,proof)
        for(id in listOf(naval,blackjack)) {
            val match=FriendGames.find(a,id)!!
            assertEquals("outgoing",match.getString("status"))
            FriendGames.receive(b,one,1,FriendGameWire("I",id,0,match.getString("token")).encode())
            FriendGames.accept(b,id,true)
            FriendGames.receive(a,two,1,sent())
            assertEquals("active",FriendGames.find(a,id)!!.getString("status"))
        }
    }
    @Test fun dottedContactNumbersCanConnectWithoutChangingPeerIdentity() {
        val a=device();val b=device();val peer="555.010.0002"
        val id=FriendGames.invite(a,peer,"naval")
        FriendGames.receive(b,one,1,FriendGameWire.QUESTION)
        FriendGames.receive(a,two,1,sent())
        assertEquals("outgoing",FriendGames.find(a,id)!!.getString("status"))
        assertEquals(phoneKey(two),phoneKey(peer))
        assertFalse(FriendGames.validNumber("555.evil.0002"))
    }
    @Test fun carrierConvertedVerificationAndTurnsStillReachTheMatch() {
        val a=device()
        val b=device()
        val id=FriendGames.invite(a,two,"naval")
        assertTrue(FriendGames.receive(b,one,1,sent()))
        val answer=sent().replace('|','@')
        assertTrue(FriendGameWire.appResponse(answer))
        assertTrue(FriendGames.receive(a,two,1,answer))
        val invite=sent().replace('|','@')
        assertEquals("I",FriendGameWire.parse(invite)?.type)
        assertTrue(FriendGames.receive(b,one,1,invite))
        FriendGames.accept(b,id,true)
        assertTrue(FriendGames.receive(a,two,1,sent().replace('|','@')))
        assertEquals("active",FriendGames.find(a,id)!!.getString("status"))
        assertEquals("active",FriendGames.find(b,id)!!.getString("status"))
        assertFalse(FriendGameWire.appResponse(answer+" extra"))
        assertNull(FriendGameWire.parse(invite+" extra"))
        assertFalse(FriendGameWire.appResponse(answer.replaceFirst('@','|')))
    }

    @Test fun otherGamesRemainIndependentWhileFriendMatchIsPaused() {
        val (a,b,id)=pair("blackjack")
        GameSessionStatus.open(a).save("hockey")
        assertEquals(id,FriendGames.resume(a,"blackjack"));assertNull(FriendGames.resume(a,"naval"))
        val naval=FriendGames.invite(a,two,"naval")
        assertNotEquals(id,naval);assertEquals("active",FriendGames.find(a,id)!!.getString("status"))
        assertThrows(IllegalStateException::class.java){FriendGames.invite(a,two,"blackjack")}
        FriendGames.markCurrent(a,naval)
        assertEquals(id,FriendGames.resume(a,"blackjack"))
    }
    @Test fun featuredGamesArePreinstalledAndSoloOnly() {
        val a=device()
        assertEquals(GamePackages.LAST_LIGHT_ID,platformGames.first().id)
        val order=a.getSharedPreferences("panda-order-${java.util.UUID.randomUUID()}",Context.MODE_PRIVATE)
        order.edit().putString("order","blackjack,four,hockey,installed.game").commit()
        assertEquals(listOf(GamePackages.LAST_LIGHT_ID,"panda",GamePackages.SHROOM_ID),includedGameOrder(order,platformGames.map{it.id}).take(3))
        assertTrue(order.getString("order","")!!.contains("installed.game"))
        order.edit().putString("order","hockey,panda,blackjack").commit()
        assertEquals("hockey",includedGameOrder(order,platformGames.map{it.id}).first())
        assertEquals("Crash Test Panda",WebGameActivity.BUILTINS["panda"])
        a.assets.open("catalog/panda.webp").use{assertTrue(it.available()>0)}
        assertTrue(setOf("panda",GamePackages.SHROOM_ID,GamePackages.LAST_LIGHT_ID).all { it in singlePlayerOnlyGames })
        assertThrows(IllegalStateException::class.java){FriendGames.token(a,"panda")}
        assertThrows(IllegalStateException::class.java){FriendGames.invite(a,two,"panda")}
    }
    @Test fun retiredPandaMatchIsRetainedButCannotInterceptSoloOrSend() {
        val (a,_,id)=pair("blackjack")
        val file=File(a.noBackupFilesDir,"friend-games/state.json")
        val root=JSONObject(file.readText());val row=root.getJSONObject("matches").getJSONObject(id)
        row.put("game","panda").put("token","pt3");file.writeText(root.toString())
        assertNull(FriendGames.resume(a,"panda"));assertNull(FriendGames.active(a,"panda"))
        assertEquals("panda",FriendGames.find(a,id)!!.getString("game"))
        val retained=FriendGames.find(a,id)!!.toString()
        FriendGames.receive(a,two,1,FriendGameWire("X",id,1,"S0,80,0").encode())
        assertEquals(retained,FriendGames.find(a,id)!!.toString())
        assertThrows(IllegalStateException::class.java){FriendGames.move(a,id,"panda","S0,80,0")}
    }
    @Test fun originalReadableCheckInvitationAcceptanceTurnsAndAcknowledgement() {
        val (a,b,id)=pair();assertEquals(1,FriendGames.move(a,id,"naval","P"));val move=sent()
        assertTrue(FriendGames.state(a,id,"naval").getBoolean("pending"))
        FriendGames.receive(b,one,1,move);val ack=sent();assertEquals("K",FriendGameWire.parse(ack)!!.type)
        FriendGames.receive(a,two,1,ack);assertFalse(FriendGames.state(a,id,"naval").getBoolean("pending"))
        assertEquals(1,FriendGames.state(b,id,"naval").getJSONArray("events").length())
        FriendGames.receive(b,one,1,move);assertEquals(1,FriendGames.state(b,id,"naval").getJSONArray("events").length())
        FriendGames.receive(b,"+15550109999",1,move);assertEquals(1,FriendGames.state(b,id,"naval").getJSONArray("events").length())
        FriendGames.quit(a,id);FriendGames.receive(b,one,1,sent());assertEquals("ended",FriendGames.find(b,id)!!.getString("status"))
    }
    @Test fun outOfOrderTurnsDrainContiguouslyAndWrongGameCannotSend() {
        val (a,b,id)=pair();FriendGames.receive(b,one,1,FriendGameWire("X",id,2,"S42").encode());assertEquals(0,FriendGames.state(b,id,"naval").getJSONArray("events").length())
        FriendGames.receive(b,one,1,FriendGameWire("X",id,1,"P").encode());val events=FriendGames.state(b,id,"naval").getJSONArray("events");assertEquals(2,events.length());assertEquals("P",events.getJSONObject(0).getString("data"))
        assertThrows(IllegalStateException::class.java){FriendGames.move(a,id,"four","F1")}
        ChatStore(a).requestSync(JSONObject().put("action","blocked").put("address",two).put("blocked",true))
        assertThrows(IllegalStateException::class.java){FriendGames.move(a,id,"naval","P")}
    }
    @Test fun replayJournalPreservesBothDirectionsAndAcknowledgesDrainedFrames() {
        val (a,b,id)=pair()
        FriendGames.move(a,id,"naval","P");FriendGames.receive(b,one,1,sent());FriendGames.receive(a,two,1,sent())
        FriendGames.move(b,id,"naval","P");FriendGames.receive(a,two,1,sent());FriendGames.receive(b,one,1,sent())
        FriendGames.move(a,id,"naval","S42")
        val timeline=FriendGames.state(a,id,"naval").getJSONArray("timeline")
        assertEquals(3,timeline.length());assertTrue(timeline.getJSONObject(0).getBoolean("own"));assertFalse(timeline.getJSONObject(1).getBoolean("own"));assertEquals("S42",timeline.getJSONObject(2).getString("data"))
        FriendGames.receive(b,one,1,FriendGameWire("X",id,3,"S43").encode());FriendGames.receive(b,one,1,FriendGameWire("X",id,2,"S42").encode())
        val jobs=FriendGames.find(b,id)!!.getJSONObject("jobs");assertTrue(jobs.has("ack:2"));assertTrue(jobs.has("ack:3"));assertEquals(3,FriendGames.state(b,id,"naval").getJSONArray("events").length())
    }
    @Test fun cancelledInvitationDoesNotBlockSoloOrReviveOnLateAppReply() {
        val a=device();val b=device();val id=FriendGames.invite(a,two,"naval");val question=sent()
        assertNotNull(FriendGames.blocking(a))
        FriendGames.cancelInvitation(a,id)
        assertNull(FriendGames.blocking(a));assertEquals("ended",FriendGames.find(a,id)!!.getString("status"))
        FriendGames.receive(b,one,1,question);FriendGames.receive(a,two,1,sent())
        assertEquals("ended",FriendGames.find(a,id)!!.getString("status"));assertNull(FriendGames.blocking(a))
        assertEquals("cancelled",FriendGames.find(a,id)!!.getJSONObject("jobs").getJSONObject("question").getString("state"))
    }
    @Test fun retryExpiredAttemptCreatesFreshWindowAndCancelCannotEndAcceptedGame() {
        val a=device();val id=FriendGames.invite(a,two,"naval")
        val state=File(a.noBackupFilesDir,"friend-games/state.json")
        val root=JSONObject(state.readText());root.getJSONObject("matches").getJSONObject(id).put("at",0);state.writeText(root.toString())
        assertNull(FriendGames.blocking(a))
        val next=FriendGames.restartInvitation(a,id,two,"naval",false)
        assertNotEquals(id,next);assertEquals("checking",FriendGames.find(a,next)!!.getString("status"));assertNull(FriendGames.find(a,id))
        assertEquals(FriendGameWire.QUESTION,sent())
        val (host,guest,active)=pair();FriendGames.cancelInvitation(host,active)
        assertEquals("active",FriendGames.find(host,active)!!.getString("status"))
        assertEquals(active,FriendGames.restartInvitation(host,active,two,"naval",false))
    }
    @Test fun standaloneReverificationNeverCreatesAnInvitationAndHonorsClose() {
        val a=device();val b=device()
        FriendGames.verify(a,two);assertEquals(FriendGameWire.QUESTION,sent());assertEquals("checking",FriendGames.verification(a,two));assertNull(FriendGames.blocking(a))
        FriendGames.receive(b,one,1,sent());FriendGames.receive(a,two,1,sent())
        assertEquals("verified",FriendGames.verification(a,two));assertTrue(phoneKey(two) in FriendGames.verifiedPeers(a));assertTrue(FriendGames.list(a).isEmpty())
        FriendGames.cancelVerification(a,two);assertEquals("idle",FriendGames.verification(a,two))
        FriendGames.verify(a,two);FriendGames.cancelVerification(a,two);assertTrue(FriendGames.list(a).isEmpty())
    }
    @Test fun incomingCancelDeclinesAndReleasesThePendingSlot() {
        val a=device();val b=device();FriendGames.invite(a,two,"naval");FriendGames.receive(b,one,1,sent());FriendGames.receive(a,two,1,sent());val invite=sent()
        FriendGames.receive(b,one,1,invite);val id=FriendGameWire.parse(invite)!!.id
        FriendGames.cancelInvitation(b,id);assertEquals("D",FriendGameWire.parse(sent())!!.type)
        FriendGames.receive(a,two,1,sent());assertNull(FriendGames.blocking(a));assertNull(FriendGames.blocking(b))
    }
    @Test fun wireRejectsMalformedOversizedAndRealtimePayloads() {
        val id="a".repeat(32)
        assertNotNull(FriendGameWire.parse(FriendGameWire("X",id,1,"S99").encode()))
        for(text in listOf(FriendGameWire("X",id,0,"P").encode(),FriendGameWire("X",id,1,"x".repeat(65)).encode(),FriendGameWire("I",id,0,"ah2").encode(),FriendGameWire("X",id,1,"x|y").encode(),"!OKG1|H|$id|1|1,2,3,4"))assertNull(FriendGameWire.parse(text))
        assertEquals("? Do you have OnKlav App ?",FriendGameWire.QUESTION)
    }
}
