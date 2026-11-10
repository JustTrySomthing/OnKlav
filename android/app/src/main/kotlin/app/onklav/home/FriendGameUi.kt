package app.onklav.home

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.*
import org.json.JSONObject

/** Native counterpart of Inner Circle's recipient -> side -> ConnectionDialog flow. */
@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun FriendInviteDialog(game:PlatformGame,preset:String?,existing:String?=null,close:()->Unit) {
    val c=LocalContext.current;val scope=rememberCoroutineScope()
    var peer by rememberSaveable{mutableStateOf(preset.orEmpty())}
    var selected by rememberSaveable{mutableStateOf(existing)}
    var stage by rememberSaveable{mutableStateOf(if(existing!=null)"connect" else if(preset.isNullOrBlank())"opponent" else if(game.id=="four")"side" else "connect")}
    var dogs by rememberSaveable{mutableStateOf(false)}
    var status by remember{mutableStateOf<JSONObject?>(null)}
    var error by remember{mutableStateOf<String?>(null)};var busy by remember{mutableStateOf(false)}
    var verifyPeer by rememberSaveable{mutableStateOf<String?>(null)}
    var verified by remember{mutableStateOf<Set<String>>(emptySet())}
    var contacts by remember{mutableStateOf<List<ContactRow>>(emptyList())}
    var query by rememberSaveable{mutableStateOf("")}
    fun act(work:suspend ()->Unit){if(busy)return;busy=true;scope.launch{try{work()}catch(e:Exception){error=e.message?:"Unable to connect"}finally{busy=false}}}
    fun invite(){error=null;act{selected=withContext(Dispatchers.IO){FriendGames.invite(c,peer,game.id,dogs)}}}
    fun cancel(){if(busy)return;act{selected?.let{id->withContext(Dispatchers.IO){FriendGames.cancelInvitation(c,id)}};close()}}
    fun recipient(number:String){peer=number;error=null;stage=if(game.id=="four")"side" else "connect"}
    LaunchedEffect(stage){
        if(stage=="connect"&&selected==null)invite()
        if(stage=="contacts")try{contacts=withContext(Dispatchers.IO){loadPhoneContacts(c)};verified=withContext(Dispatchers.IO){FriendGames.verifiedPeers(c)};if(contacts.isEmpty())stage="number"}catch(e:Exception){error="Allow Contacts in Setup, or enter their number instead."}
    }
    LaunchedEffect(selected){val id=selected?:return@LaunchedEffect;while(isActive){
        try{status=withContext(Dispatchers.IO){FriendGames.find(c,id)}
            if(status?.optString("status")=="active"){WebGameActivity.launch(c,game.id,friend=id);close();return@LaunchedEffect}
        }catch(e:Exception){error=e.message};delay(500)
    }}
    val state=status?.optString("status")
    val failed=error!=null||state in setOf("expired","declined","ended","needs_game")||status?.optString("error")?.isNotBlank()==true
    AlertDialog(onDismissRequest={cancel()},title={Text(when(stage){"opponent"->"Choose your opponent";"number"->"Invite by number";"contacts"->"Choose contact · hold to reverify";"side"->"Choose your side";else->game.title})},text={
        Column(Modifier.fillMaxWidth().heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
            when(stage){
                "opponent"->{
                    OpponentChoice("Choose contact",app.onklav.shared.R.drawable.ref_nav_contacts){stage="contacts"}
                    OpponentChoice("Enter number",app.onklav.shared.R.drawable.ref_app_phone){stage="number"}
                }
                "number"->OutlinedTextField(peer,{peer=it;error=null},label={Text("Mobile number")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Phone),modifier=Modifier.fillMaxWidth())
                "contacts"->{
                    OutlinedTextField(query,{query=it},label={Text("Search contacts")},singleLine=true,modifier=Modifier.fillMaxWidth())
                    val numbers=contacts.filter{query.isBlank()||it.name.contains(query,true)||it.numbers.any{n->n.contains(query)}}.flatMap{person->person.numbers.map{person.name to it}}
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max=280.dp)){
                        items(numbers){(name,number)->
                            Text("$name · $number"+(if(phoneKey(number) in verified)"\n✓ OnKlav Verified"else""),Modifier.fillMaxWidth().combinedClickable(onClick={recipient(number)},onLongClick={verifyPeer=number}).padding(12.dp))
                        }
                    }
                    TextButton(onClick={stage="number"}){Text("Enter number")}
                }
                "side"->{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    app.onklav.shared.ChoiceButton("Cats",Modifier.weight(1f)){dogs=false;stage="connect"}
                    app.onklav.shared.ChoiceButton("Dogs",Modifier.weight(1f)){dogs=true;stage="connect"}
                }}
                else->{
                    Text(app.onklav.shared.displayPhone(peer),Modifier.align(Alignment.CenterHorizontally))
                    if(!failed&&state!="invite")CircularProgressIndicator(Modifier.size(32.dp).align(Alignment.CenterHorizontally))
                    Text(when{error!=null->"Unable to Connect";failed&&state=="checking"->"Unable to Verify User";failed&&state=="outgoing"->"Unable to Connect";state=="checking"->"Verifying User…";state=="outgoing"->"Waiting for User to Accept…";state=="invite"->"Game invitation";state=="declined"->"Invitation Declined";state=="needs_game"->"User Needs an App Update";state=="expired"->"Unable to Verify User";state=="ended"->"Unable to Connect";else->"Connecting…"},Modifier.align(Alignment.CenterHorizontally))
                    status?.optString("error")?.takeIf{it.isNotBlank()}?.let{Text(it,color=MaterialTheme.colorScheme.error)}
                }
            }
            error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
            if(stage=="connect")Row(Modifier.fillMaxWidth().padding(top=16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                Button(enabled=!busy,onClick={cancel()},modifier=Modifier.weight(1f).heightIn(min=48.dp),shape=RoundedCornerShape(12.dp),colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.background,contentColor=MaterialTheme.colorScheme.onSurface)){Text(if(state=="invite")"Decline"else"Cancel")}
                if(failed||state=="invite")Button(enabled=!busy,onClick={
                    if(state=="invite")act{withContext(Dispatchers.IO){FriendGames.accept(c,selected!!,true)}}
                    else{error=null;status=null;act{selected=withContext(Dispatchers.IO){FriendGames.restartInvitation(c,selected,peer,game.id,dogs)}}}
                },modifier=Modifier.weight(1f).heightIn(min=48.dp),shape=RoundedCornerShape(12.dp),colors=ButtonDefaults.buttonColors(containerColor=Color(0xff7de0b4),contentColor=Color(0xff052d1e))){Text(if(state=="invite")"Accept"else"Try Again")}
            }
        }
    },confirmButton={if(stage=="number")TextButton(enabled=!busy&&peer.isNotBlank(),onClick={if(FriendGames.validNumber(peer))recipient(peer)else error="Enter a mobile number."}){Text("Invite")}},dismissButton={if(stage!="connect")TextButton(enabled=!busy,onClick={cancel()}){Text("Cancel")}})
    verifyPeer?.let{number->FriendVerificationDialog(number){verifyPeer=null;scope.launch{verified=withContext(Dispatchers.IO){FriendGames.verifiedPeers(c)}}}}

}

