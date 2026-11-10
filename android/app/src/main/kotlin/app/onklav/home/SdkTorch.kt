package app.onklav.home

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import org.json.JSONObject

/** One foreground owner; immutable lease identity prevents old timeouts releasing new leases. */
internal object SdkTorch {
 private data class Lease(val owner:String,val camera:String,val manager:CameraManager,val timeout:Runnable)
 private var lease:Lease?=null
 private val handler=Handler(Looper.getMainLooper())
 fun execute(c:Context,r:JSONObject,authorize:()->Unit):JSONObject=AppSdk.carrierEgress{synchronized(this){
  val owner=r.getJSONObject("owner").getString("session");val a=r.getJSONObject("args");authorize()
  if(!a.getBoolean("enabled")){release(owner);return@carrierEgress JSONObject().put("enabled",false)}
  val seconds=a.getLong("seconds");require(seconds in 1..120)
  check(lease==null||lease?.owner==owner){"The flashlight is being used by another app"}
  release(owner)
  val manager=c.getSystemService(CameraManager::class.java)
  val camera=manager.cameraIdList.firstOrNull{val info=manager.getCameraCharacteristics(it);info.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)==true&&info.get(CameraCharacteristics.LENS_FACING)==CameraCharacteristics.LENS_FACING_BACK}?:error("No flashlight is available")
  lateinit var timeout:Runnable
  timeout=Runnable{synchronized(this){if(lease?.timeout===timeout)release(owner)}};authorize();manager.setTorchMode(camera,true)
  lease=Lease(owner,camera,manager,timeout);handler.postDelayed(timeout,seconds*1000)
  return@carrierEgress JSONObject().put("enabled",true).put("timeout_seconds",seconds)
 }}
 @Synchronized fun release(owner:String){val l=lease?:return;if(l.owner!=owner)return;try{l.manager.setTorchMode(l.camera,false)}finally{handler.removeCallbacks(l.timeout);lease=null}}
 @Synchronized fun clear(){lease?.owner?.let{release(it)}}
}
