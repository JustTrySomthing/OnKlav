package app.onklav.home
import android.graphics.Bitmap
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
class MailWorkflowUiTest{
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 private lateinit var model:MailModel
 private lateinit var older:MailHeader
 private lateinit var newer:MailHeader
 private fun waitFor(test:()->Boolean){compose.waitUntil(15000){shadowOf(android.os.Looper.getMainLooper()).idle();test()}}
 private fun capture(name:String){compose.runOnUiThread{val v=compose.activity.window.decorView;val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(android.graphics.Canvas(b));File(System.getProperty("onklav.project.root"),"docs/evidence/email-polish/$name.png").apply{parentFile!!.mkdirs()}.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
 @Before fun setup(){
  val app=compose.activity.application;runBlocking{AppSdk.bootstrap(app)};val disk=MailDisk(app,MailTestKeys());val a=MailAccount.preset("Yahoo","me@yahoo.com");disk.saveAccounts(listOf(a))
  older=MailHeader(a.id,"INBOX",1,1,"Alice <alice@example.org>",a.email,"Weekend plans",1000,true,false,messageId="<one@example.org>")
  newer=older.copy(uid=2,from="Bob <bob@example.org>",subject="Re: Weekend plans",date=2000,seen=false,messageId="<two@example.org>",references=listOf("<one@example.org>"))
  disk.saveHeaders(a.id,listOf(older,newer));disk.write(disk.bodyName(older),MailBody("Original plans").json());disk.write(disk.bodyName(newer),MailBody("Let's meet on Saturday").json())
  model=MailModel(app,disk,false);compose.activity.setContent{OnKlavTheme{EmailContent(model,autoSync=false)}};waitFor{!model.busy};compose.waitForIdle()
 }
 @Test fun conversationCardsOpenReaderAndComposerSuggestsRecipientsWithoutNetwork(){
  compose.onNodeWithText("2 messages").assertIsDisplayed();capture("inbox")
  compose.onNodeWithText("Re: Weekend plans").performClick();waitFor{!model.busy&&model.body!=null};compose.onNodeWithText("Conversation · 2 downloaded messages").assertExists();capture("reader")
  compose.onNodeWithText("Reply all").performScrollTo().performClick();waitFor{model.draft!=null};compose.onNodeWithContentDescription("Send email").assertIsDisplayed();capture("compose")
  compose.onNodeWithText("Save draft").performClick();waitFor{model.draft==null&&!model.busy}
  compose.onNodeWithContentDescription("Compose email").performClick();waitFor{model.draft!=null}
  compose.onNodeWithContentDescription("Send email").assertIsNotEnabled();compose.onNodeWithText("To",substring=false).performTextInput("al")
  waitFor{compose.onAllNodesWithContentDescription("Use alice@example.org").fetchSemanticsNodes().isNotEmpty()};compose.onNodeWithContentDescription("Use alice@example.org").assertIsDisplayed();compose.onNodeWithContentDescription("Use alice@example.org").performClick()
  compose.onNodeWithContentDescription("Send email").assertIsEnabled();assertTrue(model.draft!!.to.contains("alice@example.org"));capture("recipients")
 }
 @Test @Config(qualifiers="w320dp-h568dp-mdpi",fontScale=1.4f) fun smallPhoneLargeTextKeepsKeyComposerActionsVisible(){
  compose.onNodeWithContentDescription("Compose email").performClick();waitFor{model.draft!=null}
  compose.onNodeWithContentDescription("Save draft").assertIsDisplayed();compose.onNodeWithContentDescription("Send email").assertIsDisplayed();compose.onNodeWithContentDescription("Attach files").assertIsDisplayed();capture("small-compose")
 }
}