@Composable private fun OpponentChoice(label:String,icon:Int,action:()->Unit) {
    Surface(onClick=action,modifier=Modifier.fillMaxWidth().heightIn(min=60.dp),shape=RoundedCornerShape(10.dp),color=MaterialTheme.colorScheme.background){
        Row(Modifier.padding(horizontal=16.dp,vertical=16.dp),verticalAlignment=Alignment.CenterVertically){
            Icon(painterResource(icon),null,Modifier.size(24.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp));Text(label,fontSize=16.sp,color=MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable private fun FriendVerificationDialog(peer:String,close:()->Unit) {
    val c=LocalContext.current;val scope=rememberCoroutineScope()
    var state by remember{mutableStateOf("idle")};var error by remember{mutableStateOf<String?>(null)};var busy by remember{mutableStateOf(false)}
    LaunchedEffect(peer){while(isActive){try{state=withContext(Dispatchers.IO){FriendGames.verification(c,peer)}}catch(e:Exception){error=e.message?:"Unable to verify user"};delay(500)}}
    fun stop(){if(busy)return;busy=true;scope.launch{try{withContext(Dispatchers.IO){FriendGames.cancelVerification(c,peer)};close()}catch(e:Exception){error=e.message?:"Unable to close verification"}finally{busy=false}}}
    AlertDialog(onDismissRequest={stop()},title={Text("App verification")},text={Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp),horizontalAlignment=Alignment.CenterHorizontally){
        Text(app.onklav.shared.displayPhone(peer))
        if(state=="verified")Text("✓ OnKlav Verified")
        if(state=="checking"&&error==null){CircularProgressIndicator(Modifier.size(32.dp));Text("Verifying User…")}
        if(state=="expired")Text("Unable to Verify User")
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        if(state!="checking"||error!=null)TextButton(enabled=!busy,onClick={busy=true;error=null;scope.launch{try{withContext(Dispatchers.IO){FriendGames.verify(c,peer)}}catch(e:Exception){error=e.message}finally{busy=false}}}){Text(if(error!=null||state=="expired")"Try Again"else if(state=="verified")"Reverify"else"Click To Verify User")}
    }},confirmButton={TextButton(enabled=!busy,onClick={stop()}){Text("Close")}})
}

@Composable internal fun FriendSessionsPanel(peer: String?, availableGameIds: Set<String>) {
    val c=LocalContext.current;val scope=rememberCoroutineScope();var quit by remember{mutableStateOf<JSONObject?>(null)};var rows by remember{mutableStateOf<List<JSONObject>>(emptyList())};var error by remember{mutableStateOf<String?>(null)};var busy by remember{mutableStateOf(false)}
    LaunchedEffect(Unit){while(isActive){try{rows=withContext(Dispatchers.IO){FriendGames.list(c)}}catch(e:Exception){error=e.message};delay(1200)}}
    fun action(work:()->Unit){if(busy)return;busy=true;scope.launch{try{withContext(Dispatchers.IO){work()}}catch(e:Exception){error=e.message}finally{busy=false}}}
    rows.filter { it.getString("game") in availableGameIds && (peer == null || phoneKey(it.getString("peer")) == phoneKey(peer)) }
        .take(32).forEach { m ->
        val id = m.getString("id")
        val game = m.getString("game")
        val state = m.getString("status")
        OutlinedCard(Modifier.fillMaxWidth().padding(vertical=4.dp)){Column(Modifier.padding(12.dp)){
            Text((platformGames.firstOrNull{it.id==game}?.title?:game)+" · "+app.onklav.shared.displayPhone(m.getString("peer")))
            Text(when(state){"invite"->"Game invitation";"active"->"Friend game · ready to play";"checking"->"Checking OnKlav…";"outgoing"->"Waiting for acceptance";else->state.replaceFirstChar{it.uppercase()}},style=MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                when(state){
                    "invite"->{TextButton(enabled=!busy,onClick={action{FriendGames.accept(c,id,true)}}){Text("Accept")};TextButton(enabled=!busy,onClick={action{FriendGames.accept(c,id,false)}}){Text("Decline")}}
                    "active"->{TextButton(onClick={WebGameActivity.launch(c,game,friend=id)}){Text("Play")};TextButton(enabled=!busy,onClick={action{FriendGames.retry(c,id)}}){Text("Retry update")};TextButton(enabled=!busy,onClick={quit=m}){Text("Quit")}}
                    "checking","outgoing"->{TextButton(enabled=!busy,onClick={action{FriendGames.retry(c,id)}}){Text("Retry")};TextButton(enabled=!busy,onClick={action{FriendGames.quit(c,id)}}){Text("Cancel")}}
                    else->TextButton(enabled=!busy,onClick={action{FriendGames.remove(c,id)}}){Text("Remove")}
                }
            }
        }}
    };error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    quit?.let{m->AlertDialog(onDismissRequest={quit=null},title={Text("Are you sure you want to quit?")},text={Text("You will lose your progress.")},confirmButton={Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){Button(onClick={quit=null},colors=ButtonDefaults.buttonColors(containerColor=androidx.compose.ui.graphics.Color(0xff318457))){Text("Resume")};Button(onClick={val id=m.getString("id");quit=null;action{FriendGames.quit(c,id);if(GameSessionStatus.open(c).current()==m.getString("game"))GameSessionStatus.open(c).save(null)}},colors=ButtonDefaults.buttonColors(containerColor=androidx.compose.ui.graphics.Color(0xffb53c46))){Text("Quit")}}})}

}
