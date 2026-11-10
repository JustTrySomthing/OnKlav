@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package app.onklav.home

import androidx.compose.ui.geometry.Offset
import androidx.media3.common.PlaybackException
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class DevicePolishTest {
 @Test fun cameraFrameFitsWholeChosenRatioInEitherOrientation(){
  for(landscape in listOf(false,true))for(r in listOf("4:3","16:9","1:1")){
   val aspect=cameraFrameAspect(r,landscape);val (w,h)=cameraFrameSize(if(landscape)760f else 400f,if(landscape)330f else 610f,aspect)
   assertEquals(aspect,w/h,.0001f);assertTrue(w<=(if(landscape)760f else 400f));assertTrue(h<=(if(landscape)330f else 610f))
  }
  assertEquals(.75f,cameraFrameAspect("4:3",false),0f);assertEquals(16f/9,cameraFrameAspect("16:9",true),0f)
 }
 @Test fun runtimeAndFrameGraphFailuresUseCompatibilityButNetworkErrorsDoNot(){
  assertTrue(videoNeedsCompatiblePreview(PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK))
  assertTrue(videoNeedsCompatiblePreview(PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED))
  assertTrue(videoNeedsCompatiblePreview(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED))
  assertFalse(videoNeedsCompatiblePreview(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND))
  assertFalse(videoNeedsCompatiblePreview(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))
 }
 @Test fun previewTapJitterDoesNotBecomeAnEditButIntentionalDragAndPinchDo(){
  assertFalse(videoPreviewPastSlop(Offset(1f,2f),1.002f,200f,8f))
  assertFalse(videoPreviewPastSlop(Offset.Zero,1f,200f,8f))
  assertTrue(videoPreviewPastSlop(Offset(10f,0f),1f,200f,8f))
  assertTrue(videoPreviewPastSlop(Offset.Zero,1.1f,200f,8f))
 }
}
