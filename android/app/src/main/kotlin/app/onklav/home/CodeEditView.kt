package app.onklav.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.*
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import kotlinx.coroutines.*

/** Native selection/IME and scrolling; highlighting changes spans, never the text or selection. */
// Deliberately native: OnKlav supplies theme/span behavior without AppCompat tinting.
@android.annotation.SuppressLint("AppCompatCustomView")
internal class CodeInput(c:Context):EditText(c){
 var selectionChanged:((Int,Int)->Unit)?=null
 override fun onSelectionChanged(start:Int,end:Int){super.onSelectionChanged(start,end);selectionChanged?.invoke(start,end)}
}
internal class CodeEditorView(c:Context):FrameLayout(c){
 val input=CodeInput(c)
 private val density=resources.displayMetrics.density
 var indentUnit="    "
 private var changeStart=0
 private var newlineCandidate=false
 private var syncing=false
 private var key=""
 private var language="Plain text"
 private var highlighting=true
 private var lastWrap:Boolean?=null
 private var lastFont=0f
 private var applyingSpans=false
 private var scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
 private var job:Job?=null
 private var listener:((String,Int,Int)->Unit)?=null
 private var palette=intArrayOf(0xff9eaaa6.toInt(),0xff81c995.toInt(),0xfff6c177.toInt(),0xffc4a7e7.toInt(),0xff7dcfff.toInt())
 private val gutter=object:View(c){val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{typeface=Typeface.MONOSPACE}
  override fun onDraw(canvas:Canvas){super.onDraw(canvas);val layout=input.layout?:return;paint.textSize=input.textSize*.82f;paint.color=input.currentHintTextColor;val first=layout.getLineForVertical(input.scrollY);val last=layout.getLineForVertical(input.scrollY+height);val text=input.text;var number=1;var previous=0
   for(line in first..last){val start=layout.getLineStart(line);while(previous<start&&previous<text.length){if(text[previous]=='\n')number++;previous++};if(start==0||text.getOrNull(start-1)=='\n'){val label=number.toString();canvas.drawText(label,width-7*density-paint.measureText(label),layout.getLineBaseline(line)+input.totalPaddingTop-input.scrollY.toFloat(),paint)}}
  }
 }
 init{
  input.typeface=Typeface.MONOSPACE;input.setTextSize(14f);input.setPadding((8*density).toInt(),(12*density).toInt(),(12*density).toInt(),(80*density).toInt());input.gravity=android.view.Gravity.TOP or android.view.Gravity.START
  input.inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
  input.imeOptions=EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
  input.setBackgroundColor(android.graphics.Color.TRANSPARENT);input.isVerticalScrollBarEnabled=true;input.isHorizontalScrollBarEnabled=true
  input.filters=arrayOf(InputFilter.LengthFilter(CodeText.MAX_CHARS));input.contentDescription="Code editor"
  addView(input,LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT).apply{leftMargin=(48*density).toInt()});addView(gutter,LayoutParams((48*density).toInt(),LayoutParams.MATCH_PARENT))
  input.selectionChanged={a,b->if(!syncing&&a>=0&&b>=0)selection?.invoke(a,b)}
  input.addTextChangedListener(object:TextWatcher{
   override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){changeStart=start;newlineCandidate=!syncing&&count==0&&after==1}
   override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){}
   override fun afterTextChanged(s:Editable?){if(!syncing&&!applyingSpans){if(newlineCandidate&&s!=null&&s.getOrNull(changeStart)=='\n'){val begin=s.toString().lastIndexOf('\n',changeStart-1)+1;val previous=s.substring(begin,changeStart);val whitespace=previous.takeWhile{it==' '||it=='\t'}+if(previous.trimEnd().lastOrNull() in listOf('{','[','(',':'))indentUnit else "";if(whitespace.isNotEmpty()&&s.length+whitespace.length<=CodeText.MAX_CHARS){syncing=true;try{val position=changeStart+1;s.insert(position,whitespace);input.setSelection(position+whitespace.length)}finally{syncing=false}}};newlineCandidate=false;listener?.invoke(s.toString(),input.selectionStart.coerceAtLeast(0),input.selectionEnd.coerceAtLeast(0));highlight()};gutter.invalidate()}
  })
  input.viewTreeObserver.addOnScrollChangedListener{gutter.invalidate();highlight()}
 }
 var selection:((Int,Int)->Unit)?=null
 fun bind(doc:CodeDocument,wrap:Boolean,font:Float,enabled:Boolean,highlighting:Boolean,foreground:Int,muted:Int,colors:IntArray,change:(String,Int,Int)->Unit,select:(Int,Int)->Unit){
  listener=change;selection=select;input.isEnabled=enabled;input.setTextColor(foreground);input.setHintTextColor(muted);val paletteChanged=!palette.contentEquals(colors);palette=colors;if(lastWrap!=wrap)input.setHorizontallyScrolling(!wrap)
  if(input.textSize/resources.displayMetrics.scaledDensity!=font)input.setTextSize(font)
  val reload=key!=doc.id||input.text.toString()!=doc.text||language!=doc.language||this.highlighting!=highlighting||paletteChanged||lastWrap!=wrap||lastFont!=font
  syncing=true;try{if(key!=doc.id||input.text.toString()!=doc.text){val different=key!=doc.id;input.setText(doc.text);if(different)input.scrollTo(0,0)};if(input.selectionStart!=doc.start||input.selectionEnd!=doc.end)input.setSelection(doc.start.coerceIn(0,doc.text.length),doc.end.coerceIn(0,doc.text.length));key=doc.id;language=doc.language;this.highlighting=highlighting;lastWrap=wrap;lastFont=font}finally{syncing=false}
  if(reload)highlight();gutter.invalidate()
 }
 private fun highlight(){job?.cancel();val snapshot=input.text.toString();val id=key;val lang=language;val active=highlighting
  job=scope.launch{delay(180);val layout=input.layout;val first=layout?.getLineStart(layout.getLineForVertical(input.scrollY))?:0;val end=layout?.getLineEnd(layout.getLineForVertical(input.scrollY+input.height))?:snapshot.length
   val tokens=withContext(Dispatchers.Default){if(active)CodeText.tokens(snapshot,lang,(first-4000).coerceAtLeast(0),(end+4000).coerceAtMost(snapshot.length))else emptyList()};if(key!=id||input.text.toString()!=snapshot)return@launch
   applyingSpans=true;try{val text=input.text;text.getSpans(0,text.length,ForegroundColorSpan::class.java).forEach{text.removeSpan(it)};tokens.forEach{t->text.setSpan(ForegroundColorSpan(palette[(t.kind-1).coerceIn(0,palette.lastIndex)]),t.start,t.end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)}}finally{applyingSpans=false}
  }
 }
 fun focusEditor(){input.requestFocus();(context.getSystemService(Context.INPUT_METHOD_SERVICE)as android.view.inputmethod.InputMethodManager).showSoftInput(input,0)}
 fun release(){scope.cancel();listener=null;selection=null}
}
