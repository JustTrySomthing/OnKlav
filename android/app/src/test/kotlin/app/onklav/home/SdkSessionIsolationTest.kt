package app.onklav.home

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class SdkSessionIsolationTest{
 @Test fun closedYouTubeModelReopensWithFreshScreenAndWorkingHome()=runBlocking{
  val c=ApplicationProvider.getApplicationContext<Application>();val model=SdkAppModel(c);model.open("app.onklav.youtube");assertNull(model.error)
  val field=SdkAppModel::class.java.getDeclaredField("session").apply{isAccessible=true};val first=field.get(model)
  var closed=false;model.close{closed=true}
  val deadline=System.nanoTime()+5_000_000_000
  while(!closed&&System.nanoTime()<deadline){org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();Thread.sleep(10)}
  assertTrue(closed);assertEquals(0,model.frame.length());assertNull(field.get(model));assertEquals(0,model.webComponent("youtube.web").length())
  model.open("app.onklav.youtube");assertNull(model.error);assertNotEquals(first,field.get(model));assertTrue(model.frame.length()>0)
  model.action("home");val end=System.nanoTime()+5_000_000_000
  while(model.busy&&System.nanoTime()<end){org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();Thread.sleep(10)}
  assertNull(model.error)
 }
 @Test fun pendingLaunchesRejectForeignTokensAndNeverRepeatTheCurrentOperation()=runBlocking{
  val c=ApplicationProvider.getApplicationContext<Application>();AppSdk.bootstrap(c)
  c.getSharedPreferences("rss",0).edit().putString("feeds","[\"https://example.org/feed\"]").remove("sdk_feeds_adopted").commit()
  val model=SdkAppModel(c);model.open("app.onklav.rss");assertNull(model.error)
  val pending=requireNotNull(model.pending);val token=pending.getString("token")
  assertFalse(model.launchPending("foreign"));assertFalse(model.launchPending(""));assertTrue(model.launchPending(token));assertFalse(model.launchPending(token))
  model.reply(pending,failure="Cancelled in fixture");assertFalse(model.launchPending(token))
 }
 @Test fun aModelCannotMoveItsDraftOrPendingOperationIntoAnotherApp()=runBlocking{
  val c=ApplicationProvider.getApplicationContext<Application>();val model=SdkAppModel(c);model.open("app.onklav.notes");assertNull(model.error)
  model.edit("note.text","Local unsaved draft");val before=model.frame.toString();model.open("app.onklav.reader")
  assertEquals("Open this app in its own app session",model.error);assertEquals(before,model.frame.toString());assertTrue(model.hasUnsaved("note.text"));assertEquals("Local unsaved draft",model.draftValue("note.text","Old value"))
 }
 @Test fun clearingTheOwnerClosesItsCoreSessionAndReleasesNativeCallbacks()=runBlocking{
  val c=ApplicationProvider.getApplicationContext<Application>();val store=ViewModelStore()
  val model=ViewModelProvider(store,ViewModelProvider.AndroidViewModelFactory(c))[SdkAppModel::class.java];model.open("app.onklav.social");assertNull(model.error)
  val field=SdkAppModel::class.java.getDeclaredField("session").apply{isAccessible=true};val token=field.get(model) as String
  model.webPool.onBack={true};model.webPool.save("one",android.os.Bundle(),1)
  store.clear();assertNull(field.get(model));assertTrue(model.webPool.entries.isEmpty());assertNull(model.webPool.onBack)
  try{AppSdk.call(c,JSONObject().put("action","check").put("session",token));fail("Cleared model retained an executable session")}catch(expected:IllegalStateException){}
 }
 @Test fun recreatedOwnerReloadsSavedDataWithoutOldPendingTokens()=runBlocking{
  val c=ApplicationProvider.getApplicationContext<Application>();val first=SdkAppModel(c)
  first.open("app.onklav.notes");assertNull(first.error)
  val field=SdkAppModel::class.java.getDeclaredField("session").apply{isAccessible=true}
  val old=field.get(first) as String
  AppSdk.call(c,JSONObject().put("action","event").put("session",old).put("event",JSONObject().put("action","note.text").put("value","Saved before restart")))
  AppSdk.call(c,JSONObject().put("action","close").put("session",old))
  val restored=SdkAppModel(c);restored.open("app.onklav.notes",recovering=true)
  assertNull(restored.error);assertTrue(restored.recoveryNotice);assertNull(restored.pending)
  assertNotEquals(old,field.get(restored));assertFalse(restored.hasUnsaved("note.text"))
  assertEquals("Saved before restart",restored.frame.child("state").child("note").getString("text"))
  restored.dismissRecoveryNotice();assertFalse(restored.recoveryNotice)
  restored.open("app.onklav.notes",recovering=true);assertFalse(restored.recoveryNotice)
 }

}
