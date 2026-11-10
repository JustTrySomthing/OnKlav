package app.onklav.home

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.net.InetAddress

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class NetworkDownloadRecoveryTest {
 @Test fun cellularNat64AllowsPublicDestinationsAndRejectsPrivateOnes(){
  assertTrue(SdkFeedService.isPublic(InetAddress.getByName("64:ff9b::808:808")))
  for(ip in listOf("64:ff9b::a00:1","64:ff9b::7f00:1","64:ff9b::a9fe:1","64:ff9b::c0a8:101","64:ff9b::6440:1","fd00::1","::1","2001:db8::1"))assertFalse(ip,SdkFeedService.isPublic(InetAddress.getByName(ip)))
  assertTrue(SdkFeedService.isPublic(InetAddress.getByName("2606:4700:4700::1111")))
 }
 @Test fun reconnectOnlyForNewValidatedDefaultRouteAndUnregisterOnClose(){
  val c=ApplicationProvider.getApplicationContext<Context>();val cm=c.getSystemService(ConnectivityManager::class.java);val shadow=shadowOf(cm);val before=shadow.networkCallbacks.toSet();var events=0
  val observer=DefaultNetworkRecovery(c){events++};val callback=(shadow.networkCallbacks.toSet()-before).single()
  val wifi=org.robolectric.shadows.ShadowNetwork.newInstance(10);val mobile=org.robolectric.shadows.ShadowNetwork.newInstance(11);val caps=org.robolectric.shadows.ShadowNetworkCapabilities.newInstance();shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
  callback.onCapabilitiesChanged(wifi,caps);shadowOf(android.os.Looper.getMainLooper()).idle();assertEquals(0,events)
  shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);callback.onCapabilitiesChanged(wifi,caps);callback.onCapabilitiesChanged(wifi,caps);shadowOf(android.os.Looper.getMainLooper()).idle();assertEquals(1,events)
  callback.onLost(wifi);callback.onCapabilitiesChanged(mobile,caps);shadowOf(android.os.Looper.getMainLooper()).idle();assertEquals(2,events)
  observer.close();callback.onCapabilitiesChanged(wifi,caps);shadowOf(android.os.Looper.getMainLooper()).idle();assertEquals(2,events);assertEquals(before,shadow.networkCallbacks.toSet())
 }
 private fun stream(id:Int,h:Int,audio:Boolean,mime:String)=YoutubeStream(id,"https://r1.googlevideo.com/videoplayback?itag=$id",mime,h,audio)
 @Test fun formatsListActualHeightsAndPairSeparateAudioWithoutUpscaling(){
  val video=stream(137,1080,false,"video/mp4");val audio=stream(140,0,true,"audio/mp4");val low=stream(18,360,true,"video/mp4");val r=YoutubeResolver.Result("A video",listOf(video,audio,low))
  assertEquals(listOf(360,1080),r.heights);assertEquals(video to audio,r.select(false,1080));assertEquals(low to null,r.select(false,360));assertEquals(audio to null,r.select(true,0));assertThrows(IllegalStateException::class.java){r.select(false,480)}
  assertTrue(YoutubeResolver.Result("Silent track",listOf(video)).heights.isEmpty())
 }
}
