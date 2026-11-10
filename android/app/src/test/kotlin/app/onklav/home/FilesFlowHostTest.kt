package app.onklav.home

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.onklav.shared.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*

/** Actual signed Files controller -> SDK broker -> canonical Rust content store. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],qualifiers="w432dp-h936dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class FilesFlowHostTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 private fun pump(){repeat(8){androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeBy(100);Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(16));compose.runOnUiThread{
  val env=compose.javaClass.getDeclaredField("environment").apply{isAccessible=true}.get(compose);val reg=env.javaClass.superclass!!.getDeclaredField("composeRootRegistry").apply{isAccessible=true}.get(env)
  (reg.javaClass.getMethod("getRegisteredComposeRoots").invoke(reg)as Set<*>).filterNotNull().forEach{root->val view=root.javaClass.getMethod("getView").invoke(root)as android.view.View;val decor=view.rootView;decor.forceLayout();decor.measure(android.view.View.MeasureSpec.makeMeasureSpec(432,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(936,android.view.View.MeasureSpec.AT_MOST));decor.layout(0,0,decor.measuredWidth,decor.measuredHeight);root.javaClass.getMethod("measureAndLayoutForTest").invoke(root)}
 }}}
 private fun rawNodes():List<androidx.compose.ui.semantics.SemanticsNode>{
  val result=mutableListOf<androidx.compose.ui.semantics.SemanticsNode>()
  compose.runOnUiThread{
   val env=compose.javaClass.getDeclaredField("environment").apply{isAccessible=true}.get(compose)
   val registry=env.javaClass.superclass!!.getDeclaredField("composeRootRegistry").apply{isAccessible=true}.get(env)
   fun visit(n:androidx.compose.ui.semantics.SemanticsNode){result+=n;n.children.forEach(::visit)}
   (registry.javaClass.getMethod("getRegisteredComposeRoots").invoke(registry)as Set<*>).filterNotNull().forEach{root->
    val owner=root.javaClass.getMethod("getSemanticsOwner").invoke(root)as androidx.compose.ui.semantics.SemanticsOwner
    visit(owner.unmergedRootSemanticsNode)
   }
  };return result
 }
 private fun waitFor(tag:String){println("Files UI waiting: $tag");try{compose.waitUntil(12000){pump();rawNodes().any{it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag)==tag}}}catch(e:Throwable){throw AssertionError("Missing $tag\n"+rawNodes().joinToString("\n"){it.config.toString()},e)}}
 private fun click(tag:String){waitFor(tag);compose.waitUntil(12000){pump();runCatching{compose.onNodeWithTag(tag).assertIsEnabled()}.isSuccess};compose.onNodeWithTag(tag).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick){it()};pump()}
 private fun text(value:String){compose.waitUntil(12000){pump();compose.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty()};compose.onNodeWithText(value).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick){it()};pump()}
 private fun sdk(action:String) {
  if(action.startsWith("options:")) {
   val item="sdk-entry:"+action.removePrefix("options:")
   val option=hasContentDescription("Options for ",substring=true) and hasAnyAncestor(hasTestTag(item))
   compose.waitUntil(12000){pump();compose.onAllNodes(option).fetchSemanticsNodes().isNotEmpty()}
   compose.onNode(option).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick){it()};pump()
  }else if(action=="new.folder"){compose.onNodeWithContentDescription("Create folder").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick){it()};pump()}else if(action=="home"){compose.waitUntil(12000){pump();runCatching{compose.onNodeWithContentDescription("Files home").assertIsEnabled()}.isSuccess};compose.onNodeWithContentDescription("Files home").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick){it()};pump()}else click("sdk-$action")
 }
 private fun request(action:String,extra:JSONObject=JSONObject())=AppSdk.sync(compose.activity,extra.put("action",action))
 private fun document():String=FileSelectionSession(compose.activity).use{s->s.start();s.importSaved(listOf("documents"),java.io.File(compose.activity.cacheDir,"flow-seed.txt").apply{writeText("One body saved in the shared Files library.")},"Original.txt","text/plain").getString("id")}
 private fun start(){
  androidx.test.espresso.IdlingPolicies.setMasterPolicyTimeout(10,java.util.concurrent.TimeUnit.SECONDS);runBlocking{AppSdk.bootstrap(compose.activity)}
  compose.mainClock.autoAdvance=false;compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{SdkAppScreen("app.onklav.files"){}}}};pump();waitFor("sdk-entry:documents")
  compose.onNodeWithContentDescription("App controls").assertDoesNotExist();compose.onNodeWithContentDescription("Refresh").assertDoesNotExist()
 }

 @Test(timeout=120000) fun fileIconFoldersRenameMoveTrashAndRestoreKeepDocumentBytes(){
  runBlocking{AppSdk.bootstrap(compose.activity)};val id=document();start()
  sdk("entry:documents");sdk("new.folder");waitFor("sdk-name");compose.onNodeWithTag("sdk-name").performTextInput("Work");sdk("name.save");sdk("options:$id");sdk("rename");waitFor("sdk-name");compose.onNodeWithTag("sdk-name").performTextReplacement("bad/name.txt");sdk("name.save");waitFor("sdk-menu.status");compose.onNodeWithTag("sdk-menu.status").assertTextContains("without slashes",substring=true);compose.onNodeWithTag("sdk-name").performTextReplacement("Renamed Note.txt");sdk("name.save");sdk("options:$id");sdk("move")
  val files=request("launch",JSONObject().put("id","app.onklav.files"))as JSONObject
  val token=files.getString("session");val docs=request("event",JSONObject().put("session",token).put("event",JSONObject().put("action","entry:documents")))as JSONObject
  val work=docs.child("frame").child("state").getJSONArray("items").let{a->(0 until a.length()).map{a.getJSONObject(it)}.single{it.getString("name")=="Work"}}.getString("id");request("close",JSONObject().put("session",token))
  sdk("entry:$work");text("Paste here");waitFor("sdk-entry:$id");compose.onNodeWithText("Renamed Note.txt").assertExists();sdk("options:$id");sdk("trash")
  val model=androidx.lifecycle.ViewModelProvider(compose.activity).get("sdk-app.onklav.files",SdkAppModel::class.java)
  compose.waitUntil(12000){pump();!model.busy&&model.frame.optJSONObject("confirm")!=null&&compose.onAllNodesWithTag("sdk-trash").fetchSemanticsNodes().isEmpty()}
  text("Delete")
  compose.waitUntil(12000){pump();!model.busy&&model.pending==null&&model.frame.optJSONObject("confirm")==null&&model.frame.child("state").getJSONArray("items").let{a->(0 until a.length()).none{a.getJSONObject(it).optString("id")==id}}}

  sdk("home");sdk("entry:trash");sdk("options:$id");sdk("restore");sdk("home");sdk("entry:documents");sdk("entry:$work");waitFor("sdk-entry:$id")
  FileSelectionSession(compose.activity).use{s->s.start();s.navigate(listOf("documents",work));val item=s.state.nodes("items").single{it.optString("id")==id};assertEquals("One body saved in the shared Files library.",java.io.File(s.choose(item).getString("path")).readText())}

 }
 @Test(timeout=45000) fun modernCalculatorKeypadUsesSignedArithmetic(){
  runBlocking{AppSdk.bootstrap(compose.activity)}
  compose.mainClock.autoAdvance=false
  compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{SdkAppScreen("app.onklav.calc",exit={})}}}
  text("7");text("×");text("8");text("=")
  compose.waitUntil(12000){pump();runCatching{compose.onNodeWithContentDescription("Calculation result").assertTextEquals("56")}.isSuccess}
  text("+");text("4");text("=")
  compose.waitUntil(12000){pump();runCatching{compose.onNodeWithContentDescription("Calculation result").assertTextEquals("60")}.isSuccess}
  text("AC");text("9");text("÷");text("0");text("=")
  compose.waitUntil(12000){pump();compose.onAllNodesWithText("Cannot divide by zero").fetchSemanticsNodes().isNotEmpty()}
 }
}
