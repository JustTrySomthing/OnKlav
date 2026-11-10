package app.onklav.home

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
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
class AudioEditorUiTest {
    private var canvasWidth=432
    private var canvasHeight=936
    @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private fun androidx.compose.ui.test.SemanticsNodeInteraction.readyClick(): androidx.compose.ui.test.SemanticsNodeInteraction {
        compose.waitUntil(15000) { pump();runCatching { assertIsEnabled();assertIsDisplayed() }.isSuccess }
        performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        pump()
        return this
    }
    private fun SemanticsNodeInteraction.scrollIntoView():SemanticsNodeInteraction{
        // ScrollBy semantics animates; a deliberately paused test clock cannot finish it.
        compose.mainClock.autoAdvance=true
        try{performScrollTo();compose.waitForIdle()}finally{compose.mainClock.autoAdvance=false}
        pump();return this
    }
    private fun pump() { repeat(8) {
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(100)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(16))
        compose.runOnUiThread {
            val env = compose.javaClass.getDeclaredField("environment").apply { isAccessible = true }.get(compose)
            val base = requireNotNull(env.javaClass.superclass)
            val registry = base.getDeclaredField("composeRootRegistry").apply { isAccessible = true }.get(env)
            val roots = registry.javaClass.getMethod("getRegisteredComposeRoots").invoke(registry) as Set<*>
            roots.filterNotNull().forEach { root ->
                val view = root.javaClass.getMethod("getView").invoke(root) as android.view.View
                run { // Android traverses newly created windows even without a pending Compose measure flag.
                    val decor = view.rootView
                    decor.forceLayout()
                    decor.measure(android.view.View.MeasureSpec.makeMeasureSpec(canvasWidth, android.view.View.MeasureSpec.EXACTLY),
                        android.view.View.MeasureSpec.makeMeasureSpec(canvasHeight, android.view.View.MeasureSpec.AT_MOST))
                    decor.layout(0, 0, decor.measuredWidth, decor.measuredHeight)
                    root.javaClass.getMethod("measureAndLayoutForTest").invoke(root)
                }
            }
        }
    } }

    private fun waitFor(test:()->Boolean) {
        try { compose.waitUntil(15000) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50));pump();test() } }
        catch(e:Throwable) {
            compose.onAllNodes(isRoot()).fetchSemanticsNodes().indices.forEach { println(compose.onAllNodes(isRoot())[it].printToString()) }
            capture("sdk-failure")
            throw e
        }
    }

    private fun capture(name:String) { compose.runOnIdle {
        val env=compose.javaClass.getDeclaredField("environment").apply{isAccessible=true}.get(compose)
        val registry=env.javaClass.superclass!!.getDeclaredField("composeRootRegistry").apply{isAccessible=true}.get(env)
        val roots=registry.javaClass.getMethod("getRegisteredComposeRoots").invoke(registry) as Set<*>
        val views=roots.filterNotNull().map{it.javaClass.getMethod("getView").invoke(it) as android.view.View}.map{it.rootView}.filter{it.width>0&&it.height>0}
        val view=views.lastOrNull{it!==compose.activity.window.decorView}?:views.lastOrNull()?:compose.activity.window.decorView
        val b=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(b))
        val f=java.io.File(requireNotNull(System.getProperty("onklav.project.root")),"docs/evidence/audio-editor-refinements/$name.png")
        f.parentFile!!.mkdirs();f.outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };b.recycle()
    } }
    private fun stage(value:String){java.io.File(requireNotNull(System.getProperty("onklav.project.root")),"docs/evidence/audio-editor-refinements/ui-progress.txt").apply{parentFile!!.mkdirs()}.appendText(value+"\n")}
    private fun openFixture(width:Int=432,height:Int=936,fontScale:Float=1f){
        stage("fixture $width x $height font $fontScale")
        canvasWidth=width;canvasHeight=height
        val c=InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking{AppSdk.bootstrap(c)};stage("bootstrap complete")
        assertEquals("audio_edit_stop",c.resources.getResourceEntryName(app.onklav.shared.R.drawable.audio_edit_stop))
        assertEquals("audio_edit_zoomout",c.resources.getResourceEntryName(app.onklav.shared.R.drawable.audio_edit_zoomout))
        assertEquals("audio_edit_selectall",c.resources.getResourceEntryName(app.onklav.shared.R.drawable.audio_edit_selectall))
        val folder=java.io.File(c.filesDir,"audio-editor");folder.mkdirs()
        val pcm=java.io.File(folder,"00000000-0000-0000-0000-000000000001.pcm")
        AudioPcmWriter(pcm).use{w->repeat(40000){w.sample((.35*kotlin.math.sin(2*Math.PI*240*it/8000)).toFloat())}}
        val e=AudioEdit(listOf(AudioRegion(0,40000)))
        val j=JSONObject().put("file",pcm.name).put("rate",8000).put("channels",1).put("frames",40000).put("name","Voice recording.wav").put("edit",e.json())
        java.io.File(folder,"project.json").writeText(j.toString())
        stage("fixture ready; composing")
        compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{
            CompositionLocalProvider(LocalDensity provides Density(1f,fontScale)){
                Surface{Column(Modifier.fillMaxSize()){
                    // Reserve host header and bottom navigation; editor gets the remaining space.
                    Box(Modifier.fillMaxWidth().height(56.dp),contentAlignment=Alignment.CenterStart){Text("Audio Edit",Modifier.padding(horizontal=16.dp),style=MaterialTheme.typography.titleLarge)}
                    Box(Modifier.weight(1f).fillMaxWidth()){AudioEditorWorkspace()}
                    Box(Modifier.fillMaxWidth().height(56.dp),contentAlignment=Alignment.Center){Text("OnKlav",style=MaterialTheme.typography.labelSmall)}
                }}
            }
        }}};compose.mainClock.autoAdvance=false
        stage("content scheduled; waiting for model")
        waitFor{compose.onAllNodesWithText("Voice recording.wav").fetchSemanticsNodes().isNotEmpty() && compose.onNodeWithText("Keep").runCatching{assertIsEnabled()}.isSuccess}
        stage("model ready")
    }
    private fun SemanticsNodeInteraction.toggleAndCheck():SemanticsNodeInteraction{
        val before=fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ToggleableState]
        readyClick()
        assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.ToggleableState,if(before==androidx.compose.ui.state.ToggleableState.On)androidx.compose.ui.state.ToggleableState.Off else androidx.compose.ui.state.ToggleableState.On))
        return this
    }
    private fun fullyInside(node:SemanticsNodeInteraction,container:String){
        val raw=node.getUnclippedBoundsInRoot()
        val bounds=compose.onNodeWithTag(container).getUnclippedBoundsInRoot()
        val b=androidx.compose.ui.geometry.Rect(raw.left.value,raw.top.value,raw.right.value,raw.bottom.value)
        val c=androidx.compose.ui.geometry.Rect(bounds.left.value,bounds.top.value,bounds.right.value,bounds.bottom.value)
        assertTrue("$b must be inside $container $c",b.width>0&&b.height>0&&b.left>=c.left-.5f&&b.right<=c.right+.5f&&b.top>=c.top-.5f&&b.bottom<=c.bottom+.5f)
    }
    private fun assertTransport(){
        for(name in listOf("Compare original","Play preview","Stop","Undo","Redo")){
            val node=compose.onNodeWithContentDescription(name);fullyInside(node,"audio-transport")
            val b=node.fetchSemanticsNode().boundsInRoot;assertTrue("Touch target for $name",b.width>=48&&b.height>=48)
        }
        fullyInside(compose.onNodeWithTag("audio-transport"),"audio-workspace")
    }
    @Test(timeout=90000) @Config(qualifiers="w360dp-h640dp-mdpi")
    fun smallPhoneKeepsToolsAndPlaybackReachable(){
        openFixture(360,640)
        capture("phone-selection")
        fullyInside(compose.onNodeWithText("Keep"),"audio-tool-viewport");assertTransport()
        compose.onNodeWithText("Sound").readyClick();compose.onNodeWithText("Pitch").readyClick()
        fullyInside(compose.onNodeWithContentDescription("Pitch"),"audio-tool-viewport")
        compose.onNodeWithContentDescription("Pitch").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress){it(6f)};pump()
        compose.onNodeWithContentDescription("Reset Pitch").scrollIntoView().readyClick()
        compose.onNodeWithContentDescription("Pitch").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo,androidx.compose.ui.semantics.ProgressBarRangeInfo(0f,-12f..12f)))
        compose.onNodeWithContentDescription("Tempo").scrollIntoView();pump()
        fullyInside(compose.onNodeWithContentDescription("Tempo"),"audio-tool-viewport");assertTransport();capture("phone-pitch")
        compose.onNodeWithText("Tone").readyClick();compose.onNodeWithContentDescription("Treble").scrollIntoView();pump()
        fullyInside(compose.onNodeWithContentDescription("Treble"),"audio-tool-viewport")
        compose.onNodeWithText("Pitch").readyClick();fullyInside(compose.onNodeWithContentDescription("Pitch"),"audio-tool-viewport")
        compose.onNodeWithText("Clean").readyClick();compose.onNodeWithText("Learn noise sample").assertIsDisplayed()
        compose.onNodeWithContentDescription("Reduce noise").assertIsNotEnabled()
        compose.onNodeWithText("Gate").readyClick();compose.onNodeWithContentDescription("Quiet background gate").scrollIntoView();pump();fullyInside(compose.onNodeWithContentDescription("Quiet background gate"),"audio-tool-viewport")
        compose.onNodeWithText("Filters").readyClick();compose.onNodeWithText("Remove low rumble").readyClick().assertIsOn()
        compose.onNodeWithText("Trim").readyClick();compose.onNodeWithText("Fades").readyClick();compose.onNodeWithContentDescription("Fade out").scrollIntoView();pump();fullyInside(compose.onNodeWithContentDescription("Fade out"),"audio-tool-viewport")
        compose.onNodeWithText("Reverse").readyClick();compose.onNodeWithText("Reverse playback").readyClick().assertIsOn()
        compose.onNodeWithText("Quick preview · up to 12 s").assertExists();assertTransport();capture("phone-trim")
        compose.onNodeWithContentDescription("Save copy").readyClick();compose.onNodeWithText("Save a copy").assertExists();compose.onNodeWithText("MP3").assertExists();compose.onNodeWithText("WAV").readyClick();compose.onNodeWithText("Cancel").readyClick()
        compose.onNodeWithContentDescription("More audio options").readyClick();compose.onNodeWithText("Audio Edit help").readyClick();compose.onNodeWithText("Done").readyClick()
        val c=InstrumentationRegistry.getInstrumentation().targetContext
        AppSdk.sync(c,JSONObject().put("action","permissions").put("id","app.onklav.audioeditor").put("grants",org.json.JSONArray()));pump()
        waitFor{compose.onAllNodesWithText("Audio Edit storage access is off. Restore it in Settings > Apps > Audio Edit.").fetchSemanticsNodes().isNotEmpty()}
    }
    @Test(timeout=90000) @Config(qualifiers="w640dp-h360dp-land-mdpi")
    fun shortLandscapeUsesSeparateToolPane(){
        openFixture(640,360)
        stage("landscape pane ready")
        compose.onNodeWithTag("audio-side-by-side").assertExists()
        compose.onNodeWithText("Sound").readyClick();compose.onNodeWithText("Pitch").readyClick()
        compose.onNodeWithContentDescription("Pitch").scrollIntoView();pump();fullyInside(compose.onNodeWithContentDescription("Pitch"),"audio-tool-viewport")
        compose.onNodeWithContentDescription("Tempo").scrollIntoView();pump();fullyInside(compose.onNodeWithContentDescription("Tempo"),"audio-tool-viewport");assertTransport();capture("landscape-pitch")
        compose.onNodeWithText("Clean").readyClick();compose.onNodeWithText("Filters").readyClick()
        compose.onNodeWithText("Remove DC offset").scrollIntoView().toggleAndCheck();assertTransport()
        compose.onNodeWithContentDescription("Zoom in").readyClick()
        compose.onNodeWithContentDescription("Waveform view position").scrollIntoView();pump();fullyInside(compose.onNodeWithContentDescription("Waveform view position"),"audio-timeline")
        compose.onNodeWithContentDescription("Edit selection times").scrollIntoView().readyClick();compose.onNodeWithText("Apply").assertIsDisplayed();compose.onNodeWithText("Cancel").readyClick()
    }
    @Test(timeout=90000) @Config(qualifiers="w320dp-h640dp-mdpi")
    fun largeTextAndNarrowWidthKeepSlidersAndActionsInBounds(){
        openFixture(320,640,1.6f)
        compose.onNodeWithText("Sound").readyClick();compose.onNodeWithText("Pitch").readyClick()
        compose.onNodeWithContentDescription("Pitch").scrollIntoView();pump();fullyInside(compose.onNodeWithContentDescription("Pitch"),"audio-tool-viewport");capture("large-type-pitch-initial")
        compose.onNodeWithContentDescription("Tempo").scrollIntoView();pump();fullyInside(compose.onNodeWithContentDescription("Tempo"),"audio-tool-viewport");assertTransport();capture("large-type-pitch")
        compose.onNodeWithText("Clean").readyClick();compose.onNodeWithText("Filters").readyClick()
        compose.onNodeWithText("Remove DC offset").scrollIntoView().toggleAndCheck()
        compose.onNodeWithContentDescription("Save copy").readyClick();compose.onNodeWithText("WAV").assertIsDisplayed();capture("large-type-save");compose.onNodeWithText("Cancel").readyClick()
    }
    @Test(timeout=90000) @Config(qualifiers="w320dp-h480dp-mdpi")
    fun tinyPhoneCanExpandWaveformAndEnterExactValues(){
        openFixture(320,480,1.6f)
        compose.onNodeWithText("Waveform & selection").assertIsDisplayed()
        compose.onNodeWithContentDescription("Expand waveform").readyClick()
        compose.onNodeWithTag("audio-expanded-waveform").assertIsDisplayed()
        compose.onNodeWithContentDescription("Selection start and end").scrollIntoView();pump()
        fullyInside(compose.onNodeWithContentDescription("Selection start and end"),"audio-timeline")
        capture("tiny-expanded-waveform")
        compose.onNodeWithContentDescription("Edit selection times").scrollIntoView().readyClick()
        compose.onNodeWithText("Start · seconds").performTextReplacement("1.000")
        compose.onNodeWithText("Apply").readyClick()
        compose.onNodeWithText("Back to tools").readyClick()
        compose.onNodeWithContentDescription("Choose audio tool").readyClick()
        compose.onNodeWithText("Sound · Pitch").scrollIntoView().readyClick()
        compose.onNodeWithContentDescription("Set exact Pitch").scrollIntoView().readyClick()
        compose.onNodeWithTag("audio-exact-input").assertIsFocused().performTextReplacement("99")
        pump();compose.onNodeWithText("Apply").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").readyClick()
        compose.onNodeWithContentDescription("Pitch").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo,androidx.compose.ui.semantics.ProgressBarRangeInfo(0f,-12f..12f)))
        compose.onNodeWithContentDescription("Set exact Pitch").scrollIntoView().readyClick()
        compose.onNodeWithTag("audio-exact-input").performTextReplacement("−3,25");pump();capture("exact-pitch")
        compose.onNodeWithTag("audio-exact-input").performImeAction();pump()
        compose.onNodeWithTag("audio-exact-input").assertDoesNotExist()
        compose.onNodeWithContentDescription("Pitch").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo,androidx.compose.ui.semantics.ProgressBarRangeInfo(-3.25f,-12f..12f)))
        compose.onNodeWithContentDescription("Undo").readyClick();compose.onNodeWithContentDescription("Pitch").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo,androidx.compose.ui.semantics.ProgressBarRangeInfo(0f,-12f..12f)))
        compose.onNodeWithContentDescription("Redo").readyClick();compose.onNodeWithContentDescription("Reset Pitch").scrollIntoView().readyClick()
        compose.onNodeWithContentDescription("Set exact Tempo").scrollIntoView().readyClick()
        compose.onNodeWithTag("audio-exact-input").performTextReplacement("125");pump();compose.onNodeWithText("Apply").readyClick()
        compose.onNodeWithContentDescription("Tempo").scrollIntoView();pump()
        compose.onNodeWithContentDescription("Tempo").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo,androidx.compose.ui.semantics.ProgressBarRangeInfo(1.25f,.5f..2f)))
        fullyInside(compose.onNodeWithContentDescription("Tempo"),"audio-tool-viewport");assertTransport();capture("tiny-precise-tools")
        compose.onNodeWithContentDescription("Expand waveform").readyClick()
        compose.runOnUiThread{compose.activity.onBackPressedDispatcher.onBackPressed()};pump()
        compose.onNodeWithTag("audio-expanded-waveform").assertDoesNotExist()
        compose.onNodeWithContentDescription("Choose audio tool").assertIsDisplayed()
    }
    @Test(timeout=90000) @Config(qualifiers="w360dp-h640dp-mdpi")
    fun longPressExplainsIconsAndLongErrorsHaveReadableDetails(){
        openFixture(360,640)
        compose.onNodeWithContentDescription("Compare original").performTouchInput{longClick()};pump()
        compose.onNodeWithText("Compare original").assertIsDisplayed();capture("compare-label")
        compose.mainClock.advanceTimeBy(3000);pump()
        compose.onNodeWithContentDescription("Play preview").assertIsEnabled()
        val message="Unable to save this file. "+"A detailed explanation of the output location. ".repeat(35)+"Final action: choose WAV or another folder."
        val failed=androidx.compose.runtime.mutableStateOf(true)
        val caption=androidx.compose.runtime.mutableStateOf(message)
        compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{Surface{Box(Modifier.fillMaxSize().padding(12.dp)){AudioStatusMessage(caption.value,failed.value,Modifier.fillMaxWidth().align(Alignment.BottomCenter))}}}}};pump()
        compose.onNodeWithContentDescription("Show audio status details").readyClick()
        compose.onNodeWithTag("audio-status-detail").assertTextEquals(message)
        compose.mainClock.autoAdvance=true
        compose.onNodeWithTag("audio-status-detail").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy){it(0f,10000f)}
        compose.waitForIdle();compose.mainClock.autoAdvance=false;pump()
        val range=compose.onNodeWithTag("audio-status-detail").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange]
        assertTrue("Full message scrolls",range.maxValue()>0f&&range.value()>0f)
        capture("full-error-details")
        compose.onNodeWithText("Close").readyClick();compose.onNodeWithTag("audio-status-detail").assertDoesNotExist()
        compose.onNodeWithContentDescription("Show audio status details").assertIsDisplayed()
    }

}
