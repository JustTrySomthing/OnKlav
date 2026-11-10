package app.onklav.home
import android.app.Application
import android.provider.CallLog
import android.telecom.TelecomManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
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
class PhoneDetailsUiTest {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 @Before fun prepare(){compose.mainClock.autoAdvance=false;androidx.test.espresso.IdlingPolicies.setMasterPolicyTimeout(10,java.util.concurrent.TimeUnit.SECONDS)}
 private fun pump(){repeat(4){androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeBy(100);org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(16));compose.runOnUiThread{val env=compose.javaClass.getDeclaredField("environment").apply{isAccessible=true}.get(compose);val registry=env.javaClass.superclass!!.getDeclaredField("composeRootRegistry").apply{isAccessible=true}.get(env);val roots=registry.javaClass.getMethod("getRegisteredComposeRoots").invoke(registry) as Set<*>;roots.filterNotNull().forEach{root->val view=root.javaClass.getMethod("getView").invoke(root) as android.view.View;val d=view.rootView;d.forceLayout();d.measure(android.view.View.MeasureSpec.makeMeasureSpec(compose.activity.resources.displayMetrics.widthPixels,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(compose.activity.resources.displayMetrics.heightPixels,android.view.View.MeasureSpec.AT_MOST));d.layout(0,0,d.measuredWidth,d.measuredHeight);root.javaClass.getMethod("measureAndLayoutForTest").invoke(root)}}}}

 private fun tap(text:String){compose.onNodeWithText(text,substring=false).performSemanticsAction(SemanticsActions.OnClick){it()};pump()}
 private fun capture(name:String){compose.runOnUiThread{val env=compose.javaClass.getDeclaredField("environment").apply{isAccessible=true}.get(compose);val registry=env.javaClass.superclass!!.getDeclaredField("composeRootRegistry").apply{isAccessible=true}.get(env);val roots=registry.javaClass.getMethod("getRegisteredComposeRoots").invoke(registry) as Set<*>;val view=roots.filterNotNull().map{it.javaClass.getMethod("getView").invoke(it) as android.view.View}.map{it.rootView}.last{it.width>0&&it.height>0};val b=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888);view.draw(android.graphics.Canvas(b));File(System.getProperty("onklav.project.root"),"docs/evidence/phone-detail-polish/$name.png").apply{parentFile!!.mkdirs()}.outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
 @Test(timeout=45000) fun padHasTallerKeysAndSpacingAndDisabledCallStaysInactive(){
  var n="";var long="";var selected=false
  compose.activity.setContent{OnKlavTheme{Surface{Column{PhonePad({n+=it},{long=it},compact=true);PhoneQuickCall("Quick call",false){selected=true}}}}};pump()
  val a=compose.onNodeWithContentDescription("2",substring=false).getUnclippedBoundsInRoot();val b=compose.onNodeWithContentDescription("5",substring=false).getUnclippedBoundsInRoot()
  assertTrue(a.bottom.value-a.top.value>=54f);assertTrue(b.top.value-a.bottom.value>=8f)
  compose.onNodeWithContentDescription("2",substring=false).performClick();pump();assertEquals("2",n)
  compose.onNodeWithContentDescription("0; hold for plus").performTouchInput{longClick()};pump();assertEquals("0",long)
  compose.onNodeWithContentDescription("Quick call").assertIsNotEnabled();assertFalse(selected);capture("pad")
 }
 @Test(timeout=45000) fun contactDialogOffersNumberSpecificCallsAndCompactActions(){
  var number="";var edited=false;var deleted=false
  compose.activity.setContent{OnKlavTheme{PhoneContactDetails(ContactRow(1,"Example Contact",null,listOf("2025550123","2025550124")),{},{number=it},{},{edited=true},{deleted=true},{})}};pump()
  compose.onNodeWithText("Phone 1").assertIsDisplayed();compose.onNodeWithContentDescription("Call (202) 555-0123").performClick();pump();assertEquals("2025550123",number)
  compose.onNodeWithText("Edit contact").performScrollTo();pump();tap("Edit contact");assertTrue(edited)
  compose.onNodeWithText("Delete contact").performScrollTo();pump();tap("Delete contact");assertTrue(deleted);capture("contact-multiple")
 }
 @Test(timeout=45000) fun singleContactUsesRecentsActionsWithEditInsteadOfSave(){
  var message="";var call="";var edited=false
  compose.activity.setContent{OnKlavTheme{
   PhoneContactDetails(ContactRow(1,"Example Contact",null,listOf("2025550123")),{},
    {call=it},{message=it},{edited=true},{},{})
  }};pump()
  compose.onAllNodesWithText("(202) 555-0123").assertCountEquals(1)
  compose.onNodeWithText("Save contact").assertDoesNotExist()
  tap("Message");tap("Call");tap("Edit contact")
  assertEquals("2025550123",message);assertEquals("2025550123",call);assertTrue(edited)
 }
 @Test(timeout=45000) fun recentDialogFormatsTheNumberOnceAndPreservesActions(){
  var called=false;var removed=false
  compose.activity.setContent{OnKlavTheme{PhoneRecentDetails(PhoneEvent(1,"2025550123","",CallLog.Calls.OUTGOING_TYPE,1793577600000,65),{},{called=true},{},{},{},{removed=true})}};pump()
  compose.onAllNodesWithText("(202) 555-0123",substring=false).assertCountEquals(1);compose.onNodeWithText("1:05").assertIsDisplayed();tap("Call");assertTrue(called)
  compose.onNodeWithText("Delete from history").performScrollTo();pump();tap("Delete from history");assertTrue(removed);capture("recent")
 }
 @Test(timeout=45000) @Config(qualifiers="w320dp-h568dp-mdpi",fontScale=1.4f) fun largeTextDialogKeepsActionsReachableAndPrivateCallsStayPrivate(){
  var called=false
  compose.activity.setContent{OnKlavTheme{PhoneRecentDetails(PhoneEvent(2,"2025550123","Secret name",CallLog.Calls.MISSED_TYPE,1793577600000,0,TelecomManager.PRESENTATION_RESTRICTED),{},{called=true},{},{},{},{})}};pump()
  compose.onNodeWithText("Private number").assertExists();compose.onNodeWithText("Secret name").assertDoesNotExist();compose.onNodeWithText("Call",substring=false).assertDoesNotExist();compose.onNodeWithText("Delete from history").performScrollTo();pump();compose.onNodeWithText("Delete from history").assertIsDisplayed();assertFalse(called);capture("private-large-text")
 }
 @Test(timeout=45000) fun phoneHeaderMatchesHomeHeaderAndMessageKeypadStaysFunctional(){
  var value by mutableStateOf("");var show by mutableStateOf(false)
  val m=PhoneModel(ApplicationProvider.getApplicationContext<Application>())
  compose.activity.setContent{OnKlavTheme{PhoneWorkspace({},m,embeddedInHome=true);if(show)NumberEntryDialog(value,{value=it},{show=false},{})}};pump()
  val header=compose.onNodeWithTag("home-header").getUnclippedBoundsInRoot()
  assertEquals(48f,header.bottom.value-header.top.value,1f)
  compose.onNodeWithContentDescription("Back",substring=false).assertDoesNotExist();capture("consistent-header-keypad")
  compose.runOnUiThread{show=true};pump()
  compose.onNodeWithTag("dialpad-1").performClick();pump();assertEquals("1",value)
  compose.onNodeWithTag("dialpad-⌫").performClick();pump();assertEquals("",value)
  capture("message-keypad-outline")
 }

}
