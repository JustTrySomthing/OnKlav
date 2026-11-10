package app.onklav.home

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class SdkCarrierIsolationTest{
 private fun request(id:String="app.onklav.walkie",service:String="communications.walkie",operation:String="begin")=JSONObject().put("service",service).put("owner",JSONObject().put("id",id).put("publisher","a".repeat(64))).put("args",JSONObject().put("operation",operation).put("peer","+15550101234"))
 @Test fun confirmationRejectsExternalOwnersWrongServicesAndRawCarrierOperations(){
  for(r in listOf(request(id="../external"),request(service="clipboard.read"),request(operation="receive"),request().apply{remove("owner")},request().apply{getJSONObject("owner").put("publisher","bad")})){
   assertThrows(Exception::class.java){SdkWalkieTransport.confirmedRequest({r}){JSONObject().put("incorrectly_executed",true)}}
  }
 }
 @Test fun executionUsesFreshlyAuthorizedArgumentsAndDoesNotProceedAfterRevocation(){
  var peer="+15550101234";var delivered=""
  peer="+15550109876"
  val result=SdkWalkieTransport.confirmedRequest({request().apply{getJSONObject("args").put("peer",peer)}}){args->delivered=args.getString("peer");JSONObject().put("started",true)}
  assertTrue(result.getBoolean("started"));assertEquals(peer,delivered)
  assertThrows(IllegalStateException::class.java){SdkWalkieTransport.confirmedRequest({error("Session revoked")}){error("Revoked request executed")}}
 }
 @Test fun grantOrPackageChangesCannotInterleaveWithConfirmedExecution(){
  val pool=Executors.newFixedThreadPool(2);val authorized=CountDownLatch(1);val release=CountDownLatch(1);val attempted=CountDownLatch(1);val changed=CountDownLatch(1)
  try{
   val send=pool.submit<JSONObject>{SdkWalkieTransport.confirmedRequest({authorized.countDown();check(release.await(3,TimeUnit.SECONDS));request()}){check(changed.count==1L);JSONObject().put("started",true)}}
   assertTrue(authorized.await(3,TimeUnit.SECONDS))
   val revoke=pool.submit{attempted.countDown();AppSdk.carrierEgress{changed.countDown()}}
   assertTrue(attempted.await(3,TimeUnit.SECONDS));assertFalse(changed.await(100,TimeUnit.MILLISECONDS));release.countDown();assertTrue(send.get(3,TimeUnit.SECONDS).getBoolean("started"));revoke.get(3,TimeUnit.SECONDS);assertEquals(0L,changed.count)
  }finally{release.countDown();pool.shutdownNow()}
 }
 @Test fun genericModelsCannotReachBuiltInCarrierStateBeforeAuthorization(){
  val model=SdkAppModel(ApplicationProvider.getApplicationContext<Application>())
  for(action in listOf("voice_floor_request","voice_floor_release","voice_commit")){val failure=assertThrows(IllegalStateException::class.java){model.voiceRequest(action,"capture",JSONObject().put("send",true))};assertEquals("Carrier voice routing is not available to this app",failure.message)}
 }
 @Test fun carrierUiEligibilityRequiresAnExplicitLiveGrant()=kotlinx.coroutines.runBlocking{
  val c=ApplicationProvider.getApplicationContext<Application>();val generic=SdkAppModel(c);assertFalse(generic.carrierVoiceEligible)
  generic.open("app.onklav.social");assertNull(generic.error);assertFalse(generic.carrierVoiceEligible)
  AppSdk.sync(c,JSONObject().put("action","permissions").put("id","app.onklav.walkie").put("grants",org.json.JSONArray(listOf("storage.private","communications.walkie"))))
  val walkie=SdkAppModel(c);walkie.open("app.onklav.walkie");assertNull(walkie.error);assertTrue(walkie.carrierVoiceEligible)
 }

}
