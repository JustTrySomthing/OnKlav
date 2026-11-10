package app.onklav.home
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class) @Config(sdk=[35])
class MessageWebLinksTest {
 @Test fun bothReportedUrlsHaveClickableRanges(){
  val text="Source: Keep Android Open https://share.google/u1hD7LsvACBBhQxkA\nhttps://keepandroidopen.org/"
  val result=messageLinkText(text,Color.Blue,{})
  assertEquals(text,result.text);assertEquals(2,result.getLinkAnnotations(0,result.length).size)
 }
 @Test fun nonWebSchemesAreNotPassedToTheBrowser(){val r=messageLinkText("javascript:alert(1)",Color.Blue,{});assertTrue(r.getLinkAnnotations(0,r.length).isEmpty())}
}
