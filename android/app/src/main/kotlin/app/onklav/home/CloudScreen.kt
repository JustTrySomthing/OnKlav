package app.onklav.home

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.onklav.shared.*
import app.onklav.shared.R
import java.util.UUID

@Composable internal fun CloudWorkspace(exit:()->Unit,website:(String)->Unit,m:CloudModel=viewModel(),share:((String)->Boolean)?=null){
 val c=LocalContext.current;val colors=MaterialTheme.colorScheme
 var editor by remember{mutableStateOf<CloudAccount?>(null)};var connecting by remember{mutableStateOf(false)};var accountMenu by remember{mutableStateOf(false)};var manage by remember{mutableStateOf(false)};var disconnect by remember{mutableStateOf<CloudAccount?>(null)};var query by remember{mutableStateOf("")};var newFolder by remember{mutableStateOf(false)};var selected by remember{mutableStateOf<CloudEntry?>(null)};var leave by remember{mutableStateOf(false)};var shareError by remember{mutableStateOf(false)};val clipboard=LocalClipboardManager.current
 val export=rememberLauncherForActivityResult(FileExportContract()){uri->if(uri==null)m.clearStaged()else m.export(uri)}
 val picker=rememberLauncherForActivityResult(OnKlavFileContracts.OpenDocument()){uri->uri?.let(m::prepare)}
 fun close(){if(m.busy)leave=true else{m.stop();exit()}}
 BackHandler{if(connecting&&!m.busy){connecting=false;editor=null}else close()}
 LaunchedEffect(m){m.start()}
 DisposableEffect(m){onDispose{m.stop();m.clearShare()}}
 Surface(Modifier.fillMaxSize(),color=colors.background){Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)){
  HomeHeader(if(connecting)"Connect cloud storage"else if(share!=null)"Share via Cloud Files"else"Cloud Files",back={if(connecting&&!m.busy){connecting=false;editor=null}else close()},actions={if(!connecting)IconButton(onClick={manage=true},enabled=!m.busy){Icon(Icons.Outlined.MoreVert,"Cloud accounts & help")}})
  if(m.busy){Column(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Text(m.phase,style=MaterialTheme.typography.labelLarge);if(m.total>0)LinearProgressIndicator(progress={(m.bytes.toDouble()/m.total).toFloat().coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())else LinearProgressIndicator(Modifier.fillMaxWidth());if(m.bytes>0)Text("${cloudSize(c,m.bytes)}${if(m.total>0)" / ${cloudSize(c,m.total)}"else""}",style=MaterialTheme.typography.bodySmall);TextButton(onClick={m.stop()}){Text("Cancel")}}}
  if(m.error.isNotBlank()&&!connecting)Surface(color=colors.errorContainer,shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth().padding(16.dp)){Column(Modifier.padding(12.dp)){Text(m.error,color=colors.onErrorContainer,modifier=Modifier.semantics{liveRegion=LiveRegionMode.Polite});Row{TextButton(onClick={if(m.account==null)m.start()else m.refresh()},enabled=!m.busy){Text("Retry")};if(m.account!=null)TextButton(onClick={editor=m.account;connecting=true},enabled=!m.busy){Text("Review sign-in")}}}}
   if(m.stagedEntry!=null&&!m.busy)TextButton(onClick={m.stagedEntry?.let{e->export.launch(FileExportRequest(FilesBroker.cleanName(e.name),cloudMime(e.name)))}}){Text(if(m.staged!=null)"Save downloaded copy"else"Choose save folder")}
  if(m.status.isNotBlank())Text(m.status,Modifier.padding(horizontal=16.dp,vertical=6.dp).semantics{liveRegion=LiveRegionMode.Polite},style=MaterialTheme.typography.bodySmall,color=colors.primary)
  if(connecting){CloudAccountEditor(editor,m.busy,m.error,website,onConnect={a->m.connect(a){connecting=false;editor=null}})}
  else if(m.accounts.isEmpty())Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(28.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){Icon(painterResource(R.drawable.ref_app_cloud_files),null,Modifier.size(80.dp),tint=colors.primary);Spacer(Modifier.height(20.dp));Text("Your files. Your cloud.",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold);Text("Use with pCloud or Koofr to browse, upload and share files.",Modifier.padding(vertical=12.dp),style=MaterialTheme.typography.bodyMedium,color=colors.onSurfaceVariant);Button(onClick={connecting=true},enabled=m.loaded&&!m.busy,modifier=Modifier.fillMaxWidth()){Text("Sign in")};OutlinedButton(onClick={connecting=true},enabled=m.loaded&&!m.busy,modifier=Modifier.fillMaxWidth()){Text("Create a free account")};Text("pCloud · Koofr",Modifier.padding(top=16.dp),style=MaterialTheme.typography.labelMedium,color=colors.onSurfaceVariant)}
  else{
   Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.weight(1f)){TextButton(onClick={accountMenu=true},enabled=!m.busy){Icon(painterResource(R.drawable.ref_app_cloud_files),null,Modifier.size(22.dp));Spacer(Modifier.width(8.dp));Text(m.account?.name?:"Choose account",maxLines=1,overflow=TextOverflow.Ellipsis);Icon(Icons.Outlined.ArrowDropDown,null)};DropdownMenu(accountMenu,{accountMenu=false}){m.accounts.forEach{a->DropdownMenuItem(text={Text(a.name)},onClick={accountMenu=false;query="";m.open(a)})};DropdownMenuItem(text={Text("Add account")},onClick={accountMenu=false;editor=null;connecting=true})}};IconButton(onClick={m.refresh()},enabled=!m.busy&&m.account!=null){Icon(Icons.Outlined.Refresh,"Refresh cloud files")}}
   val a=m.account;val path=m.path
   if(a!=null&&path!=null){val root=CloudPaths.base(a.endpoint);Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically){IconButton(onClick={query="";m.up()},enabled=!m.busy&&path!=root){Icon(PhoneIcons.Up,"Parent folder")};Text(if(path==root)"All files"else path.pathSegments.lastOrNull{it.isNotBlank()}.orEmpty(),Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,maxLines=1,overflow=TextOverflow.Ellipsis)}
    Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)){Button(onClick={picker.launch(arrayOf("*/*"))},enabled=!m.busy,modifier=Modifier.weight(1f)){Icon(Icons.Outlined.Add,null);Text("Upload")};OutlinedButton(onClick={newFolder=true},enabled=!m.busy,modifier=Modifier.weight(1f)){Icon(painterResource(R.drawable.ref_action_folder_add),null,Modifier.size(20.dp));Spacer(Modifier.width(6.dp));Text("Folder")}}
    m.listing.available?.let{Text("${cloudSize(c,it)} available",Modifier.padding(horizontal=16.dp,vertical=8.dp),style=MaterialTheme.typography.labelSmall,color=colors.onSurfaceVariant)}
    OutlinedTextField(query,{query=it.take(100)},placeholder={Text("Search this folder")},leadingIcon={Icon(Icons.Outlined.Search,null)},singleLine=true,modifier=Modifier.fillMaxWidth().padding(16.dp),shape=RoundedCornerShape(18.dp))
    val matching=remember(m.listing,query){m.listing.entries.filter{it.name.contains(query,true)}}
    LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(start=16.dp,end=16.dp,bottom=20.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
     if(matching.isEmpty()&&!m.busy)item{Column(Modifier.fillMaxWidth().padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(painterResource(R.drawable.ref_app_cloud_files),null,tint=colors.primary);Text(if(query.isBlank())"This folder is empty"else"No matching files",Modifier.padding(top=12.dp),style=MaterialTheme.typography.titleMedium);if(query.isBlank())Text("Upload a file to get started.",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)}}
     items(matching,key={it.url.toString()}){e->Surface(onClick={if(!m.busy){if(e.folder){query="";m.enter(e)}else selected=e}},shape=RoundedCornerShape(14.dp),color=colors.surfaceContainer){Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Icon(painterResource(if(e.folder)R.drawable.ref_app_files else R.drawable.ref_app_docu_view),null,Modifier.size(28.dp),tint=colors.primary);Column(Modifier.weight(1f).padding(horizontal=12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){Text(e.name,maxLines=2,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.Medium);Text(if(e.folder)"Folder"else e.size?.let{cloudSize(c,it)}?:"File",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)};Icon(if(e.folder)Icons.Outlined.ChevronRight else PhoneIcons.Down,null,tint=colors.primary)}}}
    }
   }
  }
 }}
 if(manage)AlertDialog(
  onDismissRequest={manage=false},title={Text("Cloud accounts")},
  text={Column(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
   m.accounts.forEach{a->Column{
    Text(a.name,fontWeight=FontWeight.SemiBold)
    Text(a.user,style=MaterialTheme.typography.bodySmall)
    Row{
     TextButton(onClick={manage=false;editor=a;connecting=true}){Text("Edit sign-in")}
     TextButton(onClick={manage=false;disconnect=a}){Text("Disconnect")}
    }
   }}
   TextButton(onClick={manage=false;editor=null;connecting=true}){Text("Add account")}
   Text("Transfers use your provider directly. Keep Cloud Files open until they finish. Files up to 2 GB are supported. Storage limits and provider restrictions still apply. Sharing links let anyone with the link access the file. You can remove links in your provider’s account.",style=MaterialTheme.typography.bodySmall)
  }},confirmButton={TextButton(onClick={manage=false}){Text("Done")}}
 )
 disconnect?.let{a->AlertDialog(onDismissRequest={disconnect=null},title={Text("Disconnect ${a.name}?")},text={Text("Saved sign-in details will be removed from OnKlav. Your online files will stay in your account.")},confirmButton={TextButton(onClick={disconnect=null;m.disconnect(a)}){Text("Disconnect")}},dismissButton={TextButton(onClick={disconnect=null}){Text("Cancel")}})}
 selected?.let{e->AlertDialog(onDismissRequest={selected=null},title={Text(e.name)},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Text(e.size?.let{cloudSize(c,it)}?:"File");if(e.modified.isNotBlank())Text(e.modified,style=MaterialTheme.typography.bodySmall);Text(if(share==null)"Save a local copy through OnKlav Files."else"Create a sharing link for this file. Anyone with the link can access it. Review it before adding it to your message.",style=MaterialTheme.typography.bodySmall)}},confirmButton={TextButton(onClick={selected=null;if(share!=null)m.share(e)else m.download(e){export.launch(FileExportRequest(FilesBroker.cleanName(e.name),cloudMime(e.name)))}}){Text(if(share!=null)"Create sharing link"else"Save to Files")}},dismissButton={TextButton(onClick={selected=null}){Text("Cancel")}})}
 if(newFolder)CloudNameDialog("New folder","Create",m.busy,m.error,{newFolder=false}){name->newFolder=false;m.mkdir(name)}
 m.upload?.takeIf{!m.busy}?.let{u->CloudNameDialog("Upload file","Upload",m.busy,m.error,m::dismissUpload,u.name){m.send(it)}}
 m.sharedLink?.let{link->AlertDialog(onDismissRequest=m::clearShare,title={Text("Sharing link ready")},text={Column(Modifier.heightIn(max=320.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){androidx.compose.foundation.text.selection.SelectionContainer{Text(link)};TextButton(onClick={clipboard.setText(androidx.compose.ui.text.AnnotatedString(link))}){Text("Copy link")};if(shareError)Text("Your message is full. Copy the link, shorten your message, then paste it.",color=colors.error);Text("Anyone with this link can access the file. Remove the link in your cloud account to stop sharing.",style=MaterialTheme.typography.bodySmall)}},confirmButton={TextButton(onClick={if(share?.invoke(link)==true){m.clearShare();exit()}else shareError=true}){Text("Add link to message")}},dismissButton={TextButton(onClick=m::clearShare){Text("Keep browsing")}})}
 if(leave)AlertDialog(onDismissRequest={leave=false},title={Text("Stop and leave?")},text={Text("The transfer will stop. An interrupted upload may leave a partial file online; refresh before uploading it again.")},confirmButton={TextButton(onClick={leave=false;m.stop();exit()}){Text("Stop and leave")}},dismissButton={TextButton(onClick={leave=false}){Text("Stay")}})
}

