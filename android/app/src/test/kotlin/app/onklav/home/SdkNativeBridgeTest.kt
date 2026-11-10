package app.onklav.home
import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class SdkNativeBridgeTest{
 private val c get()=ApplicationProvider.getApplicationContext<Application>()
 private val caps=listOf("camera.capture","notifications.post","storage.secrets")
 private fun call(j:JSONObject)=AppSdk.sync(c,j)as JSONObject
 private fun start():String{
  val root=File(System.getProperty("onklav.project.root"));File(c.filesDir,"app-install.zip").writeBytes(File(root,"developer/tests/fixtures/NativeServices.onklav-a").readBytes())
  val review=call(JSONObject().put("action","inspect"));call(JSONObject().put("action","install").put("hash",review.getString("hash")).put("grants",JSONArray(caps)))
  return call(JSONObject().put("action","launch").put("id","org.example.nativebridge.checks2")).getString("session")
 }
 private fun pending(session:String,action:String)=call(JSONObject().put("action","event").put("session",session).put("event",JSONObject().put("action",action))).getJSONObject("pending")
 private fun authorize(session:String,p:JSONObject)=call(JSONObject().put("action","authorize").put("session",session).put("token",p.getString("token")))
 private fun reply(session:String,p:JSONObject,value:JSONObject)=call(JSONObject().put("action","reply").put("session",session).put("token",p.getString("token")).put("reply",JSONObject().put("ok",true).put("value",value)))
 @Test fun cameraAndNotificationsRoundTripThroughFreshJniAndActualNativeHandlers(){
  val session=start();var p=pending(session,"camera");val token=p.getString("token")
  SdkCamera.prepare(c,token){authorize(session,p)};SdkCamera.file(c,token).writeBytes(File(System.getProperty("onklav.project.root"),"docs/fixtures/annotation-base.jpg").readBytes())
  val bytes=SdkCamera.read(c,token){authorize(session,p)}
  val photo=SdkCamera.commit(c,bytes,{authorize(session,p)}){call(JSONObject().put("action","camera_host").put("session",session).put("token",token))};SdkCamera.cleanup(c,token)
  assertFalse(photo.has("path"));assertFalse(photo.has("uri"));assertEquals("Camera completed. Your test photo was captured.",reply(session,p,photo).getJSONObject("frame").getJSONObject("state").getString("status"))
  shadowOf(c.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(true);p=pending(session,"notice")
  val notice=SdkNotifications.execute(c,authorize(session,p)){authorize(session,p)};assertTrue(notice.getBoolean("posted"));reply(session,p,notice);assertEquals(1,c.getSystemService(NotificationManager::class.java).activeNotifications.size)
 }
 @Test fun secureReadUsesSignedOwnerAndMissingDeviceKeystoreFailsWithoutPlaintextFallback(){
  val session=start();var p=pending(session,"secret");val request=authorize(session,p);assertEquals("org.example.nativebridge.checks2",request.getJSONObject("owner").getString("id"))
  val missing=SdkSecrets.execute(c){authorize(session,p)};assertTrue(missing.isNull("value"));reply(session,p,missing)
  p=pending(session,"store");val failure=assertThrows(IllegalStateException::class.java){SdkSecrets.execute(c){authorize(session,p)}};assertTrue(failure.message!!.contains("Existing secrets were kept"))
  assertFalse(c.noBackupFilesDir.walk().filter{it.isFile}.any{String(it.readBytes(),Charsets.ISO_8859_1).contains("private fixture")})
 }
 @Test fun revocationBlocksPendingNativeEffectsThroughTheRealBridge(){
  val session=start();val p=pending(session,"notice");AppSdk.sync(c,JSONObject().put("action","permissions").put("id","org.example.nativebridge.checks2").put("grants",JSONArray()))
  assertThrows(IllegalStateException::class.java){SdkNotifications.execute(c,authorize(session,p)){authorize(session,p)}}
  assertTrue(c.getSystemService(NotificationManager::class.java).activeNotifications.isEmpty())
 }
}
