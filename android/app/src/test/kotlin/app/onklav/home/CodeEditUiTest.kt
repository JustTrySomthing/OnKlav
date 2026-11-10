package app.onklav.home

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.onklav.shared.OnKlavTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29],qualifiers="w360dp-h720dp-mdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
class CodeEditUiTest{
 @get:Rule val keys=EmptyKeyStoreRule()
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 private lateinit var m:CodeEditModel
 private fun pump(){repeat(3){androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50));compose.mainClock.advanceTimeBy(100)}}
 private fun waitFor(test:()->Boolean){compose.waitUntil(15000){pump();test()}}
 @Before fun setup(){val app=compose.activity.application;runBlocking{AppSdk.bootstrap(app)};m=CodeEditModel(app);compose.activity.setContent{OnKlavTheme{CodeEditWorkspace({},m)}};compose.waitForIdle();compose.mainClock.autoAdvance=false;waitFor{!m.busy};compose.runOnUiThread{m.apply(CodeText.Edit("fn main() {\n    println!(\"Hello\");\n}\n",0))};pump()}
 private fun capture(name:String="phone"){compose.runOnUiThread{val v=compose.activity.window.decorView;val b=android.graphics.Bitmap.createBitmap(v.width,v.height,android.graphics.Bitmap.Config.ARGB_8888);v.draw(android.graphics.Canvas(b));File(System.getProperty("onklav.project.root"),"docs/evidence/code-edit/$name.png").apply{parentFile!!.mkdirs()}.outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
 @Test fun tabsSearchReplaceUndoAndLanguageAreReachable(){
  compose.onNodeWithContentDescription("Code options").performClick();pump();compose.onNodeWithText("Find & replace").performClick();pump();compose.onNodeWithText("Find",substring=false).performTextInput("Hello");compose.onNodeWithText("Replace with",substring=false).performTextInput("OnKlav");compose.onNodeWithContentDescription("Next match").performClick();pump();assertEquals("Hello",m.current!!.text.substring(m.current!!.start,m.current!!.end));compose.onNodeWithText("Replace",substring=false).performClick();pump();assertTrue(m.current!!.text.contains("OnKlav"));compose.onNodeWithContentDescription("Close search").performClick();pump();compose.onNodeWithContentDescription("Undo").performClick();pump();assertTrue(m.current!!.text.contains("Hello"))
  compose.onNodeWithContentDescription("Code options").performClick();pump();compose.onNodeWithText("Language",substring=false).performClick();pump();compose.onNodeWithText("Rust",substring=false).performClick();pump();assertEquals("Rust",m.current!!.language);fun editor(v:android.view.View):CodeInput?{if(v is CodeInput)return v;if(v is android.view.ViewGroup)for(i in 0 until v.childCount){editor(v.getChildAt(i))?.let{return it}};return null};waitFor{editor(compose.activity.window.decorView)?.let{it.text.getSpans(0,it.text.length,android.text.style.ForegroundColorSpan::class.java).isNotEmpty()}==true};capture()
  compose.runOnUiThread{m.new("game.gd")};pump();assertEquals("GDScript",m.current!!.language);assertEquals(2,m.documents.size)
 }
 @Test fun nativeNewlineAddsIndentWithoutLosingTheCursor(){
  compose.runOnUiThread{m.apply(CodeText.Edit("fn main() {",11))};pump()
  fun locate(v:android.view.View):CodeInput?{if(v is CodeInput)return v;if(v is android.view.ViewGroup)for(i in 0 until v.childCount){locate(v.getChildAt(i))?.let{return it}};return null}
  compose.runOnUiThread{val input=locate(compose.activity.window.decorView)!!;input.text.append("\n")};pump();assertEquals("fn main() {\n    ",m.current!!.text);assertEquals(m.current!!.text.length,m.current!!.start)
 }
 @Test @Config(qualifiers="w320dp-h568dp-mdpi",fontScale=1.4f) fun smallPhoneKeepsActionsAndCodeVisible(){
  compose.onNodeWithContentDescription("Save code file").assertIsDisplayed();compose.onNodeWithContentDescription("New code file").assertIsDisplayed();compose.onNodeWithContentDescription("Code options").performClick();pump();compose.onNodeWithText("Find & replace").performClick();pump();compose.onNodeWithContentDescription("Close search").assertIsDisplayed();capture("small-large-text")
 }
 @Test fun draftsAndNativeTextSelectionRecover(){
  compose.runOnUiThread{m.new("screen.kt");m.apply(CodeText.Edit("class Screen {\n}\n",6,12));m.flush()};waitFor{runCatching{File(compose.activity.filesDir,"code-edit/workspace.json").readText().contains("screen.kt")}.getOrDefault(false)};val next=CodeEditModel(compose.activity.application);waitFor{!next.busy&&next.current?.name=="screen.kt"};assertEquals(m.current!!.text,next.current!!.text);assertEquals(6,next.current!!.start);assertEquals(12,next.current!!.end)
 }
}
