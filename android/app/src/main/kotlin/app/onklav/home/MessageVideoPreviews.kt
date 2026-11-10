package app.onklav.home

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.onklav.shared.ChoiceButton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal data class MessageVideo(val id:String,val start:Int=0)

/** Extract identity only. Message URLs are never used as image fetch targets. */
internal object MessageVideoLinks {
    private val links=Regex("""(?i)(?<![a-z0-9_@./-])(?:https?://|(?:(?:www\.|m\.|music\.)?(?:youtube\.com|youtu\.be)/))[^\s<>"']+""")
    private val allowed=setOf("youtube.com","www.youtube.com","m.youtube.com","music.youtube.com","youtu.be","youtube-nocookie.com","www.youtube-nocookie.com")
    fun parse(raw:String):MessageVideo? {
        val text=raw.trimEnd('.',',','!','?',')',']','}')
        if(text.length>2048 || text.any{it.isISOControl()||it=='\\'})return null
        val url=(if(text.contains("://"))text else "https://$text").toHttpUrlOrNull() ?: return null
        if(url.host !in allowed || url.username.isNotEmpty() || url.password.isNotEmpty() ||
            url.port != if(url.scheme=="http")80 else 443)return null
        val parts=url.pathSegments
        val id=when {
            url.host=="youtu.be" && parts.size==1->parts[0]
            url.encodedPath=="/watch" && url.host !in setOf("youtube-nocookie.com","www.youtube-nocookie.com")->url.queryParameter("v")
            parts.size==2 && parts[0] in setOf("shorts","embed","live")->parts[1]
            else->null
        } ?: return null
        if(!id.matches(Regex("[A-Za-z0-9_-]{11}")))return null
        val time=url.queryParameter("t") ?: url.queryParameter("start") ?: url.fragment?.takeIf{it.startsWith("t=")}?.drop(2).orEmpty()
        return MessageVideo(id,seconds(time))
    }
    fun seconds(raw:String):Int {
        raw.takeIf{it.matches(Regex("[0-9]{1,6}"))}?.toIntOrNull()?.let{return it.coerceAtMost(86400)}
        val m=Regex("(?:(\\d{1,3})h)?(?:(\\d{1,3})m)?(?:(\\d{1,3})s)?").matchEntire(raw) ?: return 0
        return (m.groupValues[1].toIntOrNull().orZero()*3600+m.groupValues[2].toIntOrNull().orZero()*60+m.groupValues[3].toIntOrNull().orZero()).coerceAtMost(86400)
    }
    private fun Int?.orZero()=this ?: 0
    /** Hide only links represented by a playable card; retain other links and message text. */
    fun displayText(text:String):String {
        val shown=find(text).map{it.id}.toSet()
        return links.replace(text){match->
            if(parse(match.value)?.id in shown){val clean=match.value.trimEnd('.',',','!','?',')',']','}');match.value.substring(clean.length)}else match.value
        }.trim()
    }
    fun find(text:String):List<MessageVideo> = links.findAll(text.take(10000)).mapNotNull{parse(it.value)}.distinctBy{it.id}.take(4).toList()
}

