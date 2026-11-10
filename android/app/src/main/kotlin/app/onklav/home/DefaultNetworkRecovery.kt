package app.onklav.home

import android.content.Context
import android.net.*
import android.os.Handler
import android.os.Looper

/** Observe the OS-selected Internet route. Never bind the process or request a Wi-Fi route. */
internal class DefaultNetworkRecovery(c:Context,private val recovered:()->Unit):AutoCloseable {
 private val manager=c.getSystemService(ConnectivityManager::class.java)
 private val main=Handler(Looper.getMainLooper())
 private var usable:Network?=manager.activeNetwork?.takeIf{network->manager.getNetworkCapabilities(network)?.let{it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)&&it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)}==true}
 private var closed=false
 private var revision=0L
 private val callback=object:ConnectivityManager.NetworkCallback(){
  override fun onCapabilitiesChanged(network:Network,caps:NetworkCapabilities){
   val valid=caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)&&caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
   if(valid&&network!=usable){usable=network;val ticket=++revision;main.post{if(!closed&&usable==network&&revision==ticket)recovered()}}else if(!valid&&network==usable){usable=null;revision++}
  }
  override fun onLost(network:Network){if(usable==network){usable=null;revision++}}
 }
 init {manager.registerDefaultNetworkCallback(callback,main)}
 override fun close(){if(closed)return;closed=true;revision++;manager.unregisterNetworkCallback(callback)}
}

internal fun validatedInternet(c:Context):Boolean=runCatching{val m=c.getSystemService(ConnectivityManager::class.java);val caps=m.getNetworkCapabilities(m.activeNetwork)?:return@runCatching false;caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)&&caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)}.getOrDefault(false)
