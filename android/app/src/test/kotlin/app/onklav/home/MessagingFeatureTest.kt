package app.onklav.home
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class)
class MessagingFeatureTest {
 private val c get()=ApplicationProvider.getApplicationContext<Context>()
 @Test @Config(sdk=[29]) fun olderPhonesUseTheSupportedTelephonyFeature(){
  shadowOf(c.packageManager).setSystemFeature(PackageManager.FEATURE_TELEPHONY,true)
  MessagingTransport.enableCarrierTrial(c);assertTrue(MessagingTransport.carrierEnabled(c))
 }
 @Test @Config(sdk=[35]) fun voiceHardwareAloneDoesNotEnableMessagingOnNewerPhones(){
  shadowOf(c.packageManager).setSystemFeature(PackageManager.FEATURE_TELEPHONY,true)
  shadowOf(c.packageManager).setSystemFeature(PackageManager.FEATURE_TELEPHONY_MESSAGING,false)
  assertThrows(IllegalStateException::class.java){MessagingTransport.enableCarrierTrial(c)}
  assertFalse(MessagingTransport.carrierEnabled(c))
  shadowOf(c.packageManager).setSystemFeature(PackageManager.FEATURE_TELEPHONY_MESSAGING,true)
  MessagingTransport.enableCarrierTrial(c);assertTrue(MessagingTransport.carrierEnabled(c))
 }
}
