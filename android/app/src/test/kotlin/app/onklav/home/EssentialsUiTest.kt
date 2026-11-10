package app.onklav.home

import androidx.activity.compose.setContent
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
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
class EssentialsUiTest{
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
        compose.runOnUiThread{var node=nodes().last{if(description)desc(it,value)else text(it,value)};while(!node.config.contains(SemanticsActions.OnClick))node=requireNotNull(node.parent);assertFalse(node.config.contains(SemanticsProperties.Disabled));assertTrue(requireNotNull(node.config[SemanticsActions.OnClick].action).invoke())};pump()}
    private fun capture(name:String){compose.runOnUiThread{val v=compose.activity.window.decorView;val bitmap=android.graphics.Bitmap.createBitmap(v.width,v.height,android.graphics.Bitmap.Config.ARGB_8888);v.draw(android.graphics.Canvas(bitmap));val f=java.io.File(requireNotNull(System.getProperty("onklav.project.root")),"docs/evidence/v0.99.0/$name.png");f.parentFile!!.mkdirs();f.outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}}
    @Test(timeout=90000) fun clockTabsTimerDialogAndStopwatchControlsAreReachable(){
        val c=compose.activity.application;runBlocking{AppSdk.bootstrap(c)}
        org.robolectric.shadows.ShadowChoreographer.setPaused(true);compose.mainClock.autoAdvance=false
        compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{ClockWorkspace()}}}
        waitFor{nodes().any{text(it,"Add alarm")}};capture("clock-alarms")
        click("Add alarm");waitFor{nodes().any{text(it,"Repeat")}};click("Cancel")
        click("Timer");click("Add timer");waitFor{nodes().any{text(it,"New timer")}};click("Cancel")
        click("Stopwatch");click("Start");waitFor{nodes().any{desc(it,"Lap")}};click("Lap",true);click("Pause");waitFor{nodes().any{text(it,"Resume")}};assertEquals(1,ClockDisk.watch(c).laps.size);capture("clock-stopwatch")
        click("Reset stopwatch",true);assertEquals(0L,ClockDisk.watch(c).accumulated)
    }
    @Test(timeout=90000) fun runningTimerCountsDownWhileItsLazyRowStaysVisible(){
        val c=compose.activity.application;runBlocking{AppSdk.bootstrap(c)}
        c.getSharedPreferences("native-clock",0).edit().clear().commit()
        org.robolectric.shadows.ShadowChoreographer.setPaused(true);compose.mainClock.autoAdvance=false
        compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{ClockWorkspace()}}}
        click("Timer");click("1 min");waitFor{nodes().any{text(it,"01:00")}}
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(1200))
        waitFor{nodes().any{text(it,"00:59")||text(it,"00:58")}}
        click("Pause");val paused=ClockDisk.timers(c).single().remaining
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(1200));pump()
        assertEquals(paused,ClockDisk.timers(c).single().remaining)
        click("Reset timer",true);waitFor{nodes().any{text(it,"01:00")}}
    }
    @Test(timeout=90000) fun flashlightHasExplicitModesAutoOffAndRestorableScreenLight(){
        val c=compose.activity.application;runBlocking{AppSdk.bootstrap(c)}
        org.robolectric.shadows.ShadowChoreographer.setPaused(true);compose.mainClock.autoAdvance=false
        compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{FlashlightWorkspace()}}}
        waitFor{nodes().any{text(it,"Screen light")}};capture("flashlight")
        click("10 min");assertEquals(10,c.getSharedPreferences("flashlight",0).getInt("minutes",0))
        click("Screen light");waitFor{nodes().any{text(it,"Warm")}};click("Warm");click("Close screen light",true);waitFor{nodes().any{text(it,"Flashlight is off")}}
    }
    @Test(timeout=90000) fun cameraSettingsCapturesAndPermissionControlsHaveNoDeadEnds(){
        val c=compose.activity.application;runBlocking{AppSdk.bootstrap(c)};val model=SdkAppModel(c);runBlocking{model.open(CAMERA_APP)}
        org.robolectric.shadows.ShadowChoreographer.setPaused(true);compose.mainClock.autoAdvance=false
        compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{CameraContent(model,enableHardware=false)}}}
        waitFor{nodes().any{desc(it,"Camera settings")}};capture("camera")
        click("Camera settings",true);waitFor{nodes().any{text(it,"Mirror selfies")}};click("1:1");assertEquals("1:1",c.getSharedPreferences("camera-settings",0).getString("ratio",null));click("Shutter sound",true);assertFalse(c.getSharedPreferences("camera-settings",0).getBoolean("shutter_sound",true));click("Done")
        val preview=nodes().first{desc(it,"Camera capture preview")}.boundsInRoot;assertEquals(preview.width,preview.height,2f)
        assertFalse(nodes().any{desc(it,"Captures")})
        click("Open captures",true);waitFor{nodes().any{text(it,"No photos or videos yet")}};click("Camera");waitFor{nodes().any{text(it,"Photo")}}
        AppSdk.sync(c,JSONObject().put("action","permissions").put("id",CAMERA_APP).put("grants",org.json.JSONArray()))
        assertTrue(runCatching{runBlocking{model.cameraAction("list")}}.isFailure)
    }
}
