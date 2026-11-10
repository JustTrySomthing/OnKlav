package app.onklav.home

import android.app.Application
import android.os.Looper
import android.view.*
import android.text.style.ForegroundColorSpan
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*
import java.io.*
import java.util.zip.*

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
// Host-only owner lifecycle fixture installs a model directly; not application code.
@android.annotation.SuppressLint("RestrictedApi")
class WriteReviewTest {
 private val app get()=ApplicationProvider.getApplicationContext<Application>()
 private fun await(test:()->Boolean){val end=System.nanoTime()+8_000_000_000;while(!test()&&System.nanoTime()<end){shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50));Thread.sleep(5)};assertTrue("Operation completed",test())}
 @Test fun formattingDismissesTheNativeMenuWithoutLosingRangeOrViewport(){
  val e=WriteNativeEditor(app);e.setText("Selected text stays in place");e.setSelection(2,12);e.scrollTo(0,40)
  var notifications=0;e.selected={_,_->notifications++};var finishes=0
  val mode=object:ActionMode(){
   override fun finish(){finishes++;e.setSelection(e.length())}
   override fun invalidate()=Unit
   override fun setTitle(title:CharSequence?)=Unit;override fun setTitle(resId:Int)=Unit
   override fun setSubtitle(subtitle:CharSequence?)=Unit;override fun setSubtitle(resId:Int)=Unit
   override fun setCustomView(view:View?)=Unit
   override fun getTitle():CharSequence="";override fun getSubtitle():CharSequence=""
   override fun getCustomView():View?=null
   override fun getMenu():Menu=android.widget.PopupMenu(app,e).menu
   override fun getMenuInflater()=MenuInflater(app)
  }
  org.robolectric.util.ReflectionHelpers.setField(e,"selectionMode",mode)
  e.beginFormatting();assertEquals(1,finishes);assertEquals(2,e.selectionStart);assertEquals(12,e.selectionEnd);assertEquals(40,e.scrollY);assertEquals(0,notifications)
  e.format("highlight",android.graphics.Color.YELLOW.toString());e.format("color",android.graphics.Color.BLUE.toString())
  assertEquals(2,e.selectionStart);assertEquals(12,e.selectionEnd);assertEquals(40,e.scrollY)
  assertFalse(e.requestRectangleOnScreen(android.graphics.Rect(0,0,5,5),true))
  var resumed=false;e.resumeEditing={resumed=true};e.editableText.append("!");assertTrue(resumed);assertFalse(e.formattingToolsOpen)
 }
 @Test fun invalidFormattingDoesNotStripSpansOrLeaveTheEditorGuarded(){
  val e=WriteNativeEditor(app);e.setText("abcdef");e.setSelection(1,4);e.format("color",android.graphics.Color.RED.toString())
  val before=WriteDocument.capture("","",e.editableText)
  assertThrows(Exception::class.java){e.format("color","not-a-color")}
  assertFalse(e.guarded);assertEquals(before.marks,WriteDocument.capture("","",e.editableText).marks)
  var edits=0;e.changed={_,_->edits++};e.editableText.append("g");assertTrue(edits>0)
 }
 @Test fun backwardsSelectionFormatsTheEntireRangeAndParagraph(){
  val e=WriteNativeEditor(app);e.setText("first\nsecond");e.setSelection(10,2);e.format("color",android.graphics.Color.BLUE.toString())
  val span=e.editableText.getSpans(2,10,ForegroundColorSpan::class.java).single();assertEquals(2,e.editableText.getSpanStart(span));assertEquals(10,e.editableText.getSpanEnd(span))
  val text=android.text.SpannableStringBuilder("first\nsecond");WriteFormatting.paragraphs(text,10,2,"align","ALIGN_CENTER")
  assertTrue(text.getSpans(0,text.length,android.text.style.AlignmentSpan::class.java).all{it.alignment==android.text.Layout.Alignment.ALIGN_CENTER})
  assertEquals(2,text.getSpans(0,text.length,android.text.style.AlignmentSpan::class.java).size)
 }
 @Test fun typingAfterAFormatHasItsOwnUndoStep(){
  runBlocking{AppSdk.bootstrap(app)};val m=WriteModel(app);await{m.initialized}
  m.change(m.doc.copy(text="abc"),false)
  m.change(m.doc.copy(marks=listOf(WriteMark(0,3,"color",android.graphics.Color.BLUE.toString()))),false)
  m.change(m.doc.copy(text="abcd"),true);m.history(true)
  assertEquals("abc",m.doc.text);assertEquals(1,m.doc.marks.size)
  m.history(true);assertEquals("abc",m.doc.text);assertTrue(m.doc.marks.isEmpty());m.saveNow(false);await{m.status=="Saved"}
 }
 @Test fun damagedCurrentDocumentIsReportedAndCanBeRetriedWithoutSavingABlankReplacement(){
  runBlocking{AppSdk.bootstrap(app)};val id=java.util.UUID.randomUUID().toString();val file=File(app.filesDir,"write/documents/$id.json").apply{parentFile!!.mkdirs();writeText("{broken")}
  app.getSharedPreferences("write",0).edit().putString("current",id).commit()
  val m=WriteModel(app);await{m.initialized};assertTrue(m.openingFailed);assertTrue(m.status.startsWith("Unable"))
  val blank=m.doc;m.change(blank.copy(text="must not save"),false);m.saveNow();assertEquals(blank,m.doc);assertEquals("{broken",file.readText())
  WriteFiles.save(app,WriteDocument(id=id,text="Recovered original"));m.retryOpening();await{m.initialized&&!m.openingFailed};assertEquals(id,m.doc.id);assertEquals("Recovered original",m.doc.text)
 }
 @Test fun closingTheModelPersistsItsFinalDebouncedDraft(){
  runBlocking{AppSdk.bootstrap(app)};val m=WriteModel(app);await{m.initialized};val store=androidx.lifecycle.ViewModelStore();store.put("write",m)
  m.change(m.doc.copy(text="Last keystrokes before closing"),true);val id=m.doc.id;store.clear()
  await{runCatching{WriteFiles.read(app,id).text=="Last keystrokes before closing"}.getOrDefault(false)}
 }
 @Test fun docxIndentRoundTripsAndUnsupportedContentIsReported(){
  val doc=WriteDocument(text="Indented",marks=listOf(WriteMark(0,8,"indent","24")));val out=ByteArrayOutputStream();WriteExport.docx(doc,out)
  var warnings=emptySet<String>();val imported=WriteExport.importDocx(out.toByteArray()){warnings=it};assertEquals("24",imported.marks.single{it.kind=="indent"}.value);assertTrue(warnings.isEmpty())
  val altered=ByteArrayOutputStream();ZipOutputStream(altered).use{target->ZipInputStream(out.toByteArray().inputStream()).use{source->while(true){val entry=source.nextEntry?:break;val bytes=source.readBytes();target.putNextEntry(ZipEntry(entry.name));target.write(if(entry.name=="word/document.xml")bytes.toString(Charsets.UTF_8).replace("<w:body>","<w:body><w:tbl/><w:pict/>").toByteArray()else bytes);target.closeEntry()}};target.putNextEntry(ZipEntry("word/header1.xml"));target.write("<header/>".toByteArray());target.closeEntry()}
  WriteExport.importDocx(altered.toByteArray()){warnings=it};assertEquals(setOf("images","table layout","headers/footers"),warnings)
 }
}
