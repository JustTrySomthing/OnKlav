package app.onklav.home

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.onklav.shared.OnKlavTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29],qualifiers="w360dp-h720dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VideoPhoneWorkflowTest {
 @get:Rule val keyStore=EmptyKeyStoreRule()
 @get:Rule val testName=org.junit.rules.TestName()
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 private lateinit var model:VideoEditorModel
 @Before fun setup(){
  val app=compose.activity.application;runBlocking{AppSdk.bootstrap(app)}
  val store=VideoStore(app);val clip=VideoClip(file="${videoId()}.media",name="Sunset.jpg",sourceMs=5000,image=true,hasAudio=false)
  val p=VideoProject(name="Weekend story",clips=listOf(clip,clip.copy(id=videoId(),name="Ending.jpg")))
  val portrait=testName.methodName=="portraitCanvasGetsMorePreviewSpaceAndToolSelectionDoesNotShrinkIt"
  val b=Bitmap.createBitmap(if(portrait)180 else 320,if(portrait)320 else 180,Bitmap.Config.ARGB_8888)
  if(portrait){val canvas=android.graphics.Canvas(b);val paint=android.graphics.Paint().apply{shader=android.graphics.LinearGradient(0f,0f,180f,320f,android.graphics.Color.rgb(25,75,100),android.graphics.Color.rgb(65,150,100),android.graphics.Shader.TileMode.CLAMP)};canvas.drawRect(0f,0f,180f,320f,paint);paint.shader=null;paint.color=android.graphics.Color.WHITE;paint.textSize=17f;canvas.drawText("Portrait preview",22f,155f,paint)}else b.eraseColor(android.graphics.Color.rgb(30,90,150));File(store.folder(p.id),clip.file).outputStream().use{b.compress(Bitmap.CompressFormat.JPEG,90,it)};b.recycle();store.save(p)
  model=VideoEditorModel(app);compose.waitUntil(15000){shadowOf(android.os.Looper.getMainLooper()).idle();!model.busy}
  compose.activity.setContent{OnKlavTheme{VideoEditorContent(model,automaticPreview=false)}};compose.waitForIdle()
 }
 private fun open(tool:String){val group=videoToolGroups.first{tool in it.tools};compose.onNodeWithContentDescription("${group.name} tools").performClick();compose.onNodeWithContentDescription("Open ${videoToolLabel(tool)}").performScrollTo().performClick()}
 private fun done(){compose.onNodeWithContentDescription("Done editing").performClick()}
 private fun preview()=compose.onNodeWithContentDescription("Video preview").fetchSemanticsNode().boundsInRoot
 private fun capture(name:String){compose.waitForIdle();compose.runOnUiThread{val v=compose.activity.window.decorView;val image=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(android.graphics.Canvas(image));File(System.getProperty("onklav.project.root"),"docs/evidence/video-phone/$name.png").apply{parentFile!!.mkdirs()}.outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle()}}
 @Test fun selectionFeedbackAndUnavailableSettingsHaveClearRoutes(){
  val bounds=preview()
  compose.onNodeWithContentDescription("Selected item").assertTextContains("Clip 1 of 2",substring=true)
  compose.onNodeWithContentDescription("Audio tools").performClick();compose.onNodeWithContentDescription("Open Add audio").assertIsDisplayed();compose.onNodeWithText("Add clips").assertIsDisplayed();compose.onNodeWithContentDescription("Open Sound controls").performClick();compose.onNodeWithText("Extract needs a video clip with recorded sound.").assertExists()
  compose.onNodeWithText("Timing").performClick();compose.onNodeWithText("Choose audio track").performClick()
  compose.onNodeWithContentDescription("Choose audio track").assertExists();done()
  open("Overlay");compose.onNodeWithText("Position").performClick();compose.onNodeWithText("Choose overlay").performClick()
  compose.onNodeWithText("Add video or photo overlay").assertExists();done()
  open("Text");compose.onNodeWithText("Add text").performScrollTo().performClick();compose.onNodeWithText("Draft · Apply to save").assertExists()
  compose.onNodeWithText("Cancel").performClick();done()
  open("Filters");compose.onNodeWithText("Changes save automatically").assertExists()
  assertEquals(bounds.height,preview().height,.1f)
 }
 @Test fun allToolsKeepTheSameVisiblePreviewAndOpeningToolsDoesNotEditProject(){
  val before=model.project;val bounds=preview();assertTrue(bounds.height>=170f);capture("home")
  for(group in videoToolGroups){
   for(tool in group.tools){
    compose.onNodeWithContentDescription("${group.name} tools").performClick();assertEquals(bounds, preview());if(tool==group.tools.first())capture("palette-${group.name.lowercase()}");compose.onNodeWithContentDescription("Open ${videoToolLabel(tool)}").performScrollTo().performClick()
    assertEquals("$tool changed preview height",bounds.height,preview().height,.1f);assertEquals("$tool changed video",before,model.project)
    if(tool=="Canvas")compose.onNodeWithText("Cancel").performClick()else done()
   }
  }
 }
 @Test fun browsingPreviewCannotTransformAndDeleteRequiresConfirmationThenUndoWorks(){
  val before=model.project
  compose.onNodeWithContentDescription("Video preview").performTouchInput{swipe(center,center+androidx.compose.ui.geometry.Offset(50f,20f),300)}
  assertEquals(before,model.project)
  open("Edit");compose.onNodeWithText("Delete clip").performScrollTo().performClick();assertEquals(2,model.project.clips.size)
  compose.onNodeWithText("Cancel").performClick();assertEquals(before,model.project)
  compose.onNodeWithText("Delete clip").performScrollTo().performClick();compose.onNodeWithText("Confirm").performClick();assertEquals(1,model.project.clips.size)
  compose.onNodeWithContentDescription("Undo").performClick();assertEquals(before,model.project);capture("trim")
 }
 @Test fun audioAndColorPagesStayFocusedAndTextCancelIsNonDestructive(){
  val bounds=preview();open("Audio");capture("audio-tracks")
  compose.onNodeWithText("Volume").performClick();compose.onNodeWithText("Tracks").performClick();done()
  compose.onNodeWithContentDescription("Finish tools").performClick();compose.onNodeWithContentDescription("Open Color").performClick();compose.onNodeWithText("Adjust").performClick();compose.onNodeWithContentDescription("Brightness").assertExists();capture("color-adjust")
  assertEquals(bounds.height,preview().height,.1f);done()
  open("Text");val before=model.project;compose.onNodeWithText("Add text").performScrollTo().performClick();compose.onNodeWithText("Title or caption").performTextReplacement("A draft caption");capture("text-draft")
  assertEquals(bounds.height,preview().height,.1f);compose.onNodeWithText("Cancel").performClick();assertEquals(before,model.project)
  compose.onNodeWithText("Add text").performScrollTo().performClick();compose.onNodeWithText("Title or caption").performTextReplacement("Our weekend");compose.onNodeWithText("Apply").performClick();assertEquals("Our weekend",model.clip!!.titles.single().text)
 }
 @Test fun sliderChangesAndTimelineBrowsingNeverResizeOrAccidentallyTrim(){
  val before=model.project;val bounds=preview()
  compose.onNodeWithContentDescription("Timeline").performTouchInput{swipe(androidx.compose.ui.geometry.Offset(center.x,55f),androidx.compose.ui.geometry.Offset(center.x+35f,55f),300)}
  assertEquals(before,model.project)
  open("Filters");compose.onNodeWithText("Adjust").performClick();compose.onNodeWithContentDescription("Brightness").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress){it(.2f)}
  assertEquals(.2f,model.clip!!.brightness,.001f);assertEquals(bounds.height,preview().height,.1f)
  compose.runOnUiThread{model.edit(model.clip!!.copy(brightness=.3f))}
  assertTrue(model.hasUnsavedChanges);assertEquals(bounds.height,preview().height,.1f)
  compose.runOnUiThread{model.commit()};assertEquals(bounds.height,preview().height,.1f);compose.onNodeWithContentDescription("Undo").performClick();compose.onNodeWithContentDescription("Undo").performClick();assertEquals(before,model.project)
 }
 @Test fun addedAudioOverlayAndMotionSubpanelsAreReachableWithoutLosingThePreview(){
  val track=VideoMusic(file=model.clip!!.file,name="Theme audio",sourceMs=5000)
  val layer=VideoLayer(clip=model.clip!!.copy(id=videoId(),name="Picture in picture"))
  compose.runOnUiThread{model.apply(model.project.copy(music=listOf(track),layers=listOf(layer)));model.selectAudio(track.id)}
  val bounds=preview();open("Audio")
  for(page in listOf("Volume","Timing","Fades","Sound","Tracks")){compose.onNodeWithContentDescription("$page settings").performScrollTo().performClick();assertEquals(bounds.height,preview().height,.1f)}
  capture("audio-track-settings");done();open("Overlay")
  compose.onNodeWithContentDescription("Choose overlay").performClick();compose.onNodeWithText("Picture in picture").performClick()
  for(page in listOf("Position","Timing","Key","Mask","Layers")){compose.onNodeWithContentDescription("$page settings").performScrollTo().performClick();assertEquals(bounds.height,preview().height,.1f)}
  capture("overlay-settings");done();open("Keyframes")
  for(page in listOf("Zoom","Position","Rotation","Opacity","Easing","Points")){compose.onNodeWithContentDescription("$page settings").performScrollTo().performClick();assertEquals(bounds.height,preview().height,.1f)}
  capture("motion-points");done()
 }
 @Test @Config(qualifiers="w360dp-h800dp-mdpi") fun portraitCanvasGetsMorePreviewSpaceAndToolSelectionDoesNotShrinkIt(){
  val wide=preview()
  val wideCanvas=compose.onNodeWithContentDescription("Project canvas 16:9").fetchSemanticsNode().boundsInRoot
  assertEquals(wide.width,wideCanvas.width,.5f)
  compose.runOnUiThread{model.apply(model.project.copy(ratio="9:16"))}
  val bounds=preview();assertTrue("Portrait preview must reclaim the idle palette space",bounds.height>=480f);assertEquals(wide.height,bounds.height,.5f)
  val portraitCanvas=compose.onNodeWithContentDescription("Project canvas 9:16").fetchSemanticsNode().boundsInRoot
  assertTrue(portraitCanvas.height>wideCanvas.height*1.25f);assertEquals(bounds.height,portraitCanvas.height,.5f);assertEquals(9f/16f,portraitCanvas.width/portraitCanvas.height,.01f);
  compose.onNodeWithText("Quick tools").assertDoesNotExist();compose.onNodeWithContentDescription("Open Trim & split").assertDoesNotExist();capture("portrait-home");
  open("Overlay");assertEquals(bounds.height,preview().height,.1f);capture("portrait-video");done()
 }
 @Test @Config(qualifiers="w320dp-h568dp-mdpi",fontScale=1.4f) fun smallPhoneLargeTextKeepsPreviewDoneAndSettingsReachable(){
  val bounds=preview();assertTrue(bounds.height>=120f);open("Transform");compose.onNodeWithText("Rotate").performClick();compose.onNodeWithText("Flip vertical").performScrollTo().assertIsDisplayed();compose.onNodeWithContentDescription("Done editing").assertIsDisplayed();assertEquals(bounds.height,preview().height,.1f);capture("small-large-text");done()
 }
 @Test @Config(qualifiers="w720dp-h360dp-mdpi") fun landscapeUsesSideInspectorWithoutMovingTheVideo(){
  val bounds=preview();open("Filters");compose.onNodeWithText("Adjust").performClick();compose.onNodeWithContentDescription("Brightness").assertIsDisplayed();assertEquals(bounds.height,preview().height,.1f);capture("landscape");done()
 }
 @Test @Config(qualifiers="w360dp-h320dp-mdpi",fontScale=1.5f) fun veryShortWindowKeepsToolsAndTimelineReachable(){
  val bounds=preview();val original=model.project
  compose.onNodeWithContentDescription("Open timeline").assertIsDisplayed().performClick()
  compose.onNodeWithContentDescription("Timeline").assertIsDisplayed()
  compose.onNodeWithContentDescription("Close Timeline").performClick()
  open("Filters");compose.onNodeWithText("Adjust").performClick()
  compose.onNodeWithContentDescription("Brightness").performScrollTo().assertIsDisplayed()
  compose.onNodeWithContentDescription("Done editing").assertIsDisplayed();assertEquals(bounds.height,preview().height,.1f)
  done();assertEquals(original,model.project);capture("short-window")
 }
 @Test @Config(qualifiers="w540dp-h320dp-mdpi") fun narrowLandscapeUsesSideInspectorAndKeepsControlsVisible(){
  val bounds=preview();open("Transform")
  compose.onNodeWithText("Rotate").performClick();compose.onNodeWithText("Flip vertical").performScrollTo().assertIsDisplayed()
  compose.onNodeWithContentDescription("Done editing").assertIsDisplayed();assertEquals(bounds.height,preview().height,.1f)
  capture("narrow-landscape");done()
 }

 @Test fun homeHasOnlyFourCategoryButtonsAndMenusUseDistinctIcons(){
  compose.onNodeWithText("Quick tools").assertDoesNotExist()
  compose.onNodeWithContentDescription("Open Trim & split").assertDoesNotExist()
  videoToolGroups.forEach{group->
   compose.onAllNodesWithContentDescription("${group.name} tools").assertCountEquals(1)
   val icons=group.tools.map(::videoToolIcon);assertEquals("Duplicate icons in ${group.name}",icons.size,icons.distinct().size)
  }
 }
 @Test fun inspectorKeepsTheTimelineReachableThroughMore(){
  open("Edit");compose.onNodeWithContentDescription("More video options").performClick()
  compose.onNodeWithText("Timeline").performClick();compose.onNodeWithContentDescription("Timeline").assertIsDisplayed()
  compose.onNodeWithContentDescription("Close Timeline").performClick();compose.onNodeWithContentDescription("Done editing").assertIsDisplayed();done()
 }

}
