package app.onklav.home
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class PhotoHdrLocalTest {
 @Test fun layersKeepMarksGeometryAndHistoryWhileRetiringHdr(){
  val json="""{"format":"OnKlav Layers","version":1,"edit":{"angle":23,"hdr":0.8,"colorMode":2},"undo":[{"hdrStyle":4,"hdr":1}],"redo":[{"noir":0.6}]}"""
  val state=PhotoLayers.parse(json);assertEquals(23f,state.edit["angle"],0f);assertEquals("sepia",PhotoLooks.key(state.edit));assertEquals(PhotoEdit(),state.undo.single());assertEquals("noir",PhotoLooks.key(state.redo.single()));assertEquals(state,PhotoLayers.parse(state.json().toString()))
 }
}
