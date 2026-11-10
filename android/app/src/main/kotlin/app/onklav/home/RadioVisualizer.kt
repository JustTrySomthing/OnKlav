package app.onklav.home

import android.Manifest
import android.content.res.Configuration
import android.media.audiofx.Visualizer
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.onklav.shared.R
import kotlinx.coroutines.delay
import kotlin.math.*

/** Only the radio's authorized positive audio session; never mic input or the device mix.
 * Original lightweight implementation; no Velo code/assets/dependencies are incorporated. */
internal class RadioWaveform(private val emit:(ByteArray)->Unit,private val emitFft:(ByteArray,Int)->Unit):AutoCloseable {
 private var visualizer:Visualizer?=null
 private val main=Handler(Looper.getMainLooper())
 @Volatile private var generation=0
 @androidx.annotation.RequiresPermission(Manifest.permission.RECORD_AUDIO)
 fun start(session:Int){
  close();require(session>0)
  val token=generation
  val v=Visualizer(session)
  try {
   v.enabled=false
   val range=Visualizer.getCaptureSizeRange();v.captureSize=1024.coerceIn(range[0],range[1])
   check(v.setDataCaptureListener(object:Visualizer.OnDataCaptureListener {
    override fun onWaveFormDataCapture(v:Visualizer,data:ByteArray,rate:Int){
     val copy=data.copyOf();main.post{if(generation==token)emit(copy)}
    }
    override fun onFftDataCapture(v:Visualizer,data:ByteArray,rate:Int){
     val copy=data.copyOf();main.post{if(generation==token)emitFft(copy,rate)}
    }
   },minOf(30_000,Visualizer.getMaxCaptureRate()).coerceAtLeast(1),true,true)==Visualizer.SUCCESS)
   v.enabled=true;visualizer=v
  }catch(e:Exception){generation++;v.release();throw e}
 }
 override fun close(){
  generation++
  val v=visualizer;visualizer=null
  if(v!=null){runCatching{v.enabled=false;v.setDataCaptureListener(null,0,false,false)};runCatching{v.release()}}
  emit(byteArrayOf())
 }
}

/** Android's packed signed FFT, with log-spaced 40 Hz–16 kHz bands. DC and Nyquist omitted. */
internal fun radioSpectrum(fft:ByteArray,samplingMilliHz:Int,count:Int=32):FloatArray {
 require(count in 1..128)
 val out=FloatArray(count)
 if(fft.size<4||samplingMilliHz<=0)return out
 val hz=samplingMilliHz/1000.0
 val high=minOf(16000.0,hz/2)
 if(high<=40)return out
 for(b in out.indices){
  val lo=(40*(high/40).pow(b.toDouble()/count)*fft.size/hz).toInt().coerceIn(1,fft.size/2-1)
  val hi=ceil(40*(high/40).pow((b+1.0)/count)*fft.size/hz).toInt().coerceIn(lo+1,fft.size/2)
  var peak=0.0
  for(i in lo until hi){val real=fft[2*i].toDouble();val imag=fft[2*i+1].toDouble();peak=maxOf(peak,hypot(real,imag)/128)}
  out[b]=(ln(1+peak*24)/ln(25.0)).toFloat().coerceIn(0f,1f)
 }
 return out
}

internal val radioVisualNames=listOf("Wave", "Spectrum", "Orbit", "Mandala", "Slipstream", "Aurora")
internal fun radioVisualMode(value:Int)=value.takeIf{it in radioVisualNames.indices}?:1
internal val radioColorNames=listOf("Red", "Yellow", "Blue", "Orange", "Green", "Violet", "Rainbow")
internal val radioVisualColors=listOf(Color(0xFFFF5268),Color(0xFFFFDF59),Color(0xFF579EFF),Color(0xFFFFA04A),Color(0xFF51EBAC),Color(0xFFB18AFF),Color(0xFFD0D2D8))
internal fun radioVisualPalette(index:Int):List<Color> {
 if(index==6)return listOf(Color(0xFF69E6FF),Color(0xFFA888FF),Color(0xFFFF68B5))
 val c=radioVisualColors[index.coerceIn(0,5)]
 return listOf(androidx.compose.ui.graphics.lerp(c,Color.White,.32f),c,androidx.compose.ui.graphics.lerp(c,Color.Black,.24f))
}

internal fun radioDefaultIntensity(mode:Int)=floatArrayOf(1.05f,1.65f,1.25f,1.65f,1.35f,1.15f)[radioVisualMode(mode)]
private val radioControlGray=Color(0xFFBFC3CD)
@Composable private fun RadioColorDot(index:Int){Canvas(Modifier.size(28.dp)){if(index==6)drawCircle(Brush.sweepGradient(listOf(Color.Red,Color.Yellow,Color.Green,Color.Cyan,Color.Blue,Color.Magenta,Color.Red)))else drawCircle(radioVisualColors[index]);drawCircle(Color.White.copy(alpha=.7f),style=Stroke(1.5.dp.toPx()))}}

