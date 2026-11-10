package app.onklav.home
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.text.style.StyleSpan
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
@RunWith(RobolectricTestRunner::class) @Config(sdk=[29]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
class WriteHostTest {
 @Test fun partialAndCursorFormattingPreserveSurroundingText(){
  val s=SpannableStringBuilder("abcdef")
  WriteFormatting.apply(s,0,6,"bold","1");WriteFormatting.apply(s,2,4,"italic","1");WriteFormatting.apply(s,2,4,"bold",null)
  assertTrue(WriteFormatting.active(s,0,2,"bold"));assertFalse(WriteFormatting.active(s,2,4,"bold"));assertTrue(WriteFormatting.active(s,2,4,"italic"));assertTrue(WriteFormatting.active(s,4,6,"bold"))
  WriteFormatting.apply(s,6,6,"bold",null);s.insert(6,"X");assertFalse(WriteFormatting.active(s,6,7,"bold"))
  WriteFormatting.apply(s,7,7,"underline","1");s.insert(7,"Y");assertTrue(WriteFormatting.active(s,7,8,"underline"))
  WriteFormatting.apply(s,2,4,"highlight",Color.YELLOW.toString());assertEquals(1,s.getSpans(2,4,BackgroundColorSpan::class.java).size)
 }
 @Test fun numberedListsContinueAndRemovingOneParagraphKeepsItsNeighbors(){
  val s=SpannableStringBuilder("One\nTwo\nThree");WriteFormatting.paragraphs(s,0,s.length,"list","number")
  assertEquals(listOf(1,2,3),s.getSpans(0,s.length,WriteListSpan::class.java).sortedBy{s.getSpanStart(it)}.map{it.number})
  WriteFormatting.paragraphs(s,4,7,"list","none");assertEquals(listOf(0,8),s.getSpans(0,s.length,WriteListSpan::class.java).map{s.getSpanStart(it)}.sorted())
  val t=SpannableStringBuilder("First");WriteFormatting.paragraphs(t,0,t.length,"list","number");t.append("\n");WriteFormatting.normalizeLists(t);WriteFormatting.paragraphs(t,t.length,t.length,"list","none");t.append("Plain");WriteFormatting.normalizeLists(t)
  assertTrue(t.getSpans(0,2,WriteListSpan::class.java).isNotEmpty());assertTrue(t.getSpans(6,11,WriteListSpan::class.java).isEmpty())
  val e=WriteNativeEditor(ApplicationProvider.getApplicationContext<Context>());e.setText("Item");e.setSelection(4);e.paragraph("list","number");e.editableText.append("\n");e.editableText.append("\n");e.editableText.append("Plain");assertEquals("Item\nPlain",e.text.toString());assertTrue(e.editableText.getSpans(5,10,WriteListSpan::class.java).isEmpty())
 }
 @Test fun richDocumentsRoundTripDocxAndPdfPagination(){
  val s=SpannableStringBuilder("A title\nFirst item\nSecond item");WriteFormatting.apply(s,0,7,"bold","1");WriteFormatting.apply(s,0,7,"size","28");WriteFormatting.apply(s,8,18,"color",Color.RED.toString());WriteFormatting.apply(s,8,18,"highlight",Color.YELLOW.toString());WriteFormatting.paragraphs(s,8,s.length,"list","number")
  val doc=WriteDocument.capture(java.util.UUID.randomUUID().toString(),"A & B",s);val restored=WriteDocument.parse(doc.json());assertEquals(doc.text,restored.text);assertEquals(doc.marks,restored.marks);assertTrue(restored.html().contains("<ol>"));assertTrue(restored.html().contains("font-weight:bold"));assertTrue(restored.html().contains("#FF0000"));assertFalse(restored.html().contains("<script"))
  val bytes=ByteArrayOutputStream();WriteExport.docx(doc,bytes);val back=WriteExport.importDocx(bytes.toByteArray());assertEquals(doc.text,back.text.trimEnd());assertTrue(back.marks.any{it.kind=="style"});assertTrue(back.marks.any{it.kind=="highlight"});assertEquals(2,back.marks.count{it.kind=="list"})
  val layout=WriteExport.pdfLayout(WriteDocument(text=(1..180).joinToString("\n"){"Paragraph $it — this is a printable document."}));val pages=WriteExport.pdfPages(layout);assertTrue(pages.size>1);assertEquals(0,pages.first().first);assertEquals(layout.lineCount-1,pages.last().last);pages.zipWithNext().forEach{(a,b)->assertEquals(a.last+1,b.first)}

  assertThrows(IllegalArgumentException::class.java){safeLink("javascript:alert(1)")}
 }
 @Test fun embeddedImagesSurviveWriteRoundTripAndSafeExports(){
  val bitmap=android.graphics.Bitmap.createBitmap(16,12,android.graphics.Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.BLUE);val bytes=ByteArrayOutputStream();bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,bytes);bitmap.recycle()
  val encoded=android.util.Base64.encodeToString(bytes.toByteArray(),android.util.Base64.NO_WRAP)
  val text=SpannableStringBuilder("Before \uFFFC after");text.setSpan(WriteImageSpan(encoded),7,8,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
  val doc=WriteDocument.capture(java.util.UUID.randomUUID().toString(),"Picture",text);val back=WriteDocument.parse(doc.json());assertEquals(encoded,back.marks.single().value);assertEquals(1,back.spanned().getSpans(0,back.text.length,WriteImageSpan::class.java).size);assertTrue(back.html().contains("data:image/png;base64,"));val layout=WriteExport.pdfLayout(back);val page=android.graphics.Bitmap.createBitmap(layout.width,layout.height,android.graphics.Bitmap.Config.ARGB_8888);layout.draw(android.graphics.Canvas(page));assertTrue((0 until page.height).any{y->(0 until page.width).any{x->page.getPixel(x,y)==Color.BLUE}});page.recycle()
  val word=ByteArrayOutputStream();WriteExport.docx(back,word);val parts=mutableMapOf<String,ByteArray>();java.util.zip.ZipInputStream(word.toByteArray().inputStream()).use{zip->while(true){val e=zip.nextEntry?:break;parts[e.name]=zip.readBytes()}};assertTrue(parts.containsKey("word/media/picture1.png"));assertTrue(parts.getValue("word/document.xml").toString(Charsets.UTF_8).contains("v:imagedata"));assertTrue(parts.getValue("word/_rels/document.xml.rels").toString(Charsets.UTF_8).contains("media/picture1.png"));assertFalse(WriteImageSpan.valid("broken"))
 }
 @Test fun proofreadingPreservesOffsetsAndDoesNotRewriteText(){
  val text="The the draft  stays here.\nA and another paragraph."
  val found=writeLocalChecks(text)
  assertEquals(2,found.size)
  assertEquals("The the",text.substring(found[0].start,found[0].end))
  assertEquals("The",found[0].replacement)
  assertEquals("  ",text.substring(found[1].start,found[1].end))
  assertTrue(writeLocalChecks("The draft stays here.").isEmpty())
 }
 @Test fun documentsAreAtomicPrivateAndRespectRevocation(){
  val c=ApplicationProvider.getApplicationContext<Context>();runBlocking{AppSdk.bootstrap(c)};assertTrue(WriteFiles.allowed(c));val d=WriteDocument(title="Test",text="Keep my words");WriteFiles.save(c,d);assertEquals(d.text,WriteFiles.read(c,d.id).text);assertTrue(WriteFiles.list(c).any{it.id==d.id});
  val base=java.io.File(c.filesDir,"write/documents/${d.id}.json");assertTrue("Backup fixture created",base.renameTo(java.io.File(base.path+".bak")));
  assertTrue("Backup-only document is discoverable",WriteFiles.list(c).any{it.id==d.id});assertEquals(d.text,WriteFiles.read(c,d.id).text);assertThrows(IllegalArgumentException::class.java){WriteFiles.read(c,"../outside")}
  AppSdk.sync(c,JSONObject().put("action","permissions").put("id","app.onklav.write").put("grants",org.json.JSONArray()));assertFalse(WriteFiles.allowed(c));assertThrows(IllegalStateException::class.java){WriteFiles.save(c,d)}
 }
}
