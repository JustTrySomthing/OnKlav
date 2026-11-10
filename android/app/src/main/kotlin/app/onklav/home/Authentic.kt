package app.onklav.home

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.provider.Settings
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel
import app.onklav.shared.OnKlavFileContracts
import app.onklav.shared.R
import kotlinx.coroutines.*
import java.util.Locale

@Composable private fun AuthenticPassword(label:String,value:String,limit:Int=256,change:(String)->Unit){var show by remember{mutableStateOf(false)};OutlinedTextField(value,{change(it.take(limit))},label={Text(label)},singleLine=true,visualTransformation=if(show)VisualTransformation.None else PasswordVisualTransformation(),keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(autoCorrect=false,keyboardType=KeyboardType.Password),trailingIcon={IconButton(onClick={show=!show}){Icon(if(show)Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,if(show)"Hide $label"else"Show $label")}},modifier=Modifier.fillMaxWidth())}
@Composable internal fun AuthenticWorkspace(exit:()->Unit,m:AuthenticModel=viewModel()){
 val c=LocalContext.current;val activity=c as? Activity;val scope=rememberCoroutineScope();val snackbar=remember{SnackbarHostState()}
 var password by remember{mutableStateOf("")};var confirm by remember{mutableStateOf("")};var query by remember{mutableStateOf("")};var panel by remember{mutableStateOf<String?>(null)};var scan by remember{mutableStateOf(false)};var menu by remember{mutableStateOf(false)}
 var accountId by remember{mutableStateOf<String?>(null)};var qrId by remember{mutableStateOf<String?>(null)};var issuer by remember{mutableStateOf("")};var account by remember{mutableStateOf("")};var seed by remember{mutableStateOf("")};var type by remember{mutableStateOf("totp")};var algorithm by remember{mutableStateOf("SHA1")};var digits by remember{mutableStateOf("6")};var period by remember{mutableStateOf("30")};var counter by remember{mutableStateOf("0")};var advanced by remember{mutableStateOf(false)};var inputError by remember{mutableStateOf("")};var backupPassword by remember{mutableStateOf("")};var oldPassword by remember{mutableStateOf("")};var eraseText by remember{mutableStateOf("")}
 var now by remember{mutableLongStateOf(System.currentTimeMillis()/1000)}
 fun clear(){password="";confirm="";seed="";oldPassword="";backupPassword="";panel=null;accountId=null;qrId=null;scan=false;inputError=""}
 fun leave(){clear();m.lock();exit()}
 DisposableEffect(m,activity){val secured=activity?.window?.attributes?.flags?.and(WindowManager.LayoutParams.FLAG_SECURE)!=0;activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE);AuthenticLifecycle.register(c.applicationContext as Application,m);onDispose{AuthenticLifecycle.unregister(m);if(!secured)activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)}}
 LaunchedEffect(m.epoch){clear()}
 LaunchedEffect(m.unlocked){while(m.unlocked){now=System.currentTimeMillis()/1000;delay(250)}}
 LaunchedEffect(m.status){if(m.status.isNotBlank()&&panel==null&&!m.hasBackup){snackbar.showSnackbar(m.status);m.dismiss()}}
 BackHandler{if(scan)scan=false else if(qrId!=null)qrId=null else if(panel!=null){panel=null;seed="";password="";confirm="";oldPassword="";inputError=""}else if(m.pending.isNotEmpty()||m.batchStatus.isNotEmpty())m.discardImport()else leave()}
 val backupOut=rememberLauncherForActivityResult(OnKlavFileContracts.CreateDocument("application/octet-stream")){it?.let(m::export)}
 val backupIn=rememberLauncherForActivityResult(OnKlavFileContracts.OpenDocument()){it?.let(m::openBackup)}
 val imageIn=rememberLauncherForActivityResult(OnKlavFileContracts.OpenDocument()){uri->if(uri!=null)scope.launch{try{val text=withContext(Dispatchers.Default){AuthenticQr.selected(c,uri)};m.accept(text)}catch(e:Exception){if(e is CancellationException)throw e;m.message("No supported authenticator QR was found in that image.")}}}
 if(scan&&m.unlocked){AuthenticScanner({scan=false}){scan=false;m.accept(it)};return}
 Scaffold(
  modifier=Modifier.imePadding(),
  snackbarHost={SnackbarHost(snackbar){data->Snackbar(data,containerColor=MaterialTheme.colorScheme.surfaceContainerHigh,contentColor=MaterialTheme.colorScheme.onSurface)}},
  topBar={
   Column{
    HomeHeader("Authentic",back=::leave)
    if(m.unlocked)Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically){
     OutlinedTextField(query,{query=it.take(128)},label={Text("Search accounts")},singleLine=true,modifier=Modifier.weight(1f))
     IconButton(onClick={m.lock();clear()},enabled=!m.busy){Icon(Icons.Outlined.Lock,"Lock Authentic")}
     Box{
      IconButton(onClick={menu=true},enabled=!m.busy){Icon(Icons.Outlined.MoreVert,"Authentic options")}
      DropdownMenu(expanded=menu,onDismissRequest={menu=false}){
       listOf("Encrypted backup","Restore backup","Change password","Transfer help","Time settings").forEach{item->
        DropdownMenuItem(text={Text(item)},onClick={
         menu=false;m.dismiss()
         when(item){
          "Encrypted backup"->backupOut.launch("Authentic-${java.time.LocalDate.now()}.authentic")
          "Restore backup"->backupIn.launch(arrayOf("application/octet-stream","*/*"))
          "Time settings"->c.startActivity(Intent(Settings.ACTION_DATE_SETTINGS))
          else->panel=item
         }
        })
       }
      }
     }
    }
   }
  },
  floatingActionButton={if(m.unlocked)ExtendedFloatingActionButton(onClick={if(!m.busy){m.dismiss();panel="Add account"}},icon={Icon(Icons.Outlined.Add,null)},text={Text("Add")})}
 ){padding->
  Column(Modifier.fillMaxSize().padding(padding)){
   if(m.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
   if(!m.unlocked){if(!m.exists||m.enrolling)AuthenticRecoverySetup(m,false){backupIn.launch(arrayOf("application/octet-stream","*/*"))}else if(panel=="Reset password")AuthenticRecoverySetup(m,true)else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp),horizontalAlignment=Alignment.CenterHorizontally){Spacer(Modifier.height(24.dp));Icon(androidx.compose.ui.res.painterResource(R.drawable.ref_app_authentic),null,Modifier.size(56.dp),tint=MaterialTheme.colorScheme.primary);Text(if(m.recoveryChoices==null)"Finish Authentic setup"else"Unlock Authentic",style=MaterialTheme.typography.headlineSmall);if(!m.exists)Text("Create a password of at least 8 characters to encrypt your accounts.",style=MaterialTheme.typography.bodyMedium)
    AuthenticPassword("Password",password){password=it};if(!m.exists)AuthenticPassword("Confirm password",confirm){confirm=it}
    if(!m.exists)Text("Keep your password and your services’ recovery codes safe. A forgotten password cannot be recovered.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    var tick by remember{mutableLongStateOf(SystemClock.elapsedRealtime())};LaunchedEffect(m.retryAt){while(tick<m.retryAt){delay(500);tick=SystemClock.elapsedRealtime()}}
    val waiting=m.retryAt>tick
    Button(onClick={val value=password.toCharArray();password="";confirm="";m.unlock(value,!m.exists)},enabled=!m.busy&&!waiting&&password.length>=8&&(m.exists||password==confirm),modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(if(waiting)"Try again shortly"else if(m.recoveryChoices==null)"Continue setup"else"Unlock")}
    if(!m.exists)TextButton(onClick={backupIn.launch(arrayOf("application/octet-stream","*/*"))},enabled=!m.busy){Text("Restore encrypted backup")}
    if(m.recoveryChoices!=null)TextButton(onClick={password="";panel="Reset password"}){Text("Reset password")}
    if(m.exists&&m.recoveryChoices==null)Text("Enter your current password to protect your existing accounts with three recovery answers.",style=MaterialTheme.typography.bodySmall)
    TextButton(onClick={panel="Transfer help"}){Text(if(m.exists)"Forgot password or need help?"else"How to transfer accounts")}
   }}else{
    if(Settings.Global.getInt(c.contentResolver,Settings.Global.AUTO_TIME,1)!=1)TextButton(onClick={c.startActivity(Intent(Settings.ACTION_DATE_SETTINGS))}){Text("Automatic time is off · Check phone time",color=MaterialTheme.colorScheme.error)}
    if(m.batchStatus.isNotBlank()&&m.pending.isEmpty())Surface(Modifier.fillMaxWidth().padding(12.dp),color=MaterialTheme.colorScheme.secondaryContainer){Column(Modifier.padding(12.dp)){Text(m.batchStatus);Row{TextButton(onClick={scan=true}){Text("Scan next QR")};TextButton(onClick={imageIn.launch(arrayOf("image/*"))}){Text("QR image")};TextButton(onClick=m::discardImport){Text("Cancel")}}}}
    val rows=m.accounts.filter{query.isBlank()||it.issuer.contains(query,true)||it.account.contains(query,true)}.sortedByDescending{it.pinned}
    if(rows.isEmpty())Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){Text(if(m.accounts.isEmpty())"Your codes, in one place"else"No matching accounts",style=MaterialTheme.typography.titleLarge);if(m.accounts.isEmpty()){Text("Scan a setup QR or enter a key to get started.",style=MaterialTheme.typography.bodyMedium);TextButton(onClick={panel="Add account"}){Text("Add your first account")}}}
    else LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(start=12.dp,end=12.dp,top=12.dp,bottom=100.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){items(rows,key={it.id}){a->
     var rowMenu by remember{mutableStateOf(false)}
     Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surfaceContainer){Column(Modifier.fillMaxWidth().padding(16.dp)){
      Row(verticalAlignment=Alignment.CenterVertically){Surface(shape=CircleShape,color=MaterialTheme.colorScheme.secondaryContainer,modifier=Modifier.size(36.dp)){Box(contentAlignment=Alignment.Center){Text((a.issuer.ifBlank{a.account}).take(1).uppercase(Locale.ROOT),style=MaterialTheme.typography.titleMedium)}};Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(a.issuer.ifBlank{"Account"},style=MaterialTheme.typography.titleMedium,maxLines=1,overflow=TextOverflow.Ellipsis);Text(a.account,style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)};if(a.pinned)Icon(Icons.Outlined.Star,null,Modifier.size(16.dp),tint=MaterialTheme.colorScheme.primary);Box{IconButton(onClick={rowMenu=true},enabled=!m.busy){Icon(Icons.Outlined.MoreVert,"Options for ${a.account}")};DropdownMenu(expanded=rowMenu,onDismissRequest={rowMenu=false}){listOf("Edit name",if(a.pinned)"Unpin"else"Pin","Move up","Move down","Transfer QR","Delete account").forEach{action->DropdownMenuItem(text={Text(action)},onClick={rowMenu=false;m.dismiss();accountId=a.id;when(action){"Pin","Unpin"->m.pin(a.id);"Move up"->m.move(a.id,-1);"Move down"->m.move(a.id,1);"Edit name"->{issuer=a.issuer;account=a.account;panel=action};else->panel=action}})}}}}
      val step=if(a.type=="hotp")a.counter else now/a.period
      val code=remember(a.id,a.secret,a.algorithm,a.digits,step){AuthenticOtp.code(a,now)}
      BoxWithConstraints(Modifier.fillMaxWidth()){val font=((maxWidth.value-86)/((a.digits+1)*.62f*LocalDensity.current.fontScale)).coerceIn(20f,34f)
       Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(code.chunked(code.length/2).joinToString(" "),fontSize=font.sp,fontFamily=FontFamily.Monospace,color=MaterialTheme.colorScheme.primary,maxLines=1,modifier=Modifier.weight(1f).semantics{contentDescription="Verification code ${code.toList().joinToString(" ")}"})
        if(a.type=="totp"){val left=AuthenticOtp.remaining(a,now);Box(Modifier.size(34.dp).semantics{contentDescription="$left seconds remaining"},contentAlignment=Alignment.Center){CircularProgressIndicator(progress={left.toFloat()/a.period},modifier=Modifier.fillMaxSize(),strokeWidth=2.dp);Text(left.toString(),style=MaterialTheme.typography.labelSmall)}}
        IconButton(onClick={val current=m.accounts.firstOrNull{it.id==a.id};if(m.unlocked&&current!=null){AuthenticClipboard.copy(c,AuthenticOtp.code(current,System.currentTimeMillis()/1000),30);m.message("Code copied · clipboard clears shortly")}},enabled=!m.busy){Icon(androidx.compose.ui.res.painterResource(R.drawable.convert_copy),"Copy code for ${a.account}")}
       }
      }
      if(a.type=="hotp")Row(verticalAlignment=Alignment.CenterVertically){Text("Counter ${a.counter}",Modifier.weight(1f),style=MaterialTheme.typography.labelSmall);TextButton(onClick={m.advance(a.id)},enabled=!m.busy){Text("Next code")}}
     }}
    }}
   }
  }
 }
 if(panel=="Add account"&&m.unlocked)AlertDialog(onDismissRequest={panel=null},title={Text("Add account")},text={Column{listOf("Scan QR code","Enter setup key","Paste setup link","Import QR image","Transfer help").forEach{label->TextButton(onClick={inputError="";when(label){"Scan QR code"->{panel=null;scan=true};"Import QR image"->{panel=null;imageIn.launch(arrayOf("image/*"))};"Enter setup key"->{issuer="";account="";seed="";type="totp";algorithm="SHA1";digits="6";period="30";counter="0";advanced=false;panel=label};else->panel=label}},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(label)}}}},confirmButton={TextButton(onClick={panel=null}){Text("Cancel")}})
 if(panel in listOf("Enter setup key","Paste setup link","Edit name")&&m.unlocked)AlertDialog(onDismissRequest={panel=null;seed=""},title={Text(panel!!)},text={Column(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
  if(panel!="Paste setup link"){OutlinedTextField(issuer,{issuer=it.take(128)},label={Text("Issuer / service")},singleLine=true);OutlinedTextField(account,{account=it.take(128)},label={Text("Account name")},singleLine=true)}
  if(panel!="Edit name")AuthenticPassword(if(panel=="Paste setup link")"Setup link"else"Setup key",seed,limit=if(panel=="Paste setup link")100000 else 256){seed=it.take(if(panel=="Paste setup link")100000 else 256)}
  if(panel=="Enter setup key"){TextButton(onClick={advanced=!advanced}){Text(if(advanced)"Hide advanced options"else"Advanced options")};if(advanced){VideoChoice("Code type",type.uppercase(),listOf("totp" to "Time-based (TOTP)","hotp" to "Counter-based (HOTP)"),selectedId=type){type=it};VideoChoice("Algorithm",algorithm,listOf("SHA1","SHA256","SHA512").map{it to it}){algorithm=it};VideoChoice("Digits",digits,listOf("6" to "6 digits","8" to "8 digits")){digits=it};if(type=="totp")OutlinedTextField(period,{period=it.filter(Char::isDigit).take(4)},label={Text("Period in seconds")},singleLine=true)else OutlinedTextField(counter,{counter=it.filter(Char::isDigit).take(19)},label={Text("Starting counter")},singleLine=true)}}
  if(inputError.isNotBlank())Text(inputError,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
 }},confirmButton={TextButton(onClick={runCatching{when(panel){"Paste setup link"->{require(seed.isNotBlank());m.accept(seed);check(m.pending.isNotEmpty()||m.batchStatus.isNotEmpty()){m.status.ifBlank{"Check the setup link."}}};"Edit name"->{val original=m.accounts.first{it.id==accountId};original.copy(issuer=issuer.trim(),account=account.trim()).validate();m.labels(original.id,issuer,account)};else->{val key=AuthenticOtp.base32(seed);val normalized=try{AuthenticOtp.encode(key)}finally{key.fill(0)};val a=AuthenticAccount(issuer=issuer.trim(),account=account.trim(),secret=normalized,type=type,algorithm=algorithm,digits=digits.toInt(),period=period.toInt(),counter=counter.toLong());a.validate();m.stage(a)}};seed="";inputError="";panel=null}.onFailure{inputError=it.message?.take(160)?:"Check the account fields."}},enabled=!m.busy){Text(if(panel=="Edit name")"Save"else"Continue")}},dismissButton={TextButton(onClick={panel=null;seed="";inputError=""}){Text("Cancel")}})
 if(m.pending.isNotEmpty()&&m.unlocked){
  val known=remember(m.accounts){m.accounts.associateBy{it.fingerprint()}}
  AlertDialog(onDismissRequest=m::discardImport,title={Text("Import ${m.pending.size} account${if(m.pending.size==1)""else"s"}?")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
   LazyColumn(Modifier.heightIn(max=280.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){items(m.pending,key={it.id}){a->val existing=known[a.fingerprint()];Column{Text(a.issuer.ifBlank{"Account"},style=MaterialTheme.typography.titleSmall);Text(a.account,style=MaterialTheme.typography.bodySmall);if(existing!=null)Text(if(a.type=="hotp"&&a.counter>existing.counter)"Counter will advance"else"Already added · kept once",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)}}}
   Text("Existing accounts are preserved. Verify the codes before removing your previous authenticator.",style=MaterialTheme.typography.bodySmall)
  }},confirmButton={TextButton(onClick=m::importPending,enabled=!m.busy){Text("Import accounts")}},dismissButton={TextButton(onClick=m::discardImport,enabled=!m.busy){Text("Cancel")}})
 }

 if(panel=="Delete account"&&m.unlocked)AlertDialog(onDismissRequest={panel=null},title={Text("Delete this account?")},text={Text("This removes its key only from Authentic. Keep another sign-in method or disable two-step verification at the service first.")},confirmButton={TextButton(onClick={accountId?.let(m::delete);panel=null},enabled=!m.busy){Text("Delete",color=MaterialTheme.colorScheme.error)}},dismissButton={TextButton(onClick={panel=null}){Text("Cancel")}})
 if(panel=="Transfer QR"&&m.unlocked)AlertDialog(onDismissRequest={panel=null;password=""},title={Text("Verify password")},text={Column{AuthenticPassword("Password",password){password=it};if(m.status.isNotBlank())Text("Could not verify. Check your password.",color=MaterialTheme.colorScheme.error)}},confirmButton={TextButton(onClick={val id=accountId;if(id!=null){val p=password.toCharArray();password="";m.verifyQr(id,p){qrId=it;panel=null}}},enabled=!m.busy&&password.length>=8){Text("Show transfer QR")}},dismissButton={TextButton(onClick={panel=null;password=""}){Text("Cancel")}})
 val qr=m.accounts.firstOrNull{it.id==qrId}.takeIf{m.unlocked}
 if(qr!=null){val bitmap=remember(qr){AuthenticQr.bitmap(AuthenticOtp.uri(qr))};DisposableEffect(bitmap){onDispose{bitmap.recycle()}};AlertDialog(onDismissRequest={qrId=null},title={Text("Transfer ${qr.issuer.ifBlank{"account"}}")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){androidx.compose.foundation.Image(bitmap.asImageBitmap(),"Account transfer QR",Modifier.fillMaxWidth().aspectRatio(1f).background(androidx.compose.ui.graphics.Color.White));Text(qr.account);Text("Anyone with this QR can generate your codes. Scan it only with your own trusted authenticator.",style=MaterialTheme.typography.bodySmall)}},confirmButton={TextButton(onClick={qrId=null}){Text("Done")}})}
 if(panel=="Change password"&&m.unlocked)AlertDialog(onDismissRequest={panel=null;password="";oldPassword="";confirm=""},title={Text("Change password")},text={Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){AuthenticPassword("Current password",oldPassword){oldPassword=it};AuthenticPassword("New password (8+ characters)",password){password=it};AuthenticPassword("Confirm new password",confirm){confirm=it};if(m.status.isNotBlank())Text(m.status,style=MaterialTheme.typography.bodySmall)}},confirmButton={TextButton(onClick={val old=oldPassword.toCharArray();val next=password.toCharArray();m.changePassword(old,next){oldPassword="";password="";confirm="";panel=null}},enabled=!m.busy&&oldPassword.length>=8&&password.length>=8&&password==confirm){Text("Change password")}},dismissButton={TextButton(onClick={panel=null;password="";oldPassword="";confirm=""}){Text("Done")}})
 if(m.hasBackup)AlertDialog(onDismissRequest={m.cancelBackup();backupPassword=""},title={Text("Unlock backup")},text={Column{Text("Enter the password used when this backup was created. Existing accounts are merged, not replaced.",style=MaterialTheme.typography.bodySmall);AuthenticPassword("Backup password",backupPassword){backupPassword=it};if(m.status.isNotBlank())Text(m.status,style=MaterialTheme.typography.bodySmall)}},confirmButton={TextButton(onClick={val p=backupPassword.toCharArray();backupPassword="";m.restore(p)},enabled=!m.busy&&backupPassword.length>=8){Text("Open backup")}},dismissButton={TextButton(onClick={m.cancelBackup();backupPassword=""},enabled=!m.busy){Text("Cancel")}})
 if(panel=="Reset Authentic")AlertDialog(onDismissRequest={panel=null;eraseText=""},title={Text("Erase Authentic accounts?")},text={Column{Text("This permanently removes every local Authentic account. Other OnKlav data is untouched. Keep a working authenticator, recovery codes or an encrypted backup before continuing.");OutlinedTextField(eraseText,{eraseText=it},label={Text("Type DELETE")},singleLine=true)}},confirmButton={TextButton(onClick={m.reset();eraseText="";panel=null},enabled=eraseText=="DELETE"&&!m.busy&&!m.unlocked){Text("Erase Authentic",color=MaterialTheme.colorScheme.error)}},dismissButton={TextButton(onClick={panel=null;eraseText=""}){Text("Cancel")}})
 if(panel=="Transfer help")AlertDialog(onDismissRequest={panel=null},title={Text("Move accounts safely")},text={Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){Text("1. In Google Authenticator, open Transfer codes → Export codes.\n\n2. In Authentic, tap Add → Scan QR code. Scan every QR in the export, then confirm the import.\n\n3. Test a code with each service before removing your old authenticator.");Text("On the same phone, prepare the transfer QR images first, then use Add → Import QR image for each one. QR images contain account secrets; keep them private.",style=MaterialTheme.typography.bodySmall);Text("Authentic works offline using your phone’s time. If codes fail, enable automatic date and time. It supports standard TOTP/HOTP codes, not push approvals or Google cloud synchronization.",style=MaterialTheme.typography.bodySmall);Text("Keep an encrypted backup and your password separately. If the password is forgotten, use Reset password and answer your three recovery questions. Keep your services’ recovery codes separately too.",style=MaterialTheme.typography.bodySmall);if(m.exists&&!m.unlocked)TextButton(onClick={eraseText="";panel="Reset Authentic"}){Text("Start over · erase local accounts",color=MaterialTheme.colorScheme.error)}}},confirmButton={TextButton(onClick={panel=null}){Text("Done")}})
}

