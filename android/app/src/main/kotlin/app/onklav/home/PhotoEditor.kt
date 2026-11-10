package app.onklav.home

import app.onklav.shared.OnKlavFileContracts

import android.graphics.*
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.res.painterResource
import app.onklav.shared.R

import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import kotlin.math.*

internal fun photoAccess(c:Context):Boolean=runCatching {
    val rows=AppSdk.sync(c,JSONObject().put("action","list")) as org.json.JSONArray
    (0 until rows.length()).map{rows.getJSONObject(it)}.any{row->row.optBoolean("installed") && row.optJSONObject("manifest")?.optString("id")=="app.onklav.photo" && row.optJSONArray("grants")?.let{a->(0 until a.length()).any{a.optString(it)=="storage.private"}}==true}
}.getOrDefault(false)

/** Parameter history rather than bitmap history keeps edits reversible and memory bounded. */
internal data class PhotoEdit(val values:Map<String,Float> = emptyMap(),val marks:List<PhotoMark> = emptyList(),val retouch:List<PhotoRetouchStroke> = emptyList()) {
    operator fun get(k:String):Float {
        if(PhotoLooks.removed(k))return 0f
        if(k=="colorMode")return when(PhotoLooks.key(this)){"blackWhite"->1f;"sepia"->2f;else->0f}
        if(k=="tealOrange"||k=="moody"||k=="noir")return if(PhotoLooks.key(this)==k)PhotoLooks.amount(this)else 0f
        return values[k]?:when(k){"scaleX","scaleY","gamma","saturation","contrast","white","colorizeSaturation","colorizeValue"->1f;"colorizeHue"->30f;else->0f}
    }
    fun set(k:String,v:Float):PhotoEdit {
        require(v.isFinite());if(PhotoLooks.removed(k))return this
        if(k=="colorMode")return PhotoLooks.choose(this,when(v.toInt()){1->"blackWhite";2->"sepia";else->"none"})
        if(k=="tealOrange"||k=="moody"||k=="noir")return if(v>0f)PhotoLooks.choose(this,k,v)else if(PhotoLooks.key(this)==k)PhotoLooks.choose(this,"none")else this
        return copy(values=values+(k to v))
    }
    fun json():String {PhotoMarkup.validate(marks);PhotoRetouch.validate(retouch);return JSONObject(values.mapValues{it.value.toDouble()}).apply{if(retouch.isNotEmpty())put("_retouch",org.json.JSONArray().apply{retouch.forEach{put(it.json())}});if(marks.isNotEmpty())put("_marks",org.json.JSONArray().apply{marks.forEach{put(it.json())}})}.toString()}
    companion object { fun parse(s:String):PhotoEdit {require(s.length<=4*1024*1024);val j=JSONObject(s);val a=j.optJSONArray("_marks");require(a==null||a.length()<=300)
        val marks=if(a==null)emptyList()else List(a.length()){PhotoMark.parse(a.getJSONObject(it))};PhotoMarkup.validate(marks)
        val fixes=j.optJSONArray("_retouch");require(fixes==null||fixes.length()<=80);val retouch=if(fixes==null)emptyList()else List(fixes.length()){PhotoRetouchStroke.parse(fixes.getJSONObject(it))};PhotoRetouch.validate(retouch)
        return PhotoEdit(PhotoLooks.migrate(j.keys().asSequence().filter{it!="_marks"&&it!="_retouch"}.associateWith{j.getDouble(it).toFloat().also{v->require(v.isFinite())}}),marks,retouch) } }
}
internal object PhotoRender {
    fun geometry(src:Bitmap,e:PhotoEdit):Bitmap {
        val crop=photoCropBounds(src.width,src.height,e)
        val mapping=photoGeometry(src.width,src.height,e)
        val out=Bitmap.createBitmap(crop.width(),crop.height(),Bitmap.Config.ARGB_8888)
        val matrix=Matrix(mapping.matrix).apply{postTranslate(-crop.left.toFloat(),-crop.top.toFloat())}
        Canvas(out).drawBitmap(src,matrix,Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        return out
    }

    fun render(src:Bitmap,e:PhotoEdit,onRetouch:((Int)->Unit)?=null):Bitmap {
        val corrected=if(e.retouch.isEmpty())null else src.copy(Bitmap.Config.ARGB_8888,true).also{try{val changed=PhotoRetouch.apply(it,e.retouch);onRetouch?.invoke(changed)}catch(t:Throwable){it.recycle();throw t}}
        val out=try{geometry(corrected?:src,e)}finally{corrected?.recycle()};PhotoArtEffects.reduceNoise(out,e["denoise"]);val w=out.width;val h=out.height;val pixels=IntArray(w);val look=PhotoLooks.plan(e)
        val hue=e["hue"]*Math.PI/180;val cs=cos(hue).toFloat();val sn=sin(hue).toFloat();val exposure=2.0.pow(e["exposure"].toDouble()).toFloat()
        val low=e["black"];val high=max(low+.02f,e["white"]);val gamma=1f/e["gamma"].coerceAtLeast(.2f)
        fun channel(v:Float):Float=(((v*exposure-low)/(high-low)).coerceIn(0f,1f).pow(gamma)-.5f)*e["contrast"]+.5f+e["brightness"]*.3f
        for(y in 0 until h){out.getPixels(pixels,0,w,0,y,w,1);for(x in 0 until w){val i=x;val p=pixels[i];var r=channel(Color.red(p)/255f)+e["warmth"]*.12f+e["tint"]*.06f;var g=channel(Color.green(p)/255f)-e["tint"]*.09f;var b=channel(Color.blue(p)/255f)-e["warmth"]*.12f+e["tint"]*.06f
            val lum=.299f*r+.587f*g+.114f*b;val u=(b-lum)*.492f;val v=(r-lum)*.877f;val uu=u*cs-v*sn;val vv=u*sn+v*cs;if(e["hue"]!=0f){r=lum+1.14f*vv;g=lum-.395f*uu-.581f*vv;b=lum+2.033f*uu}
            val sat=e["saturation"];r=lum+(r-lum)*sat;g=lum+(g-lum)*sat;b=lum+(b-lum)*sat
            val distance=((x-w/2f)/(w/2f)).pow(2)+((y-h/2f)/(h/2f)).pow(2);val shade=1-e["vignette"]*(distance/2).coerceIn(0f,1f)
            pixels[i]=PhotoLooks.apply(r*shade,g*shade,b*shade,Color.alpha(p),distance,look)
        }
        out.setPixels(pixels,0,w,0,y,w,1)}
        PhotoEffects.sharpen(out,e["sharpness"]);PhotoMarkup.drawOnPhoto(Canvas(out),e,src.width,src.height)
        return out
    }

    fun load(context:Context,file:File,max:Int):Bitmap {
        val opts=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(file.path,opts);require(opts.outWidth>0&&opts.outHeight>0){"This image format is not supported"}
        opts.inJustDecodeBounds=false;opts.inSampleSize=1;while(opts.outWidth/opts.inSampleSize>max||opts.outHeight/opts.inSampleSize>max)opts.inSampleSize*=2
        val raw=BitmapFactory.decodeFile(file.path,opts)?:error("Unable to open this image")
        val exif=android.media.ExifInterface(file.path);val m=Matrix();when(exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1)){2->m.setScale(-1f,1f);3->m.setRotate(180f);4->m.setScale(1f,-1f);5->{m.setRotate(90f);m.postScale(-1f,1f)};6->m.setRotate(90f);7->{m.setRotate(270f);m.postScale(-1f,1f)};8->m.setRotate(270f)}
        val result=Bitmap.createBitmap(raw,0,0,raw.width,raw.height,m,true);if(result!==raw)raw.recycle();return result
    }
}
internal class PhotoEditorModel(app:android.app.Application):AndroidViewModel(app){
    val source=File(app.filesDir,"photo-editor/source");private val draft=File(app.filesDir,"photo-editor/edits.json")
    var edit by mutableStateOf(runCatching{PhotoEdit.parse(draft.readText())}.getOrDefault(PhotoEdit()));private set
    var preview by mutableStateOf<Bitmap?>(null);private set
    var original by mutableStateOf<Bitmap?>(null);private set
    var busy by mutableStateOf(false);private set
    var feedback by mutableStateOf("");private set
    var origin by mutableStateOf(runCatching{JSONObject(File(app.filesDir,"photo-editor/origin.json").readText())}.getOrNull());private set
    val canReplace get()=origin?.optBoolean("writable")==true&&!File(source.parentFile,"opening.json").exists()
    val previousInfo get()=runCatching{JSONObject(File(source.parentFile,"previous-info.json").readText())}.getOrNull()
    val canRecoverPrevious get()=File(source.parentFile,"previous-image").isFile&&previousInfo?.optString("mime") in setOf("image/jpeg","image/png")
    private val undo= mutableStateListOf<PhotoEdit>();private val redo=mutableStateListOf<PhotoEdit>();private var committed=edit
    val canUndo get()=undo.isNotEmpty();val canRedo get()=redo.isNotEmpty()
    private var renderJob:Job?=null
    private var loadGeneration=0
    private val renderDispatcher=Dispatchers.Default.limitedParallelism(1)
    init{viewModelScope.launch{busy=true;try{withContext(Dispatchers.IO){authorize();PhotoImageSave.recoverOpening(getApplication(),::authorize);PhotoImageSave.recover(getApplication(),::authorize)}
        origin=runCatching{JSONObject(File(source.parentFile,"origin.json").readText())}.getOrNull();edit=runCatching{PhotoEdit.parse(draft.readText())}.getOrDefault(PhotoEdit());committed=edit
        runCatching{File(source.parentFile,"history.json").let{require(it.length()<=8*1024*1024);PhotoLayers.parse(it.readText())}}.getOrNull()?.takeIf{it.edit==edit}?.let{undo.addAll(it.undo);redo.addAll(it.redo)}
    }catch(e:CancellationException){throw e}catch(e:Exception){origin=null;feedback="An interrupted save needs attention. Your previous image is retained."}finally{busy=false;if(source.exists())reload()}}}

