package app.onklav.home

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.onklav.shared.*
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun GamesPackageCenter(exit:()->Unit) {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    val removedIds = removedCatalogIds(games = true)
    var removeIncluded by remember { mutableStateOf<PlatformGame?>(null) }
    var rows by remember { mutableStateOf<List<GamePackages.Game>>(emptyList()) }
    var busy by remember { mutableStateOf(false) };var status by remember { mutableStateOf<String?>(null) }
    var failureReport by remember { mutableStateOf<GameFailureReport?>(null) }
    var candidate by remember { mutableStateOf<Pair<File,GamePackages.Game>?>(null) }
    var remove by remember { mutableStateOf<GamePackages.Game?>(null) }
    var save by remember { mutableStateOf<String?>(null) };var guide by remember { mutableStateOf(false) }
    suspend fun refresh(){rows=withContext(Dispatchers.IO){GamePackages.open(context).installed()}}
    LaunchedEffect(Unit){try{refresh()}catch(e:Exception){status=e.message?:"Could not read installed games"}}
    val latestCandidate by rememberUpdatedState(candidate)
    DisposableEffect(Unit){onDispose{latestCandidate?.first?.delete()}}
    val export=rememberLauncherForActivityResult(OnKlavFileContracts.CreateDocument("application/zip")){uri->
        val id=save;save=null
        if(uri!=null && id!=null)scope.launch{busy=true;try{withContext(Dispatchers.IO){val source=GamePackages.open(context).packageFile(id);context.contentResolver.openOutputStream(uri)?.use{out->source.inputStream().use{it.copyTo(out)}}?:error("Could not save package")};status="Game package saved. You can attach and share it from Files."}catch(e:Exception){status=e.message?:"Could not save game package"}finally{busy=false}}
    }
    val picker=rememberLauncherForActivityResult(OnKlavFileContracts.OpenDocument()){uri->
        if(uri!=null && !busy)scope.launch{
            busy=true;var staging:File?=null
            try{
                val result=withContext(Dispatchers.IO){
                    val file=File.createTempFile("game-",".onklav-g",context.cacheDir);staging=file
                    context.contentResolver.openInputStream(uri)?.use{input->file.outputStream().use{out->GamePackages.copyBounded(input,out,GamePackages.MAX_PACKAGE)}}?:error("Could not open package")
                    file to GamePackages.open(context).inspect(file)
                }
                candidate?.first?.delete();candidate=result;staging=null
            }catch(e:Exception){
                failureReport = GameFailureReport("Inspect package", e.message ?: "Could not check game package")
            }
            finally{staging?.delete();busy=false}
        }
    }
    BackHandler{if(!busy)exit()}
    Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background){Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)){
        PackageManagementHeader("Games", busy, exit)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Text("Included games",fontSize=20.sp)
            platformGames.forEach{g->OutlinedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp)){
                val removed = g.id in removedIds
                Text(g.title, fontSize = 18.sp)
                Text(if (removed) "Removed" else "Included game", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row {
                    if (removed) {
                        TextButton(enabled = !busy, onClick = {
                            runCatching { setHidden(context, true, g.id, false) }
                                .onFailure { status = it.message }
                        }) { Text("Restore") }
                    } else {
                        TextButton(enabled = !busy, onClick = { WebGameActivity.launch(context, g.id) }) { Text("Play") }
                        if (canHideCatalogEntry(games = true, id = g.id)) {
                            TextButton(enabled = !busy, onClick = { removeIncluded = g }) { Text("Remove") }
                        }
                    }
                }
            }}}
            Text("Your games",fontSize=20.sp)
            if(rows.isEmpty())Text("Install a game shared by its developer or another user.")
            rows.forEach{g->OutlinedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp)){
                Text(g.name,fontSize=18.sp);Text(g.manifest.getString("version")+" · Publisher "+g.publisher.take(16),color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(g.manifest.getString("description"))
                Row{TextButton(enabled=!busy,onClick={WebGameActivity.launch(context,g.id)}){Text("Play")};TextButton(enabled=!busy,onClick={save=g.id;export.launch(g.id+".onklav-g")}){Text("Save to share")};TextButton(enabled=!busy,onClick={remove=g}){Text("Remove")}}
            }}}
        }
        Column(Modifier.padding(12.dp)){
            ChoiceButton(if(busy)"Working…"else"Install a game",enabled=!busy,outlined=false,minimumHeight=48.dp){picker.launch(arrayOf("*/*"))}
            ChoiceButton("Developer guide",enabled=!busy,outlined=true,minimumHeight=48.dp){guide=true}
        }
    }}
    removeIncluded?.let { game ->
        RemoveIncludedDialog(game.title, games = true, dismiss = { removeIncluded = null }) {
            runCatching { setHidden(context, true, game.id, true) }
                .onFailure { status = it.message }
            removeIncluded = null
        }
    }
    candidate?.let{(file,game)->ReferenceDialog("Install ${game.name}?",{if(!busy){file.delete();candidate=null}},footer={
        DialogAction("Cancel",enabled=!busy){file.delete();candidate=null}
        DialogAction(if(busy)"Installing…"else"Install",enabled=!busy){busy=true;scope.launch{try{withContext(Dispatchers.IO){GamePackages.open(context).install(file)};refresh();file.delete();candidate=null;status="Game installed"}catch(e:Exception){failureReport=GameFailureReport("Install package",e.message?:"Could not install game",game.id)}finally{busy=false}}}
    }){Column(Modifier.padding(20.dp)){
        Text(game.manifest.getString("version")+"\n\n"+game.manifest.getString("description"));Text("\nPublisher fingerprint\n"+game.publisher)
        Text("\nYou choose which games to install. This game runs locally without access to your OnKlav files, accounts, camera, microphone or network. Updates must come from the same publisher.")
    }}}
    remove?.let{game->ReferenceDialog("Remove ${game.name}?",{if(!busy)remove=null},footer={DialogAction("Cancel",enabled=!busy){remove=null};DialogAction("Remove",enabled=!busy){busy=true;scope.launch{try{withContext(Dispatchers.IO){GamePackages.open(context).remove(game.id)};refresh();remove=null}catch(e:Exception){status=e.message?:"Could not remove game"}finally{busy=false}}}}){Text("The game will be removed. Its saved progress will be kept.",Modifier.padding(20.dp))}}
    if(guide)ReferenceDialog("Make and share games",{guide=false},large=true,footer={DialogAction("Close"){guide=false}}){Text("Use the OnKlav Game SDK and its 2D, 3D or turn-based template with Godot 4.7.2. Build a signed .onklav-g package, then share the file directly. Users install it here; no owner approval or developer account is needed.\n\nAll games use OnKlav’s shared engine. This first shared runtime supports offline play. Network multiplayer and native extensions are not available.\n\nThe SDK includes build, signing, verification, testing and performance guides.",Modifier.padding(20.dp))}
    failureReport?.let { report ->
        AlertDialog(
            onDismissRequest = { failureReport = null },
            title = { Text("Game package problem") },
            text = { Text(report.message) },
            dismissButton = { TextButton(onClick = { report.copyToClipboard(context) }) { Text("Copy report") } },
            confirmButton = { TextButton(onClick = { failureReport = null }) { Text("Close") } },
        )
    }
    ErrorDialog(status){status=null}
}
