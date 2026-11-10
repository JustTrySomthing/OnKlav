package app.onklav.home

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.*
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.hwpf.HWPFDocument

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class DocumentReadingTest {
 private lateinit var dir:File
 @Before fun setup(){dir=File(ApplicationProvider.getApplicationContext<Application>().cacheDir,"doc-reader-test").apply{mkdirs()}}
 @After fun cleanup(){dir.deleteRecursively()}
 private fun file(name:String)=File(dir,name)
 private fun zip(name:String,vararg entries:Pair<String,String>)=file(name).apply{ZipOutputStream(outputStream()).use{z->for((path,xml)in entries){z.putNextEntry(ZipEntry(path));z.write(xml.toByteArray());z.closeEntry()}}}
 private fun reject(block:()->Unit){try{block();fail("Accepted invalid data")}catch(_:IllegalArgumentException){}catch(_:IllegalStateException){}}
 @Test fun csvQuotedNewlinesDelimitersAndBlankColumnsRemainIntact(){
  assertEquals(listOf(listOf("Name","Note",""),listOf("Holmes, Mr.","one\ntwo","x")),DocumentReading.csv("Name,Note,\r\n\"Holmes, Mr.\",\"one\ntwo\",x\r\n"))
  assertEquals(listOf(listOf("A","B"),listOf("1","2")),DocumentReading.csv("A;B\n1;2"))
  assertEquals(listOf(listOf("=SUM(A1)","<script>")),DocumentReading.csv("=SUM(A1),<script>"))
  reject{DocumentReading.csv("\"unfinished")};reject{DocumentReading.csv("x,".repeat(300))}
 }
 @Test fun rtfSkipsImagesMetadataAndInstructionsButKeepsUnicodeAndVisibleFields(){
  val input="""{\rtf1\ansi\ansicpg1252{\fonttbl{\f0 Arial;}}Hello \'e9 \u-10179?\u-8704?\par {\*\private secret}{\pict 00abcdef}{\field{\*\fldinst HYPERLINK hidden}{\fldrslt Visible}}\tab Done}"""
  assertEquals("Hello é 😀\nVisible\tDone",DocumentReading.rtf(input.toByteArray(Charsets.ISO_8859_1)))
  reject{DocumentReading.rtf("{\\rtf1 broken".toByteArray())}
 }
 @Test fun docxReusesWriteFormattingAndTextDecodingHonorsBom(){
  val out=file("styled.docx");out.outputStream().use{WriteExport.docx(WriteDocument(text="Hello world",marks=listOf(WriteMark(0,5,"style","1"))),it)}
  val doc=DocumentReading.read(out,out.name);assertEquals("Hello world\n",doc.text);assertTrue(doc.styled!!.getSpans(0,5,android.text.style.StyleSpan::class.java).isNotEmpty())
  val txt=file("unicode.txt").apply{writeBytes(byteArrayOf(0xff.toByte(),0xfe.toByte())+"Café".toByteArray(Charsets.UTF_16LE))};assertEquals("Café",DocumentReading.read(txt,txt.name).text)
 }
 @Test fun legacyWordAndExcelAreParsedAsRealBinaryOfficeFiles(){
  val blank=java.util.Base64.getMimeDecoder().decode(requireNotNull(javaClass.getResourceAsStream("/docu/empty-doc.base64.txt")).use{it.readBytes()}).inputStream();val doc=file("legacy.doc");blank.use{input->HWPFDocument(input).use{w->w.range.insertBefore("Everyday Word reading\r");doc.outputStream().use{w.write(it)}}}
  assertTrue(DocumentReading.read(doc,doc.name).text.contains("Everyday Word reading"))
  val xls=file("legacy.xls");HSSFWorkbook().use{w->val s=w.createSheet("Budget");s.createRow(0).createCell(0).setCellValue("Amount");s.createRow(2).createCell(2).setCellValue(42.0);val hidden=w.createSheet("Hidden");hidden.createRow(0).createCell(0).setCellValue("not displayed");w.setSheetHidden(1,true);xls.outputStream().use{w.write(it)}}
  val value=DocumentReading.read(xls,xls.name);assertEquals(1,value.sheets.size);assertEquals("Budget",value.sheets[0].name);assertEquals(listOf("","","42"),value.sheets[0].rows[2]);assertTrue(value.notice.contains("cached"))
 }
 @Test fun xlsxRespectsSheetRelationshipsSharedStringsInlineValuesAndSparseCells(){
  val x=zip("book.xlsx","xl/workbook.xml" to "<workbook xmlns:r='r'><sheets><sheet name='Budget' r:id='rId1'/></sheets></workbook>","xl/_rels/workbook.xml.rels" to "<Relationships><Relationship Id='rId1' Type='office/worksheet' Target='worksheets/sheet1.xml'/></Relationships>","xl/sharedStrings.xml" to "<sst><si><t>Name</t></si></sst>","xl/worksheets/sheet1.xml" to "<worksheet><sheetData><row r='1'><c r='A1' t='s'><v>0</v></c><c r='C1' t='inlineStr'><is><t>Third</t></is></c></row><row r='3'><c r='B3'><f>1+1</f><v>2</v></c></row></sheetData></worksheet>")
  val s=DocumentReading.read(x,x.name).sheets.single();assertEquals("Budget",s.name);assertEquals(listOf("Name","","Third"),s.rows[0]);assertEquals(emptyList<String>(),s.rows[1]);assertEquals(listOf("","2"),s.rows[2])
 }
 @Test fun odfDocumentsSheetsAndEquationsHaveReadableContentAndRejectEntities(){
  val ns="xmlns:office='office' xmlns:text='text' xmlns:table='table'"
  val odt=zip("note.odt","content.xml" to "<office:document $ns><text:p>One<text:tab/>Two</text:p><text:p>Three</text:p></office:document>");assertEquals("One\tTwo\nThree",DocumentReading.read(odt,odt.name).text)
  val ods=zip("book.ods","content.xml" to "<office:document $ns><office:spreadsheet><table:table table:name='Data'><table:table-row><table:table-cell table:number-columns-repeated='2'><text:p>A</text:p></table:table-cell><table:table-cell><text:p>B</text:p></table:table-cell></table:table-row></table:table></office:spreadsheet></office:document>");assertEquals(listOf("A","A","B"),DocumentReading.read(ods,ods.name).sheets.single().rows.single())
  val odf=zip("equation.odf","content.xml" to "<office:document $ns><office:formula><math><mi>x</mi><mo>+</mo><mn>1</mn></math></office:formula></office:document>");assertEquals("x+1",DocumentReading.read(odf,odf.name).text)
  reject{DocumentReading.read(zip("bad.odt","content.xml" to "<!DOCTYPE x [<!ENTITY x SYSTEM 'file:///secret'>]><x/>"),"bad.odt")}
 }
 @Test fun oversizedSparseAndExternalWorkbookPathsAreRejected(){
  val x=zip("big.xlsx","xl/workbook.xml" to "<w xmlns:r='r'><sheet name='Sheet' r:id='a'/></w>","xl/_rels/workbook.xml.rels" to "<R><Relationship Id='a' Type='x/worksheet' Target='worksheets/a.xml'/></R>","xl/worksheets/a.xml" to "<w><row r='999999'><c r='A999999'><v>1</v></c></row></w>");reject{DocumentReading.read(x,x.name)}
  val bomb=file("large.txt");java.io.RandomAccessFile(bomb,"rw").use{it.setLength(DocumentReading.MAX_BYTES+1)};reject{DocumentReading.read(bomb,bomb.name)}
  val bad=zip("bad.xlsx","xl/workbook.xml" to "<w xmlns:r='r'><sheet name='Sheet' r:id='a'/></w>","xl/_rels/workbook.xml.rels" to "<R><Relationship Id='a' Type='x/worksheet' Target='../outside.xml'/></R>");reject{DocumentReading.read(bad,bad.name)}
 }
 @Test fun filesOpenRoutesDocumentsInsideHostAndNeverGrantsWrite(){
  val controller=org.robolectric.Robolectric.buildActivity(androidx.activity.ComponentActivity::class.java).setup();val activity=controller.get();val uri=Uri.parse("content://${activity.packageName}.files-open/123?name=Report.xlsx");FilesBroker.open(activity,uri,"application/octet-stream")
  val intent=org.robolectric.Shadows.shadowOf(activity).nextStartedActivity;controller.pause().stop().destroy();assertEquals(DocuViewActivity::class.java.name,intent.component!!.className);assertEquals(0,intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
  assertEquals("A",columnLabel(0));assertEquals("Z",columnLabel(25));assertEquals("AA",columnLabel(26));assertEquals("IV",columnLabel(255))
 }
}