    fun change(k:String,v:Float){edit=edit.set(k,v);render()}
    fun crop(e:PhotoEdit){edit=e;render()}
    fun commit(){if(edit!=committed){val beforeUndo=undo.toList();val beforeRedo=redo.toList();undo.add(committed);while(undo.size>60||undo.sumOf{it.json().length}>2*1024*1024)undo.removeAt(0);redo.clear();if(save())committed=edit else{undo.clear();undo.addAll(beforeUndo);redo.addAll(beforeRedo)}}}
    fun apply(e:PhotoEdit){PhotoMarkup.validate(e.marks);PhotoRetouch.validate(e.retouch);edit=e;commit();render()}
    fun retouch(stroke:PhotoRetouchStroke){val next=edit.copy(retouch=edit.retouch+stroke);PhotoRetouch.validate(next.retouch);edit=next;commit();render(stroke.tool)}
    fun history(back:Boolean){commit();if(edit!=committed)return;val a=if(back)undo else redo;val b=if(back)redo else undo;if(a.isEmpty())return;b.add(edit);edit=a.removeAt(a.lastIndex);committed=edit;save();render()}
    private fun authorize(){check(photoAccess(getApplication())){"Image Edit storage access is off. Restore it in Settings > Apps > Image Edit."}}
    private fun save():Boolean=runCatching{authorize();draft.parentFile?.mkdirs();PhotoImageSave.atomic(draft,JSONObject(edit.json()));PhotoImageSave.atomic(File(source.parentFile,"history.json"),PhotoLayers.State(edit,undo.toList(),redo.toList()).json())}.onFailure{feedback="Unable to save draft. Keep this image open and save a copy before closing."}.isSuccess
    fun open(uri:Uri){if(busy)return;commit();if(edit!=committed)return;loadGeneration++;renderJob?.cancel();viewModelScope.launch{
      busy=true;feedback="Opening image…";var pendingBitmap:Bitmap?=null;var project:PhotoLayers.State?=null
      try{
        val info=withContext(Dispatchers.IO){authorize();PhotoImageSave.recoverOpening(getApplication(),::authorize);PhotoImageSave.recover(getApplication(),::authorize);source.parentFile?.mkdirs();val tmp=File(source.parentFile,"incoming")
          try{getApplication<android.app.Application>().contentResolver.openInputStream(uri).use{input->requireNotNull(input);tmp.outputStream().use{out->FilesBroker.copyBoundedLimit(input,out,150_000_000,::authorize)}}
            if(PhotoLayers.isProject(tmp)){val image=File(source.parentFile,"incoming-image");try{project=PhotoLayers.read(tmp,image,::authorize);check(image.renameTo(tmp))}finally{image.delete()}}
            pendingBitmap=PhotoRender.load(getApplication(),tmp,1200)
            val metadata=PhotoImageSave.origin(getApplication(),uri,tmp);if(project!=null){metadata.put("writable",false).put("layers",true);PhotoImageSave.atomic(File(source.parentFile,"opening-project.json"),project!!.json())};PhotoImageSave.atomic(File(source.parentFile,"opening.json"),metadata)
            authorize();check(tmp.renameTo(source)){"Unable to open this image safely"};metadata
          }finally{tmp.delete()}
        }
        origin=info;edit=project?.edit?:PhotoEdit();committed=edit;undo.clear();redo.clear();project?.let{undo.addAll(it.undo);redo.addAll(it.redo)};original=requireNotNull(pendingBitmap);pendingBitmap=null;preview=null;render()
        withContext(Dispatchers.IO){authorize();PhotoImageSave.atomic(File(source.parentFile,"origin.json"),info);PhotoImageSave.atomic(draft,JSONObject(edit.json()));PhotoImageSave.atomic(File(source.parentFile,"history.json"),PhotoLayers.State(edit,undo.toList(),redo.toList()).json());check(File(source.parentFile,"opening.json").delete());File(source.parentFile,"opening-project.json").delete()}
        feedback=""
      }catch(e:CancellationException){throw e}catch(e:Exception){feedback=e.message?:"Unable to open image"}finally{pendingBitmap?.recycle();busy=false}
    }}

