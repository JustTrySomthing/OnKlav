package app.onklav.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.*
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.onklav.shared.*
import app.onklav.shared.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class ContactRow(val id:Long,val name:String,val photo:String?,val numbers:List<String>) {
    val letter get()=name.trim().firstOrNull()?.uppercaseChar()?.takeIf{it.isLetter()}?.toString()?:"#"
    val initials get()=name.trim().split(Regex("\\s+")).filter{it.isNotEmpty()}.take(2).joinToString(""){it.take(1)}.uppercase()
}

@Composable
fun ContactsScreen(exit: () -> Unit, walkie:((String)->Unit)?=null, selectionLabel:String="Message", pick: (String) -> Unit) {
    val context=LocalContext.current
    var query by rememberSaveable{mutableStateOf("")}
    var epoch by remember{mutableIntStateOf(0)}
    val contactMemory:ContactsMemory=androidx.lifecycle.viewmodel.compose.viewModel()
    var rows by contactMemory.rows
    var error by remember{mutableStateOf<String?>(null)}
    var loadError by remember{mutableStateOf<String?>(null)}
    var loading by remember{mutableStateOf(true)}
    var verified by remember{mutableStateOf<Set<String>>(emptySet())}
    var choosing by remember{mutableStateOf<ContactRow?>(null)}
    var calling by remember{mutableStateOf(false)}
    val granted=context.checkSelfPermission(Manifest.permission.READ_CONTACTS)==PackageManager.PERMISSION_GRANTED
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){epoch++}
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { epoch++ }
    LaunchedEffect(epoch,granted){
        loading=granted;loadError=null;choosing=null;if(!granted)rows=emptyList()
        try {
            if(granted) {
                rows=loadPhoneContacts(context)
                verified=withContext(Dispatchers.IO){runCatching{FriendGames.verifiedPeers(context)}.getOrDefault(emptySet())}
            }
        } catch(e:CancellationException){throw e}
        catch(e:Exception){loadError="Contacts could not be loaded. Check Contacts permission and try again."}
        finally{loading=false}
    }
    val matching=remember(rows,query){
        val digits=phoneInput(query)
        rows.filter{it.name.contains(query,true)||it.numbers.any{n->n.contains(query,true)||(digits.isNotEmpty()&&phoneInput(n).contains(digits))}}
    }
    fun choose(person:ContactRow,isWalkie:Boolean){
        calling=isWalkie
        if(person.numbers.size==1){if(isWalkie)walkie?.invoke(person.numbers[0])else pick(person.numbers[0])}
        else if(person.numbers.isNotEmpty())choosing=person
    }
    BackHandler(onBack=exit)
    Box(Modifier.fillMaxSize()) {
        OnKlavBackground(Modifier.matchParentSize())
        Column(Modifier.fillMaxSize().testTag("contacts-screen")) {
            Column(Modifier.background(MaterialTheme.colorScheme.background)) {
                HomeHeader("Contacts",back=exit) {
                    OriginalAction(R.drawable.ref_action_add,"Add Contact") {
                        try {context.startActivity(Intent(Intent.ACTION_INSERT,ContactsContract.Contacts.CONTENT_URI))}
                        catch(_:android.content.ActivityNotFoundException){error="No Contacts app is available to add a contact."}
                    }
                }
                OutlinedTextField(query,{query=it},Modifier.fillMaxWidth().testTag("contacts-search").padding(horizontal=8.dp,vertical=12.dp),placeholder={Text("Search all contacts")},singleLine=true,shape=RoundedCornerShape(28.dp),
                    colors=contactDirectorySearchColors())
            }
            if(!granted){Text("Allow contacts to see everyone saved on your phone.",Modifier.padding(16.dp));ChoiceButton("Allow Contacts",outlined=false){permission.launch(Manifest.permission.READ_CONTACTS)}}
            else LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                matching.groupBy{it.letter}.forEach{(letter,people)->
                    item("letter-$letter"){ContactDirectorySection(letter)}
                    items(people,key={it.id}){person->
                        Row(Modifier.fillMaxWidth().heightIn(min=56.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            ContactAvatar(person)
                            Column(Modifier.weight(1f)) {
                                Text(person.name,fontSize=20.sp,maxLines=2)
                                Text(contactNumberSummary(person),fontSize=15.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                if(person.numbers.any{phoneKey(it) in verified}) Text("Verified OnKlav",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
                            }
                            Surface(shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.surface){OriginalAction(if(selectionLabel=="Message") R.drawable.ref_nav_messages else R.drawable.ref_action_add,"$selectionLabel ${person.name}",enabled=person.numbers.isNotEmpty()){choose(person,false)}}
                            if(selectionLabel=="Message")Surface(shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.surface){OriginalAction(R.drawable.ref_app_walkie,"Walkie Talkie ${person.name}",enabled=person.numbers.isNotEmpty()&&walkie!=null){choose(person,true)}}
                        }
                    }
                }
                if(loading)item{Text("Loading Contacts…",Modifier.padding(16.dp))}
                else if(loadError!=null)item{Column(Modifier.padding(16.dp)){
                    Text(loadError!!,color=MaterialTheme.colorScheme.error)
                    TextButton(onClick={epoch++}){Text("Try Again")}
                }}
                else if(matching.isEmpty())item{Text(if(query.isBlank())"No Contacts to Display" else "No Matching Contacts",Modifier.padding(16.dp))}
            }
        }
    }
    choosing?.let{person->ReferenceDialog(person.name,{choosing=null},footer={DialogAction("Cancel"){choosing=null}}){
        person.numbers.forEach{n->SettingsChoice(displayPhone(n)){choosing=null;if(calling)walkie?.invoke(n)else pick(n)}}
    }}
    ErrorDialog(error){error=null}
}

/** Both provider queries must complete before publishing a contact snapshot. */
internal suspend fun loadPhoneContacts(context:android.content.Context):List<ContactRow> = withContext(Dispatchers.IO){
    val numbers=mutableMapOf<Long,MutableList<String>>()
    val phones=context.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,arrayOf("contact_id","data1"),null,null,null)
        ?: error("Phone numbers are unavailable")
    phones.use{c->
        var count=0
        while(c.moveToNext()&&count++<20000){
            currentCoroutineContext().ensureActive()
            val number=c.getString(1).orEmpty()
            if(number.isNotBlank())numbers.getOrPut(c.getLong(0)){mutableListOf()}.add(number)
        }
    }
    val result=mutableListOf<ContactRow>()
    val contacts=context.contentResolver.query(ContactsContract.Contacts.CONTENT_URI,arrayOf("_id","display_name","photo_thumb_uri","photo_uri"),null,null,"sort_key ASC")
        ?: error("Contacts are unavailable")
    contacts.use{c->
        while(c.moveToNext()&&result.size<5000){
            currentCoroutineContext().ensureActive()
            result+=ContactRow(c.getLong(0),c.getString(1).orEmpty().ifBlank{"Unnamed contact"},(c.getString(2)?:if(c.columnCount>3)c.getString(3)else null),numbers[c.getLong(0)].orEmpty().distinctBy{phoneInput(it)})
        }
    }
    result.sortedBy{it.name.lowercase(java.util.Locale.ROOT)}
}

internal class ContactsMemory:androidx.lifecycle.ViewModel(){val rows=mutableStateOf<List<ContactRow>>(emptyList())}
@Composable internal fun ContactAvatar(person:ContactRow,size:Dp=50.dp){
    ContactAvatar(person.numbers.firstOrNull().orEmpty(),person.name,Modifier.size(size),photoUri=person.photo,contactId=person.id)
}


internal fun contactNumberSummary(person:ContactRow):String = when(person.numbers.size){
    0 -> "No phone number"
    1 -> displayPhone(person.numbers.single())
    else -> "${person.numbers.size} phone numbers"
}

@Composable internal fun contactDirectorySearchColors() = OutlinedTextFieldDefaults.colors(
    unfocusedBorderColor=androidx.compose.ui.graphics.Color.Transparent,
    focusedBorderColor=MaterialTheme.colorScheme.primary,
    unfocusedContainerColor=MaterialTheme.colorScheme.surface
)

@Composable internal fun ContactDirectorySection(label:String) {
    val colors=MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
        Text(label,Modifier.semantics{heading()},fontSize=15.sp,color=colors.primary)
        HorizontalDivider(Modifier.weight(1f).padding(start=12.dp),color=colors.onSurface.copy(alpha=.2f))
    }
}