@Composable private fun CloudNameDialog(title:String,action:String,busy:Boolean,error:String,close:()->Unit,initial:String="",confirm:(String)->Unit){var name by remember(initial){mutableStateOf(initial)};AlertDialog(onDismissRequest={if(!busy)close()},title={Text(title)},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(name,{name=it.take(180)},label={Text("Name")},singleLine=true,enabled=!busy,modifier=Modifier.fillMaxWidth());if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall);if(action=="Upload")Text("Existing files will not be replaced. Choose another name if this one is already used.",style=MaterialTheme.typography.bodySmall)}},confirmButton={TextButton(onClick={confirm(name)},enabled=!busy&&runCatching{CloudPaths.name(name)}.isSuccess){Text(if(busy)"Working…"else action)}},dismissButton={TextButton(onClick=close,enabled=!busy){Text("Cancel")}})}

@Composable internal fun CloudAccountEditor(existing:CloudAccount?,busy:Boolean,error:String,website:(String)->Unit,onConnect:(CloudAccount)->Unit){
 val providers=remember(existing?.id){cloudProviders}
 var provider by remember(existing?.id){mutableIntStateOf(if(existing==null)0 else providers.indexOfFirst{it.endpoint==existing.endpoint}.takeIf{it>=0}?:providers.lastIndex)};var menu by remember{mutableStateOf(false)};var help by remember{mutableStateOf(false)};var name by remember(existing?.id){mutableStateOf(existing?.name.orEmpty())};var endpoint by remember(existing?.id){mutableStateOf(existing?.endpoint?:providers[provider].endpoint)};var user by remember(existing?.id){mutableStateOf(existing?.user.orEmpty())};var password by remember(existing?.id){mutableStateOf(existing?.password.orEmpty())};var visible by remember{mutableStateOf(false)}
 val preset=providers[provider]
 Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  Text("Use with pCloud or Koofr. Already have an account? Sign in below.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
  Box{OutlinedButton(onClick={menu=true},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text(preset.name,Modifier.weight(1f));Icon(Icons.Outlined.ArrowDropDown,null)};DropdownMenu(menu,{menu=false}){providers.forEachIndexed{i,p->DropdownMenuItem(text={Text(p.name)},onClick={if(provider!=i)password="";provider=i;endpoint=p.endpoint;menu=false})}}}
  if(preset.signup.isNotBlank())TextButton(onClick={website(preset.signup)},enabled=!busy){Text("Get a free ${preset.name.substringBefore(" ·")} plan")}
  OutlinedTextField(name,{name=it.take(80)},label={Text("Account name (optional)")},singleLine=true,enabled=!busy,modifier=Modifier.fillMaxWidth())
  if(preset.endpoint.isBlank())OutlinedTextField(endpoint,{endpoint=it.take(4096);password=""},label={Text("Storage address")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Uri),singleLine=true,enabled=!busy,modifier=Modifier.fillMaxWidth())
  OutlinedTextField(user,{user=it.take(320)},label={Text("Email / username")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email),singleLine=true,enabled=!busy,modifier=Modifier.fillMaxWidth())
  OutlinedTextField(password,{password=it.take(4096)},label={Text(if(preset.name=="Koofr")"App password"else"Password")},visualTransformation=if(visible)VisualTransformation.None else PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),singleLine=true,enabled=!busy,trailingIcon={IconButton(onClick={visible=!visible}){Icon(if(visible)Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,if(visible)"Hide password"else"Show password")}},modifier=Modifier.fillMaxWidth())
  TextButton(onClick={help=true}){Text("Sign-in instructions")}
  if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall,modifier=Modifier.semantics{liveRegion=LiveRegionMode.Polite})
  Button(onClick={onConnect(CloudAccount(existing?.id?:UUID.randomUUID().toString(),name.ifBlank{preset.name},endpoint,user.trim(),password))},enabled=!busy&&user.isNotBlank()&&password.isNotEmpty()&&runCatching{CloudPaths.base(endpoint)}.isSuccess,modifier=Modifier.fillMaxWidth()){Text(if(busy)"Connecting…"else"Connect")}
  Text("Sign-in is saved encrypted on this device. OnKlav does not store your files on its own servers.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
 }
 if(help)AlertDialog(onDismissRequest={help=false},title={Text("${preset.name} sign-in")},text={Text(preset.instructions)},confirmButton={TextButton(onClick={help=false}){Text("Got it")}},dismissButton={if(preset.help.isNotBlank())TextButton(onClick={help=false;website(preset.help)}){Text(if(preset.name=="Koofr")"Open Koofr settings"else"Provider help")}})
}
internal fun cloudSize(c:android.content.Context,n:Long)=android.text.format.Formatter.formatShortFileSize(c,n.coerceAtLeast(0))
