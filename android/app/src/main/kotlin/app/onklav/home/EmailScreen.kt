package app.onklav.home

import app.onklav.shared.OnKlavFileContracts

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.text.SpannableStringBuilder
import android.text.style.URLSpan
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.activity.compose.*
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.DateFormat
import java.util.Date

@Composable internal fun EmailWorkspace(){val c=LocalContext.current;val epoch by AppSdk.epoch.collectAsState();if(!remember(epoch){mailAccess(c)&&mailAccess(c,"storage.secrets")}){Text("Email access is off. Restore it in Settings > Apps > Email.",Modifier.padding(24.dp));return};val model:MailModel=viewModel(key="email-$epoch");EmailContent(model)}
@OptIn(ExperimentalMaterial3Api::class,ExperimentalFoundationApi::class)
@Composable internal fun EmailContent(m:MailModel,autoSync:Boolean=true){
 val c=LocalContext.current;val colors=MaterialTheme.colorScheme
 var accounts by remember{mutableStateOf(false)};var folders by remember{mutableStateOf(false)};var settings by remember{mutableStateOf(false)};var filters by remember{mutableStateOf(false)};var editAccount by remember{mutableStateOf<MailAccount?>(null)};var editRule by remember{mutableStateOf<MailRule?>(null)};var moving by remember{mutableStateOf<String?>(null)};var confirm by remember{mutableStateOf<String?>(null)};var link by remember{mutableStateOf<String?>(null)};var export by remember{mutableStateOf<Pair<MailHeader,MailAttachment?>?>(null)};var lastSync by remember{mutableStateOf(false)}
 val save=rememberLauncherForActivityResult(OnKlavFileContracts.CreateDocument("application/octet-stream")){uri->val target=export;if(uri!=null&&target!=null)m.saveAttachment(target.first,target.second,uri);export=null}
 val attachments=rememberLauncherForActivityResult(OnKlavFileContracts.OpenMultipleDocuments()){m.attach(it)}
 val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){m.settingsChanged()}
 val life=LocalLifecycleOwner.current
 DisposableEffect(life,m){val observer=LifecycleEventObserver{_,e->if(e==Lifecycle.Event.ON_STOP)m.flush()};life.lifecycle.addObserver(observer);onDispose{life.lifecycle.removeObserver(observer);m.flush();m.cancel()}}
 LaunchedEffect(m.accounts,m.busy){if(autoSync&&!lastSync&&m.accounts.isNotEmpty()&&!m.busy){lastSync=true;m.refresh()};m.settingsChanged()}
 BackHandler(enabled=m.draft!=null||m.opened!=null){if(m.busy)m.cancel()else if(m.draft!=null)m.closeDraft()else m.closeMessage()}
 fun openLink(url:String){if(mailAccess(c,"links.external"))runCatching{c.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}.onFailure{m.message("No browser could open this link",true)}else m.message("External links access is off",true)}
 Surface(Modifier.fillMaxSize(),color=colors.surface){Column(Modifier.fillMaxSize()){
  if(m.draft==null&&m.opened==null){
   Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically){TextButton(onClick={accounts=true},modifier=Modifier.weight(1f),contentPadding=PaddingValues(horizontal=6.dp)){Column(Modifier.weight(1f),horizontalAlignment=Alignment.Start){Text(if(m.account.isEmpty())"All inboxes" else m.accounts.firstOrNull{it.id==m.account}?.email?:"Email",maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.titleSmall);if(m.account.isNotEmpty()&&!m.folder.equals("INBOX",true))Text(m.folder,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.labelSmall)};Icon(Icons.Outlined.ExpandMore,null)};IconButton(onClick={folders=true},enabled=m.accounts.isNotEmpty()&&!m.busy){Icon(Icons.Outlined.FolderOpen,"Folders")};IconButton(onClick={settings=true}){Icon(Icons.Outlined.Settings,"Email settings")};FilledTonalIconButton(onClick={m.compose()},enabled=m.accounts.isNotEmpty()&&!m.busy){Icon(Icons.Outlined.Edit,"Compose email")}}
   if(m.accounts.isNotEmpty()){
    PrimaryTabRow(selectedTabIndex=listOf("Inbox","Starred","Drafts").indexOf(m.tab).coerceAtLeast(0)){listOf("Inbox","Starred","Drafts").forEach{label->Tab(selected=m.tab==label,onClick={m.tab(label)},enabled=!m.busy,text={Text(label)})}}
    if(m.tab!="Drafts"){Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically){OutlinedTextField(value=m.query,onValueChange={m.search(it)},enabled=!m.busy,placeholder={Text("Search email")},singleLine=true,leadingIcon={Icon(Icons.Outlined.Search,null)},trailingIcon={if(m.query.isNotEmpty())IconButton(onClick={m.search("")}){Icon(Icons.Outlined.Close,"Clear search")}},modifier=Modifier.weight(1f));IconButton(onClick={m.refresh()},enabled=!m.busy){Icon(Icons.Outlined.Refresh,"Check mail")}};Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically){FilterChip(selected=m.unread,onClick={m.unread=!m.unread},label={Text("Unread")});Spacer(Modifier.weight(1f));if(m.status=="Up to date")Text("Up to date",style=MaterialTheme.typography.labelSmall,color=colors.onSurfaceVariant);if(m.query.isNotBlank())TextButton(onClick={m.refresh(serverSearch=true)},enabled=!m.busy){Text("Search server")}}}
   }
  }
  if(m.draft!=null)Text(if(m.draftSaving)"Saving draft…"else if(m.error)"Draft needs attention"else if(m.draftSaved>0)"Draft saved on this device"else"Draft saves automatically",Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=4.dp).semantics{liveRegion=LiveRegionMode.Polite},style=MaterialTheme.typography.labelSmall,color=colors.onSurfaceVariant)
  if(m.busy){if(m.progress>=0)LinearProgressIndicator(progress={m.progress},modifier=Modifier.fillMaxWidth())else LinearProgressIndicator(Modifier.fillMaxWidth())}
  if(m.status.isNotBlank()&&(m.error||m.busy||m.status !in listOf("Up to date","Opening email"))){Row(Modifier.fillMaxWidth().background(if(m.error)colors.errorContainer else colors.surfaceContainer).padding(start=12.dp),verticalAlignment=Alignment.CenterVertically){Text(m.status,Modifier.weight(1f).semantics{liveRegion=LiveRegionMode.Polite},color=if(m.error)colors.onErrorContainer else colors.onSurfaceVariant,style=MaterialTheme.typography.bodySmall);IconButton(onClick={if(m.busy)m.cancel()else m.dismiss()}){Icon(Icons.Outlined.Close,if(m.busy)"Cancel operation" else"Dismiss")}}}
  when{
   m.draft!=null->MailCompose(m,onAttach={attachments.launch(arrayOf("*/*"))},onSend={confirm="send"},onDelete={confirm="draft"})
   m.opened!=null->{val h=m.opened!!;Column(Modifier.weight(1f)){
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically){app.onklav.shared.ClosePageButton(enabled=!m.busy,description="Close message",onClick=m::closeMessage);Spacer(Modifier.weight(1f));IconButton(onClick={m.star(h)},enabled=!m.busy){MailStar(h.starred)};IconButton(onClick={m.act("archive")},enabled=!m.busy){Icon(Icons.Outlined.Archive,"Archive")};IconButton(onClick={moving="move"},enabled=!m.busy){Icon(Icons.Outlined.FolderOpen,"Move message")};IconButton(onClick={confirm="trash"},enabled=!m.busy){Icon(Icons.Outlined.Delete,"Move to Trash")}}
    LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
     item{MailConversationMembers(m,h)}
     item{Text(h.subject.ifBlank{"(No subject)"},style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold)}
     item{Column(verticalArrangement=Arrangement.spacedBy(5.dp)){Text(h.from,style=MaterialTheme.typography.titleSmall);Text("To: ${h.to}",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant);Text(mailDate(h.date),style=MaterialTheme.typography.labelMedium,color=colors.onSurfaceVariant);HorizontalDivider(Modifier.padding(top=8.dp),color=colors.outlineVariant)}}
     item{if(m.body==null){if(!m.busy)TextButton(onClick={m.open(h)}){Text("Retry opening message")}}else{val body=m.body!!;val text=remember(body){if(body.html.isBlank())SpannableStringBuilder(body.text)else MailMime.display(body.html)};AndroidView(factory={ctx->TextView(ctx).apply{setTextIsSelectable(true);movementMethod=LinkMovementMethod.getInstance()}},update={v->val content=SpannableStringBuilder(text);content.getSpans(0,content.length,URLSpan::class.java).forEach{old->val a=content.getSpanStart(old);val b=content.getSpanEnd(old);content.removeSpan(old);content.setSpan(object:android.text.style.ClickableSpan(){override fun onClick(widget:android.view.View){link=old.url}},a,b,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)};v.text=content;v.setTextColor(colors.onSurface.toArgb());v.setLinkTextColor(colors.primary.toArgb());v.textSize=16f},modifier=Modifier.fillMaxWidth())}}
     m.body?.attachments?.forEach{f->item(key=f.part){OutlinedCard(Modifier.fillMaxWidth()){Row(Modifier.padding(8.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Outlined.AttachFile,null);Column(Modifier.weight(1f).padding(horizontal=8.dp)){Text(f.name,style=MaterialTheme.typography.bodyMedium,maxLines=2);if(f.size>0)Text(mailSize(f.size),style=MaterialTheme.typography.labelSmall,color=colors.onSurfaceVariant)};IconButton(onClick={export=h to f;save.launch(f.name)},enabled=!m.busy){Icon(Icons.Outlined.Download,"Download ${f.name}")}}}}}
     if(m.body!=null)item{Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){FilledTonalButton(onClick={m.compose(h,"reply")},enabled=!m.busy){Icon(Icons.Outlined.Reply,null);Text("Reply")};OutlinedButton(onClick={m.compose(h,"replyAll")},enabled=!m.busy){Text("Reply all")};OutlinedButton(onClick={m.compose(h,"forward")},enabled=!m.busy){Icon(Icons.Outlined.Forward,null);Text("Forward")}}}
     item{Row(Modifier.horizontalScroll(rememberScrollState())){TextButton(onClick={m.act("unread")},enabled=!m.busy){Text("Mark unread")};TextButton(onClick={export=h to null;save.launch("${h.subject.take(70).replace(Regex("[^A-Za-z0-9 ._-]"),"_").ifBlank{"Email"}}.eml")},enabled=!m.busy){Text("Save original")};TextButton(onClick={moving="copy"},enabled=!m.busy){Text("Copy to folder")};if(m.folders[h.account]?.any{it.path==h.folder&&it.role=="trash"}==true)TextButton(onClick={confirm="permanent"},enabled=!m.busy){Text("Delete permanently",color=colors.error)}}}
    }
   }}
   m.accounts.isEmpty()&&!m.busy->{Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){Icon(Icons.Outlined.Email,null,Modifier.size(64.dp),tint=colors.primary);Spacer(Modifier.height(18.dp));Text("Your mail, together",style=MaterialTheme.typography.headlineSmall);Spacer(Modifier.height(22.dp));MailSetupChoices{editAccount=MailAccount.preset(it)}}}
   m.tab=="Drafts"->{LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(12.dp)){item{Text("Drafts & sent",style=MaterialTheme.typography.titleSmall,modifier=Modifier.padding(8.dp))};val rows=m.drafts.filter{m.account.isEmpty()||it.account==m.account}.sortedByDescending{it.modified};if(rows.isEmpty())item{MailEmpty("No saved drafts")};items(rows,key={it.id}){d->ListItem(headlineContent={Text(d.subject.ifBlank{"(No subject)"},maxLines=1,overflow=TextOverflow.Ellipsis)},supportingContent={Text("${d.phase.replaceFirstChar{it.uppercase()}} · ${d.to.ifBlank{"No recipient"}}",maxLines=1,overflow=TextOverflow.Ellipsis)},leadingContent={Icon(if(d.phase=="sent")Icons.Outlined.CheckCircle else Icons.Outlined.Drafts,null)},modifier=Modifier.clickable(enabled=!m.busy){m.editDraft(d)});HorizontalDivider(color=colors.outlineVariant.copy(alpha=.5f))}}}
   else->{if(m.selected.isNotEmpty())Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically){IconButton(onClick={m.selected=emptySet()}){Icon(Icons.Outlined.Close,"Cancel selection")};Text("${m.selected.size}");IconButton(onClick={m.act("read")},enabled=!m.busy){Icon(Icons.Outlined.MarkEmailRead,"Mark selected read")};IconButton(onClick={m.act("star")},enabled=!m.busy){Icon(Icons.Outlined.StarOutline,"Star selected")};IconButton(onClick={m.act("archive")},enabled=!m.busy){Icon(Icons.Outlined.Archive,"Archive selected")};IconButton(onClick={moving="move"},enabled=!m.busy){Icon(Icons.Outlined.FolderOpen,"Move selected")};IconButton(onClick={confirm="trash"},enabled=!m.busy){Icon(Icons.Outlined.Delete,"Trash selected")}}
    val rows by remember(m){derivedStateOf{m.visible}}
    val conversations=remember(rows,m.threaded){if(m.threaded)mailConversations(rows)else rows.map{listOf(it)}}
    val mailList=rememberLazyListState()
    LaunchedEffect(mailList,m.account,m.folder,m.tab){snapshotFlow{Triple(mailList.isScrollInProgress,mailList.layoutInfo.visibleItemsInfo.lastOrNull()?.index?:-1,mailList.layoutInfo.totalItemsCount)}.collect{(scrolling,last,total)->if(scrolling&&last>=total-3&&total>0&&!m.busy&&!m.error&&m.tab=="Inbox"&&m.more.values.any{it})m.refresh(older=true)}}
    LazyColumn(Modifier.weight(1f),state=mailList,contentPadding=PaddingValues(start=10.dp,end=10.dp,top=4.dp,bottom=16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
     if(rows.isEmpty()&&!m.busy)item{MailEmpty(if(m.query.isNotBlank())"No matching email"else if(m.unread)"No unread email"else if(m.tab=="Starred")"No starred email"else if(m.accounts.none{it.enabled})"Mail checking is paused · enable an account in Settings"else"Your inbox is clear")}
     items(conversations,key={it.first().key}){conversation->MailConversationRow(conversation,m)}
     if(rows.isNotEmpty()&&m.more.values.any{it}&&m.tab=="Inbox")item{TextButton(onClick={m.refresh(older=true)},enabled=!m.busy,modifier=Modifier.fillMaxWidth()){Text(if(m.busy)"Loading earlier email…"else"Load earlier email")}}
    }

   }
  }
 }}
 if(accounts)MailDialog("Accounts",{accounts=false}){
  TextButton(onClick={accounts=false;m.choose("")},modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.AllInbox,null);Text("All inboxes",Modifier.weight(1f))}
  m.accounts.forEach{a->Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f).clickable{accounts=false;m.choose(a.id)}.padding(10.dp)){Text(a.name.ifBlank{a.email},style=MaterialTheme.typography.titleSmall);if(a.name.isNotBlank())Text(a.email,style=MaterialTheme.typography.bodySmall);val state=m.accountStatus[a.id]?:c.getSharedPreferences("email-sync",0).getString(a.id,null);if(state!=null&&state !in listOf("Up to date","Connected"))Text(state,style=MaterialTheme.typography.bodySmall,color=colors.error)};IconButton(onClick={editAccount=a;accounts=false}){Icon(Icons.Outlined.Settings,"Manage ${a.email}")}}}
  FilledTonalButton(onClick={editAccount=MailAccount.preset("Yahoo");accounts=false},modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.Add,null);Text("Add account")}
 }
 if(settings)MailDialog("Email settings",{settings=false}){
  TextButton(onClick={settings=false;accounts=true}){Icon(Icons.Outlined.ManageAccounts,null);Text("Accounts & signatures")};TextButton(onClick={settings=false;filters=true}){Icon(Icons.Outlined.FilterList,null);Text("Filters")};Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Group conversations",style=MaterialTheme.typography.titleSmall);Text("Keep related replies together",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)};Switch(m.threaded,m::threading)};Text("Check for new mail",style=MaterialTheme.typography.titleSmall);var interval by remember{mutableIntStateOf(c.getSharedPreferences("email-sync",0).getInt("minutes",30))};MailChoices(listOf(0,15,30,60).map{if(it==0)"Manual" else"$it min"},if(interval==0)"Manual" else"$interval min"){label->interval=if(label=="Manual")0 else label.substringBefore(' ').toInt();c.getSharedPreferences("email-sync",0).edit().putInt("minutes",interval).commit();m.settingsChanged()};TextButton(onClick={permission.launch(Manifest.permission.POST_NOTIFICATIONS)}){Text("Enable email notifications")};TextButton(onClick={confirm="cache"}){Text("Clear downloaded mail")};TextButton(onClick={confirm="help"}){Text("Email help")}
 }
 if(filters)MailDialog("Filters",{filters=false}){FilledTonalButton(onClick={editRule=MailRule(name="",account=m.account)}){Icon(Icons.Outlined.Add,null);Text("Add filter")};if(m.rules.isEmpty())Text("No filters",color=colors.onSurfaceVariant);m.rules.forEach{r->Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f).clickable{editRule=r}.padding(8.dp)){Text(r.name,style=MaterialTheme.typography.titleSmall);Text("${r.field}: ${r.contains} · ${r.action}",style=MaterialTheme.typography.bodySmall)};Switch(checked=r.enabled,onCheckedChange={m.rule(r.copy(enabled=it))},enabled=!m.busy)}};Text("Filters run on the inbox email checked by OnKlav.",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)}
 if(editRule!=null)MailRuleDialog(editRule!!,m,onClose={editRule=null})
 if(editAccount!=null)MailAccountDialog(editAccount!!,m,onClose={editAccount=null},onWeb=::openLink)
 if(folders||moving!=null)MailFolderDialog(m,moving,onClose={folders=false;moving=null})
 if(link!=null)AlertDialog(onDismissRequest={link=null},title={Text("Open link?")},text={Text(link!!)},confirmButton={TextButton(onClick={openLink(link!!);link=null}){Text("Open")}},dismissButton={TextButton(onClick={link=null}){Text("Cancel")}})
 if(confirm!=null){val action=confirm!!;val title=when(action){"send"->"Send email?";"draft"->"Delete local draft?";"trash"->"Move to Trash?";"permanent"->"Delete permanently?";"cache"->"Clear downloaded mail?";else->"Email help"};val detail=when(action){"send"->"To: ${m.draft?.to.orEmpty()}${m.draft?.cc?.takeIf{it.isNotBlank()}?.let{"\nCc: $it"}.orEmpty()}${m.draft?.bcc?.takeIf{it.isNotBlank()}?.let{"\nBcc: $it"}.orEmpty()}\n${m.draft?.subject.orEmpty()}";"draft"->"This removes the draft and its local attachments.";"trash"->"The selected messages will move to each account’s Trash folder.";"permanent"->"This cannot be undone.";"cache"->"Accounts, drafts and server mail are kept.";else->"Add accounts from the account menu. All inboxes combines your accounts. Long-press a message to select several.\n\nUse folders to organize server mail. Filters run on inbox messages when OnKlav checks mail. Search covers downloaded headers; Search server finds older sender, recipient and subject matches.\n\nDrafts save on this device. Send asks for confirmation. Unconfirmed delivery is never retried automatically: check your provider’s Sent folder before sending another copy.\n\nBackground checks follow Android’s battery and network scheduling. External images are never loaded in messages.\n\nChoose Yahoo, Google or Private domain. Yahoo and Google use app passwords; Setup directions includes account settings and help links. Private domain requires an existing mailbox and secure IMAP/SMTP settings from your email hosting company; owning a domain alone does not provide email hosting."};AlertDialog(onDismissRequest={confirm=null},title={Text(title)},text={Text(detail,Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState()))},confirmButton={TextButton(onClick={confirm=null;when(action){"send"->m.send();"draft"->m.draft?.let(m::deleteDraft);"trash"->m.act("trash");"permanent"->m.act("delete");"cache"->m.clearCache()}}){Text(if(action=="help")"Close" else if(action=="send")"Send" else"Confirm")}},dismissButton={if(action!="help")TextButton(onClick={confirm=null}){Text("Cancel")}})}
}
@Composable internal fun MailStar(selected:Boolean){Icon(if(selected)Icons.Outlined.Star else Icons.Outlined.StarOutline,if(selected)"Remove star" else"Star email",tint=if(selected)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)}
@Composable private fun MailEmpty(text:String){Box(Modifier.fillMaxWidth().padding(36.dp),contentAlignment=Alignment.Center){Text(text,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
internal fun mailDate(t:Long)=DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(Date(t))
internal fun mailShortDate(t:Long)=DateFormat.getDateInstance(DateFormat.SHORT).format(Date(t))
internal fun mailSize(n:Long)=if(n<1000)"$n B" else if(n<1_000_000)"${n/1000} KB" else"%.1f MB".format(n/1_000_000f)
@Composable internal fun MailDialog(title:String,close:()->Unit,content:@Composable ColumnScope.()->Unit){AlertDialog(onDismissRequest=close,title={Text(title)},text={Column(Modifier.fillMaxWidth().heightIn(max=480.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp),content=content)},confirmButton={TextButton(onClick=close){Text("Done")}})}
@Composable internal fun MailChoices(labels:List<String>,value:String,choose:(String)->Unit){Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){labels.forEach{label->FilterChip(selected=label==value,onClick={choose(label)},label={Text(label)})}}}
