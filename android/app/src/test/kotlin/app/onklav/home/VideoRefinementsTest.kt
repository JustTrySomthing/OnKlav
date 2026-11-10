package app.onklav.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.onklav.shared.Mp3Codec
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class VideoRefinementsTest {
    private fun clip(ms:Long=10000)=VideoClip(file="${videoId()}.media",name="Trip.mp4",sourceMs=ms)
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    @Test fun frameSteppingDoesNotAccumulateRoundingError(){
        for(fps in listOf(24,25,30,50,60)){var at=0L;repeat(fps*10){at=videoStepFrame(at,1,fps,20000)};assertEquals("$fps fps",10000L,at);repeat(fps*10){at=videoStepFrame(at,-1,fps,20000)};assertEquals(0L,at)}
        assertEquals(0L,videoStepFrame(0,-1,30,1000));assertEquals(1000L,videoStepFrame(1000,1,30,1000))
    }
    @Test fun snappingFindsOverlapsTitlesLayersAudioAndBeatsWithinTolerance(){
        val a=clip(4000).copy(titles=listOf(VideoTitle(start=500,end=1500)))
        val b=clip(4000).copy(transition="Crossfade",transitionMs=1000)
        val p=VideoProject(clips=listOf(a,b),layers=listOf(VideoLayer(clip=clip(1000),offset=2000)),music=listOf(VideoMusic(file=a.file,name="Music",sourceMs=1000,offset=4500)),beats=listOf(6000))
        for(anchor in listOf(0L,500L,1500L,2000L,3000L,4000L,4500L,5500L,6000L,p.duration)){assertEquals(anchor,videoTimelineSnap(p,anchor+20,30))}
        assertEquals(3250L,videoTimelineSnap(p,3250,20));assertEquals(3251L,videoTimelineSnap(p,3251,10000))
        assertEquals(500L,videoTimelineSnap(p,510,20));assertEquals(510L,videoTimelineSnap(p,510,20,a.id))
    }
    @Test fun exactPositionsRejectInvalidAndOutOfRangeInput(){
        assertEquals(1234L,videoPositionMs("1.234",5000));assertEquals(1234L,videoPositionMs("1,234",5000));assertEquals(5000L,videoPositionMs("5",5000))
        for(value in listOf("NaN","Infinity","-0.1","5.001","1e99","","1:30"))assertNull(value,videoPositionMs(value,5000))
    }
    @Test fun corruptedProjectReopensThePreviousDurableRevision(){
        val store=VideoStore(context);val c=clip();val p=VideoProject(clips=listOf(c),name="First revision");File(store.folder(p.id),c.file).writeText("source")
        store.save(p);store.save(p.copy(name="Second revision"));File(store.folder(p.id),"project.json").writeText("interrupted JSON")
        assertEquals(p,store.current());assertEquals(p,store.load(p.id));assertEquals("source",File(store.folder(p.id),c.file).readText())
        store.save(p.copy(name="Recovered edits"));File(store.folder(p.id),"project.json").writeText("broken again");assertEquals(p,store.load(p.id))
    }
    @Test fun failedSaveNeverRedirectsTheCurrentProjectPointer(){
        val store=VideoStore(context);val p=VideoProject(name="Safe");store.save(p)
        val blocked=VideoProject(name="Cannot save");File(store.root,blocked.id).writeText("blocks the project directory")
        assertThrows(java.io.IOException::class.java){store.save(blocked)};assertEquals(p,store.current())
    }
    @Test fun missingMediaKeepsTheProjectVisibleAndItsEditsIntact(){
        val store=VideoStore(context);val c=clip();val p=VideoProject(clips=listOf(c),music=listOf(VideoMusic(file=c.file,name="Extracted",sourceMs=c.sourceMs)))
        store.save(p);assertEquals(p,store.load(p.id));assertTrue(store.list().contains(p));assertEquals(listOf(c.file),store.missing(p))
        File(store.folder(p.id),c.file).writeText("restored");assertTrue(store.missing(p).isEmpty());assertEquals(p,store.load(p.id))
    }
    @Test fun proxyCacheInvalidatesWhenOriginalChangesAndNeverReplacesExport(){
        val store=VideoStore(context);val c=clip();val folder=store.folder(videoId());val source=File(folder,c.file).apply{writeText("source")};val proxy=videoProxyPath(folder,c).apply{parentFile!!.mkdirs();writeText("proxy")}
        File(proxy.path+".ready").writeText(c.sourceMs.toString());assertEquals(proxy,videoMediaFile(folder,c,true));assertEquals(source,videoMediaFile(folder,c,false))
        source.appendText("changed");assertEquals(source,videoMediaFile(folder,c,true));assertTrue(source.exists())
    }
    @Test fun wavProbeUsesPcmSamplesAndRejectsTruncation(){
        val file=File(context.cacheDir,"${videoId()}.media");val b=ByteBuffer.allocate(44+96000).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36+96000).put("WAVEfmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(48000).putInt(96000).putShort(2).putShort(16).put("data".toByteArray()).putInt(96000)
        file.writeBytes(b.array());assertEquals(1000L,videoAudioDuration(file));file.writeBytes(b.array().copyOf(100));assertThrows(IllegalArgumentException::class.java){videoAudioDuration(file)}
    }
    @Test fun realMp3ImportsNormalizeToProbeablePcmWithoutChangingTheSource(){
        for(name in listOf("vbr-stereo.mp3","mpeg2-mono.mp3","mpeg25-mono.mp3")){
            val source=File(File(requireNotNull(System.getProperty("onklav.project.root")),"docs/fixtures/mp3"),name);val bytes=source.readBytes();val wave=File(context.cacheDir,"${videoId()}.media")
            assertTrue(Mp3Codec.isMp3(source));val info=Mp3Codec.decode(source,wave);assertEquals(info.frames*1000/info.rate,videoAudioDuration(wave));assertTrue(info.frames>0);assertArrayEquals(bytes,source.readBytes());wave.delete()
        }
    }
    @Test fun m4aProbeFallsBackWhenExtractorDurationIsZero(){
        val file=File(context.cacheDir,"${videoId()}.media").apply{writeBytes(ByteArray(16))}
        val source=org.robolectric.shadows.util.DataSource.toDataSource(file.path)
        val format=android.media.MediaFormat.createAudioFormat("audio/mp4a-latm",48000,2).apply{setLong(android.media.MediaFormat.KEY_DURATION,0)}
        org.robolectric.shadows.ShadowMediaExtractor.addTrack(source,format,ByteArray(32))
        org.robolectric.shadows.ShadowMediaMetadataRetriever.addMetadata(file.path,android.media.MediaMetadataRetriever.METADATA_KEY_DURATION,"1234")
        assertEquals(1234L,videoAudioDuration(file))
        format.setLong(android.media.MediaFormat.KEY_DURATION,2345000);assertEquals(2345L,videoAudioDuration(file))
    }
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @Test fun mixedAudioUsesOriginalSourceTrimSpeedAndTimelineOffset(){
        val c=clip();val track=VideoMusic(file="${videoId()}.media",name="Music.m4a",sourceMs=20000,start=2000,end=18000,speed=2f,offset=7000)
        val p=VideoProject(clips=listOf(c),music=listOf(track));val folder=File(context.cacheDir,videoId())
        for(preview in listOf(false,true)){
            val comp=VideoComposition.build(p,folder,preview);val audio=comp.sequences.last().editedMediaItems
            assertEquals(2,audio.size);assertEquals(7000000L,audio.first().durationUs)
            val item=audio.last();assertEquals(2000L,item.mediaItem.clippingConfiguration.startPositionMs);assertEquals(8000L,item.mediaItem.clippingConfiguration.endPositionMs)
            assertTrue(item.removeVideo);assertTrue(item.mediaItem.localConfiguration!!.uri.toString().endsWith(track.file));assertTrue(item.effects.audioProcessors.size>=2)
        }
    }

}
