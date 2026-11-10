package app.onklav.home

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.unit.dp
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
class YoutubeDownloadUiTest{
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
    private fun click(value:String,description:Boolean=false){waitFor{nodes().any{n->
        if(!(if(description)desc(n,value)else text(n,value)))false else{var action=n;while(!action.config.contains(SemanticsActions.OnClick)&&action.parent!=null)action=action.parent!!;!action.config.contains(SemanticsProperties.Disabled)}
    }}
        compose.runOnUiThread{var node=nodes().last{if(description)desc(it,value)else text(it,value)};while(!node.config.contains(SemanticsActions.OnClick))node=requireNotNull(node.parent);assertFalse(node.config.contains(SemanticsProperties.Disabled));assertTrue(requireNotNull(node.config[SemanticsActions.OnClick].action).invoke())};pump()}
    private fun capture(name:String){compose.runOnUiThread{val v=roots().map{(it.javaClass.getMethod("getView").invoke(it) as android.view.View).rootView}.maxBy{it.width*it.height};val bitmap=android.graphics.Bitmap.createBitmap(v.width,v.height,android.graphics.Bitmap.Config.ARGB_8888);v.draw(android.graphics.Canvas(bitmap));val f=java.io.File(requireNotNull(System.getProperty("onklav.project.root")),"docs/evidence/media-repair/$name.png");f.parentFile!!.mkdirs();f.outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}}
    @Test(timeout=60000) fun downloadDialogChoosesAudioAndRealVideoSizes(){
        val c=compose.activity.application;runBlocking{AppSdk.bootstrap(c)};val model=SdkAppModel(c);runBlocking{model.open("app.onklav.youtube")};val pool=SdkWebPool();pool.videoStreams.bind("AbC_def-123")
        org.robolectric.shadows.ShadowChoreographer.setPaused(true);compose.mainClock.autoAdvance=false
        val visible=androidx.compose.runtime.mutableStateOf(true)
        compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{YoutubeDownloadDialog(model,"youtube.page",pool,null,visible.value,resolveVideo={_,verify->verify();YoutubeResolver.Result("Test video",listOf(YoutubeStream(135,"https://r1.googlevideo.com/videoplayback", "video/mp4",480,true),YoutubeStream(137,"https://r1.googlevideo.com/videoplayback", "video/mp4",1080,true)))}){visible.value=false}}}}
        waitFor{nodes().any{text(it,"1080p")}};click("Audio");assertTrue(nodes().any{text(it,"Faster")});assertTrue(nodes().any{text(it,"Slower")});click("MP3");capture("youtube-audio-choices");assertFalse(nodes().any{text(it,"480p")});click("Video");click("480p");capture("youtube-download-dialog");click("Cancel");waitFor{!visible.value}
    }
    @Test(timeout=60000) fun disabledDownloadsCanBeEnabledInsideDialogAndReopenAWorkingSession(){
        val c=compose.activity.application;runBlocking{AppSdk.bootstrap(c)};val model=SdkAppModel(c);runBlocking{model.open("app.onklav.youtube")}
        runBlocking{AppSdk.call(c,JSONObject().put("action","permissions").put("id","app.onklav.youtube").put("grants",org.json.JSONArray(listOf("storage.private","network.web"))))}
        val pool=SdkWebPool();pool.videoStreams.bind("AbC_def-123");val visible=androidx.compose.runtime.mutableStateOf(true)
        org.robolectric.shadows.ShadowChoreographer.setPaused(true);compose.mainClock.autoAdvance=false
        compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{YoutubeDownloadDialog(model,"youtube.page",pool,null,visible.value,resolveVideo={_,verify->verify();YoutubeResolver.Result("Test video",listOf(YoutubeStream(135,"https://r1.googlevideo.com/videoplayback", "video/mp4",480,true),YoutubeStream(137,"https://r1.googlevideo.com/videoplayback", "video/mp4",1080,true)))}){visible.value=false}}}}
        waitFor{nodes().any{text(it,"Enable downloads")}};assertFalse(nodes().any{n->n.config.getOrNull(SemanticsProperties.Text)?.any{it.text.contains("Settings >")}==true});click("Enable downloads")
        waitFor{!visible.value&&!model.busy&&runCatching{model.webComponent("youtube.page");true}.getOrDefault(false)};assertNull(model.error)
    }

    @Test(timeout=45000) fun separateProgressShowsDownloadCompleteWhileConversionContinues(){
        org.robolectric.shadows.ShadowChoreographer.setPaused(true);compose.mainClock.autoAdvance=false
        val converting=androidx.compose.runtime.mutableStateOf(false)
        compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{androidx.compose.material3.Surface{androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(20.dp)){
            YoutubeAudioChoices("M4A",true,true){}
            YoutubeAudioProgress(if(converting.value)1f else .4f,if(converting.value).25f else 0f,converting.value)
        }}}}}
        pump();assertTrue(nodes().any{text(it,"Waiting")});assertTrue(nodes().any{text(it,"40%")})
        compose.runOnUiThread{converting.value=true};pump();assertTrue(nodes().any{text(it,"100%")});assertTrue(nodes().any{text(it,"25%")});assertFalse(nodes().any{text(it,"Waiting")});capture("youtube-mp3-progress")
    }
    @Test(timeout=60000) fun calculatorUsesSpareHeightForKeysInsteadOfEmptyDisplay(){
        val c=compose.activity.application;runBlocking{AppSdk.bootstrap(c)};val model=SdkAppModel(c);runBlocking{model.open("app.onklav.calc")}
        org.robolectric.shadows.ShadowChoreographer.setPaused(true);compose.mainClock.autoAdvance=false
        compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{androidx.compose.material3.Surface(androidx.compose.ui.Modifier.fillMaxSize()){CalcWorkspace(model)}}}}
        waitFor{nodes().any{text(it,"7")}};val display=nodes().first{desc(it,"Calculator display")}.boundsInRoot
        var key=nodes().first{text(it,"7")};while(!key.config.contains(SemanticsActions.OnClick))key=requireNotNull(key.parent)
        assertTrue(display.height<=180);assertTrue("Keys use remaining space",key.boundsInRoot.height>80)
        click("7");click("+");click("8");click("=",false);waitFor{!model.busy&&nodes().any{text(it,"15")}};compose.mainClock.advanceTimeBy(1000);pump();capture("calculator-taller-keys");click("AC");click("1");click("÷");click("7");click("=");waitFor{!model.busy};compose.mainClock.advanceTimeBy(1000);pump();assertTrue(nodes().first{desc(it,"Calculation result")}.boundsInRoot.height<80);capture("calculator-long-result")
    }

}
