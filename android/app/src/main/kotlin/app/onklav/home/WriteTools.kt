@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package app.onklav.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.unit.*
import app.onklav.shared.R

/** Fixed two-row accessory. Popovers are separate windows, never part of page measurement. */
@Composable internal fun WriteTools(
    enabled:Boolean, canUndo:Boolean, canRedo:Boolean, active:(String)->Boolean,
    panel:String, textColor:Int, highlightColor:Int, fontSize:Int,
    open:(String)->Unit, dismiss:()->Unit, toggle:(String)->Unit,
    character:(String,String?)->Unit, paragraph:(String,String)->Unit,
    indent:(Int)->Unit, clear:()->Unit, image:()->Unit, undo:()->Unit, redo:()->Unit, rule:()->Unit={}
){
    if(panel=="Color"||panel=="Highlight"){
        WriteColorBar(panel,enabled,if(panel=="Color")textColor else highlightColor,
            {value->character(if(panel=="Color")"color"else"highlight",value)},
            {open("Custom $panel")},dismiss)
        return
    }
    val buttonWidth=44.dp
    val popoverHeight=(LocalConfiguration.current.screenHeightDp/3f).coerceAtMost(256f).dp
    val popoverBottom=WindowInsets.ime.getBottom(LocalDensity.current)+WindowInsets.safeDrawing.getBottom(LocalDensity.current)
    val popoverGap=with(LocalDensity.current){8.dp.roundToPx()}
    @Composable fun tool(label:String,selected:Boolean=false,onClick:()->Unit,content:@Composable ()->Unit){
        IconButton(onClick=onClick,enabled=enabled,modifier=Modifier.width(buttonWidth).height(44.dp)
            .background(if(selected)MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,RoundedCornerShape(10.dp))
            .semantics{contentDescription=label;this.selected=selected;if(label=="Color"||label=="Highlight")stateDescription=String.format("#%06X",(if(label=="Color")textColor else highlightColor) and 0xffffff)}){content()}
    }
    @Composable fun popover(name:String,content:@Composable ColumnScope.()->Unit){
        if(panel==name)Popup(onDismissRequest=dismiss,properties=PopupProperties(focusable=false),
            popupPositionProvider=object:PopupPositionProvider{
                override fun calculatePosition(anchorBounds:IntRect,windowSize:IntSize,layoutDirection:LayoutDirection,popupContentSize:IntSize):IntOffset=
                    IntOffset(anchorBounds.left.coerceIn(0,(windowSize.width-popupContentSize.width).coerceAtLeast(0)),(anchorBounds.bottom+popoverGap).coerceIn(0,(windowSize.height-popupContentSize.height-popoverBottom).coerceAtLeast(0)))
            }){
            Surface(shape=RoundedCornerShape(20.dp),tonalElevation=6.dp,shadowElevation=12.dp,
                modifier=Modifier.widthIn(max=312.dp).heightIn(max=popoverHeight).semantics{paneTitle=if(name=="Text")"Font options"else "$name options"}){
                Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=content)
            }
        }
    }
    @Composable fun choice(text:String,click:()->Unit){OutlinedButton(onClick=click,contentPadding=PaddingValues(horizontal=10.dp,vertical=4.dp)){Text(text,maxLines=1,style=MaterialTheme.typography.labelMedium)}}
    Column(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=2.dp)){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically){
            Box{tool("Text",panel=="Text",{open("Text")}){Icon(painterResource(R.drawable.photo_tool_text),null)}
                popover("Text"){
                    FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("Body" to 16,"Heading" to 24,"Title" to 32).forEach{(label,size)->choice(label){character("size",size.toString());character("bold",if(size==16)null else "1");dismiss()}}}
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("Sans" to "sans-serif","Serif" to "serif","Mono" to "monospace").forEach{(label,font)->choice(label){character("font",font);dismiss()}}}
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
                        Text("Size",style=MaterialTheme.typography.labelLarge)
                        IconButton(enabled=fontSize>8,onClick={character("size",(fontSize-1).coerceAtLeast(8).toString())},modifier=Modifier.semantics{contentDescription="Decrease font size"}){Text("−",fontSize=24.sp)}
                        Text("$fontSize",style=MaterialTheme.typography.titleMedium,modifier=Modifier.semantics{contentDescription="Font size $fontSize"})
                        IconButton(enabled=fontSize<96,onClick={character("size",(fontSize+1).coerceAtMost(96).toString())},modifier=Modifier.semantics{contentDescription="Increase font size"}){Text("+",fontSize=24.sp)}
                    }
                    TextButton(onClick={clear();dismiss()}){Text("Clear formatting")}
                }
            }
            listOf("bold" to "Bold","italic" to "Italic","underline" to "Underline","strike" to "Strikethrough").forEach{(kind,label)->
                tool(label,active(kind),{toggle(kind)}){
                    Text(when(kind){"bold"->"B";"italic"->"I";"underline"->"U";else->"S"},fontSize=20.sp,
                        fontWeight=if(kind=="bold")FontWeight.Bold else FontWeight.Medium,
                        fontFamily=if(kind=="italic")FontFamily.Serif else FontFamily.SansSerif,
                        fontStyle=if(kind=="italic")FontStyle.Italic else FontStyle.Normal,
                        textDecoration=when(kind){"underline"->TextDecoration.Underline;"strike"->TextDecoration.LineThrough;else->null})
                }
            }
            listOf("Color","Highlight").forEach{name->Box{
                tool(name,panel==name,{open(name)}){Column(horizontalAlignment=Alignment.CenterHorizontally){
                    if(name=="Color")Text("A",fontSize=20.sp,fontWeight=FontWeight.Medium)else Icon(painterResource(R.drawable.photo_tool_highlight),null,Modifier.size(22.dp))
                    Box(Modifier.width(23.dp).height(4.dp).background(Color(if(name=="Color")textColor else highlightColor),RoundedCornerShape(2.dp)).border(.5.dp,MaterialTheme.colorScheme.outline,RoundedCornerShape(2.dp)))
                }}

            }}
        }
            IconButton(enabled=canRedo&&enabled,onClick=redo,modifier=Modifier.width(buttonWidth).height(44.dp)){Icon(painterResource(R.drawable.audio_edit_redo),"Redo")}
        }
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically){
            Box{tool("Lists",panel=="Lists",{open("Lists")}){Icon(painterResource(R.drawable.write_paragraph),null)}
                popover("Lists"){FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("Bullets" to "bullet","Numbered" to "number","No list" to "none").forEach{(label,value)->choice(label){paragraph("list",value);dismiss()}}}}
            }
            Box{tool("Align",panel=="Align",{open("Align")}){Icon(painterResource(R.drawable.write_align),null)}
                popover("Align"){FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("Left" to "ALIGN_NORMAL","Center" to "ALIGN_CENTER","Right" to "ALIGN_OPPOSITE").forEach{(label,value)->choice(label){paragraph("align",value);dismiss()}}}}
            }
            Box{tool("Indent",panel=="Indent",{open("Indent")}){Icon(painterResource(R.drawable.write_indent),null)}
                popover("Indent"){FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){choice("Outdent"){indent(-24);dismiss()};choice("Indent"){indent(24);dismiss()}}}
            }
            tool("Insert horizontal line",onClick=rule){Text("−",fontSize=24.sp)}
            tool("Insert image",onClick=image){Icon(painterResource(R.drawable.ref_app_photo),null)}
            tool("Insert link",onClick={open("Link")}){Icon(painterResource(R.drawable.write_link),null)}
            tool("Spelling & grammar",onClick={open("Review")}){Icon(painterResource(R.drawable.write_spellcheck),null)}
        }
            IconButton(enabled=canUndo&&enabled,onClick=undo,modifier=Modifier.width(buttonWidth).height(44.dp)){Icon(painterResource(R.drawable.audio_edit_undo),"Undo")}
        }
    }
}

