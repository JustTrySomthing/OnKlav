package app.onklav.home

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import org.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import java.util.UUID

/** Signed controller + validator + player transaction, not just a standalone engine fixture. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class RadioActionRecoveryTest {
 @get:Rule val emptyKeyStore=EmptyKeyStoreRule()
 private val c get()=ApplicationProvider.getApplicationContext<Application>()
 private var controller:org.robolectric.android.controller.ServiceController<SdkPlaybackService>?=null
 private val engines=mutableListOf<Fake>()
 private class Fake(val events:RadioStreamEngine.Events):RadioStreamEngine{var released=false;override val audioSession=42;override val positionMs get()=android.os.SystemClock.elapsedRealtime();override fun prepare(){events.ready()};override fun play(){events.playing(true)};override fun pause(){events.playing(false)};override fun release(){released=true}}
 private fun idle(){shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20))}
 private fun await(check:()->Boolean){val end=System.nanoTime()+10_000_000_000;while(!check()&&System.nanoTime()<end){idle();Thread.sleep(5)};assertTrue("Bounded app action must finish",check())}
 private fun action(m:SdkAppModel,id:String){m.action(id);idle();await{!m.busy};idle()}
 private fun page(ids:List<String>)=JSONObject().put("items",JSONArray(ids.mapIndexed{i,id->JSONObject().put("id",id).put("name","Station $i").put("codec","MP3").put("countrycode","US")})).put("next",JSONObject.NULL)
 private fun model(ids:List<String>):SdkAppModel{runBlocking{AppSdk.bootstrap(c)};val m=SdkAppModel(c);runBlocking{m.open("app.onklav.radio");assertNull(m.error);if(m.pending==null)m.restoreRadioView();m.reply(requireNotNull(m.pending){"No search after restoration: ${m.error}"},page(ids))};return m}
 private fun fixture(m:SdkAppModel,id:String):SdkRadio.Prepared{val p=m.pending!!;val o=m.authorizeNow(p).getJSONObject("owner");val handle=UUID.randomUUID().toString();val url="https://stream.example/$id";return SdkRadio.Prepared(JSONObject().put("service","media.play").put("owner",o).put("args",JSONObject().put("queue",JSONArray().put(JSONObject().put("radio_station",handle).put("radio_id",id).put("url",url).put("title","Station")))),JSONObject().put("id",id).put("name","Station"),handle,url)}
 private fun start(m:SdkAppModel,id:String,actionId:String="radio.play.$id"):Pair<JSONObject,JSONObject>{
  action(m,actionId);val p=m.pending!!;val prepared=fixture(m,id)
  SdkMedia.radioFactory={_,_,v,e->v();Fake(e).also{engines+=it}}
  val result=SdkRadio.start(c,prepared){m.authorizeNow(p);Unit}
  if(controller==null)controller=Robolectric.buildService(SdkPlaybackService::class.java).create()
  shadowOf(c).nextStartedService?.let{controller!!.get().onStartCommand(it,0,1)};idle()
  return p to result
 }
 @After fun cleanup(){SdkMedia.stop();controller?.destroy();SdkMedia.radioFactory={c,u,v,e->ExoRadioStream(c,u,v,e)}}
 @Test fun repeatedStationStartsPassStrictSdkValidationAndEverySubsequentClickWorks(){
  val ids=List(3){UUID.randomUUID().toString()};val m=model(ids)
  for(id in ids){val(p,result)=start(m,id);assertFalse(result.getJSONObject("player").has("connection"));runBlocking{m.reply(p,result)};assertNull(m.pending);assertNull(m.error)
   action(m,"radio.listen");assertNull(m.error);action(m,"radio.explore");assertNull(m.error)
  }
  assertTrue(engines.dropLast(1).all{it.released});assertFalse(engines.last().released)
  val previous=engines.last();SdkMedia.controlFor(m.mediaOwner("radio.player"),"pause");idle();assertTrue(previous.released)
  val(resume,restarted)=start(m,ids.last(),"radio.retry");runBlocking{m.reply(resume,restarted)};assertNull(m.pending);assertNull(m.error)
 }
 @Test fun rejectedSuccessReplyIsAcknowledgedAsFailureInsteadOfOrphaningTheSdkToken(){
  val id=UUID.randomUUID().toString();val m=model(listOf(id));val(p,result)=start(m,id)
  result.getJSONObject("player").put("connection","diagnostic-not-in-sdk-schema")
  runBlocking{m.reply(p,result)};idle();assertNull(m.pending);assertFalse(m.sessionUnavailable);assertTrue(engines.last().released)
  m.error=null;action(m,"radio.failure.close");assertNull(m.error);action(m,"radio.explore");assertNull(m.error)
  val(next,valid)=start(m,id);runBlocking{m.reply(next,valid)};assertNull(m.pending);assertNull(m.error)
 }
 @Test fun radioPreparationReleasesOldStreamBeforeProbeAndRepairsOnlyTheSelectedAddress(){
  val ids=List(2){UUID.randomUUID().toString()};val m=model(ids);val(first,result)=start(m,ids[0]);runBlocking{m.reply(first,result)}
  runBlocking{SdkRadioCatalog.remember(c,listOf(SdkRadioCatalog.Station(ids[1],"New station","https://stream.example/expired")),{})}
  action(m,"radio.play.${ids[1]}");val p=m.pending!!;val old=engines.last();var refreshes=0;val probes=mutableListOf<String>()
  val work=CoroutineScope(Dispatchers.Main.immediate).async{SdkRadio.prepare(c,p.getString("token"),resolveStream={url,verify->verify();assertTrue("Old audio must already be released",old.released);probes+=url;if(url.endsWith("expired"))throw SdkRadioHealth.http(404);url},refreshStation={id,verify->verify();refreshes++;JSONObject().put("stationuuid",id).put("name","New station").put("codec","MP3").put("lastcheckok",1).put("url_resolved","https://stream.example/current")}){m.authorizeNow(p)}}
  await{work.isCompleted};val prepared=runBlocking{work.await()};assertEquals(1,refreshes);assertEquals(listOf("https://stream.example/expired","https://stream.example/current"),probes)
  val next=SdkRadio.start(c,prepared){m.authorizeNow(p);Unit};runBlocking{m.reply(p,next)};assertNull(m.pending);assertNull(m.error)
 }
 @Test fun switchingAndReopeningWaitForTheOldConnectionBeforeTheFirstProbe(){
  val ids=List(3){UUID.randomUUID().toString()};var m=model(ids);val(first,result)=start(m,ids[0]);runBlocking{m.reply(first,result)}
  runBlocking{SdkRadioCatalog.remember(c,ids.map{SdkRadioCatalog.Station(it,"Station","https://stream.example/$it")},{})}
  for((index,id) in ids.drop(1).withIndex()){
   if(index==1){var closed=false;m.close{closed=true};await{closed};m=model(ids)}
   action(m,"radio.play.$id");val p=m.pending!!;val old=engines.last();var probes=0
   val work=CoroutineScope(Dispatchers.Main.immediate).async{
    SdkRadio.prepare(c,p.getString("token"),resolveStream={url,verify->verify();assertTrue("Replacement cannot probe until the old engine releases",old.released);probes++;url}){m.authorizeNow(p)}
   }
   await{work.isCompleted};val prepared=runBlocking{work.await()};assertEquals(1,probes)
   val next=SdkRadio.start(c,prepared){m.authorizeNow(p);Unit};runBlocking{m.reply(p,next)};idle()
   assertNull(m.error);assertNull(m.pending);assertEquals("playing",SdkMedia.status(m.mediaOwner("radio.player")).getString("state"))
  }
 }
 @Test fun cancellingACompletionStillCommitsItsFailureAndBusyActionsCannotCrossPendingBarrier(){
  val id=UUID.randomUUID().toString();val m=model(listOf(id));action(m,"radio.play.$id");val p=m.pending!!
  action(m,"radio.listen");assertEquals(p.getString("token"),m.pending!!.getString("token"));assertNull(m.error)
  val scope=CoroutineScope(Dispatchers.Main.immediate);val job=scope.launch{cancel();m.reply(p,failure="Cancelled")};await{job.isCompleted}
  assertNull(m.pending);m.error=null;action(m,"radio.explore");assertNull(m.error)
 }
}
