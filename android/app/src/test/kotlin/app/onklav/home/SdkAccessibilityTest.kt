package app.onklav.home

import android.app.Application
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.onklav.shared.*
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
class SdkAccessibilityTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 private fun pump(){repeat(4){
  androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeBy(100);shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(16))
  compose.runOnUiThread{
   val env=compose.javaClass.getDeclaredField("environment").apply{isAccessible=true}.get(compose);val registry=env.javaClass.superclass!!.getDeclaredField("composeRootRegistry").apply{isAccessible=true}.get(env)
   (registry.javaClass.getMethod("getRegisteredComposeRoots").invoke(registry) as Set<*>).filterNotNull().forEach{root->val view=root.javaClass.getMethod("getView").invoke(root) as android.view.View;val decor=view.rootView;decor.forceLayout();decor.measure(android.view.View.MeasureSpec.makeMeasureSpec(320,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(640,android.view.View.MeasureSpec.AT_MOST));decor.layout(0,0,decor.measuredWidth,decor.measuredHeight);root.javaClass.getMethod("measureAndLayoutForTest").invoke(root)}
  }
 }}
 private fun widgets(vararg nodes:JSONObject,width:Int=320){
  compose.runOnUiThread{compose.activity.setContent{CompositionLocalProvider(LocalDensity provides Density(1f,2f)){OnKlavTheme{
   val model=remember{SdkAppModel(compose.activity.application as Application)};val pool=remember{SdkWebPool()};val speech=remember{SdkSpeechController(compose.activity,"org.example.accessibility",authorize={error("No speech authorization expected")})}
   DisposableEffect(speech){onDispose{speech.close()}}
   Column(Modifier.width(width.dp).padding(12.dp).verticalScroll(rememberScrollState())){nodes.forEach{SdkWidget(it,"test",model,speech,pool,if(it.optString("kind")=="canvas")Modifier.height(220.dp)else Modifier)}}
  }}}};pump()
 }
 @Test(timeout=30000)fun populatedFieldsKeepLabelsErrorsAndKeyboardFocus(){
  widgets(JSONObject("""{"kind":"field","id":"name","hint":"Task name","value":"Existing task","error":"Task name needs attention"} """),JSONObject("""{"kind":"field","id":"email","hint":"Email address","value":"person@example.com","input_kind":"email"} """))
  compose.onNodeWithTag("sdk-name").assertContentDescriptionEquals("Task name").assert(SemanticsMatcher.expectValue(SemanticsProperties.Error,"Task name needs attention"))
  compose.onNodeWithTag("sdk-email").assertContentDescriptionEquals("Email address")
  compose.onNodeWithTag("sdk-name").performSemanticsAction(SemanticsActions.RequestFocus){it()};pump();compose.onNodeWithTag("sdk-name").assertIsFocused()
  compose.onNodeWithTag("sdk-email").performSemanticsAction(SemanticsActions.RequestFocus){it()};pump();compose.onNodeWithTag("sdk-email").assertIsFocused();compose.onNodeWithTag("sdk-name").assertIsNotFocused()
 }
 @Test(timeout=30000)fun togglesExposeOneLabeledCheckboxAndDisabledState(){
  widgets(JSONObject("""{"kind":"toggle","id":"enabled","text":"Enable detailed results","checked":true} """),JSONObject("""{"kind":"toggle","id":"disabled","text":"Unavailable option","checked":false,"enabled":false} """),JSONObject("""{"kind":"select","id":"priority","text":"Task priority","value":"high","options":[{"value":"high","text":"High priority"}]} """))
  compose.onNodeWithTag("sdk-enabled").assertIsOn().assert(SemanticsMatcher.expectValue(SemanticsProperties.Role,Role.Checkbox)).assertTextContains("Enable detailed results")
  compose.onAllNodes(isToggleable(),useUnmergedTree=true).assertCountEquals(2);compose.onNodeWithTag("sdk-disabled").assertIsNotEnabled().assertIsOff()
  assertTrue(compose.onNodeWithTag("sdk-enabled").fetchSemanticsNode().boundsInRoot.height>=48f)
  compose.onNodeWithTag("sdk-priority").assertContentDescriptionEquals("Task priority").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"High priority"))
 }
 @Test(timeout=30000)fun dateTimeActionsAreDistinctAndStayInsideNarrowLargeTextFields(){
  widgets(JSONObject("""{"kind":"field","id":"start","hint":"Start date","value":"2024-02-29","input_kind":"date"} """),JSONObject("""{"kind":"field","id":"finish","hint":"Finish time","value":"23:59","input_kind":"time"} """))
  for(label in listOf("Choose date for Start date","Choose time for Finish time")){
   val node=compose.onNodeWithContentDescription(label).assertIsDisplayed().assertHasClickAction().fetchSemanticsNode();val bounds=node.boundsInRoot
   assertTrue("$label starts outside the screen: $bounds",bounds.left>=0)
   assertTrue("$label ends outside the screen: $bounds",bounds.right<=320)
   assertTrue("$label target is smaller than48dp: $bounds",bounds.width>=48f)
  }
 }
 @Test(timeout=30000)fun sharedDialogsAnnounceTheirPaneAndHeadingAtLargeText(){
  compose.runOnUiThread{compose.activity.setContent{CompositionLocalProvider(LocalDensity provides Density(1f,2f)){OnKlavTheme{ReferenceDialog("Permission needed",{},footer={DialogAction("Keep working"){}}){Text("Review this app's permissions in Settings.",Modifier.padding(20.dp))}}}}};pump()
  compose.onNodeWithTag("dialog-Permission needed").assert(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle,"Permission needed"));compose.onNodeWithText("Permission needed").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading));compose.onNodeWithText("Keep working").assertIsDisplayed()
 }
 @Test(timeout=30000)fun paletteTargetsKeepTheirSizeAndSelectedStateWhenNarrow(){
  widgets(JSONObject().put("kind","palette").put("id","colors").put("value",0xffe94242L),width=240)
  compose.onNodeWithTag("sdk-color-Red").assertIsSelected()
  for(color in listOf("Yellow","Green","Red","Black","White")){
   val node=compose.onNodeWithTag("sdk-color-$color").performScrollTo().assertIsDisplayed().assertContentDescriptionEquals(color).fetchSemanticsNode()
   assertTrue("$color target: ${node.boundsInRoot}",node.boundsInRoot.width>=48f&&node.boundsInRoot.height>=48f)
   assertTrue(node.boundsInRoot.left>=0&&node.boundsInRoot.right<=240)
  }
 }
 @Test(timeout=30000)fun mediaAndImageRowsAllowMissingOptionalActionsAndTimestamps(){
  widgets(JSONObject("""{"kind":"media_record","id":"video-basic","text":"Demo video","video":"dQw4w9WgXcQ","url":"https://www.youtube.com/watch?v=dQw4w9WgXcQ","time":1}"""),JSONObject("""{"kind":"image_record","id":"image-basic","document":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}"""),JSONObject("""{"kind":"image_record","id":"image-disabled","text":"Disabled image","document":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","enabled":false}"""))
  compose.onNodeWithTag("sdk-video-basic").assertHasClickAction().assert(SemanticsMatcher.expectValue(SemanticsProperties.Role,Role.Button)).assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnLongClick))
  compose.onNodeWithTag("sdk-image-basic").assertHasClickAction().assertTextContains("Image")
  compose.onNodeWithContentDescription("Remove Demo video").assertDoesNotExist();compose.onNodeWithContentDescription("Delete Image").assertDoesNotExist()
  compose.onNodeWithTag("sdk-image-disabled").assertIsNotEnabled()
 }
 @Test(timeout=30000)fun drawingToolbarScrollsWithoutCoveringToolsAtLargeText(){
  widgets(JSONObject("""{"kind":"canvas","id":"drawing","document":null,"tool":"move","color":4294901760,"width":4,"on_change":"changed","undo_action":"undo","redo_action":"redo","tools_action":"tools"}"""),width=240)
  val tools=compose.onNodeWithTag("sdk-canvas-tools").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
  for(label in listOf("Undo","Redo","Fit")){
   val bounds=compose.onNodeWithText(label).performScrollTo().assertIsDisplayed().fetchSemanticsNode().boundsInRoot
   assertTrue("$label overlaps tools: $bounds / $tools",bounds.right<=tools.left)
   assertTrue("$label is too short: $bounds",bounds.height>=48f)
  }
 }

 @Test(timeout=30000)fun audioControlsLabelTheirPositionAndAllowClipsWithoutDeleteActions(){
  widgets(JSONObject("""{"kind":"speech","id":"reader","value":"Example text for playback."}"""),JSONObject("""{"kind":"voice_clip","id":"clip","clip":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","text":"Voice note"}"""))
  compose.onNodeWithTag("sdk-speech-position").assertContentDescriptionEquals("Reading position")
  compose.onNodeWithTag("sdk-clip-listen").assertIsDisplayed().assertHasClickAction()
  compose.onNodeWithContentDescription("Delete Voice note").assertDoesNotExist()
 }

 @Test(timeout=30000)fun sharedHeaderExposesLoadingWithoutHidingBack(){
  compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{SdkFrameHeader(JSONObject().put("title","Example"),true,onBack={},onAction={}){}}}};pump()
  compose.onNodeWithTag("sdk-app-loading").assertContentDescriptionEquals("Loading")
  compose.onNodeWithContentDescription("Close").assertHasClickAction()
 }
 @Test(timeout=30000)fun insertingAnEarlierControlKeepsExistingControlIdentity(){
  var prepend by mutableStateOf(false);var serial=0
  compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{Column{
   val nodes=(if(prepend)listOf("new")else emptyList())+listOf("first","second")
   SdkBodyNodes(nodes.map{JSONObject().put("id",it).put("kind","label")},1,false,false){node,modifier->
    val instance=remember{++serial};Text(node.getString("id")+":"+instance,modifier)
   }
  }}}};pump()
  compose.onNodeWithText("first:1").assertExists();compose.onNodeWithText("second:2").assertExists()
  compose.runOnUiThread{prepend=true};pump()
  compose.onNodeWithText("first:1").assertExists();compose.onNodeWithText("second:2").assertExists()
 }

 @Test(timeout=30000)fun rtlHeaderKeepsBackAndActionsInsideScreen(){
  compose.runOnUiThread{compose.activity.setContent{CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl,LocalDensity provides Density(1f,2f)){OnKlavTheme{
   SdkFrameHeader(JSONObject().put("title","تطبيق طويل").put("actions",org.json.JSONArray().put(JSONObject().put("id","add").put("kind","icon").put("icon","add").put("text","إضافة"))),false,onBack={},onAction={}){}
  }}}};pump()
  val back=compose.onNodeWithContentDescription("Close").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
  val action=compose.onNodeWithContentDescription("إضافة").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
  assertTrue(back.left>action.left);assertTrue(back.right<=320);assertTrue(action.left>=0)
 }
 @Test(timeout=30000)fun sharedStatusAnnouncesUpdates(){
  widgets(JSONObject().put("kind","label").put("id","status").put("text","Loading items").put("status",true))
  compose.onNodeWithTag("sdk-status").assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion,LiveRegionMode.Polite))
 }

 @Test(timeout=30000)fun storageControlsStayReachableAndApplyOnlyAfterATap(){
  val app=kotlinx.coroutines.runBlocking{AppSdk.bootstrap(compose.activity);AppSdk.list(compose.activity).single{it.child("manifest").getString("id")=="app.onklav.social"}}
  compose.runOnUiThread{compose.activity.setContent{CompositionLocalProvider(LocalDensity provides Density(1f,2f)){OnKlavTheme{SdkStorageSettings(app){}}}}};pump()
  compose.waitUntil(5000){pump();runCatching{compose.onNodeWithText("Saved files:",substring=true).assertExists()}.isSuccess}
  compose.onNodeWithText("Close").assertIsDisplayed()
  compose.onNodeWithText("Saved files:",substring=true).performScrollTo().assertIsDisplayed().performClick()
  compose.waitUntil(5000){pump();runCatching{compose.onNodeWithText("256 MiB limit",substring=true).assertExists()}.isSuccess}
  compose.onNodeWithText("Close").assertIsDisplayed()
 }

}
