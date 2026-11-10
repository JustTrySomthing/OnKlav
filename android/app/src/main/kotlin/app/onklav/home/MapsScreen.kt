package app.onklav.home

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.onklav.shared.R

@Composable internal fun MapsIcon(name:String,description:String?=null,modifier:Modifier=Modifier){val id=when(name){"search"->R.drawable.maps_search;"location"->R.drawable.maps_location;"folder"->R.drawable.maps_folder;"directions"->R.drawable.maps_directions;"swap"->R.drawable.maps_swap;"edit"->R.drawable.maps_edit;"delete"->R.drawable.maps_delete;"settings"->R.drawable.maps_settings;"plus"->R.drawable.maps_plus;"minus"->R.drawable.maps_minus;"back"->R.drawable.maps_back;"close"->R.drawable.maps_close;"bookmark_filled"->R.drawable.maps_bookmark_filled;else->R.drawable.maps_bookmark};Icon(painterResource(id),description,modifier)}
@Composable internal fun MapsFolderButton(open:()->Unit){IconButton(onClick=open){MapsIcon("folder","Saved places and directions")}}
@Composable internal fun MapsWorkspace(savedOpen:Boolean=false,online:Boolean=true,onSavedChange:(Boolean)->Unit={}){val c=LocalContext.current;val epoch by AppSdk.epoch.collectAsState();if(!remember(epoch){mapsAccess(c)}){Text("Maps storage access is off. Restore it in Settings > Apps > Maps.",Modifier.padding(24.dp));return};val model:MapsModel=viewModel(key="maps-$epoch");Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.surface){MapsContent(model,savedOpen,onSavedChange,online)}}
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun MapsContent(model:MapsModel,savedOpen:Boolean,onSavedChange:(Boolean)->Unit,online:Boolean=true){
    val c=LocalContext.current;val compact=androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp<600;val keyboard=LocalSoftwareKeyboardController.current
    var panel by rememberSaveable{mutableStateOf("map")};var searchFor by rememberSaveable{mutableStateOf("place")};var query by rememberSaveable{mutableStateOf("")};var savedTab by rememberSaveable{mutableIntStateOf(0)}
    var settings by remember{mutableStateOf(false)};var help by remember{mutableStateOf(false)};var credits by remember{mutableStateOf(false)}
    var namingPlace by remember{mutableStateOf<MapsPlace?>(null)};var namingRoute by remember{mutableStateOf<MapsRoute?>(null)};var deletingPlace by remember{mutableStateOf<MapsPlace?>(null)};var deletingRoute by remember{mutableStateOf<MapsRoute?>(null)}
    var locationFor by remember{mutableStateOf("place")}
    val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycle,model){val observer=androidx.lifecycle.LifecycleEventObserver{_,e->if(e==androidx.lifecycle.Lifecycle.Event.ON_STOP)model.cancel()};lifecycle.lifecycle.addObserver(observer);onDispose{lifecycle.lifecycle.removeObserver(observer);model.cancel()}}
    fun bookmark(p:MapsPlace){namingPlace=model.saved.places.firstOrNull{MapsMath.distance(it,p)<20}?:p}
    fun show(value:String){onSavedChange(false);panel=value}
    val locator=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){grants->if(grants[Manifest.permission.ACCESS_COARSE_LOCATION]==true||grants[Manifest.permission.ACCESS_FINE_LOCATION]==true)model.location{if(locationFor=="from")model.useCurrentAsStart()}else{model.message("Location was not allowed. Search for a ZIP code, city, or address instead.")}}
    fun locate(purpose:String="place"){if(!mapsAccess(c,"location.foreground")){model.message("Location access is off. Restore it in Settings > Apps > Maps.");return};locationFor=purpose;if(ContextCompat.checkSelfPermission(c,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED)model.location{if(purpose=="from")model.useCurrentAsStart()}else locator.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))}
    fun search(){if(panel=="map"&&!savedOpen)searchFor="place";keyboard?.hide();model.search(query){show("results")}}
    LaunchedEffect(savedOpen){if(savedOpen)model.cancel()}
    BackHandler(savedOpen||panel!="map"){if(savedOpen)onSavedChange(false)else panel="map"}
    val visible=if(savedOpen)"saved" else panel
    Column(Modifier.fillMaxSize()){
        if(visible=="map"||visible=="search"||visible=="results"){
            Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically){
                if(visible!="map")IconButton(onClick={panel=if(searchFor=="place")"map"else"directions"}){MapsIcon("back","Back to map")}
                OutlinedTextField(value=query,onValueChange={query=it.take(160)},placeholder={Text(if(visible=="map")"ZIP, city, address, or place"else if(searchFor=="from")"Starting point"else if(searchFor=="to")"Destination"else "ZIP, city, address, or place",maxLines=1)},singleLine=true,modifier=Modifier.weight(1f),keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={search()}),trailingIcon={IconButton(onClick={search()},enabled=!model.busy){MapsIcon("search","Search maps")}})
                IconButton(onClick={locate()},enabled=!model.busy){MapsIcon("location","Current location")}
            }
        }else Row(Modifier.fillMaxWidth().padding(horizontal=4.dp),verticalAlignment=Alignment.CenterVertically){IconButton(onClick={onSavedChange(false);panel="map"}){MapsIcon("back","Back to map")};Text(if(visible=="saved")"Saved"else if(visible=="nearby")"Nearby"else "Directions",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium);IconButton(onClick={settings=true}){MapsIcon("settings","Map settings")}}
        if(model.busy)Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically){CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp);Text(model.status,Modifier.weight(1f).padding(horizontal=8.dp),style=MaterialTheme.typography.bodySmall);TextButton(onClick=model::cancel){Text("Cancel")}}
        else if(model.status.isNotBlank())Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically){Text(model.status,Modifier.weight(1f).semantics{liveRegion=LiveRegionMode.Polite},style=MaterialTheme.typography.bodySmall,color=if(model.failed)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant);IconButton(onClick={model.message("")}){MapsIcon("close","Dismiss message")}}
        AnimatedContent(targetState=visible,modifier=Modifier.weight(1f),transitionSpec={slideInHorizontally(tween(200)){it}+fadeIn(tween(180)) togetherWith slideOutHorizontally(tween(200)){it}+fadeOut(tween(180))},label="Maps pages"){page->
            when(page){
                "map"->Column(Modifier.fillMaxSize()){
                    if(!compact)Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)){MapsNet.categories.keys.forEach{cat->SuggestionChip(onClick={searchFor="place";model.nearby(cat){show("results")}},enabled=!model.busy,label={Text(cat)})}}
                    MapsCanvas(model,Modifier.weight(1f).fillMaxWidth(),online)
                    model.selected?.let{p->Surface(color=MaterialTheme.colorScheme.surfaceContainer){Column(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp)){
                        Row(verticalAlignment=Alignment.CenterVertically){Text(p.name,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis);IconButton(onClick={bookmark(p)}){MapsIcon(if(model.saved.places.any{MapsMath.distance(it,p)<20})"bookmark_filled"else"bookmark","Bookmark location")}}
                        if(!compact&&p.address.isNotBlank())Text(p.address,style=MaterialTheme.typography.bodySmall,maxLines=2,overflow=TextOverflow.Ellipsis)
                        Row{Button(onClick={if(model.route?.to?.id!=p.id)model.destination();show("directions")}){MapsIcon("directions");Spacer(Modifier.width(6.dp));Text("Directions")};if(model.route!=null)TextButton(onClick={show("directions")}){Text("Route")}}
                    }}}
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){TextButton(onClick={credits=true},modifier=Modifier.weight(1f)){Text("© OpenStreetMap contributors",style=MaterialTheme.typography.labelSmall,maxLines=1)};if(compact)TextButton(onClick={show("nearby")}){Text("Nearby")};IconButton(onClick={model.destination();show("directions")}){MapsIcon("directions","Plan directions")};IconButton(onClick={settings=true}){MapsIcon("settings","Map settings")}}
                }
                "nearby"->Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)){MapsNet.categories.keys.forEach{cat->OutlinedButton(onClick={searchFor="place";model.nearby(cat){show("results")}},modifier=Modifier.fillMaxWidth(),enabled=!model.busy){Text(cat)}}}
                "results","search"->Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=16.dp)){
                    Text(if(searchFor=="from")"Choose a starting point"else if(searchFor=="to")"Choose a destination"else model.resultTitle,style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(vertical=10.dp))
                    if(searchFor=="from")TextButton(onClick={locate("from");panel="directions"}){MapsIcon("location");Spacer(Modifier.width(6.dp));Text("Use current location")}
                    if(model.results.isEmpty()&&!model.busy)Text(if(model.hasSearched)"Try a different search or choose a bookmark."else "Search above or choose a bookmark.",style=MaterialTheme.typography.bodyMedium)
                    model.results.forEach{p->MapsPlaceRow(p,onClick={model.choose(p,searchFor);show(if(searchFor=="place")"map"else "directions")},subtitle=if(model.resultTitle.endsWith("nearby"))MapsMath.distanceText(MapsMath.distance(MapsPlace("area","Area",model.camera.lat,model.camera.lon),p),model.metric)+" · "+p.address else p.address){IconButton(onClick={bookmark(p)}){MapsIcon("bookmark","Bookmark ${p.name}")}}}
                    if(model.saved.places.isNotEmpty()){Text("Bookmarks",style=MaterialTheme.typography.titleSmall,modifier=Modifier.padding(top=16.dp));model.saved.places.sortedBy{it.name.lowercase()}.forEach{p->MapsPlaceRow(p,onClick={model.choose(p,searchFor);show(if(searchFor=="place")"map"else"directions")},subtitle=p.address)}}
                }
                "saved"->Column(Modifier.fillMaxSize()){
                    PrimaryTabRow(savedTab){listOf("Bookmarks","Directions").forEachIndexed{i,t->Tab(selected=savedTab==i,onClick={savedTab=i},text={Text(t)})}}
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=16.dp)){
                        if(savedTab==0){TextButton(onClick={namingPlace=model.selected?:MapsPlace("center:${model.camera.lat},${model.camera.lon}","Map location",model.camera.lat,model.camera.lon)}){MapsIcon("bookmark");Spacer(Modifier.width(6.dp));Text("Add bookmark")};if(model.saved.places.isEmpty())Text("Your bookmarked places will appear here.",style=MaterialTheme.typography.bodyMedium)
                            model.saved.places.sortedBy{it.name.lowercase()}.forEach{p->MapsPlaceRow(p,onClick={model.center(p);show("map")},subtitle=if(model.resultTitle.endsWith("nearby"))MapsMath.distanceText(MapsMath.distance(MapsPlace("area","Area",model.camera.lat,model.camera.lon),p),model.metric)+" · "+p.address else p.address){IconButton(onClick={bookmark(p)}){MapsIcon("edit","Rename ${p.name}")};IconButton(onClick={deletingPlace=p}){MapsIcon("delete","Delete ${p.name}")}}}
                        }else{
                            TextButton(onClick={if(model.route!=null)namingRoute=model.route else{model.destination();show("directions")}}){MapsIcon("directions");Spacer(Modifier.width(6.dp));Text(if(model.route!=null)"Save current directions"else "Plan a route")}
                            if(model.saved.routes.isEmpty())Text("Your saved directions will appear here.",style=MaterialTheme.typography.bodyMedium)
                            model.saved.routes.sortedBy{it.name.lowercase()}.forEach{r->MapsPlaceRow(r.to.copy(name=r.name),onClick={model.showRoute(r);show("directions")},subtitle="${r.mode} · ${MapsMath.distanceText(r.distance,model.metric)} · ${MapsMath.durationText(r.seconds)}"){IconButton(onClick={namingRoute=r}){MapsIcon("edit","Rename ${r.name}")};IconButton(onClick={deletingRoute=r}){MapsIcon("delete","Delete ${r.name}")}}}
                        }
                    }
                }
                else->Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Row(verticalAlignment=Alignment.CenterVertically){OutlinedButton(onClick={searchFor="from";query="";show("search")},modifier=Modifier.weight(1f)){Text(model.from?.name?:"Choose starting point",maxLines=2,overflow=TextOverflow.Ellipsis)};IconButton(onClick={locate("from")},enabled=!model.busy){MapsIcon("location","Use current location as start")}}
                    Row(verticalAlignment=Alignment.CenterVertically){OutlinedButton(onClick={searchFor="to";query="";show("search")},modifier=Modifier.weight(1f)){Text(model.to?.name?:"Choose destination",maxLines=2,overflow=TextOverflow.Ellipsis)};IconButton(onClick=model::swap,enabled=!model.busy){MapsIcon("swap","Swap start and destination")}}
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("Drive","Walk","Cycle").forEach{m->FilterChip(selected=model.mode==m,onClick={model.chooseMode(m)},enabled=!model.busy,label={Text(m)})}}
                    Button(onClick=model::directions,enabled=!model.busy&&model.from!=null&&model.to!=null,modifier=Modifier.fillMaxWidth()){Text("Get directions")}
                    model.route?.let{r->HorizontalDivider();Text("${MapsMath.durationText(r.seconds)} · ${MapsMath.distanceText(r.distance,model.metric)}",style=MaterialTheme.typography.titleLarge);Row{TextButton(onClick={model.fitRoute();show("map")}){Text("Show map")};TextButton(onClick={namingRoute=r}){MapsIcon("folder");Spacer(Modifier.width(6.dp));Text("Save directions")}};Text("Route preview · ${r.mode} · ${java.text.DateFormat.getDateInstance().format(java.util.Date(r.savedAt))}",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        r.steps.forEachIndexed{i,s->Column(Modifier.fillMaxWidth().clickable{model.center(MapsPlace("step-$i",s.text,s.lat,s.lon),17.0);show("map")}.padding(vertical=12.dp)){Text("${i+1}. ${s.text}",style=MaterialTheme.typography.bodyMedium);Text(MapsMath.distanceText(s.distance,model.metric),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};HorizontalDivider()}
                        Text("Routing: OSRM / FOSSGIS · © OpenStreetMap contributors",style=MaterialTheme.typography.labelSmall,modifier=Modifier.padding(vertical=12.dp))}
                }
            }
        }
        if(visible!="map")TextButton(onClick={credits=true},modifier=Modifier.fillMaxWidth()){Text("© OpenStreetMap contributors",style=MaterialTheme.typography.labelSmall)}
    }
    val p=namingPlace;val r=namingRoute
    if(p!=null||r!=null){var name by remember(p?.id,r?.id){mutableStateOf(p?.name?:r!!.name)};AlertDialog(onDismissRequest={namingPlace=null;namingRoute=null},title={Text(if(p!=null)"Bookmark name"else "Directions name")},text={OutlinedTextField(name,{name=it.take(120)},label={Text("Name")},singleLine=true)},confirmButton={TextButton(enabled=!model.busy&&name.isNotBlank(),onClick={if(p!=null)model.savePlace(p,name)else model.saveRoute(r!!,name);namingPlace=null;namingRoute=null}){Text("Save")}},dismissButton={TextButton(onClick={namingPlace=null;namingRoute=null}){Text("Cancel")}})}
    if(deletingPlace!=null||deletingRoute!=null)AlertDialog(onDismissRequest={deletingPlace=null;deletingRoute=null},title={Text("Delete saved item?")},text={Text(deletingPlace?.name?:deletingRoute!!.name)},confirmButton={TextButton(enabled=!model.busy,onClick={deletingPlace?.let{model.deletePlace(it.id)};deletingRoute?.let{model.deleteRoute(it.id)};deletingPlace=null;deletingRoute=null}){Text("Delete")}},dismissButton={TextButton(onClick={deletingPlace=null;deletingRoute=null}){Text("Cancel")}})
    if(settings)AlertDialog(onDismissRequest={settings=false},title={Text("Maps settings")},text={Column(Modifier.verticalScroll(rememberScrollState())){Row(verticalAlignment=Alignment.CenterVertically){Text("Metric distances",Modifier.weight(1f));Switch(model.metric,{model.settings(metric=it)})};Text("Nearby radius · ${model.radius} km");Slider(model.radius.toFloat(),{model.settings(radius=it.toInt())},valueRange=1f..50f);TextButton(onClick={model.clearCache()}){Text("Clear cached maps and searches")};TextButton(onClick={settings=false;help=true}){Text("Help")};TextButton(onClick={settings=false;credits=true}){Text("Map sources and credits")}}},confirmButton={TextButton(onClick={settings=false}){Text("Done")}})
    if(help)AlertDialog(onDismissRequest={help=false},title={Text("Maps help")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){Text("Search by ZIP code, city and state, address, place name, or latitude,longitude. Tap the location target to use your phone’s current location.");Text("Drag to move the map. Pinch or use + and − to zoom. Hold the map to drop a pin. Nearby categories search around the center of the map.");Text("The folder at the top holds Bookmarks and saved Directions. Add a bookmark, give it a name, and use the pencil to rename it later.");Text("For directions choose a starting point, destination, and travel mode. Save directions to keep the route and its instruction list. Saved routes are snapshots; Get directions refreshes them.");Text("Routes are previews, without live traffic, voice guidance, or automatic rerouting. Current conditions and access may differ. Map display and new searches/routes need an internet connection; previously cached tiles may remain available. ZIP areas are approximate.")}},confirmButton={TextButton(onClick={help=false}){Text("Done")}})
    if(credits)AlertDialog(onDismissRequest={credits=false},title={Text("Map sources")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("© OpenStreetMap contributors · ODbL");Text("Tiles: OpenStreetMap. Place search and nearby places: Photon / komoot. Routes: OSRM / FOSSGIS. Offline cities: GeoNames CC BY 4.0; US ZIP areas: GeoNames CC BY 3.0.");TextButton(onClick={if(mapsAccess(c,"links.external"))c.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://www.openstreetmap.org/copyright")))}){Text("OpenStreetMap copyright")};TextButton(onClick={if(mapsAccess(c,"links.external"))c.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://www.openstreetmap.org/fixthemap")))}){Text("Fix the map")};Text("A search, nearby lookup, or route request sends the chosen query or coordinates to the corresponding service. Location is read only when requested. Public service availability can vary.",style=MaterialTheme.typography.bodySmall)}},confirmButton={TextButton(onClick={credits=false}){Text("Done")}})
}
@Composable private fun MapsPlaceRow(p:MapsPlace,onClick:()->Unit,subtitle:String="",actions:@Composable RowScope.()->Unit={}){Column{Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f).clickable(onClick=onClick).padding(vertical=14.dp)){Text(p.name,style=MaterialTheme.typography.titleSmall,maxLines=2,overflow=TextOverflow.Ellipsis);if(subtitle.isNotBlank())Text(subtitle,style=MaterialTheme.typography.bodySmall,maxLines=2,color=MaterialTheme.colorScheme.onSurfaceVariant,overflow=TextOverflow.Ellipsis)};actions()};HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.6f))}}
