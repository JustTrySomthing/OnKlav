package app.onklav.home

import android.content.Context
import android.net.*
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiNetworkSpecifier
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Wi-Fi joining and LAN discovery are separate grants; neither exports radio identifiers. */
internal object SdkNearby {
 data class Choice(val label:String,val service:NsdServiceInfo)
 private data class WifiLease(val manager:ConnectivityManager,val callback:ConnectivityManager.NetworkCallback,val network:Network,val timeout:Runnable)
 private data class Endpoint(val address:InetAddress,val port:Int,val until:Long)
 private val wifi=ConcurrentHashMap<String,WifiLease>();private val endpoints=ConcurrentHashMap<String,Endpoint>()
 private val sockets=ConcurrentHashMap<String,java.net.Socket>()
 private val handler=android.os.Handler(android.os.Looper.getMainLooper())
 fun clear(owner:String){sockets.remove(owner)?.let{runCatching{it.close()}};endpoints.remove(owner);wifi.remove(owner)?.let{handler.removeCallbacks(it.timeout);runCatching{it.manager.unregisterNetworkCallback(it.callback)}}}
 fun clearAll(){(wifi.keys+endpoints.keys).forEach{clear(it)}}
 suspend fun join(c:Context,owner:String,a:JSONObject,authorize:()->Unit):JSONObject=withTimeout(30000){
  val ssid=a.getString("ssid");require(ssid.isNotBlank()&&ssid.toByteArray().size<=32);val password=a.optString("passphrase");require(password.isEmpty()||password.length in 8..63)
  clear(owner);val manager=c.getSystemService(ConnectivityManager::class.java)
  val spec=WifiNetworkSpecifier.Builder().setSsid(ssid).apply{if(password.isNotEmpty())setWpa2Passphrase(password)}.build()
  val request=NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).setNetworkSpecifier(spec).build()
  var callback:ConnectivityManager.NetworkCallback?=null;var accepted=false
  try{suspendCancellableCoroutine<JSONObject>{continuation->
   val cb=object:ConnectivityManager.NetworkCallback(){
    override fun onAvailable(network:Network){try{authorize();val timeout=Runnable{if(wifi[owner]?.callback===this)clear(owner)};wifi[owner]=WifiLease(manager,this,network,timeout);handler.postDelayed(timeout,120000);if(continuation.isActive){accepted=true;continuation.resume(JSONObject().put("connected",true).put("timeout_seconds",120))}else clear(owner)}catch(e:Exception){if(continuation.isActive)continuation.resumeWithException(e)}}
    override fun onUnavailable(){if(continuation.isActive)continuation.resumeWithException(IllegalStateException("Wi-Fi connection was cancelled or unavailable"))}
    override fun onLost(network:Network){if(wifi[owner]?.callback===this)clear(owner)}
   };callback=cb;continuation.invokeOnCancellation{clear(owner);runCatching{manager.unregisterNetworkCallback(cb)}};authorize();manager.requestNetwork(request,cb,25000)
  }}finally{if(!accepted)callback?.let{runCatching{manager.unregisterNetworkCallback(it)}}}
 }
 suspend fun discover(c:Context,type:String,authorize:()->Unit):List<Choice>{
  require(type.matches(Regex("_[a-zA-Z0-9-]{1,30}\\._tcp\\.")))
  val manager=c.getSystemService(NsdManager::class.java);val found=ConcurrentHashMap<String,Choice>();var failure=false
  val listener=object:NsdManager.DiscoveryListener{
   override fun onDiscoveryStarted(type:String){};override fun onDiscoveryStopped(type:String){}
   override fun onStartDiscoveryFailed(type:String,error:Int){failure=true};override fun onStopDiscoveryFailed(type:String,error:Int){}
   override fun onServiceFound(info:NsdServiceInfo){if(found.size<20)found[info.serviceName]=Choice(info.serviceName.take(60),info)}
   override fun onServiceLost(info:NsdServiceInfo){found.remove(info.serviceName)}
  }
  authorize();try{manager.discoverServices(type,NsdManager.PROTOCOL_DNS_SD,listener);delay(5000);authorize();check(!failure){"Local discovery is unavailable"};return found.values.toList()}finally{runCatching{manager.stopServiceDiscovery(listener)}}
 }
 fun local(address:InetAddress)=!address.isLoopbackAddress&&!address.isAnyLocalAddress&&!address.isMulticastAddress&&(address.isSiteLocalAddress||address.isLinkLocalAddress||(address.address.size==16&&(address.address[0].toInt() and 0xfe)==0xfc))
 @Suppress("DEPRECATION") suspend fun select(c:Context,owner:String,choice:Choice,authorize:()->Unit)=withTimeout(10000){
  val manager=c.getSystemService(NsdManager::class.java)
  val info=suspendCancellableCoroutine<NsdServiceInfo>{continuation->manager.resolveService(choice.service,object:NsdManager.ResolveListener{
   override fun onResolveFailed(info:NsdServiceInfo,error:Int){if(continuation.isActive)continuation.resumeWithException(IllegalStateException("Local service is unavailable"))}
   override fun onServiceResolved(info:NsdServiceInfo){if(continuation.isActive)continuation.resume(info)}
  })}
  require(local(info.host)&&info.port in 1..65535){"Choose a service on the local network"};authorize();endpoints[owner]=Endpoint(info.host,info.port,android.os.SystemClock.elapsedRealtime()+120000)
 }
 suspend fun exchange(owner:String,a:JSONObject,authorize:()->Unit):JSONObject=withContext(Dispatchers.IO){
  val endpoint=endpoints[owner]?.takeIf{it.until>android.os.SystemClock.elapsedRealtime()}?:error("Choose a local service again")
  val hex=a.getString("hex");require(hex.length<=8192&&hex.length%2==0&&hex.matches(Regex("[a-f0-9]*")));val bytes=hex.chunked(2).map{it.toInt(16).toByte()}.toByteArray()
  // One bounded request and one bounded response; no arbitrary guest address or port.
  val socket=wifi[owner]?.network?.socketFactory?.createSocket()?:java.net.Socket()
  sockets[owner]=socket
  try{socket.use{authorize();it.soTimeout=5000;it.connect(InetSocketAddress(endpoint.address,endpoint.port),5000);authorize();it.getOutputStream().write(bytes);it.getOutputStream().flush();val response=ByteArray(4096);val count=it.getInputStream().read(response);authorize();JSONObject().put("hex",response.take(count.coerceAtLeast(0)).joinToString(""){b->"%02x".format(b.toInt() and 255)})}}finally{sockets.remove(owner,socket)}
 }
}
