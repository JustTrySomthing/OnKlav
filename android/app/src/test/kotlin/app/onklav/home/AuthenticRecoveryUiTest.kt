package app.onklav.home
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.onklav.shared.OnKlavTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*
import java.io.File
@RunWith(RobolectricTestRunner::class) @Config(sdk=[29],qualifiers="w360dp-h720dp-mdpi")
class AuthenticRecoveryUiTest{
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 private lateinit var model:AuthenticModel
 private fun waitFor(test:()->Boolean){compose.waitUntil(20000){shadowOf(android.os.Looper.getMainLooper()).idle();test()}}
 @Before fun setup(){val app=compose.activity.application;File(app.filesDir,"authentic.bin").delete();model=AuthenticModel(app);compose.activity.setContent{OnKlavTheme{AuthenticWorkspace({},model)}};compose.waitForIdle()}
 @Test fun incompleteSetupCreatesNoFileAndCompleteRequiresAllAnswersThenPassword(){
  compose.onNodeWithText("Complete Authentic setup").assertExists();compose.onNodeWithText("Complete",substring=false).performScrollTo().assertIsNotEnabled()
  compose.onNodeWithText("Create password",substring=false).performTextInput("test-password");compose.onNodeWithText("Confirm password",substring=false).performTextInput("test-password")
  assertFalse(model.exists);compose.runOnUiThread{model.lock()};compose.waitForIdle();assertFalse(model.exists)
  compose.runOnUiThread{model.completeSetup("test-password".toCharArray(),listOf(0,0,0),listOf("snack","movie","car").map{it.toCharArray()})};waitFor{model.exists&&!model.busy};assertFalse(model.unlocked);assertNotNull(model.recoveryChoices)
  compose.runOnUiThread{model.unlock("test-password".toCharArray())};waitFor{model.unlocked&&!model.busy}
 }
 @Test fun passwordOnlyLegacySetupCannotExposeAccountsBeforeRecovery(){
  val s=AuthenticStore(compose.activity.application);s.create("old-password".toCharArray());s.close()
  val next=AuthenticModel(compose.activity.application);compose.runOnUiThread{next.unlock("old-password".toCharArray())};waitFor{next.enrolling&&!next.busy};assertFalse(next.unlocked);assertTrue(next.accounts.isEmpty())
  compose.runOnUiThread{next.completeSetup(null,listOf(0,0,0),listOf("snack","movie","car").map{it.toCharArray()})};waitFor{!next.busy&&next.recoveryChoices!=null};assertFalse(next.unlocked)
 }
}
