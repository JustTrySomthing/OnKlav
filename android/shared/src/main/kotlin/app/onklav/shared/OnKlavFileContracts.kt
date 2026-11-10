package app.onklav.shared

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract

/** All host modules and SDK brokers use OnKlav Files. Android's tree grant is
 * reserved for explicitly connecting a new device/provider location. */
object OnKlavFileContracts {
 fun intent(c:Context,mode:String,mimes:Array<String> = arrayOf("*/*"),name:String="File",multiple:Boolean=false)=
  Intent().setClassName(c.packageName,"app.onklav.home.FileBrowserActivity")
   .putExtra("mode",mode).putExtra("mimes",mimes).putExtra("name",name).putExtra("multiple",multiple)
 private fun picked(code:Int,data:Intent?)=if(code==Activity.RESULT_OK)data?.data else null
 class OpenDocument:ActivityResultContract<Array<String>,Uri?>(){
  override fun createIntent(c:Context,input:Array<String>)=intent(c,"open",input)
  override fun parseResult(code:Int,data:Intent?)=picked(code,data)
 }
 class OpenMultipleDocuments:ActivityResultContract<Array<String>,List<Uri>>(){
  override fun createIntent(c:Context,input:Array<String>)=intent(c,"open",input,multiple=true)
  override fun parseResult(code:Int,data:Intent?):List<Uri>{if(code!=Activity.RESULT_OK||data==null)return emptyList();val clips=data.clipData;return if(clips!=null)List(clips.itemCount){clips.getItemAt(it).uri}.distinct()else listOfNotNull(data.data)}
 }
 class CreateDocument(private val mime:String):ActivityResultContract<String,Uri?>(){
  override fun createIntent(c:Context,input:String)=intent(c,"save",arrayOf(mime),input)
  override fun parseResult(code:Int,data:Intent?)=picked(code,data)
 }
 class OpenDocumentTree:ActivityResultContract<Uri?,Uri?>(){
  override fun createIntent(c:Context,input:Uri?)=intent(c,"folder")
  override fun parseResult(code:Int,data:Intent?)=picked(code,data)
 }
}
