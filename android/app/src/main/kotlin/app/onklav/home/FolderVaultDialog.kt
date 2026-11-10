package app.onklav.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import org.json.JSONObject

/** Shared by Files and every SDK/host picker. Passwords never enter saved state. */
@Composable internal fun FolderVaultDialog(status:JSONObject,close:()->Unit,completed:()->Unit){
 val c=LocalContext.current;val scope=rememberCoroutineScope()
 if(!status.optBoolean("encrypted")){AlertDialog(onDismissRequest=close,title={Text("Use Vault")},text={Text("Folder encryption has moved to the Vault app.")},confirmButton={TextButton(onClick=close){Text("Done")}});return}
 var mode by remember(status){mutableStateOf(if(!status.optBoolean("encrypted"))"protect"else if(status.optBoolean("locked"))"unlock"else"manage")}
 var password by remember{mutableStateOf("")};var replacement by remember{mutableStateOf("")};var confirm by remember{mutableStateOf("")}
 var visible by remember{mutableStateOf(false)};var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}
 fun run(action:String){scope.launch{busy=true;error=null;val p=password.toCharArray();val n=replacement.toCharArray();try{
  val engine=FolderVaults.engine(c);val id=status.getString("id")
  when(action){"protect"->engine.protect(id,p);"unlock"->engine.unlock(id,p);"lock"->engine.lock(id);"change"->engine.changePassword(id,p,n);"remove"->engine.removeProtection(id,p)}
  password="";replacement="";confirm="";completed()
 }catch(e:CancellationException){throw e}catch(e:Exception){error=when(e){is javax.crypto.AEADBadTagException->"The password is incorrect, or the protected data has changed.";else->e.message?:"The folder could not be updated. Its data has been kept."}}finally{p.fill('\u0000');n.fill('\u0000');busy=false}}}
 val title=when(mode){"protect"->"Protect folder";"unlock"->"Unlock folder";"change"->"Change password";"remove"->"Remove protection";else->"Folder protection"}
 AlertDialog(onDismissRequest={if(!busy)close()},icon={Icon(Icons.Outlined.Lock,null)},title={Text(title)},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
  Text(status.optString("name","Folder"),style=MaterialTheme.typography.titleSmall)
  if(mode=="manage"){
   Text("This folder is unlocked. It locks when OnKlav leaves the screen.")
   FilledTonalButton(onClick={run("lock")},enabled=!busy,modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.Lock,null);Spacer(Modifier.width(8.dp));Text("Lock now")}
   OutlinedButton(onClick={mode="change"},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("Change password")}
   TextButton(onClick={mode="remove"},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("Remove protection")}
  }else{
   Text(when(mode){"protect"->"Encrypts files and their names. Use at least 8 characters and a password you can remember: there is no password recovery. Up to 240 MB and 1,000 items per folder.";"unlock"->"Enter your password to open the encrypted contents.";"remove"->"Your files will stay in this folder without password protection.";else->"Enter the current password and a new password of at least 8 characters."},style=MaterialTheme.typography.bodyMedium)
   OutlinedTextField(password,{password=it.take(256)},singleLine=true,enabled=!busy,label={Text(if(mode=="change")"Current password"else"Password")},visualTransformation=if(visible)VisualTransformation.None else PasswordVisualTransformation(),trailingIcon={IconButton(onClick={visible=!visible}){Icon(if(visible)Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,if(visible)"Hide password"else"Show password")}},shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth())
   if(mode=="change")OutlinedTextField(replacement,{replacement=it.take(256)},label={Text("New password")},singleLine=true,enabled=!busy,visualTransformation=if(visible)VisualTransformation.None else PasswordVisualTransformation(),shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth())
   if(mode=="protect"||mode=="change")OutlinedTextField(confirm,{confirm=it.take(256)},label={Text("Confirm password")},singleLine=true,enabled=!busy,visualTransformation=if(visible)VisualTransformation.None else PasswordVisualTransformation(),shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth())
  }
  if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
  error?.let{Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
 }},confirmButton={if(mode!="manage")Button(enabled=!busy&&password.length>=8&&(mode!="protect"||password==confirm)&&(mode!="change"||replacement.length>=8&&replacement==confirm),onClick={run(mode)}){Text(when(mode){"protect"->"Encrypt & lock";"unlock"->"Unlock";"change"->"Save password";else->"Remove protection"})}},dismissButton={TextButton(enabled=!busy,onClick=close){Text("Cancel")}})
}

@Composable internal fun FolderLockedPane(status:JSONObject,unlock:()->Unit){
 Column(Modifier.fillMaxWidth().padding(32.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
  Icon(Icons.Outlined.Lock,null,Modifier.size(40.dp),tint=MaterialTheme.colorScheme.primary)
  Text("Folder locked",style=MaterialTheme.typography.headlineSmall)
  Text("Enter its password to view and edit the contents.",color=MaterialTheme.colorScheme.onSurfaceVariant)
  Button(onClick=unlock){Text("Unlock folder")}
 }
}
