@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package app.onklav.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.media3.common.C
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class VideoProTest {
    private fun clip(ms:Long=10000)=VideoClip(file="${videoId()}.media",name="Trip.mp4",sourceMs=ms)
    @Test fun oldProjectJsonRetainsCompatibilityAndNewFieldsRoundTrip(){
        val c=clip();val old=VideoProject(clips=listOf(c)).json();old.remove("layers");old.remove("bitrate");val cj=old.getJSONArray("clips").getJSONObject(0);cj.remove("keyframes");cj.remove("rates")
        val restored=VideoProject.parse(old);assertEquals(emptyList<VideoLayer>(),restored.layers);assertEquals(c.duration,restored.duration)
        val keys=listOf(VideoKeyframe(0),VideoKeyframe(5000,2f,.3f,-.2f,45f,.5f))
        val layer=VideoLayer(clip=clip(3000),offset=1000,chroma=true,mask=1)
        val p=restored.copy(clips=listOf(c.copy(keyframes=keys)),layers=listOf(layer),height=2160,fps=60,bitrate=16_000_000)
        assertEquals(p,VideoProject.parse(p.json()));assertEquals(3840 to 2160,VideoComposition.dimensions(p))
    }
    @Test fun variableSpeedTimingSplitsAndAudioProviderAgree(){
        val c=clip().copy(start=1000,end=9000,rates=listOf(VideoRate(1000,1f),VideoRate(5000,.5f)))
        assertEquals(12000L,c.duration);assertEquals(6000L,videoSourceAt(c,6000));val split=VideoProject(clips=listOf(c)).split(0,6000)
        assertEquals(c.duration,split.duration);assertEquals(6000L,split.clips[0].duration);assertEquals(6000L,split.clips[1].duration)
        val speed=videoSpeedProvider(c);assertEquals(1f,speed.getSpeed(0),0f);assertEquals(.5f,speed.getSpeed(4_000_000),0f);assertEquals(4_000_000L,speed.getNextSpeedChangeTimeUs(0));assertEquals(C.TIME_UNSET,speed.getNextSpeedChangeTimeUs(4_000_000))
        assertEquals(split,VideoProject.parse(split.json()))
    }
    @Test fun keyframeInterpolationAndSplitBoundaryStayContinuous(){
        val c=clip(4000).copy(keyframes=listOf(VideoKeyframe(0),VideoKeyframe(4000,2f,.5f,-.5f,90f,.5f)))
        val middle=videoPose(c,2000);assertEquals(1.5f,middle.zoom,.001f);assertEquals(45f,middle.rotation,.001f);assertEquals(.75f,middle.opacity,.001f)
        val split=VideoProject(clips=listOf(c)).split(0,2000);assertEquals(videoPose(split.clips[0],2000).copy(at=0),videoPose(split.clips[1],0));assertEquals(split,VideoProject.parse(split.json()))
        assertThrows(IllegalArgumentException::class.java){VideoClip.parse(c.copy(keyframes=listOf(VideoKeyframe(5000))).json())}
    }
    @Test fun subtitlesCrossClipBoundariesAndExportWithoutLosingText(){
        val srt="1\n00:00:04,500 --> 00:00:06,500\nHello <b>world</b>\nsecond line\n\n"
        val cues=videoParseCaptions(srt);assertEquals("Hello world\nsecond line",cues.single().text)
        val p=videoAddCaptions(VideoProject(clips=listOf(clip(5000),clip(5000))),cues)
        assertEquals(4500L,p.clips[0].titles.single().start);assertEquals(5000L,p.clips[0].titles.single().end);assertEquals(1500L,p.clips[1].titles.single().end)
        assertEquals(2,videoParseCaptions(videoCaptionsSrt(p)).size)
        assertEquals(1000L,videoParseCaptions("WEBVTT\n\n00:01.000 --> 00:02.500 align:center\nHello\n").single().start)
        assertThrows(IllegalArgumentException::class.java){videoParseCaptions("1\n00:02,000 --> 00:01,000\nBad\n")}
        assertEquals(p,VideoProject.parse(p.json()))
    }
    @Test fun realOverlaySequencesCarryTimingScaleAlphaAndCutoutEffects(){
        val c=clip().copy(keyframes=listOf(VideoKeyframe(0,opacity=.5f)))
        val a=VideoLayer(clip=clip(2000),offset=1000,scale=.4f,opacity=.6f,chroma=true,mask=1)
        val b=VideoLayer(clip=clip(1000),offset=3000,x=-.5f)
        val p=VideoProject(clips=listOf(c),layers=listOf(a,b));val comp=VideoComposition.build(p,File("/tmp/video-fixture"))
        assertEquals(3,comp.sequences.size);assertTrue(comp.sequences[1].editedMediaItems.any{item->item.effects.videoEffects.any{it is VideoCutoutEffect}});assertTrue(comp.sequences[1].editedMediaItems.first{it.effects.videoEffects.any{effect->effect is VideoCutoutEffect}}.removeAudio)
        val settings=comp.videoCompositorSettings
        assertEquals(0f,settings.getOverlaySettings(0,0).alphaScale,0f)
        assertEquals(.6f,settings.getOverlaySettings(1,1_500_000).alphaScale,.001f);assertEquals(.4f,settings.getOverlaySettings(1,1_500_000).scale.first,.001f)
        assertEquals(.5f,settings.getOverlaySettings(2,0).alphaScale,.001f)
        assertFalse(VideoComposition.directPreview(p));assertEquals(p,VideoProject.parse(p.json()))
    }
    @Test fun shorteningMainTrackClampsOverlaysAndProjectStoreChecksTheirFiles(){
        val c=clip();val layer=VideoLayer(clip=clip(4000),offset=1000);val p=videoClampProject(VideoProject(clips=listOf(c.copy(end=2000)),layers=listOf(layer)))
        assertEquals(1000L,p.layers.single().clip.duration)
        val context=ApplicationProvider.getApplicationContext<Context>();val store=VideoStore(context);File(store.folder(p.id),c.file).writeText("source");File(store.folder(p.id),layer.clip.file).writeText("overlay");store.save(p);assertEquals(p,store.load(p.id))
        File(store.folder(p.id),layer.clip.file).delete();assertEquals(p,store.load(p.id));assertEquals(listOf(layer.clip.file),store.missing(p))
        assertThrows(IllegalArgumentException::class.java){VideoProject.parse(p.copy(layers=listOf(layer.copy(scale=2f))).json())}
    }
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test fun captionsUseOneBoundedTextureAndInactiveCuesStayTransparent(){
        val titles=(0 until 100).map{i->VideoTitle(text="Caption $i",start=i*1000L,end=(i+1)*1000L,caption=true)}
        val layer=VideoTitleOverlay(titles,720,1280)
        assertEquals(0f,layer.getOverlaySettings(101_000_000).alphaScale,0f)
        assertEquals(1,layer.getBitmap(101_000_000).width)
        val first=layer.getBitmap(500_000);assertTrue(first.width<=720&&first.height<=1280);assertEquals(1f,layer.getOverlaySettings(500_000).alphaScale,0f)
        assertSame(first,layer.getBitmap(600_000));assertNotSame(first,layer.getBitmap(1_500_000));layer.release()
    }
    @Test fun includedVideoDefaultsMicrophoneCapability(){
        val context=ApplicationProvider.getApplicationContext<Context>();runBlocking{AppSdk.bootstrap(context)}
        assertTrue(videoEditorAccess(context));assertTrue(videoEditorAccess(context,"audio.record"))
        val rows=AppSdk.sync(context,JSONObject().put("action","list")) as org.json.JSONArray
        val row=(0 until rows.length()).map{rows.getJSONObject(it)}.first{it.getJSONObject("manifest").getString("id")=="app.onklav.videoeditor"}
        assertEquals(4,row.getJSONObject("manifest").getInt("version"))
    }
}
