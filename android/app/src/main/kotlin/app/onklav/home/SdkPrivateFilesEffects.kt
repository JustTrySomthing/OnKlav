package app.onklav.home

import app.onklav.shared.OnKlavFileContracts

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File

/** Native picker transfers stream in bounded chunks; guest code receives handles only. */
@Composable internal fun SdkPrivateFilesEffects(model:SdkAppModel){
    val c=LocalContext.current;val scope=rememberCoroutineScope();val pending=model.pending
    var importing by rememberSaveable{mutableStateOf<String?>(null)}
    var exporting by rememberSaveable{mutableStateOf<String?>(null)}
    fun current(token:String?)=model.pending?.takeIf{it.optString("token")==token}
    val importer=rememberLauncherForActivityResult(OnKlavFileContracts.OpenDocument()){uri->
        val p=current(importing);importing=null
        if(p!=null)scope.launch{
            try{
                if(uri==null){model.reply(p,failure="No file was selected");return@launch}
                require(uri.scheme=="content"){"Choose a file in OnKlav Files"}
                model.fileStatus="Importing private file…"
                val result=withContext(Dispatchers.IO){
                    model.authorizeNow(p)
                    val name=c.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{cursor->if(cursor.moveToFirst())cursor.getString(0)else null}?:"Imported file"
                    val safe=name.map{if(it.isISOControl()||it=='/'||it=='\\')'_'else it}.joinToString("").take(128).let{if(it.lastOrNull()?.isHighSurrogate()==true)it.dropLast(1)else it}.ifBlank{"Imported file"}
                    model.privateFileNow(p,"begin",JSONObject().put("name",safe).put("mime",app.onklav.shared.Mp3Codec.mime(c.contentResolver.getType(uri)?:"application/octet-stream",safe)))
                    c.contentResolver.openInputStream(uri)?.use{input->
                        val buffer=ByteArray(65536);var offset=0L;var emptyReads=0
                        while(true){currentCoroutineContext().ensureActive();model.authorizeNow(p);val n=input.read(buffer);if(n<0)break;if(n==0){check(++emptyReads<=3){"File provider returned no data"};continue};emptyReads=0
                            val digits="0123456789abcdef";val hex=CharArray(n*2)
                            for(i in 0 until n){val value=buffer[i].toInt()and 255;hex[i*2]=digits[value ushr 4];hex[i*2+1]=digits[value and 15]}
                            val saved=model.privateFileNow(p,"append",JSONObject().put("offset",offset).put("hex",String(hex)));offset=saved.getLong("size")
                        }
                    }?:error("Selected file could not be opened")
                    currentCoroutineContext().ensureActive();model.privateFileNow(p,"finish")
                }
                model.reply(p,result)
            }catch(e:CancellationException){withContext(NonCancellable+Dispatchers.IO){runCatching{model.privateFileNow(p,"abort")}};throw e}
            catch(e:Exception){withContext(Dispatchers.IO){runCatching{model.privateFileNow(p,"abort")}};model.reply(p,failure=e.message?:"Private file import failed")}
            finally{model.fileStatus=null}
        }
    }
    val exporter=rememberLauncherForActivityResult(FileExportContract()){uri->
        val p=current(exporting);exporting=null
        if(p!=null)scope.launch{
            try{
                if(uri==null){model.reply(p,failure="No destination was selected");return@launch}
                require(uri.scheme=="content");model.fileStatus="Exporting private file…"
                withContext(Dispatchers.IO){
                    val file=model.privateFileNow(p,"resolve");val expected=file.getLong("size");var copied=0L
                    File(file.getString("path")).inputStream().use{input->writeFiledExport(c,uri){output->
                        val buffer=ByteArray(65536)
                        while(true){currentCoroutineContext().ensureActive();model.authorizeNow(p);val n=input.read(buffer);if(n<0)break;if(n==0)continue;copied+=n;check(copied<=expected){"Source size changed"};output.write(buffer,0,n)}
                        check(copied==expected){"Source file was incomplete"}
                    }}
                }
                model.reply(p,JSONObject().put("exported",true))
            }catch(e:CancellationException){throw e}catch(e:Exception){model.reply(p,failure="Copy did not complete; check the destination before retrying. ${e.message}")}
            finally{model.fileStatus=null}
        }
    }
    LaunchedEffect(pending?.optString("token")){
        val p=pending?:return@LaunchedEffect
        if(p.optString("service")!in setOf("blobs.import","blobs.export")||!model.launchPending(p.getString("token")))return@LaunchedEffect
        try{
            model.authorize(p)
            if(p.getString("service")=="blobs.import"){importing=p.getString("token");importer.launch(arrayOf("*/*"))}
            else{val file=withContext(Dispatchers.IO){model.privateFileNow(p,"resolve")};exporting=p.getString("token");exporter.launch(FileExportRequest(file.getString("name"),file.getString("mime")))}
        }catch(e:CancellationException){throw e}catch(e:Exception){model.reply(p,failure=e.message?:"Private file action could not be opened")}
    }
}
