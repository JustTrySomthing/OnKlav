package app.onklav.home

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import kotlin.math.*

/** Controls reserve their own stage; radial effects may deliberately extend beyond its edges. */
internal data class RadioStage(val width:Float,val height:Float){
 val wide get()=width>height
 val center get()=Offset(width/2,height/2)
 val unit get()=minOf(width,height)
 val spectrumBottom get()=height*.5f
 val orbitRadius get()=unit*.25f
 val orbitReach get()=unit*.18f
}
internal val radioSceneDescriptions=listOf("Audio waveform","Audio frequency spectrum","Circular audio spectrum","Audio mandala","Audio slipstream","Audio aurora")

/** A shorter, zero-crossing anchored time window makes portrait waves readable.
 * Gentle three-sample averaging rejects single sample spikes without inventing sound. */
internal fun radioWaveTrace(samples:ByteArray,wide:Boolean):FloatArray {
 if(samples.size<2)return FloatArray(2)
 val window=minOf(samples.size,if(wide)512 else 144)
 val search=minOf(samples.size-window,256).coerceAtLeast(0)
 var start=0
 for(i in 1..search){if((samples[i-1].toInt()and 255)<128&&(samples[i].toInt()and 255)>=128){start=i;break}}
 val out=FloatArray(window)
 for(i in out.indices){var total=0f;var n=0;for(j in maxOf(0,start+i-1)..minOf(samples.lastIndex,start+i+1)){total+=((samples[j].toInt()and 255)-128)/128f;n++};out[i]=total/n}
 val peak=out.maxOf{abs(it)}
 val gain=if(peak>.015f)minOf(2.5f,.86f/peak)else 1f
 return FloatArray(window){(out[it]*gain).coerceIn(-1f,1f)}
}
internal fun radioVisualLevel(raw:Float)=sqrt(raw.takeIf{it.isFinite()}?.coerceIn(0f,1f)?:0f)
internal fun radioVisualBaseHue(palette:Int)=floatArrayOf(352f,49f,216f,31f,154f,264f)[palette.coerceIn(0,5)]
internal fun radioVisualExpansion(intensity:Float)=.42f+(intensity.takeIf{it.isFinite()}?.coerceIn(.25f,2.5f)?:1.25f)*.94f
/** Separate bass/mid/treble spans, interpolated rather than cloning one EQ three times. */
internal fun radioLayerBand(bands:FloatArray,layer:Int,t:Float):Float {
 val start=intArrayOf(0,10,21)[layer.coerceIn(0,2)];val end=intArrayOf(10,21,31)[layer.coerceIn(0,2)]
 val at=start+(end-start)*t.coerceIn(0f,1f);val i=at.toInt();val f=at-i
 fun v(n:Int)=bands.getOrElse(n){0f}.takeIf{it.isFinite()}?.coerceIn(0f,1f)?:0f
 return v(i)*(1-f)+v(minOf(31,i+1))*f
}
/** Low/mid/high time-domain components from the captured waveform; never synthetic audio. */
internal fun radioWaveLayers(trace:FloatArray):Array<FloatArray>{
 fun smooth(radius:Int)=FloatArray(trace.size){i->var sum=0f;var count=0;for(j in maxOf(0,i-radius)..minOf(trace.lastIndex,i+radius)){sum+=trace[j];count++};sum/count.coerceAtLeast(1)}
 val low=smooth(12);val mid=smooth(3)
 return arrayOf(low,FloatArray(trace.size){(mid[it]-low[it])*1.9f},FloatArray(trace.size){(trace[it]-mid[it])*2.6f})
}


/** Palette accents remain near the chosen swatch, including moving scenes. */
internal fun radioSceneHue(palette:Int,offset:Float=0f,seconds:Double=0.0):Float =
 (((if(palette==6)offset+seconds.toFloat()*9f else radioVisualBaseHue(palette)+sin(offset*PI.toFloat()/180)*18f+sin(seconds*.15).toFloat()*5f)%360+360)%360)
