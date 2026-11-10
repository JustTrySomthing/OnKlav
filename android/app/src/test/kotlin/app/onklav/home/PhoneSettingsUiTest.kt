package app.onklav.home
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import app.onklav.shared.OnKlavTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
@RunWith(RobolectricTestRunner::class) @Config(sdk=[29],qualifiers="w432dp-h936dp-mdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhoneSettingsUiTest {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 private fun pump(){repeat(4){androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeBy(100);org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(16));compose.runOnUiThread{val env=compose.javaClass.getDeclaredField("environment").apply{isAccessible=true}.get(compose);val registry=env.javaClass.superclass!!.getDeclaredField("composeRootRegistry").apply{isAccessible=true}.get(env);val roots=registry.javaClass.getMethod("getRegisteredComposeRoots").invoke(registry) as Set<*>;roots.filterNotNull().forEach{root->val view=root.javaClass.getMethod("getView").invoke(root) as android.view.View;val d=view.rootView;d.forceLayout();d.measure(android.view.View.MeasureSpec.makeMeasureSpec(compose.activity.resources.displayMetrics.widthPixels,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(compose.activity.resources.displayMetrics.heightPixels,android.view.View.MeasureSpec.AT_MOST));d.layout(0,0,d.measuredWidth,d.measuredHeight);root.javaClass.getMethod("measureAndLayoutForTest").invoke(root)}}}}

 @Test(timeout=45000) fun settingsStayInternalAndVoicemailCanBeSaved(){
  val app=ApplicationProvider.getApplicationContext<Application>();app.getSharedPreferences("phone-ui",0).edit().clear().commit()
  compose.mainClock.autoAdvance=false
  compose.activity.setContent{OnKlavTheme{PhoneSettingsDialog(PhoneModel(app),true,true,{},{},{},{})}};pump()
  compose.onNodeWithText("Keypad sounds").assertIsDisplayed()
  compose.runOnUiThread{val env=compose.javaClass.getDeclaredField("environment").apply{isAccessible=true}.get(compose);val registry=env.javaClass.superclass!!.getDeclaredField("composeRootRegistry").apply{isAccessible=true}.get(env);val roots=registry.javaClass.getMethod("getRegisteredComposeRoots").invoke(registry) as Set<*>;val view=roots.filterNotNull().map{it.javaClass.getMethod("getView").invoke(it) as android.view.View}.map{it.rootView}.last{it.width>0&&it.height>0};val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888);view.draw(android.graphics.Canvas(bitmap));java.io.File(System.getProperty("onklav.project.root"),"docs/evidence/phone/settings-internal.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}

  compose.onNodeWithText("Voicemail",substring=false).performSemanticsAction(SemanticsActions.OnClick){it()};pump()
  compose.onNodeWithText("Voicemail number",substring=false).performTextInput("123");pump()
  compose.onNodeWithText("Save voicemail number").performSemanticsAction(SemanticsActions.OnClick){it()};pump()
  assertEquals("123",PhoneLocalSettings.voicemail(app,null));compose.onNodeWithText("Saved",substring=false).assertIsDisplayed()
  compose.onNodeWithText("Back",substring=false).performSemanticsAction(SemanticsActions.OnClick){it()};pump()
  compose.onNodeWithText("Blocked numbers",substring=false).performSemanticsAction(SemanticsActions.OnClick){it()};pump()
  compose.onNodeWithText("Blocked numbers are unavailable. Review Phone setup.").assertExists()
  assertNull(org.robolectric.Shadows.shadowOf(app).nextStartedActivity)
  app.getSharedPreferences("phone-ui",0).edit().clear().commit()
 }
}