    private fun reload(){val generation=++loadGeneration;viewModelScope.launch{try{val fresh=withContext(Dispatchers.Default){authorize();PhotoRender.load(getApplication(),source,1200)};if(generation!=loadGeneration){fresh.recycle();return@launch};original=fresh;render()}catch(e:Exception){feedback=e.message?:"Unable to restore image"}}}
    private fun render(retouchTool:String?=null){renderJob?.cancel();val input=original?:return;val state=edit;renderJob=viewModelScope.launch{delay(70);var pending:Bitmap?=null;try{var changed=0;withContext(renderDispatcher){pending=PhotoRender.render(input,state){changed=it}};ensureActive();preview=pending;pending=null;if(retouchTool!=null&&edit==state&&edit==committed){feedback=if(changed==0)when(retouchTool){"redEye"->"No red pupils found here · zoom in and brush over the pupil";"clone"->"No change · sample a different area";else->"No suitable nearby texture · try a smaller brush or Clone"}else when(retouchTool){"redEye"->"Red eye corrected · Undo available";"clone"->"Clone applied · Undo available";else->"Spot healed · Undo available"}}}catch(e:CancellationException){throw e}catch(e:OutOfMemoryError){feedback="Not enough memory to preview. Try a smaller image."}catch(e:Exception){feedback=e.message?:"Unable to render"}finally{pending?.recycle()}}}
    private fun encoded(state:PhotoEdit,png:Boolean,quality:Int):File {
        authorize();val encoded=File.createTempFile("photo-save-",if(png)".png"else".jpg",getApplication<android.app.Application>().cacheDir)
        try{val full=PhotoRender.load(getApplication(),source,4000);try{val rendered=PhotoRender.render(full,state);try{
          FileOutputStream(encoded).use{stream->if(!png){val flat=Bitmap.createBitmap(rendered.width,rendered.height,Bitmap.Config.ARGB_8888);try{Canvas(flat).apply{drawColor(Color.WHITE);drawBitmap(rendered,0f,0f,null)};check(flat.compress(Bitmap.CompressFormat.JPEG,quality,stream))}finally{flat.recycle()}}else check(rendered.compress(Bitmap.CompressFormat.PNG,100,stream));stream.fd.sync()}
        }finally{rendered.recycle()}}finally{full.recycle()};authorize();return encoded}catch(t:Throwable){encoded.delete();throw t}
    }
    fun saveChanges(){if(busy||!canReplace)return;commit();val state=edit;val target=origin?.let{JSONObject(it.toString())}?:return
        viewModelScope.launch{busy=true;feedback="Saving changes…";try{val saved=withContext(renderDispatcher){val file=encoded(state,target.getString("mime")=="image/png",95);try{PhotoImageSave.replace(getApplication(),target,file,::authorize)}finally{file.delete()}};origin=saved;withContext(Dispatchers.IO){PhotoImageSave.atomic(File(source.parentFile,"origin.json"),saved)};feedback="Changes saved · previous version kept for recovery"}
        catch(e:CancellationException){throw e}catch(e:OutOfMemoryError){feedback="Not enough memory. Try a smaller image."}catch(e:Exception){feedback="Changes were not saved: ${e.message}. You can save a copy instead."}finally{busy=false}}
    }
    fun exportPrevious(uri:Uri){if(busy||!canRecoverPrevious)return;viewModelScope.launch{busy=true;feedback="Saving previous version…";try{withContext(Dispatchers.IO){authorize();val previous=File(source.parentFile,"previous-image");writeFiledExport(getApplication(),uri){out->previous.inputStream().use{FilesBroker.copyBoundedLimit(it,out,FilesBroker.MAX_FILE,::authorize)}}};awaitFiled(uri);feedback="Previous version saved"}catch(e:CancellationException){throw e}catch(e:Exception){feedback="Unable to save previous version: ${e.message}"}finally{busy=false}}}
    private suspend fun awaitFiled(uri:Uri)=awaitFiledExport(getApplication(),uri)
    fun discard(onClosed:()->Unit){if(busy)return;renderJob?.cancel();loadGeneration++;viewModelScope.launch{busy=true
      try{withContext(Dispatchers.IO){authorize();PhotoImageSave.recoverOpening(getApplication(),::authorize);PhotoImageSave.recover(getApplication(),::authorize)
        for(name in listOf("edits.json","history.json","origin.json","source")){authorize();val file=File(source.parentFile,name);check(!file.exists()||file.delete()){ "Unable to discard this working image" }}
      };edit=PhotoEdit();committed=edit;undo.clear();redo.clear();original=null;preview=null;origin=null;feedback="";busy=false;onClosed()
      }catch(e:CancellationException){throw e}catch(e:Exception){feedback=e.message?:"Unable to close image"}finally{busy=false}}
    }
    fun exportLayers(uri:Uri){if(busy)return;commit();val state=PhotoLayers.State(edit,undo.toList(),redo.toList());viewModelScope.launch{busy=true;feedback="Saving editable layers…";val file=File.createTempFile("photo-project-",".layers",getApplication<android.app.Application>().cacheDir)
      try{withContext(Dispatchers.IO){authorize();PhotoLayers.write(file,source,state,::authorize);writeFiledExport(getApplication(),uri){out->file.inputStream().use{FilesBroker.copyBoundedLimit(it,out,FilesBroker.MAX_FILE,::authorize)}}};awaitFiled(uri);feedback="Layers saved · reopen to continue editing"}catch(e:CancellationException){throw e}catch(e:Exception){feedback="Unable to save layers: ${e.message}"}finally{file.delete();busy=false}}
    }
    fun export(uri:Uri,png:Boolean,quality:Int){if(busy)return;commit();val state=edit;viewModelScope.launch{busy=true;feedback="Saving copy…";try{
      withContext(renderDispatcher){val file=encoded(state,png,quality);try{authorize();writeFiledExport(getApplication(),uri){out->file.inputStream().use{FilesBroker.copyBoundedLimit(it,out,FilesBroker.MAX_FILE,::authorize)}}}finally{file.delete()}}
      awaitFiled(uri)
      feedback="Copy saved"
    }catch(e:CancellationException){throw e}catch(e:OutOfMemoryError){feedback="Not enough memory. Try a smaller image."}catch(e:Exception){feedback="Unable to save: ${e.message}"}finally{busy=false}}}

}



