package app.onklav.home

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import org.json.JSONObject

internal object SdkHandoffs {
 fun mime(value:String)=value.length<=100&&value.matches(Regex("[a-z0-9.+-]+/[a-z0-9.+-]+"))
 fun intent(operation:String,mime:String,uri:Uri?=null,text:String?=null):Intent{
  require(mime(mime));require(operation in setOf("view","share"))
  val i=Intent(if(operation=="view")Intent.ACTION_VIEW else Intent.ACTION_SEND).setType(mime)
  if(uri!=null){require(uri.scheme=="content");if(operation=="view")i.setDataAndType(uri,mime)else i.putExtra(Intent.EXTRA_STREAM,uri);i.clipData=ClipData.newRawUri("Selected file",uri);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)}
  if(text!=null){require(operation=="share"&&mime=="text/plain"&&text.length in 1..50000);i.putExtra(Intent.EXTRA_TEXT,text)}
  return i
 }
 @Suppress("DEPRECATION") fun available(c:Context,operation:String,mime:String):Boolean{
  val sample=Uri.Builder().scheme("content").authority(c.packageName+".sdk-handoff").appendPath("probe").build()
  return c.packageManager.queryIntentActivities(intent(operation,mime,sample),PackageManager.MATCH_DEFAULT_ONLY).any{it.activityInfo?.let{a->a.exported&&a.enabled&&a.applicationInfo.enabled&&a.packageName!=c.packageName}==true}
 }
 fun discover(c:Context,a:JSONObject,authorize:()->Unit):JSONObject{authorize();val result=available(c,a.getString("operation"),a.getString("mime"));authorize();return JSONObject().put("available",result)}
 fun launch(c:Context,operation:String,mime:String,uri:Uri?=null,text:String?=null,authorize:()->Unit):JSONObject=AppSdk.carrierEgress{
  check(available(c,operation,mime)){"No compatible Android app is available for this file type"};authorize()
  val base=intent(operation,mime,uri,text);val chooser=Intent.createChooser(base,if(operation=="view")"Open selected file"else"Share selected content")
  if(uri!=null){chooser.clipData=base.clipData;chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)}
  c.startActivity(chooser);JSONObject().put("handed_off",true).put("completed",false)
 }
}
