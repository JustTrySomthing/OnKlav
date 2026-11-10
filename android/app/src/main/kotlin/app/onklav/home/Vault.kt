package app.onklav.home

import android.app.Application
import android.text.Spanned
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import app.onklav.shared.R
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.onklav.shared.OnKlavFileContracts
import kotlinx.coroutines.*

internal class VaultModel(app:Application):AndroidViewModel(app){
 private val store=VaultStore(app)
 private val initialRecovery=runCatching{store.recoveryQuestions()}
 var exists by mutableStateOf(store.exists()&&(initialRecovery.isFailure||initialRecovery.getOrNull()!=null));private set
 var locked by mutableStateOf(true);private set
 var busy by mutableStateOf(false);private set
 var document by mutableStateOf<WriteDocument?>(null);private set
 var revision by mutableIntStateOf(0);private set
 var status by mutableStateOf(if(store.exists()&&!exists)"Setup was not completed. Create a new password."else"");private set
 var error by mutableStateOf<String?>(null);private set
 var recoveryChoices by mutableStateOf(initialRecovery.getOrNull());private set
 var showQuestions by mutableStateOf(false);private set
 private var draftPassword:CharArray?=null
 private var failures=0
 private var blockedUntil=0L
 private fun mayAttempt():Boolean{val wait=blockedUntil-android.os.SystemClock.elapsedRealtime();if(wait<=0)return true;error="Please wait ${(wait+999)/1000} seconds before trying again.";return false}
 private fun failedAttempt(){failures++;if(failures>=5)blockedUntil=android.os.SystemClock.elapsedRealtime()+30000}
 private var pending:WriteDocument?=null
 private var autosave:Job?=null
 private var generation=0
 var pendingImage by mutableStateOf<android.net.Uri?>(null)
 fun open(password:CharArray,create:Boolean){
  if(busy||!mayAttempt()){password.fill('\u0000');return}
  if(create||!exists){draftPassword?.fill('\u0000');draftPassword=password.copyOf();password.fill('\u0000');showQuestions=true;error=null;return}
  busy=true;error=null;val token=++generation
  viewModelScope.launch{try{
   val saved=withContext(Dispatchers.IO){try{store.unlock(password)}finally{password.fill('\u0000')}}
   exists=true
   if(token!=generation){store.close();return@launch}
   failures=0;recoveryChoices=store.recoveryQuestions()
   check(recoveryChoices!=null){"Setup is incomplete"}
   document=pending?:saved;pending=null;locked=false;revision++;status="Saved"
   if(document!=saved)save()
  }catch(e:Exception){if(e is CancellationException){store.close();throw e};store.close();failedAttempt();error=if(create)e.message?:"Could not create Vault"else"Unable to unlock. Check your password. If correct, Vault may be damaged; restore your backup on a fresh installation."}finally{busy=false}}
 }
 fun completeSetup(selected:List<Int>,answers:List<CharArray>){
  if(busy||!showQuestions){answers.forEach{it.fill('\u0000')};return};busy=true;error=null;val token=++generation;val password=draftPassword?.copyOf()
  viewModelScope.launch{try{
   withContext(Dispatchers.IO){store.createWithRecovery(checkNotNull(password),selected,answers)}
   exists=true;recoveryChoices=selected.toList()
   if(token==generation){draftPassword?.fill('\u0000');draftPassword=null;showQuestions=false;locked=true;document=null;status="Setup complete. Enter your password."}
  }catch(e:Exception){if(e is CancellationException)throw e;if(token==generation)error="Setup was not completed. Please try again."}finally{password?.fill('\u0000');answers.forEach{it.fill('\u0000')};if(token!=generation)store.close();busy=false}}
 }
 fun resetPassword(answers:List<CharArray>,replacement:CharArray){
  if(busy||!locked||showQuestions||recoveryChoices==null||!mayAttempt()){answers.forEach{it.fill('\u0000')};replacement.fill('\u0000');return};busy=true;error=null;val token=++generation
  viewModelScope.launch{try{withContext(Dispatchers.IO){store.resetPassword(answers,replacement)};failures=0;if(token==generation){status="Password reset. Enter your new password.";revision++}}
   catch(e:Exception){if(e is CancellationException)throw e;failedAttempt();if(token==generation)error="Unable to reset. Check all three answers. If correct, restore a backup."}
   finally{answers.forEach{it.fill('\u0000')};replacement.fill('\u0000');store.close();busy=false}}
 }
 fun clearError(){error=null}
 fun edit(s:Spanned){val old=document?:return;val next=WriteDocument.capture(old.id,"Vault",s)
  if(next.json().toString().toByteArray().size>VaultStore.MAX_DOCUMENT){error="Vault is full (8 MB). Remove an image or shorten the text.";revision++;return}
  document=next;status="Unsaved changes";autosave?.cancel();autosave=viewModelScope.launch{
   delay(700);val snapshot=document?:return@launch
   try{withContext(Dispatchers.IO){store.save(snapshot)};if(!locked&&document==snapshot){status="Saved";error=null}}
   catch(e:Exception){if(e is CancellationException)throw e;if(!locked){status="Not saved · retry Save";error=e.message?:"Could not save Vault"}}
  }
 }
 fun save(){if(locked)return;autosave?.cancel();autosave=null;val doc=document?:return
  // Atomic saves are serialized by the store; the final exit flush always wins.
  try{store.save(doc);status="Saved";error=null}catch(e:Exception){status="Not saved · retry Save";error=e.message?:"Could not save Vault"}
 }
 fun lock():Boolean{
  generation++;draftPassword?.fill('\u0000');draftPassword=null;showQuestions=false;autosave?.cancel();autosave=null
  val doc=document
  if(!locked&&doc!=null){save();if(status!="Saved"){pending=doc;locked=true;document=null;store.close();return false}}
  locked=true;document=null;store.close();return pending==null
 }
 fun changePassword(old:CharArray,next:CharArray){if(locked){old.fill('\u0000');next.fill('\u0000');return}
  try{store.changePassword(old,next,checkNotNull(document));status="Saved";error=null}catch(e:Exception){error="Password was not changed. Check your current password and try again."}finally{old.fill('\u0000');next.fill('\u0000')}
 }
 fun insertImage(encoded:String,a:Int,b:Int){val doc=document?:return
  if(doc.marks.count{it.kind=="image"}>=12){error="Use up to 12 images.";return}
  val text=doc.spanned();val start=a.coerceIn(0,text.length);val end=b.coerceIn(start,text.length)
  text.replace(start,end,"\uFFFC");text.setSpan(WriteImageSpan(encoded),start,start+1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);edit(text);revision++
 }
 override fun onCleared(){lock();super.onCleared()}
}