/** Memory-only thumbnails. Privacy revocation invalidates in-flight results. */
internal object MessageVideoPreviews {
    val epoch=MutableStateFlow(0L)
    private val gate=Any()
    private val work=Semaphore(2)
    private val calls=ConcurrentHashMap<Call,Boolean>()
    private val memory=object:LruCache<String,Bitmap>(2*1024*1024){override fun sizeOf(key:String,value:Bitmap)=value.byteCount}
    private val client=OkHttpClient.Builder().connectTimeout(4,TimeUnit.SECONDS).readTimeout(4,TimeUnit.SECONDS)
        .callTimeout(8,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false)
        .dns(object:Dns{override fun lookup(hostname:String)=SdkFeedService.publicAddresses(hostname)}).build()
    fun allowed(c:Context)=c.getSharedPreferences("privacy",0).getBoolean("youtube",true)
    fun setAllowed(c:Context,value:Boolean)=synchronized(gate){
        check(c.getSharedPreferences("privacy",0).edit().putBoolean("youtube",value).commit()){ "Could not save Privacy settings" }
        epoch.value++;calls.keys.forEach{it.cancel()};calls.clear();memory.evictAll()
    }
    fun trimMemory(){synchronized(gate){memory.evictAll()}}
    fun cancelLoads(){calls.keys.forEach{it.cancel()}}
    private fun fetch(id:String,check:()->Unit):ByteArray {
        val call=client.newCall(Request.Builder().url(SdkThumbnails.url(id)).cacheControl(CacheControl.FORCE_NETWORK).build())
        synchronized(gate){check();calls[call]=true}
        try{return call.execute().use {response->
            check();require(response.code==200){"Thumbnail unavailable"}
            val body=response.body ?: error("Thumbnail unavailable")
            require(body.contentLength()<=98304){"Thumbnail too large"}
            SdkThumbnails.read(body.byteStream(),check)
        }}finally{calls.remove(call);call.cancel()}
    }
    suspend fun load(c:Context,id:String,visible:()->Boolean={true},download:(String,()->Unit)->ByteArray=::fetch):Bitmap?=withContext(Dispatchers.IO){
        SdkThumbnails.url(id)
        val generation=epoch.value
        fun checkCurrent(){check(allowed(c)&&epoch.value==generation&&visible()){ "Preview permission changed" }}
        try {work.withPermit{
            ensureActive()
            synchronized(gate){checkCurrent();memory.get(id)}?.let{return@withPermit it}
            val bytes=download(id){ensureActive();synchronized(gate){checkCurrent()}}
            ensureActive();synchronized(gate){checkCurrent()}
            val image=SdkThumbnails.decode(bytes) ?: return@withPermit null
            synchronized(gate){checkCurrent();memory.put(id,image)};image
        }}catch(e:CancellationException){throw e}catch(_:Exception){null}
    }
}

@Composable
internal fun MessageVideoCards(text:String,bubbleColor:androidx.compose.ui.graphics.Color?=null,loader:suspend(Context,MessageVideo,()->Boolean)->Bitmap?={c,v,visible->MessageVideoPreviews.load(c,v.id,visible)},play:(MessageVideo)->Unit) {
    val videos=remember(text){MessageVideoLinks.find(text)}
    val c=LocalContext.current
    val revision by MessageVideoPreviews.epoch.collectAsState()
    val owner=LocalLifecycleOwner.current
    val visible=remember(owner){AtomicBoolean(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))}
    var foreground by remember(owner){mutableStateOf(visible.get())}
    DisposableEffect(owner){
        val observer=LifecycleEventObserver{_,event->
            if(event==Lifecycle.Event.ON_START){visible.set(true);foreground=true}
            if(event==Lifecycle.Event.ON_STOP){visible.set(false);foreground=false;MessageVideoPreviews.cancelLoads()}
        }
        owner.lifecycle.addObserver(observer)
        onDispose{visible.set(false);owner.lifecycle.removeObserver(observer)}
    }
    val enabled=revision.let{MessageVideoPreviews.allowed(c)}
    videos.forEach{video->key(video.id){
        var image by remember(video.id,revision){mutableStateOf<Bitmap?>(null)}
        LaunchedEffect(video.id,revision,foreground){
            image=null
            if(enabled&&foreground)image=loader(c,video,visible::get)
        }
        Column(Modifier.fillMaxWidth().padding(top=8.dp).clip(RoundedCornerShape(12.dp)).testTag("message-video-${video.id}")){
            Box(Modifier.fillMaxWidth().height(180.dp).clickable{play(video)}.testTag("video-thumbnail-${video.id}"),contentAlignment=androidx.compose.ui.Alignment.Center){
                image?.let{bitmap->Image(bitmap.asImageBitmap(),"YouTube video thumbnail",Modifier.fillMaxSize(),contentScale=ContentScale.Fit)}
                    ?: Icon(androidx.compose.ui.res.painterResource(app.onklav.shared.R.drawable.ref_radio_play),"Play Video",Modifier.size(56.dp),tint=MaterialTheme.colorScheme.primary)
            }
            MessageMediaAction("Play Video",bubbleColor?:MaterialTheme.colorScheme.surface){play(video)}
        }
    }}
}
