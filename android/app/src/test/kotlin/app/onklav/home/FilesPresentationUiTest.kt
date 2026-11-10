package app.onklav.home
import androidx.activity.compose.setContent
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.onklav.shared.OnKlavTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
@RunWith(RobolectricTestRunner::class) @Config(sdk=[29],qualifiers="w432dp-h936dp-mdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class FilesPresentationUiTest{
 @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private fun roots():List<Any>{
        val env=compose.javaClass.getDeclaredField("environment").apply{isAccessible=true}.get(compose)
        val registry=requireNotNull(env.javaClass.superclass).getDeclaredField("composeRootRegistry").apply{isAccessible=true}.get(env)
        return (registry.javaClass.getMethod("getRegisteredComposeRoots").invoke(registry) as Set<*>).filterNotNull()
    }
    // Do not combine a paused Android vsync clock with Compose's waitForNextChoreographerFrame.
    // Drive bounded frames and inspect the real native screen semantics directly instead.
    private fun pump(){repeat(4){
        Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeBy(32)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(16))
        compose.runOnUiThread{roots().forEach{root->val view=root.javaClass.getMethod("getView").invoke(root) as android.view.View
            val decor=view.rootView;decor.forceLayout();decor.measure(android.view.View.MeasureSpec.makeMeasureSpec(432,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(936,android.view.View.MeasureSpec.AT_MOST));decor.layout(0,0,decor.measuredWidth,decor.measuredHeight);root.javaClass.getMethod("measureAndLayoutForTest").invoke(root)}}
    }}
    private fun nodes():List<SemanticsNode>{val all=mutableListOf<SemanticsNode>();compose.runOnUiThread{
        fun walk(n:SemanticsNode){all.add(n);n.children.forEach(::walk)}
        roots().forEach{r->val owner=r.javaClass.getMethod("getSemanticsOwner").invoke(r) as SemanticsOwner;walk(owner.unmergedRootSemanticsNode)}
    };return all}
    private fun text(n:SemanticsNode,value:String)=n.config.getOrNull(SemanticsProperties.Text)?.any{it.text==value}==true
    private fun desc(n:SemanticsNode,value:String)=n.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(value)==true
    private fun waitFor(block:()->Boolean){val end=System.nanoTime()+15_000_000_000L;while(System.nanoTime()<end){pump();if(block())return;Thread.sleep(10)};error("Screen condition timed out: "+nodes().joinToString{"${it.config}"})}
    private fun click(value:String,description:Boolean=false){
        fun ready(n:SemanticsNode):Boolean{var node=n;while(!node.config.contains(SemanticsActions.OnClick))node=node.parent?:return false;return !node.config.contains(SemanticsProperties.Disabled)}
        waitFor{nodes().any{(if(description)desc(it,value)else text(it,value))&&ready(it)}}
        compose.runOnUiThread{var node=nodes().last{(if(description)desc(it,value)else text(it,value))&&ready(it)};while(!node.config.contains(SemanticsActions.OnClick))node=requireNotNull(node.parent);assertTrue(requireNotNull(node.config[SemanticsActions.OnClick].action).invoke())};pump()
    }
    private fun capture(name:String){compose.runOnUiThread{val v=compose.activity.window.decorView;val bitmap=android.graphics.Bitmap.createBitmap(v.width,v.height,android.graphics.Bitmap.Config.ARGB_8888);v.draw(android.graphics.Canvas(bitmap));val f=java.io.File(requireNotNull(System.getProperty("onklav.project.root")),"docs/evidence/files-simplification/$name.png");f.parentFile!!.mkdirs();f.outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}}


 @Test(timeout=60000) fun thumbnailAndListViewsKeepPencilsOnlyOnEditableItems(){
  val gridState=androidx.compose.runtime.mutableStateOf(true)
  var edited="";var opened=""
  val app=compose.activity.application;runBlocking{AppSdk.bootstrap(app)}
  val picture=android.graphics.Bitmap.createBitmap(480,320,android.graphics.Bitmap.Config.ARGB_8888);val canvas=android.graphics.Canvas(picture);canvas.drawColor(android.graphics.Color.rgb(70,150,210));val paint=android.graphics.Paint().apply{color=android.graphics.Color.rgb(60,135,75)};canvas.drawRect(0f,200f,480f,320f,paint);paint.color=android.graphics.Color.rgb(255,210,80);canvas.drawCircle(370f,70f,35f,paint)
  java.io.File(app.filesDir,"content-import.tmp").outputStream().use{picture.compress(android.graphics.Bitmap.CompressFormat.JPEG,90,it)};picture.recycle()
  val imageId=(AppSdk.sync(app,org.json.JSONObject().put("action","message_import").put("name","Holiday photo.jpg").put("mime","image/jpeg")) as org.json.JSONObject).getString("id")
  val rows=listOf(org.json.JSONObject().put("id",imageId).put("name","Holiday photo.jpg").put("mime","image/jpeg").put("bytes",212000),org.json.JSONObject().put("id","system").put("name","OnKlave Files").put("folder",true).put("protected",true).put("bytes",0))
  org.robolectric.shadows.ShadowChoreographer.setPaused(true);compose.mainClock.autoAdvance=false
  compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{androidx.compose.material3.Surface{androidx.compose.foundation.layout.Column{FilesViewButton(gridState.value){gridState.value=!gridState.value};FilesRows(rows,gridState.value,false,open={opened=it.getString("id")},edit={edited=it.getString("id")})}}}}}
  waitFor{nodes().any{text(it,"Holiday photo.jpg")}};waitFor{nodes().any{desc(it,"Holiday photo.jpg")}};assertFalse(nodes().any{desc(it,"Options for OnKlave Files")});assertTrue(nodes().any{text(it,"212KB")});assertFalse(nodes().any{text(it,"0B")});capture("thumbnails")
  click("Options for Holiday photo.jpg",true);assertEquals(imageId,edited);assertEquals("",opened)
  click("List view",true);assertFalse(gridState.value);assertTrue(nodes().any{desc(it,"Thumbnail view")});assertTrue(nodes().any{text(it,"212KB · image/jpeg")});assertFalse(nodes().any{text(it,"0B")});capture("list")
  click("Holiday photo.jpg");assertEquals(imageId,opened)
 }
 @Test(timeout=60000) fun imageAttachmentPickerStartsInImagesWithViewSwitch(){
  val app=compose.activity.application;runBlocking{AppSdk.bootstrap(app)}
  org.robolectric.shadows.ShadowChoreographer.setPaused(true);compose.mainClock.autoAdvance=false
  compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{FileBrowserPane(android.content.Intent().putExtra("mimes",arrayOf("image/*"))){}}}}
  waitFor{nodes().any{text(it,"Images")}};assertTrue(nodes().any{desc(it,"List view")||desc(it,"Thumbnail view")});capture("image-picker")
  click("Android");waitFor{nodes().any{text(it,"Internal files")}};assertFalse(nodes().any{text(it,"Trash")||text(it,"Photos")||text(it,"APK")});click("OnKlav");waitFor{nodes().any{text(it,"Documents")}}
 }

 @Test(timeout=90000) fun filesHomeAndAndroidCategoriesUseTheSameModernBrowser(){
  runBlocking{AppSdk.bootstrap(compose.activity.application)}
  org.robolectric.Shadows.shadowOf(compose.activity.application).grantPermissions(android.Manifest.permission.READ_EXTERNAL_STORAGE,android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
  org.robolectric.shadows.ShadowChoreographer.setPaused(true);compose.mainClock.autoAdvance=false
  compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{SdkAppScreen("app.onklav.files"){}}}}
  waitFor{nodes().any{text(it,"OnKlav")}&&nodes().any{text(it,"Android")}};assertFalse(nodes().any{text(it,"OnKlave Files")||text(it,"OnKlav System Files")});capture("files-home")
  click("Android");waitFor{nodes().any{text(it,"Internal files")}&&nodes().any{text(it,"Trash")}}
  for(label in listOf("Images","Audio","Video","Documents","Downloads","Trash","Internal files"))assertTrue(nodes().any{text(it,label)})
  assertFalse(nodes().any{text(it,"Connect Android Folder")||text(it,"Browse Android Files")});capture("android-categories")
  click("Trash");waitFor{nodes().any{text(it,"Restore all")}&&nodes().any{text(it,"Empty trash")}}
  click("Restore all");val model=androidx.lifecycle.ViewModelProvider(compose.activity).get("sdk-app.onklav.files",SdkAppModel::class.java);waitFor{!model.busy&&model.pending==null};assertNull(model.error)
  click("Empty trash");waitFor{nodes().any{text(it,"Empty Android Trash?")}};click("Cancel")
  click("OnKlav");waitFor{nodes().any{text(it,"Audio")}&&!nodes().any{text(it,"Internal files")}}
  click(if(nodes().any{desc(it,"List view")})"List view"else"Thumbnail view",true)
 }
 @Test(timeout=90000) fun readableAndroidAudioCanBeChosenAsAFolderButNotAsASaveDestination(){
  val app=compose.activity.application;runBlocking{AppSdk.bootstrap(app)}
  org.robolectric.Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.READ_EXTERNAL_STORAGE,android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
  org.robolectric.shadows.ShadowChoreographer.setPaused(true);compose.mainClock.autoAdvance=false
  var result:android.net.Uri?=null
  compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{FileBrowserPane(android.content.Intent().putExtra("mode","folder")){result=it.firstOrNull()}}}}
  click("Android");waitFor{nodes().any{text(it,"Internal files")}};click("Audio");click("Use folder");waitFor{result!=null}
  assertEquals(listOf("external","android-audio"),FileSelectionStore.paths(FileSelectionStore.read(app,result!!)))
  compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{FileBrowserPane(android.content.Intent().putExtra("mode","save").putExtra("mimes",arrayOf("audio/*"))){}}}}
  click("Android");waitFor{nodes().any{text(it,"Internal files")}};click("Audio");waitFor{nodes().any{text(it,"Save here")}&&nodes().any{text(it,"Audio")}}
  compose.runOnUiThread{var node=nodes().last{text(it,"Save here")};while(!node.config.contains(SemanticsActions.OnClick))node=requireNotNull(node.parent);assertTrue(node.config.contains(SemanticsProperties.Disabled))}
 }

 @Test(timeout=90000) fun androidBackNavigatesParentsBeforeReturningToOnKlav(){
  val app=compose.activity.application;runBlocking{AppSdk.bootstrap(app)}
  org.robolectric.Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.READ_EXTERNAL_STORAGE,android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
  val fixture=java.io.File(AndroidFilesAccess.root(),"Download/Navigation/Child").apply{mkdirs()}
  org.robolectric.shadows.ShadowChoreographer.setPaused(true);compose.mainClock.autoAdvance=false
  try{
   compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{SdkAppScreen("app.onklav.files"){}}}}
   val model=androidx.lifecycle.ViewModelProvider(compose.activity).get("sdk-app.onklav.files",SdkAppModel::class.java)
   waitFor{nodes().any{text(it,"Documents")}&&!model.busy&&model.pending==null}
   click("Android");waitFor{model.frame.child("state").optString("folder")=="external"&&model.frame.child("state").optBoolean("external_mode")&&!model.busy&&model.pending==null}
   click("Downloads");waitFor{model.frame.child("state").optString("folder")=="android-downloads"&&!model.busy&&model.pending==null}
   click("Navigation");waitFor{nodes().any{text(it,"Child")}}
   click("Parent folder");waitFor{model.frame.child("state").optString("folder")=="android-downloads"&&!model.busy&&model.pending==null}
   click("Parent folder");waitFor{model.frame.child("state").optString("folder")=="external"&&!model.busy&&model.pending==null}
   compose.runOnUiThread{compose.activity.onBackPressedDispatcher.onBackPressed()};waitFor{!model.frame.child("state").optBoolean("external_mode")&&model.frame.child("state").optString("folder").isEmpty()&&!model.busy}
  }finally{fixture.parentFile!!.deleteRecursively()}
 }

 @Test(timeout=60000) fun slideoutCloseUsesXAndDismissesInsteadOfNavigatingToTheParent(){
  runBlocking{AppSdk.bootstrap(compose.activity.application)}
  org.robolectric.shadows.ShadowChoreographer.setPaused(true);compose.mainClock.autoAdvance=false
  var closed=false
  compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{SdkAppScreen("app.onklav.files"){closed=true}}}}
  click("Documents");waitFor{nodes().any{text(it,"Parent folder")}}
  assertFalse(nodes().any{desc(it,"Back")});click("Close",true);waitFor{closed}
 }

}
