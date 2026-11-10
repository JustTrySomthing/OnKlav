package app.onklav.home

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.*
import app.onklav.shared.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*
import java.io.File

/** Desktop-only visual feedback. No guest/session launch, service effects or OS results. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],qualifiers="w432dp-h936dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class SdkNativePreviewTest{
 @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()
 private fun roots():List<android.view.View>{
  val env=compose.javaClass.getDeclaredField("environment").apply{isAccessible=true}.get(compose)
  val registry=env.javaClass.superclass!!.getDeclaredField("composeRootRegistry").apply{isAccessible=true}.get(env)
  return (registry.javaClass.getMethod("getRegisteredComposeRoots").invoke(registry)as Set<*>).filterNotNull().map{it.javaClass.getMethod("getView").invoke(it)as android.view.View}.map{it.rootView}.distinct()
 }
 private fun pump(width:Int,height:Int){repeat(4){
  androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeBy(100);shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(16))
  compose.runOnUiThread{roots().forEach{view->view.forceLayout();view.measure(android.view.View.MeasureSpec.makeMeasureSpec(width,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(height,android.view.View.MeasureSpec.EXACTLY));view.layout(0,0,width,height)}}
 }}
 private fun nodes(frame:JSONObject,key:String)=frame.optJSONArray(key)?.let{a->List(a.length()){a.getJSONObject(it)}}?:emptyList()
 private val deviceWidgets=setOf("pdf_viewer","map_viewer","web","canvas","image_record","media_record","speech","speech_settings","voice_capture","voice_clip")
 private fun render(frame:JSONObject,width:Int,height:Int,scale:Float,dark:Boolean,palette:Int,onDrawerClose:()->Unit={}){
  compose.runOnUiThread{compose.activity.setContent{OnKlavTheme(preview=AppearanceSpec(dark=dark,palette=palette)){CompositionLocalProvider(LocalDensity provides Density(1f,scale)){
   val model=remember{SdkAppModel(compose.activity.application as Application)};val pool=remember{SdkWebPool()};val speech=remember{SdkSpeechController(compose.activity,"preview",authorize={error("Preview cannot authorize speech")})}
   DisposableEffect(speech){onDispose{speech.close()}}
   val widget:@Composable (JSONObject,Modifier)->Unit={node,modifier->if(node.optString("kind")in deviceWidgets)Text("Device component: "+node.optString("text").ifBlank{node.optString("kind")},modifier.fillMaxWidth().padding(12.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)else SdkWidget(node,"preview",model,speech,pool,modifier)}
   Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background){Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)){
    SdkFrameHeader(frame,false,onBack={},onAction={}){CompactAction(Icons.Outlined.MoreVert,"App controls"){} }
    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()){
     val columns=(maxWidth.value/(160f*scale.coerceIn(1f,1.5f))).toInt().coerceIn(1,3)
     val body=nodes(frame,"body");val compact=maxHeight<360.dp&&body.any{it.optString("kind")=="editor"};val scroll=frame.optBoolean("scroll_body")||compact||body.none{it.optString("kind")in setOf("editor","web","canvas","pdf_viewer","map_viewer")}
     Column(Modifier.fillMaxSize().then(if(frame.optBoolean("full_bleed"))Modifier else Modifier.padding(horizontal=12.dp,vertical=8.dp)).then(if(scroll)Modifier.verticalScroll(rememberScrollState())else Modifier)){
      SdkBodyNodes(body,columns,compact,frame.optBoolean("scroll_body"),widget)
     }
     if(frame.optBoolean("drawer_open"))Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=.4f)),contentAlignment=Alignment.TopEnd){SdkDrawerContent(frame,rememberScrollState(),onClose=onDrawerClose){node->widget(node,Modifier)}}
    }
   }}
   if(frame.optBoolean("menu_open"))ReferenceDialog(frame.optString("menu_title",frame.optString("title")),{},footer={DialogAction("Cancel"){} }){Column(Modifier.padding(16.dp)){nodes(frame,"menu").forEach{widget(it,Modifier)}}}
   frame.optJSONObject("confirm")?.let{confirm->ReferenceDialog(confirm.optString("title"),{},footer={DialogAction("Cancel"){};DialogAction(confirm.optString("accept_label","Continue")){} }){Text(confirm.optString("text"),Modifier.padding(20.dp))}}
  }}}};pump(width,height)
 }
 @Test(timeout=60000)fun renderRequestedFrameOrCheckNativeFixture(){
  val input=System.getenv("ONKLAV_PREVIEW_INPUT");val out=System.getenv("ONKLAV_PREVIEW_OUTPUT")
  val request=if(input!=null){val file=File(input);require(file.length()<=1_100_000);JSONObject(file.readText())}else JSONObject("""{"frame":{"title":"Native preview","body":[{"kind":"field","id":"name","hint":"Task name","value":"Example"},{"kind":"button","id":"save","text":"Save"},{"kind":"web","id":"web","url":"https://example.org"}]},"width":320,"height":640,"font_scale":2,"dark":false,"palette":8}""")
  val width=request.getInt("width");val height=request.getInt("height");val scale=request.getDouble("font_scale").toFloat();require(width in 280..1200&&height in 320..1600&&scale in .8f..2f)
  render(request.getJSONObject("frame"),width,height,scale,request.getBoolean("dark"),request.getInt("palette"))
  if(input==null){compose.onNodeWithText("Native preview").assertIsDisplayed();compose.onNodeWithTag("sdk-name").assertIsDisplayed();compose.onNodeWithText("Device component: web").assertIsDisplayed();assertFalse(File(compose.activity.filesDir,"app-sdk/registry.json").exists())}
  if(out!=null)compose.runOnUiThread{val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);try{val canvas=Canvas(bitmap);roots().forEachIndexed{index,view->if(index>0)canvas.drawColor(0x77000000);view.draw(canvas)};File(out).outputStream().use{check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))}}finally{bitmap.recycle()}}
 }
 @Test(timeout=30000)fun drawerUsesSharedLayoutAtNarrowWidthAndLargeText(){
  var closed=false
  val frame=JSONObject("""{"title":"Preview","drawer_open":true,"drawer_title":"Saved items","drawer_close":"close.drawer","drawer":[{"kind":"button","id":"close.drawer","text":"Duplicate close"},{"kind":"label","id":"entry","text":"Saved example"}]}""")
  render(frame,320,640,2f,false,8){closed=true}
  val bounds=compose.onNodeWithTag("sdk-app-drawer").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
  assertTrue(bounds.left>0&&bounds.right<=320);compose.onNodeWithText("Saved example").assertIsDisplayed();compose.onNodeWithText("Duplicate close").assertDoesNotExist()
  compose.onNodeWithContentDescription("Close Saved items").performClick();assertTrue(closed)
 }
 @Test(timeout=30000)fun previewHeaderRetainsProductionCounterAndDisabledActions(){
  val frame=JSONObject("""{"title":"Preview","counter":"2 / 8","actions":[{"id":"refresh","text":"Refresh","icon":"refresh","enabled":false,"long_action":"all","long_label":"Refresh all"}],"body":[{"kind":"label","id":"body","text":"Example"}]}""")
  render(frame,432,936,1f,true,3)
  compose.onNodeWithText("2 / 8").assertIsDisplayed();compose.onNodeWithContentDescription("Refresh").assertIsNotEnabled().assertHasClickAction();compose.onNodeWithContentDescription("App controls").assertIsDisplayed()
 }

}
