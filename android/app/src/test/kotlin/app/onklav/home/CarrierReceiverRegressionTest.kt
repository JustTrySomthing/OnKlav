package app.onklav.home

import android.app.Activity
import android.content.*
import android.net.Uri
import android.os.Looper
import android.Manifest
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class CarrierReceiverRegressionTest {
 @Test fun realReceiverPreservesSuccessfulCarrierResultAcrossAsyncHandoff(){
  val c=ApplicationProvider.getApplicationContext<Context>();val id="d".repeat(32)
  ChatStore(c).requestSync(JSONObject().put("action","stage").put("id",id).put("address","15555550101").put("text","Carrier fixture").put("parts",1).put("at",System.currentTimeMillis()))
  val receiver=MessageResultReceiver()
  val type=Class.forName("android.content.BroadcastReceiver\$PendingResult")
  val pending=type.getConstructor(Int::class.javaPrimitiveType,String::class.java,android.os.Bundle::class.java,Int::class.javaPrimitiveType,Boolean::class.javaPrimitiveType,Boolean::class.javaPrimitiveType,android.os.IBinder::class.java,Int::class.javaPrimitiveType,Int::class.javaPrimitiveType).newInstance(Activity.RESULT_OK,null,null,1,true,false,android.os.Binder(),0,0)
  BroadcastReceiver::class.java.getMethod("setPendingResult",type).invoke(receiver,pending)
  assertEquals(Activity.RESULT_OK,receiver.resultCode)
  val i=Intent("app.onklav.sms_sent").setData(Uri.parse("onklav-result://sms_sent/$id/0")).putExtra("kind","sms_sent").putExtra("token",id).putExtra("part",0)
  receiver.onReceive(c,i)
  assertTrue(shadowOf(receiver).wentAsync())
  ChatStore.executor.submit{}.get(10,TimeUnit.SECONDS)
  val row=ChatStore(c).requestSync(JSONObject().put("action","snapshot")).child("outgoing").child(id)
  assertEquals("Sent",row.getString("status"));assertEquals("",row.getString("error"))

 }
 @Test fun picturesDefaultOnAndSetupIncludesAllMessagingApprovals(){
  val c=ApplicationProvider.getApplicationContext<Context>();c.getSharedPreferences("messaging_setup",0).edit().clear().commit()
  assertTrue(MessagingTransport.carrierEnabled(c));assertFalse(MessagingTransport.MMS_READY)
  assertTrue(OnKlavPermissions.messageRuntime.containsAll(listOf(Manifest.permission.READ_SMS,Manifest.permission.SEND_SMS,Manifest.permission.RECEIVE_SMS,Manifest.permission.RECEIVE_MMS,Manifest.permission.RECEIVE_WAP_PUSH,Manifest.permission.READ_PHONE_STATE)))
 }
}
