package app.onklav.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import app.onklav.shared.LauncherLayout
import app.onklav.shared.R
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AppsDesktop(resetKey:Int=0,manage:(()->Unit)?=null,open:(String)->Unit){
 val c=LocalContext.current;val prefs=remember{c.getSharedPreferences("apps_desktop",0)};val epoch by AppSdk.epoch.collectAsState()
 val removedIds = removedCatalogIds(games = false)
 var installed by remember{mutableStateOf(emptyList<org.json.JSONObject>())};var ready by remember{mutableStateOf(false)}
 var loadError by remember { mutableStateOf<String?>(null) }
 var retry by remember { mutableIntStateOf(0) }
 LaunchedEffect(epoch,retry){
  loadError=null
  try {
   installed=AppSdk.list(c).filter{it.optBoolean("installed")}
   ready=true
  } catch(e:kotlinx.coroutines.CancellationException){throw e}
  catch(e:Exception){loadError=e.message ?: "Apps could not be loaded."}
 }
 val apps=platformApps.filter{it.id !in setOf("phone","camera")}.filter{a->AppSdk.bundledApps[a.id]?.let{p->installed.any{it.child("manifest").optString("id")==p.first}}?:true}+installed.filter{p->AppSdk.bundledApps.values.none{it.first==p.child("manifest").optString("id")}}.map{SdkDesktopIcons.app(it.child("manifest"))}
 var problem by remember{mutableStateOf<String?>(null)}
 var writable by remember{mutableStateOf(true)}
 var folders by remember{mutableStateOf(runCatching{DesktopFolders.read(prefs.getString("folders_v1",null))}.getOrElse{writable=false;problem="Folder layout could not be loaded. Restart OnKlav before changing folders.";DesktopFolders(emptyList())})}
 fun catalog()=folders.desktop(apps.map{it.id})
 var layout by remember{mutableStateOf(LauncherLayout.read(prefs.getString("positions","").orEmpty(),catalog()))}
 val pager=rememberPagerState(initialPage=0){layout.pages};val scope=rememberCoroutineScope();val scroll=rememberScrollState();val density=LocalDensity.current;val haptic=LocalHapticFeedback.current
 var opened by remember{mutableStateOf<String?>(null)};var naming by remember{mutableStateOf<String?>(null)};var deleting by remember{mutableStateOf<String?>(null)};var folderMenu by remember{mutableStateOf(false)}
 var dragging by remember{mutableStateOf<String?>(null)};var sourceFolder by remember{mutableStateOf<String?>(null)};var before by remember{mutableStateOf(layout)}
 var finger by remember{mutableStateOf(Offset.Zero)};var grab by remember{mutableStateOf(Offset.Zero)};var dragSize by remember{mutableStateOf(Size.Zero)}
 var viewport by remember{mutableStateOf(IntSize.Zero)};var origin by remember{mutableStateOf(Offset.Zero)};var rowPixels by remember{mutableFloatStateOf(1f)}
 var folderRect by remember{mutableStateOf(Rect.Zero)};var folderGridRect by remember{mutableStateOf(Rect.Zero)};val memberRects=remember{mutableMapOf<String,Rect>()};var hovering by remember{mutableStateOf<String?>(null)}
 var edge by remember{mutableIntStateOf(0)};var consumed by remember{mutableStateOf(false)};val edgePx=with(density){28.dp.toPx()}
 val folder=folders.folders.firstOrNull{it.id==opened};val members=folder?.apps.orEmpty().mapNotNull{id->apps.firstOrNull{it.id==id&&id !in removedIds}}
 val folderPager=rememberPagerState(initialPage=0){maxOf(1,(members.size+8)/9)}
 fun reconcile(){layout=LauncherLayout.read(layout.encode(),catalog())}
 fun persist(arrangementVersion:Int?=null){if(!ready||!writable)return;layout=layout.trimmed();prefs.edit().putString("positions",layout.encode()).putString("folders_v1",folders.encode()).putInt("page",0).apply{if(arrangementVersion!=null)putInt("arrangement_version",arrangementVersion)}.apply()}
 fun dismissFolder(){opened=null;hovering=null;folderMenu=false;memberRects.clear()}
 fun cancel(){layout=before;dragging=null;sourceFolder=null;edge=0;hovering=null}
 fun slot(p:Offset,page:Int=pager.currentPage)=LauncherLayout.destination(page,p.x,p.y,viewport.width.toFloat(),rowPixels,scroll.value.toFloat())
 fun tileRect(index:Int)=Rect((index%3)*viewport.width/3f,((index%15)/3)*rowPixels-scroll.value,((index%3)+1)*viewport.width/3f,((index%15)/3+1)*rowPixels-scroll.value)
 fun moveAccessible(id:String,target:Int):Boolean {if(!writable||target !in 0 until LauncherLayout.MAX_PAGES*15)return false;layout=layout.ensurePage(target/15).move(id,target);persist();scope.launch{pager.animateScrollToPage(target/15)};return true}
 fun hover(p:Offset){
  finger=p
  val inFolder=opened!=null&&folderRect.contains(p)
  val bounds=if(inFolder)folderRect else Rect(0f,0f,viewport.width.toFloat(),viewport.height.toFloat())
  val next=if(!bounds.contains(p))0 else if(p.x<bounds.left+edgePx)-1 else if(p.x>bounds.right-edgePx)1 else 0
  if(edge!=next){edge=next;consumed=false}
  if(opened==null&&sourceFolder==null&&dragging!=null){
   val dest=slot(p);val target=layout.slots.getOrNull(dest);val box=tileRect(dest)
   val f=folders.folders.firstOrNull{it.id==target&&it.id!=dragging}
   // Reserve the whole folder cell before reordering; edge-first drags must not move the target away.
   val center=box
   hovering=if(f!=null&&!dragging.orEmpty().startsWith("folder-")&&center.contains(p))f.id else null
   if(hovering==null&&edge==0&&p.y in 0f..viewport.height.toFloat()&&!pager.isScrollInProgress)layout=layout.move(dragging!!,dest)
  }
 }
 val start by rememberUpdatedState<(Offset)->Unit>({p->
  if(writable&&ready){
   val id=if(opened!=null)memberRects.entries.firstOrNull{folderGridRect.contains(p)&&it.key in members.drop(folderPager.currentPage*9).take(9).map{a->a.id}&&it.value.contains(p)}?.key else layout.slots.getOrNull(slot(p))?.takeIf{it.isNotBlank()&&it !in removedIds}
   if(id!=null){val rect=if(opened!=null)memberRects[id]!! else tileRect(slot(p));before=layout;dragging=id;sourceFolder=opened;finger=p;grab=p-rect.topLeft;dragSize=rect.size;edge=0;consumed=false;haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)}
  }
 })
 val move by rememberUpdatedState<(Offset)->Unit>({d->if(dragging!=null)hover(finger+d)})
 val finish by rememberUpdatedState<()->Unit>({
  dragging?.let{id->
   val inside=Rect(0f,0f,viewport.width.toFloat(),viewport.height.toFloat()).contains(finger)
   val target=opened?:hovering
   if(inside&&target!=null&&!id.startsWith("folder-")&&(opened==null||folderRect.contains(finger)||(sourceFolder==null&&hovering==target&&tileRect(layout.slots.indexOf(target)).contains(finger)))){folders=folders.put(target,id);reconcile();persist();opened=target}
   else if(inside){if(sourceFolder!=null){folders=folders.takeOut(id);reconcile()};val next=pager.targetPage;layout=layout.ensurePage(next).move(id,slot(finger,next));persist();dismissFolder();scope.launch{withFrameNanos{};pager.scrollToPage(next.coerceAtMost(layout.pages-1))}}
   else layout=before
  };dragging=null;sourceFolder=null;edge=0;hovering=null
 })
 LaunchedEffect(apps.map{it.id},ready){
  if(dragging!=null)cancel()
  if(ready&&writable&&prefs.getInt("arrangement_version",0)<DesktopArrangement.VERSION){
   runCatching{val updated=folders.withRequestedFolders();val arranged=DesktopArrangement.arrange(updated.desktop(apps.map{it.id}));folders=updated;layout=arranged;persist(DesktopArrangement.VERSION)}.onFailure{problem="Desktop update could not finish. ${it.message.orEmpty()}"}
  }else layout=LauncherLayout.read(prefs.getString("positions","").orEmpty(),catalog())
 }
 LaunchedEffect(resetKey){if(dragging!=null)cancel();dismissFolder();pager.scrollToPage(0);scroll.scrollTo(0)}
 LaunchedEffect(opened){memberRects.clear();folderPager.scrollToPage(0)}
 LaunchedEffect(hovering,dragging){val target=hovering;if(target!=null&&dragging!=null){delay(450);if(hovering==target&&dragging!=null)opened=target}}
 LaunchedEffect(edge,dragging,opened){if(dragging!=null&&edge!=0&&!consumed){delay(550);consumed=true;if(opened!=null&&folderRect.contains(finger)){val next=folderPager.currentPage+edge;if(next in 0 until folderPager.pageCount)folderPager.animateScrollToPage(next)}else{val next=pager.currentPage+edge;if(next in 0 until LauncherLayout.MAX_PAGES){layout=layout.ensurePage(next);withFrameNanos{};withFrameNanos{};pager.animateScrollToPage(next)}}}}
 LaunchedEffect(dragging,finger.y,opened){while(dragging!=null&&opened==null){val margin=with(density){36.dp.toPx()};val delta=if(finger.y<margin)-6 else if(finger.y>viewport.height-margin)6 else 0;if(delta==0)break;val target=(scroll.value+delta).coerceIn(0,scroll.maxValue);if(target==scroll.value)break;scroll.scrollTo(target);hover(finger);delay(16)}}
 BackHandler(opened!=null||dragging!=null){if(dragging!=null)cancel()else dismissFolder()}
 Column(Modifier.fillMaxSize().testTag("apps-desktop")){
  HomeHeader("Apps",actions={OriginalAction(R.drawable.ref_action_folder_add,"New folder",enabled=writable&&ready&&dragging==null){naming=""};manage?.let{OriginalAction(R.drawable.ref_nav_settings,"Manage apps",action=it)}})
  Spacer(Modifier.height(10.dp))
  loadError?.let { message ->
   Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically){
    Text(message,Modifier.weight(1f),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
    TextButton(onClick={retry++}){Text("Try again")}
   }
  }
  if(problem!=null)Text(problem!!,Modifier.padding(12.dp),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
  BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().testTag("apps-grid-viewport").onSizeChanged{viewport=it}.onGloballyPositioned{origin=it.positionInRoot()}.pointerInput(Unit){detectDragGesturesAfterLongPress(onDragStart={start(it)},onDragEnd={finish()},onDragCancel={if(dragging!=null)cancel()}){change,d->if(dragging!=null){change.consume();move(d)}}}){
   val workspaceHeight=maxHeight
   val cellHeight=maxOf(maxHeight/5,68.dp+(32f*density.fontScale).dp);rowPixels=with(density){cellHeight.toPx()}
   Box(Modifier.fillMaxSize().verticalScroll(scroll,enabled=dragging==null&&opened==null).then(if(opened!=null)Modifier.clearAndSetSemantics{}else Modifier)){
    HorizontalPager(pager,Modifier.fillMaxWidth().height(cellHeight*5),userScrollEnabled=dragging==null&&opened==null,key={it}){page->
     LazyVerticalGrid(GridCells.Fixed(3),userScrollEnabled=false,modifier=Modifier.fillMaxSize().testTag("apps-page-$page")){
      items(15,key={local->layout.slots.getOrNull(page*15+local).orEmpty().ifEmpty{"empty-${page*15+local}"}}){local->val index=page*15+local;val id=layout.slots.getOrNull(index).orEmpty();val f=folders.folders.firstOrNull{it.id==id};val a=apps.firstOrNull{it.id==id&&id !in removedIds}?:f?.let{PlatformApp(it.id,it.name,R.drawable.ref_desktop_folder)}
       Box(Modifier.animateItem(fadeInSpec=null,fadeOutSpec=null,placementSpec=spring(dampingRatio=.9f,stiffness=450f)).fillMaxWidth().height(cellHeight).testTag("app-cell-$index")){
        if(a!=null)AppTile(a,Modifier.fillMaxSize().alpha(if(dragging==id)0f else 1f).then(if(hovering==id)Modifier.background(MaterialTheme.colorScheme.primaryContainer,RoundedCornerShape(18.dp))else Modifier).semantics{customActions=listOf(CustomAccessibilityAction("Move Earlier"){moveAccessible(id,index-1)},CustomAccessibilityAction("Move Later"){moveAccessible(id,index+1)},CustomAccessibilityAction("Move to Previous Page"){if(page==0)false else moveAccessible(id,layout.firstSlot(page-1))},CustomAccessibilityAction("Move to Next Page"){if(page+1>=LauncherLayout.MAX_PAGES)false else moveAccessible(id,layout.ensurePage(page+1).firstSlot(page+1))})+if(f==null)folders.folders.map{dest->CustomAccessibilityAction("Move to ${dest.name}"){folders=folders.put(dest.id,id);reconcile();persist();true}}else emptyList()},folderLetter=f?.letter){if(dragging==null){if(f!=null)opened=id else open(id)}}
       }
      }
     }
    }
   }
   // Same-window dialog keeps the original pointer stream alive across hover-open and drag-out.
   androidx.compose.animation.AnimatedVisibility(visible=opened!=null,modifier=Modifier.fillMaxSize(),enter=fadeIn(tween(160)),exit=fadeOut(tween(120))){Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha=.55f)).clickable(indication=null,interactionSource=remember{MutableInteractionSource()}){if(dragging==null)dismissFolder()})}
   if(folder!=null){
    Surface(Modifier.align(Alignment.Center).widthIn(max=390.dp).fillMaxWidth(.94f).heightIn(max=maxHeight-12.dp).onGloballyPositioned{coords->folderRect=coords.boundsInRoot().translate(-origin)}.testTag("desktop-folder-dialog").semantics{paneTitle=folder.name;isTraversalGroup=true}.clickable(indication=null,interactionSource=remember{MutableInteractionSource()}){},shape=RoundedCornerShape(26.dp),color=MaterialTheme.colorScheme.surfaceContainerHigh,shadowElevation=12.dp){
     Column(Modifier.padding(12.dp)){
      Row(verticalAlignment=Alignment.CenterVertically){Text(folder.name,Modifier.weight(1f).padding(start=8.dp),style=MaterialTheme.typography.titleLarge,maxLines=1,overflow=TextOverflow.Ellipsis);Box{IconButton(onClick={folderMenu=true},enabled=dragging==null){Icon(Icons.Outlined.MoreVert,"Folder options")};DropdownMenu(folderMenu,{folderMenu=false}){DropdownMenuItem(text={Text("Rename")},onClick={folderMenu=false;naming=folder.id});DropdownMenuItem(text={Text("Delete folder")},onClick={folderMenu=false;deleting=folder.id})}};IconButton(onClick={dismissFolder()},enabled=dragging==null){Icon(Icons.Outlined.Close,"Close folder")}}
      Text(if(dragging!=null&&sourceFolder==null)"Release in this folder to add the app"else if(dragging!=null&&sourceFolder!=null&&!folderRect.contains(finger))"Release outside to move to Apps"else"Hold and drag an app outside to move it to Apps",Modifier.padding(horizontal=8.dp,vertical=6.dp),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
      val tileHeight=maxOf(92.dp,(64f+32f*density.fontScale).dp)
      val gridHeight=minOf(tileHeight*3,(workspaceHeight-190.dp).coerceAtLeast(120.dp))
      HorizontalPager(folderPager,Modifier.fillMaxWidth().height(gridHeight).onGloballyPositioned{folderGridRect=it.boundsInRoot().translate(-origin)}.testTag("folder-pager"),userScrollEnabled=dragging==null){page->
       Column(Modifier.verticalScroll(rememberScrollState(),enabled=dragging==null)){repeat(3){row->Row{repeat(3){col->val app=members.getOrNull(page*9+row*3+col);Box(Modifier.weight(1f).height(tileHeight)){
        if(app!=null)AppTile(app,Modifier.fillMaxSize().onGloballyPositioned{memberRects[app.id]=it.boundsInRoot().translate(-origin)}.alpha(if(dragging==app.id)0f else 1f).semantics{customActions=listOf(CustomAccessibilityAction("Move to Apps"){folders=folders.takeOut(app.id);reconcile();persist();true})}){if(dragging==null)open(app.id)}
       }}}}}
      }
      if(members.isEmpty())Text("Drag apps onto this folder to add them.",Modifier.padding(8.dp),style=MaterialTheme.typography.bodySmall)
      Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center){IconButton(onClick={scope.launch{folderPager.animateScrollToPage(folderPager.currentPage-1)}},enabled=folderPager.currentPage>0&&dragging==null){Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft,"Previous folder page")};Text("${folderPager.currentPage+1} / ${folderPager.pageCount}",Modifier.semantics{contentDescription="Folder page ${folderPager.currentPage+1} of ${folderPager.pageCount}"},style=MaterialTheme.typography.labelMedium);IconButton(onClick={scope.launch{folderPager.animateScrollToPage(folderPager.currentPage+1)}},enabled=folderPager.currentPage<folderPager.pageCount-1&&dragging==null){Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight,"Next folder page")}}
     }
    }
   }
   val dragged=apps.firstOrNull{it.id==dragging}?:folders.folders.firstOrNull{it.id==dragging}?.let{PlatformApp(it.id,it.name,R.drawable.ref_desktop_folder)}
   if(dragged!=null)AppTile(dragged,Modifier.offset{IntOffset((finger.x-grab.x).roundToInt(),(finger.y-grab.y).roundToInt())}.size(with(density){dragSize.width.toDp()},with(density){dragSize.height.toDp()}).graphicsLayer{scaleX=1.04f;scaleY=1.04f}.clearAndSetSemantics{},interactive=false,folderLetter=folders.folders.firstOrNull{it.id==dragging}?.letter){}
  }
  DesktopPageControls(pager.currentPage,layout.pages,dragging==null&&opened==null){page->scope.launch{pager.animateScrollToPage(page)}}
 }
 naming?.let{id->var name by remember(id){mutableStateOf(folders.folders.firstOrNull{it.id==id}?.name.orEmpty())};val valid=runCatching{DesktopFolders.validName(name)}.isSuccess
  AlertDialog(onDismissRequest={naming=null},title={Text(if(id.isEmpty())"New folder"else"Rename folder")},text={OutlinedTextField(name,{name=it.take(40)},label={Text("Folder name")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("folder-name"))},confirmButton={TextButton(onClick={if((catalog().size>=LauncherLayout.MAX_PAGES*15||folders.folders.size>=200)&&id.isEmpty())problem="The desktop is full."else{folders=if(id.isEmpty())folders.add(name)else folders.rename(id,name);reconcile();persist();if(id.isEmpty()){opened=folders.folders.last().id;val page=layout.slots.indexOf(opened)/15;scope.launch{withFrameNanos{};withFrameNanos{};pager.scrollToPage(page.coerceIn(0,layout.pages-1))}}};naming=null},enabled=valid){Text(if(id.isEmpty())"Create"else"Save")}},dismissButton={TextButton(onClick={naming=null}){Text("Cancel")}})
 }
 deleting?.let{id->AlertDialog(onDismissRequest={deleting=null},title={Text("Delete folder?")},text={Text("Its apps will move back to the Apps pages. No apps or app data will be deleted.")},confirmButton={TextButton(onClick={folders=folders.delete(id);reconcile();persist();dismissFolder();deleting=null}){Text("Delete folder")}},dismissButton={TextButton(onClick={deleting=null}){Text("Cancel")}})}
}

