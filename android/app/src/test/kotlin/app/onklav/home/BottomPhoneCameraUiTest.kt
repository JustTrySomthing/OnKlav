package app.onklav.home

import android.Manifest
import android.app.Application
import android.app.role.RoleManager
import android.telecom.TelecomManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29],qualifiers="w360dp-h780dp-mdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
class BottomPhoneCameraUiTest {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 @get:Rule val keys=EmptyKeyStoreRule()
 private val app get()=ApplicationProvider.getApplicationContext<Application>()
 private fun capture(name:String){compose.runOnUiThread{val v=compose.activity.window.decorView;val b=android.graphics.Bitmap.createBitmap(v.width,v.height,android.graphics.Bitmap.Config.ARGB_8888);v.draw(android.graphics.Canvas(b));File(System.getProperty("onklav.project.root"),"docs/evidence/phone-root-navigation/$name.png").apply{parentFile!!.mkdirs()}.outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
 @Test fun phoneIsARootDestinationWithNoBackArrowAndClearCallButtonSpacing(){
  OnKlavPermissions.finish(app);shadowOf(app.getSystemService(TelecomManager::class.java)).setDefaultDialerPackage(app.packageName)
  compose.activity.setContent{OnKlavTheme{OnKlavScreen()}}
  compose.onNodeWithTag("tab-Phone").performClick()
  compose.onNodeWithTag("tab-Phone").assertIsSelected()
  compose.onNodeWithTag("home-navigation").assertIsDisplayed()
  compose.onNodeWithTag("phone-workspace").assertIsDisplayed()
  compose.onNodeWithContentDescription("Back",substring=false).assertDoesNotExist()
  compose.onNodeWithContentDescription("2",substring=false).performClick()
  compose.onNodeWithContentDescription("Phone number: 2").assertIsDisplayed()
  val button=compose.onNodeWithTag("phone-call-button").fetchSemanticsNode().boundsInRoot;val nav=compose.onNodeWithTag("home-navigation").fetchSemanticsNode().boundsInRoot
  assertTrue("Call button needs at least 16dp clearance",nav.top-button.bottom>=15f)
  val number=compose.onNodeWithContentDescription("Phone number: 2").fetchSemanticsNode().boundsInRoot
  val firstKey=compose.onNodeWithContentDescription("2",substring=false).fetchSemanticsNode().boundsInRoot
  val lastKey=compose.onNodeWithContentDescription("#",substring=false).fetchSemanticsNode().boundsInRoot
  assertTrue("Number needs flexible space above the keypad",firstKey.top-number.bottom>=20f)
  assertTrue("Call actions need flexible space below the keypad",button.top-lastKey.bottom>=20f)
  compose.onNodeWithText("Recents",substring=false).assertIsDisplayed();capture("root-phone")
  compose.onNodeWithTag("tab-Apps").performClick()
  compose.onNodeWithTag("tab-Apps").assertIsSelected()
  compose.onNodeWithTag("phone-workspace").assertDoesNotExist()
 }
 @Test fun portraitCameraKeepsTheBottomMenuAndRootHeaderHasNoExitArrow(){
  OnKlavPermissions.finish(app);shadowOf(app).denyPermissions(Manifest.permission.CAMERA);kotlinx.coroutines.runBlocking{AppSdk.bootstrap(app)}
  compose.activity.setContent{OnKlavTheme{OnKlavScreen()}}
  compose.onNodeWithTag("tab-Camera").performClick()
  compose.onNodeWithTag("tab-Camera").assertIsSelected()
  compose.onNodeWithTag("home-navigation").assertIsDisplayed()
  compose.onNodeWithTag("sdk-app-screen").assertIsDisplayed()
  compose.waitUntil(15000){shadowOf(android.os.Looper.getMainLooper()).idle();compose.onAllNodesWithText("Camera",substring=false).fetchSemanticsNodes().size>=2}
  compose.onNodeWithContentDescription("Back",substring=false).assertDoesNotExist()
  compose.onNodeWithContentDescription("Back to previous app",substring=false).assertDoesNotExist();capture("root-camera")
  compose.onNodeWithTag("tab-Messages").performClick()
  compose.onNodeWithTag("tab-Messages").assertIsSelected()
  compose.onNodeWithTag("sdk-app-screen").assertDoesNotExist()
 }
 @Test @Config(qualifiers="w780dp-h360dp-land-mdpi") fun landscapeCameraFillsTheShellAndPortraitRestoresNavigation(){
  OnKlavPermissions.finish(app);shadowOf(app).denyPermissions(Manifest.permission.CAMERA);kotlinx.coroutines.runBlocking{AppSdk.bootstrap(app)}
  val config=mutableStateOf(android.content.res.Configuration(compose.activity.resources.configuration))
  compose.activity.setContent{CompositionLocalProvider(androidx.compose.ui.platform.LocalConfiguration provides config.value){OnKlavTheme{OnKlavScreen()}}}
  compose.onNodeWithTag("tab-Camera").performClick()
  compose.waitUntil(15000){shadowOf(android.os.Looper.getMainLooper()).idle();compose.onAllNodesWithTag("home-navigation").fetchSemanticsNodes().isEmpty()}
  compose.onNodeWithTag("sdk-app-screen").assertIsDisplayed()
  compose.onNodeWithTag("home-navigation").assertDoesNotExist()
  compose.onNodeWithTag("status-bar-backplate").assertDoesNotExist()
  compose.onNodeWithTag("navigation-bar-backplate").assertDoesNotExist()
  val workspace=compose.onNodeWithTag("home-workspace").fetchSemanticsNode().boundsInRoot
  val root=compose.onRoot().fetchSemanticsNode().boundsInRoot
  assertEquals("Landscape preview reaches the bottom",root.bottom,workspace.bottom,1f)
  assertEquals("Landscape preview reaches the left edge",root.left,workspace.left,1f)
  assertEquals("Landscape preview reaches the right edge",root.right,workspace.right,1f)
  compose.runOnIdle{config.value=android.content.res.Configuration(config.value).apply{orientation=android.content.res.Configuration.ORIENTATION_PORTRAIT}}
  compose.onNodeWithTag("home-navigation").assertIsDisplayed()
  compose.onNodeWithTag("tab-Camera").assertIsSelected()
  compose.onNodeWithTag("tab-Apps").performClick()
  compose.onNodeWithTag("tab-Apps").assertIsSelected()
  compose.onNodeWithTag("sdk-app-screen").assertDoesNotExist()
 }
 @Test @Config(qualifiers="w320dp-h568dp-mdpi",fontScale=1.4f) fun smallRootDialerKeepsCallAndTabsAboveTheMainMenu(){
  val m=PhoneModel(app);m.number="2025550123"
  compose.activity.setContent{OnKlavTheme{Column(Modifier.fillMaxSize()){Box(Modifier.weight(1f)){PhoneWorkspace({},m,embeddedInHome=true)};HomeNavigation(5){}}}}
  capture("root-phone-large-text-before")
  compose.onNodeWithContentDescription("Speed dial").assertIsDisplayed()
  compose.onNodeWithContentDescription("Voicemail",substring=false).assertIsDisplayed()
  compose.onNodeWithContentDescription("Paste phone number").assertDoesNotExist()
  compose.onNodeWithText("Hold 1 for voicemail",substring=true).assertDoesNotExist()
  compose.onNodeWithContentDescription("2",substring=false).performClick()
  compose.onNodeWithContentDescription("Delete digit; hold to clear").performClick()
  compose.onNodeWithContentDescription("Phone number: 2025550123").assertIsDisplayed()
  compose.onNodeWithText("Contacts",substring=false).assertIsDisplayed()
  compose.onNodeWithContentDescription("Call",substring=false).assertIsDisplayed()
  compose.onNodeWithContentDescription("#",substring=false).assertIsDisplayed()
  compose.onNodeWithContentDescription("Back",substring=false).assertDoesNotExist();capture("root-phone-large-text")
 }
 @Test fun defaultPhoneActionRequestsTheAndroidDialerRole(){
  shadowOf(app.getSystemService(RoleManager::class.java)).addAvailableRole(RoleManager.ROLE_DIALER);shadowOf(app.getSystemService(TelecomManager::class.java)).setDefaultDialerPackage("other.phone")
  compose.activity.setContent{OnKlavTheme{PhoneWorkspace({},PhoneModel(app),embeddedInHome=true)}}
  compose.onNodeWithText("Use OnKlav",substring=false).performClick()
  val launched=shadowOf(compose.activity).nextStartedActivityForResult
  assertEquals("android.app.role.action.REQUEST_ROLE",launched.intent.action);assertEquals(RoleManager.ROLE_DIALER,launched.intent.getStringExtra("android.intent.extra.ROLE_NAME"))
 }
}