@Composable internal fun VaultWorkspace(exit:()->Unit, model:VaultModel=viewModel(key="private-vault")){
 val context=LocalContext.current
 val lifecycle=LocalLifecycleOwner.current.lifecycle
 val scope=rememberCoroutineScope()
 var editor by remember{mutableStateOf<WriteNativeEditor?>(null)}
 var selectionTick by remember{mutableIntStateOf(0)}
 var password by remember{mutableStateOf("")};var confirm by remember{mutableStateOf("")};var visible by remember{mutableStateOf(false)}
 var linkDialog by remember{mutableStateOf(false)};var link by remember{mutableStateOf("")};var linkError by remember{mutableStateOf("")}
 var changePassword by remember{mutableStateOf(false)};var newPassword by remember{mutableStateOf("")}
 var resetPassword by remember{mutableStateOf(false)}
 var answers by remember{mutableStateOf(List(3){""})};var questions by remember{mutableStateOf(List(3){0})}
 var answerVisible by remember{mutableStateOf(List(3){false})}
 var resetStarted by remember{mutableStateOf(false)}
 LaunchedEffect(model.busy,model.revision){if(resetStarted&&!model.busy&&model.error==null){resetPassword=false;resetStarted=false;answers=List(3){""};newPassword="";confirm="";password=""}}
 var range by remember{mutableStateOf(0 to 0)};var imageBusy by remember{mutableStateOf(false)}
 fun clearEditor(){editor?.let{it.guarded=true;it.setText("");it.changed=null};editor=null;password="";confirm="";newPassword="";visible=false;link="";linkDialog=false;changePassword=false;answers=List(3){""};answerVisible=List(3){false};resetPassword=false;resetStarted=false}
 fun leave(){val saved=model.lock();clearEditor();if(saved)exit()}
 BackHandler{leave()}
 DisposableEffect(lifecycle){
  val activity=context as? android.app.Activity
  val secured=activity?.window?.attributes?.flags?.and(WindowManager.LayoutParams.FLAG_SECURE)!=0
  activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
  val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_STOP){model.lock();clearEditor()}}
  lifecycle.addObserver(observer)
  onDispose{model.lock();clearEditor();lifecycle.removeObserver(observer);if(!secured)activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)}
 }
 LaunchedEffect(model.showQuestions){if(!model.showQuestions){answers=List(3){""};answerVisible=List(3){false}}}
 val picker=rememberLauncherForActivityResult(OnKlavFileContracts.OpenDocument()){uri->if(uri!=null)model.pendingImage=uri}
 LaunchedEffect(model.locked,model.pendingImage){
  val uri=model.pendingImage
  if(!model.locked&&uri!=null){model.pendingImage=null;imageBusy=true
   try{val encoded=withContext(Dispatchers.IO){WriteImageSpan.read(context,uri)};if(!model.locked)model.insertImage(encoded,range.first,range.second).also{linkError="Original images stay in Files."}else model.pendingImage=uri}catch(e:Exception){if(e is CancellationException)throw e;linkError=e.message?:"Could not insert image"}finally{imageBusy=false}}
 }
 Surface(Modifier.fillMaxSize()){
 Column(Modifier.fillMaxSize().imePadding()){
  HomeHeader("Vault",back={leave()})
  if(model.locked){
   Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
    Icon(Icons.Outlined.Lock,null,Modifier.size(48.dp),tint=MaterialTheme.colorScheme.primary)
    val setup=model.showQuestions
    val recovery=resetPassword&&model.recoveryChoices!=null
    Text(when{setup->"Secure your recovery";recovery->"Reset password";model.exists->"Unlock Vault";else->"Your private space"},style=MaterialTheme.typography.headlineSmall)
    when{
     setup||recovery->{
      Text(if(setup)"Step 2 of 2 · Answer all three questions" else "Answer all three questions to choose a new password.")
      if(setup)Text("Choose private, hard-to-guess answers. Anyone who knows all three can reset your password.",style=MaterialTheme.typography.bodySmall)
      repeat(3){i->VaultQuestion(i,if(recovery)model.recoveryChoices!![i]else questions[i],answers[i],answerVisible[i],!model.busy,setup,
       {q->questions=questions.toMutableList().also{it[i]=q};answers=answers.toMutableList().also{it[i]=""}},
       {value->answers=answers.toMutableList().also{it[i]=value}},
       {answerVisible=answerVisible.toMutableList().also{it[i]=!it[i]}})}
      Text("Answers ignore capitalization and extra spaces.",style=MaterialTheme.typography.bodySmall)
      if(recovery){VaultPassword(newPassword,{newPassword=it},"New password",visible,{visible=!visible},!model.busy);VaultPassword(confirm,{confirm=it},"Confirm new password",visible,{visible=!visible},!model.busy)
       if(confirm.isNotEmpty()&&confirm!=newPassword)Text("Passwords do not match",color=MaterialTheme.colorScheme.error)}
      model.error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
      if(model.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
      Button(onClick={val copy=answers.map{it.toCharArray()};if(setup)model.completeSetup(questions,copy)else{resetStarted=true;model.resetPassword(copy,newPassword.toCharArray())}},
       enabled=!model.busy&&answers.all(VaultRecovery::validAnswer)&&(!recovery||(newPassword.length in 8..256&&newPassword==confirm)),modifier=Modifier.fillMaxWidth()){Text(if(setup)"Complete"else"Reset password")}
     }
     else->{
      Text(when{!model.exists->"Vault is a password protected and encrypted place to store information.";else->"Enter your password to open Vault."})
      if(!model.exists)Text("Step 1 of 2 · Create your password",style=MaterialTheme.typography.labelLarge)
      VaultPassword(password,{password=it},"Password",visible,{visible=!visible},!model.busy)
      if(!model.exists){VaultPassword(confirm,{confirm=it},"Confirm password",visible,{visible=!visible},!model.busy)
       Text("Use at least 8 characters. Store a safe copy of your password and recovery answers.",style=MaterialTheme.typography.bodySmall)
       if(confirm.isNotEmpty()&&password!=confirm)Text("Passwords do not match",color=MaterialTheme.colorScheme.error)}
      model.error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
      if(model.status.startsWith("Setup complete")||model.status.startsWith("Password reset"))Text(model.status,color=MaterialTheme.colorScheme.primary)
      if(model.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
      Button(onClick={val p=password.toCharArray();password="";confirm="";visible=false;answers=List(3){""};model.open(p,!model.exists)},enabled=!model.busy&&password.length in 8..256&&(model.exists||password==confirm),modifier=Modifier.fillMaxWidth()){Text(if(!model.exists||model.recoveryChoices==null)"Continue"else"Unlock")}
      if(model.recoveryChoices!=null)TextButton(onClick={resetPassword=true;answers=List(3){""};password="";confirm="";newPassword="";visible=false;model.clearError()},enabled=!model.busy,modifier=Modifier.fillMaxWidth()){Text("Reset password")}
     }
    }
    TextButton(onClick={if(recovery){resetPassword=false;resetStarted=false;answers=List(3){""};newPassword="";confirm="";model.clearError()}else{model.lock();clearEditor();model.pendingImage=null;exit()}},enabled=!model.busy,modifier=Modifier.fillMaxWidth()){Text("Cancel")}

   }
  }else{
   selectionTick
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){
    val tools=listOf("bold" to "Bold","italic" to "Italic","underline" to "Underline","strike" to "Strikethrough")
    tools.forEach{(kind,label)->
     val selected=editor?.let{WriteFormatting.active(it.editableText,it.selectionStart,it.selectionEnd,kind)}?:false
     IconButton(modifier=Modifier.semantics{contentDescription=label;this.selected=selected}.background(if(selected)MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,androidx.compose.foundation.shape.RoundedCornerShape(10.dp)),onClick={editor?.let{e->val active=WriteFormatting.active(e.editableText,e.selectionStart,e.selectionEnd,kind);e.format(kind,if(active)null else "1")}}){
      Text(when(kind){"bold"->"B";"italic"->"I";"underline"->"U";else->"S"},fontSize=20.sp,fontWeight=if(kind=="bold")FontWeight.Bold else FontWeight.Medium,fontStyle=if(kind=="italic")FontStyle.Italic else FontStyle.Normal,textDecoration=when(kind){"underline"->TextDecoration.Underline;"strike"->TextDecoration.LineThrough;else->null})
     }
    }
    IconButton(onClick={editor?.let{range=it.selectionStart to it.selectionEnd;it.beginFormatting()};link="";linkError="";linkDialog=true}){Icon(Icons.Outlined.Link,"Insert link")}
    IconButton(onClick={editor?.let{range=it.selectionStart to it.selectionEnd};picker.launch(arrayOf("image/*"))},enabled=!imageBusy){Icon(painterResource(R.drawable.ref_app_photo),"Insert image")}
   }
   val doc=model.document
   if(doc!=null)AndroidView(factory={c->WriteNativeEditor(c,"host.vault").also{e->
    editor=e;e.selected={_,_->selectionTick++};e.contentDescription="Vault text";e.hint="Type or paste private information…"
    e.imeOptions=e.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
    e.inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
    if(android.os.Build.VERSION.SDK_INT>=30)e.importantForContentCapture=android.view.View.IMPORTANT_FOR_CONTENT_CAPTURE_NO_EXCLUDE_DESCENDANTS
    e.importantForAutofill=android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
    e.load(doc,model.revision.toLong(),0,0);e.changed={s,_->model.edit(s)}
   }},update={e->editor=e;if(e.revision!=model.revision.toLong())e.load(doc,model.revision.toLong(),range.first,range.second);e.changed={s,_->model.edit(s)}},modifier=Modifier.weight(1f).fillMaxWidth().background(Color.White))
   model.error?.let{Text(it,Modifier.padding(horizontal=16.dp),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
   if(linkError.isNotEmpty()&&!linkDialog)Text(linkError,Modifier.padding(horizontal=16.dp),color=if(linkError=="Original images stay in Files.")MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
   Text(model.status,Modifier.padding(horizontal=20.dp),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
   Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),horizontalArrangement=Arrangement.SpaceBetween){
    TextButton(onClick={model.save();if(model.status=="Saved")android.widget.Toast.makeText(context,"Vault saved",android.widget.Toast.LENGTH_SHORT).show()}){Icon(painterResource(R.drawable.photo_save),null);Spacer(Modifier.width(8.dp));Text("Save")}
    IconButton(onClick={changePassword=true;password="";newPassword="";confirm="";visible=false}){Icon(painterResource(R.drawable.ref_action_keypad),"Change password")}
    TextButton(onClick={leave()}){Icon(Icons.Outlined.Lock,null);Spacer(Modifier.width(6.dp));Text("Lock & close")}
   }
  }
 }
 }
 if(linkDialog&&!model.locked)AlertDialog(onDismissRequest={linkDialog=false},title={Text("Insert link")},text={Column{OutlinedTextField(link,{link=it;linkError=""},label={Text("https://…")},singleLine=true);if(linkError.isNotEmpty())Text(linkError,color=MaterialTheme.colorScheme.error)}},confirmButton={TextButton(onClick={try{val url=safeLink(link);editor?.let{e->e.setSelection(range.first.coerceIn(0,e.length()),range.second.coerceIn(0,e.length()));if(e.selectionStart==e.selectionEnd){val start=e.selectionStart;e.editableText.insert(start,url);e.setSelection(start,start+url.length)};e.format("link",url)};linkDialog=false}catch(e:Exception){linkError="Enter a valid web or email link."}}){Text("Insert")}},dismissButton={TextButton(onClick={linkDialog=false}){Text("Cancel")}})
 if(changePassword&&!model.locked)AlertDialog(onDismissRequest={changePassword=false;password="";newPassword="";confirm=""},title={Text("Change Vault password")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
  VaultPassword(password,{password=it},"Current password",visible,{visible=!visible},true)
  VaultPassword(newPassword,{newPassword=it},"New password",visible,{visible=!visible},true)
  VaultPassword(confirm,{confirm=it},"Confirm new password",visible,{visible=!visible},true)
  Text("Keep a safe copy. Older backups still require their original password.",style=MaterialTheme.typography.bodySmall)
 }},confirmButton={TextButton(enabled=password.length>=8&&newPassword.length>=8&&newPassword==confirm,onClick={model.changePassword(password.toCharArray(),newPassword.toCharArray());password="";newPassword="";confirm="";changePassword=false}){Text("Save password")}},dismissButton={TextButton(onClick={changePassword=false;password="";newPassword="";confirm=""}){Text("Cancel")}})
}

