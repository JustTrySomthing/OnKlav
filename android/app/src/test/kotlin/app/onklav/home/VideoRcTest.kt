@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package app.onklav.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.media3.common.C
import androidx.media3.common.audio.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.*
import java.nio.*
import kotlin.math.*

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class VideoRcTest {
    private fun clip(ms:Long=4000)=VideoClip(file="${videoId()}.media",name="Source.mp4",sourceMs=ms)
    @Test fun overlapsChangeTheTimelineWithoutTripleOverlap(){
        val a=clip();val b=clip(2000).copy(transition="Crossfade",transitionMs=2000);val c=clip().copy(transition="Wipe left",transitionMs=2000);val p=VideoProject(clips=listOf(a,b,c))
        assertEquals(1000L,videoOverlap(p.clips,1));assertEquals(1000L,videoOverlap(p.clips,2));assertEquals(8000L,p.duration);assertEquals(3000L,p.clipStart(1));assertEquals(4000L,p.clipStart(2));assertEquals(p,VideoProject.parse(p.json()))
        for(t in 0L until p.duration step 50)assertTrue(p.clips.indices.count{t in p.clipStart(it) until p.clipStart(it)+p.clips[it].duration}<=2)
        assertThrows(IllegalArgumentException::class.java){p.split(1,500)}
        val split=VideoProject(clips=listOf(a,clip().copy(transition="Crossfade"))).split(1,2000);assertEquals(7500L,split.duration);assertEquals("None",split.clips[2].transition)
    }
    @Test fun crossfadeUsesTwoRealSequencesAndFillsTheirTimelineGaps(){
        val p=VideoProject(clips=listOf(clip(),clip().copy(transition="Crossfade",transitionMs=1000),clip().copy(transition="Wipe up",transitionMs=500)))
        val comp=VideoComposition.build(p,File("/tmp/rc-fixture"));assertEquals(2,comp.sequences.size)
        assertTrue(comp.sequences[0].editedMediaItems.first().effects.videoEffects.any{it is VideoTransitionEffect});assertFalse(comp.sequences[1].editedMediaItems.last{it.effects.videoEffects.isNotEmpty()}.effects.videoEffects.any{it is VideoTransitionEffect})
        assertEquals(0f,comp.videoCompositorSettings.getOverlaySettings(1,1_000_000).alphaScale,0f)
        assertEquals(1f,comp.videoCompositorSettings.getOverlaySettings(0,3_500_000).alphaScale,0f);assertEquals(1f,comp.videoCompositorSettings.getOverlaySettings(1,3_500_000).alphaScale,0f)
        val fade=comp.sequences[0].editedMediaItems.first().effects.audioProcessors.filterIsInstance<VideoGain>().single();fade.configure(AudioProcessor.AudioFormat(1000,1,C.ENCODING_PCM_16BIT));fade.flush();val values=ShortArray(4000){10000};val out=process(fade,values);assertEquals(10000,out[3000].toInt());assertEquals(5000,out[3500].toInt())
    }
    @Test fun wipeSettingsHandleBothAlternatingDirections(){
        val fx=VideoTransitionEffect("Wipe left",1000,"Crossfade",500,4000)
        assertEquals(Triple(2,.5f,0f),fx.settings(500));assertEquals(Triple(0,1f,0f),fx.settings(2000));assertEquals(Triple(1,.5f,1f),fx.settings(3750))
    }
    @Test fun customBezierInvertsTimeAndRetainsItsShapeAcrossSplits(){
        val linear=VideoKeyframe(0,easing="Custom",cx1=0f,cy1=0f,cx2=1f,cy2=1f)
        for(i in 0..100)assertEquals(i/100f,videoEase(linear,i/100f),.00001f)
        val k=linear.copy(cx1=.12f,cy1=.7f,cx2=.8f,cy2=.9f);val c=clip().copy(keyframes=listOf(k,VideoKeyframe(4000,zoom=2f,rotation=90f)))
        val p=VideoProject.parse(VideoProject(clips=listOf(c)).split(0,1300).json())
        for(t in 0L..4000L step 37){val actual=if(t<=1300)videoPose(p.clips[0],t)else videoPose(p.clips[1],t-1300);assertEquals(videoPose(c,t).zoom,actual.zoom,.0001f)}
        assertThrows(IllegalArgumentException::class.java){c.copy(keyframes=listOf(k.copy(cx1=2f))).checked()}
    }
    private fun cube(swap:Boolean=false):String="TITLE \"Test\"\nLUT_3D_SIZE 2\nDOMAIN_MIN 0 0 0\nDOMAIN_MAX 1 1 1\n"+(0..1).flatMap{b->(0..1).flatMap{g->(0..1).map{r->if(swap)"$b $g $r"else"$r $g $b"}}}.joinToString("\n")
    @Test fun cubeLutsUseRedFastestOrderAndBlendInLinearLight(){
        val identity=VideoLut.parse(cube());assertArrayEquals(floatArrayOf(.2f,.4f,.8f),identity.sample(.2f,.4f,.8f),.00001f)
        val swap=VideoLut.parse(cube(true));assertArrayEquals(floatArrayOf(.8f,.4f,.2f),swap.sample(.2f,.4f,.8f),.00001f)
        val strong=swap.cube(1f);assertEquals(android.graphics.Color.BLUE,strong[1][0][0]);val neutral=swap.cube(0f);assertEquals(android.graphics.Color.RED,neutral[1][0][0]);assertEquals(2,swap.effect(.5f).getLength(0))
        val context=ApplicationProvider.getApplicationContext<Context>();val folder=File(context.cacheDir,videoId()).apply{mkdirs()};val oversized=File(folder,"${videoId()}.media")
        try{java.io.RandomAccessFile(oversized,"rw").use{it.setLength(8_000_000_000L)};val c=clip().copy(lut=oversized.name);assertThrows(IllegalArgumentException::class.java){VideoComposition.build(VideoProject(clips=listOf(c)),folder)}}finally{folder.deleteRecursively()}
        for(bad in listOf("LUT_3D_SIZE 65",cube()+"\n0 0 0",cube().replace("DOMAIN_MAX 1 1 1","DOMAIN_MAX 2 2 2"),cube().replace("1 1 1\n","NaN 1 1\n")))assertThrows(IllegalArgumentException::class.java){VideoLut.parse(bad)}
    }
    private fun process(p:BaseAudioProcessor,s:ShortArray):ShortArray{val b=ByteBuffer.allocateDirect(s.size*2).order(ByteOrder.nativeOrder());s.forEach{b.putShort(it)};b.flip();p.queueInput(b);val out=p.output.order(ByteOrder.nativeOrder());return ShortArray(out.remaining()/2){out.short}}
    @Test fun noiseCleanupIsChunkStableAndRumbleFilterReducesBass(){
        fun make(amount:Float,rumble:Boolean)=VideoNoise(amount,rumble).apply{configure(AudioProcessor.AudioFormat(48000,1,C.ENCODING_PCM_16BIT));flush()}
        val source=ShortArray(48000){(sin(2*PI*20*it/48000)*4000).roundToInt().toShort()}
        fun rms(s:ShortArray)=sqrt(s.drop(10000).map{it.toDouble()*it}.average())
        val neutral=process(make(0f,false),source);assertArrayEquals(source,neutral);val reduced=process(make(0f,true),source);assertTrue(rms(reduced)<rms(source)*.3)
        val noise=ShortArray(48000){((it*1664525L+1013904223L)%199-99).toShort()};val all=process(make(1f,false),noise);assertTrue(rms(all)<rms(noise)*.8)
        val chunk=make(1f,false);assertArrayEquals(all,process(chunk,noise.take(1337).toShortArray())+process(chunk,noise.drop(1337).toShortArray()))
    }
    @Test fun sonicPitchChangesFrequencyWhileKeepingDuration(){
        val p=SonicAudioProcessor().apply{setPitch(2f);configure(AudioProcessor.AudioFormat(48000,1,C.ENCODING_PCM_16BIT));flush(AudioProcessor.StreamMetadata.DEFAULT)}
        val data=ByteBuffer.allocateDirect(96000).order(ByteOrder.nativeOrder());repeat(48000){data.putShort((sin(2*PI*440*it/48000)*10000).roundToInt().toShort())};data.flip();p.queueInput(data)
        val values=ArrayList<Short>();fun drain(){val o=p.output.order(ByteOrder.nativeOrder());while(o.remaining()>=2)values+=o.short};drain();p.queueEndOfStream();drain()
        assertTrue(abs(values.size-48000)<1000);val middle=values.drop(5000).take(30000);val crossings=middle.zipWithNext().count{it.first<=0&&it.second>0};val hz=crossings*48000f/middle.size;assertEquals(880f,hz,15f)
    }
    @Test fun beatDetectorFindsRhythmicOnsetsAndRejectsQuietBackground(){
        val d=VideoBeatDetector();val quiet=VideoBeatDetector()
        for(i in 0 until 240000){val ms=i*1000L/48000;val hit=ms%500<35;d.sample(if(hit).3f*sin(i*.3f)else .001f,ms);quiet.sample(.001f*sin(i*.3f),ms)};d.finish();quiet.finish();assertTrue(d.beats.size in 9..10);assertTrue(d.beats.all{min(it%500,500-it%500)<50});assertTrue(quiet.beats.isEmpty())
    }
    @Test fun previewProxyIsNeverUsedForExportOrPortableBackup(){
        val context=ApplicationProvider.getApplicationContext<Context>();val store=VideoStore(context);val c=clip();val lut="${videoId()}.media";val p=VideoProject(clips=listOf(c.copy(lut=lut,lutName="Look")),beats=listOf(500),previewHeight=360);val folder=store.folder(p.id);val source=File(folder,c.file).apply{writeText("original")};File(folder,lut).writeText(cube());val proxy=videoProxyPath(folder,c).apply{parentFile!!.mkdirs();writeText("proxy")}
        assertEquals(source,videoMediaFile(folder,c,true));File(proxy.path+".ready").writeText(c.sourceMs.toString());assertEquals(proxy,videoMediaFile(folder,c,true));assertEquals(source,videoMediaFile(folder,c,false));assertEquals(listOf(c.file,lut),VideoProjectArchive.files(p))
        store.save(p);assertEquals(p,store.load(p.id));val out=ByteArrayOutputStream();VideoProjectArchive.write(p,folder,out);val restored=VideoProjectArchive.read(ByteArrayInputStream(out.toByteArray()),store.root);assertEquals(cube(),File(store.folder(restored.id),lut).readText());assertFalse(File(store.folder(restored.id),".proxies").exists())
        source.appendText("changed");assertEquals(source,videoMediaFile(folder,c,true));assertEquals(640 to 360,VideoComposition.dimensions(p,true));assertEquals(1920 to 1080,VideoComposition.dimensions(p,false))
    }
    @Test fun captionsFollowOverlapTimelineAndMarkersClampOnShortening(){
        val p=VideoProject(clips=listOf(clip(),clip().copy(transition="Crossfade",transitionMs=1000)),beats=listOf(500,6500));val captions=videoAddCaptions(p,listOf(VideoCaption(2800,3500,"Across")))
        assertEquals(3000L,captions.clips[0].titles.single().end);assertEquals(0L,captions.clips[1].titles.single().start);assertEquals(500L,captions.clips[1].titles.single().end);assertEquals(3500L,videoParseCaptions(videoCaptionsSrt(captions)).last().end)
        assertEquals(listOf(500L),videoClampProject(p.copy(clips=listOf(p.clips[0]))).beats)
    }
}
