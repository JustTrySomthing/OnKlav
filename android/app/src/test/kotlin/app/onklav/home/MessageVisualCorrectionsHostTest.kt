package app.onklav.home

import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.onklav.shared.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],qualifiers="w432dp-h936dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class MessageVisualCorrectionsHostTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 private fun pump(){repeat(3){
  compose.mainClock.advanceTimeBy(50)
  org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(16))
  compose.runOnUiThread {
   val env=compose.javaClass.getDeclaredField("environment").apply{isAccessible=true}.get(compose)
   val registry=requireNotNull(env.javaClass.superclass).getDeclaredField("composeRootRegistry").apply{isAccessible=true}.get(env)
   val roots=registry.javaClass.getMethod("getRegisteredComposeRoots").invoke(registry) as Set<*>
   roots.filterNotNull().forEach{root->val view=root.javaClass.getMethod("getView").invoke(root) as android.view.View;val decor=view.rootView;decor.forceLayout();decor.measure(android.view.View.MeasureSpec.makeMeasureSpec(432,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(936,android.view.View.MeasureSpec.AT_MOST));decor.layout(0,0,decor.measuredWidth,decor.measuredHeight);root.javaClass.getMethod("measureAndLayoutForTest").invoke(root)}
  }
 }}
 private fun bitmap(name:String):android.graphics.Bitmap {
  lateinit var bitmap:android.graphics.Bitmap
  compose.runOnUiThread{val view=compose.activity.window.decorView;bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888);view.draw(android.graphics.Canvas(bitmap))}
  val file=java.io.File(System.getProperty("onklav.project.root"),"docs/evidence/v0.54.0/$name.png");file.parentFile!!.mkdirs();file.outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
  return bitmap
 }
 @Test(timeout=60000) fun arrowStaysVisibleAtPhoneDensityWhilePortraitPreviewPreservesItsFullHeight(){
  val file=java.io.File(compose.activity.cacheDir,"portrait-test.png")
  val source=android.graphics.Bitmap.createBitmap(160,320,android.graphics.Bitmap.Config.ARGB_8888);val canvas=android.graphics.Canvas(source);val paint=android.graphics.Paint()
  paint.color=android.graphics.Color.RED;canvas.drawRect(0f,0f,160f,100f,paint);paint.color=android.graphics.Color.GREEN;canvas.drawRect(0f,100f,160f,220f,paint);paint.color=android.graphics.Color.BLUE;canvas.drawRect(0f,220f,160f,320f,paint);file.outputStream().use{source.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};source.recycle()
  compose.mainClock.autoAdvance=false
  compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{Column(Modifier.fillMaxSize().background(Color.Black)){
   CompositionLocalProvider(LocalDensity provides Density(3f)){Box(Modifier.size(24.dp).testTag("speech-arrow")){SpeechActivityIcon(Color.White)}}
   MessageThumbnail(MessagePart(android.net.Uri.fromFile(file).toString(),"image/png","Portrait"),Modifier.width(280.dp).testTag("portrait-preview"),naturalHeight=true){}
  }}}}
  compose.waitUntil(10000){pump();compose.onNodeWithTag("portrait-preview").getUnclippedBoundsInRoot().height.value>500f}
  for(phase in 0..7){pump();val bounds=compose.onNodeWithTag("speech-arrow").fetchSemanticsNode().boundsInRoot;val image=bitmap("media-arrow-$phase");var visible=0
   for(y in bounds.top.toInt() until bounds.bottom.toInt())for(x in bounds.left.toInt() until bounds.right.toInt()){val pixel=image.getPixel(x,y);if(android.graphics.Color.red(pixel)>200&&android.graphics.Color.green(pixel)>200&&android.graphics.Color.blue(pixel)>200)visible++}
   var edge=0
   for(x in bounds.left.toInt() until bounds.right.toInt())for(y in listOf(bounds.top.toInt(),bounds.bottom.toInt()-1)){if(android.graphics.Color.red(image.getPixel(x,y))>200)edge++}
   for(y in bounds.top.toInt() until bounds.bottom.toInt())for(x in listOf(bounds.left.toInt(),bounds.right.toInt()-1)){if(android.graphics.Color.red(image.getPixel(x,y))>200)edge++}
   assertEquals("Arrow tip clips at phase $phase",0,edge)
   image.recycle();assertTrue("Arrow disappeared at density 3, phase $phase: $visible pixels",visible>150)
  }
 }
 @Test(timeout=60000) fun setupNextActionAndEmptyTrashLabelsRemainVisibleAndContactsHasOneAddAction(){
  compose.mainClock.autoAdvance=false;var page by mutableStateOf("setup")
  compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{when(page){"setup"->SetupScreen({},{}) ;"trash"->Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)){TrashControls(false,{},{})};else->ContactsScreen({}){}}}}};pump()
  compose.onNodeWithText("Let’s get you messaging").assertExists();compose.onNodeWithText("Set as default SMS app").assertIsDisplayed();bitmap("setup").recycle()
  compose.runOnUiThread{page="trash"};pump();compose.onNodeWithText("Restore all").assertIsDisplayed();compose.onNodeWithText("Empty trash").assertIsDisplayed();bitmap("trash-empty").recycle()
  compose.runOnUiThread{page="contacts"};pump();compose.onAllNodesWithTag("action-Add Contact").assertCountEquals(1);compose.onNodeWithContentDescription("Close").assertExists();compose.onNodeWithTag("action-Contacts").assertDoesNotExist();bitmap("contacts").recycle()
 }
}
