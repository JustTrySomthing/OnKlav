package app.onklav.home

import android.app.Activity
import android.content.*
import android.net.Uri
import android.os.Looper
import android.telephony.SmsManager
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.time.Duration

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class SmsSettlementTest {
 private fun context():Context{val app=ApplicationProvider.getApplicationContext<Context>();val root=File(app.cacheDir,"sms-settle-${java.util.UUID.randomUUID()}").apply{mkdirs()};return object:ContextWrapper(app){override fun getFilesDir()=root;override fun getApplicationContext():Context=this}}
 private val id="f".repeat(32)
 private fun intent()=Intent("app.onklav.sms_sent").setData(Uri.parse("onklav-result://sms_sent/$id/0")).putExtra("token",id).putExtra("kind","sms_sent").putExtra("part",0)
 private fun stage(c:Context,parts:Int=1)=ChatStore(c).requestSync(JSONObject().put("action","stage").put("id",id).put("address","+15555550101").put("text","Fixture").put("parts",parts).put("at",System.currentTimeMillis()))
 private fun row(c:Context)=ChatStore(c).requestSync(JSONObject().put("action","snapshot")).child("outgoing").child(id)
 @Test fun transientNegativeThenSuccessNeverFlashesAFailureAndNeverResends(){
  val c=context();stage(c);MessagingTransport.receiveResult(c,intent(),SmsManager.RESULT_ERROR_GENERIC_FAILURE);assertEquals("Sending",row(c).getString("status"));assertEquals("",row(c).getString("error"));assertEquals(1,CarrierResults(c).ids().size)
  MessagingTransport.receiveResult(c,intent(),Activity.RESULT_OK);assertEquals("Sent",row(c).getString("status"));shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(9));CarrierResults(c).let{receipts->receipts.ids().forEach{token->receipts.read(token)?.let{saved->saved.put("received_at",System.currentTimeMillis()-9000).put("settle_at",System.currentTimeMillis()-1000);receipts.save(token,saved)}}};MessagingTransport.recoverResults(c);assertEquals("Sent",row(c).getString("status"));assertEquals("",row(c).getString("error"));assertTrue(CarrierResults(c).ids().isEmpty());assertEquals(1,ChatStore(c).requestSync(JSONObject().put("action","snapshot")).child("outgoing").length())
 }
 @Test fun genuineFailuresStillAppearAfterTheBoundedDurableWindow(){
  val c=context();stage(c);MessagingTransport.receiveResult(c,intent(),SmsManager.RESULT_ERROR_GENERIC_FAILURE);val receipts=CarrierResults(c);val token=receipts.ids().single();val saved=receipts.read(token)!!;assertTrue(MessagingTransport.receiptWait(saved)>0)
  saved.put("received_at",System.currentTimeMillis()-9000).put("settle_at",System.currentTimeMillis()-1000);receipts.save(token,saved);MessagingTransport.recoverResults(c);assertEquals("Failed",row(c).getString("status"));assertTrue(row(c).getString("error").contains("carrier"));assertTrue(receipts.ids().isEmpty())
 }
 @Test fun actionableSimRadioPermissionErrorsAndClockChangesDoNotWait(){
  assertEquals(0L,MessagingTransport.smsSettleDelay(Activity.RESULT_OK));assertEquals(0L,MessagingTransport.smsSettleDelay(1,true));assertEquals(0L,MessagingTransport.smsSettleDelay(SmsManager.RESULT_ERROR_RADIO_OFF));assertEquals(8000L,MessagingTransport.smsSettleDelay(SmsManager.RESULT_ERROR_GENERIC_FAILURE))
  assertEquals(0L,MessagingTransport.receiptWait(JSONObject().put("received_at",2000).put("settle_at",10000),1000));assertEquals(0L,MessagingTransport.receiptWait(JSONObject().put("received_at",1000).put("settle_at",99999999),2000))
 }
 @Test fun network17UsesTheStagedPartCountAndALateSuccessStillReconcilesWithoutResending(){
  for(parts in listOf(1,2)){
   val c=context();stage(c,parts)
   MessagingTransport.receiveResult(c,intent().putExtra("errorCode",2),SmsManager.RESULT_NETWORK_ERROR)
   assertEquals("Sending",row(c).getString("status"))
   val receipts=CarrierResults(c);val token=receipts.ids().single();val saved=receipts.read(token)!!
   saved.put("received_at",System.currentTimeMillis()-9000).put("settle_at",System.currentTimeMillis()-1000);receipts.save(token,saved)
   MessagingTransport.recoverResults(c)
   assertEquals("Failed",row(c).getString("status"));val error=row(c).getString("error")
   assertTrue(error.contains("mobile network"));assertTrue(error.contains("SMS 17, modem 2"));assertEquals(parts>1,error.contains("other parts"))
   repeat(parts){part->MessagingTransport.outcome(c,id,part,true,false)}
   assertEquals("Sent",row(c).getString("status"));assertEquals("",row(c).getString("error"))
   assertEquals(1,ChatStore(c).requestSync(JSONObject().put("action","snapshot")).child("outgoing").length())
  }
 }

}
