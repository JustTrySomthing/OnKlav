package app.onklav.home

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.*
import app.onklav.shared.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.*
import java.io.File
import java.time.Duration

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29],qualifiers="w360dp-h800dp-mdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
class MessagingPolishRegressionTest {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 @get:Rule val emptyKeyStore=EmptyKeyStoreRule()
 @Before fun prepare(){kotlinx.coroutines.runBlocking{AppSdk.bootstrap(compose.activity)};androidx.test.espresso.IdlingPolicies.setMasterPolicyTimeout(10,java.util.concurrent.TimeUnit.SECONDS);compose.mainClock.autoAdvance=false}
 private fun pump(){repeat(4){
  androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
  Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16));compose.mainClock.advanceTimeBy(100)
  compose.runOnUiThread {
   val env=compose.javaClass.getDeclaredField("environment").apply{isAccessible=true}.get(compose)
   val registry=requireNotNull(env.javaClass.superclass).getDeclaredField("composeRootRegistry").apply{isAccessible=true}.get(env)
   val roots=registry.javaClass.getMethod("getRegisteredComposeRoots").invoke(registry) as Set<*>
   roots.filterNotNull().forEach{root->val view=root.javaClass.getMethod("getView").invoke(root) as android.view.View;val decor=view.rootView;decor.forceLayout();decor.measure(android.view.View.MeasureSpec.makeMeasureSpec(360,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(800,android.view.View.MeasureSpec.AT_MOST));decor.layout(0,0,decor.measuredWidth,decor.measuredHeight);root.javaClass.getMethod("measureAndLayoutForTest").invoke(root)}
  }
 }}
 private fun await(test:()->Boolean){compose.waitUntil(15000){pump();test()}}
 private fun picture(name:String){compose.runOnIdle{val env=compose.javaClass.getDeclaredField("environment").apply{isAccessible=true}.get(compose);val registry=requireNotNull(env.javaClass.superclass).getDeclaredField("composeRootRegistry").apply{isAccessible=true}.get(env);val roots=registry.javaClass.getMethod("getRegisteredComposeRoots").invoke(registry) as Set<*>;val main=compose.activity.window.decorView;val v=roots.filterNotNull().map{it.javaClass.getMethod("getView").invoke(it) as android.view.View}.map{it.rootView}.lastOrNull{it!==main}?:main;val b=android.graphics.Bitmap.createBitmap(v.width,v.height,android.graphics.Bitmap.Config.ARGB_8888);v.draw(android.graphics.Canvas(b));val f=File(System.getProperty("onklav.project.root"),"docs/evidence/messaging-polish/$name.png");f.parentFile!!.mkdirs();f.outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
 @Test(timeout=60000)fun bulkActionsWrapAtLargeTypeAndRemainUsable(){
  var count by mutableIntStateOf(0);var moved="";var blocked=false
  compose.setContent{OnKlavTheme{CompositionLocalProvider(LocalDensity provides Density(1f,1.5f)){Column(Modifier.width(360.dp)){
   BulkConversationActions(count,"Inbox",move={moved=it},block={blocked=it},done={count=0})
  }}}}
  pump();compose.onNodeWithText("Select conversations").assertIsDisplayed();compose.onNodeWithText("Archive").assertIsNotEnabled()
  compose.runOnIdle{count=2};pump();compose.onNodeWithText("Archive").assertIsDisplayed().performClick();compose.runOnIdle{assertEquals("Archive",moved)}
  compose.onNodeWithText("Trash").assertIsDisplayed();compose.onNodeWithText("Block").assertIsDisplayed().performClick()
  pump();compose.onNodeWithText("Block selected senders?").assertExists();compose.onAllNodesWithText("Block").onLast().performClick()
  pump();compose.runOnIdle{assertTrue(blocked)};picture("bulk-large-type")
 }
 @Test(timeout=60000)fun converterCategoryNamesFitAndSelectTemperature(){
  compose.setContent{OnKlavTheme{SdkAppScreen("app.onklav.convert",exit={})}}
  await{runCatching{compose.onNodeWithTag("sdk-convert.category").assertIsEnabled()}.isSuccess}
  compose.onNodeWithTag("sdk-convert.category").performClick();pump()
  compose.onNodeWithText("Temperature").assertIsDisplayed();picture("convert-measurement")
  compose.onNodeWithText("Temperature").performClick();await{compose.onAllNodesWithText("Measurement").fetchSemanticsNodes().isEmpty()}
 }
 @Test(timeout=60000)fun browserMenuButtonsHaveGapsAndLongTextKeepsItsWidth(){
  compose.setContent{OnKlavTheme{SdkAppScreen("app.onklav.browser",exit={})}}
  await{runCatching{compose.onNodeWithTag("sdk-address").assertExists()}.isSuccess}
  compose.onNodeWithContentDescription("Browser Settings").performClick()
  await{compose.onAllNodesWithTag("dialog-Browser Settings").fetchSemanticsNodes().isNotEmpty()}
  val share=compose.onNodeWithText("Share Page").fetchSemanticsNode().boundsInRoot
  val open=compose.onNodeWithText("Open in Browser").fetchSemanticsNode().boundsInRoot
  assertTrue(open.top>share.bottom);picture("browser-settings")
  compose.onNodeWithText("Cancel").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick){it()}
  await{compose.onAllNodesWithTag("dialog-Browser Settings").fetchSemanticsNodes().isEmpty()}
  compose.onNodeWithTag("sdk-tabs").performClick();await{compose.onAllNodesWithTag("dialog-Tabs").fetchSemanticsNodes().isNotEmpty()};picture("browser-tabs")
 }
}
