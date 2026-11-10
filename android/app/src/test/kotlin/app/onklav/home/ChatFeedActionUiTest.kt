package app.onklav.home
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.onklav.shared.OnKlavTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File
@RunWith(RobolectricTestRunner::class) @Config(sdk=[29],qualifiers="w360dp-h780dp-mdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChatFeedActionUiTest {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 private fun pump(){repeat(4){androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeBy(100);org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(16));compose.runOnUiThread{val env=compose.javaClass.getDeclaredField("environment").apply{isAccessible=true}.get(compose);val registry=env.javaClass.superclass!!.getDeclaredField("composeRootRegistry").apply{isAccessible=true}.get(env);val roots=registry.javaClass.getMethod("getRegisteredComposeRoots").invoke(registry) as Set<*>;roots.filterNotNull().forEach{root->val view=root.javaClass.getMethod("getView").invoke(root) as android.view.View;val d=view.rootView;d.forceLayout();d.measure(android.view.View.MeasureSpec.makeMeasureSpec(compose.activity.resources.displayMetrics.widthPixels,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(compose.activity.resources.displayMetrics.heightPixels,android.view.View.MeasureSpec.AT_MOST));d.layout(0,0,d.measuredWidth,d.measuredHeight);root.javaClass.getMethod("measureAndLayoutForTest").invoke(root)}}}}

 private fun capture(name:String){compose.runOnUiThread{val env=compose.javaClass.getDeclaredField("environment").apply{isAccessible=true}.get(compose);val registry=env.javaClass.superclass!!.getDeclaredField("composeRootRegistry").apply{isAccessible=true}.get(env);val roots=registry.javaClass.getMethod("getRegisteredComposeRoots").invoke(registry) as Set<*>;val view=roots.filterNotNull().map{it.javaClass.getMethod("getView").invoke(it) as android.view.View}.map{it.rootView}.last{it.width>0&&it.height>0};val b=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888);view.draw(android.graphics.Canvas(b));File(System.getProperty("onklav.project.root"),"docs/evidence/chat-latest-arrow/$name.png").apply{parentFile!!.mkdirs()}.outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
 @Test(timeout=45000) fun sharedFeedControlKeepsJumpAndActivitiesActions(){
  var atLatest by mutableStateOf(false);var clicks=0
  compose.mainClock.autoAdvance=false
  compose.activity.setContent{OnKlavTheme{Surface{ChatFeedAction(atLatest){clicks++}}}};pump()
  val arrow=compose.onNodeWithTag("chat-latest-arrow",useUnmergedTree=true).getUnclippedBoundsInRoot()
  assertEquals(32f,arrow.right.value-arrow.left.value,1f)
  compose.onNodeWithContentDescription("Jump to latest message").performClick();pump();assertEquals(1,clicks);capture("bold-arrow")
  compose.runOnUiThread{atLatest=true};pump()
  compose.onNodeWithContentDescription("Jump to latest message").assertDoesNotExist()
  compose.onNodeWithContentDescription("Conversation Activities").performClick();pump();assertEquals(2,clicks)
 }
}
