package app.onklav.home

import android.Manifest
import android.content.*
import android.media.*
import android.net.Uri
import android.provider.*
import android.telecom.*
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import app.onklav.shared.OnKlavBackground
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.*

@OptIn(ExperimentalMaterial3Api::class,ExperimentalFoundationApi::class)
@android.annotation.SuppressLint("MissingPermission") // Voicemail lookup is user-started and failures route to setup.
@Composable internal fun PhoneWorkspace(exit:()->Unit,m:PhoneModel=viewModel(),initialNumber:String?=null,launchKey:Long=0,initialTab:String?=null,embeddedInHome:Boolean=false){
 val c=LocalContext.current;val colors=MaterialTheme.colorScheme
 val defaultPhone=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){m.load();m.status=if(PhoneAccess.role(c))"OnKlav is ready for incoming and ongoing calls"else"Choose OnKlav as your default Phone app to use its call screen"}
 fun chooseDefaultPhone(){runCatching{defaultPhone.launch(PhoneAccess.request(c))}.onFailure{m.status="Phone setup could not open. Try Setup & permissions."}}
 var setup by remember{mutableStateOf(false)};var settings by remember{mutableStateOf(false)};var person by remember{mutableStateOf<ContactRow?>(null)};var event by remember{mutableStateOf<PhoneEvent?>(null)};var remove by remember{mutableStateOf<PhoneEvent?>(null)};var block by remember{mutableStateOf<String?>(null)};var dialing by remember{mutableStateOf<String?>(null)};var speed by remember{mutableStateOf(false)};var assign by remember{mutableStateOf<Int?>(null)};var voicemail by remember{mutableStateOf(false)}
 val live by PhoneCalls.state.collectAsState();val preferences=c.getSharedPreferences("phone-ui",0);var tones by remember{mutableStateOf(preferences.getBoolean("tones",true))};var vibrate by remember{mutableStateOf(preferences.getBoolean("vibrate",true))};val view=LocalView.current
 val tone=remember{runCatching{ToneGenerator(AudioManager.STREAM_DTMF,55)}.getOrNull()};DisposableEffect(tone){onDispose{tone?.release()}}
 LaunchedEffect(launchKey,initialNumber){if(initialNumber!=null){m.number=PhoneRules.input(initialNumber);m.tab=initialTab?.takeIf{it in setOf("Keypad","Recents","Contacts")}?:"Keypad"}}
 LifecycleEventEffect(Lifecycle.Event.ON_RESUME){m.load()}
 BackHandler{if(setup)setup=false else if(settings)settings=false else exit()}
 fun openInternal(intent:Intent){runCatching{c.startActivity(intent)}.onFailure{m.status="OnKlav could not open this option."}}
 fun message(n:String)=openInternal(Intent(Intent.ACTION_SENDTO,Uri.fromParts("smsto",n,null)).setPackage(c.packageName))
 fun call(n:String){val number=PhoneRules.input(n);if(!PhoneAccess.role(c)){chooseDefaultPhone();return};if(!PhoneAccess.permission(c,Manifest.permission.CALL_PHONE)){setup=true;return};if(m.lines.size>1)dialing=number else m.call(number)}
 fun voicemailCall(line:PhoneLine?=null){
  if(!PhoneAccess.role(c)||!PhoneAccess.permission(c,Manifest.permission.READ_PHONE_STATE)||!PhoneAccess.permission(c,Manifest.permission.CALL_PHONE)){setup=true;return}
  if(line==null&&m.lines.size>1){voicemail=true;return}
  runCatching{val t=c.getSystemService(TelecomManager::class.java);val handle=line?.handle?:t.getDefaultOutgoingPhoneAccount("tel")?:m.lines.firstOrNull()?.handle;val n=PhoneLocalSettings.voicemail(c,handle).ifBlank{handle?.let{t.getVoiceMailNumber(it)}.orEmpty()};if(n.isNullOrBlank()){m.status="Add your carrier’s voicemail number in Phone settings.";settings=true}else m.call(PhoneRules.input(n),handle)}.onFailure{m.status="Voicemail is unavailable. Review Phone setup."}
 }
 fun digit(key:String){if(m.number.length<80)m.number=PhoneRules.input(m.number+key);if(vibrate)view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);if(tones){val n=when(key){"*"->ToneGenerator.TONE_DTMF_S;"#"->ToneGenerator.TONE_DTMF_P;else->key.toIntOrNull()?.let{ToneGenerator.TONE_DTMF_0+it}};n?.let{runCatching{tone?.startTone(it,90)}}}}
 if(setup){PlatformSetupScreen(done={setup=false;m.load()},messaging={},showMessagingOptions=false);return}
 val directory=m.tab!="Keypad"
 Box(Modifier.fillMaxSize()){
 if(directory&&!embeddedInHome)OnKlavBackground(Modifier.matchParentSize())
 Scaffold(modifier=Modifier.testTag("phone-workspace"),containerColor=if(directory)Color.Transparent else colors.background,contentColor=colors.onBackground,
  contentWindowInsets=if(embeddedInHome)WindowInsets(0,0,0,0)else ScaffoldDefaults.contentWindowInsets,
  topBar={Column{
   HomeHeader("Phone",back=if(embeddedInHome)null else exit,actions={
    IconButton(onClick={speed=true}){Icon(Icons.Outlined.StarOutline,"Speed dial",tint=colors.primary)}
    IconButton(onClick={voicemailCall()}){Icon(PhoneIcons.Voicemail,"Voicemail",tint=colors.primary)}
    if(m.tab=="Contacts")OriginalAction(app.onklav.shared.R.drawable.ref_action_add,"Add contact"){m.newContact()}
    OriginalAction(app.onklav.shared.R.drawable.ref_nav_settings,"Phone settings"){settings=true}
   })
   val tabs=listOf("Keypad","Recents","Contacts")
   TabRow(selectedTabIndex=tabs.indexOf(m.tab).coerceAtLeast(0),containerColor=colors.background,modifier=Modifier.testTag("phone-tabs")){
    tabs.forEach{tab->Tab(selected=m.tab==tab,onClick={m.tab=tab;m.query="";m.load()}){Text(tab,Modifier.padding(horizontal=4.dp,vertical=14.dp),style=MaterialTheme.typography.labelLarge,maxLines=1)}}
   }
  }}
 ){padding->Column(Modifier.fillMaxSize().padding(padding)){
  if(live.calls.isNotEmpty())Surface(onClick={PhoneCalls.show(c)},color=colors.primaryContainer,modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=6.dp),shape=RoundedCornerShape(14.dp)){Row(Modifier.padding(14.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)){Icon(PhoneIcons.Call,null);Text("Return to call",Modifier.weight(1f),fontWeight=FontWeight.SemiBold);Icon(Icons.Outlined.ChevronRight,null)}}
  if(!PhoneAccess.role(c))Surface(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=6.dp),shape=RoundedCornerShape(14.dp),color=colors.surfaceContainerHigh){Row(Modifier.padding(start=14.dp,end=4.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f).padding(vertical=10.dp)){Text(if(PhoneAccess.available(c))"Use OnKlav for calls"else"Calling is unavailable on this device",style=MaterialTheme.typography.titleSmall);if(PhoneAccess.available(c))Text("Use the OnKlav incoming and ongoing call screen",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)};if(PhoneAccess.available(c))TextButton(onClick={chooseDefaultPhone()}){Text("Use OnKlav")}}}
  if(m.status.isNotBlank())Row(Modifier.fillMaxWidth().padding(start=16.dp),verticalAlignment=Alignment.CenterVertically){Text(m.status,Modifier.weight(1f).semantics{liveRegion=LiveRegionMode.Polite},style=MaterialTheme.typography.bodySmall);IconButton(onClick={m.status=""}){Icon(Icons.Outlined.Close,"Dismiss status")}}
  if(m.busy&&m.tab!="Keypad")LinearProgressIndicator(Modifier.fillMaxWidth())
  if(m.tab=="Keypad")BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()){
   val compactPad=maxHeight<440.dp||LocalDensity.current.fontScale>1.25f
   val callSize=if(maxHeight<380.dp)48.dp else 60.dp
   val keyHeight=((maxHeight-callSize*2-phonePadRowGap(compactPad)*3)/4)
    .coerceIn(32.dp,phonePadKeyHeight(maxWidth-48.dp))
   Column(
    Modifier.fillMaxSize().padding(horizontal=24.dp).testTag("phone-keypad-group"),
    horizontalAlignment=Alignment.CenterHorizontally
   ){
    Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center){
     SelectionContainer{
      Text(PhoneRules.typing(m.number).ifBlank{"Enter a number"},
       Modifier.fillMaxWidth().semantics{contentDescription="Phone number: ${m.number}"},
       style=if(compactPad||m.number.length>18)MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
       maxLines=1,overflow=TextOverflow.Ellipsis,textAlign=androidx.compose.ui.text.style.TextAlign.Center,
       color=if(m.number.isBlank())colors.onSurfaceVariant else colors.onSurface)
     }
    }
    PhonePad(compact=compactPad,keyHeight=keyHeight,press=::digit,longPress={key->when(key){"0"->digit("+");"1"->voicemailCall();else->key.toIntOrNull()?.takeIf{it in 2..9}?.let{k->m.speedDial[k]?.let{call(it)}?:run{speed=true;assign=k}}}})
    Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center){
    Row(Modifier.widthIn(max=378.dp).fillMaxWidth().testTag("phone-call-actions"),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically){
     Box(Modifier.weight(1f),contentAlignment=Alignment.Center){TextButton(onClick={m.newContact(m.number)},enabled=m.number.isNotBlank()){Icon(Icons.Outlined.Add,null,Modifier.size(18.dp));Spacer(Modifier.width(4.dp));Text("Save")}}
     FilledIconButton(onClick={call(m.number)},enabled=PhoneRules.callable(m.number),modifier=Modifier.padding(horizontal=16.dp).size(callSize).testTag("phone-call-button"),shape=CircleShape,colors=phoneCallColors()){Icon(PhoneIcons.Call,"Call",Modifier.size(28.dp))}
     Box(Modifier.weight(1f),contentAlignment=Alignment.Center){Box(Modifier.size(48.dp).clip(CircleShape).combinedClickable(onClick={m.number=m.number.dropLast(1)},onLongClick={m.number=""}).semantics{role=Role.Button;contentDescription="Delete digit; hold to clear"},contentAlignment=Alignment.Center){Icon(PhoneIcons.Backspace,null,tint=colors.primary)}}
    }
    }
   }

  }else{
   Column(Modifier.fillMaxWidth().background(colors.background)){
   OutlinedTextField(m.query,{m.search(it)},placeholder={Text(if(m.tab=="Recents")"Search calls"else"Search contacts")},singleLine=true,leadingIcon={Icon(Icons.Outlined.Search,null)},trailingIcon={if(m.query.isNotEmpty())IconButton(onClick={m.search("")}){Icon(Icons.Outlined.Close,"Clear search")}},modifier=Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=8.dp),shape=RoundedCornerShape(28.dp),colors=contactDirectorySearchColors())
   val filters=FilterChipDefaults.filterChipColors(containerColor=colors.surface,selectedContainerColor=colors.primaryContainer,selectedLabelColor=colors.onPrimaryContainer)
   Row(Modifier.padding(horizontal=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
    if(m.tab=="Recents"){
     FilterChip(!m.missed,{m.missed=false;m.load()},label={Text("All calls")},colors=filters)
     FilterChip(m.missed,{m.missed=true;m.load()},label={Text("Missed")},colors=filters)
    }else{
     FilterChip(!m.favorites,{m.favorites=false},label={Text("All contacts")},colors=filters)
     FilterChip(m.favorites,{m.favorites=true},label={Text("Favorites")},colors=filters)
    }
   }
   }
   if(m.tab=="Recents"){
    if(!PhoneAccess.role(c)||!PhoneAccess.permission(c,Manifest.permission.READ_CALL_LOG))PhoneEmpty("Call history needs access","Complete Phone setup to see recent calls."){setup=true}
    else {val state=rememberLazyListState();LaunchedEffect(state,m.more){snapshotFlow{state.layoutInfo.visibleItemsInfo.lastOrNull()?.index?:-1}.collect{last->if(state.isScrollInProgress&&last>=m.events.size-4)m.load(true)}}
     LazyColumn(Modifier.weight(1f),state=state,contentPadding=PaddingValues(horizontal=8.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
      if(m.events.isEmpty()&&!m.busy)item{PhoneEmpty("No calls here",if(m.query.isNotBlank())"Try another name or number."else"Your recent calls will appear here.")}
      var prior=""
      m.events.forEach{e->
       val day=DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(e.date))
       if(day!=prior){item(key="day-${e.id}"){ContactDirectorySection(day)};prior=day}
       item(key=e.id){
        PhoneDirectoryCard(onClick={event=e}){
         val title=if(e.name.isBlank()&&e.callable)PhoneRules.display(e.number)else e.title
         key(e.callable,e.number){
          ContactAvatar(if(e.callable)e.number else "",title,Modifier.size(48.dp))
         }
         Column(Modifier.weight(1f).padding(horizontal=12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
          Text(title,fontSize=19.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
          Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)){
           Icon(if(e.type==CallLog.Calls.OUTGOING_TYPE)PhoneIcons.Up else PhoneIcons.Down,null,tint=if(e.type==CallLog.Calls.MISSED_TYPE)colors.error else colors.primary,modifier=Modifier.size(16.dp))
           Text("${PhoneRules.type(e.type)} · ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(e.date))}",fontSize=14.sp,color=colors.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
          }
         }
         PhoneQuickCall("Call ${e.title}",e.callable){call(e.number)}
        }
       }
      }
     }
    }
   }else{
    if(!PhoneAccess.permission(c,Manifest.permission.READ_CONTACTS))PhoneEmpty("Contacts need access","Allow contacts in OnKlav setup."){setup=true}else{val matching=remember(m.contacts,m.stars,m.favorites,m.query){m.contacts.filter{(!m.favorites||it.id in m.stars)&&(it.name.contains(m.query,true)||it.numbers.any{n->PhoneRules.input(n).contains(PhoneRules.input(m.query))&&PhoneRules.input(m.query).isNotBlank()})}};LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(horizontal=8.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
     if(matching.isEmpty()&&!m.busy)item{PhoneEmpty("No contacts found",if(m.favorites)"Star a contact to keep them close."else"Add a contact or try another search.")}
     matching.groupBy{it.letter}.forEach{(letter,people)->
      item(key="letter-$letter"){ContactDirectorySection(letter)}
      items(people,key={it.id}){p->
       PhoneDirectoryCard(onClick={person=p}){
        ContactAvatar(p,48.dp)
        Column(Modifier.weight(1f).padding(horizontal=12.dp)){
         Text(p.name,fontSize=19.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
         Text(contactNumberSummary(p),fontSize=14.sp,color=colors.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
        IconButton(onClick={m.favorite(p)},enabled=PhoneAccess.permission(c,Manifest.permission.WRITE_CONTACTS)){Icon(if(p.id in m.stars)Icons.Outlined.Star else Icons.Outlined.StarOutline,if(p.id in m.stars)"Remove favorite ${p.name}"else"Favorite ${p.name}",tint=colors.primary)}
        PhoneQuickCall("Call ${p.name}",p.numbers.any{PhoneRules.callable(PhoneRules.input(it))}){
         val numbers=p.numbers.filter{PhoneRules.callable(PhoneRules.input(it))}
         if(numbers.size==1)call(numbers.single())else person=p
        }
       }
      }
     }
    }}
   }
  }
 }}
 }
 if(settings)PhoneSettingsDialog(m,tones,vibrate,{tones=it;preferences.edit().putBoolean("tones",it).apply()},{vibrate=it;preferences.edit().putBoolean("vibrate",it).apply()},{settings=false;setup=true},{settings=false})

 person?.let{p->PhoneContactDetails(p,{person=null},{n->person=null;call(n)},{n->person=null;message(n)},{m.edit(p);person=null},{m.prepareRemoval(p);person=null},{n->person=null;block=n})}
 event?.let{e->PhoneRecentDetails(e,{event=null},{event=null;call(e.number)},{event=null;message(e.number)},{event=null;m.newContact(e.number)},{event=null;block=e.number},{event=null;remove=e})}

 remove?.let{e->PhoneConfirm("Remove call?","Only this call-history entry will be deleted.",{remove=null}){remove=null;m.remove(e)}}
 block?.let{n->PhoneConfirm("Block ${PhoneRules.display(n)}?","Android will block calls and text messages from this number. Manage blocked numbers in Phone settings.",{block=null}){block=null;m.block(PhoneRules.input(n))}}
 dialing?.let{n->AlertDialog(onDismissRequest={dialing=null},title={Text("Choose calling account")},text={Column{m.lines.forEach{line->TextButton(onClick={dialing=null;m.call(n,line.handle)},modifier=Modifier.fillMaxWidth()){Text(line.name)}}}},confirmButton={TextButton(onClick={dialing=null}){Text("Cancel")}})}
 if(speed)PhoneSpeedDialDialog(m,assign,{assign=it},{speed=false;assign=null},onCall={n->speed=false;assign=null;call(n)})
 if(voicemail)AlertDialog(onDismissRequest={voicemail=false},title={Text("Voicemail account")},text={Column{m.lines.forEach{line->TextButton(onClick={voicemail=false;voicemailCall(line)},modifier=Modifier.fillMaxWidth()){Text(line.name)}}}},confirmButton={TextButton(onClick={voicemail=false}){Text("Cancel")}})
 m.contactRemoval?.let{target->PhoneConfirm("Delete ${target.name}?","This removes the contact and its linked entries from your phone and may also remove it from synced accounts. Messages and call history are kept.",{m.contactRemoval=null}){m.contactRemoval=null;m.deleteContact(target)}}
 m.editing?.let{PhoneContactEditor(it,m)}
}
@Composable private fun PhoneDirectoryCard(onClick:()->Unit,content:@Composable RowScope.()->Unit){
 Surface(onClick=onClick,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.surface){
  Row(Modifier.padding(start=12.dp,top=8.dp,bottom=8.dp,end=8.dp),verticalAlignment=Alignment.CenterVertically,content=content)
 }
}
@Composable internal fun PhoneEmpty(title:String,body:String,action:(()->Unit)?=null){Column(Modifier.fillMaxWidth().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)){Icon(PhoneIcons.Call,null,tint=MaterialTheme.colorScheme.primary);Text(title,style=MaterialTheme.typography.titleMedium);Text(body,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);if(action!=null)TextButton(onClick=action){Text("Open setup")}}}
@Composable internal fun PhoneConfirm(title:String,body:String,close:()->Unit,confirm:()->Unit){AlertDialog(onDismissRequest=close,title={Text(title)},text={Text(body)},confirmButton={TextButton(onClick=confirm){Text("Confirm")}},dismissButton={TextButton(onClick=close){Text("Cancel")}})}
@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun PhonePad(press:(String)->Unit,longPress:((String)->Unit)?=null,compact:Boolean=false,keyHeight:Dp=if(compact)54.dp else 66.6.dp){val keyColors=dialPadColors(MaterialTheme.colorScheme);val rows=listOf(listOf("1" to "", "2" to "ABC","3" to "DEF"),listOf("4" to "GHI","5" to "JKL","6" to "MNO"),listOf("7" to "PQRS","8" to "TUV","9" to "WXYZ"),listOf("*" to "","0" to "+","#" to ""));Column(Modifier.widthIn(max=phonePadWidth(keyHeight)).fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(phonePadRowGap(compact))){rows.forEach{row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){row.forEach{(key,letters)->Column(Modifier.weight(1f).height(keyHeight).clip(RoundedCornerShape(22.dp)).background(keyColors.fill).border(BorderStroke(1.dp,keyColors.outline),RoundedCornerShape(22.dp)).combinedClickable(onClick={press(key)},onLongClick=longPress?.let{{it(key)}}).semantics{role=Role.Button;contentDescription=if(key=="0"&&longPress!=null)"0; hold for plus"else if(key=="1"&&longPress!=null)"1; hold for voicemail"else key},horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){if(keyHeight<48.dp){Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)){Text(key,style=MaterialTheme.typography.titleLarge,color=keyColors.ink);if(letters.isNotEmpty())Text(letters,fontSize=8.sp,color=keyColors.secondary,maxLines=1)}}else{Text(key,style=if(compact)MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,color=keyColors.ink);if(letters.isNotEmpty())Text(letters,style=MaterialTheme.typography.labelSmall,fontSize=if(compact)10.sp else 11.sp,color=keyColors.secondary)}}}}}}}
@Composable private fun PhoneContactEditor(initial:PhoneContactEdit,m:PhoneModel){var d by remember(initial){mutableStateOf(initial)};AlertDialog(onDismissRequest={if(!m.saving)m.editing=null},title={Text(if(d.raw==null)"New contact"else"Edit contact")},text={Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){OutlinedTextField(d.name,{d=d.copy(name=it.take(120))},label={Text("Name")},singleLine=true,modifier=Modifier.fillMaxWidth());d.numbers.forEachIndexed{i,n->Row(verticalAlignment=Alignment.CenterVertically){OutlinedTextField(n,{v->d=d.copy(numbers=d.numbers.mapIndexed{j,old->if(i==j)PhoneRules.input(v)else old})},label={Text("Phone ${i+1}")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Phone),modifier=Modifier.weight(1f),singleLine=true);if(d.numbers.size>1)IconButton(onClick={d=d.copy(numbers=d.numbers.filterIndexed{j,_->i!=j})}){Icon(Icons.Outlined.Close,"Remove phone ${i+1}")}}};if(d.numbers.size<10)TextButton(onClick={d=d.copy(numbers=d.numbers+"")}){Text("Add phone number")};Text(if(d.raw==null)"Saved as a local contact on this phone."else "Editing: ${d.account.ifBlank{"On this phone"}}. Other linked contacts are kept.",style=MaterialTheme.typography.bodySmall);if(m.status.isNotBlank())Text(m.status,style=MaterialTheme.typography.bodySmall)}},confirmButton={TextButton(onClick={m.save(d)},enabled=!m.saving&&d.name.isNotBlank()&&d.numbers.filter{it.isNotBlank()}.all{PhoneRules.callable(it)}){Text(if(m.saving)"Saving…"else"Save")}},dismissButton={TextButton(onClick={m.editing=null},enabled=!m.saving){Text("Cancel")}})}
