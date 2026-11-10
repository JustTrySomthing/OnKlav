package app.onklav.home

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.onklav.shared.AppearanceSpec
import app.onklav.shared.OnKlavTheme
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],qualifiers="w320dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class SdkAppActionsTest{
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 private fun pump(){repeat(4){
  androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeBy(100);shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(16))
  compose.runOnUiThread{
   val env=compose.javaClass.getDeclaredField("environment").apply{isAccessible=true}.get(compose);val registry=env.javaClass.superclass!!.getDeclaredField("composeRootRegistry").apply{isAccessible=true}.get(env)
   (registry.javaClass.getMethod("getRegisteredComposeRoots").invoke(registry)as Set<*>).filterNotNull().forEach{root->val view=root.javaClass.getMethod("getView").invoke(root)as android.view.View;val decor=view.rootView;decor.forceLayout();decor.measure(android.view.View.MeasureSpec.makeMeasureSpec(320,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(640,android.view.View.MeasureSpec.AT_MOST));decor.layout(0,0,decor.measuredWidth,decor.measuredHeight);root.javaClass.getMethod("measureAndLayoutForTest").invoke(root)}
  }
 }}
 private fun show(blocked:Boolean=false,overflow:Boolean=false,dark:Boolean=true,onAction:(String)->Unit){
  val actions=listOf(JSONObject("""{"id":"refresh","text":"Refresh","long_action":"refresh.all","long_label":"Refresh all"}"""),JSONObject("""{"id":"remove","text":"Delete","enabled":false,"long_action":"remove.all","long_label":"Delete all"}"""),JSONObject("""{"id":"settings","text":"Options"}"""))
  compose.runOnUiThread{compose.activity.setContent{OnKlavTheme(preview=AppearanceSpec(dark=dark,palette=3)){Surface{Column(Modifier.fillMaxWidth()){SdkAppActionItems(actions,blocked,overflow,onAction)}}}}};pump()
 }
 @Test(timeout=30000)fun floatingPanelsRespectDisabledActionsAndExposeSecondaryActions(){
  var selected="";show{selected=it}
  compose.onNodeWithText("Delete").assertIsNotEnabled();compose.onNodeWithText("Delete all").assertIsNotEnabled()
  compose.onNodeWithText("Refresh all").assertIsEnabled().performClick();assertEquals("refresh.all",selected)
 }
 @Test(timeout=30000)fun pendingOrBusyPanelsDisableEveryGuestAction(){
  var calls=0;show(blocked=true){calls++}
  for(label in listOf("Refresh","Refresh all","Delete","Delete all","Options"))compose.onNodeWithText(label).assertIsNotEnabled()
  assertEquals(0,calls)
 }
 @Test(timeout=30000)fun toolbarOverflowIncludesPrimaryLongActionsInLightTheme(){
  var selected="";show(overflow=true,dark=false){selected=it}
  compose.onNodeWithText("Refresh").assertDoesNotExist();compose.onNodeWithText("Delete").assertDoesNotExist()
  compose.onNodeWithText("Refresh all").assertIsDisplayed().performClick();assertEquals("refresh.all",selected)
  compose.onNodeWithText("Delete all").assertIsNotEnabled();compose.onNodeWithText("Options").assertIsDisplayed()
 }
 @Test(timeout=30000)fun permissionRowsHaveOneLabeledTargetAndWrapAtLargeText(){
  var checked=false
  compose.runOnUiThread{compose.activity.setContent{OnKlavTheme(preview=AppearanceSpec(dark=false,palette=8)){androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(1f,2f)){Surface{SdkPermissionChoice("Browse and change all OnKlav documents and media, including Trash",false){checked=it}}}}}};pump()
  compose.onAllNodes(isToggleable(),useUnmergedTree=true).assertCountEquals(1)
  val row=compose.onNode(isToggleable()).assertIsOff().assertTextContains("Browse and change all OnKlav documents and media, including Trash").assertIsDisplayed()
  val bounds=row.fetchSemanticsNode().boundsInRoot;assertTrue(bounds.left>=0&&bounds.right<=320);assertTrue(bounds.height>=48)
  row.performClick();assertTrue(checked)
 }
 @Test(timeout=30000)fun applyingPermissionsDisablesTheWholeRow(){
  compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{Surface{SdkPermissionChoice("Also delete this app's saved data",true,enabled=false){error("Disabled choice changed")}}}}};pump()
  compose.onNode(isToggleable()).assertIsOn().assertIsNotEnabled().assertTextContains("Also delete this app's saved data")
 }

}
