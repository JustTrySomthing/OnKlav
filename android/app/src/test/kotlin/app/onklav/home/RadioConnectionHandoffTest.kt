package app.onklav.home

import android.net.Uri
import androidx.media3.datasource.DataSpec
import okhttp3.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.*
import java.net.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*

/** Real sockets/server and real OkHttp/RadioHttpSource. No decoder or station directory fixture. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
// Host-only Media3 fixtures explicitly opt in; no private API is shipped.
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class RadioConnectionHandoffTest {
 @Test fun liveHandoffHasNoWholeCallDeadline(){
  val call=SdkRadio.liveCall(Request.Builder().url("https://stream.example/live").build())
  assertEquals("A live stream must not inherit the directory request deadline",0L,call.timeout().timeoutNanos());assertFalse(call.timeout().hasDeadline());call.cancel()
 }
 private class SingleListener:AutoCloseable {
  private val server=ServerSocket(0,8,InetAddress.getByName("127.0.0.1"))
  val requests=AtomicInteger();val connections=CopyOnWriteArrayList<Socket>();val client=OkHttpClient.Builder().connectTimeout(2,TimeUnit.SECONDS).readTimeout(2,TimeUnit.SECONDS).build()
  private val thread=kotlin.concurrent.thread(isDaemon=true){while(!server.isClosed){val socket=try{server.accept()}catch(_:IOException){break};connections+=socket;kotlin.concurrent.thread(isDaemon=true){try{
   val reader=socket.getInputStream().bufferedReader();while(true){val line=reader.readLine()?:return@thread;if(line.isEmpty())break}
   val out=socket.getOutputStream();val number=requests.incrementAndGet()
   if(number>1){out.write("HTTP/1.1 503 One listener only\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray());out.flush();socket.close()}
   else {out.write("HTTP/1.1 200 OK\r\nContent-Type: audio/mpeg\r\nicy-metaint: 16000\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n4\r\nABCD\r\n".toByteArray());out.flush();while(socket.getInputStream().read()>=0){}}
  }catch(_:IOException){}finally{socket.close()}}}}
  fun connect(request:Request):Call=client.newCall(request.newBuilder().url("http://127.0.0.1:${server.localPort}/live").build())
  override fun close(){server.close();connections.forEach{it.close()};client.dispatcher.executorService.shutdownNow();client.connectionPool.evictAll();thread.join(1000)}
 }
 @Test fun oldProbeThenReopenReproducesTheFailureButTransferredStreamPlaysOnTheFirstRequest(){
  val url="https://stream.example/live"
  SingleListener().use{s->SdkRadio.resolve("old-flow",url,{},s::connect,{});val source=RadioHttpSource({},connect=s::connect);assertThrows(Exception::class.java){source.open(DataSpec(Uri.parse(url)))};source.close();assertEquals(2,s.requests.get())}
  SingleListener().use{s->var connection:RadioConnection?=null;val selected=SdkRadio.resolve("new-flow",url,{},s::connect,{},handoff={connection=it})
   val source=RadioHttpSource({},connection,s::connect);source.open(DataSpec(Uri.parse(selected)));val bytes=ByteArray(4);assertEquals(4,source.read(bytes,0,4));assertEquals("ABCD",bytes.toString(Charsets.US_ASCII));assertEquals(1,s.requests.get());assertEquals(listOf("16000"),source.responseHeaders["icy-metaint"]);source.close()
  }
 }
 @Test fun switchAndReopenCyclesEachUseOneRequestAndAlwaysStartAtTheFirstAudioByte(){
  repeat(4){index->SingleListener().use{s->var prepared:RadioConnection?=null;val url="https://stream.example/station-$index";SdkRadio.resolve("cycle-$index",url,{},s::connect,{},handoff={prepared=it});val source=RadioHttpSource({},prepared,s::connect);source.open(DataSpec(Uri.parse(url)));val first=ByteArray(1);assertEquals(1,source.read(first,0,1));assertEquals('A'.code,first[0].toInt());assertEquals(1,s.requests.get());source.close()}}
 }
 @Test fun preparedStreamIsOneShotAndClosingAnUnusedDataSourceCancelsIt(){
  SingleListener().use{s->var prepared:RadioConnection?=null;val url="https://stream.example/live";SdkRadio.resolve("cancel",url,{},s::connect,{},handoff={prepared=it});val source=RadioHttpSource({},prepared,s::connect);source.close();assertNull(prepared!!.take());assertEquals(1,s.requests.get())}
 }
 @Test fun revokedReadNeverConsumesThePreparedAudio(){
  SingleListener().use{s->var prepared:RadioConnection?=null;val url="https://stream.example/live";SdkRadio.resolve("revoke",url,{},s::connect,{},handoff={prepared=it});val source=RadioHttpSource({error("Revoked")},prepared,s::connect);assertThrows(Exception::class.java){source.open(DataSpec(Uri.parse(url)))};source.close();assertNull(prepared!!.take());assertEquals(1,s.requests.get())}
 }
 @Test fun releasingTheTransportOwnerUnblocksAnActiveSocketReadBeforeOpeningAnotherStation(){
  SingleListener().use{s->var connection:RadioConnection?=null;val url="https://stream.example/live";SdkRadio.resolve("blocked-read",url,{},s::connect,{},handoff={connection=it})
   val owner=RadioSources({});owner.adopt(connection);val source=owner.source();source.open(DataSpec(Uri.parse(url)));source.read(ByteArray(4),0,4)
   val entered=CountDownLatch(1);val finished=CountDownLatch(1);val failure=AtomicReference<Throwable?>()
   val reader=kotlin.concurrent.thread(isDaemon=true){entered.countDown();try{source.read(ByteArray(1),0,1)}catch(e:Throwable){failure.set(e)}finally{finished.countDown()}}
   assertTrue(entered.await(1,TimeUnit.SECONDS));assertFalse(finished.await(100,TimeUnit.MILLISECONDS));owner.close();assertTrue("Close must cancel the real blocked read",finished.await(2,TimeUnit.SECONDS));reader.join(1000);assertNotNull(failure.get());assertThrows(Exception::class.java){source.open(DataSpec(Uri.parse(url)))}
  }
 }

}