internal val photoGeometryKeys=setOf("angle","turn","flipX","flipY","scaleX","scaleY","perspectiveX","perspectiveY","moveX","moveY")
internal data class PhotoGeometry(val matrix:Matrix,val corners:List<Offset>,val width:Int,val height:Int)
/** Image coordinates change inside a fixed source canvas. Crop changes only the output window. */
internal fun photoGeometry(w:Int,h:Int,e:PhotoEdit):PhotoGeometry {
    require(w>0&&h>0&&w.toLong()*h<=16_000_000)
    val px=e["perspectiveX"].coerceIn(-1f,1f)*.35f;val py=e["perspectiveY"].coerceIn(-1f,1f)*.35f
    val fw=w.toFloat();val fh=h.toFloat()
    val from=floatArrayOf(0f,0f,fw,0f,fw,fh,0f,fh)
    val to=floatArrayOf(max(0f,py)*fw,max(0f,px)*fh,fw-max(0f,py)*fw,max(0f,-px)*fh,fw-max(0f,-py)*fw,fh-max(0f,-px)*fh,max(0f,-py)*fw,fh-max(0f,px)*fh)
    val m=Matrix();check(m.setPolyToPoly(from,0,to,0,4)){"This perspective cannot be applied"}
    m.postTranslate(-fw/2,-fh/2)
    m.postScale(e["scaleX"].coerceIn(.1f,3f)*(if(e["flipX"]==1f)-1f else 1f),e["scaleY"].coerceIn(.1f,3f)*(if(e["flipY"]==1f)-1f else 1f))
    m.postRotate(e["turn"]+e["angle"])
    m.postTranslate(fw/2+e["moveX"].coerceIn(-1f,1f)*fw,fh/2+e["moveY"].coerceIn(-1f,1f)*fh)
    val points=from.copyOf();m.mapPoints(points)
    return PhotoGeometry(m,(0..3).map{Offset(points[it*2],points[it*2+1])},w,h)
}

internal fun fitPhotoImage(w:Int,h:Int,e:PhotoEdit,fill:Boolean=false):PhotoEdit {
    val base=e.set("scaleX",1f).set("scaleY",1f).set("moveX",0f).set("moveY",0f)
    val corners=photoGeometry(w,h,base).corners
    val left=corners.minOf{it.x};val right=corners.maxOf{it.x};val top=corners.minOf{it.y};val bottom=corners.maxOf{it.y}
    val crop=photoCropBounds(w,h,e)
    val scale=(if(fill)max(crop.width()/(right-left),crop.height()/(bottom-top))else min(crop.width()/(right-left),crop.height()/(bottom-top))).coerceIn(.1f,3f)
    return base.set("scaleX",scale).set("scaleY",scale).set("moveX",(crop.exactCenterX()-w/2f-((left+right)/2-w/2f)*scale)/w).set("moveY",(crop.exactCenterY()-h/2f-((top+bottom)/2-h/2f)*scale)/h)
}
internal fun photoGeometryDrag(start:PhotoEdit,key:String,handle:Int,origin:Offset,point:Offset,center:Offset,unit:Float):PhotoEdit {
    if(key=="angle"||handle==8){
        val a=atan2(point.y-center.y,point.x-center.x)-atan2(origin.y-center.y,origin.x-center.x)
        var angle=start["angle"]+a*180f/PI.toFloat();while(angle>180)angle-=360;while(angle< -180)angle+=360
        return start.set("angle",if(abs(angle)<1f)0f else angle)
    }
    if(key.startsWith("perspective")){
        val delta=(point-origin)/unit.coerceAtLeast(1f)
        val right=handle in listOf(1,2,5);val bottom=handle in listOf(2,3,6)
        var out=start
        if(key!="perspectiveX")out=out.set("perspectiveY",(start["perspectiveY"]+delta.x*(if(right)-2f else 2f)).coerceIn(-1f,1f))
        if(key!="perspectiveY")out=out.set("perspectiveX",(start["perspectiveX"]+delta.y*(if(bottom)-2f else 2f)).coerceIn(-1f,1f))
        return out
    }
    val a=origin-center;val b=point-center
    val ratio=(b.getDistance()/a.getDistance().coerceAtLeast(1f)).coerceIn(.05f,20f)
    var out=start
    val width=key=="scaleX"|| (key=="scale"&&handle in listOf(5,7))
    val height=key=="scaleY"|| (key=="scale"&&handle in listOf(4,6))
    if(!height)out=out.set("scaleX",(start["scaleX"]*ratio).coerceIn(.1f,2f))
    if(!width)out=out.set("scaleY",(start["scaleY"]*ratio).coerceIn(.1f,2f))
    return out
}

