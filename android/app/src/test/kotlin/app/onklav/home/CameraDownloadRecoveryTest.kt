package app.onklav.home

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.camera.core.ImageCapture
import androidx.camera.video.Quality
import androidx.camera.view.PreviewView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class CameraDownloadRecoveryTest{
 @Test fun cameraAcceptsTrustedAndroidDataDirectoryAlias(){
  val c=ApplicationProvider.getApplicationContext<Context>();val root=Files.createTempDirectory("camera-alias").toFile();val actual=File(root,"actual").apply{mkdirs()};val alias=File(root,"user-alias");Files.createSymbolicLink(alias.toPath(),actual.toPath())
  val wrapped=object:ContextWrapper(c){override fun getFilesDir()=alias}
  assertNotEquals(alias.absoluteFile,alias.canonicalFile)
  val folder=CameraDisk.folder(wrapped);assertEquals(File(actual,"camera-pending"),folder);assertTrue(folder.isDirectory)
  val capture=CameraDisk.create(wrapped,false).apply{writeBytes(byteArrayOf(1))};assertEquals(listOf(capture),CameraDisk.pending(wrapped));capture.delete();alias.delete();root.deleteRecursively()
 }
 @Test fun cameraRejectsRedirectedCaptureFolderButKeepsExternalData(){
  val c=ApplicationProvider.getApplicationContext<Context>();val root=Files.createTempDirectory("camera-leaf").toFile();val app=File(root,"app").apply{mkdirs()};val outside=File(root,"outside").apply{mkdirs()};val keep=File(outside,"keep").apply{writeText("keep")};val link=File(app,"camera-pending");Files.createSymbolicLink(link.toPath(),outside.toPath());val wrapped=object:ContextWrapper(c){override fun getFilesDir()=app}
  assertThrows(IllegalStateException::class.java){CameraDisk.folder(wrapped)};assertEquals("keep",keep.readText());link.delete();root.deleteRecursively()
 }
 @Test fun cameraProviderInitializationFailureBecomesRetryFeedback(){
  val c=ApplicationProvider.getApplicationContext<Context>();val camera=NativeCamera(c){throw IllegalStateException("Camera service unavailable")};val owner=object:LifecycleOwner{val registry=LifecycleRegistry(this);override val lifecycle:Lifecycle get()=registry}
  camera.bind(owner,PreviewView(c),false,false,Quality.SD,"4:3",true,ImageCapture.FLASH_MODE_OFF);assertFalse(camera.ready);assertEquals("Camera service unavailable",camera.message);camera.close()
 }
 private fun formats(sequence:Int)=YoutubeResolver.Result("Video",listOf(YoutubeStream(140,"https://r1.googlevideo.com/videoplayback?source=youtube&seq=$sequence","audio/mp4",0,true)))
 @Test fun rejectedDownloadLinksRefreshOnceAndThenSucceed()=runBlocking<Unit>{var resolves=0;var attempts=0;var refresh=0;val seen=mutableListOf<String>();val value=freshYoutubeTransfer(resolve={formats(++resolves)},refresh={refresh++}){r->seen+=r.select(true,0).first.url;if(++attempts==1)throw YoutubeLinkRejected(403);"saved"};assertEquals("saved",value);assertEquals(2,resolves);assertEquals(1,refresh);assertNotEquals(seen[0],seen[1])}
 @Test fun repeatedLinkRejectionAndCancellationNeverLoop()=runBlocking<Unit>{var resolves=0;var calls=0;try{freshYoutubeTransfer(resolve={formats(++resolves)}){calls++;throw YoutubeLinkRejected(410)};fail("Must fail after one refresh")}catch(e:YoutubeLinkRejected){assertEquals(410,e.status)};assertEquals(2,resolves);assertEquals(2,calls);resolves=0;try{freshYoutubeTransfer(resolve={formats(++resolves)}){throw CancellationException("cancel")};fail("Cancellation must propagate")}catch(e:CancellationException){};assertEquals(1,resolves)}
 @Test fun mediaServerRejectionPreservesHttpStatusForRefresh()=runBlocking<Unit>{val c=ApplicationProvider.getApplicationContext<Context>();val target=File(c.cacheDir,"youtube-rejected");val client=OkHttpClient.Builder().addInterceptor{chain->Response.Builder().request(chain.request()).code(403).protocol(Protocol.HTTP_1_1).message("Forbidden").body("rejected".toResponseBody()).build()}.build();try{YoutubeDownload.transfer(formats(1).streams[0].url,target,"Test",{},client){_,_->};fail("Should reject")}catch(e:YoutubeLinkRejected){assertEquals(403,e.status);assertEquals(0,target.length())};target.delete()}
}