@Composable private fun VaultPassword(value:String,change:(String)->Unit,label:String,visible:Boolean,toggle:()->Unit,enabled:Boolean){
 OutlinedTextField(value,{change(it.take(256))},label={Text(label)},singleLine=true,enabled=enabled,modifier=Modifier.fillMaxWidth(),
  keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Password),
  visualTransformation=if(visible)VisualTransformation.None else PasswordVisualTransformation(),trailingIcon={IconButton(onClick=toggle){Icon(if(visible)Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,if(visible)"Hide password"else"Show password")}})
}

@Composable private fun VaultQuestion(index:Int,selected:Int,answer:String,visible:Boolean,enabled:Boolean,choose:Boolean,onChoose:(Int)->Unit,onAnswer:(String)->Unit,onVisible:()->Unit){
 var expanded by remember{mutableStateOf(false)}
 Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surfaceContainer){Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
  Text("Question ${index+1} of 3",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
  if(choose)Box{TextButton(onClick={expanded=true},enabled=enabled){Text(VaultRecovery.questions[index][selected],Modifier.weight(1f));Icon(Icons.Outlined.ExpandMore,"Choose question ${index+1}")}
   DropdownMenu(expanded,onDismissRequest={expanded=false}){VaultRecovery.questions[index].forEachIndexed{i,q->DropdownMenuItem(text={Text(q)},onClick={onChoose(i);expanded=false})}}}
  else Text(VaultRecovery.questions[index][selected],style=MaterialTheme.typography.titleSmall)
  VaultPassword(answer,onAnswer,"Answer ${index+1}",visible,onVisible,enabled)
 }}
}
