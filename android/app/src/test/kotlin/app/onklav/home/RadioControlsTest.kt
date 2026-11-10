package app.onklav.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class RadioControlsTest {
 private val id="aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
 private fun context():Context=ApplicationProvider.getApplicationContext<Context>().also{SdkRadioHealth.restore(it);SdkRadioHealth.configure(it,true,false)}
 @Test fun onlyConfirmedUnplayableResponsesAreAutomaticallyHidden(){
  val c=context()
  for(code in listOf(429,503,401,403,500)){
   val f=SdkRadioHealth.http(code);SdkRadioHealth.record(c,id,f);assertFalse("Hidden for HTTP$code",SdkRadio.hidden(id));assertEquals(f.label,SdkRadioHealth.notices.value[id]?.label)
  }
  SdkRadioHealth.record(c,id,SdkRadioHealth.http(404));assertTrue(SdkRadio.hidden(id));assertEquals("Unavailable",SdkRadioHealth.notices.value[id]?.label)
  SdkRadioHealth.record(c,id,SdkRadioHealth.http(503));assertFalse("A new temporary result should clear automatic suppression",SdkRadio.hidden(id))
 }
 @Test fun usersCanDisableAutomaticHidingShowHiddenManuallyHideAndRestore(){
  val c=context();SdkRadioHealth.record(c,id,SdkRadioHealth.http(410));assertTrue(SdkRadio.hidden(id));SdkRadioHealth.configure(c,false,null);assertFalse(SdkRadio.hidden(id))
  SdkRadio.hide(c,id);assertTrue("Manual choice still applies with automatic hiding off",SdkRadio.hidden(id));SdkRadioHealth.configure(c,null,true);assertFalse(SdkRadio.hidden(id));SdkRadioHealth.configure(c,null,false);assertTrue(SdkRadio.hidden(id));SdkRadio.retryUnavailable(c);assertFalse(SdkRadio.hidden(id));assertFalse(SdkRadioHealth.options.value.autoHide)
 }
 @Test fun successfulRetryRemovesOldFailureAndDoesNotChangeUserSettings(){
  val c=context();SdkRadioHealth.configure(c,true,true);SdkRadio.hide(c,id);assertNotNull(SdkRadioHealth.notices.value[id]);SdkRadioHealth.recovered(c,id);assertNull(SdkRadioHealth.notices.value[id]);assertFalse(id in SdkRadio.unavailable.value);assertTrue(SdkRadioHealth.options.value.showHidden)
 }
 @Test fun networkTimeoutsAndOfflineDnsDoNotClaimStationClosure(){
  val c=context();val timeout=SdkRadioHealth.classify(java.net.SocketTimeoutException());assertEquals("Connection timed out",timeout.label);SdkRadioHealth.record(c,id,timeout);assertFalse(SdkRadio.hidden(id));val offline=SdkRadioHealth.classify(java.net.UnknownHostException());assertEquals("connection",offline.code);assertFalse(offline.hide)
 }
}
