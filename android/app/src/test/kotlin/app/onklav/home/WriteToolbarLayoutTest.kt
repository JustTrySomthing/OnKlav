package app.onklav.home
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.onklav.shared.OnKlavTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
@RunWith(RobolectricTestRunner::class) @Config(sdk=[29],qualifiers="w384dp-h720dp-mdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
class WriteToolbarLayoutTest {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 @Test fun textComesFirstParagraphMenusAreIndependentAndHistoryIsStackedOnTheRight(){
  var command="";var undos=0;var redos=0
  compose.activity.setContent{OnKlavTheme{var panel by remember{mutableStateOf("")};WriteTools(true,true,true,{false},panel,0xff000000.toInt(),0xffffff00.toInt(),16,{panel=it},{panel=""},{},{_,_->},{kind,value->command="$kind:$value"},{command="indent:$it"},{},{},{undos++},{redos++})}}
  val text=compose.onNodeWithContentDescription("Text").fetchSemanticsNode().boundsInRoot
  val bold=compose.onNodeWithContentDescription("Bold").fetchSemanticsNode().boundsInRoot
  val redo=compose.onNodeWithContentDescription("Redo").fetchSemanticsNode().boundsInRoot
  val undo=compose.onNodeWithContentDescription("Undo").fetchSemanticsNode().boundsInRoot
  assertTrue(text.left<bold.left);assertEquals(text.top,bold.top,.1f);assertEquals(redo.left,undo.left,.1f);assertTrue(redo.top<undo.top);assertTrue(undo.left>compose.onNodeWithContentDescription("Spelling & grammar").fetchSemanticsNode().boundsInRoot.left)
  compose.onNodeWithContentDescription("Lists").performClick();compose.onNodeWithText("Numbered").performClick();assertEquals("list:number",command)
  compose.onNodeWithContentDescription("Align").performClick();compose.onNodeWithText("Center").performClick();assertEquals("align:ALIGN_CENTER",command)
  compose.onNodeWithContentDescription("Indent").performClick();compose.onNodeWithText("Outdent").performClick();assertEquals("indent:-24",command)
  compose.onNodeWithContentDescription("Undo").performClick();compose.onNodeWithContentDescription("Redo").performClick();assertEquals(1,undos);assertEquals(1,redos)
  compose.runOnUiThread{val v=compose.activity.window.decorView;val b=android.graphics.Bitmap.createBitmap(v.width,v.height,android.graphics.Bitmap.Config.ARGB_8888);v.draw(android.graphics.Canvas(b));java.io.File("/workspace/scratch/c739b0c1372b/write-toolbar.png").outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
 }
}
