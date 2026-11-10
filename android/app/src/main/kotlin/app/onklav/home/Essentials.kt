package app.onklav.home

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

internal const val CLOCK_APP="app.onklav.clock"
internal const val TORCH_APP="app.onklav.flashlight"
internal const val CAMERA_APP="app.onklav.camera"
internal fun essentialAccess(c:Context,id:String,vararg caps:String):Boolean=runCatching{
 val rows=AppSdk.sync(c,JSONObject().put("action","list")) as JSONArray
 (0 until rows.length()).any{val a=rows.getJSONObject(it);a.optBoolean("installed")&&a.getJSONObject("manifest").optString("id")==id&&caps.all{cap->a.getJSONArray("grants").let{g->(0 until g.length()).any{g.optString(it)==cap}}}}
}.getOrDefault(false)
@Composable internal fun EssentialDialog(title:String,close:()->Unit,content:@Composable ColumnScope.()->Unit){
 AlertDialog(onDismissRequest=close,title={Text(title)},text={Column(Modifier.fillMaxWidth().heightIn(max=480.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)},confirmButton={TextButton(onClick=close){Text("Done")}})
}
internal fun elapsedLabel(ms:Long,tenths:Boolean=false):String{val s=ms.coerceAtLeast(0)/1000;val base=if(s>=3600)"%d:%02d:%02d".format(s/3600,s/60%60,s%60)else"%02d:%02d".format(s/60,s%60);return if(tenths)base+".%d".format(ms.coerceAtLeast(0)%1000/100)else base}
