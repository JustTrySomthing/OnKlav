package app.onklav.home

import android.app.Application
import android.telecom.Call
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import app.onklav.shared.OnKlavTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29],qualifiers="w360dp-h780dp-mdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhoneUiTest {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 @After fun clear(){PhoneCalls.state.value=PhoneCallState();PhoneCalls.waiting.value=emptyMap();PhoneCalls.error.value="";PhoneCalls.keypad.value=false;PhoneCalls.ended.value=null}
 private fun capture(name:String){compose.runOnUiThread{val v=compose.activity.window.decorView;val b=android.graphics.Bitmap.createBitmap(v.width,v.height,android.graphics.Bitmap.Config.ARGB_8888);v.draw(android.graphics.Canvas(b));File(System.getProperty("onklav.project.root"),"docs/evidence/phone-root-navigation/$name.png").apply{parentFile!!.mkdirs()}.outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
 @Test fun keypadUpdatesDigitsAndTabsRemainAvailable(){val m=PhoneModel(ApplicationProvider.getApplicationContext<Application>());compose.activity.setContent{OnKlavTheme{PhoneWorkspace({},m)}};compose.onNodeWithContentDescription("2",substring=false).performClick();compose.onNodeWithContentDescription("3",substring=false).performClick();compose.onNodeWithContentDescription("Phone number: 23").assertIsDisplayed();compose.onNodeWithContentDescription("Delete digit; hold to clear").performClick();assertEquals("2",m.number);capture("keypad");compose.onNodeWithText("Recents",substring=false).performClick();compose.onNodeWithText("Call history needs access").assertIsDisplayed();compose.onNodeWithText("Contacts",substring=false).performClick();compose.onNodeWithText("Search contacts").assertIsDisplayed()}
 @Test @Config(qualifiers="w320dp-h568dp-mdpi",fontScale=1.4f) fun narrowPhoneKeepsTheKeypadAndCallActionReachable(){compose.activity.setContent{OnKlavTheme{PhoneWorkspace({})}};compose.onNodeWithContentDescription("#",substring=false).performScrollTo().assertIsDisplayed();compose.onNodeWithContentDescription("Call",substring=false).assertIsDisplayed();compose.onNodeWithText("Recents",substring=false).assertIsDisplayed();capture("keypad-large-text")}
 @Test fun incomingScreenHasExplicitAnswerAndDecline(){PhoneCalls.state.value=PhoneCallState(listOf(PhoneLiveCall("example","2025550123","Example caller",Call.STATE_RINGING)));compose.activity.setContent{OnKlavTheme{PhoneInCallScreen{}}};compose.onNodeWithContentDescription("Answer",substring=false).assertIsDisplayed();compose.onNodeWithContentDescription("Decline",substring=false).assertIsDisplayed();compose.onNodeWithText("Incoming call").assertIsDisplayed();capture("incoming")}
 @Test fun activeCallHasStableEndActionAndAnInCallKeypad(){PhoneCalls.state.value=PhoneCallState(listOf(PhoneLiveCall("example","2025550123","Example caller",Call.STATE_ACTIVE,connected=1,hold=true)),audio=listOf(PhoneAudio("phone","Phone",true)),add=true);compose.activity.setContent{OnKlavTheme{PhoneInCallScreen{}}};compose.onNodeWithContentDescription("End call",substring=false).assertIsDisplayed();compose.onNodeWithContentDescription("Keypad",substring=false).performScrollTo().performClick();compose.onNodeWithContentDescription("8",substring=false).performScrollTo().performClick();compose.onAllNodesWithText("8",substring=false).assertCountEquals(2);compose.onNodeWithContentDescription("End call",substring=false).assertIsDisplayed();capture("active-keypad")}
 @Test fun keypadFormatsNumbersAndOffersVoicemailAndSpeedDial(){
  val app=ApplicationProvider.getApplicationContext<Application>();app.getSharedPreferences("phone-speed-dial",0).edit().clear().commit()
  val m=PhoneModel(app);m.number="2025550123"
  compose.activity.setContent{OnKlavTheme{PhoneWorkspace({},m)}}
  compose.onNodeWithText("(202) 555-0123").assertIsDisplayed();compose.onNodeWithContentDescription("Voicemail",substring=false).assertIsDisplayed()
  // Bound Material text-field/dialog animations explicitly in Robolectric.
  androidx.test.espresso.IdlingPolicies.setMasterPolicyTimeout(10,java.util.concurrent.TimeUnit.SECONDS)
  compose.mainClock.autoAdvance=false
  fun pump() { repeat(8) {
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(100)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(16))
        compose.runOnUiThread {
            val env = compose.javaClass.getDeclaredField("environment").apply { isAccessible = true }.get(compose)
            val base = requireNotNull(env.javaClass.superclass)
            val registry = base.getDeclaredField("composeRootRegistry").apply { isAccessible = true }.get(env)
            val roots = registry.javaClass.getMethod("getRegisteredComposeRoots").invoke(registry) as Set<*>
            roots.filterNotNull().forEach { root ->
                val view = root.javaClass.getMethod("getView").invoke(root) as android.view.View
                run { // Android traverses newly created windows even without a pending Compose measure flag.
                    val decor = view.rootView
                    decor.forceLayout()
                    decor.measure(android.view.View.MeasureSpec.makeMeasureSpec(360, android.view.View.MeasureSpec.EXACTLY),
                        android.view.View.MeasureSpec.makeMeasureSpec(780, android.view.View.MeasureSpec.AT_MOST))
                    decor.layout(0, 0, decor.measuredWidth, decor.measuredHeight)
                    root.javaClass.getMethod("measureAndLayoutForTest").invoke(root)
                }
            }
        }
    } }
  compose.onNodeWithContentDescription("Speed dial",substring=false).performClick();pump();compose.onNodeWithContentDescription("Assign speed dial 2").performClick();pump()
  compose.onNodeWithText("Use (202) 555-0123").performClick();pump();assertEquals("2025550123",m.speedDial[2])
  compose.onNodeWithContentDescription("Speed dial 2 options").performClick();pump();compose.onNodeWithText("Remove",substring=false).performClick();pump();assertNull(m.speedDial[2]);compose.onNodeWithText("Done",substring=false).performClick();pump()
  capture("keypad-shortcuts");app.getSharedPreferences("phone-speed-dial",0).edit().clear().commit()
 }

 @Test fun openingAddCallKeypadDoesNotReopenExistingCall(){
  PhoneCalls.state.value=PhoneCallState(listOf(PhoneLiveCall("existing","2025550123","Caller",Call.STATE_ACTIVE)))
  val app=ApplicationProvider.getApplicationContext<Application>()
  compose.activity.setContent{OnKlavTheme{PhoneWorkspace({},PhoneModel(app))}}
  compose.onNodeWithContentDescription("2",substring=false).performClick()
  compose.onNodeWithContentDescription("Phone number: 2").assertIsDisplayed()
  assertNull(org.robolectric.Shadows.shadowOf(app).nextStartedActivity)
 }

}