internal class PhotoToolbarState {
    var enabled by mutableStateOf(false);var canSave by mutableStateOf(false);var canRestore by mutableStateOf(false)
    var open:()->Unit={};var save:()->Unit={};var restore:()->Unit={}
}
@Composable internal fun PhotoHeaderButtons(state:PhotoToolbarState){
    PhotoAction("Restore original image",R.drawable.audio_edit_restartalt,state.canRestore){state.restore()}
    PhotoAction("Open image",R.drawable.maps_folder,state.enabled){state.open()}
    IconButton(enabled=state.canSave,onClick={state.save()}){Icon(painterResource(R.drawable.photo_save),"Save image")}
}

@Composable internal fun PhotoDefaultSlider(label:String,value:Float,default:Float,range:ClosedFloatingPointRange<Float>,enabled:Boolean=true,onChange:(Float)->Unit,onFinish:()->Unit,step:Float?=null,format:(Float)->String={"%.2f".format(it)}){
    val neutral=abs(value-default)<.001f;val tint=if(neutral)MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
    val marker=MaterialTheme.colorScheme.onSurfaceVariant
    val sliderIcon=when {
        label.contains("Text",true)->R.drawable.photo_tool_text
        label.contains("Brush",true)||label.contains("Stroke",true)->R.drawable.photo_tool_pen
        label.contains("Hardness",true)->R.drawable.photo_tool_retouch
        label.contains("quality",true)->R.drawable.photo_save
        label.contains("hue",true)||label.contains("saturation",true)||label.contains("tint",true)||label.contains("warmth",true)->R.drawable.photo_edit_color
        label.contains("strength",true)->R.drawable.photo_edit_effects
        label.contains("angle",true)||label.contains("rotate",true)||label.contains("scale",true)||label.contains("offset",true)||label.contains("perspective",true)->R.drawable.photo_edit_geometry
        else->R.drawable.audio_edit_graphiceq
    }
    Row(Modifier.height(44.dp),verticalAlignment=Alignment.CenterVertically){
        Icon(painterResource(sliderIcon),label,Modifier.padding(end=6.dp).size(18.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
        if(step!=null)IconButton(enabled=enabled&&value>range.start,onClick={onChange((value-step).coerceIn(range));onFinish()},modifier=Modifier.size(36.dp)){Icon(painterResource(R.drawable.photo_minus),"Decrease $label",Modifier.size(18.dp))}
        Box(Modifier.weight(1f)){
            Slider(modifier=Modifier.fillMaxWidth().testTag("photo-adjustment-slider").semantics{contentDescription=label},enabled=enabled,value=value.coerceIn(range),onValueChange={val threshold=step?.times(.45f)?:((range.endInclusive-range.start)*.015f);val v=if(abs(it-default)<threshold)default else if(step!=null)(round(it/step)*step).coerceIn(range)else it;onChange(v)},onValueChangeFinished=onFinish,valueRange=range,colors=SliderDefaults.colors(thumbColor=tint,activeTrackColor=tint))
            androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(44.dp).semantics{contentDescription="Original position: $default"}){val inset=12.dp.toPx();val x=inset+(size.width-inset*2)*(default-range.start)/(range.endInclusive-range.start);drawLine(marker,Offset(x,size.height/2+9.dp.toPx()),Offset(x,size.height/2+14.dp.toPx()),2.dp.toPx())}
        }
        Text(format(value),Modifier.width(if(step!=null)62.dp else 44.dp),style=MaterialTheme.typography.labelSmall)
        if(step!=null)IconButton(enabled=enabled&&value<range.endInclusive,onClick={onChange((value+step).coerceIn(range));onFinish()},modifier=Modifier.size(36.dp)){Icon(Icons.Outlined.Add,"Increase $label",Modifier.size(18.dp))}
        IconButton(modifier=Modifier.size(36.dp),enabled=enabled&&!neutral,onClick={onChange(default);onFinish()}){Icon(painterResource(R.drawable.audio_edit_restartalt),"Reset $label",Modifier.size(18.dp))}
    }
}

/** Crop coordinates refer to the untransformed source, including its EXIF orientation. */
internal fun movePhotoCrop(e:PhotoEdit,handle:String,dx:Float,dy:Float):PhotoEdit {
    var l=e["left"];var t=e["top"];var r=1-e["right"];var b=1-e["bottom"]
    val minimum=.05f
    if(handle=="move"){
        val x=dx.coerceIn(-l,1-r);val y=dy.coerceIn(-t,1-b)
        l+=x;r+=x;t+=y;b+=y
    }else{
        if('l' in handle)l=(l+dx).coerceIn(0f,r-minimum)
        if('r' in handle)r=(r+dx).coerceIn(l+minimum,1f)
        if('t' in handle)t=(t+dy).coerceIn(0f,b-minimum)
        if('b' in handle)b=(b+dy).coerceIn(t+minimum,1f)
    }
    return e.set("left",l).set("right",1-r).set("top",t).set("bottom",1-b)
}

@Composable private fun PhotoAction(label:String,icon:Int,enabled:Boolean=true,onClick:()->Unit){
    IconButton(enabled=enabled,onClick=onClick){Icon(painterResource(icon),label)}
}

@Composable internal fun PhotoEditorWorkspace(toolbar:PhotoToolbarState?=null,onClose:()->Unit={}){
    val context=LocalContext.current
    val epoch by AppSdk.epoch.collectAsState()
    val allowed=remember(epoch){photoAccess(context)}
    if(!allowed){Text("Image Edit storage access is off. Restore it in Settings > Apps > Image Edit.",Modifier.padding(24.dp));return}
    val model:PhotoEditorModel=viewModel(key="photo-editor-$epoch")
    var tab by remember{mutableStateOf("Crop")};var selected by remember{mutableStateOf("angle")};var sliderEpoch by remember{mutableIntStateOf(0)}
    var chooser by remember{mutableStateOf<String?>(null)}
    var export by remember{mutableStateOf(false)};var saveChanges by remember{mutableStateOf(false)};var replace by remember{mutableStateOf(false)};var reset by remember{mutableStateOf(false)}
    var layers by remember{mutableStateOf(false)}
    var retouch by remember{mutableStateOf(false)}
    val retouchTools=remember(model.original){PhotoRetouchState()}
    var png by remember{mutableStateOf(false)};var quality by remember{mutableFloatStateOf(95f)};var compare by remember{mutableStateOf(false)};var zoom by remember{mutableFloatStateOf(1f)};var pan by remember{mutableStateOf(Offset.Zero)}
    val picker=rememberLauncherForActivityResult(OnKlavFileContracts.OpenDocument()){it?.let{zoom=1f;pan=Offset.Zero;model.open(it)}}
    val saveLayers=rememberLauncherForActivityResult(OnKlavFileContracts.CreateDocument(PhotoLayers.MIME)){it?.let(model::exportLayers)}
    val savePng=rememberLauncherForActivityResult(OnKlavFileContracts.CreateDocument("image/png")){it?.let{model.export(it,true,100)}}
    val savePrevious=rememberLauncherForActivityResult(FileExportContract()){it?.let{model.exportPrevious(it)}}
    val saveJpeg=rememberLauncherForActivityResult(OnKlavFileContracts.CreateDocument("image/jpeg")){it?.let{model.export(it,false,quality.toInt())}}
    val e=model.edit
    val markTools=remember(model.original){PhotoMarkupState()}
    val markupFocus=androidx.compose.ui.platform.LocalFocusManager.current;val markupKeyboard=androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val textIme=tab=="Tools"&&!retouch&&markTools.tool=="text"&&WindowInsets.ime.getBottom(LocalDensity.current)>0
    fun flushMarkup():Boolean{
        if(markTools.tool!="text"||markTools.text.isBlank())return true
        return runCatching{
            check(markTools.editing==null||model.edit.marks.any{it.id==markTools.editing&&it.tool=="text"}){"This text changed. Select it again."}
            val src=requireNotNull(model.original);val mark=markTools.note(src.width,src.height);mark.validate()
            val marks=if(markTools.editing==null)model.edit.marks+mark else model.edit.marks.map{if(it.id==markTools.editing)mark else it}
            model.apply(model.edit.copy(marks=marks));markTools.selected=mark.id;markTools.finish();markTools.tool="move";markupFocus.clearFocus();markupKeyboard?.hide()
        }.onFailure{markTools.error=it.message;tab="Tools";compare=false}.isSuccess
    }
    val toolsBase by produceState<Triple<Bitmap,PhotoEdit,Bitmap>?>(null,model.original,e.copy(marks=emptyList()),tab=="Tools"&&!retouch){
        value=null;if(tab=="Tools"&&!retouch){val src=model.original;val base=e.copy(marks=emptyList());value=if(src!=null)withContext(Dispatchers.Default){Triple(src,base,PhotoRender.render(src,base))}else null}
    }
    val originalCanvas by produceState<Bitmap?>(null,model.original,PhotoEdit(e.values.filterKeys{it in photoCropKeys})){val src=model.original;value=if(src!=null)withContext(Dispatchers.Default){PhotoRender.geometry(src,PhotoEdit(e.values.filterKeys{it in photoCropKeys}))}else null}
    val image=if(compare)originalCanvas else model.preview
    val enabled=model.original!=null&&!model.busy&&!compare

    LaunchedEffect(tab,e.values["look"]){if(tab=="Effects"&&PhotoLooks.controls.any{it.first==selected})selected=PhotoLooks.key(e)}
    val controls=when(tab){"Geometry"->listOf("angle" to "Rotate","scale" to "Scale","scaleX" to "Width scale","scaleY" to "Height scale","perspectiveX" to "Horizontal perspective","perspectiveY" to "Vertical perspective");"Color"->listOf("exposure" to "Exposure","brightness" to "Brightness","contrast" to "Contrast","saturation" to "Saturation","hue" to "Hue","warmth" to "Warmth","tint" to "Color cast","black" to "Black point","white" to "White point","gamma" to "Midtones");else->PhotoLooks.controls+listOf("sharpness" to "Sharpen","denoise" to "Reduce noise","vignette" to "Vignette")}
    Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.surface){
    Column(Modifier.fillMaxSize().padding(horizontal=8.dp)){
        val openImage:()->Unit={if(model.original!=null)replace=true else picker.launch(arrayOf("image/*",PhotoLayers.MIME))}
        if(toolbar==null)Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){PhotoAction("Restore original image",R.drawable.audio_edit_restartalt,enabled){model.commit();reset=true};PhotoAction("Open image",R.drawable.maps_folder,!model.busy,openImage);IconButton(enabled=model.original!=null&&!model.busy,onClick={if(flushMarkup()){model.commit();export=true}}){Icon(painterResource(R.drawable.photo_save),"Save image")}}
        else {
            SideEffect{toolbar.enabled=!model.busy;toolbar.canRestore=enabled;toolbar.restore={model.commit();reset=true};toolbar.canSave=model.original!=null&&!model.busy;toolbar.open=openImage;toolbar.save={if(flushMarkup()){model.commit();export=true}}}
            DisposableEffect(toolbar){onDispose{toolbar.enabled=false;toolbar.canSave=false;toolbar.canRestore=false;toolbar.open={};toolbar.save={};toolbar.restore={}}}
        }
        Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow),contentAlignment=Alignment.Center){
            if(model.original!=null&&tab=="Crop"&&!compare)PhotoCropFrame(model.original!!,e,enabled,model::crop,model::commit)
            else if(tab=="Tools"&&!compare&&retouch){
                if(image!=null)PhotoRetouchCanvas(image,e,model.original!!.width,model.original!!.height,retouchTools,enabled,zoom,pan,{z,p->zoom=z;pan=p}){stroke->model.retouch(stroke)}
                else CircularProgressIndicator()
            }
            else if(tab=="Tools"&&!compare){
                val base=toolsBase;val src=model.original
                if(base!=null&&src!=null&&base.first===src&&base.second==e.copy(marks=emptyList()))PhotoMarkupCanvas(base.third,e,src.width,src.height,markTools,enabled,zoom,pan,{z,p->zoom=z;pan=p}){marks->model.apply(e.copy(marks=marks))}
                else if(src!=null)CircularProgressIndicator()
            }
            else if(image!=null)PhotoCanvasPreview(image,e,tab=="Geometry",zoom,pan,
                onView={z,p->zoom=z;pan=p},editable=enabled,sourceWidth=model.original?.width?:image.width,sourceHeight=model.original?.height?:image.height,
                onGeometry=model::crop,onFinish=model::commit)
            else if(model.original!=null)CircularProgressIndicator()
            else Column(horizontalAlignment=Alignment.CenterHorizontally){Icon(painterResource(R.drawable.ref_app_photo),null,Modifier.size(48.dp),tint=MaterialTheme.colorScheme.primary);Text("Image Edit",style=MaterialTheme.typography.titleLarge);TextButton(onClick={picker.launch(arrayOf("image/*",PhotoLayers.MIME))}){Text("Choose an image")}}
            if(model.busy)CircularProgressIndicator()
        }
        if(model.feedback.isNotBlank())Text(model.feedback,Modifier.padding(4.dp).semantics{liveRegion=LiveRegionMode.Polite},style=MaterialTheme.typography.bodySmall)
        if(model.original!=null){
            if(!textIme)Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                Box(Modifier.weight(1f)){
                    if(tab=="Tools")Text(if(retouch)"Retouch"else when(markTools.tool){"pen"->"Pen";"highlight"->"Highlighter";"text"->"Text";"move"->"Move";else->"Eraser"},Modifier.padding(horizontal=12.dp),style=MaterialTheme.typography.titleSmall)
                    else FilledTonalButton(modifier=Modifier.fillMaxWidth(),enabled=enabled,shape=RoundedCornerShape(12.dp),contentPadding=PaddingValues(horizontal=10.dp),onClick={chooser=if(tab=="Crop")"Ratios" else "Adjustments"}){
                        Text(if(tab=="Crop")"Ratio" else controls.firstOrNull{it.first==selected}?.second?:"Tools",Modifier.weight(1f),maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Icon(painterResource(R.drawable.photo_edit_chevron),null,Modifier.size(16.dp))
                    }

                }
                PhotoAction("Undo",R.drawable.audio_edit_undo,model.canUndo&&enabled){markTools.finish();markTools.selected=null;model.history(true);sliderEpoch++}
                PhotoAction("Redo",R.drawable.audio_edit_redo,model.canRedo&&enabled){markTools.finish();markTools.selected=null;model.history(false);sliderEpoch++}
                IconToggleButton(checked=compare,onCheckedChange={compare=it},enabled=!model.busy,modifier=Modifier.semantics{contentDescription=if(compare)"Show edited image"else"Show original image";stateDescription=if(compare)"Original"else"Edited"}){Icon(Icons.Outlined.Visibility,null,tint=if(compare)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)}
                PhotoAction("Reset zoom",R.drawable.video_edit_fullscreen,!model.busy){zoom=1f;pan=Offset.Zero}
            }
            if(tab=="Tools"){
                PhotoMarkupPanel(markTools,e,enabled,model.original!!.width,model.original!!.height,beforeToolChange={if(flushMarkup()){retouch=false;true}else false},onRetouch={if(flushMarkup()){retouch=true;compare=false}},retouchActive=retouch){marks->model.apply(e.copy(marks=marks))}
                if(retouch)PhotoRetouchPanel(retouchTools,enabled)
            }
            if(tab=="Geometry"){
                val label=controls.firstOrNull{it.first==selected}?.second.orEmpty();val value=photoAdjustmentValue(e,selected)
                val range=when(selected){"angle"->-180f..180f;"scale","scaleX","scaleY"->.1f..3f;else->-1f..1f}
                PhotoDefaultSlider(label,value,photoAdjustmentValue(PhotoEdit(),selected),range,enabled,{model.crop(photoAdjustment(e,selected,it))},model::commit,step=if(selected=="angle").1f else .01f,format={v->if(selected=="angle")"%.1f°".format(v)else"%.0f%%".format(v*100)})

            }
            if(tab=="Color"||tab=="Effects"){
                val lookControl=tab=="Effects"&&PhotoLooks.controls.any{it.first==selected}
                val controlKey=if(lookControl)"lookStrength"else selected
                val range=when(controlKey){"contrast","saturation"->0f..2f;"gamma"->.2f..3f;"white"->.55f..1f;"black"->0f.. .45f;"exposure"->-3f..3f;"hue"->-180f..180f;"vignette","sharpness","lookStrength","denoise"->0f..1f;else->-1f..1f}
                if(tab=="Color"&&PhotoLooks.amount(e)>0f)Text("Color adjustments apply before ${PhotoLooks.label(e)}.",style=MaterialTheme.typography.labelSmall)
                val blockedColor=tab=="Color"&&selected in setOf("saturation","hue","warmth","tint")&&PhotoLooks.monochrome(e)
                if(lookControl&&PhotoLooks.key(e)=="none")Text("Choose one look. Selecting another replaces it.",style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(6.dp))
                else key(sliderEpoch,selected){PhotoDefaultSlider(if(lookControl)"Look strength"else controls.firstOrNull{it.first==selected}?.second.orEmpty(),e[controlKey],PhotoEdit()[controlKey],range,enabled&&!blockedColor,{model.change(controlKey,it)},model::commit,format={v->if(tab=="Effects")"${(v*100).roundToInt()}%"else"%.2f".format(v)})}
                if(blockedColor)Text("${PhotoLooks.label(e)} at 100% removes color. Lower its strength or choose No look in Effects.",style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(6.dp))
                if(lookControl&&PhotoLooks.key(e)=="colorize")TextButton(enabled=enabled,onClick={chooser="Colorize color"}){Text("Choose color · #"+Integer.toHexString(android.graphics.Color.HSVToColor(floatArrayOf(e["colorizeHue"],e["colorizeSaturation"],e["colorizeValue"]))).takeLast(6).uppercase())}
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                    Text(if(PhotoLooks.amount(e)>0f)"Look: ${PhotoLooks.label(e)} · ${(PhotoLooks.amount(e)*100).roundToInt()}%"else"Look: None",Modifier.weight(1f),style=MaterialTheme.typography.labelSmall)
                    if(PhotoLooks.key(e)!="none")TextButton(enabled=enabled,onClick={model.apply(PhotoLooks.choose(e,"none"));if(tab=="Effects"&&lookControl)selected="none"}){Text("Remove look")}
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically){
                listOf(Triple("Crop",R.drawable.audio_edit_crop,"angle"),Triple("Geometry",R.drawable.photo_edit_geometry,"angle"),Triple("Color",R.drawable.photo_edit_color,"exposure"),Triple("Effects",R.drawable.photo_edit_effects,"none"),Triple("Tools",R.drawable.photo_tool_pen,"pen")).forEach{(name,icon,key)->
                    val tint=if(tab==name)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    Column(Modifier.weight(1f).selectable(selected=tab==name,role=Role.Tab,onClick={if(flushMarkup()){model.commit();tab=name;selected=if(name=="Effects")PhotoLooks.key(e)else key;compare=false;zoom=1f;pan=Offset.Zero}}).padding(vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(painterResource(icon),null,tint=tint);Text(name,color=tint,style=MaterialTheme.typography.labelSmall)}
                }
            }
        }
    }
    }
    if(chooser=="Colorize color")PhotoColorizeDialog(e,enabled,{chooser=null}){hue,saturation,value->model.apply(e.set("colorizeHue",hue).set("colorizeSaturation",saturation).set("colorizeValue",value));chooser=null}
    else if(chooser!=null){
        val choices=mutableListOf<PhotoWheelOption>()
        if(tab=="Crop"){
            listOf("Full image" to 0f,"Square · 1:1" to 1f,"Landscape · 4:3" to 4f/3,"Portrait · 3:4" to 3f/4,"Landscape · 3:2" to 1.5f,"Wide · 16:9" to 16f/9,"Portrait · 9:16" to 9f/16).forEachIndexed{index,(name,ratio)->
                choices+=PhotoWheelOption("ratio$index",name){val b=model.original!!;var n=e.set("left",0f).set("right",0f).set("top",0f).set("bottom",0f);if(ratio>0){val actual=b.width.toFloat()/b.height;if(actual>ratio){val c=(1-ratio/actual)/2;n=n.set("left",c).set("right",c)}else{val c=(1-actual/ratio)/2;n=n.set("top",c).set("bottom",c)}};model.apply(n)}
            }
        }else{
            controls.forEach{(key,label)->choices+=PhotoWheelOption(key,label){selected=key;if(tab=="Effects"&&PhotoLooks.controls.any{it.first==key})model.apply(PhotoLooks.choose(e,key))}}
            if(tab=="Geometry")listOf("Rotate 90 degrees","Flip horizontal","Flip vertical").forEachIndexed{index,label->choices+=PhotoWheelOption("action$index",label){model.apply(when(index){0->photoAdjustment(e,"angle",photoAdjustmentValue(e,"angle").let{if(it+90>180)it-270 else it+90});1->e.set("flipX",1-e["flipX"]);else->e.set("flipY",1-e["flipY"])})}}
        }
        PhotoOptionsWheel(if(tab=="Crop")"Crop ratio"else if(tab=="Effects")"Photo effects"else "$tab tools",choices,selected,{chooser=null}){option->model.commit();option.choose();chooser=null}
    }
    if(export)AlertDialog(onDismissRequest={export=false},text={Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Button(enabled=model.canReplace,onClick={export=false;saveChanges=true},modifier=Modifier.fillMaxWidth()){Icon(painterResource(R.drawable.photo_save),null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text("Save (Overwrite)")}
        Button(enabled=!model.busy,onClick={export=false;markTools.finish();markTools.selected=null;model.discard(onClose)},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=androidx.compose.ui.graphics.Color(0xFF555861),contentColor=androidx.compose.ui.graphics.Color.White)){Text("Close (Discard Changes)")}
        if(!model.canReplace)Text("Save a copy, or reopen a writable JPEG or PNG to replace the original.",style=MaterialTheme.typography.bodySmall)
        if(model.canRecoverPrevious)TextButton(onClick={export=false;val info=model.previousInfo!!;val mime=info.getString("mime");savePrevious.launch(FileExportRequest("Image-previous."+if(mime=="image/png")"png"else"jpg",mime))}){Text("Save previous version…")}
        HorizontalDivider();Text("Save a copy",style=MaterialTheme.typography.titleSmall);Text("Images larger than 4000 px per side are downsampled for export. Save Layers to keep the original source and editable settings.",style=MaterialTheme.typography.bodySmall)
        Row{FilterChip(selected=!png&&!layers,onClick={png=false;layers=false},label={Text("JPEG")});Spacer(Modifier.width(8.dp));FilterChip(selected=png&&!layers,onClick={png=true;layers=false},label={Text("PNG")});Spacer(Modifier.width(8.dp));FilterChip(selected=layers,onClick={layers=true},label={Text("Layers")})}
        if(!png&&!layers){Text("Quality · ${quality.toInt()}%");PhotoDefaultSlider("JPEG quality",quality,95f,60f..100f,onChange={quality=it},onFinish={})}
        if(layers)Text("Editable original, adjustments, marks and Undo/Redo history. Open this .layers file in Image Edit to continue.",style=MaterialTheme.typography.bodySmall)else Text("Up to 4,000 pixels per side. PNG keeps transparent areas; JPEG uses white. Your working original stays available for Undo.",style=MaterialTheme.typography.bodySmall)
    }},confirmButton={TextButton(onClick={export=false;if(layers)saveLayers.launch("Image-edited.layers")else if(png)savePng.launch("Image-edited.png")else saveJpeg.launch("Image-edited.jpg")}){Text("Save a copy")}},dismissButton={TextButton(onClick={export=false}){Text("Cancel")}})
    if(saveChanges)AlertDialog(onDismissRequest={saveChanges=false},title={Text("Save changes?")},text={Text("Replace ${model.origin?.optString("name","this image")}. The previous version is retained for recovery. Your edits remain reversible in this editor.")},confirmButton={TextButton(onClick={saveChanges=false;model.saveChanges()}){Text("Save changes")}},dismissButton={TextButton(onClick={saveChanges=false}){Text("Cancel")}})

    if(reset||replace)AlertDialog(onDismissRequest={reset=false;replace=false},title={Text(if(reset)"Restore original image?" else "Open another image?")},text={Text(if(reset)"You can undo this reset." else "Save a copy first if you want to keep these edits.")},confirmButton={TextButton(onClick={if(reset){markTools.finish();markTools.selected=null;model.apply(PhotoEdit());sliderEpoch++;zoom=1f;pan=Offset.Zero;compare=false}else picker.launch(arrayOf("image/*",PhotoLayers.MIME));reset=false;replace=false}){Text("Continue")}},dismissButton={TextButton(onClick={reset=false;replace=false}){Text("Cancel")}})
}
