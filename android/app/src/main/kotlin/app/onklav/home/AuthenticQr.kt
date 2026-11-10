package app.onklav.home

import android.Manifest
import android.graphics.Bitmap
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.concurrent.Executors

internal object AuthenticQr{
 private fun decode(source:LuminanceSource):String=QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)),mapOf(DecodeHintType.TRY_HARDER to true,DecodeHintType.CHARACTER_SET to "UTF-8")).text.also{require(it.length<=100000)}
 fun image(bitmap:Bitmap):String{require(bitmap.width.toLong()*bitmap.height<=5_000_000);val pixels=IntArray(bitmap.width*bitmap.height);bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height);return try{decode(RGBLuminanceSource(bitmap.width,bitmap.height,pixels))}finally{pixels.fill(0)}}
 fun selected(c:Context,uri:Uri):String{val resolver=c.contentResolver;val options=android.graphics.BitmapFactory.Options().apply{inJustDecodeBounds=true};resolver.openInputStream(uri).use{android.graphics.BitmapFactory.decodeStream(it,null,options)};require(options.outWidth>0&&options.outHeight>0){"Choose an image containing an authenticator QR."};options.inJustDecodeBounds=false;options.inSampleSize=1;while(options.outWidth/options.inSampleSize>2000||options.outHeight/options.inSampleSize>2000)options.inSampleSize*=2
  val bitmap=resolver.openInputStream(uri).use{android.graphics.BitmapFactory.decodeStream(it,null,options)}?:error("Image could not be opened.");return try{image(bitmap)}finally{bitmap.recycle()}
 }
 fun frame(frame:ImageProxy):String{val plane=frame.planes.first();val buffer=plane.buffer.duplicate();val w=frame.width;val h=frame.height;require(w.toLong()*h<=5_000_000);val offset=buffer.position();val bytes=ByteArray(w*h);for(y in 0 until h)for(x in 0 until w)bytes[y*w+x]=buffer.get(offset+y*plane.rowStride+x*plane.pixelStride);return try{decode(PlanarYUVLuminanceSource(bytes,w,h,0,0,w,h,false))}finally{bytes.fill(0)}}
 fun bitmap(value:String):Bitmap{require(value.length<=8192);val matrix=QRCodeWriter().encode(value,BarcodeFormat.QR_CODE,600,600,mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,EncodeHintType.MARGIN to 4,EncodeHintType.CHARACTER_SET to "UTF-8"));return Bitmap.createBitmap(matrix.width,matrix.height,Bitmap.Config.ARGB_8888).apply{val p=IntArray(matrix.width*matrix.height){i->if(matrix[i%matrix.width,i/matrix.width])android.graphics.Color.BLACK else android.graphics.Color.WHITE};setPixels(p,0,width,0,0,width,height)}}
}
@Composable internal fun AuthenticScanner(close:()->Unit,result:(String)->Unit){
 val c=LocalContext.current;val owner=LocalLifecycleOwner.current;var error by remember{mutableStateOf("")};var retry by remember{mutableIntStateOf(0)}
 val permission=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()){if(it){error="";retry++}else error="Camera access was not granted. You can enter a setup key or use a QR image."}
 var granted by remember(retry){mutableStateOf(ContextCompat.checkSelfPermission(c,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)}
 Column(Modifier.fillMaxSize()){
  HomeHeader("Scan authenticator QR",back=close)
  Text("Scan a setup QR or Google Authenticator transfer QR.",Modifier.padding(16.dp),style=MaterialTheme.typography.bodyMedium)
  if(granted){key(retry){val preview=remember{PreviewView(c).apply{implementationMode=PreviewView.ImplementationMode.COMPATIBLE;scaleType=PreviewView.ScaleType.FIT_CENTER}}
   val callback by rememberUpdatedState(result)
   DisposableEffect(preview,owner){val executor=Executors.newSingleThreadExecutor();val future=ProcessCameraProvider.getInstance(c);var active=true;var provider:ProcessCameraProvider?=null;var analysis:ImageAnalysis?=null;var usePreview:Preview?=null;var found=false
    future.addListener({if(active)runCatching{val p=future.get();provider=p;val view=Preview.Builder().build().also{it.surfaceProvider=preview.surfaceProvider};usePreview=view;val reader=ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();analysis=reader
     reader.setAnalyzer(executor){image->try{if(!found&&active){val value=AuthenticQr.frame(image);found=true;ContextCompat.getMainExecutor(c).execute{if(active)callback(value)}}}catch(_:Exception){}finally{image.close()}}
     p.bindToLifecycle(owner,CameraSelector.DEFAULT_BACK_CAMERA,view,reader)
    }.onFailure{error="Camera unavailable. Close other camera use, then retry or enter the key manually."}},ContextCompat.getMainExecutor(c))
    onDispose{active=false;analysis?.clearAnalyzer();val owned=listOfNotNull(usePreview,analysis);if(owned.isNotEmpty())provider?.unbind(*owned.toTypedArray());executor.shutdownNow()}
   }
   AndroidView(factory={preview},modifier=Modifier.fillMaxWidth().weight(1f))
  }}else{Spacer(Modifier.weight(1f));Button(onClick={permission.launch(Manifest.permission.CAMERA)},modifier=Modifier.align(Alignment.CenterHorizontally)){Text("Enable camera")};Spacer(Modifier.weight(1f))}
  if(error.isNotBlank())Text(error,Modifier.padding(16.dp),color=MaterialTheme.colorScheme.error)
  if(error.isNotBlank()&&granted)TextButton(onClick={retry++;error=""}){Text("Retry camera")}
  TextButton(onClick=close,modifier=Modifier.align(Alignment.CenterHorizontally)){Text("Back to Authentic")}
 }
}
