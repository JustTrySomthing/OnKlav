package app.onklav.home

import android.content.Context
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import java.util.UUID

@Composable internal fun MailAccountDialog(initial:MailAccount,m:MailModel,onClose:()->Unit,onWeb:(String)->Unit){
 var a by remember(initial.id){mutableStateOf(initial)};var password by remember{mutableStateOf("")};var smtpPassword by remember{mutableStateOf("")};var showPassword by remember{mutableStateOf(false)};var advanced by remember{mutableStateOf(initial.provider=="Other")};var directions by remember{mutableStateOf(false)};var preferences by remember{mutableStateOf(false)};var remove by remember{mutableStateOf(false)};var error by remember{mutableStateOf("")};val existing=m.accounts.any{it.id==initial.id}
 fun connect(){error="";try{a.validate();if(password.isBlank()&&(!existing))error=if(MailSetup.requiresAppPassword(a.provider))"Enter your app password"else"Enter your mail password" else m.connect(a,password,smtpPassword,onClose)}catch(e:Exception){error=MailMime.error(e)}}
 MailDialog(if(existing)"Account settings" else"Add email account",{if(!m.busy)onClose()}){
  if(!existing)Text("Email provider",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
  if(!existing)MailChoices(MailSetup.labels,MailSetup.label(a.provider)){label->val provider=MailSetup.provider(label);a=MailAccount.preset(provider,a.email).copy(id=a.id,name=a.name);password="";smtpPassword="";advanced=provider=="Other"}


  if(!existing&&a.provider=="Other")Text("Enter the secure IMAP and SMTP settings from the company hosting your mailbox.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
  MailField("Email address",a.email,!m.busy){a=a.withAddress(it.trim())};MailField("Your name",a.name,!m.busy){a=a.copy(name=it.take(120).replace('\r',' ').replace('\n',' '))}
  if(MailSetup.hasDirections(a.provider))TextButton(onClick={directions=true},enabled=!m.busy){Icon(Icons.Outlined.Info,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("Setup directions")}
   Text(when {
      a.provider == "GMX" -> "Enable IMAP in GMX first. Use an app password if two-factor authentication is enabled."
      MailSetup.requiresAppPassword(a.provider) -> "Use a provider-issued app password."
      else -> "Use the password supplied by your mail provider. Open your provider’s website in your browser for setup or password changes."
   },style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
   OutlinedTextField(value=password,onValueChange={password=it},label={Text(if(existing)"New password (optional)" else if(MailSetup.requiresAppPassword(a.provider))"App password"else if(a.provider=="GMX")"GMX password or app password"else"Password")},visualTransformation=if(showPassword)VisualTransformation.None else PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(autoCorrect=false,keyboardType=KeyboardType.Password),singleLine=true,enabled=!m.busy,modifier=Modifier.fillMaxWidth(),trailingIcon={IconButton(onClick={showPassword=!showPassword}){Icon(if(showPassword)Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,"Show or hide password")}})
   if (MailSetup.requiresAppPassword(a.provider)) TextButton(
       onClick = { onWeb(MailSetup.accountSettings(a.provider)) }, enabled = !m.busy,
   ) { Text("Get an app password") }
   if (!existing && a.provider == "GMX") TextButton(
       onClick = { onWeb(MailSetup.gmxSignup) }, enabled = !m.busy,
   ) { Text("Create a GMX account") }
  TextButton(onClick={advanced=!advanced}){Text(if(advanced)"Hide server settings" else"Server settings")}
  if(advanced){Text("Incoming · IMAP over TLS",style=MaterialTheme.typography.titleSmall);MailField("Server",a.imap,!m.busy){a=a.copy(imap=it.trim())};MailPort("Port",a.imapPort,!m.busy){a=a.copy(imapPort=it)};MailField("Incoming username",a.user,!m.busy){a=a.copy(user=it.trim())};Text("Outgoing · SMTP",style=MaterialTheme.typography.titleSmall);MailField("Server",a.smtp,!m.busy){a=a.copy(smtp=it.trim())};MailChoices(listOf("TLS","STARTTLS"),if(a.smtpTls)"STARTTLS" else"TLS"){a=a.copy(smtpTls=it=="STARTTLS",smtpPort=if(it=="STARTTLS")587 else 465)};MailPort("Port",a.smtpPort,!m.busy){a=a.copy(smtpPort=it)};MailField("Outgoing username",a.smtpUser,!m.busy){a=a.copy(smtpUser=it.trim())};OutlinedTextField(value=smtpPassword,onValueChange={smtpPassword=it},label={Text("Outgoing password (if different)")},singleLine=true,visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(autoCorrect=false,keyboardType=KeyboardType.Password),modifier=Modifier.fillMaxWidth(),enabled=!m.busy)}
  TextButton(onClick={preferences=!preferences}){Text(if(preferences)"Hide preferences"else"Signature & notifications")}
  if(preferences){
  OutlinedTextField(value=a.signature,onValueChange={a=a.copy(signature=it.take(4000))},label={Text("Signature")},modifier=Modifier.fillMaxWidth(),minLines=2,enabled=!m.busy)
  Row(verticalAlignment=Alignment.CenterVertically){Text("Check for new mail",Modifier.weight(1f));Switch(checked=a.enabled,onCheckedChange={a=a.copy(enabled=it)},enabled=!m.busy)};Row(verticalAlignment=Alignment.CenterVertically){Text("Notifications",Modifier.weight(1f));Switch(checked=a.notify,onCheckedChange={a=a.copy(notify=it)},enabled=!m.busy)}
  }
  if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
  if(m.error)Text(m.status,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
  if(m.busy){Text(m.status,style=MaterialTheme.typography.bodySmall);LinearProgressIndicator(Modifier.fillMaxWidth());TextButton(onClick=m::cancel){Text("Cancel connection")}}
  Button(onClick={connect()},enabled=!m.busy,modifier=Modifier.fillMaxWidth()){Text(if(existing)"Save & check connection" else"Connect account")}

  if(existing){OutlinedButton(onClick={m.updateAccount(a);onClose()},enabled=!m.busy&&password.isBlank()&&smtpPassword.isBlank()&&a.copy(name=initial.name,signature=initial.signature,enabled=initial.enabled,notify=initial.notify)==initial,modifier=Modifier.fillMaxWidth()){Text("Save preferences")};TextButton(onClick={remove=true},enabled=!m.busy){Text("Remove account",color=MaterialTheme.colorScheme.error)}}
 }
 if(directions)MailAppPasswordDirections(a.provider,{directions=false},onWeb,a.email)
 if(remove)AlertDialog(onDismissRequest={remove=false},title={Text("Remove account?")},text={Text("Remove ${a.email}, its downloaded mail and local drafts from OnKlav? Server mail is kept.")},confirmButton={TextButton(onClick={remove=false;m.remove(a);onClose()}){Text("Remove")}},dismissButton={TextButton(onClick={remove=false}){Text("Cancel")}})
}
@Composable internal fun MailField(label:String,value:String,enabled:Boolean=true,change:(String)->Unit){OutlinedTextField(value=value,onValueChange={change(it.take(1000))},label={Text(label)},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=if(label.contains("email",true)||label.endsWith("username",true))KeyboardType.Email else KeyboardType.Text),enabled=enabled,modifier=Modifier.fillMaxWidth())}
@Composable private fun MailPort(label:String,value:Int,enabled:Boolean,change:(Int)->Unit){var text by remember(value){mutableStateOf(value.toString())};OutlinedTextField(value=text,onValueChange={s->if(s.length<=5&&s.all{it.isDigit()}){text=s;change(s.toIntOrNull()?:0)}},label={Text(label)},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),enabled=enabled,modifier=Modifier.fillMaxWidth())}
@Composable internal fun MailFolderDialog(m:MailModel,mode:String?,onClose:()->Unit){var account by remember{mutableStateOf(m.opened?.account?:m.selectedRows().firstOrNull()?.account?:m.account.ifBlank{m.accounts.firstOrNull()?.id.orEmpty()})};var edit by remember{mutableStateOf<MailFolder?>(null)};var creating by remember{mutableStateOf(false)};var name by remember{mutableStateOf("")};var deletion by remember{mutableStateOf(false)};var error by remember{mutableStateOf("")};val a=m.accounts.firstOrNull{it.id==account}
 MailDialog(if(mode=="copy")"Copy to folder" else if(mode!=null)"Move to folder" else"Folders",onClose){
  if(mode==null)MailChoices(m.accounts.map{it.email},a?.email.orEmpty()){label->account=m.accounts.first{it.email==label}.id}
  if(mode!=null&&m.selectedRows().map{it.account}.distinct().size>1)Text("Select messages from one account to choose a destination.",color=MaterialTheme.colorScheme.error)
  val allowed=mode==null||m.selectedRows().all{it.account==account}
  m.folders[account].orEmpty().forEach{f->Row(verticalAlignment=Alignment.CenterVertically){TextButton(onClick={if(mode==null)m.choose(account,f.path)else m.act(mode,f.path);onClose()},enabled=!m.busy&&f.selectable&&allowed,modifier=Modifier.weight(1f)){Icon(Icons.Outlined.FolderOpen,null);Text(f.name,Modifier.weight(1f).padding(start=8.dp))};if(mode==null&&f.role.isBlank())IconButton(onClick={edit=f;name=f.path}){Icon(Icons.Outlined.Edit,"Manage ${f.name}")}}}
  if(a!=null&&mode==null){OutlinedButton(onClick={creating=true;name=""}){Icon(Icons.Outlined.CreateNewFolder,null);Text("New folder")};TextButton(onClick={m.choose(a.id);onClose()}){Text("Refresh folders")}}
 }
 if(creating||edit!=null)AlertDialog(onDismissRequest={creating=false;edit=null},title={Text(if(creating)"New folder" else"Rename folder")},text={Column{MailField("Folder name",name){name=it};if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error);if(!creating)TextButton(onClick={deletion=true}){Text("Delete empty folder",color=MaterialTheme.colorScheme.error)}}},confirmButton={TextButton(onClick={if(name.isBlank())error="Enter a folder name" else if(a!=null){m.folderChange(a,if(creating)name else edit!!.path,if(creating)"create" else"rename",name);creating=false;edit=null;onClose()}}){Text(if(creating)"Create" else"Rename")}},dismissButton={TextButton(onClick={creating=false;edit=null}){Text("Cancel")}})
 if(deletion)AlertDialog(onDismissRequest={deletion=false},title={Text("Delete empty folder?")},text={Text("${edit?.path.orEmpty()} must be empty. System folders cannot be deleted here.")},confirmButton={TextButton(onClick={if(a!=null&&edit!=null)m.folderChange(a,edit!!.path,"delete");deletion=false;edit=null;onClose()}){Text("Delete")}},dismissButton={TextButton(onClick={deletion=false}){Text("Cancel")}})
}
@Composable internal fun MailRuleDialog(initial:MailRule,m:MailModel,onClose:()->Unit){var r by remember{mutableStateOf(initial)};var error by remember{mutableStateOf("")};var deleting by remember{mutableStateOf(false)};MailDialog("Email filter",onClose){MailField("Name",r.name){r=r.copy(name=it)};Text("Account",style=MaterialTheme.typography.labelMedium);MailChoices(listOf("All accounts")+m.accounts.map{it.email},m.accounts.firstOrNull{it.id==r.account}?.email?:"All accounts"){r=r.copy(account=m.accounts.firstOrNull{a->a.email==it}?.id.orEmpty(),folder="")};MailChoices(listOf("From","To","Subject"),r.field.replaceFirstChar{it.uppercase()}){r=r.copy(field=it.lowercase())};MailField("Contains",r.contains){r=r.copy(contains=it)};MailChoices(listOf("Star","Mark read","Move"),when(r.action){"read"->"Mark read";"move"->"Move";else->"Star"}){r=r.copy(action=when(it){"Mark read"->"read";"Move"->"move";else->"star"})};if(r.action=="move"){if(r.account.isBlank())Text("Choose one account for a Move filter.",color=MaterialTheme.colorScheme.error)else{var menu by remember{mutableStateOf(false)};Box{OutlinedButton(onClick={menu=true}){Text(r.folder.ifBlank{"Choose folder"});Icon(Icons.Outlined.ExpandMore,null)};DropdownMenu(expanded=menu,onDismissRequest={menu=false}){m.folders[r.account].orEmpty().filter{it.selectable&&!it.path.equals("INBOX",true)}.forEach{f->DropdownMenuItem(text={Text(f.name)},onClick={r=r.copy(folder=f.path);menu=false})}}}}};if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error);Button(onClick={try{r.validate();m.rule(r);onClose()}catch(e:Exception){error=e.message.orEmpty()}},enabled=!m.busy,modifier=Modifier.fillMaxWidth()){Text("Save filter")};if(m.rules.any{it.id==r.id})TextButton(onClick={deleting=true}){Text("Delete filter",color=MaterialTheme.colorScheme.error)}};if(deleting)AlertDialog(onDismissRequest={deleting=false},title={Text("Delete filter?")},text={Text(r.name)},confirmButton={TextButton(onClick={m.rule(null,r.id);onClose()}){Text("Delete")}},dismissButton={TextButton(onClick={deleting=false}){Text("Cancel")}})}
