package app.onklav.home

import android.util.Xml
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import java.io.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resumeWithException

internal data class CloudEntry(val url:HttpUrl,val name:String,val folder:Boolean,val size:Long?=null,val modified:String="",val etag:String="")
internal data class CloudListing(val entries:List<CloudEntry>,val available:Long?=null,val used:Long?=null)
/** Credentials stay on a single HTTPS origin/root. No redirects, transparent write retries or cookies. */
internal class CloudDav(private val account:CloudAccount,private val exchange:(suspend(Request)->Response)?=null){
 val root=CloudPaths.base(account.endpoint);private val active=AtomicReference<Call?>()
 companion object{
  const val MAX_TRANSFER=2L*1024*1024*1024
  private val http=OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).connectTimeout(20,TimeUnit.SECONDS).readTimeout(45,TimeUnit.SECONDS).writeTimeout(45,TimeUnit.SECONDS).callTimeout(15,TimeUnit.MINUTES).build()
  private val props="""<?xml version="1.0" encoding="utf-8"?><d:propfind xmlns:d="DAV:"><d:prop><d:displayname/><d:resourcetype/><d:getcontentlength/><d:getlastmodified/><d:getetag/><d:quota-available-bytes/><d:quota-used-bytes/></d:prop></d:propfind>"""
  fun status(code:Int):String=when(code){401->"Sign-in was not accepted. Check your credentials or app password; your provider may require login approval.";403->"Your provider denied access. Check account permissions and free-plan file restrictions.";404->"This folder or file is no longer available. Refresh and try again.";405,501->"This server does not support the required storage operation.";409->"The destination folder is unavailable. Refresh and try again.";412->"A file already exists with this name, or the remote file changed. Refresh or choose a new name.";411->"This provider requires a known file size. Save a local copy in Files, then upload it.";413->"The provider rejected this file’s size.";423->"This file is locked by the provider. Try again later.";429->"The provider is limiting requests. Please try again later.";507->"Your cloud storage is full.";in 300..399->"The server redirected this request. Check the storage address; credentials were not forwarded.";else->"The cloud request could not finish (HTTP $code)."}
 }
 fun cancel(){active.getAndSet(null)?.cancel()}
 private suspend fun request(url:HttpUrl,method:String,body:RequestBody?=null,headers:Map<String,String> = emptyMap()):Response{
  require(CloudPaths.safe(root,url)){"The server returned a destination outside this account."}
  val r=Request.Builder().url(url).header("Authorization",Credentials.basic(account.user,account.password,Charsets.UTF_8)).header("Accept-Encoding","identity").method(method,body).apply{headers.forEach{(k,v)->header(k,v)}}.build()
  exchange?.let{return it(r)}
  return suspendCancellableCoroutine{cont->val call=http.newCall(r);active.set(call);cont.invokeOnCancellation{call.cancel()};call.enqueue(object:Callback{override fun onFailure(call:Call,e:IOException){if(cont.isActive)cont.resumeWithException(IOException("Could not reach cloud storage. Check your connection and retry."))};override fun onResponse(call:Call,response:Response){cont.resume(response){_,value,_->value.close()}}})}
 }
 suspend fun list(folder:HttpUrl=root):CloudListing=withContext(Dispatchers.IO){require(folder.encodedPath.endsWith('/'));request(folder,"PROPFIND",props.toRequestBody("application/xml; charset=utf-8".toMediaType()),mapOf("Depth" to "1")).use{r->check(r.code==207){status(r.code)};val xml=r.body?.byteStream()?.use{it.readBytesBounded(4*1024*1024)}?:error("The server returned no folder listing.");parse(folder,xml)}}
 internal fun parse(parent:HttpUrl,bytes:ByteArray):CloudListing{
  require(bytes.size<=4*1024*1024);val text=String(bytes,Charsets.UTF_8);require(!text.contains("<!DOCTYPE",true)&&!text.contains("<!ENTITY",true)){"Unsafe folder response."}
  val p=Xml.newPullParser();p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES,true);p.setInput(java.io.StringReader(text));p.nextTag();require(p.name=="multistatus"&&p.namespace=="DAV:"){"The server did not return a cloud folder listing."};val entries=mutableListOf<CloudEntry>();var available:Long?=null;var used:Long?=null;var responses=0;var tokens=0
  while(p.eventType!=XmlPullParser.END_DOCUMENT){require(++tokens<200000&&p.depth<40){"Folder response is too complex."};if(p.eventType==XmlPullParser.START_TAG&&p.name=="response"&&p.namespace=="DAV:"){
   require(++responses<=10001){"This folder has too many entries. Open a smaller subfolder."};val depth=p.depth;var href="";var name="";var folder=false;var size:Long?=null;var modified="";var etag="";var quota:Long?=null;var taken:Long?=null;var good=false
   while(!(p.eventType==XmlPullParser.END_TAG&&p.depth==depth)){p.next();require(++tokens<200000&&p.depth<40&&p.eventType!=XmlPullParser.END_DOCUMENT);if(p.eventType==XmlPullParser.START_TAG&&p.namespace=="DAV:")when(p.name){
    "href"->href=p.nextText().take(8193)
    "propstat"->{val end=p.depth;var status="";var n="";var f=false;var s:Long?=null;var m="";var e="";var q:Long?=null;var u:Long?=null
     while(!(p.eventType==XmlPullParser.END_TAG&&p.depth==end)){p.next();require(++tokens<200000&&p.depth<40&&p.eventType!=XmlPullParser.END_DOCUMENT);if(p.eventType==XmlPullParser.START_TAG&&p.namespace=="DAV:")when(p.name){"status"->status=p.nextText();"displayname"->n=p.nextText().take(300);"collection"->f=true;"getcontentlength"->s=p.nextText().toLongOrNull()?.takeIf{it>=0};"getlastmodified"->m=p.nextText().take(100);"getetag"->e=p.nextText().take(500);"quota-available-bytes"->q=p.nextText().toLongOrNull()?.takeIf{it>=0};"quota-used-bytes"->u=p.nextText().toLongOrNull()?.takeIf{it>=0}}}
     if(Regex("HTTP/\\S+ 200(?: .*)?").matches(status.trim())){good=true;if(n.isNotBlank())name=n;folder=folder||f;size=s?:size;if(m.isNotBlank())modified=m;if(e.isNotBlank())etag=e;quota=q?:quota;taken=u?:taken}
    }
   }}
   val url=CloudPaths.href(root,parent,href);if(good&&url!=null){if(url.encodedPath.trimEnd('/')==parent.encodedPath.trimEnd('/')){available=quota;used=taken}else if(CloudPaths.direct(parent,url)){
    val canonical=if(folder&&!url.encodedPath.endsWith('/'))url.newBuilder().addPathSegment("").build()else url
    entries+=CloudEntry(canonical,name.ifBlank{url.pathSegments.lastOrNull{it.isNotBlank()}?:"File"},folder,size,modified,etag)
   }}
  };p.next()}
  return CloudListing(entries.distinctBy{it.url}.sortedWith(compareByDescending<CloudEntry>{it.folder}.thenBy{it.name.lowercase(java.util.Locale.ROOT)}),available,used)
 }
 suspend fun mkdir(parent:HttpUrl,name:String)=withContext(Dispatchers.IO){request(CloudPaths.child(parent,name,true),"MKCOL").use{check(it.code==201){status(it.code)}}}
 suspend fun upload(parent:HttpUrl,name:String,length:Long,source:()->InputStream,progress:(Long)->Unit)=withContext(Dispatchers.IO){
  require(length in -1..MAX_TRANSFER){"Files up to 2 GB are supported."};val job=currentCoroutineContext();val body=object:RequestBody(){override fun contentType()="application/octet-stream".toMediaType();override fun contentLength()=length;override fun isOneShot()=true;override fun writeTo(sink:okio.BufferedSink){source().use{input->val b=ByteArray(65536);var n=0L;while(true){job.ensureActive();val count=input.read(b);if(count<0)break;if(count==0)continue;n+=count;require(n<=MAX_TRANSFER&&(length<0||n<=length)){"The selected file changed or exceeds 2 GB."};sink.write(b,0,count);progress(n)};require(length<0||n==length){"The selected file changed. Choose it again."}}}}
  request(CloudPaths.child(parent,name),"PUT",body,mapOf("If-None-Match" to "*")).use{check(it.code in setOf(200,201,204)){status(it.code)}}
 }
 suspend fun download(entry:CloudEntry,target:File,limit:Long=MAX_TRANSFER,progress:(Long,Long)->Unit)=withContext(Dispatchers.IO){require(!entry.folder);require(limit>0);val maximum=minOf(MAX_TRANSFER,limit);val job=currentCoroutineContext();try{request(entry.url,"GET",headers=if(entry.etag.isNotBlank()&&!entry.etag.startsWith("W/")&&entry.etag.none{it=='\r'||it=='\n'})mapOf("If-Match" to entry.etag)else emptyMap()).use{r->check(r.code==200){status(r.code)};val body=r.body?:error("The server returned no file.");val size=body.contentLength();require(size<=maximum){"This file exceeds the selected folder’s size limit. Choose a connected phone folder in Files."};body.byteStream().use{input->target.outputStream().use{out->val b=ByteArray(65536);var total=0L;while(true){job.ensureActive();val count=input.read(b);if(count<0)break;if(count==0)continue;total+=count;require(total<=maximum){"This file exceeds the selected folder’s size limit. Choose a connected phone folder in Files."};check(target.parentFile!!.usableSpace>16*1024*1024){"Not enough space to save this file."};out.write(b,0,count);progress(total,size)};check(size<0||total==size){"The download was interrupted. Retry to get a complete file."}}}}}catch(e:Exception){target.delete();throw e}}
}
