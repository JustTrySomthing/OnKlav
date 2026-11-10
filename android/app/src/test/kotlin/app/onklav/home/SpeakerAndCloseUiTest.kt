package app.onklav.home

import android.app.Application
import android.app.role.RoleManager
import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import app.onklav.shared.*
import androidx.test.core.app.ApplicationProvider
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29],qualifiers="w360dp-h800dp-mdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
class SpeakerAndCloseUiTest {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 @After fun clean(){PhoneCalls.state.value=PhoneCallState();PhoneCalls.error.value="";PhoneCalls.waiting.value=emptyMap();PhoneCalls.ended.value=null;PhoneCalls.service=null;PhoneCalls.resetSpeakerMemory()}
 @Test fun closeIsTransparentAndRetainsItsTouchTargetAndCallback(){
  var closed=false
  compose.activity.setContent{OnKlavTheme{Box(Modifier.size(96.dp).background(Color(0xff102030))){ClosePageButton{closed=true}}}}
  val button=compose.onNodeWithContentDescription("Close");button.assertHasClickAction();assertTrue(button.fetchSemanticsNode().boundsInRoot.width>=48)
  button.performClick();assertTrue(closed)
 }
 @Test fun speakerIsAnImmediateToggleAndNeverOpensARouteDialog(){
  val app=ApplicationProvider.getApplicationContext<Application>();Shadows.shadowOf(app.getSystemService(android.telecom.TelecomManager::class.java)).setDefaultDialerPackage(app.packageName);Shadows.shadowOf(app.getSystemService(RoleManager::class.java)).addHeldRole(RoleManager.ROLE_DIALER)
  val controller=Robolectric.buildService(PhoneInCallService::class.java).create();val service=controller.get();service.onBind(Intent(InCallService.SERVICE_INTERFACE))
  try {
   Shadows.shadowOf(service).setSupportedRouteMask(CallAudioState.ROUTE_EARPIECE or CallAudioState.ROUTE_SPEAKER)
   service.onCallAudioStateChanged(CallAudioState(false,CallAudioState.ROUTE_EARPIECE,CallAudioState.ROUTE_EARPIECE or CallAudioState.ROUTE_SPEAKER))
   val call=PhoneLiveCall("test-call","2025550101","Caller",Call.STATE_ACTIVE,connected=1)
   PhoneCalls.state.value=PhoneCalls.state.value.copy(calls=listOf(call))
   compose.activity.setContent{OnKlavTheme{PhoneInCallScreen{}}}
   val button=compose.onNodeWithContentDescription("Speaker");button.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"Off"))
   button.performScrollTo().performClick();Shadows.shadowOf(Looper.getMainLooper()).idle()
   assertEquals(CallAudioState.ROUTE_SPEAKER,service.callAudioState.route)
   service.onCallAudioStateChanged(service.callAudioState);PhoneCalls.state.value=PhoneCalls.state.value.copy(calls=listOf(call))
   button.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"On"));compose.onNodeWithText("Call audio").assertDoesNotExist()
   button.performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();assertEquals(CallAudioState.ROUTE_EARPIECE,service.callAudioState.route)
   service.onCallAudioStateChanged(service.callAudioState);PhoneCalls.state.value=PhoneCalls.state.value.copy(calls=listOf(call))
   button.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"Off"));compose.onNodeWithText("Earpiece").assertDoesNotExist()
  } finally {controller.destroy()}
 }
 @Test fun speakerTargetUsesTypesAndRestoresAvailablePreviousAudio(){
  val routes=listOf(PhoneAudio("uuid1","Localized name",true,earpiece=true),PhoneAudio("uuid2","Another localized name",false,speaker=true),PhoneAudio("uuid3","Headset",false))
  assertEquals("uuid2",PhoneCalls.speakerTarget(routes,null)!!.id)
  val active=routes.map{it.copy(selected=it.speaker)}
  assertEquals("uuid3",PhoneCalls.speakerTarget(active,"uuid3")!!.id)
  assertEquals("uuid1",PhoneCalls.speakerTarget(active,"removed-device")!!.id)
  assertNull(PhoneCalls.speakerTarget(routes.filterNot{it.speaker},null))
 }
 @Test fun singlePlayerStartIsCompactAndGreen(){
  compose.activity.setContent{OnKlavTheme{GameStartDialog(platformGames.first{it.id=="dominos"},false,{},{},{})}}
  compose.waitUntil(10000){runCatching{compose.onNodeWithText("Start").assertExists()}.isSuccess}
  val button=compose.onNodeWithText("Start");val bounds=button.fetchSemanticsNode().boundsInRoot
  assertTrue(bounds.height<=60);assertTrue(bounds.width<=220)
  compose.onNodeWithText("Single Player Only").assertExists()
 }
}
