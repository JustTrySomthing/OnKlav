package app.onklav.home
import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class SdkCarrierReceiptsTest{
 private val c get()=ApplicationProvider.getApplicationContext<Application>()
 private fun row(job:String,app:String,parts:Boolean=false):File{
  val f=File(c.filesDir,"walkie-carrier-jobs/$job.json");f.parentFile!!.mkdirs()
  val j=JSONObject().put("owner",JSONObject().put("id",app).put("publisher","a".repeat(64))).put("kind","control").put("peer","+15550100001").put("sub",2).put("at",System.currentTimeMillis()).put("request",JSONObject().put("operation","send.result"))
  if(parts)j.put("parts",JSONArray().put(JSONObject.NULL).put(JSONObject.NULL));f.writeText(j.toString());return f
 }
 private fun result(job:String,part:Int=-1)=Intent().setAction("app.onklav.WALKIE_RESULT").setData(Uri.parse("onklav-walkie://result/$job"+if(part>=0)"/$part"else"")).putExtra("job",job).putExtra("part",part)
 @Test fun multipartCallbacksRemainDurableAndSeparateWhenCarrierAccessIsOff(){
  val a="1".repeat(32);val b="2".repeat(32);val first=row(a,"org.example.one",true);val other=row(b,"org.example.two");val original=other.readText()
  SdkWalkieTransport.result(c,result(a,0),Activity.RESULT_OK);assertTrue(JSONObject(first.readText()).getJSONArray("parts").getBoolean(0));assertFalse(JSONObject(first.readText()).has("callback_ok"))
  assertThrows(IllegalStateException::class.java){SdkWalkieTransport.result(c,result(a,1),Activity.RESULT_OK)}
  assertTrue(JSONObject(first.readText()).getBoolean("callback_ok"));assertEquals(original,other.readText())
  SdkWalkieTransport.result(c,result(a,0),Activity.RESULT_OK);assertEquals(original,other.readText())
 }
 @Test fun removingOneAppDeletesOnlyItsJobsAndResponseFiles(){
  val a="3".repeat(32);val b="4".repeat(32);val first=row(a,"org.example.one");val other=row(b,"org.example.two")
  val responseA=File(first.parentFile,"$a.response").apply{writeText("one")};val responseB=File(other.parentFile,"$b.response").apply{writeText("two")}
  SdkWalkieTransport.packageRemoved(c,"org.example.one");assertFalse(first.exists());assertFalse(responseA.exists());assertTrue(other.exists());assertEquals("two",responseB.readText())
 }
 @Test fun completePartResultsSurviveACrashBeforeTheAggregateWasSaved(){
  val job="5".repeat(32);val file=row(job,"org.example.one",true)
  val saved=JSONObject(file.readText()).put("parts",JSONArray().put(true).put(true));file.writeText(saved.toString())
  assertThrows(IllegalStateException::class.java){SdkWalkieTransport.result(c,result(job,1),Activity.RESULT_OK)}
  assertTrue(JSONObject(file.readText()).getBoolean("callback_ok"))
 }
 @Test fun aConflictingDuplicateCannotChangeAnAlreadySavedPart(){
  val job="6".repeat(32);val file=row(job,"org.example.one",true)
  SdkWalkieTransport.result(c,result(job,0),Activity.RESULT_OK)
  SdkWalkieTransport.result(c,result(job,0),Activity.RESULT_CANCELED)
  assertTrue(JSONObject(file.readText()).getJSONArray("parts").getBoolean(0));assertFalse(JSONObject(file.readText()).has("callback_ok"))
 }

}
