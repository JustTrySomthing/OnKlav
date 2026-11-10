package app.onklav.home

import android.content.Context
import android.media.AudioManager
import android.provider.BlockedNumberContract
import android.telecom.PhoneAccountHandle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*

internal object PhoneLocalSettings {
 private fun key(line:PhoneAccountHandle?)="voicemail:"+(line?.let{it.componentName.flattenToString()+":"+it.id+":"+it.userHandle.toString()}?:"default")
 fun voicemail(c:Context,line:PhoneAccountHandle?)=c.getSharedPreferences("phone-ui",0).getString(key(line),"").orEmpty()
 fun voicemail(c:Context,line:PhoneAccountHandle?,number:String){require(number.isBlank()||PhoneRules.callable(number));c.getSharedPreferences("phone-ui",0).edit().putString(key(line),number).apply()}
 fun blocked(c:Context):List<Pair<Long,String>> {
  check(PhoneAccess.role(c)){"Complete Phone setup to manage blocked numbers."}
  val rows=mutableListOf<Pair<Long,String>>()
  c.contentResolver.query(BlockedNumberContract.BlockedNumbers.CONTENT_URI,arrayOf("_id",BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER),null,null,"_id DESC")?.use{q->while(q.moveToNext()&&rows.size<500)rows+=q.getLong(0) to q.getString(1).orEmpty()}
  return rows
 }
 fun unblock(c:Context,id:Long){check(PhoneAccess.role(c));check(c.contentResolver.delete(BlockedNumberContract.BlockedNumbers.CONTENT_URI,"_id = ?",arrayOf(id.toString()))==1){"Number was already removed."}}
}

/** Internal settings: no external Settings/Contacts/dialer intents. Telecom owns carrier provisioning. */
@Composable internal fun PhoneSettingsDialog(m:PhoneModel,tones:Boolean,vibrate:Boolean,onTones:(Boolean)->Unit,onVibrate:(Boolean)->Unit,setup:()->Unit,close:()->Unit){
 val c=LocalContext.current;val scope=rememberCoroutineScope();val audio=remember{c.getSystemService(AudioManager::class.java)}
 var section by remember{mutableStateOf("")};var error by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)}
 var rows by remember{mutableStateOf<List<Pair<Long,String>>>(emptyList())};var removal by remember{mutableStateOf<Pair<Long,String>?>(null)}
 var line by remember{mutableStateOf(m.lines.firstOrNull()?.handle)};var number by remember(line){mutableStateOf(PhoneLocalSettings.voicemail(c,line))};var saved by remember(line){mutableStateOf(false)}
 var ring by remember{mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_RING).toFloat())};val ringMax=audio.getStreamMaxVolume(AudioManager.STREAM_RING).coerceAtLeast(1)
 fun load(){busy=true;scope.launch{try{rows=withContext(Dispatchers.IO){PhoneLocalSettings.blocked(c)};error=""}catch(_:Exception){error="Blocked numbers are unavailable. Review Phone setup."}finally{busy=false}}}
 AlertDialog(onDismissRequest=close,title={Text(when(section){"blocked"->"Blocked numbers";"voicemail"->"Voicemail";else->"Phone settings"})},text={Column(Modifier.fillMaxWidth().heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
  if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
  when(section){
   "blocked"->{Text("Blocked numbers cannot call or text you. Emergency-service exceptions are controlled by Android.",style=MaterialTheme.typography.bodySmall);if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())else if(rows.isEmpty())Text("No blocked numbers");rows.forEach{row->Row(verticalAlignment=Alignment.CenterVertically){Text(PhoneRules.display(row.second),Modifier.weight(1f));IconButton(onClick={removal=row},enabled=!busy){Icon(Icons.Outlined.Close,"Unblock ${row.second}")}}};if(rows.size==500)Text("Showing the latest 500 numbers.",style=MaterialTheme.typography.bodySmall)}
   "voicemail"->{Text("Enter the voicemail access number supplied by your carrier. Leave blank to use the SIM’s number.",style=MaterialTheme.typography.bodyMedium);m.lines.forEach{item->Row(verticalAlignment=Alignment.CenterVertically){RadioButton(selected=line==item.handle,onClick={line=item.handle});Text(item.name)}};OutlinedTextField(number,{number=PhoneRules.input(it);saved=false},label={Text("Voicemail number")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Phone),modifier=Modifier.fillMaxWidth());Button(onClick={PhoneLocalSettings.voicemail(c,line,number);saved=true},enabled=number.isBlank()||PhoneRules.callable(number)){Text(if(saved)"Saved"else"Save voicemail number")}}
   else->{
    Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surfaceContainerHigh){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){Text(if(PhoneAccess.role(c))"OnKlav is your Phone app"else"Finish Phone setup",style=MaterialTheme.typography.titleSmall);Text("Calls, contacts and call controls stay in OnKlav.",style=MaterialTheme.typography.bodySmall);if(!OnKlavPermissions.lockScreen(c))Text("Allow full-screen alerts in setup to see incoming calls on the lock screen.",style=MaterialTheme.typography.bodySmall);TextButton(onClick=setup){Text("Setup & permissions")}}}
    Row(verticalAlignment=Alignment.CenterVertically){Text("Keypad sounds",Modifier.weight(1f));Switch(tones,onTones)}
    Row(verticalAlignment=Alignment.CenterVertically){Text("Keypad vibration",Modifier.weight(1f));Switch(vibrate,onVibrate)}
    HorizontalDivider();Text("Ring volume",style=MaterialTheme.typography.titleSmall)
    Slider(modifier=Modifier.semantics{contentDescription="Ring volume"},value=ring,valueRange=0f..ringMax.toFloat(),onValueChange={ring=it},onValueChangeFinished={try{audio.setStreamVolume(AudioManager.STREAM_RING,ring.toInt(),0);ring=audio.getStreamVolume(AudioManager.STREAM_RING).toFloat();error=""}catch(_:SecurityException){error="Android’s sound policy prevents changing ring volume right now."}})
    TextButton(onClick={section="voicemail";error=""}){Icon(PhoneIcons.Voicemail,null);Spacer(Modifier.width(8.dp));Text("Voicemail")}
    TextButton(onClick={section="blocked";load()}){Icon(PhoneIcons.End,null);Spacer(Modifier.width(8.dp));Text("Blocked numbers")}
    if(m.lines.isNotEmpty()){HorizontalDivider();Text("Calling accounts",style=MaterialTheme.typography.titleSmall);m.lines.forEach{Text(it.name,style=MaterialTheme.typography.bodyMedium)};Text("Choose a SIM when placing a call. Carrier forwarding and call-waiting provisioning remain controlled by your network.",style=MaterialTheme.typography.bodySmall)}
   }
  }
 }},confirmButton={TextButton(onClick={if(section.isEmpty())close()else{section="";error=""}}){Text(if(section.isEmpty())"Done"else"Back")}})
 removal?.let{row->PhoneConfirm("Unblock ${PhoneRules.display(row.second)}?","This number will be able to call and text you again.",{removal=null}){removal=null;busy=true;scope.launch{try{withContext(Dispatchers.IO){PhoneLocalSettings.unblock(c,row.first)};load()}catch(_:Exception){busy=false;error="Could not unblock this number. Review Phone setup."}}}}
}
