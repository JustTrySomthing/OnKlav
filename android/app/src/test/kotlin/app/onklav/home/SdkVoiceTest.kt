package app.onklav.home

import android.Manifest
import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.time.Duration
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],qualifiers="w432dp-h936dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class SdkVoiceTest {
    private fun audio():VoiceAudio {val b=ArrayList<Byte>();b.addAll("#!AMR\n".toByteArray().toList());repeat(20){b.add(60);repeat(31){b.add(0)}};return VoiceAudio(b.toByteArray(),"audio/amr",400)}
    private class Focus:VoiceFocus {var allow=true;var held=false;override fun acquire():Boolean{held=allow;return allow};override fun release(){held=false}}
    private inner class Backend:VoiceBackend {
        var beeps=0;override fun beep(){beeps++}
        var captures=0;var cancellations=0;var closed=false;var limit:(()->Unit)?=null;var error:((String)->Unit)?=null;var ready:(()->Boolean)?=null;var done:((String?)->Unit)?=null
        override fun record(allowed:()->Boolean,limit:()->Unit,error:(String)->Unit){check(allowed());captures++;this.limit=limit;this.error=error}
        override fun finish()=audio()
        override fun cancelRecord(){cancellations++}
        override fun play(audio:VoiceAudio,ready:()->Boolean,done:(String?)->Unit){this.ready=ready;this.done=done}
        override fun stopPlay(){}
        override fun close(){closed=true}
    }
    private fun context():Context=ApplicationProvider.getApplicationContext<Application>().also {Shadows.shadowOf(it).grantPermissions(Manifest.permission.RECORD_AUDIO)}
    @Test fun twelveSecondLimitReviewQueueAndDiscard() {
        val c=context();val b=Backend();val f=Focus();var queued=0
        val host={action:String,args:JSONObject->when(action){"host_voice"->JSONObject().put("key","one");"voice_prepare"->audio().json().put("millis",400);"voice_commit"->{queued++;JSONObject()};else->error("Unexpected $action")}}
        val p=SdkVoiceController(c,"capture",true,host,b,f)
        p.record();assertTrue(p.recording);assertEquals(0,queued)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(12000))
        assertFalse(p.recording);assertNotNull(p.draft);assertFalse(f.held);assertEquals(0,queued)
        p.play();b.ready!!();b.done!!(null);assertNotNull(p.draft);assertEquals(0,queued)
        p.queue();assertNull(p.draft);assertEquals(1,queued)
        p.record();p.finish();p.cancel();p.queue();assertEquals(1,queued);p.close();assertTrue(b.closed)
    }
    @Test fun revocationBackgroundAndStaleCallbacksDiscardWithoutQueueing() {
        val c=context();val b=Backend();val f=Focus();var allowed=true;var key="one";var queued=0
        val host={action:String,args:JSONObject->check(allowed){"Revoked"};when(action){"host_voice"->JSONObject().put("key",key);"voice_prepare"->audio().json().put("millis",400);"voice_commit"->{queued++;JSONObject()};else->JSONObject()}}
        val p=SdkVoiceController(c,"capture",true,host,b,f)
        p.record();val stale=b.limit!!;p.setForeground(false);stale();assertFalse(p.recording);assertNull(p.draft)
        p.setForeground(true);p.record();p.finish();allowed=false;p.checkPermission();assertNull(p.draft);assertFalse(f.held)
        allowed=true;p.record();p.finish();key="other recipient";p.queue();assertNull(p.draft);assertEquals(0,queued)
        key="one";p.record();Shadows.shadowOf(c as Application).denyPermissions(Manifest.permission.RECORD_AUDIO);p.checkPermission();assertFalse(p.recording);p.close()
    }
    @Test fun playbackMarksHeardOnlyOnAuthorizedCompletion() {
        val c=context();val b=Backend();val f=Focus();var allowed=true;var heard=0
        val host={action:String,args:JSONObject->check(allowed){"Revoked"};when(action){"host_voice"->JSONObject().put("key","clip").put("audio",audio().json());"voice_played"->{heard++;JSONObject()};else->error("Unexpected")}}
        val p=SdkVoiceController(c,"clip",false,host,b,f)
        p.play();b.ready!!();val stale=b.done!!;p.setForeground(false);stale(null);assertEquals(0,heard)
        p.setForeground(true);p.play();b.done!!("Decoder failed");assertEquals(0,heard)
        p.play();allowed=false;b.done!!(null);assertEquals(0,heard);assertFalse(p.playing)
        allowed=true;p.play();b.ready!!();b.done!!(null);assertEquals(1,heard);assertFalse(f.held);p.close()
    }
    @Test fun focusDenialBackendErrorAndOtherOwnerLeaveNoDraft() {
        val c=context();val b=Backend();val f=Focus().apply {allow=false};val host={a:String,v:JSONObject->JSONObject().put("key","one")}
        val p=SdkVoiceController(c,"a",true,host,b,f);p.record();assertFalse(p.recording);assertEquals(0,b.captures)
        f.allow=true;p.record();b.error!!("Device failed");assertFalse(p.recording);assertNull(p.draft)
        p.record();val other=SdkVoiceController(c,"b",true,host,Backend(),Focus());other.record();assertFalse(p.recording);other.close();p.close()
    }
    @Test fun liveRecordingWaitsForOwnershipAndWarmupAndCancelsBeforeStart() {
        val c=context();val b=Backend();val f=Focus();var owner=false;var released=0
        val host={action:String,args:JSONObject->when(action){
            "host_voice"->{if(!args.optBoolean("permission_only"))check(owner){"Waiting for microphone ownership"};JSONObject().put("key","floor-one").put("live",true).put("beeps",true)}
            "voice_floor_request"->JSONObject().put("lease","request-one")
            "voice_floor_release"->{assertEquals("request-one",args.getString("lease"));released++;JSONObject()}
            "voice_prepare"->audio().json().put("millis",400)
            else->JSONObject()
        }}
        val p=SdkVoiceController(c,"live",true,host,b,f)
        p.record();Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100));assertTrue(p.waiting);assertEquals(0,b.captures)
        owner=true;Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100));assertTrue(p.warming);assertEquals(1,b.beeps);assertEquals(0,b.captures)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(179));assertEquals(0,b.captures)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1));assertTrue(p.recording);assertEquals(1,b.captures)
        p.finish();assertNotNull(p.draft);p.cancel();assertEquals(1,released)
        p.record();Shadows.shadowOf(Looper.getMainLooper()).idle();assertTrue(p.warming);p.setForeground(false)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300));assertEquals(1,b.captures);assertEquals(2,released);assertFalse(f.held);p.close()
    }
    @Test fun unverifiedCarrierCannotStartOrConsumeTrafficOrChangeRole() {
        val c=context();val before=MessagingTransport.role(c)
        assertTrue(runCatching {SdkWalkieTransport.connect(c,JSONObject().put("operation","begin").put("peer","+15550101234").put("subscription",2))}.isFailure)
        assertFalse(SdkWalkieTransport.receiveControl(c,"+15550101234",2,"!OKW1|P|"+"a".repeat(32)+"|0|wt1"))
        assertFalse(java.io.File(c.filesDir,"walkie-carrier-jobs").exists());assertEquals(before,MessagingTransport.role(c))
    }
    @Test fun controllerUsesRealSignedSdkAndRustArchiveWithFixtureDeviceOnly() {
        val c=context();runBlocking {AppSdk.bootstrap(c)}
        fun call(v:JSONObject)=AppSdk.sync(c,v)
        val id="app.onklav.walkie"
        val denied=call(JSONObject().put("action","launch").put("id",id)) as JSONObject
        fun setup(app:JSONObject) {
            val token=app.getString("session");val body=app.child("frame").getJSONArray("body")
            if((0 until body.length()).any{body.getJSONObject(it).optString("id")=="peer"}) {
                call(JSONObject().put("action","event").put("session",token).put("event",JSONObject().put("action","peer").put("value","+15550101234")))
                call(JSONObject().put("action","event").put("session",token).put("event",JSONObject().put("action","prepare")))
            }
            call(JSONObject().put("action","event").put("session",token).put("event",JSONObject().put("action","record.toggle")))
        }
        setup(denied)
        assertTrue(runCatching {call(JSONObject().put("action","host_voice").put("session",denied.getString("session")).put("widget","capture"))}.isFailure)
        call(JSONObject().put("action","permissions").put("id",id).put("grants",JSONArray(listOf("storage.private","audio.record","audio.playback"))))
        val app=call(JSONObject().put("action","launch").put("id",id)) as JSONObject;val token=app.getString("session");setup(app)
        val b=Backend();val p=SdkVoiceController(c,"capture",true,{a,v->call(v.put("action",a).put("session",token).put("widget","capture")) as JSONObject},b,Focus())
        p.record();p.finish();assertNotNull(p.draft);p.queue();assertNull(p.draft)
        val clips=call(JSONObject().put("action","event").put("session",token).put("event",JSONObject().put("action","clips"))) as JSONObject
        assertEquals("queued",clips.getJSONObject("frame").getJSONObject("state").getJSONArray("clips").getJSONObject(0).getString("state"))
        assertFalse(clips.has("bytes"));p.close()
        val incoming=JSONObject().put("id","d".repeat(32)).put("session","a".repeat(32)).put("peer","+15550101234").put("subscription",2).put("outgoing",false)
        val fixture=audio().json().put("operation","ingest").put("binding",incoming).put("sequence",1)
        call(JSONObject().put("action","walkie_archive").put("args",fixture))
        call(JSONObject().put("action","event").put("session",token).put("event",JSONObject().put("action","clips.refresh")))
        val playing=Backend();val widget="clip."+"d".repeat(32)
        val listener=SdkVoiceController(c,widget,false,{a,v->call(v.put("action",a).put("session",token).put("widget",widget)) as JSONObject},playing,Focus())
        listener.play();assertTrue(listener.playing);playing.ready!!();playing.done!!(null);assertEquals("Playback complete",listener.status)
        val updated=call(JSONObject().put("action","event").put("session",token).put("event",JSONObject().put("action","clips.refresh"))) as JSONObject
        val rows=updated.getJSONObject("frame").getJSONObject("state").getJSONArray("clips")
        assertEquals("heard",(0 until rows.length()).map {rows.getJSONObject(it)}.first {it.getString("id")=="d".repeat(32)}.getString("state"))
        listener.close();call(JSONObject().put("action","close").put("session",token))
    }
}
