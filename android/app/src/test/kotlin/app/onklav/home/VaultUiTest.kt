package app.onklav.home

import android.app.Application
import android.text.SpannableStringBuilder
import android.view.WindowManager
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29],qualifiers="w360dp-h720dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VaultUiTest{
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 private val app get()=ApplicationProvider.getApplicationContext<Application>()
 @Before fun reset(){listOf("vault.bin","vault.bin.new","vault.bin.bak").forEach{File(app.filesDir,it).delete()}}
 private fun pump(){shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50))}
 private fun waitFor(model:VaultModel){compose.waitUntil(15000){pump();!model.busy};assertNull(model.error);compose.waitForIdle();if(!model.locked)compose.onNodeWithContentDescription("Bold").assertExists()}
 private fun editor(view:android.view.View=compose.activity.window.decorView):WriteNativeEditor?{if(view is WriteNativeEditor)return view;if(view is android.view.ViewGroup)for(i in 0 until view.childCount){editor(view.getChildAt(i))?.let{return it}};return null}
 private fun capture(name:String){compose.waitForIdle();compose.runOnUiThread{val view=compose.activity.window.decorView;val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888);view.draw(android.graphics.Canvas(bitmap));File(System.getProperty("onklav.project.root"),"docs/evidence/vault-recovery/$name").apply{parentFile!!.mkdirs()}.outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}}
 @Test fun setupMatchesPasswordsAndOffersVisibilityThenFlushesAndLocks(){
  val model=VaultModel(app);var closed=false
  compose.activity.setContent{OnKlavTheme{VaultWorkspace({closed=true},model)}}
  compose.onNodeWithText("Continue").performScrollTo().assertIsNotEnabled()
  val fields=compose.onAllNodes(hasSetTextAction());fields[0].performTextInput("a long unique password");fields[1].performTextInput("different password")
  compose.onNodeWithText("Passwords do not match").performScrollTo().assertExists();compose.onNodeWithText("Continue").assertIsNotEnabled()
  fields[1].performTextReplacement("a long unique password")
  compose.onAllNodesWithContentDescription("Show password")[0].performClick();compose.onAllNodesWithContentDescription("Hide password")[0].assertExists();capture("vault-setup.png")
  compose.onNodeWithText("Continue").performScrollTo().performClick();waitFor(model)
  assertTrue(model.locked);assertTrue(model.showQuestions);compose.onNodeWithText("Complete").performScrollTo().assertIsNotEnabled()
  repeat(3){i->compose.onNodeWithContentDescription("Choose question ${i+1}").performScrollTo().performClick();compose.onNodeWithText(VaultRecovery.questions[i][2]).performClick()}
  repeat(3){i->compose.onNodeWithText("Answer ${i+1}").performScrollTo().performTextInput("private answer $i")}
  capture("vault-recovery-questions.png")
  compose.onNodeWithText("Complete").performScrollTo().performClick();waitFor(model);assertTrue(model.locked);assertNull(model.document)
  compose.onNodeWithText("Password").performScrollTo().performTextInput("a long unique password")
  compose.onNodeWithText("Unlock").performScrollTo().performClick();waitFor(model)
  assertFalse(model.locked);assertTrue(compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE!=0)
  compose.onNodeWithContentDescription("Bold").assertExists();compose.onNodeWithContentDescription("Insert image").assertExists()
  compose.runOnUiThread{editor()!!.requestFocus();editor()!!.setText("My private information");editor()!!.setSelection(0,2)}
  compose.onNodeWithContentDescription("Bold").performClick();assertTrue(model.document!!.marks.any{it.kind=="style"})
  compose.runOnUiThread{val clipboard=app.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager;clipboard.setPrimaryClip(android.content.ClipData.newHtmlText("Text"," pasted","<a href=\"javascript:unsafe\"> pasted</a>"));val e=editor()!!;e.requestFocus();e.setSelection(e.length());assertTrue("Paste rejected",e.onTextContextMenuItem(android.R.id.paste));assertEquals("My private information pasted",model.document!!.text);assertFalse("Foreign unsafe link was imported",model.document!!.marks.any{it.kind=="link"})}
  compose.onNodeWithText("Save",useUnmergedTree=true).performClick();compose.onNodeWithText("Saved").assertExists();capture("vault-editor.png")
  compose.runOnUiThread{editor()!!.setText("Final changes before leaving")}
  compose.onNodeWithText("Lock & close").performClick();assertTrue(closed);assertTrue(model.locked);assertNull(model.document)
  val read=VaultStore(app);assertEquals("Final changes before leaving",read.unlock("a long unique password".toCharArray()).text);read.close()
  compose.onNodeWithText("Unlock Vault").assertExists();compose.onNodeWithContentDescription("Bold").assertDoesNotExist()
 }
 @Test fun unfinishedPasswordOnlyVaultShowsNewPasswordAndConfirmationWithoutUnlock(){
  VaultStore(app).apply{create("unknown old password".toCharArray());close()}
  val model=VaultModel(app);compose.activity.setContent{OnKlavTheme{VaultWorkspace({},model)}}
  compose.onNodeWithText("Confirm password").performScrollTo().assertIsDisplayed();compose.onNodeWithText("Unlock").assertDoesNotExist();capture("vault-incomplete-restart.png")
  compose.onNodeWithText("Password").performScrollTo().performTextInput("new setup password");compose.onNodeWithText("Confirm password").performScrollTo().performTextInput("new setup password")
  compose.onNodeWithText("Continue").performScrollTo().performClick();waitFor(model);compose.onNodeWithText("Complete").performScrollTo().assertIsNotEnabled();assertTrue(model.showQuestions);assertNull(model.document)
 }
 @Test fun backgroundingHidesEditorAndRequiresPasswordAgain(){
  val model=VaultModel(app);compose.activity.setContent{OnKlavTheme{VaultWorkspace({},model)}}
  compose.runOnUiThread{model.open("a long unique password".toCharArray(),true);model.completeSetup(listOf(0,0,0),List(3){"private answer $it".toCharArray()})};waitFor(model)
  compose.runOnUiThread{model.open("a long unique password".toCharArray(),false)};waitFor(model)
  compose.runOnUiThread{model.edit(SpannableStringBuilder("Background draft"));(compose.activity.lifecycle as androidx.lifecycle.LifecycleRegistry).handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_STOP)}
  assertTrue(model.locked);assertNull(model.document)
  compose.runOnUiThread{(compose.activity.lifecycle as androidx.lifecycle.LifecycleRegistry).handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_RESUME)}
  compose.onNodeWithText("Unlock Vault").assertExists()
  compose.runOnUiThread{model.open("a long unique password".toCharArray(),false)};waitFor(model);assertEquals("Background draft",model.document!!.text)
 }
 @Test fun autosavePersistsTheLatestTypingWithoutPressingSave(){
  val model=VaultModel(app);compose.activity.setContent{OnKlavTheme{VaultWorkspace({},model)}}
  compose.runOnUiThread{model.open("a long unique password".toCharArray(),true);model.completeSetup(listOf(0,0,0),List(3){"private answer $it".toCharArray()})};waitFor(model)
  compose.runOnUiThread{model.open("a long unique password".toCharArray(),false)};waitFor(model)
  compose.runOnUiThread{editor()!!.setText("First draft");editor()!!.setText("Latest private draft")}
  compose.waitUntil(15000){pump();model.status=="Saved"}
  val reader=VaultStore(app);assertEquals("Latest private draft",reader.unlock("a long unique password".toCharArray()).text);reader.close()
 }
 @Test @Config(qualifiers="w640dp-h360dp-mdpi",fontScale=1.4f) fun landscapeSetupKeepsCreateAndCancelReachable(){
  val model=VaultModel(app);compose.activity.setContent{OnKlavTheme{VaultWorkspace({},model)}}
  compose.onNodeWithText("Confirm password").performScrollTo().assertIsDisplayed()
  compose.onNodeWithText("Continue").performScrollTo().assertIsDisplayed();compose.onNodeWithText("Cancel").performScrollTo().assertIsDisplayed();capture("vault-landscape.png")
 }
 @Test fun resetRequiresAllThreeAnswersAndMatchingPasswordsThenReturnsToLogin(){
  val store=VaultStore(app);store.createWithRecovery("original password".toCharArray(),listOf(0,1,2),List(3){"private answer $it".toCharArray()});store.close()
  val model=VaultModel(app);compose.activity.setContent{OnKlavTheme{VaultWorkspace({},model)}}
  compose.onNodeWithText("Reset password").performScrollTo().performClick();compose.onNodeWithText("What is your favorite book?").performScrollTo().assertExists();compose.onNodeWithText("What was your childhood best friend's name?").performScrollTo().assertExists()
  compose.onAllNodesWithText("Reset password").onLast().performScrollTo().assertIsNotEnabled()
  repeat(3){i->compose.onNodeWithText("Answer ${i+1}").performScrollTo().performTextInput("private answer $i")}
  compose.onNodeWithText("New password").performScrollTo().performTextInput("replacement password");compose.onNodeWithText("Confirm new password").performScrollTo().performTextInput("different password")
  compose.onAllNodesWithText("Reset password").onLast().performScrollTo().assertIsNotEnabled();compose.onNodeWithText("Confirm new password").performScrollTo().performTextReplacement("replacement password")
  capture("vault-reset.png")
  compose.onAllNodesWithText("Reset password").onLast().performScrollTo().performClick();waitFor(model);assertTrue(model.locked);assertNull(model.document)
  compose.onNodeWithText("Unlock Vault").assertExists();compose.onNodeWithText("Password reset. Enter your new password.").assertExists()
 }

}
