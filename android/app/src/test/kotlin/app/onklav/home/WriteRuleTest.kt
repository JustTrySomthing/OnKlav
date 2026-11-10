package app.onklav.home

import android.app.Application
import android.graphics.Typeface
import android.text.*
import android.text.style.StyleSpan
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class WriteRuleTest {
 private val app get()=ApplicationProvider.getApplicationContext<Application>()
 @Test fun ruleInsertsAtCursorWithoutReplacingTextAndKeepsSurroundingBold(){
  val doc=WriteDocument(text="AlphaBeta",marks=listOf(WriteMark(0,9,"style",Typeface.BOLD.toString())))
  val editor=WriteNativeEditor(app);editor.load(doc,0,5,5);var saved:WriteDocument?=null;editor.changed={s,_->saved=WriteDocument.capture(doc.id,doc.title,s)};editor.insertRule()
  assertEquals("Alpha\n\uFFFC\nBeta",editor.text.toString());assertEquals(8,editor.selectionStart)
  val value=checkNotNull(saved);assertEquals(1,value.marks.count{it.kind=="rule"});assertTrue(value.spanned().getSpans(0,5,StyleSpan::class.java).any{it.style==Typeface.BOLD});assertTrue(value.spanned().getSpans(8,12,StyleSpan::class.java).any{it.style==Typeface.BOLD})
  assertEquals(value.text,WriteDocument.parse(value.json()).text)
 }
 @Test fun ruleIsPersistedAndExportedAsARealWordBorderAndPdfSpan(){
  val doc=WriteDocument(text="Before\n\uFFFC\nAfter",marks=listOf(WriteMark(7,8,"rule","1")))
  val read=WriteDocument.parse(doc.json());assertEquals(1,read.spanned().getSpans(0,read.text.length,WriteRuleSpan::class.java).size);assertTrue(read.html().contains("role=\"separator\""))
  val stream=ByteArrayOutputStream();WriteExport.docx(doc,stream);val imported=WriteExport.importDocx(stream.toByteArray());assertEquals(doc.text+"\n",imported.text);assertEquals(1,imported.marks.count{it.kind=="rule"})
  val layout=WriteExport.pdfLayout(doc);val spans=(layout.text as Spanned).getSpans(0,layout.text.length,WriteRuleSpan::class.java);assertEquals(499,spans.single().widthPx)
 }
 @Test fun malformedRuleMarkersAreRejectedAndInsertionAtAnEmptyLineKeepsOnlyOnePrefix(){
  assertThrows(Exception::class.java){WriteDocument.parse(WriteDocument(text="x",marks=listOf(WriteMark(0,1,"rule","1"))).json())}
  val editor=WriteNativeEditor(app);editor.load(WriteDocument(text="One\nTwo"),0,4,4);editor.insertRule();assertEquals("One\n\uFFFC\nTwo",editor.text.toString())
 }
}
