package app.onklav.home

import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.*

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class PhotoLayersTest {
 @Test fun portableProjectRetainsOriginalEditsMarksAndBothHistoryDirections(){
  val dir=kotlin.io.path.createTempDirectory("layers-test").toFile()
  try{val source=File(dir,"original").apply{writeBytes(ByteArray(3000){it.toByte()})};val mark=PhotoMark(tool="text",text="Editable label",color=0xffaa4400.toInt(),x=.2f,y=.3f)
   val edit=PhotoEdit().set("angle",23f).set("hdr",.8f).copy(marks=listOf(mark));val state=PhotoLayers.State(edit,listOf(PhotoEdit()),listOf(edit.set("exposure",.4f)))
   val file=File(dir,"image.layers");PhotoLayers.write(file,source,state){};assertTrue(PhotoLayers.isProject(file));val restored=File(dir,"restored");assertEquals(state,PhotoLayers.read(file,restored){});assertArrayEquals(source.readBytes(),restored.readBytes())
  }finally{dir.deleteRecursively()}
 }
 @Test fun invalidVersionTraversalAndOversizedHistoryAreRejectedBeforeSourceIsWritten(){
  val dir=kotlin.io.path.createTempDirectory("layers-invalid").toFile()
  try{val state=PhotoLayers.State(PhotoEdit(),emptyList(),emptyList());try{PhotoLayers.parse(state.json().put("version",99).toString());fail()}catch(_:IllegalArgumentException){}
   val archive=File(dir,"bad.layers");ZipOutputStream(archive.outputStream()).use{it.putNextEntry(ZipEntry("../source"));it.write(byteArrayOf(1));it.closeEntry()};val out=File(dir,"source");try{PhotoLayers.read(archive,out){};fail()}catch(_:IllegalArgumentException){};assertFalse(out.exists())
   try{PhotoLayers.parse(PhotoLayers.State(PhotoEdit(),List(61){PhotoEdit()},emptyList()).json().toString());fail()}catch(_:IllegalArgumentException){}
  }finally{dir.deleteRecursively()}
 }
 @Test fun cancellationStopsArchiveWithoutChangingOriginal(){
  val dir=kotlin.io.path.createTempDirectory("layers-cancel").toFile();try{val source=File(dir,"source").apply{writeBytes(ByteArray(10000){42})};try{PhotoLayers.write(File(dir,"image.layers"),source,PhotoLayers.State(PhotoEdit(),emptyList(),emptyList())){throw kotlinx.coroutines.CancellationException()};fail()}catch(_:kotlinx.coroutines.CancellationException){};assertTrue(source.readBytes().all{it==42.toByte()})}finally{dir.deleteRecursively()}
 }
}
