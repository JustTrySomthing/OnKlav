package app.onklav.home

import android.app.Application
import android.graphics.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import app.onklav.shared.OnKlavTheme
import app.onklav.shared.AppearanceSpec
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],qualifiers="w360dp-h720dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VideoDailyReviewTest {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 private val app get()=ApplicationProvider.getApplicationContext<Application>()
 private fun clip()=VideoClip(file="${videoId()}.media",name="Daylight.jpg",sourceMs=10000,image=true,hasAudio=false)
 private fun pump(){org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50))}
 private fun waitFor(m:VideoEditorModel){compose.waitUntil(15000){pump();!m.busy}}
 private fun fixture():VideoEditorModel{runBlocking{AppSdk.bootstrap(app)};val store=VideoStore(app);val c=clip();val p=VideoProject(name="Daily edit",clips=listOf(c,c.copy(id=videoId())),ratio="9:16");val b=Bitmap.createBitmap(120,200,Bitmap.Config.ARGB_8888);b.eraseColor(Color.rgb(40,110,160));File(store.folder(p.id),c.file).outputStream().use{b.compress(Bitmap.CompressFormat.JPEG,90,it)};b.recycle();store.save(p);val model=VideoEditorModel(app);waitFor(model);return model}
 private fun capture(name:String){compose.waitForIdle();compose.runOnUiThread{val v=compose.activity.window.decorView;val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(Canvas(b));File("/workspace/scratch/c739b0c1372b",name).outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
 @Test fun trimAcrossSpeedChangesUsesTheIntegratedSourceTimeAndAllowsExtension(){
  val c=clip().copy(image=false,start=1000,end=9000,rates=listOf(VideoRate(2000,2f),VideoRate(6000,.5f)))
  assertEquals(4000L,videoTrimSource(c,true,2000))
  assertEquals(7500L,videoTrimSource(c,false,-3000))
  assertEquals(10000L,videoTrimSource(c,false,90000));assertEquals(0L,videoTrimSource(c,true,-90000))
  assertEquals(8900L,videoTrimSource(c,true,90000));assertEquals(1100L,videoTrimSource(c,false,-90000))
 }
 @Test fun trimmingKeepsMotionAtTheCutAndAudioFadesStayWithinTheNewDuration(){
  val c=clip().copy(keyframes=listOf(VideoKeyframe(0,zoom=1f,easing="Smooth"),VideoKeyframe(10000,zoom=2f)))
  val trimmed=videoClampClip(c.copy(end=5000));assertEquals(2,trimmed.keyframes.size);assertEquals(5000L,trimmed.keyframes.last().at)
  for(time in listOf(0L,1000L,2500L,5000L))assertEquals(videoPose(c,time).zoom,videoPose(trimmed,time).zoom,.001f)
  val m=fixture();val sound=VideoMusic(file=m.clip!!.file,name="Music",sourceMs=10000,fadeIn=3000,fadeOut=3000)
  compose.runOnUiThread{m.apply(m.project.copy(music=listOf(sound)));m.music(sound.copy(end=1000));m.commit()}
  assertEquals(1000L,m.project.music.single().fadeIn);assertEquals(1000L,m.project.music.single().fadeOut)
 }
 @Test fun textLaneTapSelectsTheClipAtThatTime(){
  val m=fixture();compose.runOnUiThread{m.apply(m.project.copy(clips=m.project.clips.map{it.copy(titles=listOf(VideoTitle(text="Caption",start=0,end=3000)))}));m.seek(12000)}
  compose.activity.setContent{OnKlavTheme{VideoEditorContent(m,false)}}
  compose.onNodeWithContentDescription("Timeline").performTouchInput{click(androidx.compose.ui.geometry.Offset(width/2f,116f))}
  assertEquals(1,m.selected);assertEquals(12000L,m.cursor);compose.onNodeWithContentDescription("Done editing").assertIsDisplayed();compose.runOnUiThread{m.cancel()}
 }
 @Test fun outputSettingsKeepCanvasAndEditTimelineUnchanged(){
  val p=VideoProject(clips=listOf(clip()),ratio="9:16",fill=true)
  val output=VideoOutputSettings(720,24,4000000);val next=output.project(p)
  assertEquals(p.clips,next.clips);assertEquals(p.ratio,next.ratio);assertEquals(p.fill,next.fill);assertEquals(1080,p.height);assertEquals(720,next.height);assertEquals(720 to 1280,VideoComposition.dimensions(next));assertTrue(output.estimatedBytes(p.duration)!!>0);assertNull(VideoOutputSettings().estimatedBytes(p.duration))
 }
 @Test fun draggingDefersPreviewAndUndoPreservesUncommittedChanges(){
  val m=fixture();val original=m.project
  compose.runOnUiThread{m.edit(m.clip!!.copy(brightness=.25f))};assertTrue(m.transforming);assertTrue(m.hasUnsavedChanges);assertTrue(m.canUndo)
  compose.runOnUiThread{m.history(true)};assertEquals(original,m.project);assertFalse(m.transforming);assertFalse(m.hasUnsavedChanges);assertTrue(m.canRedo)
  compose.runOnUiThread{m.history(false)};assertEquals(.25f,m.clip!!.brightness,0f);assertEquals(m.project,VideoStore(app).current());assertFalse(m.transforming)
  compose.runOnUiThread{m.cancel()}
 }
 @Test fun failedSaveKeepsEditsVisibleBlocksExportAndSwitchAndRetries(){
  val m=fixture();val original=m.project;compose.waitUntil(15000){pump();m.thumbnails.isNotEmpty()};val directory=VideoStore(app).folder(original.id);val renamed=File(directory.parentFile,directory.name+"-hold");assertTrue(directory.renameTo(renamed));directory.writeText("blocks saving")
  compose.runOnUiThread{m.edit(m.clip!!.copy(contrast=.3f));m.commit();m.dismiss()};assertTrue(m.hasUnsavedChanges);assertTrue(m.error);assertTrue(m.status.contains("Unsaved"))
  compose.runOnUiThread{m.export(android.net.Uri.fromFile(File(app.cacheDir,"must-not-export.mp4")));m.newProject()};assertEquals(original.id,m.project.id);assertEquals(.3f,m.clip!!.contrast,0f);assertFalse(m.busy)
  directory.delete();assertTrue(renamed.renameTo(directory));compose.runOnUiThread{m.commit()};assertFalse(m.hasUnsavedChanges);assertEquals(.3f,VideoStore(app).current()!!.clips[0].contrast,0f);assertFalse(m.error)
 }
 @Test fun largeProjectBackupExplainsTheHostLimitBeforeWriting(){
  val c=clip();val p=VideoProject(clips=listOf(c));val folder=File(app.cacheDir,"backup-limit").apply{mkdirs()};val media=File(folder,c.file)
  java.io.RandomAccessFile(media,"rw").use{it.setLength(FilesBroker.MAX_FILE)}
  try{videoCheckBackupDestination(p,folder,android.net.Uri.parse("content://${app.packageName}.file-selection/selected"),app.packageName);fail("Oversized backup accepted")}catch(e:IllegalArgumentException){assertTrue(e.message!!.contains("connected phone folder"))}
  videoCheckBackupDestination(p,folder,android.net.Uri.parse("content://connected.phone/backup.zip"),app.packageName)
  media.writeText("Small backup");videoCheckBackupDestination(p,folder,android.net.Uri.parse("content://${app.packageName}.file-selection/selected"),app.packageName);folder.deleteRecursively()
 }
 @Test fun cancellingCanvasSettingsDoesNotChangeTheProject(){
  val p=VideoProject(clips=listOf(clip()));var applied:VideoProject?=null;var closed=false
  compose.activity.setContent{OnKlavTheme{VideoCanvasPanel(p,{applied=it},{closed=true})}}
  compose.onNodeWithContentDescription("9:16 canvas").performScrollTo().performClick()
  compose.onNodeWithText("Cancel").performClick();assertTrue(closed);assertNull(applied);assertEquals("16:9",p.ratio)
 }
 @Test fun exportDialogHasVisibleChoicesAndCancellingLeavesProjectIntact(){
  val p=VideoProject(clips=listOf(clip()));var applied:VideoProject?=null
  compose.activity.setContent{OnKlavTheme{VideoOutputDialog(p,true,{applied=it},{})}}
  compose.onNodeWithContentDescription("Output resolution").performClick();compose.onNodeWithText("720p").performClick()
  compose.onNodeWithContentDescription("Output frame rate").performScrollTo().performClick();compose.onNodeWithText("24 fps").performClick()
  compose.onNodeWithText("Choose save location").assertIsDisplayed().performClick();assertEquals(720,applied!!.height);assertEquals(24,applied!!.fps);assertEquals(1080,p.height)
 }
 @Test fun portraitCanvasHasTheActualProjectAspectAndInspectorCanClose(){
  val m=fixture();val light=androidx.compose.runtime.mutableStateOf(false);compose.activity.setContent{OnKlavTheme(preview=AppearanceSpec(dark=!light.value)){VideoEditorContent(m,false)}}
  compose.onNodeWithContentDescription("Project canvas 9:16").assertIsDisplayed();val bounds=compose.onNodeWithContentDescription("Project canvas 9:16").fetchSemanticsNode().boundsInRoot;assertEquals(9f/16,bounds.width/bounds.height,.01f)
  capture("video-daily-portrait.png");compose.runOnUiThread{light.value=true};capture("video-daily-light.png")
  compose.onNodeWithContentDescription("Edit tools").performClick();compose.onNodeWithContentDescription("Done editing").assertIsDisplayed().performClick();compose.runOnUiThread{m.cancel()}
 }
 @Test @Config(qualifiers="w540dp-h320dp-mdpi") fun narrowLandscapeKeepsPreviewAndToolSettingsUsable(){
  val m=fixture();compose.activity.setContent{OnKlavTheme{VideoEditorContent(m,false)}}
  compose.onNodeWithContentDescription("Edit tools").performClick();compose.onNodeWithContentDescription("Done editing").assertIsDisplayed();compose.onNodeWithText("Drag the clip edges",substring=true).performScrollTo().assertIsDisplayed();capture("video-daily-landscape.png");compose.runOnUiThread{m.cancel()}
 }
 @Test @Config(qualifiers="w360dp-h320dp-mdpi",fontScale=1.5f) fun shortLargeTextUsesTheAvailableSpaceForTheInspector(){
  val m=fixture();compose.activity.setContent{OnKlavTheme{VideoEditorContent(m,false)}}
  compose.onNodeWithContentDescription("Edit tools").performClick();compose.onNodeWithContentDescription("Done editing").assertIsDisplayed();compose.onNodeWithText("Snap to edits and beats").performScrollTo().assertIsDisplayed();capture("video-daily-short-large.png");compose.runOnUiThread{m.cancel()}
 }
}