@Composable internal fun RadioVisualizerDialog(
 title:String,playing:Boolean,foreground:Boolean,samples:ByteArray,bands:FloatArray,status:String?,canEnable:Boolean,
 onEnable:()->Unit,onRetry:()->Unit,onDismiss:()->Unit,transportEnabled:Boolean,onPlayPause:()->Unit
){
 val context=LocalContext.current
 val prefs=remember(context){context.getSharedPreferences("radio-player",0)}
 var savedMode by rememberSaveable{mutableIntStateOf(radioVisualMode(prefs.getInt("visual_mode",1)))}
 val mode=radioVisualMode(savedMode)
 // A new preference avoids interpreting the former three multicolor palettes as six swatches.
 var color by rememberSaveable{mutableIntStateOf(prefs.getInt("visual_color_v2",6).coerceIn(0,6))}
 var intensity by rememberSaveable{mutableFloatStateOf(radioDefaultIntensity(mode))}
 var controls by rememberSaveable{mutableStateOf(true)}
 var effectPicker by remember{mutableStateOf(false)}
 var colorPicker by remember{mutableStateOf(false)}
 val wide=LocalConfiguration.current.orientation==Configuration.ORIENTATION_LANDSCAPE
 MediaAutoRotation(true)
 LaunchedEffect(wide,controls,playing,foreground,status,mode,color,effectPicker,colorPicker){
  if(wide&&controls&&playing&&foreground&&status==null&&!effectPicker&&!colorPicker){delay(5000);controls=false}
 }
 LaunchedEffect(wide,status){controls=true}
 Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)){
  ImmersiveMediaMode(wide)
  Box(Modifier.fillMaxSize().then(if(wide)Modifier else Modifier.safeDrawingPadding().padding(12.dp)),contentAlignment=Alignment.Center){
   Surface(Modifier.fillMaxWidth().then(if(wide)Modifier.fillMaxHeight()else Modifier.heightIn(max=820.dp).fillMaxHeight(.94f)),
    color=Color(0xFF070C18),contentColor=Color(0xFFEAF3FF),shape=RoundedCornerShape(if(wide)0.dp else 28.dp)){
    Box(Modifier.fillMaxSize().testTag(if(wide)"radio-visualizer-landscape"else"radio-visualizer-portrait")){
     // Controls reserve real space: the scene fits the visible area in both orientations.
     Column(Modifier.fillMaxSize().then(if(wide&&controls)Modifier.safeDrawingPadding()else Modifier)){
      if(controls||!wide)Row(Modifier.fillMaxWidth().padding(start=20.dp,end=8.dp,top=4.dp),verticalAlignment=Alignment.CenterVertically){
       Column(Modifier.weight(1f)){
        Text("VISUALIZER",style=MaterialTheme.typography.labelSmall,color=radioControlGray)
        Text(title,color=radioControlGray,style=MaterialTheme.typography.titleMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
       }
       IconButton(onClick=onDismiss){Icon(painterResource(R.drawable.ref_action_close),"Close music visualizer")}
      }
      RadioVisualCanvas(samples,bands,mode,color,Modifier.fillMaxWidth().weight(1f).testTag("radio-visual-scene")
       .then(if(wide)Modifier.clickable(onClickLabel=if(controls)"Hide visualizer controls"else"Show visualizer controls"){controls=!controls}else Modifier),active=playing&&foreground,intensity=intensity)
      if(controls||!wide)Column(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)){
       status?.let{Text(it,style=MaterialTheme.typography.bodySmall,color=Color(0xFFCCD5E9),maxLines=2,overflow=TextOverflow.Ellipsis)}
       if(canEnable){FilledTonalButton(onClick=onEnable){Text("Enable audio visuals")}}
       else if(status=="Audio visuals unavailable on this output"){TextButton(onClick=onRetry){Text("Retry audio visuals")}}
       Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
        Surface(onClick={effectPicker=true},shape=RoundedCornerShape(18.dp),color=Color(0xFF172238),modifier=Modifier.weight(1f).semantics{contentDescription="Choose visualizer effect";stateDescription=radioVisualNames[mode]}){
         Row(Modifier.padding(horizontal=16.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
          Text(radioVisualNames[mode],Modifier.weight(1f),style=MaterialTheme.typography.titleSmall,maxLines=1)
          Text("⌄",color=Color(0xFFADBEDB))
         }
        }
        Box{
         IconButton(onClick={colorPicker=!colorPicker},modifier=Modifier.size(48.dp).semantics{contentDescription="Choose visualizer color";stateDescription=radioColorNames[color]}){
          RadioColorDot(color)
         }
         DropdownMenu(expanded=colorPicker,onDismissRequest={colorPicker=false},containerColor=Color(0xFF142037),shape=RoundedCornerShape(22.dp)){
          Column(Modifier.padding(8.dp)){
           radioColorNames.chunked(3).forEachIndexed{row,names->Row{names.forEachIndexed{col,name->val i=row*3+col
            IconButton(onClick={color=i;prefs.edit().putInt("visual_color_v2",i).apply();colorPicker=false},modifier=Modifier.size(48.dp).semantics{contentDescription="$name visualizer color";selected=color==i}){
             RadioColorDot(i)
            }
           }}}
          }
         }
        }
        FilledIconButton(onClick=onPlayPause,enabled=transportEnabled,modifier=Modifier.requiredSize(48.dp),colors=IconButtonDefaults.filledIconButtonColors(containerColor=radioControlGray,contentColor=Color(0xFF151923))){
         Icon(painterResource(if(playing)R.drawable.ref_radio_pause else R.drawable.ref_radio_play),if(playing)"Pause station"else"Play station",Modifier.size(24.dp))
        }
       }
       Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
        Icon(painterResource(R.drawable.audio_edit_graphiceq),null,Modifier.size(20.dp),tint=radioControlGray)
        Column(Modifier.weight(1f)){
         Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Intensity",color=radioControlGray,style=MaterialTheme.typography.labelMedium);Text("${(intensity*100).roundToInt()}%",style=MaterialTheme.typography.labelSmall,color=Color(0xFFADBEDB))}
         Slider(value=intensity,onValueChange={intensity=it;controls=true},onValueChangeFinished={prefs.edit().putFloat("visual_intensity",intensity).apply()},valueRange=.25f..2.5f,modifier=Modifier.fillMaxWidth().height(30.dp).semantics{contentDescription="Visualizer intensity"},colors=SliderDefaults.colors(thumbColor=radioControlGray,activeTrackColor=radioControlGray))
        }
       }
       if(!wide)Text("Rotate for full screen",style=MaterialTheme.typography.labelSmall,color=Color(0xFF8E9BB8))
      }
     }
     if(effectPicker)RadioEffectPicker(mode,radioVisualColors[color],{effectPicker=false}){if(mode!=it)intensity=radioDefaultIntensity(it);savedMode=it;prefs.edit().putInt("visual_mode",it).apply();effectPicker=false}
    }
   }
  }
 }
}

