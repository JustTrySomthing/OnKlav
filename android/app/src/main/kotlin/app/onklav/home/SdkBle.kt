package app.onklav.home

import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Selection never exports a device address. Each GATT transaction closes in finally. */
internal object SdkBle {
 data class Choice(val label:String,val device:BluetoothDevice)
 private data class Selected(val device:BluetoothDevice,val service:UUID,val until:Long)
 private val selected=ConcurrentHashMap<String,Selected>()
 private val active=ConcurrentHashMap<String,BluetoothGatt>()
 fun clear(owner:String){selected.remove(owner);active.remove(owner)?.let{runCatching{it.disconnect()};it.close()}}
 fun clearAll(){(selected.keys+active.keys).forEach{clear(it)}}
 suspend fun scan(c:Context,service:String,authorize:()->Unit):List<Choice>{
  val uuid=UUID.fromString(service);val scanner=c.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner?:error("Turn on Bluetooth in Android Settings")
  val found=ConcurrentHashMap<String,Choice>();var failure=false
  val callback=object:ScanCallback(){override fun onScanFailed(code:Int){failure=true};override fun onScanResult(type:Int,result:ScanResult){if(found.size<20)found.putIfAbsent(result.device.address,Choice((result.scanRecord?.deviceName?:"Bluetooth device").take(60),result.device))}}
  authorize();try{scanner.startScan(listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(uuid)).build()),ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build(),callback);delay(8000);authorize();check(!failure){"Bluetooth scan unavailable"};return found.values.toList()}finally{scanner.stopScan(callback)}
 }
 fun select(owner:String,service:String,choice:Choice){selected[owner]=Selected(choice.device,UUID.fromString(service),android.os.SystemClock.elapsedRealtime()+120000)}
 suspend fun execute(c:Context,r:JSONObject,authorize:()->Unit):JSONObject=withTimeout(15000){
  val owner=r.getJSONObject("owner").getString("session");val a=r.getJSONObject("args");val operation=r.getString("service")
  if(operation=="ble.disconnect"){clear(owner);return@withTimeout JSONObject().put("disconnected",true)}
  val choice=selected[owner]?.takeIf{it.until>android.os.SystemClock.elapsedRealtime()}?:error("Choose a Bluetooth device again")
  val wanted=if(operation=="ble.connect")null else UUID.fromString(a.getString("characteristic"))
  var gatt:BluetoothGatt?=null
  try{suspendCancellableCoroutine<JSONObject>{continuation->
   fun fail(message:String){if(continuation.isActive)continuation.resumeWithException(IllegalStateException(message))}
   fun done(value:JSONObject){try{authorize();if(continuation.isActive)continuation.resume(value)}catch(e:Exception){if(continuation.isActive)continuation.resumeWithException(e)}}
   fun bytes(value:ByteArray):JSONObject{if(value.size>512){fail("Bluetooth response exceeds512 bytes");return JSONObject()};return JSONObject().put("hex",value.joinToString(""){"%02x".format(it.toInt() and 255)})}
   val callback=object:BluetoothGattCallback(){
    override fun onConnectionStateChange(g:BluetoothGatt,status:Int,state:Int){if(status!=BluetoothGatt.GATT_SUCCESS){fail("Bluetooth connection failed");return};if(state==BluetoothProfile.STATE_CONNECTED){if(!g.discoverServices())fail("Bluetooth discovery unavailable")}else if(state==BluetoothProfile.STATE_DISCONNECTED)fail("Bluetooth device disconnected")}
    @Suppress("DEPRECATION") override fun onServicesDiscovered(g:BluetoothGatt,status:Int){try{
     authorize();if(status!=BluetoothGatt.GATT_SUCCESS){fail("Bluetooth service discovery failed");return}
     val service=g.getService(choice.service)?:error("Selected service is unavailable")
     if(wanted==null){done(JSONObject().put("connected",true).put("transactional",true));return}
     val ch=service.getCharacteristic(wanted)?:error("Selected characteristic is unavailable")
     when(operation){
      "ble.read"->check(g.readCharacteristic(ch)){"Characteristic cannot be read"}
      "ble.write"->{val hex=a.getString("hex");require(hex.length<=1024&&hex.length%2==0&&hex.matches(Regex("[0-9a-f]*")));ch.writeType=BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;ch.value=hex.chunked(2).map{it.toInt(16).toByte()}.toByteArray();check(g.writeCharacteristic(ch)){"Characteristic cannot be written"}}
      "ble.notify"->{check(g.setCharacteristicNotification(ch,true));val descriptor=ch.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))?:error("Notification descriptor unavailable");descriptor.value=if(ch.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY!=0)BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.ENABLE_INDICATION_VALUE;check(g.writeDescriptor(descriptor))}
      else->error("Unsupported Bluetooth operation")
     }
    }catch(e:Exception){fail(e.message?:"Bluetooth operation failed")}}
    @Suppress("DEPRECATION") override fun onCharacteristicRead(g:BluetoothGatt,ch:BluetoothGattCharacteristic,status:Int){if(status==0)done(bytes(ch.value?:byteArrayOf()))else fail("Bluetooth read failed")}
    override fun onCharacteristicWrite(g:BluetoothGatt,ch:BluetoothGattCharacteristic,status:Int){if(status==0)done(JSONObject().put("written",true))else fail("Bluetooth write failed")}
    override fun onDescriptorWrite(g:BluetoothGatt,d:BluetoothGattDescriptor,status:Int){if(status!=0)fail("Bluetooth notifications unavailable")}
    @Suppress("DEPRECATION") override fun onCharacteristicChanged(g:BluetoothGatt,ch:BluetoothGattCharacteristic){if(ch.uuid==wanted)done(bytes(ch.value?:byteArrayOf()))}
   }
   continuation.invokeOnCancellation{gatt?.disconnect();gatt?.close()}
   authorize();gatt=choice.device.connectGatt(c,false,callback,BluetoothDevice.TRANSPORT_LE)
   if(gatt==null)fail("Bluetooth connection unavailable")else active[owner]=gatt!!
  }}finally{gatt?.let{active.remove(owner,it)};gatt?.disconnect();gatt?.close()}
 }
}
