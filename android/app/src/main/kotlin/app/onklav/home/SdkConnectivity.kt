package app.onklav.home

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import org.json.JSONObject

internal object SdkConnectivity {
 fun snapshot(c:Context):JSONObject{
  val manager=c.getSystemService(ConnectivityManager::class.java);val n=manager.getNetworkCapabilities(manager.activeNetwork)
  val transport=when{n==null->"none";n.hasTransport(NetworkCapabilities.TRANSPORT_VPN)->"vpn";n.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)->"wifi";n.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)->"cellular";n.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)->"ethernet";else->"other"}
  return JSONObject().put("connected",n!=null).put("internet",n?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)==true).put("validated",n?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)==true).put("captive_portal",n?.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)==true).put("metered",manager.isActiveNetworkMetered).put("roaming",n!=null&&!n.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)).put("transport",transport)
 }
 fun settings(c:Context,a:JSONObject,authorize:()->Unit):JSONObject{
  val action=when(a.getString("target")){"internet"->Settings.ACTION_WIRELESS_SETTINGS;"wifi"->Settings.ACTION_WIFI_SETTINGS;"bluetooth"->Settings.ACTION_BLUETOOTH_SETTINGS;"location"->Settings.ACTION_LOCATION_SOURCE_SETTINGS;else->error("Unsupported settings page")}
  val i=Intent(action);check(i.resolveActivity(c.packageManager)!=null){"This settings page is unavailable"};authorize();c.startActivity(i);return JSONObject().put("opened",true)
 }
}