/** No password-only file is created: setup writes password and recovery atomically on Complete. */
@Composable private fun AuthenticRecoverySetup(m:AuthenticModel,reset:Boolean,restore:(()->Unit)?=null){
 var password by remember(m.epoch,reset){mutableStateOf("")};var confirm by remember(m.epoch,reset){mutableStateOf("")}
 var answers by remember(m.epoch,reset){mutableStateOf(List(3){""})};var choices by remember(m.epoch,reset){mutableStateOf(listOf(0,0,0))}
 var clock by remember{mutableLongStateOf(SystemClock.elapsedRealtime())};LaunchedEffect(m.retryAt){while(clock<m.retryAt){delay(500);clock=SystemClock.elapsedRealtime()}}
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  Text(if(reset)"Reset password"else"Complete Authentic setup",style=MaterialTheme.typography.headlineSmall)
  if(!reset&&!m.enrolling){AuthenticPassword("Create password",password){password=it};AuthenticPassword("Confirm password",confirm){confirm=it}}
  Text(if(reset)"Answer all three questions to keep your accounts and choose a new password."else"Choose three recovery questions. Setup is complete only after all three answers are saved.",style=MaterialTheme.typography.bodyMedium)
  repeat(3){i->var expanded by remember{mutableStateOf(false)};val selected=if(reset)m.recoveryChoices!![i]else choices[i]
   Text("${i+1}. Recovery question",style=MaterialTheme.typography.labelLarge)
   if(reset)Text(VaultRecovery.questions[i][selected])else Box{TextButton(onClick={expanded=true},enabled=!m.busy){Text(VaultRecovery.questions[i][selected]);Icon(Icons.Outlined.ExpandMore,null)}
    DropdownMenu(expanded,{expanded=false}){VaultRecovery.questions[i].forEachIndexed{index,q->DropdownMenuItem(text={Text(q)},onClick={choices=choices.toMutableList().also{it[i]=index};answers=answers.toMutableList().also{it[i]=""};expanded=false})}}
   }
   AuthenticPassword("Answer ${i+1}",answers[i]){value->answers=answers.toMutableList().also{it[i]=value}}
  }
  if(reset){AuthenticPassword("New password",password){password=it};AuthenticPassword("Confirm new password",confirm){confirm=it}}
  Text("Keep your password and recovery answers in a safe place.",style=MaterialTheme.typography.bodySmall)
  Button(onClick={val a=answers.map{it.toCharArray()};val p=if(reset||!m.enrolling)password.toCharArray()else null;answers=List(3){""};password="";confirm="";if(reset)m.resetPassword(a,p!!)else m.completeSetup(p,choices,a)},enabled=!m.busy&&(!reset||clock>=m.retryAt)&&answers.all(VaultRecovery::validAnswer)&&((!reset&&m.enrolling)||(password.length in 8..256&&password==confirm)),modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(if(reset&&clock<m.retryAt)"Try again shortly"else if(reset)"Reset password"else"Complete")}
  if(!reset&&!m.exists&&restore!=null)TextButton(onClick=restore,enabled=!m.busy){Text("Restore encrypted backup")}
 }
}
