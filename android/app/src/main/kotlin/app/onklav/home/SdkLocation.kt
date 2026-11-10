package app.onklav.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import kotlin.coroutines.resume

internal object SdkLocation {
 internal fun approximate(value:Double)=kotlin.math.round(value*50.0)/50.0
 fun manual(a:JSONObject):JSONObject{
  val lat=a.getDouble("latitude");val lon=a.getDouble("longitude");require(lat.isFinite()&&lon.isFinite()&&lat in -90.0..90.0&&lon in -180.0..180.0)
  return JSONObject().put("latitude",lat).put("longitude",lon).put("source","manual").put("accuracy_m",JSONObject.NULL)
 }
 suspend fun current(c:Context,authorize:()->Unit):JSONObject{
  // Approximate foreground permission is enough; never request background access.
  if(ContextCompat.checkSelfPermission(c,Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED)return JSONObject().put("available",false).put("manual_fallback",true).put("reason","permission_required")
  val manager=c.getSystemService(LocationManager::class.java)
  val provider=manager.getProviders(true).firstOrNull{it==LocationManager.NETWORK_PROVIDER}?:return JSONObject().put("available",false).put("manual_fallback",true).put("reason","location_unavailable")
  val location=withTimeoutOrNull(15000){suspendCancellableCoroutine<Location>{continuation->
   lateinit var listener:LocationListener
   listener=object:LocationListener{
    override fun onLocationChanged(location:Location){manager.removeUpdates(this);if(continuation.isActive)continuation.resume(location)}
    override fun onProviderEnabled(provider:String){}
    override fun onProviderDisabled(provider:String){}
    @Deprecated("Android legacy callback") override fun onStatusChanged(provider:String?,status:Int,extras:Bundle?){}
   }
   continuation.invokeOnCancellation{manager.removeUpdates(listener)}
   authorize();manager.requestLocationUpdates(provider,0L,0f,listener,Looper.getMainLooper())
  }}
  authorize()
  if(location==null)return JSONObject().put("available",false).put("manual_fallback",true).put("reason","timeout")
  return JSONObject().put("available",true).put("source","device").put("latitude",approximate(location.latitude)).put("longitude",approximate(location.longitude)).put("accuracy_m",location.accuracy.toDouble().coerceAtLeast(2200.0)).put("timestamp_ms",location.time)
 }
}
