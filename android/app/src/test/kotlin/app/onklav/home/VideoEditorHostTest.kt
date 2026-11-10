@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package app.onklav.home
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class VideoEditorHostTest{
 @Test fun videoTransferChecksSizeAndReportsTheCompletedByteCount() = runBlocking {
    val source = ByteArray(300_000) { it.toByte() }
    val output = ByteArrayOutputStream()
    val progress = mutableListOf<Long>()
    copyVideoMedia(source.inputStream(), output, checkAccess = {}, reportProgress = progress::add)
    assertArrayEquals(source, output.toByteArray())
    assertEquals(source.size.toLong(), progress.last())
    val limited = ByteArrayOutputStream()
    val failure = runCatching { copyVideoMedia(source.inputStream(), limited, 32L, checkAccess = {}) }
    assertTrue(failure.exceptionOrNull() is IllegalArgumentException)
    assertEquals(0, limited.size())
 }

 @Test fun canceledVideoReadDoesNotWriteTheReturnedChunk() = runBlocking {
    val output = ByteArrayOutputStream()
    var didRead = false
    val transfer = launch {
        val owner = coroutineContext.job
        val source = object : InputStream() {
            override fun read(): Int = error("Expected a buffered read")
            override fun read(bytes: ByteArray): Int {
                didRead = true
                owner.cancel()
                bytes[0] = 1
                return 1
            }
        }
        copyVideoMedia(source, output, checkAccess = {})
    }
    transfer.join()
    assertTrue(didRead)
    assertTrue(transfer.isCancelled)
    assertEquals(0, output.size())
 }

 @Test fun previewAtEndKeepsLastFrameUntilPlaybackIsRequested() {
    assertEquals(3999L, videoPreviewPosition(4000L, 4000L, false))
    assertEquals(0L, videoPreviewPosition(4000L, 4000L, true))
    assertEquals(1750L, videoPreviewPosition(1750L, 4000L, false))
    assertEquals(1750L, videoPreviewPosition(1750L, 4000L, true))
    assertEquals(0L, videoPreviewPosition(0L, 0L, false))
 }

 private val context get()=ApplicationProvider.getApplicationContext<Context>()
 private fun clip()=VideoClip(file="${videoId()}.media",name="Source.mp4",sourceMs=10000,start=1000,end=9000,speed=2f,titles=listOf(VideoTitle(text="A title",start=1000,end=3500)))
 @Test fun splitKeepsSourceTimingAndDividesCaptionRanges(){val c=clip();val p=VideoProject(clips=listOf(c));assertEquals(4000L,p.duration);val split=p.split(0,2000);assertEquals(2,split.clips.size);assertEquals(5000L,split.clips[0].end);assertEquals(5000L,split.clips[1].start);assertEquals(p.duration,split.duration);assertEquals(1000L,split.clips[0].titles.single().start);assertEquals(2000L,split.clips[0].titles.single().end);assertEquals(0L,split.clips[1].titles.single().start);assertEquals(1500L,split.clips[1].titles.single().end);assertNotEquals(split.clips[0].id,split.clips[1].id);assertThrows(IllegalArgumentException::class.java){p.split(0,1)};assertEquals(split,VideoProject.parse(split.json()))}
 @Test fun projectStoreRestoresOriginalCopiesAndRejectsUnsafePaths(){val store=VideoStore(context);val c=clip();val p=VideoProject(name="My trip",clips=listOf(c));val media=File(store.folder(p.id),c.file);media.writeBytes(byteArrayOf(1,2,3));store.save(p);assertEquals(p,store.current());val changed=p.copy(name="A new name");store.save(changed);assertEquals(changed,store.load(p.id));assertArrayEquals(byteArrayOf(1,2,3),media.readBytes());assertTrue(store.list().any{it.name==changed.name});val j=c.json().put("file","../../outside");assertThrows(IllegalArgumentException::class.java){VideoClip.parse(j)};val outside=File(context.filesDir,"keep-me").apply{writeText("Original")};store.delete(p.id);assertTrue(outside.exists());assertFalse(media.exists())}
 @Test fun pcmGainAndFadesUseRealSamplesAndProtectPeaks(){val g=VideoGain(2f,1000,200,200);g.configure(AudioProcessor.AudioFormat(1000,1,C.ENCODING_PCM_16BIT));g.flush();val input=ByteBuffer.allocateDirect(2000).order(ByteOrder.nativeOrder());repeat(1000){input.putShort(20000)};input.flip();g.queueInput(input);val output=g.output.order(ByteOrder.nativeOrder());val samples=ShortArray(output.remaining()/2){output.short};assertEquals(0,samples[0].toInt());assertEquals(20000,samples[100].toInt());assertEquals(32767,samples[500].toInt());assertEquals(200,samples[999].toInt());assertEquals(0f,videoEnvelope(0,1000,200,200),0f);assertEquals(.5f,videoEnvelope(900,1000,200,200),.001f)}
 @Test fun compositionCarriesClipsTextSpeedAndMixedSound(){val c=clip().copy(rotate=90,brightness=.1f,fadeIn=200,fadeOut=200,motion=true,endZoom=1.3f);val p=VideoProject(clips=listOf(c,c.copy(id=videoId())),music=listOf(VideoMusic(file="${videoId()}.media",name="Music",sourceMs=12000,offset=500)),ratio="9:16",height=720);val comp=VideoComposition.build(p,File(context.filesDir,"test"));assertEquals(2,comp.sequences.size);assertEquals(2,comp.sequences[0].editedMediaItems.size);val item=comp.sequences[0].editedMediaItems[0];assertEquals(1000L,item.mediaItem.clippingConfiguration.startPositionMs);assertEquals(9000L,item.mediaItem.clippingConfiguration.endPositionMs);assertTrue(item.effects.videoEffects.size>=6);assertTrue(comp.effects.videoEffects.any{it is androidx.media3.effect.OverlayEffect});assertEquals(2,item.effects.audioProcessors.size);assertEquals(2,comp.sequences[1].editedMediaItems.size);assertEquals(setOf(C.TRACK_TYPE_AUDIO),comp.sequences[1].trackTypes);assertEquals(720 to 1280,VideoComposition.dimensions(p));assertEquals(p,VideoProject.parse(p.json()))}
 @Test fun bundledSignedAppLaunchesAndHonorsRevocation(){val c=context;runBlocking{AppSdk.bootstrap(c)};val result=AppSdk.sync(c,JSONObject().put("action","launch").put("id","app.onklav.videoeditor")) as JSONObject;assertEquals("Video Editor",result.getJSONObject("frame").getString("title"));assertTrue(videoEditorAccess(c));assertTrue(videoEditorAccess(c,"audio.playback"));AppSdk.sync(c,JSONObject().put("action","permissions").put("id","app.onklav.videoeditor").put("grants",JSONArray()));assertFalse(videoEditorAccess(c));assertFalse(videoEditorAccess(c,"audio.playback"))}
 @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
 @Test fun longCaptionsWrapInsideBoundedBitmapTextures(){val b=videoTitleBitmap(VideoTitle(text="A long caption with many words ".repeat(25),size=2f),720,1280);assertTrue(b.width<=720);assertTrue(b.height<=1024);val pixels=IntArray(b.width*b.height);b.getPixels(pixels,0,b.width,0,0,b.width,b.height);assertTrue(pixels.any{android.graphics.Color.alpha(it)>0});b.recycle()}

}
