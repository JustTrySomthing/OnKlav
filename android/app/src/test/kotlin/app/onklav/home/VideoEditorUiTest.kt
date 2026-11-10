package app.onklav.home

import androidx.activity.compose.setContent
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.onklav.shared.OnKlavTheme
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],qualifiers="w432dp-h936dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class VideoEditorUiTest{
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
    private fun click(value:String,description:Boolean=false){waitFor{nodes().any{if(description)desc(it,value)else text(it,value)}}
        repeat(6){
            val n=nodes().last{if(description)desc(it,value)else text(it,value)}
            if(n.positionInRoot.y<0||n.positionInRoot.y+n.size.height>936){
                compose.runOnUiThread{var ancestor=n.parent;while(ancestor!=null&&!ancestor.config.contains(SemanticsActions.ScrollBy))ancestor=ancestor.parent;ancestor?.config?.getOrNull(SemanticsActions.ScrollBy)?.action?.invoke(0f,if(n.positionInRoot.y<0)-180f else 180f)};pump()
            } else if(n.positionInRoot.x<0||n.positionInRoot.x+n.size.width>432){
                compose.runOnUiThread{var ancestor=n.parent;while(ancestor!=null&&!ancestor.config.contains(SemanticsActions.ScrollBy))ancestor=ancestor.parent;ancestor?.config?.getOrNull(SemanticsActions.ScrollBy)?.action?.invoke(if(n.positionInRoot.x<0)-220f else 220f,0f)};pump()
            }
        }
        compose.runOnUiThread{var node=nodes().last{if(description)desc(it,value)else text(it,value)};while(!node.config.contains(SemanticsActions.OnClick))node=requireNotNull(node.parent);assertFalse(node.config.contains(SemanticsProperties.Disabled));assertTrue(requireNotNull(node.config[SemanticsActions.OnClick].action).invoke())};pump()}
    private fun openTool(name:String){
        if(name=="Performance"){click("More video options",true);click("Preview & proxies");return}
        val group=videoToolGroups.first{ name in it.tools }
        if(!nodes().any{desc(it,"Open ${videoToolLabel(name)}")})click("${group.name} tools",true)
        click("Open ${videoToolLabel(name)}",true)
    }
    private fun capture(name:String){compose.runOnUiThread{val v=compose.activity.window.decorView;val bitmap=android.graphics.Bitmap.createBitmap(v.width,v.height,android.graphics.Bitmap.Config.ARGB_8888);v.draw(android.graphics.Canvas(bitmap));val f=java.io.File(requireNotNull(System.getProperty("onklav.project.root")),"docs/evidence/video-pro/$name.png");f.parentFile!!.mkdirs();f.outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}}
    @Test(timeout=120000) fun timelineTextCanvasAndProjectLibraryAreReachable(){
        val c=compose.activity.application
        runBlocking{AppSdk.bootstrap(c)}
        val store=VideoStore(c);val clip=VideoClip(file="${videoId()}.media",name="Sunset.jpg",sourceMs=5000,image=true,hasAudio=false);val p=VideoProject(name="Weekend story",clips=listOf(clip,clip.copy(id=videoId(),name="Ending.jpg")))
        val b=android.graphics.Bitmap.createBitmap(320,180,android.graphics.Bitmap.Config.ARGB_8888);b.eraseColor(android.graphics.Color.rgb(30,90,150));java.io.File(store.folder(p.id),clip.file).outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.JPEG,90,it)};b.recycle();store.save(p)
        org.robolectric.shadows.ShadowChoreographer.setPaused(true);compose.mainClock.autoAdvance=false
        compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{VideoEditorWorkspace(automaticPreview=false)}}}
        waitFor{nodes().any{desc(it,"Edit tools")}};assertTrue(nodes().first{desc(it,"Video preview")}.boundsInRoot.height>=200);capture("video-editor-timeline")
        val editor=androidx.lifecycle.ViewModelProvider(compose.activity)["video-editor-${AppSdk.epoch.value}",VideoEditorModel::class.java]
        val beforeTap=editor.project;val beforeRevision=editor.revision
        fun gesture(dx:Float){
            val b=nodes().first{desc(it,"Video preview")}.boundsInRoot;val x=b.center.x;val y=b.center.y;val t=android.os.SystemClock.uptimeMillis()
            fun event(action:Int,px:Float){compose.runOnUiThread{val e=android.view.MotionEvent.obtain(t,android.os.SystemClock.uptimeMillis(),action,px,y,0);compose.activity.window.decorView.dispatchTouchEvent(e);e.recycle()};pump()}
            event(android.view.MotionEvent.ACTION_DOWN,x);event(android.view.MotionEvent.ACTION_MOVE,x+dx);event(android.view.MotionEvent.ACTION_UP,x+dx)
        }
        gesture(0f);gesture(1f)
        assertEquals(beforeTap,editor.project);assertEquals(beforeRevision,editor.revision);assertFalse(editor.transforming)
        gesture(44f);assertEquals(beforeTap,editor.project)
        openTool("Transform");gesture(44f);assertNotEquals(beforeTap,editor.project);assertFalse(editor.transforming);click("Done editing",true)
        compose.runOnUiThread{editor.history(true)};pump();assertEquals(beforeTap,editor.project)
        openTool("Text");click("Add text");waitFor{nodes().any{text(it,"Edit text")}}
        compose.runOnUiThread{val input=nodes().last{it.config.contains(SemanticsActions.SetText)};assertTrue(requireNotNull(input.config[SemanticsActions.SetText].action).invoke(AnnotatedString("Our weekend")))};pump();click("Apply")
        waitFor{nodes().any{text(it,"Our weekend")}};click("Done editing",true);click("Undo",true);openTool("Text");assertFalse(nodes().any{text(it,"Our weekend")});click("Done editing",true);click("Redo",true);openTool("Text");assertTrue(nodes().any{text(it,"Our weekend")});click("Done editing",true);capture("video-editor-text")
        openTool("Keyframes");click("Easing settings",true);click("Push in");click("Motion easing",true);click("Ease in");click("Points settings",true);click("Add keyframe");click("Zoom settings",true)
        compose.runOnUiThread{val slider=nodes().first{desc(it,"Keyframe zoom")&&it.config.contains(SemanticsActions.SetProgress)};assertTrue(requireNotNull(slider.config[SemanticsActions.SetProgress].action).invoke(1.7f))};pump()
        click("Reset Keyframe zoom",true)
        waitFor{nodes().any{desc(it,"Keyframe zoom")&&it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)?.current==1f}}
        capture("video-editor-offline-motion");assertTrue(nodes().first{desc(it,"Video preview")}.boundsInRoot.height>=100);click("Done editing",true)
        openTool("Captions");assertTrue(nodes().any{text(it,"Import SRT / WebVTT")});click("Done editing",true)
        openTool("Overlay");assertTrue(nodes().any{text(it,"Add video or photo overlay")});click("Done editing",true)

        compose.runOnUiThread{val timeline=nodes().first{desc(it,"Timeline")};assertTrue(timeline.config[SemanticsActions.CustomActions].first{it.label=="Next clip"}.action())};pump()
        openTool("Transitions");click("Transition style",true);click("Crossfade");assertTrue(nodes().any{text(it,"Into this clip")});capture("video-editor-rc-transitions");click("Done editing",true)
        openTool("Performance");click("360p");assertTrue(nodes().any{text(it,"Create 360p proxy for this source")});capture("video-editor-rc-performance");click("Done editing",true)
        click("Export video",true);waitFor{nodes().any{text(it,"Export video")}};click("Output resolution",true);click("720p");click("Cancel")
        click("Projects",true);assertTrue(nodes().any{text(it,"New project")});assertTrue(nodes().any{text(it,"Import portable project")});assertTrue(nodes().any{text(it,"Back up project with media")});click("Done")
        AppSdk.sync(c,JSONObject().put("action","permissions").put("id","app.onklav.videoeditor").put("grants",org.json.JSONArray()))
        waitFor{nodes().any{text(it,"Video Edit storage access is off. Restore it in Settings > Apps > Video Edit.")}}
    }
}
