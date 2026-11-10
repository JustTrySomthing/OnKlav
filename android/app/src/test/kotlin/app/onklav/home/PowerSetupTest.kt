package app.onklav.home

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import app.onklav.shared.OnKlavTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w360dp-h720dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PowerSetupTest {
 @get:Rule val emptyKeyStore=EmptyKeyStoreRule()
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 private val c get()=ApplicationProvider.getApplicationContext<Context>()
 private fun capture(name:String){compose.waitForIdle();compose.runOnUiThread{val v=compose.activity.window.decorView;val b=android.graphics.Bitmap.createBitmap(v.width,v.height,android.graphics.Bitmap.Config.ARGB_8888);v.draw(android.graphics.Canvas(b));java.io.File("/workspace/scratch/c739b0c1372b",name).outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
 @Test fun powerIsARegisteredMinimalAccessibilityServiceAndRevocationIsObserved(){
  val info=c.packageManager.getServiceInfo(ComponentName(c,PowerAccessibilityService::class.java),PackageManager.GET_META_DATA)
  assertEquals("android.permission.BIND_ACCESSIBILITY_SERVICE",info.permission);assertTrue(info.exported);assertNotNull(info.metaData)
  assertFalse(PowerAccessibilityService.openMenu(c))
  val service=AccessibilityServiceInfo().apply{org.robolectric.util.ReflectionHelpers.setField(this,"mResolveInfo",android.content.pm.ResolveInfo().apply{serviceInfo=info})}
  val manager=shadowOf(c.getSystemService(AccessibilityManager::class.java))
  manager.setEnabledAccessibilityServiceList(listOf(service));assertTrue(PowerAccessibilityService.enabled(c))
  assertTrue(platformSetupSteps(c).single{it.id=="power"}.complete)
  val controller=org.robolectric.Robolectric.buildService(PowerAccessibilityService::class.java).create()
  val connected=controller.get();org.robolectric.util.ReflectionHelpers.callInstanceMethod<Void>(connected,"onServiceConnected")
  assertTrue(PowerAccessibilityService.openMenu(c))
  assertEquals(listOf(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_POWER_DIALOG),shadowOf(connected).globalActionsPerformed)
  controller.destroy();assertFalse(PowerAccessibilityService.openMenu(c))
  manager.setEnabledAccessibilityServiceList(emptyList());assertFalse(PowerAccessibilityService.enabled(c));assertFalse(PowerAccessibilityService.openMenu(c))
 }
 @Test fun incompleteSetupShowsNumberedStepsProgressNextActionAndExplicitExit(){
  var closed=false
  compose.activity.setContent{OnKlavTheme{PlatformSetupScreen({closed=true},{})}}
  capture("power-setup-portrait.png")
  compose.onNodeWithTag("setup-progress").assertIsDisplayed()
  compose.onNodeWithTag("setup-progress").assertTextContains("remaining",substring=true)
  compose.onNodeWithText("Step ",substring=true).assertIsDisplayed()
  compose.onNodeWithText("Finish later",substring=true).performClick();assertTrue(closed)
  assertTrue(OnKlavPermissions.seen(c));assertTrue(platformSetupSteps(c).any{!it.complete})
 }
 @Test @Config(qualifiers="w640dp-h360dp-mdpi") fun landscapeKeepsTheNextActionAndExitVisible(){
  compose.activity.setContent{OnKlavTheme{PlatformSetupScreen({},{})}}
  compose.onNodeWithText("Step ",substring=true).assertIsDisplayed()
  compose.onNodeWithText("Finish later",substring=true).assertIsDisplayed()
  capture("power-setup-landscape.png")
  compose.onNodeWithTag("setup-step-power").performScrollTo();compose.onNodeWithText("Enable Power").assertIsDisplayed()
 }
 @Test fun powerDispatchHasNoWorkspaceAndClosesImmediately(){
  var closed=false
  compose.activity.setContent{OnKlavTheme{PowerWorkspace({closed=true})}}
  compose.waitForIdle();assertTrue(closed);compose.onNodeWithText("Power menu").assertDoesNotExist();compose.onNodeWithText("Enable Power").assertDoesNotExist()
 }
 @Test fun directionsAndSkippingAreAvailableWithoutMarkingTheStepComplete(){
  compose.activity.setContent{OnKlavTheme{PlatformSetupScreen({},{})}}
  compose.onNodeWithTag("setup-step-power").performScrollTo();compose.onNodeWithText("Directions").performClick();compose.onNodeWithText("Installed apps",substring=true).assertExists();compose.onNodeWithText("Got it").performClick()
  compose.onAllNodesWithText("Skip this step for now").onLast().performScrollTo().performClick();compose.onNodeWithText("Skipped for now").assertExists();assertFalse(platformSetupSteps(c).single{it.id=="power"}.complete)
  compose.onNodeWithText("Finish later",substring=true).assertIsDisplayed()
 }
 @Test fun desktopPowerInvokesOnlyTheSystemActionAndLeavesAppsVisible(){
  kotlinx.coroutines.runBlocking{AppSdk.bootstrap(c)};OnKlavPermissions.finish(c)
  val info=c.packageManager.getServiceInfo(ComponentName(c,PowerAccessibilityService::class.java),PackageManager.GET_META_DATA)
  val enabled=AccessibilityServiceInfo().apply{org.robolectric.util.ReflectionHelpers.setField(this,"mResolveInfo",android.content.pm.ResolveInfo().apply{serviceInfo=info})}
  shadowOf(c.getSystemService(AccessibilityManager::class.java)).setEnabledAccessibilityServiceList(listOf(enabled))
  val controller=org.robolectric.Robolectric.buildService(PowerAccessibilityService::class.java).create();val service=controller.get();org.robolectric.util.ReflectionHelpers.callInstanceMethod<Void>(service,"onServiceConnected")
  try{compose.activity.setContent{OnKlavTheme{OnKlavScreen()}};compose.onNodeWithTag("app-icon-power").performClick();compose.waitForIdle();compose.onNodeWithTag("app-icon-power").assertIsDisplayed();compose.onNodeWithText("Power menu").assertDoesNotExist();assertEquals(listOf(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_POWER_DIALOG),shadowOf(service).globalActionsPerformed)}finally{controller.destroy()}
 }

}
