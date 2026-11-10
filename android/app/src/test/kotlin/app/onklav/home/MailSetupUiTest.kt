package app.onklav.home

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.onklav.shared.OnKlavTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29],qualifiers="w360dp-h780dp-mdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
class MailSetupUiTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 @Test @Config(qualifiers="w320dp-h568dp-mdpi",fontScale=1.4f) fun fourChoicesRemainReadableAndPutGmxFirst(){
  val selected = mutableListOf<String>()
  var signup = false
  compose.activity.setContent { OnKlavTheme { Column { MailSetupChoices(createGmx = { signup = true }) { selected.add(it) } } } }
  val labels = listOf("GMX", "Yahoo", "Google", "Private domain")
  val positions = labels.map { compose.onNodeWithText(it).assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top }
  assertEquals(positions.sorted(), positions)
  labels.forEach { compose.onNodeWithText(it).performClick() }
  assertEquals(listOf("GMX", "Yahoo", "Gmail", "Other"), selected)
  compose.onNodeWithText("Create a GMX account").performClick()
  assertTrue(signup)
  listOf("Gmail","Outlook","iCloud","Mail.com").forEach{compose.onNodeWithText(it).assertDoesNotExist()}
 }
 @Test fun gmxInstructionsOpenOfficialSetupPages() {
  val links = mutableListOf<String>()
  compose.activity.setContent { OnKlavTheme { MailAppPasswordDirections("GMX", {}, links::add) } }
  compose.onNodeWithText("How to enable IMAP").performScrollTo().performClick()
  compose.onNodeWithText("App password instructions").performScrollTo().performClick()
  compose.onNodeWithText("Open account settings").performScrollTo().performClick()
  assertEquals(listOf(MailSetup.gmxImapHelp, MailSetup.gmxPasswordHelp, "https://www.gmx.com/"), links)
 }
 @Test fun googleInstructionsOpenOfficialHelp(){
  val links=mutableListOf<String>();compose.activity.setContent{OnKlavTheme{MailAppPasswordDirections("Gmail",{},links::add)}}
  compose.onNodeWithText("App password instructions").performScrollTo().performClick();compose.onNodeWithText("Open account settings").performScrollTo().performClick()
  assertEquals(listOf(MailSetup.googlePasswordHelp,"https://myaccount.google.com/apppasswords"),links)
 }
 @Test fun yahooInstructionsOpenOfficialHelp(){
  val links=mutableListOf<String>();compose.activity.setContent{OnKlavTheme{MailAppPasswordDirections("Yahoo",{},links::add)}}
  compose.onNodeWithText("App password instructions").performScrollTo().performClick();compose.onNodeWithText("Open account settings").performScrollTo().performClick()
  assertEquals(listOf(MailSetup.yahooPasswordHelp,"https://login.yahoo.com/account/security"),links)
 }
 @Test fun paperclipOffersCloudSharingInsteadOfContactCard(){
  var attached=false
  compose.activity.setContent{OnKlavTheme{MessageAttachments(true,{}, {300000}, {attached=true})}}
  compose.onNodeWithText("Share via Cloud Files").assertExists();compose.onNodeWithText("Contact card").assertDoesNotExist();assertFalse(attached)
 }

}