/** Replaces the two existing toolbar rows: no document resize, bottom sheet, or IME change. */
@Composable private fun WriteColorBar(name:String,enabled:Boolean,current:Int,apply:(String?)->Unit,custom:()->Unit,done:()->Unit){
 val colors=listOf("Black" to 0xff111111.toInt(),"Gray" to 0xff666666.toInt(),"Red" to 0xffb71c1c.toInt(),"Gold" to 0xffffd54f.toInt(),"Green" to 0xff1b5e20.toInt(),"Blue" to 0xff0d47a1.toInt(),"White" to 0xffffffff.toInt(),"Orange" to 0xffe65100.toInt(),"Purple" to 0xff6a1b9a.toInt(),"Pink" to 0xfff8bbd0.toInt(),"Mint" to 0xffb2dfdb.toInt(),"Sky" to 0xffb3e5fc.toInt())
 Column(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=2.dp).semantics{paneTitle=if(name=="Color")"Text color"else"Highlight color"}){
  Row(Modifier.fillMaxWidth().height(44.dp),verticalAlignment=Alignment.CenterVertically){
   Text(if(name=="Color")"Text color"else"Highlight",Modifier.weight(1f),style=MaterialTheme.typography.labelLarge,maxLines=1)
   TextButton(enabled=enabled,onClick={apply(null)}){Text(if(name=="Color")"Default"else"Remove",maxLines=1)}
   TextButton(enabled=enabled,onClick=custom){Text("Custom",maxLines=1)}
   IconButton(onClick=done,modifier=Modifier.size(44.dp)){Text("✓",Modifier.semantics{contentDescription="Done formatting"},fontSize=22.sp)}
  }
  Row(Modifier.fillMaxWidth().height(44.dp).horizontalScroll(rememberScrollState())){colors.forEach{(label,color)->
   IconButton(enabled=enabled,onClick={apply(color.toString())},modifier=Modifier.size(44.dp).semantics{contentDescription=label;selected=current==color}){
    Box(Modifier.size(28.dp).background(Color(color),CircleShape).border(if(current==color)3.dp else 1.dp,MaterialTheme.colorScheme.outline,CircleShape))
   }
  }}
 }
}