internal fun radioMandalaTurn(seconds:Double,layer:Int)=seconds.toFloat()*(floatArrayOf(.34f,.48f,.41f,.59f)[layer.coerceIn(0,3)])*(if(layer%2==0)1 else -1)
internal fun radioMandalaPulse(bands:FloatArray,layer:Int):Float {
 val start=intArrayOf(0,6,14,23)[layer.coerceIn(0,3)];val end=intArrayOf(5,13,22,31)[layer.coerceIn(0,3)]
 var sum=0f;for(i in start..end){val v=bands.getOrElse(i){0f}.takeIf{it.isFinite()}?.coerceIn(0f,1f)?:0f;sum+=v*v}
 return sqrt(sum/(end-start+1)).pow(floatArrayOf(.95f,.80f,1.1f,.85f)[layer.coerceIn(0,3)])
}
internal fun radioMandalaRadius(unit:Float,layer:Int,angle:Float,pulse:Float):Float{
 val fold=(.5f+.5f*cos(angle*(6+layer*2)+pulse*(layer+1)*.7f)).pow(1.45f)
 return unit*((.045f+layer*.034f)*(.8f+pulse*1.1f)+(.012f+pulse*.23f)*fold)
}
/** Low frequencies sit at the center; both sides share the same measured band and taper. */
internal fun radioStarburstHeight(bands:FloatArray,t:Float,intensity:Float):Float{
 val distance=abs(t.coerceIn(0f,1f)*2-1)
 val at=distance*31;val index=at.toInt();val fraction=at-index
 val signal=bands.getOrElse(index){0f}*(1-fraction)+bands.getOrElse(minOf(31,index+1)){0f}*fraction
 // Each mirrored pair follows its own FFT neighborhood; a small floor keeps the silhouette legible.
 val response=signal.coerceIn(0f,1f).pow(1.9f)
 return ((.025f+response*1.05f)*intensity*(.32f+.68f*(1-distance).pow(.65f))).coerceIn(0f,1.5f)
}
/** A stable smooth oscilloscope ribbon, with windowed edges and no detached frequency traces. */
internal fun radioWaveRibbon(trace:FloatArray,count:Int=96):FloatArray{
 if(trace.size<2)return FloatArray(count)
 return FloatArray(count){i->
  val at=i*(trace.lastIndex)/(count-1f);val radius=maxOf(2,trace.size/80);val center=at.toInt();var sum=0f;var weight=0f
  for(j in maxOf(0,center-radius)..minOf(trace.lastIndex,center+radius)){val k=1f-abs(j-at)/(radius+1);sum+=trace[j]*k;weight+=k}
  sum/weight.coerceAtLeast(.01f)*sin(PI*i/(count-1)).toFloat().coerceAtLeast(0f).pow(.45f)
 }
}

/** Only actual captured audio advances reactive scenes; silence and pause never invent beats.
 * Work is bounded: 32 bands, 512 wave vertices, <1100 scene vertices, no particles/assets. */