/** A snapping wheel: neighboring names remain visible, selected row always reads as a button. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable private fun RadioEffectPicker(mode:Int,accent:Color,onDismiss:()->Unit,onChoose:(Int)->Unit){
 val state=androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex=mode)
 val fling=androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(state)
 var centered by remember{mutableIntStateOf(mode)}
 LaunchedEffect(state){snapshotFlow{state.layoutInfo}.collect{info->
  val middle=(info.viewportStartOffset+info.viewportEndOffset)/2
  info.visibleItemsInfo.minByOrNull{abs(it.offset+it.size/2-middle)}?.let{centered=it.index}
 }}
 Dialog(onDismissRequest=onDismiss){
  Surface(shape=RoundedCornerShape(28.dp),color=Color(0xFF101B2E),contentColor=Color(0xFFEAF3FF)){
   Column(Modifier.widthIn(max=340.dp).fillMaxWidth().padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally){
    Row(verticalAlignment=Alignment.CenterVertically){Text("Visual style",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge);IconButton(onClick=onDismiss){Icon(painterResource(R.drawable.ref_action_close),"Close effect picker")}}
    Box(Modifier.fillMaxWidth().height(220.dp),contentAlignment=Alignment.Center){
     Box(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).background(accent.copy(alpha=.15f)))
     androidx.compose.foundation.lazy.LazyColumn(state=state,flingBehavior=fling,contentPadding=PaddingValues(vertical=84.dp),horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.fillMaxSize().testTag("radio-effect-wheel")){
      items(radioVisualNames.size){i->
       TextButton(onClick={onChoose(i)},modifier=Modifier.fillMaxWidth().height(52.dp).semantics{selected=centered==i},colors=ButtonDefaults.textButtonColors(contentColor=if(centered==i)accent else Color(0xFF8091AF))){Text(radioVisualNames[i],style=if(centered==i)MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium)}
      }
     }
     Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(38.dp).background(Brush.verticalGradient(listOf(Color(0xFF101B2E),Color.Transparent))))
     Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(38.dp).background(Brush.verticalGradient(listOf(Color.Transparent,Color(0xFF101B2E)))))
    }
    Button(onClick={onChoose(centered)},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=accent,contentColor=Color(0xFF071220))){Text("Use ${radioVisualNames[centered]}")}
   }
  }
 }
}