@Composable private fun DesktopPageControls(page:Int,pages:Int,enabled:Boolean,select:(Int)->Unit){
 val ink=MaterialTheme.colorScheme.primary
 Box(Modifier.fillMaxWidth().height(48.dp).testTag("apps-page-dots").semantics{contentDescription="Apps Page ${page+1} of $pages";stateDescription="$pages pages"},contentAlignment=Alignment.Center){
  if(pages>1){IconButton(onClick={select(page-1)},enabled=page>0&&enabled,modifier=Modifier.align(Alignment.CenterStart).testTag("apps-previous-page")){Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft,"Previous apps page",tint=ink.copy(alpha=if(page>0).65f else .18f))};IconButton(onClick={select(page+1)},enabled=page<pages-1&&enabled,modifier=Modifier.align(Alignment.CenterEnd).testTag("apps-next-page")){Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight,"Next apps page",tint=ink.copy(alpha=if(page<pages-1).65f else .18f))}}
  val count=minOf(9,pages);val first=(page-count/2).coerceIn(0,pages-count)
  Row{repeat(count){at->val n=first+at;Box(Modifier.size(16.dp).clickable(enabled=enabled){select(n)}.semantics{contentDescription="Go to Apps Page ${n+1}"},contentAlignment=Alignment.Center){Canvas(Modifier.size(if(n==page)6.dp else 4.dp)){drawCircle(ink,alpha=if(n==page).82f else .25f)}}}}
 }
}
@Composable
private fun AppTile(
    app: PlatformApp,
    modifier: Modifier,
    interactive: Boolean = true,
    folderLetter:String?=null,
    open: () -> Unit,
) {
    val surface = MaterialTheme.colorScheme.surface
    val background = MaterialTheme.colorScheme.background
    Column(
        modifier
            .clickable(
                enabled = interactive,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = open,
            )
            .semantics(mergeDescendants = true) { contentDescription = app.title }
            .padding(4.dp)
            .testTag("app-icon-${app.id}"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(56.dp)
                .background(
                    Brush.linearGradient(listOf(surface, background)),
                    RoundedCornerShape(15.dp),
                )
                .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(15.dp))
                .padding(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            val bitmap by produceState<android.graphics.Bitmap?>(null,app.iconPng){value=app.iconPng?.let{SdkDesktopIcons.bitmap(it)}}
            if(bitmap!=null)Image(requireNotNull(bitmap).asImageBitmap(),null,Modifier.fillMaxSize().testTag("desktop-custom-icon-${app.id}"),contentScale=androidx.compose.ui.layout.ContentScale.Fit)
            else {Icon(painterResource(app.artwork),null,Modifier.fillMaxSize(),tint=MaterialTheme.colorScheme.primary);if(folderLetter!=null)Text(folderLetter,Modifier.padding(top=4.dp),color=MaterialTheme.colorScheme.onPrimary,fontSize=16.sp,maxLines=1)}
        }
        Text(
            app.title,
            Modifier.fillMaxWidth().padding(top = 4.dp),
            fontSize = 12.sp,
            lineHeight = 15.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