@Composable internal fun RadioVisualCanvas(samples:ByteArray,bands:FloatArray,mode:Int,palette:Int,modifier:Modifier=Modifier,active:Boolean=true,intensity:Float=1.25f){
 val strength=intensity.takeIf{it.isFinite()}?.coerceIn(.25f,2.5f)?:1.25f
 val colors=radioVisualPalette(palette)
 var envelope by remember{mutableStateOf(FloatArray(32))}
 var pulseBands by remember{mutableStateOf(FloatArray(32))}
 var motion by remember{mutableDoubleStateOf(0.0)}
 var energy by remember{mutableFloatStateOf(0f)}
 var beat by remember{mutableFloatStateOf(0f)}
 var previousBass by remember{mutableFloatStateOf(0f)}
 var drift by remember{mutableDoubleStateOf(0.0)}
 var lastTime by remember{mutableLongStateOf(0L)}
 LaunchedEffect(samples,bands,active){
  val now=android.os.SystemClock.elapsedRealtime()
  val dt=if(lastTime==0L)0f else ((now-lastTime)/1000f).coerceIn(0f,.1f);lastTime=now
  if(!active||samples.isEmpty()){envelope=FloatArray(32);pulseBands=FloatArray(32);energy=0f;beat=0f;previousBass=0f}
  else{
   envelope=FloatArray(32){i->val next=radioVisualLevel(bands.getOrElse(i){0f});val old=envelope[i];old+(next-old)*(if(next>old).78f else .26f)}
   pulseBands=FloatArray(32){i->val next=bands.getOrElse(i){0f}.takeIf{it.isFinite()}?.coerceIn(0f,1f)?:0f;val old=pulseBands[i];old+(next-old)*(if(next>old).85f else .22f)}
   var square=0f
   for(b in samples){val s=((b.toInt()and 255)-128)/128f;square+=s*s}
   val signal=sqrt(square/samples.size).coerceIn(0f,1f)
   energy=(signal*2.2f).coerceIn(0f,1f)
   val bass=envelope.take(8).average().toFloat()
   beat=maxOf(beat*.82f,((bass-previousBass-.015f)*5f).coerceIn(0f,1f));previousBass=bass
   if(energy>.005f){motion+=dt*(1.1+energy*3.8)*strength;drift+=dt}
  }
 }
 val wideTrace=remember(samples){radioWaveTrace(samples,true)}
 val narrowTrace=remember(samples){radioWaveTrace(samples,false)}
 val ribbons=remember(wideTrace,narrowTrace){arrayOf(radioWaveRibbon(narrowTrace),radioWaveRibbon(wideTrace))}
 val selectedMode=radioVisualMode(mode)
 Canvas(modifier.clipToBounds().semantics{contentDescription=radioSceneDescriptions[selectedMode]}){
  // Wrap each angle/travel in its own period; no visible global-phase jump.
  val phase=motion.toFloat()
  fun hue(offset:Float)=Color.hsv(radioSceneHue(palette,offset,drift),.72f,1f)
  fun level(i:Int)=(envelope[i]*strength).coerceIn(0f,1f)
  val power=(energy*strength).coerceIn(0f,1f)
  val stage=RadioStage(size.width,size.height);val w=stage.width;val h=stage.height;val u=stage.unit;val c=stage.center
  drawRect(Brush.radialGradient(listOf(colors[1].copy(alpha=.13f),Color(0xFF070C18)),center=c,radius=size.maxDimension*.7f))
  fun glow(path:Path,color:Color,width:Float=2.dp.toPx(),alpha:Float=1f){
   drawPath(path,color.copy(alpha=.09f*alpha),style=Stroke(width*4,cap=StrokeCap.Round,join=StrokeJoin.Round))
   drawPath(path,color.copy(alpha=alpha),style=Stroke(width,cap=StrokeCap.Round,join=StrokeJoin.Round))
  }
  when(selectedMode){
   0->{ // A single luminous ribbon: measured, softly interpolated, centered in either orientation.
    val trace=ribbons[if(stage.wide)1 else 0];val line=Path();val points=trace.mapIndexed{i,v->Offset(w*(.04f+.92f*i/trace.lastIndex),c.y-(if(active)v else 0f)*h*.35f*strength)}
    line.moveTo(points.first().x,points.first().y)
    for(i in 0 until points.lastIndex){val before=points[maxOf(0,i-1)];val a=points[i];val b=points[i+1];val after=points[minOf(points.lastIndex,i+2)]
     line.cubicTo(a.x+(b.x-before.x)/6,a.y+(b.y-before.y)/6,b.x-(after.x-a.x)/6,b.y-(after.y-a.y)/6,b.x,b.y)
    }
    val fill=Path().apply{addPath(line);lineTo(w*.96f,c.y);lineTo(w*.04f,c.y);close()}
    drawPath(fill,Brush.verticalGradient(listOf(colors[1].copy(alpha=.05f),colors[1].copy(alpha=.22f),colors[1].copy(alpha=.04f)),startY=c.y-h*.3f,endY=c.y+h*.3f))
    if(palette==6)drawPath(line,Brush.horizontalGradient(listOf(hue(0f),hue(90f),hue(180f),hue(270f))),style=Stroke(3.dp.toPx(),cap=StrokeCap.Round,join=StrokeJoin.Round))else glow(line,colors[1],3.dp.toPx(),.9f)
    drawPath(line,colors[0].copy(alpha=.9f),style=Stroke(1.dp.toPx(),cap=StrokeCap.Round,join=StrokeJoin.Round))
   }
   1->{ // A symmetric starburst: bass in the middle, treble at the edges, reflected above/below.
    val count=48;val span=w*.91f;val step=span/count;val bar=step*.76f;val room=h*.42f
    for(i in 0 until count){val t=(i+.5f)/count;val extent=radioStarburstHeight(envelope,t,strength)*room
     val response=radioLayerBand(pulseBands,1,t)
     val sway=sin(phase*1.7f+i*.83f)*step*.23f*power
     val x=w*.045f+i*step+(step-bar)/2+sway;val alpha=(.42f+response*.45f+(.5f+.5f*sin(phase*2.2f+i*1.31f))*.18f).coerceIn(.35f,1f);val ink=Color.hsv(radioSceneHue(palette,i*37f,drift),.48f+.46f*(.5f+.5f*sin(i*2.4f)),.72f+.28f*(.5f+.5f*cos(i*1.7f)))
     if(extent>.2f){
      drawRoundRect(ink.copy(alpha=.06f),Offset(x-bar*.65f,c.y-extent),Size(bar*2.3f,extent*2),CornerRadius(bar))
      val path=Path();val segments=12
      fun side(direction:Float,reverse:Boolean){for(j in 0..segments){val k=if(reverse)segments-j else j;val f=k/segments.toFloat();val y=c.y-extent+extent*2*f;val bend=sin(f*PI.toFloat())*sin(f*9+phase*2.5f+i*.91f)*step*.34f*power;val xx=x+bar/2+bend+direction*bar/2;if(j==0&&!reverse)path.moveTo(xx,y)else path.lineTo(xx,y)}}
      side(-1f,false);side(1f,true);path.close()
      drawPath(path,Brush.verticalGradient(listOf(ink.copy(alpha=alpha),ink.copy(alpha=alpha*.65f),lerp(ink,Color.White,.4f).copy(alpha=alpha),ink.copy(alpha=alpha*.65f),ink.copy(alpha=alpha)),startY=c.y-extent,endY=c.y+extent))
     }
    }
   }
   2->{ scale(radioVisualExpansion(strength),pivot=c){
    val bass=radioLayerBand(envelope,0,.4f).pow(1.65f);val r=u*(.12f+bass*.11f);val reach=u*.34f
    drawCircle(Brush.radialGradient(listOf(colors[1].copy(alpha=.12f+energy*.14f),Color.Transparent),center=c,radius=r*1.5f),r*1.5f,c)
    drawCircle(colors[0].copy(alpha=.25f),r,c,style=Stroke(1.dp.toPx()))
    for(i in 0 until 96){val band=if(i<48)i*32/48 else (95-i)*32/48;val a=i/96f*2*PI.toFloat()+PI.toFloat()/2;val direction=Offset(cos(a),sin(a));val from=c+direction*(r+3.dp.toPx());val to=c+direction*(r+5.dp.toPx()+reach*envelope[band].pow(1.7f));val ink=lerp(colors[0],colors[2],i/95f)
     drawLine(ink.copy(alpha=.12f),from,to,7.dp.toPx(),StrokeCap.Round);drawLine(ink,from,to,2.dp.toPx(),StrokeCap.Round)
    }
   }}
   3->{ scale(radioVisualExpansion(strength),pivot=c){
    for(layer in 3 downTo 0){val path=Path();val pulse=radioMandalaPulse(pulseBands,layer);val turn=radioMandalaTurn(drift,layer)
     fun point(a:Float):Offset{val local=radioLayerBand(pulseBands,layer%3,((a/(2*PI.toFloat())+layer*.17f)%1f)).pow(.7f)
      val wiggle=sin(a*(17+layer*3)+phase*(1.4f+layer*.21f))* .6f+sin(a*(29+layer*2)-phase*.93f)*.4f
      val radius=radioMandalaRadius(u,layer,a,(pulse*1.18f).coerceIn(0f,1f))+u*(.003f+pulse*.025f+local*.018f)*wiggle;return c+Offset(cos(a+turn),sin(a+turn))*radius}
     for(i in 0..256){val p=point(i/256f*2*PI.toFloat());if(i==0)path.moveTo(p.x,p.y)else path.lineTo(p.x,p.y)}
     path.close();val ink=hue(layer*55f);drawPath(path,ink.copy(alpha=.025f+pulse*.045f));glow(path,ink,(1.4f+pulse*1.1f).dp.toPx(),.8f)
     // Beads attach to this contour and rotate/pulse with the same layer, never a separate orbit.
     val beads=6+layer*2
     for(i in 0 until beads){val a=i*2*PI.toFloat()/beads;drawCircle(colors[0],u*(.0025f+pulse*.0035f),point(a))}
    }
    val bass=radioMandalaPulse(pulseBands,0)
    drawCircle(Brush.radialGradient(listOf(colors[1].copy(alpha=bass*.5f),Color.Transparent),center=c,radius=u*.10f),u*.10f,c)
   }}
   4->{ // Accelerating light tunnel with deterministic, independently drifting streaks.
    val horizon=Offset(w*(.5f+sin(phase*.43f)*.07f*power),h*(.42f+cos(phase*.31f)*.04f*power))
    for(lane in -7..7){val path=Path();val band=abs(lane)*4;val spread=lane/7f
     for(i in 0..32){val t=i/32f;val drift=sin(t*4+phase+lane)*level(band)*u*.16f*t;val x=horizon.x+spread*w*.85f*t*t+drift;val y=horizon.y+h*.68f*t*t;if(i==0)path.moveTo(x,y)else path.lineTo(x,y)}
     glow(path,hue(lane*29f+220f),1.2.dp.toPx(),.25f+level(band)*.6f)
    }
    for(i in 0 until 80){val seed=(sin(i*127.1+17.7)*43758.5453).let{it-floor(it)};val z=((motion*(.30+seed*.35)+seed)%1.0).toFloat();val a=(i*2.399963+sin(motion*.37+i)*.22*power).toFloat();val tail=(z-.035f-level(i%32)*.10f).coerceAtLeast(0f)
     fun point(depth:Float):Offset{val d=depth.pow(1.65f);return horizon+Offset(cos(a)*w*.85f,sin(a)*h*.95f)*d+Offset(sin(phase+i)*d*u*.045f*power,0f)}
     val ink=hue(i*19f+z*90);drawLine(ink.copy(alpha=(.2f+level(i%32)*.8f)*z),point(tail),point(z),(1f+z*3.4f).dp.toPx(),StrokeCap.Round)
    }
    drawCircle(Brush.radialGradient(listOf(hue(80f).copy(alpha=power*.55f),Color.Transparent),center=horizon,radius=u*.26f),u*.26f,horizon)
   }
   5->{ // Northern lights: folded translucent vertical curtains over a deep, starry night.
    for(i in 0 until 48){val x=((sin(i*73.7)*16713).let{it-floor(it)}).toFloat()*w;val y=((sin(i*31.9+9)*9511).let{it-floor(it)}).toFloat()*h*.72f;drawCircle(Color(0xFFCFDFF5).copy(alpha=.18f+.22f*(.5f+.5f*sin(i+phase*.3f))),if(i%9==0)1.2.dp.toPx()else .65.dp.toPx(),Offset(x,y))}
    for(layer in 2 downTo 0){val count=100;val points=Array(count+1){i->val t=i/count.toFloat();val fold=sin(t*7.5f+phase*.45f+layer*.7f)+.35f*sin(t*19-phase*.8f+layer);val band=t*31;val at=band.toInt();val response=level(at)+(level(minOf(31,at+1))-level(at))*(band-at)
      Offset(w*t,h*(.62f+layer*.065f)+fold*h*(.055f+power*.07f)-response*h*.07f)}
     val tops=Array(count+1){i->val t=i/count.toFloat();val shimmer=.45f+.55f*(.5f+.5f*sin(t*45+phase*1.3f+layer));points[i]+Offset(sin(t*8+phase*.4f+layer)*w*.035f,-h*(.22f+power*.19f+shimmer*.11f))}
     for(i in 0 until count){val t=i/count.toFloat();val shimmer=.45f+.55f*(.5f+.5f*sin(t*45+phase*1.3f+layer));val a=points[i];val b=points[i+1];val overlap=.75.dp.toPx()
      val curtain=Path().apply{moveTo(a.x,a.y);lineTo(b.x+overlap,b.y);lineTo(tops[i+1].x+overlap,tops[i+1].y);lineTo(tops[i].x,tops[i].y);close()}
      val ink=Color.hsv(radioSceneHue(palette,t*160+layer*40,drift),.65f,1f)
      drawPath(curtain,Brush.verticalGradient(listOf(Color.Transparent,colors[0].copy(alpha=.08f+power*.09f),ink.copy(alpha=(.17f+power*.29f)*shimmer),Color.Transparent),startY=tops[i].y,endY=a.y+h*.045f))
     }
    }
    val ridge=Path().apply{moveTo(0f,h);lineTo(0f,h*.94f);for(i in 0..40){val x=i/40f*w;lineTo(x,h*(.93f-.035f*sin(i*.6f)-.012f*cos(i*1.7f)))};lineTo(w,h);close()};drawPath(ridge,Color(0xFF040914))
   }
  }
 }
}
