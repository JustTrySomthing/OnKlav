package app.onklav.home

import androidx.activity.ComponentActivity
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
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 @Test @Config(qualifiers="w320dp-h568dp-mdpi",fontScale=1.4f) fun threeChoicesRemainReadableAndOpenTheCorrectProvider(){
  val selected=mutableListOf<String>();compose.activity.setContent{OnKlavTheme{Column{MailSetupChoices{selected.add(it)}}}}
  compose.onNodeWithText("GMX").assertDoesNotExist();compose.onNodeWithText("Private domain").assertIsDisplayed().performClick();compose.onNodeWithText("Yahoo").assertIsDisplayed().performClick();compose.onNodeWithText("Google").assertIsDisplayed().performClick();assertEquals(listOf("Other","Yahoo","Gmail"),selected)
  listOf("Gmail","Outlook","iCloud","Mail.com").forEach{compose.onNodeWithText(it).assertDoesNotExist()}
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
