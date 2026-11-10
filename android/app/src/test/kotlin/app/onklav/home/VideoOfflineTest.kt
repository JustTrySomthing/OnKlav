@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package app.onklav.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.*

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class VideoOfflineTest {
    private fun clip(ms:Long=4000)=VideoClip(file="${videoId()}.media",name="Trip.mp4",sourceMs=ms)
    @Test fun easingCutsKeepTheSameTrajectoryAcrossBothHalves(){
        for(easing in videoEasings){val c=clip().copy(keyframes=listOf(VideoKeyframe(0,easing=easing),VideoKeyframe(4000,2f,.5f,-.5f,90f,.3f)))
            val p=VideoProject(clips=listOf(c)).split(0,1500);val restored=VideoProject.parse(p.json())
            for(at in 0L..4000L step 100){val actual=if(at<=1500)videoPose(restored.clips[0],at)else videoPose(restored.clips[1],at-1500);val expected=videoPose(c,at);assertEquals("$easing at $at",expected.zoom,actual.zoom,.0001f);assertEquals(expected.rotation,actual.rotation,.001f);assertEquals(expected.opacity,actual.opacity,.0001f)}
        }
        assertEquals(.15625f,videoEase("Smooth",.25f),.0001f)
    }
    @Test fun oldJsonDefaultsAndOfflineSettingsRoundTrip(){
        val c=clip();val voice=VideoMusic(file=c.file,name="Voice",sourceMs=4000,narration=true,pan=-.2f,tone="Speech clarity")
        val p=VideoProject(clips=listOf(c.copy(titles=listOf(VideoTitle(entrance="Slide",exit="Pop")))) ,music=listOf(voice,voice.copy(id=videoId(),narration=false,duck=true,tone="Warm")))
        assertEquals(p,VideoProject.parse(p.json()));val old=voice.json();listOf("narration","duck","pan","tone").forEach{old.remove(it)};val restored=VideoMusic.parse(old);assertFalse(restored.narration);assertFalse(restored.duck);assertEquals("Original",restored.tone)
        assertThrows(IllegalArgumentException::class.java){VideoMusic.parse(voice.json().put("pan","NaN"))}
        assertThrows(IllegalArgumentException::class.java){VideoClip.parse(c.copy(titles=listOf(VideoTitle(entrance="Fake AI"))).json())}
        val comp=VideoComposition.build(p,File("/tmp/fixture"));assertEquals(3,comp.sequences.size);assertTrue(comp.sequences[2].editedMediaItems.last().effects.audioProcessors.any{it is VideoSound})
    }
    @Test fun duckingAttackAndReleaseAreSmoothAndOverlapDoesNotStack(){
        val ranges=listOf(1000L to 2000L,1500L to 2500L)
        assertEquals(1f,videoDuckGain(700,ranges),0f);assertEquals(.625f,videoDuckGain(900,ranges),.001f);assertEquals(.25f,videoDuckGain(1000,ranges),0f);assertEquals(.25f,videoDuckGain(1750,ranges),0f);assertEquals(.625f,videoDuckGain(2700,ranges),.001f);assertEquals(1f,videoDuckGain(3000,ranges),0f)
    }
    private fun samples(processor:VideoSound,values:ShortArray):ShortArray{val input=ByteBuffer.allocateDirect(values.size*2).order(ByteOrder.nativeOrder());values.forEach{input.putShort(it)};input.flip();processor.queueInput(input);val out=processor.output.order(ByteOrder.nativeOrder());return ShortArray(out.remaining()/2){out.short}}
    @Test fun audioActuallyBalancesDucksAndPreservesChunkState(){
        val p=VideoSound(pan=1f,offset=1000,duckRanges=listOf(1000L to 2000L));p.configure(AudioProcessor.AudioFormat(44100,2,C.ENCODING_PCM_16BIT));p.flush();val out=samples(p,shortArrayOf(12000,12000,-12000,-12000));assertArrayEquals(shortArrayOf(0,3000,0,-3000),out)
        fun processor()=VideoSound(tone="Telephone").apply{configure(AudioProcessor.AudioFormat(44100,1,C.ENCODING_PCM_16BIT));flush()}
        val source=ShortArray(500){if(it<200)12000 else -8000};val all=samples(processor(),source);val chunked=processor();assertArrayEquals(all,samples(chunked,source.take(137).toShortArray())+samples(chunked,source.drop(137).toShortArray()));assertTrue(all.last().toInt() in -100..100);assertFalse(all.contentEquals(source));chunked.flush();assertArrayEquals(all,samples(chunked,source))
    }
    @Test fun titleAnimationTimingSurvivesSplit(){
        val title=VideoTitle(start=0,end=4000,entrance="Slide",exit="Pop");val c=clip().copy(titles=listOf(title));val p=VideoProject(clips=listOf(c)).split(0,200)
        assertEquals(0f,videoTitlePose(title,0).alpha,0f);assertTrue(videoTitlePose(title,200).y>0);assertEquals(1f,videoTitlePose(title,1000).alpha,0f)
        for(at in 0L..3999L step 17){val q=if(at<200)videoTitlePose(p.clips[0].titles.single(),at)else videoTitlePose(p.clips[1].titles.single(),at-200);assertEquals(videoTitlePose(title,at),q)}
    }
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test fun animatedTitleRendersAndSettlesToCachedTexture(){
        val overlay=VideoTitleOverlay(listOf(VideoTitle(text="Local title",start=0,end=3000,entrance="Pop",exit="Slide")),360,640)
        assertEquals(0f,overlay.getOverlaySettings(0).alphaScale,0f)
        val early=overlay.getBitmap(100_000);assertTrue(early.width>1);val pixels=IntArray(early.width*early.height);early.getPixels(pixels,0,early.width,0,0,early.width,early.height);assertTrue(pixels.any{android.graphics.Color.alpha(it)>0})
        val settled=overlay.getBitmap(700_000);assertSame(settled,overlay.getBitmap(900_000));assertNotSame(settled,overlay.getBitmap(2_800_000));overlay.release()
    }
    @Test fun portableArchiveRestoresActualMediaWithNewIdentity(){
        val ctx=ApplicationProvider.getApplicationContext<Context>();val store=VideoStore(ctx);val c=clip();val overlay=clip(1000);val p=VideoProject(clips=listOf(c),music=listOf(VideoMusic(file=c.file,name="Extracted",sourceMs=c.sourceMs)),layers=listOf(VideoLayer(clip=overlay,offset=500)))
        File(store.folder(p.id),c.file).writeBytes(ByteArray(321){it.toByte()});File(store.folder(p.id),overlay.file).writeText("overlay")
        val out=ByteArrayOutputStream();VideoProjectArchive.write(p,store.folder(p.id),out);val restored=VideoProjectArchive.read(ByteArrayInputStream(out.toByteArray()),store.root)
        assertNotEquals(p.id,restored.id);assertEquals(p.copy(id=restored.id),restored);assertArrayEquals(File(store.folder(p.id),c.file).readBytes(),File(store.folder(restored.id),c.file).readBytes());store.save(restored);assertEquals(restored,store.load(restored.id));assertTrue(File(store.folder(p.id),c.file).exists())
    }
    @Test fun badOrCancelledArchiveNeverPublishesPartialMedia(){
        val ctx=ApplicationProvider.getApplicationContext<Context>();val root=File(ctx.cacheDir,videoId()).apply{mkdirs()};val c=clip();val p=VideoProject(clips=listOf(c))
        fun archive(entries:List<Pair<String,ByteArray>>):ByteArray=ByteArrayOutputStream().also{out->ZipOutputStream(out).use{zip->entries.forEach{(name,bytes)->zip.putNextEntry(ZipEntry(name));zip.write(bytes);zip.closeEntry()}}}.toByteArray()
        val manifest="project.json" to p.json().toString().toByteArray()
        for(bytes in listOf(archive(listOf(manifest)),archive(listOf(manifest,"../escape" to byteArrayOf(1))),archive(listOf(manifest,"media/${c.file}" to byteArrayOf())))){
            assertThrows(IllegalArgumentException::class.java){VideoProjectArchive.read(ByteArrayInputStream(bytes),root)};assertTrue(root.listFiles().orEmpty().isEmpty())
        }
        val valid=archive(listOf(manifest,"media/${c.file}" to ByteArray(500000)))
        var calls=0;assertThrows(java.util.concurrent.CancellationException::class.java){VideoProjectArchive.read(ByteArrayInputStream(valid),root){if(++calls==5)throw java.util.concurrent.CancellationException()}};assertTrue(root.listFiles().orEmpty().isEmpty())
    }
}
