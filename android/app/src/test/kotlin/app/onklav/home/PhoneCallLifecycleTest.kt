package app.onklav.home

import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.telecom.Call
import android.telecom.InCallService
import android.telecom.TelecomManager
import androidx.test.core.app.ApplicationProvider
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class PhoneCallLifecycleTest {
 private val c get()=ApplicationProvider.getApplicationContext<Application>()
 private lateinit var controller:org.robolectric.android.controller.ServiceController<PhoneInCallService>
 private lateinit var service:PhoneInCallService
 private val nm get()=c.getSystemService(NotificationManager::class.java)
 private val owner=Any()
 @Before fun start(){shadowOf(c.getSystemService(TelecomManager::class.java)).setDefaultDialerPackage(c.packageName);controller=Robolectric.buildService(PhoneInCallService::class.java).create();service=controller.get()}
 @After fun stop(){PhoneCalls.screen(owner,false);controller.destroy();nm.cancelAll()}
 // Construct framework objects without a carrier; leave Call.state deliberately stale
 // when invoking callbacks to verify the callback's supplied state is authoritative.
 private fun call(state:Int):Call {
  val call=ReflectionHelpers.newInstance(Call::class.java)
  val details=ReflectionHelpers.newInstance(Call.Details::class.java)
  ReflectionHelpers.setField(details,"mHandlePresentation",TelecomManager.PRESENTATION_ALLOWED)
  ReflectionHelpers.setField(details,"mCallDirection",Call.Details.DIRECTION_UNKNOWN)
  ReflectionHelpers.setField(call,"mDetails",details)
  ReflectionHelpers.setField(call,"mState",state)
  ReflectionHelpers.setField(call,"mChildren",arrayListOf<Call>())
  ReflectionHelpers.setField(call,"mUnmodifiableChildren",emptyList<Call>())
  ReflectionHelpers.setField(call,"mChildrenIds",arrayListOf<String>())
  ReflectionHelpers.setField(call,"mChildrenCached",true)
  ReflectionHelpers.setField(call,"mConferenceableCalls",arrayListOf<Call>())
  ReflectionHelpers.setField(call,"mUnmodifiableConferenceableCalls",emptyList<Call>())
  ReflectionHelpers.setField(call,"mCallbackRecords",CopyOnWriteArrayList<Any>())
  service.onCallAdded(call);return call
 }
 private fun callback(call:Call)=ReflectionHelpers.getField<MutableMap<Call,Call.Callback>>(service,"callbacks").getValue(call)
 private fun notification()=nm.activeNotifications.firstOrNull{it.id==7310}
 @Test fun destroyedCallWithDetachedDetailsStillClearsItsState(){
  val call=call(Call.STATE_RINGING);val cb=callback(call)
  ReflectionHelpers.setField(call,"mDetails",null)
  cb.onCallDestroyed(call)
  assertTrue(PhoneCalls.state.value.calls.isEmpty());assertNull(notification())
 }
 @Test fun remoteDisconnectClearsNotificationAndLiveIndicatorBeforeRemoval(){
  val call=call(Call.STATE_RINGING);val cb=callback(call);val id=PhoneCalls.state.value.calls.single().id
  assertNotNull(notification());assertNull(shadowOf(c).nextStartedActivity)
  PhoneCalls.waiting.value=mapOf(id to "12");PhoneCalls.keypad.value=true
  cb.onStateChanged(call,Call.STATE_DISCONNECTED)
  assertNull(notification());assertTrue(PhoneCalls.state.value.calls.isEmpty());assertTrue(PhoneCalls.waiting.value.isEmpty());assertFalse(PhoneCalls.keypad.value)
  cb.onDetailsChanged(call,call.details);cb.onStateChanged(call,Call.STATE_RINGING)
  assertNull(notification());assertTrue(PhoneCalls.state.value.calls.isEmpty())
  service.onCallRemoved(call);cb.onCallDestroyed(call);assertNull(notification())
 }
 @Test fun visibleIncomingScreenSuppressesTheDuplicateBannerAndRestoresItOnMinimize(){
  call(Call.STATE_RINGING);assertNotNull(notification());assertNotNull(notification()!!.notification.fullScreenIntent)
  PhoneCalls.screen(owner,true);assertNull(notification())
  PhoneCalls.screen(Any(),false);assertNull(notification())
  PhoneCalls.screen(owner,false);assertNotNull(notification());assertNull(notification()!!.notification.fullScreenIntent)
 }
 @Test fun answeredCallMovesToQuietChannelAndDoesNotCreateAnIconBadge(){
  val call=call(Call.STATE_RINGING);callback(call).onStateChanged(call,Call.STATE_ACTIVE)
  assertEquals("phone-ongoing",notification()!!.notification.channelId)
  assertEquals(NotificationManager.IMPORTANCE_LOW,nm.getNotificationChannel("phone-ongoing").importance)
  assertFalse(nm.getNotificationChannel("phone-ongoing").canShowBadge());assertFalse(nm.getNotificationChannel("phone-incoming-v2").canShowBadge())
  callback(call).onStateChanged(call,Call.STATE_DISCONNECTED);assertNull(notification())
 }
 @Test fun endingOneCallKeepsTheOtherCallAvailable(){
  val first=call(Call.STATE_ACTIVE);val second=call(Call.STATE_RINGING)
  callback(second).onStateChanged(second,Call.STATE_DISCONNECTED)
  assertEquals(1,PhoneCalls.state.value.calls.size);assertEquals(Call.STATE_ACTIVE,PhoneCalls.state.value.calls.single().state);assertNotNull(notification())
  callback(first).onStateChanged(first,Call.STATE_DISCONNECTED);assertTrue(PhoneCalls.state.value.calls.isEmpty());assertNull(notification())
 }
 @Test fun unbindClearsLiveStateAndLateCallbacksCannotRecreateTheNotification(){
  val call=call(Call.STATE_RINGING);val cb=callback(call)
  service.onUnbind(Intent(InCallService.SERVICE_INTERFACE))
  assertTrue(PhoneCalls.state.value.calls.isEmpty());assertNull(notification())
  cb.onStateChanged(call,Call.STATE_RINGING);cb.onDetailsChanged(call,call.details);assertTrue(PhoneCalls.state.value.calls.isEmpty());assertNull(notification())
 }
 @Test fun destroyedCallIsRemovedEvenWithoutOnCallRemoved(){
  val call=call(Call.STATE_RINGING);callback(call).onCallDestroyed(call)
  assertTrue(PhoneCalls.state.value.calls.isEmpty());assertNull(notification())
 }
 @Test fun nonDefaultServiceDoesNotPostASecondDialerNotification(){
  shadowOf(c.getSystemService(TelecomManager::class.java)).setDefaultDialerPackage("other.phone")
  call(Call.STATE_RINGING);assertNull(notification())
 }
 @Test @Config(sdk=[35]) fun android15DisconnectAlsoClearsTheOngoingNotification(){
  shadowOf(c).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
  val call=call(Call.STATE_RINGING);assertNotNull(notification())
  callback(call).onStateChanged(call,Call.STATE_ACTIVE);assertNotNull(notification())
  callback(call).onStateChanged(call,Call.STATE_DISCONNECTED)
  assertNull(notification());assertTrue(PhoneCalls.state.value.calls.isEmpty())
 }
 @Test fun staleOngoingNotificationIsRemovedWithoutClearingMissedCallHistory(){
  service.onDestroy()
  nm.notify(7310,android.app.Notification.Builder(c).setContentTitle("Old call").build())
  nm.notify(7311,android.app.Notification.Builder(c).setContentTitle("Missed call").build())
  PhoneCalls.reconcile(c)
  assertNull(notification());assertTrue(nm.activeNotifications.any{it.id==7311})
 }

 @Test fun incomingCallAddedBeforeRingingDoesNotLaunchASecondScreen(){
  val call=call(Call.STATE_NEW)
  assertNull(shadowOf(c).nextStartedActivity)
  callback(call).onStateChanged(call,Call.STATE_RINGING)
  assertNotNull(notification());assertNull(shadowOf(c).nextStartedActivity)
 }

 @Test fun outgoingCallThatStartsNewOpensItsScreenOnceWhenConnecting(){
  val call=call(Call.STATE_NEW);assertNull(shadowOf(c).nextStartedActivity)
  callback(call).onStateChanged(call,Call.STATE_CONNECTING)
  assertEquals(PhoneCallActivity::class.java.name,shadowOf(c).nextStartedActivity.component!!.className)
  callback(call).onStateChanged(call,Call.STATE_DIALING);callback(call).onDetailsChanged(call,call.details)
  assertNull(shadowOf(c).nextStartedActivity)
 }
 @Test fun incomingDirectionNeverUsesTheOutgoingConnectingShortcut(){
  val call=call(Call.STATE_NEW)
  ReflectionHelpers.setField(call.details,"mCallDirection",Call.Details.DIRECTION_INCOMING)
  callback(call).onStateChanged(call,Call.STATE_CONNECTING)
  callback(call).onStateChanged(call,Call.STATE_RINGING)
  assertNull(shadowOf(c).nextStartedActivity)
 }
 @Test fun aNewWaitingCallCanAlertButRepeatedUpdatesOfTheSameCallDoNot(){
  val first=call(Call.STATE_RINGING)
  assertEquals(0,notification()!!.notification.flags and android.app.Notification.FLAG_ONLY_ALERT_ONCE)
  callback(first).onDetailsChanged(first,first.details)
  assertNotEquals(0,notification()!!.notification.flags and android.app.Notification.FLAG_ONLY_ALERT_ONCE)
  callback(first).onStateChanged(first,Call.STATE_ACTIVE)
  val waiting=call(Call.STATE_RINGING)
  assertEquals(0,notification()!!.notification.flags and android.app.Notification.FLAG_ONLY_ALERT_ONCE)
  callback(waiting).onDetailsChanged(waiting,waiting.details)
  assertNotEquals(0,notification()!!.notification.flags and android.app.Notification.FLAG_ONLY_ALERT_ONCE)
 }
 @org.robolectric.annotation.Implements(Call::class)
 class ActionShadow:org.robolectric.shadows.ShadowCall(){
  companion object{val actions=mutableListOf<String>()}
  @org.robolectric.annotation.Implementation protected fun answer(video:Int){actions+="answer"}
  @org.robolectric.annotation.Implementation protected fun hold(){actions+="hold"}
  @org.robolectric.annotation.Implementation protected fun unhold(){actions+="unhold"}
  @org.robolectric.annotation.Implementation protected fun playDtmfTone(key:Char){actions+="tone:$key"}
 }
 @Test @Config(shadows=[ActionShadow::class]) fun callControlsUseTheSameCallbackStateAsTheVisibleScreen(){
  ActionShadow.actions.clear();val call=call(Call.STATE_NEW);val id=PhoneCalls.state.value.calls.single().id
  ReflectionHelpers.setField(call.details,"mCallCapabilities",Call.Details.CAPABILITY_HOLD)
  callback(call).onStateChanged(call,Call.STATE_RINGING);service.act(id,"answer")
  callback(call).onStateChanged(call,Call.STATE_ACTIVE);service.act(id,"hold");service.tone(id,'5')
  callback(call).onStateChanged(call,Call.STATE_HOLDING);service.act(id,"hold")
  callback(call).onStateChanged(call,Call.STATE_DISCONNECTED);service.tone(id,'6');service.act(id,"answer")
  assertEquals(listOf("answer","hold","tone:5","unhold"),ActionShadow.actions)
 }

}
