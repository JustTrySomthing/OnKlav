package app.onklav.home

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID

internal class CompressModel(app:Application):AndroidViewModel(app) {
    var kind by mutableStateOf<CompressKind?>(null);private set
    var source by mutableStateOf<CompressSource?>(null);private set
    var options by mutableStateOf(CompressOptions());private set
    var busy by mutableStateOf(false);private set
    var progress by mutableFloatStateOf(-1f);private set
    var status by mutableStateOf("");private set
    var error by mutableStateOf(false);private set
    var estimate by mutableStateOf<Long?>(null);private set
    var estimating by mutableStateOf(false);private set
    var estimateError by mutableStateOf<String?>(null);private set
    var result by mutableStateOf<File?>(null);private set
    var saved by mutableStateOf<String?>(null);private set
    var savedUri by mutableStateOf<Uri?>(null);private set
    private val root=File(app.cacheDir,"compress-${UUID.randomUUID()}").apply{mkdirs()}
    private var staging:File?=null
    private var task:Job?=null
    private var preview:Job?=null
    private val imageWork=Mutex()
    private var generation=0
    val extension get()=options.extension(kind?:CompressKind.FILES)
    val mime get()=options.mime(kind?:CompressKind.FILES)
    val outputName get()=(source?.name?.substringBeforeLast('.')?:"Archive").take(90)+"-compressed.$extension"
    fun choose(value:CompressKind){if(busy)return;reset();kind=value}
    fun reset(){if(busy)return;generation++;preview?.cancel();preview=null;source=null;result?.delete();result=null;staging?.deleteRecursively();staging=null;options=CompressOptions();kind=null;estimate=null;estimateError=null;estimating=false;status="";error=false;saved=null;savedUri=null}
    fun cancel(){task?.cancel()}
    private fun run(label:String,block:suspend ()->Unit){if(busy)return;busy=true;error=false;status=label;progress=-1f
        task=viewModelScope.launch{try{block()}catch(e:CancellationException){status="Cancelled · originals unchanged";throw e}catch(e:Exception){status=e.message?.take(240)?:"Unable to finish. Try another file.";error=true}finally{busy=false;progress=-1f;task=null}}
    }
    fun open(uris:List<Uri>,folder:Boolean=false){val selected=kind?:return;if(uris.isEmpty())return
        generation++;preview?.cancel();estimating=false
        run("Preparing ${if(folder)"folder"else"files"}"){
            val stage=File(root,"input-${UUID.randomUUID()}").apply{mkdirs()};var accepted=false
            try{
                val prepared=withContext(Dispatchers.IO){
                    if(folder)compressFolder(getApplication(),uris.single(),stage)
                    else if(selected==CompressKind.FILES){require(uris.size<=20){"Choose up to 20 files."};val entries=mutableListOf<CompressEntry>();val names=mutableSetOf<String>();var bytes=0L
                        for((index,uri) in uris.withIndex()){val file=File(stage,"source-$index");val copied=CompressEngine.copy(getApplication(),uri,file,CompressEngine.MAX_BYTES-bytes);bytes+=copied.second;entries+=CompressEntry(CompressEngine.uniqueName(copied.first,names),file)}
                        CompressSource(if(entries.size==1)entries.first().name else "${entries.size} files",selected,entries,bytes,note="ZIP is lossless. Photos, video and existing archives may not shrink much.")
                    }else{
                        val file=File(stage,"source");val copied=CompressEngine.copy(getApplication(),uris.single(),file);CompressEngine.inspect(file,copied.first,selected)
                    }
                }
                staging?.deleteRecursively();staging=stage;result?.delete();result=null;saved=null;savedUri=null;source=prepared;options=CompressOptions();accepted=true;status="Adjust size, then compress";refreshEstimate()
            }finally{if(!accepted)stage.deleteRecursively()}
        }
    }
    fun update(value:CompressOptions){if(busy||result!=null)return;options=value;refreshEstimate()}
    private fun refreshEstimate(){val input=source?:return;generation++;val version=generation;preview?.cancel();estimate=null;estimateError=null;estimating=false
        if(input.kind!=CompressKind.IMAGE){estimate=CompressEngine.estimate(input,options);return}
        val settings=options;estimating=true
        preview=viewModelScope.launch{val file=File(root,"estimate-${UUID.randomUUID()}.${settings.extension(input.kind)}")
            try{delay(350);imageWork.withLock{CompressEngine.compress(getApplication(),input,settings,file){}};if(version==generation)estimate=file.length()}
            catch(e:CancellationException){throw e}catch(e:Exception){if(version==generation)estimateError="Size preview unavailable · try a smaller image size"}
            finally{file.delete();if(version==generation)estimating=false}
        }
    }
    fun compress(){val input=source?:return;val settings=options;generation++;preview?.cancel();estimating=false
        run("Compressing"){
            val out=File(root,"result-${UUID.randomUUID()}.${settings.extension(input.kind)}");var complete=false;var last=0L
            try{require(root.usableSpace>64_000_000){"Free at least 64 MB before compressing."}
                val perform:suspend ()->Unit={CompressEngine.compress(getApplication(),input,settings,out){p->val now=System.nanoTime();if(now-last>100_000_000){last=now;viewModelScope.launch{progress=p.coerceIn(0f,1f)}}}}
                if(input.kind==CompressKind.IMAGE)imageWork.withLock{perform()}else perform()
                require(out.length()>0){"No output was produced. Try another setting."};if(input.kind==CompressKind.FILES)require(out.length()<=FilesBroker.MAX_FILE){"ZIP is over 256 MB. Choose fewer files or a smaller folder."};result=out;complete=true;status="Ready to save"
            }finally{if(!complete)out.delete()}
        }
    }
    fun adjust(){if(busy)return;result?.delete();result=null;saved=null;savedUri=null;status="Adjust size, then compress";refreshEstimate()}
    fun save(uri:Uri){val output=result?:return;val name=outputName
        run("Saving copy"){writeFiledExport(getApplication(),uri){out->output.inputStream().use{input->val buf=ByteArray(131072);while(true){currentCoroutineContext().ensureActive();val n=input.read(buf);if(n<0)break;out.write(buf,0,n)}}};saved=name;savedUri=uri;status="Saved in your chosen folder"}
    }
    override fun onCleared(){generation++;preview?.cancel();task?.cancel();root.deleteRecursively();super.onCleared()}
}
