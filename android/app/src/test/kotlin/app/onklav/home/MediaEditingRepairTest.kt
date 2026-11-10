@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package app.onklav.home

import android.app.Application
import android.media.MediaFormat
import android.net.Uri
import androidx.media3.common.C
import androidx.test.core.app.ApplicationProvider
import app.onklav.shared.Mp3Codec
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowMediaExtractor
import org.robolectric.shadows.util.DataSource
import java.io.File
import kotlin.math.*

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29]) @LooperMode(LooperMode.Mode.PAUSED)
class MediaEditingRepairTest{
 private val app get()=ApplicationProvider.getApplicationContext<Application>()
 private fun wave(file:File,rate:Int=44100,channels:Int=2){file.outputStream().buffered().use{out->AudioEditEngine.writeWaveHeader(out,rate,channels,rate.toLong());repeat(rate){i->repeat(channels){val n=(sin(2*PI*440*i/rate)*12000).toInt();out.write(n and 255);out.write((n shr 8)and 255)}}}}
 private fun waitFor(test:()->Boolean){val end=System.nanoTime()+15_000_000_000;while(System.nanoTime()<end){org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(30));if(test())return;Thread.sleep(10)};assertTrue("Model operation completed",test())}
 @Test fun bundledMp3ImportKeepsARealAudioTrackWithoutVendorHasAudioTags(){
  runBlocking{AppSdk.bootstrap(app)}
  val folder=File(app.cacheDir,"mp3-video-import").apply{mkdirs()};val wav=File(folder,"tone.wav");wave(wav)
  val mp3=File(folder,"tone.mp3");val started=System.nanoTime();Mp3Codec.encode(wav,mp3)
  val encodedSha=java.security.MessageDigest.getInstance("SHA-256").digest(mp3.readBytes()).joinToString(""){"%02x".format(it)}
  println("MP3_FIXTURE_SHA256=$encodedSha encode_ms=${(System.nanoTime()-started)/1_000_000}")
  val model=VideoEditorModel(app);waitFor{!model.busy}
  val clip=VideoClip(file="${videoId()}.media",name="Video",sourceMs=8000);val project=VideoProject(clips=listOf(clip));File(model.store.folder(project.id),clip.file).writeText("source-video-fixture");model.apply(project)
  val provider=Mp3IntegrationTest.FixtureProvider().apply{file=mp3;attachInfo(app,android.content.pm.ProviderInfo().apply{authority="test.video.mp3";exported=false;grantUriPermissions=true})}
  org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("test.video.mp3",provider)
  model.import(listOf(Uri.parse("content://test.video.mp3/tone")),audio=true);waitFor{!model.busy}
  assertFalse(model.status,model.error);assertEquals(1,model.project.music.size)
  val track=model.project.music.single();val imported=File(model.store.folder(project.id),track.file)
  assertTrue(track.sourceMs in 950..1100);assertEquals(track.sourceMs,videoAudioDuration(imported));assertTrue(mp3.isFile)
  val comp=VideoComposition.build(model.project,model.store.folder(project.id),true,true,audioOnly=true)
  assertTrue(comp.sequences.all{it.trackTypes==setOf(C.TRACK_TYPE_AUDIO)})
  assertTrue(comp.effects.videoEffects.isEmpty());assertTrue(comp.sequences.flatMap{it.editedMediaItems}.all{(it.effects.audioProcessors.isEmpty()||it.removeVideo)&&it.effects.videoEffects.isEmpty()})
  model.cancel()
 }
 @Test fun wavDurationIsExactAndTruncationCannotBeImported(){
  val f=File(app.cacheDir,"probe.media");wave(f,48000,1);assertEquals(1000L,videoAudioDuration(f))
  java.io.RandomAccessFile(f,"rw").use{it.setLength(f.length()-2)};assertThrows(IllegalArgumentException::class.java){videoAudioDuration(f)}
 }
 @Test fun m4aUsesActualAudioTrackAndDurationWithoutOptionalRetrieverFlags(){
  val f=File(app.cacheDir,"aac.media").apply{writeText("extractor-fixture")}
  val fmt=MediaFormat.createAudioFormat("audio/mp4a-latm",48000,2).apply{setLong(MediaFormat.KEY_DURATION,8_100_000)}
  ShadowMediaExtractor.addTrack(DataSource.toDataSource(f.path),fmt,ByteArray(256))
  assertEquals(8100L,videoAudioDuration(f))
 }
 @Test fun soundtrackMixPreservesOffsetsFadesAndSilenceWithoutAnyVideoWork(){
  val c=VideoClip(file="${videoId()}.media",name="Video",sourceMs=8000)
  val music=VideoMusic(file="${videoId()}.media",name="Music.m4a",sourceMs=12000,start=1000,end=10000,offset=500,fadeIn=400,pitch=2f)
  val p=VideoProject(clips=listOf(c),music=listOf(music));assertFalse(VideoComposition.directPreview(p));assertTrue(VideoComposition.directVisualPreview(p))
  val mix=VideoComposition.build(p,app.cacheDir,true,true,audioOnly=true)
  assertEquals(2,mix.sequences.size);assertTrue(mix.sequences.all{it.trackTypes==setOf(C.TRACK_TYPE_AUDIO)})
  mix.sequences.forEach{seq->assertEquals(8_000_000L,seq.editedMediaItems.sumOf{item->(item.javaClass.getDeclaredMethod("getPresentationDurationUs").apply{isAccessible=true}.invoke(item)as Long)});assertTrue(seq.editedMediaItems.all{it.effects.videoEffects.isEmpty()})}
  val item=mix.sequences[1].editedMediaItems.first{it.effects.audioProcessors.any{p->p is VideoGain}};assertEquals(1000L,item.mediaItem.clippingConfiguration.startPositionMs);assertEquals(8500L,item.mediaItem.clippingConfiguration.endPositionMs)
  assertTrue(item.effects.audioProcessors.any{it is VideoGain});assertTrue(item.effects.audioProcessors.any{it is androidx.media3.common.audio.SonicAudioProcessor})
  assertFalse(VideoComposition.directVisualPreview(p.copy(clips=listOf(c.copy(brightness=.2f)))))
 }
 @Test fun originalYoutubeAudioIsByteExactAndMp3IsAnExplicitDifferentOutput(){
  val input=File(app.cacheDir,"native-source").apply{writeBytes(ByteArray(400000){(it%251).toByte()})};val out=File(app.cacheDir,"native-copy.m4a")
  val stream=YoutubeStream(140,"https://r1.googlevideo.com/videoplayback","audio/mp4",0,true)
  assertEquals(YoutubeAudioFormat("m4a","audio/mp4"),youtubeAudioFormat(stream,false));assertEquals(YoutubeAudioFormat("mp3","audio/mpeg"),youtubeAudioFormat(stream,true))
  assertEquals(YoutubeAudioFormat("webm","audio/webm"),youtubeAudioFormat(stream.copy(mime="audio/webm"),false))
  youtubeOriginalAudio(input,out,stream){};assertArrayEquals(input.readBytes(),out.readBytes())
  var checks=0;assertThrows(IllegalStateException::class.java){youtubeOriginalAudio(input,out,stream){if(++checks>2)error("Cancelled")}};assertFalse(out.exists());assertTrue(input.exists())
 }
}
