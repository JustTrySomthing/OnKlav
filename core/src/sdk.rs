//! Internal signed app packages and a capability-limited Rust/Wasm UI runtime.
//! No WASI, Java, JNI, native loading, filesystem or network imports reach guests.
use crate::{Result, Note};
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use sha2::{Digest, Sha256};
use ed25519_dalek::{Signature, VerifyingKey};
use std::{collections::{BTreeMap,BTreeSet},fs::{self,File,OpenOptions},io::{Read,Write},path::{Path,PathBuf},sync::{Mutex,OnceLock}};
use wasmi::{Config,Engine,Linker,Module,Store,StoreLimitsBuilder};
const MAX_PACKAGE:u64=700_000;
const MAX_WASM:usize=512_000;
const FUEL:u64=120_000_000;
const CAPS:[&str;37]=["ebooks.library","audiobooks.library","podcasts.library","radio.stations","apps.handoff","calendar.handoff","device.connectivity","device.settings","location.foreground","device.torch","nearby.bluetooth","nearby.wifi","network.lan","background.tasks","accounts.connect","network.http","camera.capture","notifications.post","storage.secrets","storage.private","clipboard.read","clipboard.write","files.export","share.text","network.feeds","network.web","speech.output","links.external","network.thumbnails","files.images","images.edit","audio.record","audio.playback","contacts.pick","communications.walkie","files.manage","files.external"];
#[derive(Clone,Debug,Serialize,Deserialize)]
#[serde(deny_unknown_fields)]
pub struct NetworkPolicy{pub origins:Vec<String>,#[serde(default,skip_serializing_if="Vec::is_empty")]pub accounts:Vec<AccountProvider>}
#[derive(Clone,Debug,Serialize,Deserialize)]
#[serde(deny_unknown_fields)]
pub struct AccountProvider{pub id:String,pub label:String,pub authorization_endpoint:String,pub token_endpoint:String,pub client_id:String,pub scopes:Vec<String>,pub api_origins:Vec<String>,#[serde(default,skip_serializing_if="Option::is_none")]pub revocation_endpoint:Option<String>}
#[derive(Clone,Debug,Serialize,Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Manifest {pub schema:String,pub runtime:String,pub id:String,pub name:String,pub version:u64,pub publisher:String,pub capabilities:BTreeSet<String>,#[serde(default,skip_serializing_if="Option::is_none")]pub network:Option<NetworkPolicy>,#[serde(default,skip_serializing_if="Option::is_none")]pub min_host:Option<u32>,#[serde(default,skip_serializing_if="Option::is_none")]pub icon:Option<AppIcon>}
pub const HOST_BUILD:u32=96;
pub const DESKTOP_SYMBOLS:[&str;33]=["calc","convert","weather","ebooks","audiobooks","podcasts","radio","apps","notes","files","walkie","social","browser","youtube","reader","annotation","rss","tabs","contacts","folder","file","home","forward","back","go","bookmark","menu","refresh","history","settings","add","download","edit"];
#[derive(Clone,Debug,Serialize,Deserialize)]
#[serde(deny_unknown_fields)]
pub struct AppIcon{#[serde(default,skip_serializing_if="Option::is_none")]pub symbol:Option<String>,#[serde(default,skip_serializing_if="Option::is_none")]pub png_hex:Option<String>}
fn validate_metadata(m:&Manifest)->Result<()>{
 if m.capabilities.contains("accounts.connect")&&(!m.capabilities.contains("network.http")||m.network.as_ref().is_none_or(|p|p.accounts.is_empty())){return Err("accounts.connect requires network.http and signed providers".into())}
 crate::sdk_http::validate_policy(m.network.as_ref(),m.capabilities.contains("network.http"))?;
 if let Some(version)=m.min_host{if version==0{return Err("Minimum host build must be positive".into())}if version>HOST_BUILD{return Err(format!("This app requires OnKlav build {version} or newer"))}}
 if let Some(icon)=&m.icon{match (&icon.symbol,&icon.png_hex){
 (Some(name),None) if DESKTOP_SYMBOLS.contains(&name.as_str())=>{},
 (None,Some(data))=>{
  if data.len()<66||data.len()>12288||data.len()%2!=0||!data.bytes().all(|c|c.is_ascii_digit()||(b'a'..=b'f').contains(&c)){return Err("Invalid or oversized desktop icon".into())}
  let bytes=(0..data.len()).step_by(2).map(|i|u8::from_str_radix(&data[i..i+2],16)).collect::<std::result::Result<Vec<_>,_>>().map_err(|_|"Invalid desktop icon")?;
  let width=u32::from_be_bytes(bytes[16..20].try_into().unwrap());let height=u32::from_be_bytes(bytes[20..24].try_into().unwrap());
  if &bytes[..8]!=b"\x89PNG\r\n\x1a\n"||bytes[8..12]!=[0,0,0,13]||&bytes[12..16]!=b"IHDR"||!(1..=128).contains(&width)||!(1..=128).contains(&height)||bytes[24]!=8||![2,6].contains(&bytes[25])||bytes[26..29]!=[0,0,0]{return Err("Desktop icon must be a bounded, noninterlaced RGB/RGBA PNG".into())}
 },_=>return Err("Choose one supported stock symbol or PNG icon".into())}}
 Ok(())
}
#[derive(Clone,Serialize,Deserialize)]
#[serde(deny_unknown_fields)]
struct Installed {manifest:Manifest,hash:String,grants:BTreeSet<String>,installed:bool,#[serde(default)] adopted_own_notes:bool}
#[derive(Default,Serialize,Deserialize)]
#[serde(default,deny_unknown_fields)]
struct Registry {apps:BTreeMap<String,Installed>}
struct Package {manifest:Manifest,bytes:Vec<u8>,hash:String,wasm:Vec<u8>}
struct Session {root:PathBuf,id:String,hash:String,module:Module,state:Value,frame:Value,pending:Option<(String,Value,String)>}
static SESSIONS:OnceLock<Mutex<BTreeMap<String,Session>>>=OnceLock::new();
fn hex(bytes:&[u8])->String {bytes.iter().map(|b|format!("{b:02x}")).collect()}
fn unhex<const N:usize>(s:&str)->Result<[u8;N]>{if s.len()!=N*2||!s.bytes().all(|c|c.is_ascii_digit()||(b'a'..=b'f').contains(&c)){return Err("Invalid publisher identity".into())}let mut b=[0;N];for i in 0..N{b[i]=u8::from_str_radix(&s[i*2..i*2+2],16).map_err(|e|e.to_string())?}Ok(b)}
fn valid_id(id:&str)->bool {id.len()<=128&&id.split('.').count()>=2&&id.split('.').all(|p|!p.is_empty()&&p.as_bytes()[0].is_ascii_lowercase()&&p.bytes().all(|b|b.is_ascii_lowercase()||b.is_ascii_digit()))}
fn validate(m:&Manifest)->Result<()>{validate_metadata(m)?;if m.schema!="onklav.app.v1"||m.runtime!="rust-wasm-ui.v1"||!valid_id(&m.id)||m.name.trim().is_empty()||m.name.encode_utf16().count()>60||crate::plain(&m.name,60)!=m.name||!(1..=2147483647).contains(&m.version)||m.capabilities.iter().any(|c|!CAPS.contains(&c.as_str())){return Err("Unsupported or invalid OnKlav app manifest".into())}unhex::<32>(&m.publisher)?;Ok(())}
pub fn signature_message(manifest:&[u8],wasm:&[u8])->Vec<u8>{let mut b=b"onklav.app.v1\0".to_vec();b.extend_from_slice(&(manifest.len() as u64).to_le_bytes());b.extend_from_slice(manifest);b.extend_from_slice(&Sha256::digest(wasm));b}
pub(crate) fn read_bounded(p:&Path,max:u64)->Result<Vec<u8>>{let meta=fs::symlink_metadata(p).map_err(|e|e.to_string())?;if !meta.is_file()||meta.len()>max{return Err("Invalid or oversized app file".into())}fs::read(p).map_err(|e|e.to_string())}
fn package(bytes:Vec<u8>)->Result<Package>{
 if bytes.len() as u64>MAX_PACKAGE{return Err("App package exceeds 700 KB".into())}
 let mut z=zip::ZipArchive::new(std::io::Cursor::new(&bytes)).map_err(|e|e.to_string())?;
 if z.len()!=3{return Err("Expected manifest.json, main.wasm and signature.ed25519 only".into())}
 let mut files=BTreeMap::new();
 for i in 0..z.len(){let mut f=z.by_index(i).map_err(|e|e.to_string())?;let name=f.name().to_string();if !["manifest.json","main.wasm","signature.ed25519"].contains(&name.as_str())||files.contains_key(&name)||f.unix_mode().is_some_and(|m|m&0o170000==0o120000){return Err("Unsafe or duplicate app entry".into())}
 let max=match name.as_str(){"manifest.json"=>16_384,"main.wasm"=>MAX_WASM,_=>64};let mut b=Vec::new();(&mut f).take(max as u64+1).read_to_end(&mut b).map_err(|e|e.to_string())?;if b.len()>max{return Err("App entry exceeds its limit".into())}files.insert(name,b);}
 let raw=files.get("manifest.json").ok_or("Missing manifest")?;let manifest:Manifest=serde_json::from_slice(raw).map_err(|e|e.to_string())?;validate(&manifest)?;
 let wasm=files.get("main.wasm").ok_or("Missing main.wasm")?.clone();let key=VerifyingKey::from_bytes(&unhex::<32>(&manifest.publisher)?).map_err(|e|e.to_string())?;
 if key.is_weak(){return Err("Invalid publisher key".into())}let sig=Signature::from_slice(files.get("signature.ed25519").ok_or("Missing signature")?).map_err(|e|e.to_string())?;
 key.verify_strict(&signature_message(raw,&wasm),&sig).map_err(|_|"App signature did not verify")?;
 compile(&wasm)?;let hash=hex(&Sha256::digest(&bytes));Ok(Package{manifest,bytes,hash,wasm})
}
fn engine()->&'static Engine{static E:OnceLock<Engine>=OnceLock::new();E.get_or_init(||{let mut c=Config::default();c.consume_fuel(true);Engine::new(&c)})}
fn compile(bytes:&[u8])->Result<Module>{let module=Module::new(engine(),bytes).map_err(|e|format!("Unsupported app program: {e}"))?;if module.imports().next().is_some(){return Err("App imports are forbidden; use OnKlav capability services".into())}Ok(module)}
fn invoke(module:&Module,input:&Value)->Result<Value>{
 let limits=StoreLimitsBuilder::new().memory_size(8*1024*1024).table_elements(2048).instances(1).memories(1).tables(1).trap_on_grow_failure(true).build();
 let mut store=Store::new(engine(),limits);store.limiter(|l|l);store.set_fuel(FUEL).map_err(|e|e.to_string())?;
 let linker=Linker::<wasmi::StoreLimits>::new(engine());let instance=linker.instantiate(&mut store,module).and_then(|p|p.start(&mut store)).map_err(|e|format!("App could not start: {e}"))?;
 let memory=instance.get_memory(&store,"memory").ok_or("Missing app memory")?;
 let alloc=instance.get_typed_func::<i32,i32>(&store,"onklav_alloc").map_err(|e|e.to_string())?;
 let step=instance.get_typed_func::<(i32,i32),i64>(&store,"onklav_step").map_err(|e|e.to_string())?;
 let bytes=serde_json::to_vec(input).map_err(|e|e.to_string())?;if bytes.len()>1_000_000{return Err("App input exceeds limit".into())}
 let ptr=alloc.call(&mut store,bytes.len() as i32).map_err(|e|format!("App allocation stopped: {e}"))? as u32;
 memory.write(&mut store,ptr as usize,&bytes).map_err(|e|e.to_string())?;
 let out=step.call(&mut store,(ptr as i32,bytes.len() as i32)).map_err(|e|format!("App execution stopped: {e}"))? as u64;
 let len=(out&0xffffffff) as usize;let pos=(out>>32) as usize;if len==0||len>1_000_000{return Err("App output exceeds limit".into())}
 let mut b=vec![0;len];memory.read(&store,pos,&mut b).map_err(|e|e.to_string())?;let v:Value=serde_json::from_slice(&b).map_err(|e|e.to_string())?;validate_frame(&v)?;Ok(v)
}
fn validate_frame(f:&Value)->Result<()>{
 if !f.is_object()||f["title"].as_str().is_none_or(|s|s.encode_utf16().count()>80)||serde_json::to_vec(&f["state"]).map_err(|e|e.to_string())?.len()>450_000{return Err("Invalid app UI frame".into())}
 if let Some(action)=f.get("back_action"){if action.as_str().is_none_or(|v|v.is_empty()||v.len()>96){return Err("Invalid Back Action".into())}}
 for key in ["scroll_key","drawer_scroll_key"] {if let Some(value)=f.get(key){if value.as_str().is_none_or(|v|v.encode_utf16().count()>512||v.chars().any(char::is_control)){return Err("Invalid Scroll Identity".into())}}}
 let mut ids=BTreeSet::new();let mut count=0;let mut chars=0;let mut webs=0;let mut canvases=0;let mut pdfs=0;let mut maps=0;let mut docked=0;
 for section in ["actions","body","drawer","menu"]{
 let nodes=f[section].as_array().ok_or("Missing app UI section")?;
 for n in nodes{count+=1;let kind=n["kind"].as_str().ok_or("Missing UI kind")?;if !["button","label","field","editor","record","toggle","select","chips","speech","speech_settings","web","icon","media_record","canvas","image_record","slider","palette","voice_dial","voice_capture","voice_clip","media_player","pdf_viewer","map_viewer"].contains(&kind){return Err("Unsupported SDK UI widget".into())}
 if ["voice_capture","voice_clip"].contains(&kind){if kind=="voice_capture"&&(section!="body"||n["on_change"].as_str().is_none_or(|v|v.is_empty()||v.len()>96)||n["peer"].as_str().is_none_or(|v|v.len()>64)){return Err("Invalid Voice Capture Component".into())}if kind=="voice_clip"&&n["clip"].as_str().is_none_or(|v|!crate::valid_id(v)){return Err("Invalid Voice Clip Component".into())}}
 if kind=="map_viewer"{maps+=1;if maps>1||section!="body"||f["scroll_body"]==true{return Err("Use one map viewer in a non-scrolling body".into())}crate::sdk_maps::validate(n)?;}
 if kind=="pdf_viewer"{pdfs+=1;if pdfs>1||section!="body"||f["scroll_body"]==true||n["file"].as_str().is_none_or(|s|s.len()!=32||!s.bytes().all(|b|b.is_ascii_digit()||(b'a'..=b'f').contains(&b))){return Err("Use one private PDF viewer in a non-scrolling body".into())}}
 for flag in ["keypad","accent","muted_key","utility_display","weather_display","downloaded","hide_unavailable"]{if n.get(flag).is_some_and(|v|!v.is_boolean()){return Err("Invalid utility display flag".into())}}
 if n["weather_display"]==true{if kind!="label"||n["forecast"].to_string().len()>16000||n["zone"].as_str().is_none_or(|z|z.len()>80)||n["forecast"]["points"].as_array().is_none_or(|p|p.is_empty()||p.len()>120){return Err("Use a bounded weather display".into())}}
 if let Some(v)=n.get("podcast_episode"){if kind!="record"||!crate::sdk_podcast_library::episode(v){return Err("Invalid podcast episode presentation".into())}}
 if let Some(v)=n.get("radio_station"){if kind!="record"||!crate::sdk_radio::station(v){return Err("Invalid radio station presentation".into())}}
 if kind=="media_player"{if section!="body"{return Err("Place media controls in the body".into())}for key in["radio","podcast","audiobook","dock"]{if n.get(key).is_some_and(|v|!v.is_boolean()){return Err("Invalid media presentation flag".into())}}if n["dock"]==true{docked+=1;if docked>1{return Err("Use one docked player".into())}}if n.get("favorite_label").is_some_and(|v|v.as_str().is_none_or(|s|s.len()>80)){return Err("Invalid favorite label".into())}}
 if kind=="voice_dial"&&(section!="body"||n["phase"]!="offline"||n["enabled"]!=false){return Err("Unsupported voice dial state".into())}
 let id=n["id"].as_str().ok_or("Missing widget identity")?;if id.is_empty()||id.len()>96||!ids.insert(id){return Err("Duplicate or invalid widget identity".into())}
 for key in ["delete","copy","retry","long_action","undo_action","redo_action","tools_action"] {if let Some(v)=n.get(key){let action=v.as_str().ok_or("Invalid UI Action")?;if action.is_empty()||action.len()>96||!ids.insert(action){return Err("Duplicate or Invalid UI Action".into())}}}
 if kind=="canvas"{canvases+=1;if canvases>1{return Err("Use One Visible Drawing Canvas".into())}if section!="body"||n["on_change"].as_str().is_none_or(|a|a.is_empty()||a.len()>96)||!["move","pen","highlight","text","erase"].contains(&n["tool"].as_str().unwrap_or(""))||n["width"].as_u64().is_none_or(|w|!(1..=12).contains(&w))||n["color"].as_u64().is_none_or(|c|c>u32::MAX as u64){return Err("Invalid Drawing Component".into())}if !n["document"].is_null()&&(n["document"]["id"].as_str().is_none_or(|i|!crate::valid_id(i))||n["document"]["revision"].as_u64().is_none()){return Err("Invalid Drawing Identity".into())}}
 if kind=="image_record"&&n["document"].as_str().is_none_or(|i|!crate::valid_id(i)){return Err("Invalid Image Record".into())}
 if kind=="slider"&&(n["value"].as_u64().is_none_or(|v|!(1..=12).contains(&v))){return Err("Invalid Stroke Width".into())}
 if kind=="palette"&&n["value"].as_u64().is_none_or(|v|v>u32::MAX as u64){return Err("Invalid Palette".into())}
 if kind=="media_record"{let u=n["url"].as_str().ok_or("Missing Video Address")?;if !crate::videos::page(u)||crate::videos::account(u)||n["video"].as_str()!=Some(crate::videos::video_id(u).as_str())||n["time"].as_u64().is_none(){return Err("Invalid Video Record".into())}chars+=u.encode_utf16().count();}
 for key in ["trailing","secondary_trailing"] {if let Some(t)=n.get(key){let id=t["id"].as_str().ok_or("Missing trailing action")?;if id.is_empty()||id.len()>96||!ids.insert(id){return Err("Invalid trailing action identity".into())}chars+=t["text"].as_str().ok_or("Missing action label")?.encode_utf16().count();}}
 if ["select","chips"].contains(&kind){let options=n["options"].as_array().ok_or("Missing choices")?;if options.len()>20{return Err("Too many choices".into())}for o in options{for k in ["text","value"]{chars+=o[k].as_str().ok_or("Invalid choice")?.encode_utf16().count();}}}
 for k in ["text","value","hint","detail"]{if let Some(s)=n[k].as_str(){chars+=s.encode_utf16().count();}}
 if kind=="web" {webs+=1;if webs>1||section!="body"{return Err("Use One Visible Web Component".into())}if !crate::feeds::https(n["url"].as_str().unwrap_or(""))||n["on_change"].as_str().is_none_or(|s|s.is_empty()||s.len()>96)||n["tab"].as_str().is_none_or(|s|!crate::valid_id(s))||!["load","back","forward","reload","find","find.next","find.previous","find.close","clear","reset"].contains(&n["command"].as_str().unwrap_or(""))||n["seq"].as_u64().is_none(){return Err("Invalid Web Component".into())}if let Some(provider)=n.get("provider"){if provider!="youtube"||!crate::videos::page(n["url"].as_str().unwrap())||crate::videos::account(n["url"].as_str().unwrap()){return Err("Unsupported Video Provider or Page".into())}}
 for key in ["on_notice"]{if let Some(action)=n.get(key){if action.as_str().is_none_or(|x|x.is_empty()||x.len()>96){return Err("Invalid Web Callback".into())}}}
 chars+=n["url"].as_str().unwrap().encode_utf16().count();chars+=n["find"].as_str().unwrap_or("").encode_utf16().count();}
 if kind=="speech"&&n["value"].as_str().is_none_or(|s|s.encode_utf16().count()>50000){return Err("Speech document exceeds limit".into())}
 if let Some(row)=n.get("row"){if !["button","field","icon","toggle"].contains(&kind)||row.as_str().is_none_or(|s|s.is_empty()||s.len()>32){return Err("Invalid widget row".into())}}
 if ["field","editor"].contains(&kind)&&n["limit"].as_u64().is_none_or(|m|m>100_000){return Err("Invalid field limit".into())}
 }}
 if let Some(c)=f.get("confirm").filter(|c|!c.is_null()){for field in ["title","text","accept","cancel"]{if c[field].as_str().is_none_or(|s|s.is_empty()||s.len()>500){return Err("Invalid app confirmation".into())}}}
 if count>100||chars>150_000{return Err("App UI resource limit reached".into())}Ok(())
}
fn dir(root:&Path)->Result<PathBuf>{let p=root.join("app-sdk");fs::create_dir_all(&p).map_err(|e|e.to_string())?;if fs::symlink_metadata(&p).map_err(|e|e.to_string())?.file_type().is_symlink(){return Err("Invalid SDK storage".into())}Ok(p)}
fn sub(root:&Path,name:&str)->Result<PathBuf>{let p=dir(root)?.join(name);fs::create_dir_all(&p).map_err(|e|e.to_string())?;if fs::symlink_metadata(&p).map_err(|e|e.to_string())?.file_type().is_symlink(){return Err("Invalid app storage".into())}Ok(p)}
fn load(root:&Path)->Result<Registry>{let p=dir(root)?.join("registry.json");if !p.exists(){return Ok(Registry::default())}let r:Registry=serde_json::from_slice(&read_bounded(&p,2_000_000)?).map_err(|e|e.to_string())?;if r.apps.len()>100{return Err("Too many apps".into())}for (id,a) in &r.apps{validate(&a.manifest)?;if *id!=a.manifest.id||a.hash.len()!=64||unhex::<32>(&a.hash).is_err()||!a.grants.is_subset(&a.manifest.capabilities){return Err("Invalid app registry".into())}}Ok(r)}
pub(crate) fn atomic(p:&Path,b:&[u8])->Result<()>{let tmp=p.with_extension(format!("{}.tmp",uuid::Uuid::new_v4().simple()));let result=(||{let mut f=OpenOptions::new().write(true).create_new(true).open(&tmp).map_err(|e|e.to_string())?;f.write_all(b).and_then(|_|f.sync_all()).map_err(|e|e.to_string())?;fs::rename(&tmp,p).map_err(|e|e.to_string())?;File::open(p.parent().ok_or("Invalid storage")?).and_then(|f|f.sync_all()).map_err(|e|e.to_string())?;Ok(())})();if result.is_err(){let _=fs::remove_file(tmp);}result}
fn save(root:&Path,r:&Registry)->Result<()>{if r.apps.len()>100{return Err("App limit reached".into())}let bytes=serde_json::to_vec(r).map_err(|e|e.to_string())?;if bytes.len()>2_000_000{return Err("App registry exceeds its limit".into())}atomic(&dir(root)?.join("registry.json"),&bytes)}
// Registry commits precede best-effort collection. Never delete retained current
// packages, user documents, links or unrelated names. Bound each maintenance pass.
fn collect_unused_packages(root:&Path,r:&Registry){
 let keep=r.apps.values().map(|a|a.hash.as_str()).collect::<BTreeSet<_>>();
 let Ok(entries)=fs::read_dir(root.join("app-sdk/packages"))else{return};
 for item in entries.take(512).flatten(){let Ok(kind)=item.file_type()else{continue};if !kind.is_file(){continue}let name=item.file_name();let Some(name)=name.to_str()else{continue};let Some(hash)=name.strip_suffix(".onklav-a")else{continue};if unhex::<32>(hash).is_ok()&&!keep.contains(hash){let _=fs::remove_file(item.path());}}
}
fn blob(root:&Path,hash:&str)->Result<PathBuf>{unhex::<32>(hash)?;Ok(sub(root,"packages")?.join(format!("{hash}.onklav-a")))}
pub(crate) fn scope(root:&Path,id:&str)->Result<PathBuf>{if !valid_id(id){return Err("Invalid app identity".into())}let p=sub(root,"data")?.join(id);fs::create_dir_all(&p).map_err(|e|e.to_string())?;if fs::symlink_metadata(&p).map_err(|e|e.to_string())?.file_type().is_symlink(){return Err("Invalid app data".into())}Ok(p)}
fn get<'a>(r:&'a Registry,id:&str)->Result<&'a Installed>{r.apps.get(id).filter(|a|a.installed).ok_or_else(||"This app is not installed".into())}
fn carrier_owner(a:&Installed)->Value{json!({"id":a.manifest.id,"publisher":a.manifest.publisher,"route":hex(&Sha256::digest(format!("onklav.carrier.v2\0{}\0{}",a.manifest.id,a.manifest.publisher).as_bytes()))[..32],"carrier":a.grants.contains("communications.walkie")&&a.grants.contains("storage.private")})}
fn carrier_app<'a>(r:&'a Registry,j:&Value)->Result<&'a Installed>{
 let owner=&j["owner"];let id=if owner.is_null(){"app.onklav.walkie"}else{owner["id"].as_str().ok_or("Missing carrier app identity")?};let a=get(r,id)?;
 if !owner.is_null()&&owner["publisher"]!=a.manifest.publisher{return Err("Carrier publisher changed".into())}
 capability(a,"storage.private")?;capability(a,"communications.walkie")?;Ok(a)
}
fn capability(a:&Installed,cap:&str)->Result<()>{if !a.grants.contains(cap){return Err(format!("{} access is off. Change this app's permissions in Settings > Apps.",match cap {"network.http"=>"HTTPS data requests","camera.capture"=>"Camera capture","notifications.post"=>"App notification","storage.secrets"=>"App secrets","storage.private"=>"Private document","clipboard.read"=>"Paste from clipboard","clipboard.write"=>"Copy to clipboard","files.export"=>"File export","share.text"=>"Text sharing","audio.record"=>"Microphone","audio.playback"=>"Voice playback","contacts.pick"=>"Contact picker","communications.walkie"=>"Walkie invitations","files.manage"=>"OnKlav file","files.external"=>"Android file",_=>"Requested"}))}Ok(())}
fn secret_args(service:&str,args:&Value)->Result<()>{
 let object=args.as_object().ok_or("Secret arguments must be an object")?;
 let allowed=if service=="secrets.set"{vec!["key","value"]}else{vec!["key"]};
 if object.keys().any(|k|!allowed.contains(&k.as_str())){return Err("Unsupported secret argument".into())}
 let key=args["key"].as_str().ok_or("Choose a secret key")?;
 if key.is_empty()||key.len()>64||!key.chars().all(|c|c.is_ascii_alphanumeric()||"._-".contains(c)){return Err("Secret keys use1–64 ASCII letters, digits, dots, underscores or hyphens".into())}
 if service=="secrets.set"&&args["value"].as_str().is_none_or(|v|v.encode_utf16().count()>4096){return Err("Secret values are text up to4096 UTF-16 units".into())}Ok(())
}
fn clock_args(args:&Value)->Result<()>{if args.as_object().is_none_or(|o|!o.is_empty()){return Err("Clock read takes no arguments".into())}Ok(())}
fn clock_reply(v:&Value)->Result<()>{
 let keys=["epoch_ms","monotonic_ms","timezone","offset_seconds","local_date","local_time","locale","first_weekday","hour_cycle","daylight_saving"];
 let o=v.as_object().ok_or("Invalid clock response")?;
 if o.len()!=keys.len()||o.keys().any(|k|!keys.contains(&k.as_str()))||v["epoch_ms"].as_i64().is_none()||v["monotonic_ms"].as_u64().is_none()||v["offset_seconds"].as_i64().is_none_or(|n|!(-64800..=64800).contains(&n))||v["first_weekday"].as_u64().is_none_or(|n|!(1..=7).contains(&n))||!matches!(v["hour_cycle"].as_str(),Some("h12"|"h23"))||!v["daylight_saving"].is_boolean(){return Err("Invalid clock response".into())}
 for (k,max) in [("timezone",128),("locale",128),("local_date",32),("local_time",32)]{if v[k].as_str().is_none_or(|s|s.is_empty()||s.len()>max||s.chars().any(char::is_control)){return Err("Invalid clock text".into())}}Ok(())
}
fn notification_query_reply(service:&str,v:&Value)->Result<()>{
 let allowed=match service{"notifications.schedule_at"=>vec!["scheduled","due_ms","exact"],"notifications.status"=>vec!["pending","due_ms","overdue","registered","exact","notifications_enabled"],_=>vec!["items","exact","notifications_enabled"]};
 let o=v.as_object().ok_or("Invalid reminder response")?;if o.len()!=allowed.len()||o.keys().any(|k|!allowed.contains(&k.as_str()))||v["exact"]!=false{return Err("Invalid reminder response".into())}
 let due=|v:&Value|v.as_u64().is_some_and(|t|t>0&&t<=253402300799999);
 if service=="notifications.schedule_at"{if v["scheduled"]!=true||!due(&v["due_ms"]){return Err("Invalid scheduled reminder response".into())}return Ok(())}
 if !v["notifications_enabled"].is_boolean(){return Err("Invalid notification access feedback".into())}
 if service=="notifications.status"{for k in ["pending","overdue","registered"]{if !v[k].is_boolean(){return Err("Invalid reminder status".into())}}if (v["pending"]==true&&!due(&v["due_ms"]))||(v["pending"]==false&&(!v["due_ms"].is_null()||v["overdue"]!=false||v["registered"]!=false)){return Err("Invalid reminder due time".into())}}
 else{let rows=v["items"].as_array().filter(|r|r.len()<=8).ok_or("Invalid pending reminders")?;let mut seen=BTreeSet::new();for r in rows{let o=r.as_object().ok_or("Invalid pending reminder")?;if o.len()!=4||o.keys().any(|k|!["key","due_ms","overdue","registered"].contains(&k.as_str()))||!due(&r["due_ms"])||!r["overdue"].is_boolean()||!r["registered"].is_boolean(){return Err("Invalid pending reminder fields".into())}let key=r["key"].as_str().ok_or("Invalid reminder key")?;if key.is_empty()||key.len()>48||!key.bytes().all(|c|c.is_ascii_alphanumeric()||b"._-".contains(&c))||!seen.insert(key){return Err("Invalid or duplicate reminder key".into())}}}Ok(())
}
fn device_args(service:&str,args:&Value)->Result<()>{
 let object=args.as_object().ok_or("Device arguments must be an object")?;
 if service=="camera.capture"{if !object.is_empty(){return Err("Camera capture takes no file paths or arguments".into())}return Ok(())}
 if service=="notifications.pending"{if !object.is_empty(){return Err("Pending reminder query takes no arguments".into())}return Ok(())}
 let allowed=if service=="notifications.schedule_at"{vec!["key","title","text","at_ms","missed","repeat_seconds","repeat_count","snooze_seconds"]}else if service=="notifications.schedule"{vec!["key","title","text","delay_seconds","missed","repeat_seconds","repeat_count","snooze_seconds"]}else if service=="notifications.show"{vec!["key","title","text"]}else{vec!["key"]};
 if service=="notifications.schedule_at"&&args["at_ms"].as_u64().is_none_or(|t|t==0||t>253402300799999){return Err("Invalid absolute reminder timestamp".into())}
 if service=="notifications.schedule"&&args["delay_seconds"].as_u64().is_none_or(|delay|!(60..=2_592_000).contains(&delay)){return Err("Notification delay must be60–2592000 whole seconds".into())}
 if matches!(service,"notifications.schedule"|"notifications.schedule_at"){
  if args.get("missed").is_some()&&!matches!(args["missed"].as_str(),Some("latest"|"skip")){return Err("Choose latest or skip missed reminders".into())}
  for (key,min,max) in [("repeat_seconds",60,2_592_000),("repeat_count",1,100),("snooze_seconds",60,86400)]{if args.get(key).is_some()&&args[key].as_u64().is_none_or(|n|n<min||n>max){return Err("Invalid reminder policy bound".into())}}
  if args.get("repeat_seconds").is_some()!=args.get("repeat_count").is_some(){return Err("Repeat interval and count are required together".into())}
 }
 if object.keys().any(|key|!allowed.contains(&key.as_str())){return Err("Unsupported notification argument".into())}
 let key=args["key"].as_str().ok_or("Choose a notification key")?;
 if key.is_empty()||key.len()>48||!key.chars().all(|c|c.is_ascii_alphanumeric()||"._-".contains(c)){return Err("Notification keys use1–48 ASCII letters, digits, dots, underscores or hyphens".into())}
 if matches!(service,"notifications.show"|"notifications.schedule"|"notifications.schedule_at"){for (key,limit) in [("title",80),("text",500)]{let text=args[key].as_str().ok_or("Missing notification text")?;if text.trim().is_empty()||text.encode_utf16().count()>limit||crate::plain(text,limit)!=text{return Err("Notification title/text is empty, too long or contains unsupported characters".into())}}}Ok(())
}
fn extra_file_permissions(a:&Installed,service:&str,args:&Value)->Result<()>{
 if matches!(service,"podcasts.download"|"podcasts.delete"|"podcasts.downloads"){capability(a,"storage.private")?;}
 if service=="library.share"{capability(a,"files.export")?;}
 if service=="handoff.file"{capability(a,"storage.private")?;capability(a,"files.export")?;}
 if service=="handoff.text"{capability(a,"share.text")?;}
 if matches!(service,"jobs.schedule"|"downloads.start"){capability(a,"network.http")?;capability(a,"storage.private")?;if args.get("account").is_some()||args["request"].get("account").is_some(){capability(a,"accounts.connect")?;}}
 if service=="media.play"{for item in args["queue"].as_array().ok_or("Choose a media queue")?{if item.get("file").is_some(){capability(a,"storage.private")?;}else{capability(a,"network.http")?;}if item.get("account").is_some(){capability(a,"accounts.connect")?;}}}

 if service.starts_with("accounts."){capability(a,"network.http")?;}
 if service=="http.request"&&args.get("account").is_some(){capability(a,"accounts.connect")?;}
 if service=="http.request"&&args.get("upload").is_some(){capability(a,"storage.private")?;}
 if service=="blobs.import"{capability(a,"files.external")?;}
 if service=="blobs.export"{capability(a,"files.export")?;}
 if matches!(service,"library.export"|"library.share")||(service=="library.external"&&matches!(args["operation"].as_str(),Some("export"|"share"))){capability(a,"files.export")?;}
 if service.starts_with("images.")&&service!="images.clear"{capability(a,"images.edit")?;capability(a,"storage.private")?;}Ok(())
}
// Structured denial metadata supplements the existing live authorization checks.
// Guests cannot grant themselves capabilities through this information.
fn service_requirements(service:&str,args:&Value)->Vec<&'static str>{
 let mut caps=if service.starts_with("ebooks."){vec!["ebooks.library","storage.private"]}else if matches!(service,"podcasts.search"|"podcasts.feed"){vec!["podcasts.library"]}else if matches!(service,"podcasts.download"|"podcasts.delete"|"podcasts.downloads"){vec!["podcasts.library","storage.private"]}else if service=="podcasts.play"{vec!["podcasts.library","audio.playback"]}else if service=="radio.search"{vec!["radio.stations"]}else if service=="radio.play"{vec!["radio.stations","audio.playback"]}else if service=="mail.attempt.new"{vec!["storage.private"]}else if service=="mail.mime.parse"{vec!["storage.private"]}else if service=="podcasts.parse"{vec!["storage.private"]}else if service.starts_with("maps.cache."){vec!["storage.private"]}else if service.starts_with("epub."){vec!["storage.private"]}else if service=="handoff.file"{vec!["apps.handoff","storage.private","files.export"]}else if service=="handoff.text"{vec!["apps.handoff","share.text"]}else if service=="handoff.handlers"{vec!["apps.handoff"]}else if service.starts_with("blobs.")||service.starts_with("collections.")||service.starts_with("records.")||service.starts_with("browser.")||service.starts_with("videos.")||service.starts_with("feeds.")&&service!="feeds.fetch"{vec!["storage.private"]}
 else if service.starts_with("jobs.")||service=="downloads.start"{vec!["background.tasks","storage.private","network.http"]}
 else if service.starts_with("media."){vec!["audio.playback"]}
 else if service.starts_with("accounts."){vec!["network.http","accounts.connect"]}
 else if service=="http.request"{if args.get("account").is_some(){vec!["network.http","accounts.connect"]}else{vec!["network.http"]}}
 else if let Some(cap)=crate::sdk_devices::cap(service){if cap=="clock.read"{vec![]}else{vec![cap]}}
 else if service=="camera.capture"{vec!["camera.capture"]}
 else if matches!(service,"notifications.show"|"notifications.schedule"|"notifications.schedule_at"|"notifications.status"|"notifications.pending"|"notifications.cancel"){vec!["notifications.post"]}
 else if matches!(service,"secrets.get"|"secrets.set"|"secrets.delete"){vec!["storage.secrets"]}
 else if service.starts_with("drawing."){vec!["storage.private","images.edit"]}
 else if service.starts_with("walkie."){if matches!(service,"walkie.connect"|"walkie.accept"){vec!["storage.private","communications.walkie"]}else{vec!["storage.private"]}}
 else if service.starts_with("library."){vec![if service=="library.external"{"files.external"}else{"files.manage"}]}
 else{match service{
 "clipboard.read"=>vec!["clipboard.read"],"clipboard.write"=>vec!["clipboard.write"],"contacts.pick"=>vec!["contacts.pick"],"files.export"=>vec!["files.export"],"share.text"=>vec!["share.text"],"feeds.fetch"=>vec!["network.feeds","storage.private"],"web.open"=>vec!["network.web"],"links.external"=>vec!["links.external"],"communications.walkie"=>vec!["communications.walkie"],
 "images.clear"=>vec!["storage.private"],"images.import"=>vec!["files.images","images.edit","storage.private"],"images.text"=>vec!["images.edit","storage.private"],"images.export"=>{let mut c=vec!["images.edit","storage.private"];if let Ok(cap)=image_export_cap(args){c.push(cap)}c},_=>vec![]}};
 if service=="http.request"&&args.get("upload").is_some(){caps.push("storage.private")}
 if service=="blobs.import"{caps.push("files.external")}
 if service=="blobs.export"||matches!(service,"library.export"|"library.share")||service=="library.external"&&matches!(args["operation"].as_str(),Some("export"|"share")){caps.push("files.export")}caps
}
pub(crate) fn private_size(root:&Path, exclude:&Path)->Result<u64>{
 let mut total=0u64;for base in [root.to_path_buf(),root.join("notes")] {if !base.exists(){continue}for e in fs::read_dir(&base).map_err(|e|e.to_string())? {let p=e.map_err(|e|e.to_string())?.path();if p==exclude{continue}let m=fs::symlink_metadata(&p).map_err(|e|e.to_string())?;if m.is_file(){total=total.checked_add(m.len()).ok_or("Private data limit exceeded")?;}}}Ok(total)
}
fn records(root:&Path,a:&Installed,effect:&Value)->Result<Value>{
 capability(a,"storage.private")?;let data=if a.manifest.id=="app.onklav.notes"{crate::content::notes_root(root)?}else{scope(root,&a.manifest.id)?};let args=&effect["args"];
 let list=||if a.manifest.id=="app.onklav.notes"{crate::content::note_list(root)}else{crate::list_notes(&data)};
 let read=|id:&str|if a.manifest.id=="app.onklav.notes"{crate::content::note_read(root,id)}else{crate::read_note(&data,id)};
 match effect["service"].as_str().unwrap_or(""){
 "records.page"=>{let query=args["query"].as_str().unwrap_or("").to_lowercase();let notes:Vec<_>=list()?.into_iter().filter(|n|n.title.to_lowercase().contains(&query)||n.text.to_lowercase().contains(&query)).collect();let total=notes.len();let offset=args["offset"].as_u64().unwrap_or(0).min(10000) as usize;let items:Vec<Value>=notes.into_iter().skip(offset).take(40).map(|n|json!({"id":n.id,"title":n.title,"preview":crate::plain(n.text.lines().next().unwrap_or(""),80),"updated":n.updated})).collect();Ok(json!({"items":items,"total":total,"offset":offset}))},
 "records.list"=>{let query=args["query"].as_str().unwrap_or("").to_lowercase();let result:Vec<Value>=list()?.into_iter().filter(|n|n.title.to_lowercase().contains(&query)||n.text.to_lowercase().contains(&query)).take(40).map(|n|json!({"id":n.id,"title":n.title,"preview":crate::plain(n.text.lines().next().unwrap_or(""),80),"updated":n.updated})).collect();Ok(json!(result))},
 "records.current"=>{
  let last=data.join("last.json");let note=if last.exists(){String::from_utf8(read_bounded(&last,32)?).ok().and_then(|id|read(&id).ok())}else{list()?.into_iter().next()};
  Ok(json!(note.unwrap_or(Note{id:uuid::Uuid::new_v4().simple().to_string(),title:String::new(),text:String::new(),updated:0})))
 },
 "records.new"=>{let n=Note{id:uuid::Uuid::new_v4().simple().to_string(),title:String::new(),text:String::new(),updated:0};atomic(&data.join("last.json"),n.id.as_bytes())?;Ok(json!(n))},
 "records.load"=>{let n=read(args["id"].as_str().ok_or("Missing record")?)?;atomic(&data.join("last.json"),n.id.as_bytes())?;Ok(json!(n))},
 "records.save"=>{let mut n:Note=serde_json::from_value(args["note"].clone()).map_err(|e|e.to_string())?;crate::validate(&n)?;if a.manifest.id=="app.onklav.notes"&&n.updated>0{crate::content::note_read(root,&n.id)?;}let encoded=serde_json::to_vec(&n).map_err(|e|e.to_string())?;let total=if a.manifest.id=="app.onklav.notes"{crate::content::note_bytes(root,&n.id)?}else{private_size(&data,&crate::location(&data,&n.id)?)?};if total+encoded.len() as u64>crate::sdk_storage::limits(&scope(root,&a.manifest.id)?)?.documents{return Err("App's 16 MB private document quota reached".into())}n.updated=std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).map_err(|e|e.to_string())?.as_millis() as u64;if a.manifest.id=="app.onklav.notes"{crate::content::note_save(root,&n)?;}else{crate::save_note(&data,&n)?;}atomic(&data.join("last.json"),n.id.as_bytes())?;Ok(json!(n))},
 "records.delete" if a.manifest.id=="app.onklav.notes"=>crate::content::service(root,"library.trash",args),
 "records.delete"=>{fs::remove_file(crate::location(&data,args["id"].as_str().ok_or("Missing record")?)?).map_err(|e|e.to_string())?;Ok(json!(true))},_=>Err("Unsupported private document service".into())}
}
// Only a single explicit host selection reaches a guest, never a provider URI or address book.
fn selected_contact(v:&Value)->Result<Value>{
 let peer=crate::messaging::phone(v["phone"].as_str().ok_or("Choose a contact with a phone number")?)?;
 let name=v["name"].as_str().ok_or("Invalid contact name")?;
 if name.encode_utf16().count()>80||name.chars().any(char::is_control){return Err("Invalid contact name".into())}
 Ok(json!({"phone":peer,"name":name}))
}
fn image_export_cap(args:&Value)->Result<&'static str>{match args["mode"].as_str(){Some("files")=>Ok("files.export"),Some("share")=>Ok("share.text"),Some("copy")=>Ok("clipboard.write"),_=>Err("Invalid Image Export Mode".into())}}
fn advance(root:&Path,a:&Installed,s:&mut Session,mut event:Value,gesture:bool)->Result<Value>{
 for _ in 0..6 {
 let frame=invoke(&s.module,&json!({"state":s.state,"event":event}))?;
 s.state=frame["state"].clone();s.frame=frame.clone();let effect=&frame["effect"];
 if effect.is_null(){return Ok(json!({"frame":frame}))}
 let service=effect["service"].as_str().ok_or("Invalid app service")?;let then=effect["then"].as_str().ok_or("Missing app continuation")?.to_string();
 if then.len()>96{return Err("Invalid continuation".into())}
 if let Some(cap)=service_requirements(service,&effect["args"]).into_iter().find(|cap|!a.grants.contains(*cap)){
  let error=capability(a,cap).err().ok_or("Permission check failed")?;
  event=json!({"action":then,"reply":{"ok":false,"error":error,"access":{"capability":cap,"declared":a.manifest.capabilities.contains(cap),"service":service}}});
  continue
 }
 let reply=if service=="places.search"{crate::sdk_places::service(&effect["args"])}else if service=="mail.attempt.new"{capability(a,"storage.private").map(|_|json!({"id":uuid::Uuid::new_v4().simple().to_string()}))}else if service=="mail.mime.parse"{capability(a,"storage.private").and_then(|_|crate::sdk_mime::parse(&scope(root,&a.manifest.id)?,&effect["args"]))}else if service=="podcasts.parse"{capability(a,"storage.private").and_then(|_|crate::sdk_podcasts::parse(&effect["args"]))}else if service.starts_with("maps.cache."){capability(a,"storage.private").and_then(|_|crate::sdk_map_cache::service(&scope(root,&a.manifest.id)?,&a.manifest.publisher,service,&effect["args"]))}else if service.starts_with("epub."){capability(a,"storage.private").and_then(|_|crate::sdk_epub::service(&scope(root,&a.manifest.id)?,service,&effect["args"]))}else if service.starts_with("blobs.")&&!matches!(service,"blobs.import"|"blobs.export"){capability(a,"storage.private").and_then(|_|crate::sdk_storage::service(&scope(root,&a.manifest.id)?,service,&effect["args"]))}else if service.starts_with("collections."){capability(a,"storage.private").and_then(|_|crate::sdk_collections::service(&scope(root,&a.manifest.id)?,service,&effect["args"]))}else if service.starts_with("library.")&&!matches!(service,"library.open"|"library.import"|"library.export"|"library.share"|"library.external"){
 capability(a,"files.manage").and_then(|_|{if !matches!(service,"library.list"|"library.info")&&!gesture{return Err("This action needs a direct button tap".into())}crate::content::service(root,service,&effect["args"])})
 }else if service.starts_with("walkie."){capability(a,"storage.private").and_then(|_|{
  if ["walkie.connect","walkie.accept"].contains(&service){capability(a,"communications.walkie")?;if !gesture{return Err("This action needs a direct button tap".into())}}
  crate::walkie_store::service(&scope(root,&a.manifest.id)?,service,&effect["args"])})}else if service.starts_with("drawing."){capability(a,"storage.private").and_then(|_|capability(a,"images.edit")).and_then(|_|crate::drawing::service(&scope(root,&a.manifest.id)?,service,&effect["args"]))}else if service.starts_with("videos."){capability(a,"storage.private").and_then(|_|crate::videos::service(&scope(root,&a.manifest.id)?,service,&effect["args"]))}else if service.starts_with("browser."){capability(a,"storage.private").and_then(|_|crate::browser::service(&scope(root,&a.manifest.id)?,service,&effect["args"]))}else if service.starts_with("records."){records(root,a,effect)}else if service.starts_with("feeds.") && service!="feeds.fetch" {capability(a,"storage.private").and_then(|_|crate::feeds::service(&scope(root,&a.manifest.id)?,service,&effect["args"]))}else{
  let cap=match service{other if other.starts_with("ebooks.")=>"ebooks.library",other if crate::sdk_handoff::supported(other)=>"apps.handoff",other if crate::sdk_devices::cap(other).is_some()=>crate::sdk_devices::cap(other).unwrap(),"clock.read"|"calendar.expand"=>"clock.read","jobs.schedule"|"jobs.status"|"jobs.cancel"|"jobs.list"|"downloads.start"=>"background.tasks","podcasts.search"|"podcasts.feed"|"podcasts.play"|"podcasts.download"|"podcasts.delete"|"podcasts.downloads"=>"podcasts.library","radio.search"|"radio.play"=>"radio.stations","media.play"|"media.status"|"media.control"=>"audio.playback","accounts.connect"|"accounts.list"|"accounts.refresh"|"accounts.disconnect"=>"accounts.connect","http.request"=>"network.http","blobs.import"|"blobs.export"=>"storage.private","camera.capture"=>"camera.capture","notifications.show"|"notifications.schedule"|"notifications.schedule_at"|"notifications.status"|"notifications.pending"|"notifications.cancel"=>"notifications.post","secrets.get"|"secrets.set"|"secrets.delete"=>"storage.secrets","library.open"|"library.import"|"library.export"|"library.share"=>"files.manage","library.external"=>"files.external","communications.walkie"=>"communications.walkie","contacts.pick"=>"contacts.pick","clipboard.read"=>"clipboard.read","clipboard.write"=>"clipboard.write","files.export"=>"files.export","share.text"=>"share.text","feeds.fetch"=>"network.feeds","web.open"=>"network.web","links.external"=>"links.external","images.clear"=>"storage.private","images.import"=>"files.images","images.text"=>"images.edit","images.export"=>image_export_cap(&effect["args"])?,_=>return Err("Unsupported app capability service".into())};
  match (if matches!(service,"clock.read"|"calendar.expand"|"calendar.resolve"|"calendar.localize"){Ok(())}else{capability(a,cap)}).and_then(|_|extra_file_permissions(a,service,&effect["args"])){Err(e)=>Err(e),Ok(()) if !gesture && !matches!(service,"clock.read"|"calendar.expand"|"calendar.resolve"|"calendar.localize"|"connectivity.read"|"notifications.status"|"notifications.pending") && !matches!(service,"ebooks.search"|"podcasts.search"|"podcasts.feed"|"podcasts.downloads"|"radio.search") && service!="feeds.fetch" && service!="http.request" && service!="accounts.list" && service!="accounts.refresh" && !matches!(service,"jobs.status"|"jobs.list"|"media.status") && !(service=="library.external"&&matches!(effect["args"]["operation"].as_str(),Some("list"|"info")))=>Err("This action needs a direct button tap".into()),Ok(())=>{
   if crate::sdk_handoff::supported(service){crate::sdk_handoff::request(service,&effect["args"])?;}if crate::sdk_devices::cap(service).is_some(){crate::sdk_devices::request(service,&effect["args"])?;}if service=="clock.read"{clock_args(&effect["args"])?;}if service=="calendar.expand"{crate::sdk_calendar::request(&effect["args"])?;}
   if service.starts_with("ebooks."){crate::sdk_ebooks::request(service,&effect["args"])?;capability(a,"storage.private")?;}if matches!(service,"podcasts.search"|"podcasts.feed"|"podcasts.play"|"podcasts.download"|"podcasts.delete"|"podcasts.downloads"){crate::sdk_podcast_library::request(service,&effect["args"])?;}if service.starts_with("radio."){crate::sdk_radio::request(service,&effect["args"])?;}if service.starts_with("jobs.")||service.starts_with("media.")||service=="downloads.start"{crate::sdk_tasks::request(a.manifest.network.as_ref(),service,&effect["args"])?;}
   if service.starts_with("accounts."){crate::sdk_accounts::request(a.manifest.network.as_ref(),service,&effect["args"])?;}if service=="http.request"{crate::sdk_http::request(a.manifest.network.as_ref(),&effect["args"])?;}
   if matches!(service,"blobs.import"|"blobs.export"){crate::sdk_storage::native_args(service,&effect["args"])?;}
   if service=="camera.capture"||service.starts_with("notifications."){device_args(service,&effect["args"])?;}
   if service.starts_with("secrets."){secret_args(service,&effect["args"])?;}
   let text=effect["args"]["text"].as_str().unwrap_or("");if text.encode_utf16().count()>100_100{return Err("Service payload exceeds limit".into())}
   if service.starts_with("images.") && service!="images.clear"{capability(a,"images.edit")?;capability(a,"storage.private")?;if service!="images.import"{crate::drawing::prepare(&scope(root,&a.manifest.id)?,&effect["args"])?;}}
   let mut effect=effect.clone();if service=="links.external"&&effect["args"].get("target").is_some_and(|v|v!="youtube"){return Err("Unsupported External App Target".into())}if (service=="web.open"||service=="links.external")&&!crate::feeds::https(effect["args"]["url"].as_str().unwrap_or("")){return Err("Use an HTTPS article address".into())}if service=="feeds.fetch"{capability(a,"storage.private")?;effect["args"]=crate::feeds::prepare(&scope(root,&a.manifest.id)?,&effect["args"])?;}
   let token=uuid::Uuid::new_v4().simple().to_string();s.pending=Some((token.clone(),effect.clone(),then));return Ok(json!({"frame":frame,"pending":{"token":token,"service":service,"args":effect["args"]}}));
  }}
 };
 event=match reply{Ok(v)=>json!({"action":then,"reply":{"ok":true,"value":v}}),Err(e)=>json!({"action":then,"reply":{"ok":false,"error":e}})};
 }
 Err("App service loop limit reached".into())
}
fn staged(root:&Path)->Result<Package>{package(read_bounded(&root.join("app-install.zip"),MAX_PACKAGE)?)}
fn descriptor(p:&Package,r:&Registry)->Result<Value>{
 if let Some(old)=r.apps.get(&p.manifest.id){if old.manifest.publisher!=p.manifest.publisher{return Err("This app ID belongs to a different publisher".into())}if p.manifest.version<old.manifest.version||(p.manifest.version==old.manifest.version&&(old.installed||p.hash!=old.hash)){return Err("Install a newer signed version of this app".into())}}
 Ok(json!({"manifest":p.manifest,"hash":p.hash,"publisher_fingerprint":hex(&Sha256::digest(unhex::<32>(&p.manifest.publisher)?)),"update":r.apps.get(&p.manifest.id).is_some_and(|a|a.installed),"signature_verified":true}))
}
pub fn dispatch(root:&Path,j:&Value)->Result<Value>{
 let mut sessions=SESSIONS.get_or_init(||Mutex::new(BTreeMap::new())).lock().map_err(|_|"App runtime unavailable")?;let mut r=load(root)?;
 let id=j["id"].as_str().unwrap_or("");
 match j["action"].as_str().ok_or("Missing SDK operation")?{
 "list"=>Ok(json!(r.apps.values().collect::<Vec<_>>())),
 "files_preview"=>{let a=get(&r,"app.onklav.files")?;capability(a,"files.manage")?;let file=crate::content::resolve(root,id)?;let mime=file["mime"].as_str().unwrap_or("");if !mime.starts_with("image/")&&!mime.starts_with("video/"){return Err("Preview is available for images and video".into())}Ok(file)},
 "storage_admin"=>{let a=get(&r,id)?;crate::sdk_storage::admin(&scope(root,&a.manifest.id)?,&j["args"])},
 "library_backup"=>{
  if j["operation"]=="restore"&&sessions.values().any(|s|s.root==root){return Err("Close open apps before restoring library content".into())}
  let app=match j["kind"].as_str(){Some("rss")=>"app.onklav.rss",Some("reader")=>"app.onklav.reader",Some("browser")=>"app.onklav.browser",Some("youtube")=>"app.onklav.youtube",Some("walkie")=>"app.onklav.walkie",_=>"app.onklav.annotation"};
  let data=scope(root,app)?;crate::library_backup::dispatch(root,&data,j)
 },
 "notes_backup"=>{
  if j["operation"]=="restore"&&sessions.values().any(|s|s.root==root){return Err("Close open apps before restoring Notes".into())}
  crate::content::notes_backup(root,j["operation"].as_str().ok_or("Choose a Notes backup operation")?,j["sha256"].as_str().unwrap_or(""))
 },
 "walkie_routes"=>Ok(json!(r.apps.values().filter(|a|a.installed&&a.grants.contains("storage.private")&&a.grants.contains("communications.walkie")).map(carrier_owner).collect::<Vec<_>>())),
 "walkie_preferences"=>{let a=get(&r,"app.onklav.walkie")?;capability(a,"storage.private")?;let data=scope(root,&a.manifest.id)?;match j["args"].as_object(){Some(_)=>crate::walkie_store::service(&data,"walkie.preferences",&j["args"]),None=>crate::walkie_store::service(&data,"walkie.state",&json!({}))}},
 "walkie_carrier"=>{let a=carrier_app(&r,j)?;capability(a,"storage.private")?;capability(a,"communications.walkie")?;crate::walkie_carrier::service(&scope(root,&a.manifest.id)?,&j["args"])},
 "walkie_session"=>{let a=get(&r,"app.onklav.walkie")?;capability(a,"storage.private")?;if !["state","end"].contains(&j["args"]["operation"].as_str().unwrap_or("")){capability(a,"communications.walkie")?;}crate::walkie_session::host(&scope(root,&a.manifest.id)?,&j["args"])},
 "walkie_clip_info"=>{let a=carrier_app(&r,j)?;capability(a,"storage.private")?;capability(a,"communications.walkie")?;crate::walkie_clips::component(&scope(root,&a.manifest.id)?,j["clip"].as_str().ok_or("Choose a voice clip")?,false)},
 "walkie_archive_list"=>{let a=carrier_app(&r,j)?;capability(a,"storage.private")?;capability(a,"communications.walkie")?;crate::walkie_clips::service(&scope(root,&a.manifest.id)?,"walkie.clips",&json!({}))},
 "walkie_archive"=>{let a=get(&r,"app.onklav.walkie")?;capability(a,"storage.private")?;crate::walkie_clips::host(&scope(root,&a.manifest.id)?,&j["args"])},
 "inspect"=>descriptor(&staged(root)?,&r),
 "install"=>{let p=staged(root)?;descriptor(&p,&r)?;if j["hash"]!=p.hash{return Err("Package changed after review. Review it again.".into())}let grants:BTreeSet<String>=serde_json::from_value(j["grants"].clone()).map_err(|e|e.to_string())?;if !grants.is_subset(&p.manifest.capabilities){return Err("Undeclared app permission".into())}
 if p.manifest.capabilities.contains("communications.walkie"){crate::walkie_session::end(&scope(root,&p.manifest.id)?,"cancelled")?;}
 atomic(&blob(root,&p.hash)?,&p.bytes)?;r.apps.insert(p.manifest.id.clone(),Installed{manifest:p.manifest.clone(),hash:p.hash,grants,installed:true,adopted_own_notes:r.apps.get(&p.manifest.id).is_some_and(|a|a.adopted_own_notes)});save(root,&r)?;sessions.retain(|_,s|s.root!=root||s.id!=p.manifest.id);collect_unused_packages(root,&r);Ok(json!(p.manifest))},
 "permissions"=>{let a=r.apps.get_mut(id).filter(|a|a.installed).ok_or("App is not installed")?;let grants:BTreeSet<String>=serde_json::from_value(j["grants"].clone()).map_err(|e|e.to_string())?;if !grants.is_subset(&a.manifest.capabilities){return Err("Undeclared app permission".into())}if a.manifest.capabilities.contains("communications.walkie")&&grants.contains("communications.walkie"){crate::walkie_session::end(&scope(root,id)?,"cancelled")?;}let carrier=a.manifest.capabilities.contains("communications.walkie");a.grants=grants;save(root,&r)?;if carrier{let _=crate::walkie_session::end(&scope(root,id)?,"cancelled");}sessions.retain(|_,s|s.root!=root||s.id!=id);Ok(json!(true))},
 "remove"=>{let a=r.apps.get_mut(id).ok_or("App is not installed")?;let carrier=a.manifest.capabilities.contains("communications.walkie");a.installed=false;a.grants.clear();save(root,&r)?;if carrier{let _=crate::walkie_session::end(&scope(root,id)?,"cancelled");}sessions.retain(|_,s|s.root!=root||s.id!=id);if j["delete_data"]==true{if id=="app.onklav.notes"{crate::content::delete_notes(root)?;}fs::remove_dir_all(scope(root,id)?).map_err(|e|e.to_string())?;}Ok(json!(true))},
 "adopt_own_feeds"=>{
  let a=get(&r,"app.onklav.rss")?;
  if a.manifest.publisher!=j["publisher"]{return Err("Only the included RSS publisher can import OnKlav feeds".into())}
  capability(a,"storage.private")?;
  if sessions.values().any(|s|s.root==root&&s.id==a.manifest.id){return Err("Close RSS before importing previous sources".into())}
  crate::feeds::adopt_own(&scope(root,&a.manifest.id)?,j["feeds"].as_str().ok_or("Missing previous feed list")?)
 },
 "adopt_own_notes"=>{
  let a=get(&r,"app.onklav.notes")?;if a.manifest.publisher!=j["publisher"]{return Err("Only the included Notes publisher can import OnKlav notes".into())}
  capability(a,"storage.private")?;if !a.adopted_own_notes{
   let old=crate::list_notes(root)?;let total:usize=old.iter().map(|n|n.text.len()+n.title.len()).sum();if total>16*1024*1024{return Err("Export existing notes before migrating this library larger than 16 MB".into())}
   let data=scope(root,"app.onklav.notes")?;
   for n in old {if !crate::location(&data,&n.id)?.exists(){crate::save_note(&data,&n)?;}}
   if let Some(last)=j["last"].as_str().filter(|s|crate::valid_id(s)){if crate::location(&data,last)?.exists(){atomic(&data.join("last.json"),last.as_bytes())?;}}
   r.apps.get_mut("app.onklav.notes").unwrap().adopted_own_notes=true;save(root,&r)?;
  }Ok(json!(true))
 },
 "audiobook_authorize"|"background_authorize"|"background_blob"|"media_authorize"|"media_blob"|"task_cleanup"=>{
  let owner=&j["owner"];let a=r.apps.get(owner["id"].as_str().ok_or("Missing task owner")?).ok_or("App unavailable")?;
  if owner["publisher"]!=a.manifest.publisher||(j["action"]!="task_cleanup"&&owner["hash"]!=a.hash){return Err("App changed; restart this operation".into())}
  let data=scope(root,&a.manifest.id)?;
  if j["action"]=="task_cleanup"{let file=j["file"].as_str().ok_or("Missing staging file")?;let info=crate::sdk_storage::service(&data,"blobs.info",&json!({"id":file}))?;if info["ready"]==true{return Err("Cleanup cannot delete a completed file".into())}return crate::sdk_storage::service(&data,"blobs.delete",&json!({"id":file}))}
  if !a.installed{return Err("App removed".into())}
  if j["action"]=="audiobook_authorize"{capability(a,"audiobooks.library")?;capability(a,"storage.private")?;if j["args"].as_object().is_none_or(|o|!o.is_empty()){return Err("Audiobook authorization accepts no paths or URLs".into())}return Ok(json!({"id":a.manifest.id,"publisher":a.manifest.publisher,"hash":a.hash}))}
  let media=j["action"].as_str().unwrap().starts_with("media_");capability(a,if media{"audio.playback"}else{"background.tasks"})?;
  if j["action"]=="background_blob"||j["action"]=="media_blob"{capability(a,"storage.private")?;let operation=j["service"].as_str().ok_or("Missing private file operation")?;if media{if operation!="resolve"{return Err("Media may only resolve immutable files".into())}return crate::sdk_storage::resolve(&data,j["args"]["id"].as_str().ok_or("Choose a media file")?)}if !["blobs.begin","blobs.append","blobs.finish","blobs.info","blobs.delete"].contains(&operation){return Err("Unsupported job file action".into())}if operation=="blobs.begin"{return crate::sdk_storage::begin_job(&data,&j["args"],j["job_token"].as_str().ok_or("Missing host job token")?)}return crate::sdk_storage::service(&data,operation,&j["args"])}
  if media&&j["args"].get("audiobook_track").is_some(){capability(a,"audiobooks.library")?;capability(a,"storage.private")?;let args=&j["args"];if args.as_object().is_none_or(|o|o.len()!=1)||!crate::sdk_radio::station(&args["audiobook_track"]){return Err("Invalid host audiobook handle".into())}return Ok(json!({"audiobook_track":args["audiobook_track"],"id":a.manifest.id,"publisher":a.manifest.publisher,"hash":a.hash}))}
  if media&&j["args"].get("podcast_episode").is_some(){if j["args"]["podcast_local"]==true{capability(a,"storage.private")?;}capability(a,"podcasts.library")?;let args=&j["args"];if args.as_object().is_none_or(|o|o.len()!=if args["podcast_local"]==true{2}else{1})||!crate::sdk_radio::station(&args["podcast_episode"]){return Err("Invalid host podcast stream handle".into())}return Ok(json!({"podcast_episode":args["podcast_episode"],"id":a.manifest.id,"publisher":a.manifest.publisher,"hash":a.hash}))}
  if media&&j["args"].get("radio_station").is_some(){capability(a,"radio.stations")?;let args=&j["args"];if args.as_object().is_none_or(|o|o.len()!=1)||!crate::sdk_radio::station(&args["radio_station"]){return Err("Invalid host radio stream handle".into())}return Ok(json!({"radio_station":args["radio_station"],"id":a.manifest.id,"publisher":a.manifest.publisher,"hash":a.hash}))}
  let mut http=json!({"service":"http.request","owner":{"id":a.manifest.id,"publisher":a.manifest.publisher},"args":j["args"],"origins":a.manifest.network.as_ref().map(|p|p.origins.clone()).unwrap_or_default(),"providers":a.manifest.network.as_ref().map(|p|p.accounts.clone()).unwrap_or_default()});
  if j["args"].is_object()&&!j["args"].as_object().unwrap().is_empty(){capability(a,"network.http")?;if j["args"].get("account").is_some(){capability(a,"accounts.connect")?;}crate::sdk_http::request(a.manifest.network.as_ref(),&j["args"])?;}http["app_name"]=json!(a.manifest.name);Ok(http)
 },
 "export"=>{let a=get(&r,id)?;let b=read_bounded(&blob(root,&a.hash)?,MAX_PACKAGE)?;if hex(&Sha256::digest(&b))!=a.hash{return Err("Installed package is damaged".into())}atomic(&root.join("app-export.onklav-a"),&b)?;Ok(json!(true))},
 "message_import"=>{let mime=j["mime"].as_str().ok_or("Missing media type")?;let parent=if mime.starts_with("image/"){"images"}else if mime.starts_with("video/"){"video"}else if mime.starts_with("audio/"){"audio"}else{return Err("Choose a photo, video or audio file".into())};crate::content::import_staged(root,parent,j["name"].as_str().ok_or("Missing media name")?,mime,"host.messenger","Messages",true)},
 "launch"=>{let a=get(&r,id)?;let p=package(read_bounded(&blob(root,&a.hash)?,MAX_PACKAGE)?)?;if p.hash!=a.hash{return Err("Installed package is damaged".into())}if sessions.values().filter(|s|s.root==root).count()>=8{return Err("Close another app before opening this one".into())}
 let token=uuid::Uuid::new_v4().simple().to_string();let mut s=Session{root:root.to_path_buf(),id:id.into(),hash:a.hash.clone(),module:compile(&p.wasm)?,state:Value::Null,frame:Value::Null,pending:None};let mut response=advance(root,a,&mut s,json!({"action":"start"}),false)?;response["session"]=json!(token);response["owner"]=carrier_owner(a);sessions.insert(token,s);Ok(response)},
 "native_camera"|"event"|"reply"|"authorize"|"close"|"check"|"host_clipboard"|"host_speech"|"speech_event"|"host_web"|"web_event"|"host_image"|"host_canvas"|"canvas_event"|"host_drawing_image"|"host_voice"|"voice_prepare"|"voice_commit"|"voice_played"|"video_open"|"walkie_refresh"|"voice_floor_request"|"voice_floor_release"|"camera_host"|"handoff_host"|"blob_host"|"library_host"|"note_open"|"web_file_save"|"media_component"|"pdf_component"|"map_component"|"map_event"=>{
 let token=j["session"].as_str().ok_or("Missing app session")?;
 if j["action"]=="close"{if sessions.get(token).is_some_and(|s|s.root!=root){return Err("Session belongs to another host".into())}if let Some(s)=sessions.get(token){if r.apps.get(&s.id).is_some_and(|a|a.manifest.capabilities.contains("communications.walkie")){crate::walkie_session::end(&scope(root,&s.id)?,"cancelled")?;}}if let Some(s)=sessions.get(token){if let Some((_,effect,_))=&s.pending{if effect["service"]=="ebooks.acquire"{if let Some(id)=effect["transfer"].as_str(){let data=scope(root,&s.id)?;if crate::sdk_storage::service(&data,"blobs.info",&json!({"id":id})).is_ok_and(|v|v["ready"]!=true){let _=crate::sdk_storage::service(&data,"blobs.delete",&json!({"id":id}));}}}}}sessions.remove(token);return Ok(json!(true))}
 let s=sessions.get_mut(token).ok_or("App changed or permission was revoked. Open it again.")?;if s.root!=root{return Err("Session belongs to another host".into())}let a=get(&r,&s.id)?;if a.hash!=s.hash{return Err("App was updated. Open it again.".into())}
 if j["action"]=="native_camera"{
  if a.manifest.id!="app.onklav.camera"{return Err("This operation belongs to Camera".into())}
  for cap in ["storage.private","camera.capture","files.manage"]{capability(a,cap)?;}
  return match j["operation"].as_str().unwrap_or(""){
   "save"=>{let mime=j["mime"].as_str().ok_or("Missing capture type")?;let parent=match mime{"image/jpeg"=>"images","video/mp4"=>"video",_=>return Err("Unsupported camera capture".into())};crate::content::import_staged(root,parent,j["name"].as_str().ok_or("Missing capture name")?,mime,&a.manifest.id,"Camera",true)},
   op=>crate::content::owned_media(root,&a.manifest.id,op,j)
  }
 }
 if j["action"]=="map_event"{if s.pending.is_some(){return Err("Map action is pending".into())}let n=s.frame["body"].as_array().and_then(|rows|rows.iter().find(|n|n["id"]==j["node"]&&n["kind"]=="map_viewer"&&n["enabled"]!=false)).cloned().ok_or("Map view unavailable")?;let callback=n["on_viewport"].as_str().ok_or("Map callbacks unavailable")?;let value=&j["value"];if value["width"].as_u64().is_none_or(|n|n==0||n>4096)||value["height"].as_u64().is_none_or(|n|n==0||n>4096){return Err("Invalid map viewport size".into())}return advance(root,a,s,json!({"action":callback,"value":value}),false)}
 if j["action"]=="map_component"{
  capability(a,"storage.private")?;
  let node=s.frame["body"].as_array().and_then(|rows|rows.iter().find(|n|n["id"]==j["node"]&&n["kind"]=="map_viewer"&&n["enabled"]!=false)).ok_or("Map viewer unavailable")?;
  crate::sdk_maps::validate(node)?;let mut tiles=Vec::new();let mut total=0u64;
  for tile in node["tiles"].as_array().unwrap(){let file=crate::sdk_storage::resolve(&scope(root,&a.manifest.id)?,tile["file"].as_str().unwrap())?;let size=file["size"].as_u64().ok_or("Invalid map tile")?;total+=size;if size==0||size>512*1024||total>8*1024*1024||!["image/png","image/jpeg"].contains(&file["mime"].as_str().unwrap_or("")){return Err("Map tile exceeds raster bounds".into())}tiles.push(json!({"x":tile["x"],"y":tile["y"],"file":file}));}
  return Ok(json!({"owner":{"id":a.manifest.id,"publisher":a.manifest.publisher,"hash":a.hash},"node":node,"tiles":tiles}))
 }
 if j["action"]=="pdf_component"{
  capability(a,"storage.private")?;
  let node=s.frame["body"].as_array().and_then(|rows|rows.iter().find(|n|n["id"]==j["node"]&&n["kind"]=="pdf_viewer"&&n["enabled"]!=false)).ok_or("PDF viewer unavailable")?;
  let file=crate::sdk_storage::resolve(&scope(root,&a.manifest.id)?,node["file"].as_str().ok_or("Choose a private PDF")?)?;
  if file["size"].as_u64().is_none_or(|n|n==0||n>50*1024*1024){return Err("PDF file exceeds the50MiB viewer limit".into())}
  return Ok(json!({"owner":{"id":a.manifest.id,"publisher":a.manifest.publisher,"hash":a.hash},"file":file}))
 }
 if j["action"]=="speech_event"{capability(a,"speech.output")?;if s.pending.is_some(){return Err("Finish saving the reading position first".into())}let node=s.frame["body"].as_array().and_then(|rows|rows.iter().find(|n|n["kind"]=="speech"&&n["id"]==j["node"]&&n["enabled"]!=false)).ok_or("Reading controls unavailable")?;let callback=node["on_done"].as_str().filter(|s|!s.is_empty()&&s.len()<=96).ok_or("Speech continuation unavailable")?.to_owned();return advance(root,a,s,json!({"action":callback}),false)}
 if j["action"]=="media_component"{let node=s.frame["body"].as_array().and_then(|a|a.iter().find(|n|n["id"]==j["node"]&&n["kind"]=="media_player"&&n["enabled"]!=false)).ok_or("Media controls unavailable")?;if node["audiobook"]==true{capability(a,"audiobooks.library")?;capability(a,"storage.private")?;}capability(a,"audio.playback")?;return Ok(json!({"id":a.manifest.id,"publisher":a.manifest.publisher,"hash":a.hash,"name":a.manifest.name}))}
 if j["action"]=="note_open"{
  if a.manifest.id!="app.onklav.notes"{return Err("Open saved notes in Notes".into())}capability(a,"storage.private")?;
  if s.pending.is_some(){return Err("Finish the pending note action".into())}let id=j["note"].as_str().ok_or("Choose a note")?;
  let note=crate::content::note_read(root,id)?;atomic(&crate::content::notes_root(root)?.join("last.json"),id.as_bytes())?;
  return advance(root,a,s,json!({"action":"opened","reply":{"ok":true,"value":note}}),false)
 }
 if j["action"]=="handoff_host"{
  let (pending,effect,_)=s.pending.as_ref().ok_or("Handoff expired")?;
  if j["token"]!=*pending||effect["service"]!="handoff.file"||j["operation"]!="resolve"{return Err("Handoff token mismatch".into())}
  capability(a,"apps.handoff")?;extra_file_permissions(a,"handoff.file",&effect["args"])?;crate::sdk_handoff::request("handoff.file",&effect["args"])?;
  return crate::sdk_storage::resolve(&scope(root,&a.manifest.id)?,effect["args"]["id"].as_str().unwrap())
 }
 if j["action"]=="blob_host"{
  let (pending,effect,_)=s.pending.as_mut().ok_or("Private file action expired")?;
  let service=effect["service"].as_str().ok_or("Invalid file action")?.to_owned();
  if j["token"]!=*pending||!matches!(service.as_str(),"blobs.import"|"blobs.export"|"ebooks.acquire"){return Err("Private file action token mismatch".into())}
  capability(a,"storage.private")?;extra_file_permissions(a,&service,&effect["args"])?;if service=="ebooks.acquire"{capability(a,"ebooks.library")?;crate::sdk_ebooks::request(&service,&effect["args"])?;}
  let data=scope(root,&a.manifest.id)?;
  if service=="blobs.export"{if j["operation"]!="resolve"{return Err("Unsupported export action".into())}return crate::sdk_storage::resolve(&data,effect["args"]["id"].as_str().ok_or("Choose a private file")?)}
  if j["operation"]=="begin"{
   if !effect["transfer"].is_null(){return Err("File import already started".into())}
   let mut args=j["args"].clone();args["cache"]=json!(effect["args"]["cache"].as_bool().unwrap_or(false));
   let value=crate::sdk_storage::service(&data,"blobs.begin",&args)?;effect["transfer"]=value["id"].clone();return Ok(value)
  }
  let id=effect["transfer"].as_str().ok_or("Start the file import first")?.to_owned();
  let mut args=j["args"].clone();if !args.is_object(){return Err("Invalid import arguments".into())}args["id"]=json!(id);
  let op=match j["operation"].as_str(){Some("append")=>"blobs.append",Some("finish")=>"blobs.finish",Some("abort")=>"blobs.delete",_=>return Err("Unsupported import action".into())};
  let result=crate::sdk_storage::service(&data,op,&args)?;if service=="ebooks.acquire"&&op=="blobs.finish"{if let Err(error)=crate::sdk_epub::service(&data,"epub.info",&json!({"id":id})){let _=crate::sdk_storage::service(&data,"blobs.delete",&json!({"id":id}));return Err(error)}}return Ok(result)
 }
 if j["action"]=="camera_host"{
  let (pending,effect,_)=s.pending.as_mut().ok_or("Camera action expired")?;
  if j["token"]!=*pending||effect["service"]!="camera.capture"{return Err("Camera action token mismatch".into())}
  capability(a,"camera.capture")?;
  if effect["captured"].is_object(){return Ok(effect["captured"].clone())}
  let bytes=read_bounded(&root.join("content-import.tmp"),600*1024)?;
  if bytes.len()<4||!bytes.starts_with(&[255,216])||!bytes.ends_with(&[255,217]){return Err("Camera output must be a normalized JPEG".into())}
  let entry=crate::content::import_staged(root,"images","Photo.jpg","image/jpeg",&a.manifest.id,&a.manifest.name,true)?;
  effect["captured"]=entry.clone();return Ok(entry)
 }
 if j["action"]=="library_host"{
  let (pending,effect,_)=s.pending.as_ref().ok_or("File action expired")?;
  if j["token"]!=*pending{return Err("File action token mismatch".into())}
  let service=effect["service"].as_str().ok_or("Invalid file action")?;
  capability(a,match service{other if other.starts_with("ebooks.")=>"ebooks.library",other if crate::sdk_handoff::supported(other)=>"apps.handoff",other if crate::sdk_devices::cap(other).is_some()=>crate::sdk_devices::cap(other).unwrap(),"files.export"=>"files.export","images.export"=>image_export_cap(&effect["args"])?,"library.external"=>"files.external",_=>"files.manage"})?;
  extra_file_permissions(a,service,&effect["args"])?;
  return match (service,j["operation"].as_str().unwrap_or("")){
   ("library.share","resolve")=>crate::content::share(root,effect["args"]["id"].as_str().ok_or("Choose a file")?),
   ("library.open"|"library.export","resolve")=>crate::content::resolve(root,effect["args"]["id"].as_str().ok_or("Choose a file")?),
   ("library.import","import")=>crate::content::import_staged(root,effect["args"]["folder"].as_str().ok_or("Choose a folder")?,j["name"].as_str().ok_or("Choose a file name")?,j["mime"].as_str().unwrap_or("application/octet-stream"),&a.manifest.id,&a.manifest.name,false),
   ("files.export","save_text")=>crate::content::store_text(root,&a.manifest.id,&a.manifest.name,effect["args"]["name"].as_str().unwrap_or("Document"),effect["args"]["text"].as_str().ok_or("Missing document text")?),
   ("images.export","save_image") if effect["args"]["mode"]=="files"=>crate::content::import_staged(root,"images",j["name"].as_str().unwrap_or("Image.jpg"),"image/jpeg",&a.manifest.id,&a.manifest.name,true),
   ("library.external","check")=>Ok(json!(true)),
   ("library.external","import_external") if effect["args"]["operation"]=="browse"=>{capability(a,"files.manage")?;crate::content::import_staged(root,"downloads",j["name"].as_str().ok_or("Choose a file name")?,j["mime"].as_str().unwrap_or("application/octet-stream"),&a.manifest.id,&a.manifest.name,false)},
   _=>Err("Unsupported host file operation".into())
  }
 }
 if j["action"]=="check"{if a.manifest.id=="app.onklav.files"{capability(a,"files.manage")?;}return Ok(json!(true))}
 if j["action"]=="video_open" {
  if a.manifest.id!="app.onklav.youtube"{return Err("Open videos in the YouTube app".into())}
  if a.manifest.version<2{return Err("Update YouTube in Settings > Apps to open message videos".into())}
  capability(a,"storage.private")?;capability(a,"network.web")?;capability(a,"clipboard.read")?;capability(a,"clipboard.write")?;
  if s.pending.is_some(){return Err("Finish the pending app action".into())}
  let id=j["video"].as_str().ok_or("Choose a video")?;let start=j["start"].as_u64().filter(|n|*n<=86400).ok_or("Invalid video start time")?;
  if id.len()!=11||!id.bytes().all(|c|c.is_ascii_alphanumeric()||b"_-".contains(&c)){return Err("Invalid video identity".into())}
  let url=format!("https://www.youtube.com/watch?v={id}&t={start}");
  return advance(root,a,s,json!({"action":"host.video.open","url":url,"video":id}),false)
 }
 if j["action"]=="walkie_refresh"{capability(a,"communications.walkie")?;if s.pending.is_some(){return Err("Finish pending action".into())}return advance(root,a,s,json!({"action":"session.changed"}),false)}
 if ["host_voice","voice_prepare","voice_commit","voice_played","voice_floor_request","voice_floor_release"].contains(&j["action"].as_str().unwrap_or("")) {
 if matches!(j["action"].as_str(),Some("voice_floor_request"|"voice_floor_release"))||(j["action"]=="voice_commit"&&j["send"]==true){capability(a,"communications.walkie")?;}
 capability(a,"storage.private")?;
 if s.pending.is_some()||s.frame["menu_open"]==true||s.frame["confirm"].is_object(){return Err("Close the pending dialog before using voice".into())}
 let widget=j["widget"].as_str().ok_or("Missing voice component")?;
 let n=["body","drawer"].into_iter().filter(|key|if s.frame["drawer_open"]==true{*key=="drawer"}else{*key=="body"}).flat_map(|k|s.frame[k].as_array().into_iter().flatten()).find(|n|n["id"]==widget&&[Some("voice_capture"),Some("voice_clip")].contains(&n["kind"].as_str())).cloned().ok_or("Voice component is closed")?;
 let capture=n["kind"]=="voice_capture";if capture&&j["preview"]==true{capability(a,"audio.playback")?;}capability(a,if capture{"audio.record"}else{"audio.playback"})?;
 let data=scope(root,&a.manifest.id)?;
 let binding=if capture {
  let state=crate::walkie_store::service(&data,"walkie.state",&json!({}))?;
  let peer=state["recent"].as_array().into_iter().flatten().find(|r|r["id"]==state["selected"]).ok_or("Save a recipient before recording")?;
  if n["peer"]!=peer["peer"]&&j["action"]!="voice_floor_release"{return Err("Save the recipient before recording".into())}
  let live=&state["session"];if live["phase"]=="connected"{
   capability(a,"communications.walkie")?;if live["peer"]!=peer["peer"]{return Err("Connected recipient changed".into())}
   json!({"session":live["id"],"peer":live["peer"],"subscription":live["subscription"],"outgoing":true})
  }else{json!({"session":peer["id"],"peer":peer["peer"],"subscription":peer["subscription"],"outgoing":true})}
 }else{json!({"id":n["clip"]})};
 let live=crate::walkie_session::snapshot(&data)?;
 if capture&&["voice_floor_request","voice_floor_release"].contains(&j["action"].as_str().unwrap_or("")){
  capability(a,"communications.walkie")?;
  let op=if j["action"]=="voice_floor_request"{"floor.request"}else{"floor.release"};
  let lease=format!("{token}:{widget}:{}",binding);
  if op=="floor.release"&&j["lease"]!=lease{return Err("Microphone request belongs to an older session".into())}
  let mut out=crate::walkie_carrier::service(&data,&json!({"operation":op,"id":live["id"],"peer":binding["peer"],"facts":j["facts"]}))?;out["lease"]=json!(lease);return Ok(out)
 }
 if capture&&j["permission_only"]==true{return Ok(json!({"permitted":true,"live":live["phase"]=="connected"}))}
 if capture&&live["phase"]=="connected"&&live["can_record"]!=true{return Err("Waiting for microphone ownership".into())}
 let key=format!("{token}:{widget}:{}:{}",binding,if capture&&live["phase"]=="connected"{live["floor"]["epoch"].to_string()}else{String::new()});
 if j["action"]=="host_voice"{
  let preferences=crate::walkie_store::service(&data,"walkie.state",&json!({}))?;let mut out=json!({"owner":carrier_owner(a),"key":key,"capture":capture,"peer":binding["peer"],"subscription":binding["subscription"],"live":live["phase"]=="connected","beeps":preferences["preferences"]["beeps"]==true&&a.grants.contains("audio.playback"),"auto_send":preferences["preferences"]["auto_send"]});
  if !capture{let clip=crate::walkie_clips::component(&data,n["clip"].as_str().unwrap(),false)?;out["auto_play"]=json!(preferences["preferences"]["auto_play"]==true&&live["phase"]=="connected"&&clip["binding"]["outgoing"]==false&&clip["state"]=="received"&&clip["restored"]!=true);}
  if !capture&&j["read"]==true{out["audio"]=crate::walkie_clips::component(&data,n["clip"].as_str().unwrap(),true)?;}
  return Ok(out)
 }
 if j["key"]!=key{return Err("Voice recipient or component changed; discard this draft".into())}
 if capture&&(j["action"]=="voice_prepare"||j["action"]=="voice_commit"){
  let bytes=j["bytes"].as_array().filter(|b|b.len()<=65536).ok_or("Invalid recording size")?.iter().map(|n|n.as_u64().filter(|n|*n<=255).map(|n|n as u8).ok_or_else(||"Invalid voice byte".to_string())).collect::<Result<Vec<_>>>()?;
  let mime=j["mime"].as_str().ok_or("Missing voice format")?;let bytes=crate::walkie::capture_audio(&bytes,mime)?;
  if j["action"]=="voice_prepare"{return Ok(json!({"mime":mime,"bytes":bytes,"millis":crate::walkie::audio_duration(&bytes,mime)?}))}
  let id=j["capture"].as_str().filter(|s|crate::valid_id(s)).ok_or("Invalid recording identity")?;
  let mut binding=binding;binding["id"]=json!(id);
  if j["send"]==true{if a.manifest.id!="app.onklav.walkie"&&live["phase"]!="connected"{return Err("External carrier voice requires a connected app session".into())}capability(a,"communications.walkie")?;let sub=crate::walkie_carrier::ready(j)?;if binding["subscription"].as_i64().is_some_and(|old|old>=0&&old!=sub as i64){return Err("Saved sending SIM changed".into())}if binding["peer"]!=j["facts"]["peer"]{return Err("Recording recipient changed before sending".into())}binding["subscription"]=json!(sub);}
  crate::walkie_clips::host(&data,&json!({"operation":"ingest","binding":binding,"sequence":if live["phase"]=="connected"{live["floor"]["epoch"].clone()}else{json!(0)},"mime":mime,"bytes":bytes}))?;
  return advance(root,a,s,json!({"action":n["on_change"],"value":{"id":id,"state":"queued","sending_requested":j["send"]==true}}),false)
 }
 if !capture&&j["action"]=="voice_played"{
  let audio=crate::walkie_clips::component(&data,n["clip"].as_str().unwrap(),false)?;
  if audio["binding"]["outgoing"]==false{crate::walkie_clips::host(&data,&json!({"operation":"played","binding":audio["binding"]}))?;if a.grants.contains("communications.walkie")&&audio["restored"]!=true{crate::walkie_session::receipt(&data,&audio["binding"],true)?;}}
  let value=json!({"played":true});if n["on_change"].is_string(){return advance(root,a,s,json!({"action":n["on_change"],"value":value}),false)}return Ok(value)
 }
 return Err("Unsupported voice component action".into())
 }
 if ["host_canvas","canvas_event","host_drawing_image"].contains(&j["action"].as_str().unwrap_or("")){
 capability(a,"storage.private")?;capability(a,"images.edit")?;
 let widget=j["widget"].as_str().ok_or("Missing Canvas")?;
 if j["action"]=="host_drawing_image"{let n=s.frame["drawer"].as_array().into_iter().flatten().find(|n|n["id"]==widget&&n["kind"]=="image_record"&&s.frame["drawer_open"]==true).ok_or("Image Library Is Closed")?;let id=n["document"].as_str().ok_or("Missing Image")?;return crate::drawing::snapshot(&scope(root,&a.manifest.id)?,id,n["revision"].as_u64().ok_or("Missing Image Revision")?,true)}
 let n=s.frame["body"].as_array().into_iter().flatten().find(|n|n["id"]==widget&&n["kind"]=="canvas").cloned().ok_or("Canvas Is Closed")?;let id=n["document"]["id"].as_str().ok_or("Open a Picture First")?;let revision=n["document"]["revision"].as_u64().ok_or("Missing Image Revision")?;let data=scope(root,&a.manifest.id)?;
 if j["action"]=="host_canvas"{return Ok(json!({"app":s.id,"node":n,"document":crate::drawing::snapshot(&data,id,revision,j["image"]==true)?}))}
 if s.pending.is_some(){return Err("Finish the Pending Image Action".into())}if j["document"]!=id||j["revision"].as_u64()!=Some(revision){return Err("Stale Canvas Event".into())}
 let mut args=j["value"].clone();args["id"]=json!(id);args["revision"]=json!(revision);
 let value=if args["op"]=="text"{let doc=crate::drawing::snapshot(&data,id,revision,false)?;if let Some(i)=args["index"].as_u64(){let mark=doc["marks"].as_array().and_then(|m|m.get(i as usize)).filter(|m|m["tool"]=="text").ok_or("Text Note Is Gone")?;json!({"op":"text","index":i,"mark":mark})}else{let x=args["x"].as_f64().ok_or("Invalid Text Position")?;let y=args["y"].as_f64().ok_or("Invalid Text Position")?;if x<0.||y<0.||x>doc["width"].as_f64().unwrap()||y>doc["height"].as_f64().unwrap(){return Err("Text Position Is Outside Image".into())}json!({"op":"text","mark":{"tool":"text","text":"","x":x,"y":y,"color":n["color"],"width":4,"points":[]}})}}else{json!({"doc":crate::drawing::service(&data,"drawing.edit",&args)?})};return advance(root,a,s,json!({"action":n["on_change"],"value":value}),false)
 }
 if j["action"]=="host_image"{
 capability(a,"storage.private")?;let widget=j["widget"].as_str().ok_or("Missing Image Component")?;let n=["body","drawer","menu"].into_iter().filter(|key|*key=="body"||(*key=="drawer"&&s.frame["drawer_open"]==true)||(*key=="menu"&&s.frame["menu_open"]==true)).flat_map(|k|s.frame[k].as_array().into_iter().flatten()).find(|n|n["id"]==widget&&n["kind"]=="media_record").ok_or("Image Component Is Closed")?;return Ok(json!({"app":s.id,"node":n,"download":a.grants.contains("network.thumbnails")}))
 }
 if j["action"]=="host_web"||j["action"]=="web_event"||j["action"]=="web_file_save"{
 capability(a,"network.web")?;let widget=j["widget"].as_str().ok_or("Missing Web Component")?;let n=s.frame["body"].as_array().into_iter().flatten().find(|n|n["id"]==widget&&n["kind"]=="web").cloned().ok_or("Web Component Is Closed")?;
 capability(a,"clipboard.read")?;capability(a,"clipboard.write")?;
 if j["action"]=="web_file_save"{capability(a,"storage.private")?;if s.pending.is_some(){return Err("Finish the browser action before saving a download".into())}let mime=j["mime"].as_str().unwrap_or("application/octet-stream");let parent=if n["provider"]=="youtube"{capability(a,"files.manage")?;let requested=j["folder"].as_str().unwrap_or("");if !((requested=="audio"&&mime.starts_with("audio/"))||(requested=="video"&&mime.starts_with("video/"))){return Err("Choose Audio or Video for this download".into())}requested}else if n["provider"].is_null(){"downloads"}else{return Err("Downloads are unavailable for this provider".into())};return crate::content::import_staged(root,parent,j["name"].as_str().ok_or("Choose a file name")?,mime,&a.manifest.id,&a.manifest.name,a.manifest.id!="app.onklav.browser")}
 if j["action"]=="host_web"{return Ok(json!({"app":s.id,"node":n}))}
 if s.pending.is_some(){return Err("An App Action Is Pending".into())}if j["tab"]!=n["tab"]{return Err("Stale Web Tab Callback".into())}
 if j["epoch"].as_u64().unwrap_or(0)!=n["epoch"].as_u64().unwrap_or(0){return Err("Stale Web Metadata Callback".into())}
 let payload=&j["value"];let url=payload["url"].as_str().ok_or("Missing Website Address")?;if !crate::feeds::https(url){return Err("Unsafe Website Address".into())}
 let kind=payload["kind"].as_str().unwrap_or("navigation");if !["navigation","account","external","error"].contains(&kind){return Err("Invalid Web Notice".into())}if kind=="navigation"&&n["provider"]=="youtube"&&(!crate::videos::page(url)||crate::videos::account(url)){return Err("Signed-Out Video Navigation Required".into())}
 let value=json!({"kind":kind,"id":n["tab"],"url":url,"video":crate::videos::video_id(url),"title":crate::plain(payload["title"].as_str().unwrap_or(""),160),"back":payload["back"]==true,"forward":payload["forward"]==true,"channel":if n["provider"]=="youtube"{crate::videos::channel(payload["channel"].as_str().unwrap_or(""))}else{String::new()},"channel_title":crate::plain(payload["channel_title"].as_str().unwrap_or(""),160)});let action=if kind=="navigation"{n["on_change"].clone()}else{n.get("on_notice").cloned().ok_or("Web Notices Are Not Enabled")?};return advance(root,a,s,json!({"action":action,"value":value}),false)
 }
 if j["action"]=="host_speech"{capability(a,"speech.output")?;let widget=j["widget"].as_str().ok_or("Missing speech component")?;let node=["body","menu"].into_iter().flat_map(|k|s.frame[k].as_array().into_iter().flatten()).find(|n|n["id"]==widget&&[Some("speech"),Some("speech_settings")].contains(&n["kind"].as_str())).ok_or("Speech component is no longer available")?;if node["kind"]=="speech_settings"&&s.frame["menu_open"]!=true{return Err("Voice settings are closed".into())}return Ok(node.clone())}
 if j["action"]=="host_clipboard"{let cap=j["cap"].as_str().ok_or("Missing clipboard capability")?;if !["clipboard.read","clipboard.write"].contains(&cap){return Err("Unsupported native field capability".into())}capability(a,cap)?;return Ok(json!(true))}
 if j["action"]=="event"{
  if s.pending.is_some(){return Err("Finish or cancel the pending app action".into())}
  let event=&j["event"];let action=event["action"].as_str().ok_or("Missing app action")?;
  let node=["actions","body","drawer","menu"].into_iter().flat_map(|k|s.frame[k].as_array().into_iter().flatten()).find(|n|n["id"]==action||n["delete"]==action||n["copy"]==action||n["retry"]==action||n["long_action"]==action||n["undo_action"]==action||n["redo_action"]==action||n["tools_action"]==action||n["trailing"]["id"]==action||n["secondary_trailing"]["id"]==action).cloned().or_else(||{
    if s.frame["back_action"]==action{return Some(json!({"kind":"button"}))}
    if (s.frame["drawer_open"]==true && s.frame["drawer_close"]==action)||(s.frame["menu_open"]==true && s.frame["menu_close"]==action){return Some(json!({"kind":"button"}))}
    let c=&s.frame["confirm"];if c.is_object()&&(c["accept"]==action||c["cancel"]==action){Some(json!({"kind":"button"}))}else{None}
  }).ok_or("This UI action is no longer available")?;
  if node["enabled"]==false{return Err("This control is unavailable".into())}
  let is_field=node["id"]==action&&(node["kind"]=="field"||node["kind"]=="editor");
  if is_field&&event["value"].as_str().is_none_or(|s|s.encode_utf16().count()>node["limit"].as_u64().unwrap_or(0) as usize){return Err("Field exceeds its limit".into())}
  advance(root,a,s,event.clone(),!is_field)
 }else{
  let (pending,effect,then)=s.pending.as_ref().ok_or("App action expired")?;
  if j["token"]!=*pending{return Err("App action token mismatch".into())}let service=effect["service"].as_str().ok_or("Invalid pending capability")?;if !matches!(service,"clock.read"|"calendar.expand"|"calendar.resolve"|"calendar.localize"){capability(a,match service{other if other.starts_with("ebooks.")=>"ebooks.library",other if crate::sdk_handoff::supported(other)=>"apps.handoff",other if crate::sdk_devices::cap(other).is_some()=>crate::sdk_devices::cap(other).unwrap(),"jobs.schedule"|"jobs.status"|"jobs.cancel"|"jobs.list"|"downloads.start"=>"background.tasks","podcasts.search"|"podcasts.feed"|"podcasts.play"|"podcasts.download"|"podcasts.delete"|"podcasts.downloads"=>"podcasts.library","radio.search"|"radio.play"=>"radio.stations","media.play"|"media.status"|"media.control"=>"audio.playback","accounts.connect"|"accounts.list"|"accounts.refresh"|"accounts.disconnect"=>"accounts.connect","http.request"=>"network.http","blobs.import"|"blobs.export"=>"storage.private","camera.capture"=>"camera.capture","notifications.show"|"notifications.schedule"|"notifications.schedule_at"|"notifications.status"|"notifications.pending"|"notifications.cancel"=>"notifications.post","secrets.get"|"secrets.set"|"secrets.delete"=>"storage.secrets","library.open"|"library.import"|"library.export"|"library.share"=>"files.manage","library.external"=>"files.external","feeds.fetch"=>"network.feeds","web.open"=>"network.web","images.clear"=>"storage.private","images.import"=>"files.images","images.text"=>"images.edit","images.export"=>image_export_cap(&effect["args"])?,other=>other})?;}if crate::sdk_handoff::supported(service){crate::sdk_handoff::request(service,&effect["args"])?;}if crate::sdk_devices::cap(service).is_some(){crate::sdk_devices::request(service,&effect["args"])?;}if service=="clock.read"{clock_args(&effect["args"])?;}if service=="calendar.expand"{crate::sdk_calendar::request(&effect["args"])?;}if service=="feeds.fetch"{capability(a,"storage.private")?;}
  if service.starts_with("images.")&&service!="images.clear"{capability(a,"images.edit")?;capability(a,"storage.private")?;}
  extra_file_permissions(a,service,&effect["args"])?;
 if j["action"]=="authorize"{let mut native=effect.clone();if service.starts_with("ebooks."){capability(a,"ebooks.library")?;capability(a,"storage.private")?;crate::sdk_ebooks::request(service,&effect["args"])?;native["owner"]=json!({"id":a.manifest.id,"publisher":a.manifest.publisher,"hash":a.hash});}if crate::sdk_handoff::supported(service){crate::sdk_handoff::request(service,&effect["args"])?;native["owner"]=json!({"id":a.manifest.id,"publisher":a.manifest.publisher,"hash":a.hash,"session":j["session"]});}if crate::sdk_handoff::supported(service){crate::sdk_handoff::request(service,&effect["args"])?;}if crate::sdk_devices::cap(service).is_some(){crate::sdk_devices::request(service,&effect["args"])?;native["owner"]=json!({"id":a.manifest.id,"publisher":a.manifest.publisher,"session":j["session"]});}if service.starts_with("ebooks."){crate::sdk_ebooks::request(service,&effect["args"])?;capability(a,"storage.private")?;}if matches!(service,"podcasts.search"|"podcasts.feed"|"podcasts.play"|"podcasts.download"|"podcasts.delete"|"podcasts.downloads"){capability(a,"podcasts.library")?;if service=="podcasts.play"{capability(a,"audio.playback")?;}crate::sdk_podcast_library::request(service,&effect["args"])?;native["owner"]=json!({"id":a.manifest.id,"publisher":a.manifest.publisher,"hash":a.hash,"name":a.manifest.name});}if service.starts_with("radio."){if service=="radio.play"{capability(a,"audio.playback")?;}crate::sdk_radio::request(service,&effect["args"])?;native["owner"]=json!({"id":a.manifest.id,"publisher":a.manifest.publisher,"hash":a.hash,"name":a.manifest.name});}if service.starts_with("jobs.")||service.starts_with("media.")||service=="downloads.start"{crate::sdk_tasks::request(a.manifest.network.as_ref(),service,&effect["args"])?;native["owner"]=json!({"id":a.manifest.id,"publisher":a.manifest.publisher,"hash":a.hash,"name":a.manifest.name});}if service.starts_with("accounts."){capability(a,"network.http")?;crate::sdk_accounts::request(a.manifest.network.as_ref(),service,&effect["args"])?;native["owner"]=json!({"id":a.manifest.id,"publisher":a.manifest.publisher});native["providers"]=json!(a.manifest.network.as_ref().ok_or("Missing account policy")?.accounts);}if service=="http.request"{crate::sdk_http::request(a.manifest.network.as_ref(),&effect["args"])?;if let Some(id)=effect["args"]["upload"].as_str(){capability(a,"storage.private")?;let file=crate::sdk_storage::resolve(&scope(root,&a.manifest.id)?,id)?;if file["size"].as_u64().is_none_or(|n|n>10*1024*1024){return Err("Upload exceeds10MiB".into())}native["upload_file"]=file;}if effect["args"].get("account").is_some(){native["providers"]=json!(a.manifest.network.as_ref().ok_or("Missing account policy")?.accounts);}native["origins"]=json!(a.manifest.network.as_ref().ok_or("Missing HTTP policy")?.origins);native["owner"]=json!({"id":a.manifest.id,"publisher":a.manifest.publisher});}if matches!(service,"blobs.import"|"blobs.export"){crate::sdk_storage::native_args(service,&effect["args"])?;}if service.starts_with("notifications."){device_args(service,&effect["args"])?;native["owner"]=json!({"id":a.manifest.id,"publisher":a.manifest.publisher,"name":a.manifest.name});}if service.starts_with("secrets."){secret_args(service,&effect["args"])?;native["owner"]=json!({"id":a.manifest.id,"publisher":a.manifest.publisher});}if service=="communications.walkie" {
  capability(a,"storage.private")?;
  native["owner"]=carrier_owner(a);
  let data=scope(root,&a.manifest.id)?;
  native["args"]=match effect["args"]["operation"].as_str(){Some("begin")=>{let mut peer=crate::walkie_store::selected(&data)?;peer["operation"]=json!("begin");peer},Some("retry")=>{let id=effect["args"]["clip"].as_str().ok_or("Choose a voice clip")?;let clip=crate::walkie_clips::component(&data,id,false)?;if clip["binding"]["outgoing"]!=true||!["failed","uncertain"].contains(&clip["state"].as_str().unwrap_or("")){return Err("This voice clip does not need retrying".into())}let mut b=clip["binding"].clone();b["operation"]=json!("retry");b["clip"]=json!(id);b["uncertain"]=json!(clip["state"]=="uncertain");b},Some("accept")=>{let mut state=crate::walkie_session::snapshot(&data)?;if state["phase"]!="incoming"||state["id"]!=effect["args"]["id"]{return Err("Invitation expired".into())}state["operation"]=json!("accept");state},_=>return Err("Unsupported Walkie communication request".into())};
 }if service=="images.export"||service=="images.text"{native["document"]=crate::drawing::prepare(&scope(root,&a.manifest.id)?,&effect["args"])?;}return Ok(native)}
  let then=then.clone();let mut reply=j["reply"].clone();if service=="ebooks.acquire"&&reply["ok"]==false{if let Some(id)=effect["transfer"].as_str(){let data=scope(root,&a.manifest.id)?;let _=crate::sdk_storage::service(&data,"blobs.delete",&json!({"id":id}));}}if service.starts_with("ebooks.")&&reply["ok"]==true{crate::sdk_ebooks::reply(service,&reply["value"])?;if service=="ebooks.acquire"{if reply["value"]["id"]!=effect["transfer"]{return Err("Ebook download did not commit its own file".into())}crate::sdk_epub::service(&scope(root,&a.manifest.id)?,"epub.info",&json!({"id":reply["value"]["id"]}))?;}}if crate::sdk_handoff::supported(service)&&reply["ok"]==true{crate::sdk_handoff::reply(service,&reply["value"])?;}if service=="clock.read"&&reply["ok"]==true{clock_reply(&reply["value"])?;}if service=="calendar.expand"&&reply["ok"]==true{crate::sdk_calendar::reply(&effect["args"],&reply["value"])?;}if service=="camera.capture"&&reply["ok"]==true{if !effect["captured"].is_object(){return Err("No authorized camera image was committed".into())}reply["value"]=effect["captured"].clone();}
 if matches!(service,"podcasts.search"|"podcasts.feed"|"podcasts.play"|"podcasts.download"|"podcasts.delete"|"podcasts.downloads")&&reply["ok"]==true{crate::sdk_podcast_library::reply(service,&reply["value"])?;}if service.starts_with("radio.")&&reply["ok"]==true{crate::sdk_radio::reply(service,&reply["value"])?;}if (service.starts_with("jobs.")||service.starts_with("media.")||service=="downloads.start")&&reply["ok"]==true{crate::sdk_tasks::reply(service,&reply["value"])?;}
 if service.starts_with("accounts.")&&reply["ok"]==true{crate::sdk_accounts::reply(a.manifest.network.as_ref(),service,&reply["value"])?;}
 if service=="http.request"&&reply["ok"]==true{crate::sdk_http::reply(&reply["value"])?;let field=if effect["args"]["response"]=="hex"{"hex"}else{"text"};if !reply["value"][field].is_string(){return Err("HTTP response mode mismatch".into())}}
 if matches!(service,"notifications.schedule_at"|"notifications.status"|"notifications.pending")&&reply["ok"]==true{notification_query_reply(service,&reply["value"])?;}
 if matches!(service,"notifications.show"|"notifications.schedule"|"notifications.cancel")&&reply["ok"]==true{let flag=if service=="notifications.show"{"posted"}else if service=="notifications.schedule"{"scheduled"}else{"cancelled"};if !reply["value"][flag].is_boolean(){return Err("Invalid notification response".into())}reply["value"]=json!({flag:reply["value"][flag]});}
if service.starts_with("secrets.")&&reply["ok"]==true{
 let value=&reply["value"];let valid=match service{"secrets.get"=>value.is_object()&&value.get("value").is_some_and(|v|v.is_null()||v.as_str().is_some_and(|s|s.encode_utf16().count()<=4096)),"secrets.set"=>value["stored"]==true,"secrets.delete"=>value["deleted"].is_boolean(),_=>false};
 if !valid{return Err("Invalid secure storage response".into())}
 }if service=="contacts.pick"&&reply["ok"]==true{reply=match selected_contact(&reply["value"]){Ok(v)=>json!({"ok":true,"value":v}),Err(e)=>json!({"ok":false,"error":e})};}if reply["ok"]==true&&(service=="images.import"||service=="images.text"){let data=scope(root,&a.manifest.id)?;let result=if service=="images.import"{crate::drawing::import(&data,&reply["value"])}else{let mut args=effect["args"].clone();args["mark"]["box_width"]=reply["value"]["box_width"].clone();args["mark"]["box_height"]=reply["value"]["box_height"].clone();crate::drawing::service(&data,"drawing.text",&args)};reply=match result{Ok(v)=>json!({"ok":true,"value":v}),Err(e)=>json!({"ok":false,"error":e})};}if service=="feeds.fetch"{let data=scope(root,&a.manifest.id)?;if reply["ok"]==true{reply=match crate::feeds::commit(&data,&effect["args"],&reply["value"]){Ok(v)=>json!({"ok":true,"value":v}),Err(e)=>json!({"ok":false,"error":e})};}else{crate::feeds::failure(&data,&effect["args"],reply["error"].as_str().unwrap_or("Could not load source"))?;}}s.pending=None;advance(root,a,s,json!({"action":then,"reply":reply}),false)
 }
 },
 _=>Err("Unsupported SDK operation".into())
 }
}

#[cfg(test)] mod tests {
 use super::*;use ed25519_dalek::{SigningKey,Signer};use zip::write::SimpleFileOptions;
 fn repack(wasm:&[u8],id:&str,version:u64,key:u8,caps:&[&str])->Vec<u8>{let k=SigningKey::from_bytes(&[key;32]);let m=json!({"schema":"onklav.app.v1","runtime":"rust-wasm-ui.v1","id":id,"name":"Test App","version":version,"publisher":hex(k.verifying_key().as_bytes()),"capabilities":caps});let raw=serde_json::to_vec(&m).unwrap();let sig=k.sign(&signature_message(&raw,wasm));let mut z=zip::ZipWriter::new(std::io::Cursor::new(Vec::new()));for (name,b) in [("manifest.json",raw.as_slice()),("main.wasm",wasm),("signature.ed25519",sig.to_bytes().as_slice())]{z.start_file(name,SimpleFileOptions::default()).unwrap();z.write_all(b).unwrap()}z.finish().unwrap().into_inner()}
 fn fixture()->Vec<u8>{include_bytes!("../../sdk/packages/Notes.onklav-a").to_vec()}
 fn install(root:&Path,bytes:Vec<u8>,grants:&[&str])->Result<Value>{fs::write(root.join("app-install.zip"),bytes).unwrap();let review=dispatch(root,&json!({"action":"inspect"}))?;dispatch(root,&json!({"action":"install","hash":review["hash"],"grants":grants}))}
 fn launch(root:&Path,id:&str)->Value{dispatch(root,&json!({"action":"launch","id":id})).unwrap()}
 fn event(root:&Path,s:&str,action:&str,value:&str)->Result<Value>{dispatch(root,&json!({"action":"event","session":s,"event":{"action":action,"value":value}}))}
 fn maps_test_node(handle:&str)->Value{json!({"kind":"map_viewer","id":"map","provider":{"id":"demo","name":"Demo","data":"osm","attribution":"Synthetic sample","copyright_url":"https://www.openstreetmap.org/copyright","template":"https://example.org/{z}/{x}/{y}.png","offline_allowed":false},"zoom":1,"latitude":0.,"longitude":0.,"tiles":[{"x":0,"y":0,"file":handle}],"markers":[]})}
 #[test]fn maps_widget_requires_one_non_scrolling_body(){let n=maps_test_node(&"a".repeat(32));let mut f=json!({"title":"Maps","state":{},"actions":[],"body":[n.clone()],"drawer":[],"menu":[]});assert!(validate_frame(&f).is_ok());f["scroll_body"]=json!(true);assert!(validate_frame(&f).is_err());f["scroll_body"]=json!(false);f["body"]=json!([]);f["menu"]=json!([n.clone()]);assert!(validate_frame(&f).is_err());f["menu"]=json!([]);let mut second=n.clone();second["id"]=json!("second");f["body"]=json!([n,second]);assert!(validate_frame(&f).is_err());}
 #[test]fn maps_component_scopes_tiles_to_current_visible_signed_owner(){let d=tempfile::tempdir().unwrap();let root=d.path();let wasm=package(fixture()).unwrap().wasm;let id="org.example.maps";let other="org.example.othermaps";for app in[id,other]{install(root,repack(&wasm,app,1,9,&["storage.private"]),&["storage.private"]).unwrap();}
 let file=crate::sdk_storage::save_bytes(&scope(root,id).unwrap(),"Tile.png","image/png",include_bytes!("../../docs/fixtures/maps/1-0-0.png")).unwrap();let a=launch(root,id);let b=launch(root,other);let t=a["session"].as_str().unwrap();let u=b["session"].as_str().unwrap();for token in[t,u]{let mut sessions=SESSIONS.get().unwrap().lock().unwrap();sessions.get_mut(token).unwrap().frame["body"]=json!([maps_test_node(file["id"].as_str().unwrap())]);}
 let call=|token:&str|dispatch(root,&json!({"action":"map_component","session":token,"node":"map"}));let own=call(t).unwrap();assert_eq!(own["owner"]["id"],id);assert_eq!(own["tiles"][0]["file"]["sha256"],file["sha256"]);assert!(call(u).is_err());{let mut sessions=SESSIONS.get().unwrap().lock().unwrap();sessions.get_mut(t).unwrap().frame["body"][0]["enabled"]=json!(false);}assert!(call(t).is_err());dispatch(root,&json!({"action":"permissions","id":id,"grants":[]})).unwrap();assert!(call(t).is_err());}
 #[test]fn pdf_widget_requires_one_bounded_body_and_an_opaque_handle(){
  let frame=|nodes:Value|json!({"title":"PDF","state":{},"actions":[],"body":nodes,"drawer":[],"menu":[]});let node=json!({"kind":"pdf_viewer","id":"pdf","file":"a".repeat(32)});
  assert!(validate_frame(&frame(json!([node.clone()]))).is_ok());assert!(validate_frame(&frame(json!([node.clone(),{"kind":"pdf_viewer","id":"second","file":"b".repeat(32)}]))).is_err());
  let mut scrolling=frame(json!([node.clone()]));scrolling["scroll_body"]=json!(true);assert!(validate_frame(&scrolling).is_err());let mut wrong=frame(json!([]));wrong["menu"]=json!([node]);assert!(validate_frame(&wrong).is_err());assert!(validate_frame(&frame(json!([{"kind":"pdf_viewer","id":"pdf","file":"file:///private"}]))).is_err());
 }
 #[test]fn pdf_component_resolves_only_the_current_signed_owners_visible_file(){
  let d=tempfile::tempdir().unwrap();let root=d.path();let wasm=package(fixture()).unwrap().wasm;let id="org.example.pdf";let other="org.example.otherpdf";
  for app in [id,other]{install(root,repack(&wasm,app,1,9,&["storage.private"]),&["storage.private"]).unwrap();}
  let file=crate::sdk_storage::save_bytes(&scope(root,id).unwrap(),"Sample.pdf","application/pdf",include_bytes!("../../docs/fixtures/pdf-sample.pdf")).unwrap();let handle=file["id"].as_str().unwrap();let one=launch(root,id);let two=launch(root,other);let session=one["session"].as_str().unwrap();let other_session=two["session"].as_str().unwrap();
  for token in [session,other_session]{let mut sessions=SESSIONS.get().unwrap().lock().unwrap();sessions.get_mut(token).unwrap().frame["body"]=json!([{"kind":"pdf_viewer","id":"pdf","file":handle}]);}
  let call=|token:&str|dispatch(root,&json!({"action":"pdf_component","session":token,"node":"pdf"}));let resolved=call(session).unwrap();assert_eq!(resolved["owner"]["id"],id);assert_eq!(resolved["file"]["sha256"],file["sha256"]);assert!(resolved["file"]["path"].as_str().unwrap().contains(id));assert!(call(other_session).is_err());
  {let mut sessions=SESSIONS.get().unwrap().lock().unwrap();sessions.get_mut(session).unwrap().frame["body"][0]["enabled"]=json!(false);}assert!(call(session).is_err());
  dispatch(root,&json!({"action":"permissions","id":id,"grants":[]})).unwrap();assert!(call(session).is_err());
 }
 #[test]fn handoff_file_scope_tokens_and_removal_are_owner_bound(){
  let d=tempfile::tempdir().unwrap();let root=d.path();let wasm=package(fixture()).unwrap().wasm;let caps=["storage.private","files.export","apps.handoff","share.text"];
  for id in ["org.example.sender","org.example.other"]{install(root,repack(&wasm,id,1,9,&caps),&caps).unwrap();}
  let file=crate::sdk_storage::save_bytes(&scope(root,"org.example.sender").unwrap(),"Book.pdf","application/pdf",b"private bytes").unwrap();let handle=file["id"].as_str().unwrap();
  let sender=launch(root,"org.example.sender");let other=launch(root,"org.example.other");let token=sender["session"].as_str().unwrap();let second=other["session"].as_str().unwrap();
  for(session,nonce)in [(token,"sender-token"),(second,"other-token")]{let mut sessions=SESSIONS.get().unwrap().lock().unwrap();sessions.get_mut(session).unwrap().pending=Some((nonce.into(),json!({"service":"handoff.file","args":{"operation":"view","id":handle}}),"done".into()));}
  let resolve=|session:&str,nonce:&str|dispatch(root,&json!({"action":"handoff_host","session":session,"token":nonce,"operation":"resolve"}));
  let own=resolve(token,"sender-token").unwrap();assert_eq!(own["mime"],"application/pdf");assert!(own["path"].as_str().unwrap().contains("org.example.sender"));
  assert!(resolve(second,"other-token").is_err());assert!(resolve(second,"sender-token").is_err());assert!(resolve(token,"other-token").is_err());
  let native=dispatch(root,&json!({"action":"authorize","session":token,"token":"sender-token"})).unwrap();assert_eq!(native["owner"]["id"],"org.example.sender");assert_eq!(native["owner"]["session"],token);assert!(native["owner"]["hash"].is_string());
  dispatch(root,&json!({"action":"remove","id":"org.example.sender","delete_data":false})).unwrap();assert!(resolve(token,"sender-token").is_err());
  assert!(install(root,repack(&wasm,"org.example.sender",2,8,&caps),&caps).is_err());install(root,repack(&wasm,"org.example.sender",2,9,&caps),&caps).unwrap();assert!(resolve(token,"sender-token").is_err());
  let retained=crate::sdk_storage::resolve(&scope(root,"org.example.sender").unwrap(),handle).unwrap();assert_eq!(retained["size"],13);
  dispatch(root,&json!({"action":"remove","id":"org.example.sender","delete_data":true})).unwrap();assert!(!root.join("app-sdk/data/org.example.sender").exists());
 }
 #[test]fn handoff_permissions_and_replies_fail_closed(){
  assert_eq!(service_requirements("handoff.file",&json!({})),vec!["apps.handoff","storage.private","files.export"]);
  let d=tempfile::tempdir().unwrap();let root=d.path();let wasm=package(fixture()).unwrap().wasm;let caps=["storage.private","files.export","apps.handoff"];let id="org.example.deniedhandoff";install(root,repack(&wasm,id,1,9,&caps),&["storage.private"]).unwrap();let app=launch(root,id);let token=app["session"].as_str().unwrap();
  {let mut sessions=SESSIONS.get().unwrap().lock().unwrap();sessions.get_mut(token).unwrap().pending=Some(("one".into(),json!({"service":"handoff.file","args":{"operation":"share","id":"a".repeat(32)}}),"done".into()));}
  assert!(dispatch(root,&json!({"action":"authorize","session":token,"token":"one"})).is_err());assert!(dispatch(root,&json!({"action":"handoff_host","session":token,"token":"one","operation":"resolve"})).is_err());
  assert!(crate::sdk_handoff::reply("handoff.text",&json!({"handed_off":true,"completed":false})).is_ok());
 }
 #[test]fn group12_reminder_policy_is_bounded_and_legacy_requests_remain_valid(){
  let legacy=json!({"key":"one","title":"Reminder","text":"Body","delay_seconds":60});assert!(device_args("notifications.schedule",&legacy).is_ok());
  let mut a=legacy;a["missed"]=json!("skip");a["snooze_seconds"]=json!(120);a["repeat_seconds"]=json!(86400);a["repeat_count"]=json!(5);assert!(device_args("notifications.schedule",&a).is_ok());
  for(key,value)in [("repeat_count",json!(101)),("repeat_seconds",json!(0)),("snooze_seconds",json!(86401)),("missed",json!("flood")),("owner",json!("foreign"))]{let mut bad=a.clone();bad[key]=value;assert!(device_args("notifications.schedule",&bad).is_err(),"{key}");}
  a.as_object_mut().unwrap().remove("repeat_count");assert!(device_args("notifications.schedule",&a).is_err());
 }
 #[test]fn group12_foreground_native_authorization_binds_session_owner_and_revocation(){
  let d=tempfile::tempdir().unwrap();let root=d.path();let wasm=package(fixture()).unwrap().wasm;let id="org.example.devices";
  let caps=["storage.private","calendar.handoff","device.connectivity","device.settings","location.foreground","device.torch","nearby.bluetooth","nearby.wifi","network.lan"];
  install(root,repack(&wasm,id,1,9,&caps),&caps).unwrap();let app=launch(root,id);let session=app["session"].as_str().unwrap();
  for(service,args)in [("calendar.insert",json!({"title":"Meeting","start_ms":1000,"end_ms":2000,"timezone":"UTC"})),("connectivity.read",json!({})),("device.settings",json!({"target":"wifi"})),("location.current",json!({})),("torch.set",json!({"enabled":true,"seconds":30})),("ble.select",json!({"service":"0000180d-0000-1000-8000-00805f9b34fb"})),("wifi.join",json!({"ssid":"Device"})),("lan.select",json!({"type":"_onklav._tcp."}))]{
   {let mut sessions=SESSIONS.get().unwrap().lock().unwrap();sessions.get_mut(session).unwrap().pending=Some(("device-token".into(),json!({"service":service,"args":args}),"done".into()));}
   let op=dispatch(root,&json!({"action":"authorize","session":session,"token":"device-token"})).unwrap();assert_eq!(op["owner"]["id"],id);assert_eq!(op["owner"]["session"],session);assert!(op["owner"]["publisher"].is_string());
   assert!(dispatch(root,&json!({"action":"authorize","session":session,"token":"foreign"})).is_err());
  }
  dispatch(root,&json!({"action":"permissions","id":id,"grants":["storage.private"]})).unwrap();assert!(dispatch(root,&json!({"action":"authorize","session":session,"token":"device-token"})).is_err());
 }
 #[test]fn collections_broker_grants_isolation_and_removal(){
  let start=json!({"title":"Storage fixture","state":{},"actions":[],"body":[],"drawer":[],"menu":[],"effect":{"service":"collections.transact","args":{"name":"items","expected_revision":0,"schema":1,"operations":[{"operation":"put","id":"one","data":{"owner":"fixture"}}]},"then":"done"}}).to_string();
  let done=json!({"title":"Storage fixture","state":{},"actions":[],"body":[],"drawer":[],"menu":[]}).to_string();
  let escape=|s:&str|s.bytes().map(|b|format!("\\{b:02x}")).collect::<String>();
  let wasm=wat::parse_str(format!("(module (memory (export \"memory\") 1) (data (i32.const 4096) \"{}\") (data (i32.const 8192) \"{}\") (func (export \"onklav_alloc\") (param i32)(result i32) i32.const 0) (func (export \"onklav_step\") (param i32 i32)(result i64) local.get 1 i32.const 80 i32.gt_u if (result i64) i64.const {} else i64.const {} end))",escape(&start),escape(&done),(8192u64<<32)|done.len()as u64,(4096u64<<32)|start.len()as u64)).unwrap();
  let d=tempfile::tempdir().unwrap();let root=d.path();let id="org.example.collections";let package=repack(&wasm,id,1,9,&["storage.private"]);
  install(root,package.clone(),&[]).unwrap();let denied=launch(root,id);assert!(denied["pending"].is_null());assert!(!root.join("app-sdk/data").join(id).exists());
  dispatch(root,&json!({"action":"permissions","id":id,"grants":["storage.private"]})).unwrap();launch(root,id);
  let own=scope(root,id).unwrap();assert_eq!(crate::sdk_collections::service(&own,"collections.info",&json!({"name":"items"})).unwrap()["count"],1);
  install(root,repack(&wasm,"org.example.othercollections",1,9,&["storage.private"]),&["storage.private"]).unwrap();launch(root,"org.example.othercollections");
  crate::sdk_collections::service(&own,"collections.clear",&json!({"name":"items","expected_revision":1})).unwrap();let other=scope(root,"org.example.othercollections").unwrap();assert_eq!(crate::sdk_collections::service(&other,"collections.info",&json!({"name":"items"})).unwrap()["count"],1);
  dispatch(root,&json!({"action":"remove","id":id,"delete_data":false})).unwrap();assert!(own.join("collection-items.json").exists());install(root,package,&["storage.private"]).unwrap();
  dispatch(root,&json!({"action":"remove","id":id,"delete_data":true})).unwrap();assert!(!own.exists());assert!(other.join("collection-items.json").exists());
 }

 #[test]fn storage_native_transfer_tokens_and_revocation_are_live(){
  let d=tempfile::tempdir().unwrap();let root=d.path();let wasm=package(fixture()).unwrap().wasm;let id="org.example.storage";
  install(root,repack(&wasm,id,1,9,&["storage.private","files.external","files.export"]),&["storage.private","files.external","files.export"]).unwrap();let opened=launch(root,id);let token=opened["session"].as_str().unwrap();
  {let mut sessions=SESSIONS.get().unwrap().lock().unwrap();let s=sessions.get_mut(token).unwrap();s.pending=Some(("import-token".into(),json!({"service":"blobs.import","args":{"cache":false}}),"imported".into()));}
  let call=|operation:&str,args:Value|dispatch(root,&json!({"action":"blob_host","session":token,"token":"import-token","operation":operation,"args":args}));
  let created=call("begin",json!({"name":"Book.m4b","mime":"audio/mp4"})).unwrap();assert!(call("begin",json!({"name":"Again"})).is_err());
  call("append",json!({"offset":0,"hex":"68656c6c6f"})).unwrap();let ready=call("finish",json!({})).unwrap();assert_eq!(ready["size"],5);assert_eq!(created["id"],ready["id"]);
  assert!(dispatch(root,&json!({"action":"blob_host","session":token,"token":"foreign","operation":"resolve","args":{}})).is_err());
  dispatch(root,&json!({"action":"permissions","id":id,"grants":["storage.private"]})).unwrap();assert!(call("append",json!({"offset":5,"hex":"21"})).is_err());
  assert_eq!(service_requirements("blobs.export",&json!({})),vec!["storage.private","files.export"]);
  assert_eq!(service_requirements("blobs.import",&json!({})),vec!["storage.private","files.external"]);
  assert!(crate::sdk_storage::service(&scope(root,id).unwrap(),"blobs.limits",&json!({"files":1})).is_err());
 }

 #[test]fn radio_broker_is_owner_bound_revocable_and_does_not_widen_generic_networking(){
  let d=tempfile::tempdir().unwrap();let root=d.path();let old=package(fixture()).unwrap();let mut m=old.manifest.clone();m.id="org.example.radiobroker".into();m.capabilities=["radio.stations".into(),"audio.playback".into()].into_iter().collect();m.network=None;let key=SigningKey::from_bytes(&[9;32]);m.publisher=hex(key.verifying_key().as_bytes());validate(&m).unwrap();let raw=serde_json::to_vec(&m).unwrap();let sig=key.sign(&signature_message(&raw,&old.wasm));let mut z=zip::ZipWriter::new(std::io::Cursor::new(Vec::new()));for(name,bytes)in[("manifest.json",raw.as_slice()),("main.wasm",old.wasm.as_slice()),("signature.ed25519",sig.to_bytes().as_slice())]{z.start_file(name,SimpleFileOptions::default()).unwrap();z.write_all(bytes).unwrap();}install(root,z.finish().unwrap().into_inner(),&["radio.stations","audio.playback"]).unwrap();let a=get(&load(root).unwrap(),&m.id).unwrap().clone();let owner=json!({"id":a.manifest.id,"publisher":a.manifest.publisher,"hash":a.hash});let handle=uuid::Uuid::new_v4().to_string();
  let auth=|args:Value|dispatch(root,&json!({"action":"media_authorize","owner":owner,"args":args}));assert_eq!(auth(json!({"radio_station":handle})).unwrap()["radio_station"],handle);assert!(auth(json!({"url":"https://stream.example.org/music"})).is_err());assert!(auth(json!({"radio_station":handle,"url":"https://evil.example.org"})).is_err());let mut foreign=owner.clone();foreign["hash"]=json!("b".repeat(64));assert!(dispatch(root,&json!({"action":"media_authorize","owner":foreign,"args":{"radio_station":handle}})).is_err());
  let token=uuid::Uuid::new_v4().simple().to_string();{let mut sessions=SESSIONS.get().unwrap().lock().unwrap();sessions.insert(token.clone(),Session{root:root.into(),id:a.manifest.id.clone(),hash:a.hash.clone(),module:compile(&old.wasm).unwrap(),state:json!({}),frame:json!({}),pending:Some(("radio-token".into(),json!({"service":"radio.play","args":{"station":handle}}),"done".into()))});}let pending=||dispatch(root,&json!({"action":"authorize","session":token,"token":"radio-token"}));assert_eq!(pending().unwrap()["owner"]["hash"],a.hash);dispatch(root,&json!({"action":"permissions","id":m.id,"grants":["radio.stations"]})).unwrap();assert!(pending().is_err());assert!(auth(json!({"radio_station":handle})).is_err());dispatch(root,&json!({"action":"permissions","id":m.id,"grants":["audio.playback"]})).unwrap();assert!(auth(json!({"radio_station":handle})).is_err());
  assert_eq!(service_requirements("radio.play",&json!({})),vec!["radio.stations","audio.playback"]);assert!(crate::sdk_tasks::request(None,"media.play",&json!({"queue":[{"url":"https://stream.example.org/music","title":"Fake","radio_station":handle}]})).is_err());
 }
 #[test]fn audiobooks_are_private_owner_bound_and_revocable(){
  let d=tempfile::tempdir().unwrap();let root=d.path();let old=package(fixture()).unwrap();let mut m=old.manifest.clone();m.id="org.example.audiobooks".into();m.capabilities=["audiobooks.library".into(),"audio.playback".into(),"storage.private".into()].into_iter().collect();m.network=None;let key=SigningKey::from_bytes(&[9;32]);m.publisher=hex(key.verifying_key().as_bytes());validate(&m).unwrap();let raw=serde_json::to_vec(&m).unwrap();let sig=key.sign(&signature_message(&raw,&old.wasm));let mut z=zip::ZipWriter::new(std::io::Cursor::new(Vec::new()));for(name,bytes)in[("manifest.json",raw.as_slice()),("main.wasm",old.wasm.as_slice()),("signature.ed25519",sig.to_bytes().as_slice())]{z.start_file(name,SimpleFileOptions::default()).unwrap();z.write_all(bytes).unwrap();}install(root,z.finish().unwrap().into_inner(),&["audiobooks.library","audio.playback","storage.private"]).unwrap();let a=get(&load(root).unwrap(),&m.id).unwrap().clone();let owner=json!({"id":a.manifest.id,"publisher":a.manifest.publisher,"hash":a.hash});let handle=uuid::Uuid::new_v4().to_string();
  let auth=|args:Value|dispatch(root,&json!({"action":"media_authorize","owner":owner,"args":args}));assert_eq!(auth(json!({"audiobook_track":handle})).unwrap()["audiobook_track"],handle);assert!(auth(json!({"url":"https://stream.example.org/music"})).is_err());assert!(auth(json!({"audiobook_track":handle,"url":"https://evil.example.org"})).is_err());let mut foreign=owner.clone();foreign["hash"]=json!("b".repeat(64));assert!(dispatch(root,&json!({"action":"media_authorize","owner":foreign,"args":{"audiobook_track":handle}})).is_err());
  assert!(dispatch(root,&json!({"action":"audiobook_authorize","owner":owner,"args":{"path":"/etc/passwd"}})).is_err());
  dispatch(root,&json!({"action":"permissions","id":m.id,"grants":["audio.playback","storage.private"]})).unwrap();assert!(auth(json!({"audiobook_track":handle})).is_err());
  assert!(crate::sdk_tasks::request(None,"media.play",&json!({"queue":[{"title":"Fake","audiobook_track":handle}]})).is_err());
 }
 #[test]fn podcast_broker_is_owner_bound_revocable_and_does_not_widen_generic_networking(){
  let d=tempfile::tempdir().unwrap();let root=d.path();let old=package(fixture()).unwrap();let mut m=old.manifest.clone();m.id="org.example.podcastbroker".into();m.capabilities=["podcasts.library".into(),"audio.playback".into()].into_iter().collect();m.network=None;let key=SigningKey::from_bytes(&[9;32]);m.publisher=hex(key.verifying_key().as_bytes());validate(&m).unwrap();let raw=serde_json::to_vec(&m).unwrap();let sig=key.sign(&signature_message(&raw,&old.wasm));let mut z=zip::ZipWriter::new(std::io::Cursor::new(Vec::new()));for(name,bytes)in[("manifest.json",raw.as_slice()),("main.wasm",old.wasm.as_slice()),("signature.ed25519",sig.to_bytes().as_slice())]{z.start_file(name,SimpleFileOptions::default()).unwrap();z.write_all(bytes).unwrap();}install(root,z.finish().unwrap().into_inner(),&["podcasts.library","audio.playback"]).unwrap();let a=get(&load(root).unwrap(),&m.id).unwrap().clone();let owner=json!({"id":a.manifest.id,"publisher":a.manifest.publisher,"hash":a.hash});let handle=uuid::Uuid::new_v4().to_string();
  let auth=|args:Value|dispatch(root,&json!({"action":"media_authorize","owner":owner,"args":args}));assert_eq!(auth(json!({"podcast_episode":handle})).unwrap()["podcast_episode"],handle);assert!(auth(json!({"url":"https://stream.example.org/music"})).is_err());assert!(auth(json!({"podcast_episode":handle,"url":"https://evil.example.org"})).is_err());let mut foreign=owner.clone();foreign["hash"]=json!("b".repeat(64));assert!(dispatch(root,&json!({"action":"media_authorize","owner":foreign,"args":{"podcast_episode":handle}})).is_err());
  let token=uuid::Uuid::new_v4().simple().to_string();{let mut sessions=SESSIONS.get().unwrap().lock().unwrap();sessions.insert(token.clone(),Session{root:root.into(),id:a.manifest.id.clone(),hash:a.hash.clone(),module:compile(&old.wasm).unwrap(),state:json!({}),frame:json!({}),pending:Some(("radio-token".into(),json!({"service":"podcasts.play","args":{"feed":"https://feeds.example.org/rss","episode":"a".repeat(64)}}),"done".into()))});}let pending=||dispatch(root,&json!({"action":"authorize","session":token,"token":"radio-token"}));assert_eq!(pending().unwrap()["owner"]["hash"],a.hash);dispatch(root,&json!({"action":"permissions","id":m.id,"grants":["podcasts.library"]})).unwrap();assert!(pending().is_err());assert!(auth(json!({"podcast_episode":handle})).is_err());dispatch(root,&json!({"action":"permissions","id":m.id,"grants":["audio.playback"]})).unwrap();assert!(auth(json!({"podcast_episode":handle})).is_err());
  assert_eq!(service_requirements("podcasts.play",&json!({})),vec!["podcasts.library","audio.playback"]);assert!(crate::sdk_tasks::request(None,"media.play",&json!({"queue":[{"url":"https://stream.example.org/music","title":"Fake","podcast_episode":handle}]})).is_err());
 }
 #[test]fn wrap_upload_live_authorization_resolves_only_owned_private_files(){
  let d=tempfile::tempdir().unwrap();let root=d.path();let old=package(fixture()).unwrap();let mut m=old.manifest.clone();m.id="org.example.upload".into();m.capabilities=["network.http".into(),"storage.private".into()].into_iter().collect();m.network=Some(NetworkPolicy{origins:vec!["https://api.example.com".into()],accounts:vec![]});validate(&m).unwrap();
  let key=SigningKey::from_bytes(&[9;32]);m.publisher=hex(key.verifying_key().as_bytes());let raw=serde_json::to_vec(&m).unwrap();let sig=key.sign(&signature_message(&raw,&old.wasm));let mut z=zip::ZipWriter::new(std::io::Cursor::new(Vec::new()));
  for(name,bytes)in [("manifest.json",raw.as_slice()),("main.wasm",old.wasm.as_slice()),("signature.ed25519",sig.to_bytes().as_slice())]{z.start_file(name,SimpleFileOptions::default()).unwrap();z.write_all(bytes).unwrap();}
  install(root,z.finish().unwrap().into_inner(),&["network.http","storage.private"]).unwrap();
  let a=get(&load(root).unwrap(),"org.example.upload").unwrap().clone();let f=crate::sdk_storage::save_bytes(&scope(root,&a.manifest.id).unwrap(),"local.bin","application/octet-stream",b"private").unwrap();let foreign=crate::sdk_storage::save_bytes(&scope(root,"org.example.otherupload").unwrap(),"foreign.bin","application/octet-stream",b"foreign").unwrap();let token=uuid::Uuid::new_v4().simple().to_string();let effect=json!({"service":"http.request","args":{"url":"https://api.example.com/upload","method":"POST","upload":f["id"]}});
  {let mut sessions=SESSIONS.get().unwrap().lock().unwrap();sessions.insert(token.clone(),Session{root:root.into(),id:a.manifest.id.clone(),hash:a.hash.clone(),module:compile(&old.wasm).unwrap(),state:json!({}),frame:json!({}),pending:Some(("upload-token".into(),effect,"done".into()))});}
  let call=||dispatch(root,&json!({"action":"authorize","session":token,"token":"upload-token"}));assert_eq!(call().unwrap()["upload_file"]["id"],f["id"]);
  {let mut sessions=SESSIONS.get().unwrap().lock().unwrap();sessions.get_mut(&token).unwrap().pending.as_mut().unwrap().1["args"]["upload"]=foreign["id"].clone();}assert!(call().is_err());
  dispatch(root,&json!({"action":"permissions","id":"org.example.upload","grants":["network.http"]})).unwrap();assert!(call().is_err());
 }
 #[test]fn http_manifest_and_live_authorization_bind_signed_origins(){
  let d=tempfile::tempdir().unwrap();let root=d.path();let old=package(fixture()).unwrap();let mut m=old.manifest.clone();m.id="org.example.http".into();m.capabilities=["network.http".into()].into_iter().collect();m.network=Some(NetworkPolicy{origins:vec!["https://api.example.com".into()],accounts:vec![]});validate(&m).unwrap();
  let key=SigningKey::from_bytes(&[9;32]);m.publisher=hex(key.verifying_key().as_bytes());let raw=serde_json::to_vec(&m).unwrap();let sig=key.sign(&signature_message(&raw,&old.wasm));let mut z=zip::ZipWriter::new(std::io::Cursor::new(Vec::new()));
  for(name,bytes)in [("manifest.json",raw.as_slice()),("main.wasm",old.wasm.as_slice()),("signature.ed25519",sig.to_bytes().as_slice())]{z.start_file(name,SimpleFileOptions::default()).unwrap();z.write_all(bytes).unwrap();}
  install(root,z.finish().unwrap().into_inner(),&["network.http"]).unwrap();
  // Use the normal broker session and a host-pending fixture to check live policy.
  let a=get(&load(root).unwrap(),"org.example.http").unwrap().clone();let token="http-fixture-session";
  let effect=json!({"service":"http.request","args":{"url":"https://api.example.com/items"}});
  {let mut sessions=SESSIONS.get().unwrap().lock().unwrap();sessions.insert(token.into(),Session{root:root.into(),id:a.manifest.id.clone(),hash:a.hash.clone(),module:compile(&old.wasm).unwrap(),state:json!({}),frame:json!({}),pending:Some(("request-token".into(),effect,"done".into()))});}
  let native=dispatch(root,&json!({"action":"authorize","session":token,"token":"request-token"})).unwrap();assert_eq!(native["origins"][0],"https://api.example.com");assert_eq!(native["owner"]["id"],"org.example.http");
  assert!(dispatch(root,&json!({"action":"authorize","session":token,"token":"wrong"})).is_err());
  dispatch(root,&json!({"action":"permissions","id":"org.example.http","grants":[]})).unwrap();assert!(dispatch(root,&json!({"action":"authorize","session":token,"token":"request-token"})).is_err());
 }
 #[test]fn tasks_background_owner_policy_and_media_handles_are_live_and_scoped(){
  let d=tempfile::tempdir().unwrap();let root=d.path();let old=package(fixture()).unwrap();let mut m=old.manifest.clone();m.id="org.example.taskbroker".into();m.capabilities=["network.http".into(),"background.tasks".into(),"storage.private".into(),"audio.playback".into()].into_iter().collect();m.network=Some(NetworkPolicy{origins:vec!["https://api.example.com".into()],accounts:vec![]});validate(&m).unwrap();
  let key=SigningKey::from_bytes(&[9;32]);m.publisher=hex(key.verifying_key().as_bytes());let raw=serde_json::to_vec(&m).unwrap();let sig=key.sign(&signature_message(&raw,&old.wasm));let mut z=zip::ZipWriter::new(std::io::Cursor::new(Vec::new()));
  for(name,bytes)in [("manifest.json",raw.as_slice()),("main.wasm",old.wasm.as_slice()),("signature.ed25519",sig.to_bytes().as_slice())]{z.start_file(name,SimpleFileOptions::default()).unwrap();z.write_all(bytes).unwrap();}
  install(root,z.finish().unwrap().into_inner(),&["network.http","background.tasks","storage.private","audio.playback"]).unwrap();
  let a=get(&load(root).unwrap(),"org.example.taskbroker").unwrap().clone();let owner=json!({"id":a.manifest.id,"publisher":a.manifest.publisher,"hash":a.hash});
  let policy=dispatch(root,&json!({"action":"background_authorize","owner":owner,"args":{"url":"https://api.example.com/file"}})).unwrap();assert_eq!(policy["origins"][0],"https://api.example.com");
  assert!(dispatch(root,&json!({"action":"background_authorize","owner":owner,"args":{"url":"https://other.example.com/"}})).is_err());let mut other=owner.clone();other["publisher"]=json!("b".repeat(64));assert!(dispatch(root,&json!({"action":"background_authorize","owner":other,"args":{}})).is_err());
  let file=crate::sdk_storage::save_bytes(&scope(root,&a.manifest.id).unwrap(),"Clip.mp3","audio/mpeg",b"media").unwrap();let media=dispatch(root,&json!({"action":"media_blob","owner":owner,"service":"resolve","args":{"id":file["id"]}})).unwrap();assert!(media["path"].is_string());
  assert!(dispatch(root,&json!({"action":"task_cleanup","owner":owner,"file":file["id"]})).is_err());
  dispatch(root,&json!({"action":"permissions","id":"org.example.taskbroker","grants":[]})).unwrap();assert!(dispatch(root,&json!({"action":"background_authorize","owner":owner,"args":{}})).is_err());assert!(dispatch(root,&json!({"action":"media_blob","owner":owner,"service":"resolve","args":{"id":file["id"]}})).is_err());
 }
 #[test]fn accounts_signed_policy_and_revocation_bind_pending_connection(){
  let d=tempfile::tempdir().unwrap();let root=d.path();let old=package(fixture()).unwrap();let mut m=old.manifest.clone();m.id="org.example.accountbroker".into();m.capabilities=["network.http".into(),"accounts.connect".into()].into_iter().collect();m.network=Some(NetworkPolicy{origins:vec!["https://api.example.com".into()],accounts:vec![AccountProvider{id:"example".into(),label:"Example".into(),authorization_endpoint:"https://api.example.com/auth".into(),token_endpoint:"https://api.example.com/token".into(),client_id:"public-client".into(),scopes:vec!["profile".into()],api_origins:vec!["https://api.example.com".into()],revocation_endpoint:None}]});validate(&m).unwrap();
  let key=SigningKey::from_bytes(&[9;32]);m.publisher=hex(key.verifying_key().as_bytes());let raw=serde_json::to_vec(&m).unwrap();let sig=key.sign(&signature_message(&raw,&old.wasm));let mut z=zip::ZipWriter::new(std::io::Cursor::new(Vec::new()));
  for(name,bytes)in [("manifest.json",raw.as_slice()),("main.wasm",old.wasm.as_slice()),("signature.ed25519",sig.to_bytes().as_slice())]{z.start_file(name,SimpleFileOptions::default()).unwrap();z.write_all(bytes).unwrap();}
  install(root,z.finish().unwrap().into_inner(),&["network.http","accounts.connect"]).unwrap();
  // Use the normal broker session and a host-pending fixture to check live policy.
  let a=get(&load(root).unwrap(),"org.example.accountbroker").unwrap().clone();let token="accounts-fixture-session";
  let effect=json!({"service":"accounts.connect","args":{"provider":"example"}});
  {let mut sessions=SESSIONS.get().unwrap().lock().unwrap();sessions.insert(token.into(),Session{root:root.into(),id:a.manifest.id.clone(),hash:a.hash.clone(),module:compile(&old.wasm).unwrap(),state:json!({}),frame:json!({}),pending:Some(("request-token".into(),effect,"done".into()))});}
  let native=dispatch(root,&json!({"action":"authorize","session":token,"token":"request-token"})).unwrap();assert_eq!(native["providers"][0]["api_origins"][0],"https://api.example.com");assert_eq!(native["owner"]["id"],"org.example.accountbroker");
  assert!(dispatch(root,&json!({"action":"authorize","session":token,"token":"wrong"})).is_err());
  dispatch(root,&json!({"action":"permissions","id":"org.example.accountbroker","grants":[]})).unwrap();assert!(dispatch(root,&json!({"action":"authorize","session":token,"token":"request-token"})).is_err());
 }
 #[test]fn signed_metadata_is_optional_bounded_and_rejects_newer_hosts(){
  let mut m=package(fixture()).unwrap().manifest;assert!(m.icon.is_none());assert!(m.min_host.is_none());let value=serde_json::to_value(&m).unwrap();assert!(value.get("icon").is_none());assert!(value.get("min_host").is_none());
  m.min_host=Some(HOST_BUILD);m.icon=Some(AppIcon{symbol:Some("notes".into()),png_hex:None});validate(&m).unwrap();m.min_host=Some(HOST_BUILD+1);assert!(validate(&m).unwrap_err().contains("requires"));m.min_host=Some(0);assert!(validate(&m).is_err());m.min_host=None;
  m.icon=Some(AppIcon{symbol:Some("unknown".into()),png_hex:None});assert!(validate(&m).is_err());m.icon=Some(AppIcon{symbol:None,png_hex:Some("00".repeat(6145))});assert!(validate(&m).is_err());
 }
 #[test]fn package_collection_retains_current_removed_apps_and_unrelated_entries(){
  let d=tempfile::tempdir().unwrap();let root=d.path();install(root,fixture(),&[]).unwrap();let mut r=load(root).unwrap();let a=r.apps.values_mut().next().unwrap();a.installed=false;let keep=blob(root,&a.hash).unwrap();save(root,&r).unwrap();let orphan=blob(root,&"a".repeat(64)).unwrap();fs::write(&orphan,b"old package").unwrap();let unrelated=orphan.with_file_name("notes.txt");fs::write(&unrelated,b"private content").unwrap();
  #[cfg(unix)]{std::os::unix::fs::symlink(&unrelated,orphan.with_file_name(format!("{}.onklav-a","b".repeat(64)))).unwrap();}
  collect_unused_packages(root,&r);assert!(keep.is_file());assert!(!orphan.exists());assert_eq!(fs::read(unrelated).unwrap(),b"private content");
 }
 #[test] fn incoming_consent_permission_revoke_and_local_decline_use_signed_controller(){
 let d=tempfile::tempdir().unwrap();let root=d.path();install(root,include_bytes!("../../sdk/packages/WalkieTalkie.onklav-a").to_vec(),&["storage.private"]).unwrap();
 let data=scope(root,"app.onklav.walkie").unwrap();let fixture=crate::walkie_session::fixture_incoming(&data,"+15550101234");
 let app=launch(root,"app.onklav.walkie");let t=app["session"].as_str().unwrap();assert_eq!(app["frame"]["menu_title"],"Walkie Talkie Invitation");
 assert!(event(root,t,"invite.accept","").unwrap()["frame"]["state"]["status"].as_str().unwrap().contains("access is off"));
 assert_eq!(crate::walkie_session::snapshot(&data).unwrap()["phase"],"incoming");
 if SESSIONS.get().unwrap().lock().unwrap().get(t).unwrap().frame["menu_title"]=="Permission needed"{event(root,t,"sdk.permission.dismiss","").unwrap();}
 let declined=event(root,t,"invite.decline","").unwrap();assert_eq!(declined["frame"]["state"]["meta"]["session"]["reason"],"declined");assert_eq!(declined["frame"]["menu_open"],false);
 assert!(crate::walkie_session::decline_local(&data,fixture["id"].as_str().unwrap()).is_err());
 dispatch(root,&json!({"action":"permissions","id":"app.onklav.walkie","grants":["storage.private","communications.walkie"]})).unwrap();
 crate::walkie_session::fixture_incoming(&data,"+15550104321");let app=launch(root,"app.onklav.walkie");let t=app["session"].as_str().unwrap();
 let pending=event(root,t,"invite.accept","").unwrap();assert_eq!(pending["pending"]["service"],"communications.walkie");let failed=dispatch(root,&json!({"action":"reply","session":t,"token":pending["pending"]["token"],"reply":{"ok":false,"error":"Carrier verification required. No invitation was sent."}})).unwrap();assert!(failed["frame"]["state"]["status"].as_str().unwrap().contains("No invitation was sent"));assert_eq!(crate::walkie_session::snapshot(&data).unwrap()["phase"],"incoming");
 dispatch(root,&json!({"action":"permissions","id":"app.onklav.walkie","grants":["storage.private"]})).unwrap();assert_eq!(crate::walkie_session::snapshot(&data).unwrap()["reason"],"cancelled");assert!(event(root,t,"invite.accept","").is_err());
 let blocked=json!({"action":"walkie_session","args":{"operation":"incoming","allowed":true,"wire":"!OKW1|P|aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa|0|wt1","peer":"5551234","subscription":2,"ready":true}});assert!(dispatch(root,&blocked).unwrap_err().contains("access is off"));
 }
 #[test] fn walkie_session_host_gate_recovery_and_guest_isolation(){
 let d=tempfile::tempdir().unwrap();let root=d.path();
 let request=json!({"action":"walkie_session","args":{"operation":"begin","peer":"5551234","subscription":2,"ready":true}});
 assert!(dispatch(root,&request).is_err());
 install(root,include_bytes!("../../sdk/packages/WalkieTalkie.onklav-a").to_vec(),&["storage.private","communications.walkie"]).unwrap();
 assert!(dispatch(root,&request).unwrap_err().contains("No invitation was sent"));
 let data=scope(root,"app.onklav.walkie").unwrap();assert!(!data.join("walkie-session.json").exists());
 // A durable pre-restart session fixture must never reopen as connected.
 fs::write(data.join("walkie-session.json"),serde_json::to_vec(&json!({"current":{"id":"a".repeat(32),"peer":"5551234","subscription":2,"process":"b".repeat(32),"phase":"connected","deadline":101000,"observed":1000,"attempt":null,"reason":""}})).unwrap()).unwrap();
 let app=launch(root,"app.onklav.walkie");let t=app["session"].as_str().unwrap();
 assert_eq!(app["frame"]["state"]["meta"]["session"]["phase"],"ended");
 assert_eq!(app["frame"]["state"]["meta"]["session"]["reason"],"interrupted");
 assert!(app["frame"]["state"]["status"].as_str().unwrap().contains("interrupted"));
 assert!(crate::walkie_store::service(&data,"walkie_session",&json!({"operation":"receive"})).is_err());
 assert!(crate::walkie_store::service(&data,"walkie.connected",&json!({"connected":true})).is_err());
 dispatch(root,&json!({"action":"permissions","id":"app.onklav.walkie","grants":[]})).unwrap();
 assert!(dispatch(root,&json!({"action":"walkie_session","args":{"operation":"state"}})).is_err());
 assert!(event(root,t,"retry","").is_err());
 }
 #[test] fn walkie_contact_selection_permission_cancel_validation_and_revocation(){
 let d=tempfile::tempdir().unwrap();let root=d.path();
 install(root,include_bytes!("../../sdk/packages/WalkieTalkie.onklav-a").to_vec(),&["storage.private"]).unwrap();
 let app=launch(root,"app.onklav.walkie");let t=app["session"].as_str().unwrap();
 let denied=event(root,t,"contacts","").unwrap();assert!(denied["pending"].is_null());assert!(denied["frame"]["state"]["status"].as_str().unwrap().contains("access is off"));
 dispatch(root,&json!({"action":"permissions","id":"app.onklav.walkie","grants":["storage.private","contacts.pick"]})).unwrap();
 let app=launch(root,"app.onklav.walkie");let t=app["session"].as_str().unwrap();
 let pick=event(root,t,"contacts","").unwrap();assert_eq!(pick["pending"]["service"],"contacts.pick");
 let reply=|pending:&Value,value:Value|json!({"action":"reply","session":t,"token":pending["pending"]["token"],"reply":{"ok":true,"value":value}});
 let stale=json!({"action":"reply","session":t,"token":"wrong","reply":{"ok":true,"value":{"phone":"5551234","name":"Wrong"}}});assert!(dispatch(root,&stale).is_err());
 let chosen=dispatch(root,&reply(&pick,json!({"phone":"+1 (555) 010-1234","name":"Charles","uri":"never expose"}))).unwrap();
 assert_eq!(chosen["frame"]["state"]["peer"],"+15550101234");assert_eq!(chosen["frame"]["state"]["label"],"Charles");
 assert_eq!(chosen["frame"]["state"]["meta"]["connection"],"offline");assert!(chosen["pending"].is_null());
 assert!(dispatch(root,&reply(&pick,json!({"phone":"5554321","name":"Replay"}))).is_err());
 let pick=event(root,t,"contacts","").unwrap();let invalid=dispatch(root,&reply(&pick,json!({"phone":"../bad","name":"Bad"}))).unwrap();assert_eq!(invalid["frame"]["state"]["peer"],"+15550101234");
 let pick=event(root,t,"contacts","").unwrap();let cancelled=dispatch(root,&json!({"action":"reply","session":t,"token":pick["pending"]["token"],"reply":{"ok":false,"error":"Contact selection cancelled"}})).unwrap();assert_eq!(cancelled["frame"]["state"]["label"],"Charles");
 let reopened=launch(root,"app.onklav.walkie");assert_eq!(reopened["frame"]["state"]["label"],"Charles");
 let pick=event(root,t,"contacts","").unwrap();dispatch(root,&json!({"action":"permissions","id":"app.onklav.walkie","grants":["storage.private"]})).unwrap();
 assert!(dispatch(root,&json!({"action":"authorize","session":t,"token":pick["pending"]["token"]})).is_err());assert!(dispatch(root,&reply(&pick,json!({"phone":"5554321","name":"Revoked"}))).is_err());
 }
 #[test] fn voice_component_permissions_binding_trim_commit_and_playback_are_host_only(){
 let d=tempfile::tempdir().unwrap();let root=d.path();let caps=["storage.private","audio.record","audio.playback","contacts.pick","communications.walkie"];
 install(root,include_bytes!("../../sdk/packages/WalkieTalkie.onklav-a").to_vec(),&caps).unwrap();
 let app=launch(root,"app.onklav.walkie");let token=app["session"].as_str().unwrap();event(root,token,"peer","+15550101234").unwrap();event(root,token,"prepare","").unwrap();event(root,token,"record.toggle","").unwrap();
 let auth=dispatch(root,&json!({"action":"host_voice","session":token,"widget":"capture"})).unwrap();let key=auth["key"].clone();
 let mut raw=b"#!AMR\n".to_vec();for _ in 0..610{raw.push(60);raw.extend([0;31]);}
 let mut req=json!({"action":"voice_prepare","session":token,"widget":"capture","key":key,"mime":"audio/amr","bytes":raw});
 assert_eq!(dispatch(root,&req).unwrap()["millis"],12000);
 event(root,token,"record.toggle","").unwrap();assert!(dispatch(root,&req).is_err());event(root,token,"record.toggle","").unwrap();
 event(root,token,"settings","").unwrap();assert!(dispatch(root,&req).is_err());event(root,token,"settings.close","").unwrap();
 req["action"]=json!("voice_commit");req["capture"]=json!("c".repeat(32));let saved=dispatch(root,&req).unwrap();assert_eq!(saved["frame"]["state"]["status"],"Voice clip saved locally.");
 let clips=event(root,token,"clips","").unwrap();assert_eq!(clips["frame"]["state"]["clips"][0]["state"],"queued");let widget=format!("clip.{}","c".repeat(32));
 let audio=dispatch(root,&json!({"action":"host_voice","session":token,"widget":widget,"read":true})).unwrap();assert_eq!(audio["audio"]["binding"]["outgoing"],true);
 dispatch(root,&json!({"action":"voice_played","session":token,"widget":widget,"key":audio["key"]})).unwrap();
 assert_eq!(event(root,token,"clips.refresh","").unwrap()["frame"]["state"]["clips"][0]["state"],"queued");
 event(root,token,"clips.close","").unwrap();assert!(dispatch(root,&json!({"action":"host_voice","session":token,"widget":widget,"read":true})).is_err());
 dispatch(root,&json!({"action":"permissions","id":"app.onklav.walkie","grants":["storage.private"]})).unwrap();assert!(dispatch(root,&req).is_err());
 let app=launch(root,"app.onklav.walkie");assert!(dispatch(root,&json!({"action":"host_voice","session":app["session"],"widget":"capture"})).is_err());
 }
 #[test] fn walkie_archive_is_consumed_by_signed_sdk_and_host_writes_are_gated(){
 let d=tempfile::tempdir().unwrap();let root=d.path();
 install(root,include_bytes!("../../sdk/packages/WalkieTalkie.onklav-a").to_vec(),&["storage.private"]).unwrap();
 let mut audio=b"#!AMR\n".to_vec();for _ in 0..20{audio.push(60);audio.extend([0;31]);}
 let ingest=|n:u32|json!({"action":"walkie_archive","args":{"operation":"ingest","binding":{"id":format!("{n:032x}"),"session":"a".repeat(32),"peer":"+15550101234","subscription":2,"outgoing":false},"sequence":n,"mime":"audio/amr","bytes":audio}});
 for n in 1..=8 {dispatch(root,&ingest(n)).unwrap();}
 let app=launch(root,"app.onklav.walkie");let token=app["session"].as_str().unwrap();let opened=event(root,token,"clips","").unwrap();
 assert_eq!(opened["frame"]["state"]["clips"].as_array().unwrap().len(),8);
 assert_eq!(opened["frame"]["drawer"].as_array().unwrap().iter().filter(|n|n["kind"]=="voice_clip").count(),6);
 let next=event(root,token,"clips.next","").unwrap();assert_eq!(next["frame"]["drawer"].as_array().unwrap().iter().filter(|n|n["kind"]=="voice_clip").count(),2);
 event(root,token,"clips.previous","").unwrap();let action=format!("clip.delete.{:032x}",8);event(root,token,&action,"").unwrap();event(root,token,"clips.cancel","").unwrap();
 assert_eq!(event(root,token,"clips.refresh","").unwrap()["frame"]["state"]["clips"].as_array().unwrap().len(),8);
 event(root,token,&action,"").unwrap();let gone=event(root,token,"clips.accept","").unwrap();assert_eq!(gone["frame"]["state"]["clips"].as_array().unwrap().len(),7);
 assert_eq!(dispatch(root,&ingest(8)).unwrap()["duplicate"],true);
 event(root,token,"clips.clear","").unwrap();let cleared=event(root,token,"clips.accept","").unwrap();assert_eq!(cleared["frame"]["state"]["clips"],json!([]));
 assert_eq!(dispatch(root,&ingest(1)).unwrap()["duplicate"],true);
 fs::write(scope(root,"app.onklav.walkie").unwrap().join("walkie-clips.json"),b"{broken").unwrap();
 let failed=event(root,token,"clips.refresh","").unwrap();assert_eq!(failed["frame"]["drawer_open"],false);assert!(failed["frame"]["state"]["status"].as_str().unwrap().contains("could not be opened"));
 dispatch(root,&json!({"action":"permissions","id":"app.onklav.walkie","grants":[]})).unwrap();assert!(dispatch(root,&ingest(9)).is_err());
 dispatch(root,&json!({"action":"remove","id":"app.onklav.walkie","delete_data":true})).unwrap();assert!(!scope(root,"app.onklav.walkie").unwrap().join("walkie-clips.json").exists());assert!(dispatch(root,&ingest(9)).is_err());
 }
 #[test] fn walkie_signed_controller_persists_preferences_and_never_fakes_connection(){
 let d=tempfile::tempdir().unwrap();let root=d.path();
 install(root,include_bytes!("../../sdk/packages/WalkieTalkie.onklav-a").to_vec(),&["storage.private","communications.walkie"]).unwrap();
 let app=launch(root,"app.onklav.walkie");let token=app["session"].as_str().unwrap();
 assert_eq!(app["frame"]["state"]["meta"]["connection"],"offline");
 assert!(event(root,token,"dial","").is_err());assert!(event(root,token,"connect","").is_err());
 event(root,token,"peer","+1 (555) 010-1234").unwrap();let saved=event(root,token,"prepare","").unwrap();
 assert_eq!(saved["frame"]["state"]["peer"],"+15550101234");
 let pending=event(root,token,"connect","").unwrap();assert_eq!(pending["pending"]["service"],"communications.walkie");let connect=dispatch(root,&json!({"action":"reply","session":token,"token":pending["pending"]["token"],"reply":{"ok":false,"error":"Carrier verification is required. No invitation was sent."}})).unwrap();assert!(connect["frame"]["state"]["status"].as_str().unwrap().contains("No invitation was sent"));assert!(connect["pending"].is_null());assert_eq!(connect["frame"]["menu_open"],true);event(root,token,"connection.close","").unwrap();
 event(root,token,"settings","").unwrap();event(root,token,"settings.auto_send","false").unwrap();event(root,token,"settings.close","").unwrap();
 assert_eq!(event(root,token,"settings","").unwrap()["frame"]["state"]["draft"]["auto_send"],true);
 event(root,token,"settings.auto_send","false").unwrap();event(root,token,"settings.save","").unwrap();
 dispatch(root,&json!({"action":"close","session":token})).unwrap();
 let reopened=launch(root,"app.onklav.walkie");let token=reopened["session"].as_str().unwrap();
 assert_eq!(reopened["frame"]["state"]["meta"]["preferences"]["auto_send"],false);
 assert_eq!(reopened["frame"]["state"]["meta"]["connection"],"offline");
 assert!(event(root,token,"peer","5550109999").is_err());let quick=event(root,token,"quick.auto_play","false").unwrap();assert_eq!(quick["frame"]["state"]["peer"],"+15550101234");
 event(root,token,"history","").unwrap();event(root,token,"history.clear","").unwrap();event(root,token,"clear.cancel","").unwrap();
 assert_eq!(event(root,token,"history.clear","").unwrap()["frame"]["state"]["meta"]["recent"].as_array().unwrap().len(),1);
 assert_eq!(event(root,token,"clear.accept","").unwrap()["frame"]["state"]["meta"]["recent"],json!([]));
 dispatch(root,&json!({"action":"permissions","id":"app.onklav.walkie","grants":[]})).unwrap();
 assert!(event(root,token,"prepare","").is_err());
 let denied=launch(root,"app.onklav.walkie");assert!(denied["frame"]["state"]["meta"].is_null());
 dispatch(root,&json!({"action":"remove","id":"app.onklav.walkie","delete_data":true})).unwrap();
 assert!(!scope(root,"app.onklav.walkie").unwrap().join("walkie.json").exists());
 }
 #[test]fn social_is_only_a_permission_free_hello_world_package(){let d=tempfile::tempdir().unwrap();let root=d.path();let installed=install(root,include_bytes!("../../sdk/packages/Social.onklav-a").to_vec(),&[]).unwrap();assert_eq!(installed["capabilities"],json!([]));let app=launch(root,"app.onklav.social");assert_eq!(app["frame"]["title"],"Social");assert_eq!(app["frame"]["body"][0]["text"],"Hello World");assert_eq!(app["frame"]["body"].as_array().unwrap().len(),1);assert!(app["pending"].is_null());assert!(app["frame"]["actions"].as_array().unwrap().is_empty());dispatch(root,&json!({"action":"close","session":app["session"]})).unwrap();dispatch(root,&json!({"action":"remove","id":"app.onklav.social","delete_data":true})).unwrap();}
 #[test]fn drawing_component_count_is_bounded(){
 let n=json!({"kind":"canvas","id":"one","document":null,"tool":"pen","width":4,"color":4294901760u32,"on_change":"changed"});
 let mut frame=json!({"title":"Drawing","state":null,"actions":[],"body":[n],"drawer":[],"menu":[]});assert!(validate_frame(&frame).is_ok());let mut second=frame["body"][0].clone();second["id"]=json!("two");frame["body"].as_array_mut().unwrap().push(second);assert_eq!(validate_frame(&frame).unwrap_err(),"Use One Visible Drawing Canvas");
 }
 #[test]fn annotation_guest_edits_import_permissions_export_and_reopen(){
 let d=tempfile::tempdir().unwrap();let root=d.path();install(root,include_bytes!("../../sdk/packages/Annotation.onklav-a").to_vec(),&["storage.private","images.edit","files.images","files.export","clipboard.write","share.text"]).unwrap();let a=launch(root,"app.onklav.annotation");let t=a["session"].as_str().unwrap();assert!(a["frame"]["state"]["doc"].is_null());let p=event(root,t,"image.open","").unwrap();assert_eq!(p["pending"]["service"],"images.import");let image=crate::drawing::encode(include_bytes!("../../docs/fixtures/annotation-base.jpg"));let loaded=dispatch(root,&json!({"action":"reply","session":t,"token":p["pending"]["token"],"reply":{"ok":true,"value":{"image":image}}})).unwrap();let id=loaded["frame"]["state"]["doc"]["id"].clone();
 let canvas=|rev:u64,value:Value|dispatch(root,&json!({"action":"canvas_event","session":t,"widget":"image.canvas","document":id,"revision":rev,"value":value}));
 let stroke=json!({"op":"stroke","mark":{"tool":"pen","color":4293476930u32,"width":4,"x":0,"y":0,"points":[[10,10],[30,30]]}});let drawn=canvas(0,stroke.clone()).unwrap();assert_eq!(drawn["frame"]["state"]["doc"]["count"],1);assert!(canvas(0,stroke).is_err());
 event(root,t,"canvas.undo","").unwrap();event(root,t,"canvas.redo","").unwrap();let tapped=canvas(3,json!({"op":"text","x":20,"y":30})).unwrap();assert_eq!(tapped["frame"]["menu_title"],"Text Note");event(root,t,"note.text","A note").unwrap();let text=event(root,t,"note.accept","").unwrap();assert_eq!(text["pending"]["service"],"images.text");let text=dispatch(root,&json!({"action":"reply","session":t,"token":text["pending"]["token"],"reply":{"ok":true,"value":{"box_width":80,"box_height":40}}})).unwrap();assert_eq!(text["frame"]["state"]["doc"]["count"],2);
 event(root,t,"image.title","My Image").unwrap();event(root,t,"export","").unwrap();let p=event(root,t,"export.copy","").unwrap();let op=dispatch(root,&json!({"action":"authorize","session":t,"token":p["pending"]["token"]})).unwrap();assert_eq!(op["document"]["title"],"My Image");assert_eq!(op["document"]["marks"].as_array().unwrap().len(),2);dispatch(root,&json!({"action":"reply","session":t,"token":p["pending"]["token"],"reply":{"ok":true}})).unwrap();
 event(root,t,"library","").unwrap();let row=format!("open:{}",id.as_str().unwrap());assert!(dispatch(root,&json!({"action":"host_drawing_image","session":t,"widget":row})).unwrap()["image"].is_string());event(root,t,"drawer.close","").unwrap();assert!(dispatch(root,&json!({"action":"host_drawing_image","session":t,"widget":row})).is_err());
 dispatch(root,&json!({"action":"close","session":t})).unwrap();let a=launch(root,"app.onklav.annotation");assert_eq!(a["frame"]["state"]["doc"]["count"],2);assert_eq!(a["frame"]["state"]["doc"]["title"],"My Image");let token=a["session"].as_str().unwrap();dispatch(root,&json!({"action":"permissions","id":"app.onklav.annotation","grants":["storage.private"]})).unwrap();assert!(dispatch(root,&json!({"action":"host_canvas","session":token,"widget":"image.canvas"})).is_err());let denied=launch(root,"app.onklav.annotation");assert!(denied["frame"]["state"]["doc"].is_null());assert!(denied["frame"]["state"]["status"].as_str().unwrap().contains("access is off"));
 }
 #[test] fn youtube_channel_bookmarks_use_validated_metadata_and_persist(){
 let d=tempfile::tempdir().unwrap();let root=d.path();install(root,include_bytes!("../../sdk/packages/YouTube.onklav-a").to_vec(),&["storage.private","network.web","network.thumbnails","clipboard.read","clipboard.write","links.external"]).unwrap();
 let a=launch(root,"app.onklav.youtube");let t=&a["session"];let tab=&a["frame"]["body"][0]["tab"];
 let metadata=|channel:&str|dispatch(root,&json!({"action":"web_event","session":t,"widget":"youtube.page","tab":tab,"value":{"url":"https://www.youtube.com/watch?v=AbC_def-123","title":"Video","back":true,"channel":channel,"channel_title":"Example"}}));
 let bad=metadata("https://evil.test/@Example").unwrap();assert_eq!(bad["frame"]["state"]["channel"],"");assert!(bad["frame"].get("back_action").is_none());
 metadata("https://m.youtube.com/@Example/videos").unwrap();event(root,t.as_str().unwrap(),"channels","").unwrap();let saved=event(root,t.as_str().unwrap(),"channel.add","").unwrap();assert_eq!(saved["frame"]["state"]["listing"]["total"],1);assert_eq!(saved["frame"]["state"]["listing"]["items"][0]["url"],"https://www.youtube.com/@Example");
 dispatch(root,&json!({"action":"close","session":t})).unwrap();let a=launch(root,"app.onklav.youtube");let listed=event(root,a["session"].as_str().unwrap(),"channels","").unwrap();assert_eq!(listed["frame"]["state"]["listing"]["total"],1);
 }
 #[test] fn youtube_guest_library_reset_suppression_and_capability_lifecycle(){
 let d=tempfile::tempdir().unwrap();let root=d.path();install(root,include_bytes!("../../sdk/packages/YouTube.onklav-a").to_vec(),&["storage.private","network.web","network.thumbnails","clipboard.read","clipboard.write","links.external"]).unwrap();
 let a=launch(root,"app.onklav.youtube");let token=a["session"].as_str().unwrap();let tab=a["frame"]["body"][0]["tab"].clone();
 let navigate=|epoch:u64,url:&str,kind:&str|dispatch(root,&json!({"action":"web_event","session":token,"widget":"youtube.page","tab":tab,"epoch":epoch,"value":{"url":url,"title":"Example — YouTube","kind":kind}}));
 let video="https://www.youtube.com/watch?v=AbC_def-123";
 assert_eq!(navigate(0,video,"navigation").unwrap()["frame"]["state"]["meta"]["history"],1);
 assert!(navigate(0,"https://www.youtube.com/signin","navigation").is_err());
 let notice=navigate(0,"https://accounts.google.com/signin","account").unwrap();assert!(notice["pending"].is_null());assert_eq!(notice["frame"]["menu_title"],"YouTube Sign-In");event(root,token,"menu.close","").unwrap();
 event(root,token,"bookmarks","").unwrap();let saved=event(root,token,"bookmark.add","").unwrap();assert_eq!(saved["frame"]["state"]["meta"]["bookmarks"],1);
 let row=saved["frame"]["drawer"].as_array().unwrap().iter().find(|n|n["kind"]=="media_record").unwrap();let rowid=row["id"].clone();
 let image=dispatch(root,&json!({"action":"host_image","session":token,"widget":rowid})).unwrap();assert_eq!(image["app"],"app.onklav.youtube");assert_eq!(image["download"],true);
 let copied=event(root,token,row["copy"].as_str().unwrap(),"").unwrap();assert_eq!(copied["pending"]["service"],"clipboard.write");dispatch(root,&json!({"action":"reply","session":token,"token":copied["pending"]["token"],"reply":{"ok":true}})).unwrap();
 event(root,token,"drawer.close","").unwrap();assert!(dispatch(root,&json!({"action":"host_image","session":token,"widget":rowid})).is_err());
 event(root,token,"history","").unwrap();event(root,token,"history.clear","").unwrap();event(root,token,"confirm.cancel","").unwrap();event(root,token,"history.clear","").unwrap();let clear=event(root,token,"confirm.accept","").unwrap();assert_eq!(clear["frame"]["state"]["meta"]["history"],0);assert_eq!(clear["pending"]["service"],"images.clear");dispatch(root,&json!({"action":"reply","session":token,"token":clear["pending"]["token"],"reply":{"ok":true}})).unwrap();
 assert!(navigate(0,video,"navigation").is_err());let suppressed=navigate(1,video,"navigation").unwrap();assert_eq!(suppressed["frame"]["state"]["meta"]["history"],0);
 event(root,token,"drawer.close","").unwrap();event(root,token,"viewer.reset","").unwrap();let reset=event(root,token,"confirm.accept","").unwrap();assert_eq!(reset["frame"]["body"][0]["command"],"reset");assert_eq!(reset["frame"]["state"]["meta"]["bookmarks"],1);dispatch(root,&json!({"action":"reply","session":token,"token":reset["pending"]["token"],"reply":{"ok":true}})).unwrap();
 dispatch(root,&json!({"action":"permissions","id":"app.onklav.youtube","grants":["storage.private","network.web","clipboard.read","clipboard.write"]})).unwrap();assert!(navigate(2,video,"navigation").is_err());let b=launch(root,"app.onklav.youtube");let t=b["session"].as_str().unwrap();let bookmarks=event(root,t,"bookmarks","").unwrap();let id=bookmarks["frame"]["drawer"].as_array().unwrap().iter().find(|n|n["kind"]=="media_record").unwrap()["id"].clone();assert_eq!(dispatch(root,&json!({"action":"host_image","session":t,"widget":id})).unwrap()["download"],false);
 }
 #[test] fn browser_guest_ui_data_and_web_callbacks_are_scoped_and_non_gestural(){
 let d=tempfile::tempdir().unwrap();let root=d.path();install(root,include_bytes!("../../sdk/packages/Browser.onklav-a").to_vec(),&["storage.private","network.web","clipboard.read","clipboard.write"]).unwrap();let app=launch(root,"app.onklav.browser");let token=app["session"].as_str().unwrap();let widget="browser.page";
 let native=dispatch(root,&json!({"action":"host_web","session":token,"widget":widget})).unwrap();let tab=native["node"]["tab"].clone();assert_eq!(native["app"],"app.onklav.browser");
 let updated=dispatch(root,&json!({"action":"web_event","session":token,"widget":widget,"tab":tab,"value":{"url":"https://example.org/page","title":"An Example","back":true}})).unwrap();assert_eq!(updated["frame"]["state"]["meta"]["tabs"][0]["title"],"An Example");assert_eq!(updated["frame"]["state"]["back"],true);
 event(root,token,"bookmarks","").unwrap();event(root,token,"bookmark.add","").unwrap();event(root,token,"drawer.close","").unwrap();event(root,token,"tabs","").unwrap();let next=event(root,token,"tab.new","").unwrap();assert_eq!(next["frame"]["state"]["meta"]["tabs"].as_array().unwrap().len(),2);
 assert!(dispatch(root,&json!({"action":"web_event","session":token,"widget":widget,"tab":tab,"value":{"url":"https://example.org/late"}})).is_err());
 event(root,token,"settings","").unwrap();let settings=event(root,token,"settings.home","https://example.org/home").unwrap();assert_eq!(settings["frame"]["state"]["settings"]["home"],"https://example.org/home");event(root,token,"settings.save","").unwrap();dispatch(root,&json!({"action":"close","session":token})).unwrap();let reopened=launch(root,"app.onklav.browser");assert_eq!(reopened["frame"]["state"]["meta"]["bookmarks"],1);assert_eq!(reopened["frame"]["state"]["meta"]["home"],"https://example.org/home");
 let token=reopened["session"].as_str().unwrap();dispatch(root,&json!({"action":"permissions","id":"app.onklav.browser","grants":["storage.private","network.web"]})).unwrap();assert!(dispatch(root,&json!({"action":"host_web","session":token,"widget":widget})).is_err());let denied=launch(root,"app.onklav.browser");assert!(dispatch(root,&json!({"action":"host_web","session":denied["session"],"widget":widget})).is_err());
 let mut sessions=SESSIONS.get().unwrap().lock().unwrap();let session=sessions.get_mut(denied["session"].as_str().unwrap()).unwrap();session.frame["body"].as_array_mut().unwrap().iter_mut().find(|n|n["kind"]=="web").unwrap()["javascript"]=json!(false);drop(sessions);assert!(dispatch(root,&json!({"action":"host_web","session":denied["session"],"widget":widget})).is_err());
 }
 #[test]fn wrap_map_callbacks_are_owned_bounded_and_non_gestural(){let d=tempfile::tempdir().unwrap();let root=d.path();install(root,include_bytes!("../../sdk/packages/Browser.onklav-a").to_vec(),&["storage.private","network.web","clipboard.read","clipboard.write","links.external"]).unwrap();let a=launch(root,"app.onklav.browser");let token=a["session"].as_str().unwrap();{let mut sessions=SESSIONS.get().unwrap().lock().unwrap();sessions.get_mut(token).unwrap().frame["body"]=json!([{"id":"map","kind":"map_viewer","on_viewport":"external"}]);}let call=|id:&str,w:u64|dispatch(root,&json!({"action":"map_event","session":token,"node":id,"value":{"width":w,"height":300}}));assert!(call("foreign",300).is_err());assert!(call("map",5000).is_err());let result=call("map",300).unwrap();assert!(result["pending"].is_null());assert!(result["frame"]["state"]["status"].as_str().unwrap().contains("direct button tap"));}
 #[test] fn web_callback_cannot_authorize_clipboard_or_other_host_tap_actions(){
 let d=tempfile::tempdir().unwrap();let root=d.path();install(root,include_bytes!("../../sdk/packages/Browser.onklav-a").to_vec(),&["storage.private","network.web","clipboard.read","clipboard.write","links.external"]).unwrap();let a=launch(root,"app.onklav.browser");let token=a["session"].as_str().unwrap();let mut sessions=SESSIONS.get().unwrap().lock().unwrap();let s=sessions.get_mut(token).unwrap();s.frame["body"].as_array_mut().unwrap().iter_mut().find(|n|n["kind"]=="web").unwrap()["on_change"]=json!("external");drop(sessions);let n=dispatch(root,&json!({"action":"host_web","session":token,"widget":"browser.page"})).unwrap();let out=dispatch(root,&json!({"action":"web_event","session":token,"widget":"browser.page","tab":n["node"]["tab"],"value":{"url":"https://example.org/"}})).unwrap();assert!(out["pending"].is_null());assert!(out["frame"]["state"]["status"].as_str().unwrap().contains("direct button tap"));
 }
 #[test] fn reader_guest_draft_selection_clear_reopen_and_live_voice_permission(){
 let d=tempfile::tempdir().unwrap();let root=d.path();install(root,include_bytes!("../../sdk/packages/ReadAloud.onklav-a").to_vec(),&["storage.private","clipboard.read"]).unwrap();
 let a=launch(root,"app.onklav.reader");let token=a["session"].as_str().unwrap();
 assert!(dispatch(root,&json!({"action":"host_speech","session":token,"widget":"reader.playback"})).is_err());
 event(root,token,"text","Hello 🐈 world.").unwrap();
 let p=dispatch(root,&json!({"action":"event","session":token,"event":{"action":"paste","selection":6,"selection_end":8}})).unwrap();
 let pasted=dispatch(root,&json!({"action":"reply","session":token,"token":p["pending"]["token"],"reply":{"ok":true,"value":"cat"}})).unwrap();assert_eq!(pasted["frame"]["state"]["note"]["text"],"Hello cat world.");
 let huge="界".repeat(50000);event(root,token,"text",&huge).unwrap();assert!(event(root,token,"text",&format!("{huge}a")).is_err());
 let p=event(root,token,"paste","").unwrap();let rejected=dispatch(root,&json!({"action":"reply","session":token,"token":p["pending"]["token"],"reply":{"ok":true,"value":"x"}})).unwrap();assert_eq!(rejected["frame"]["state"]["note"]["text"],huge);
 event(root,token,"clear","").unwrap();event(root,token,"clear.cancel","").unwrap();dispatch(root,&json!({"action":"close","session":token})).unwrap();
 dispatch(root,&json!({"action":"permissions","id":"app.onklav.reader","grants":["storage.private","speech.output"]})).unwrap();let a=launch(root,"app.onklav.reader");let token=a["session"].as_str().unwrap();assert_eq!(a["frame"]["state"]["note"]["text"],huge);
 assert!(dispatch(root,&json!({"action":"host_speech","session":token,"widget":"reader.playback"})).is_ok());assert!(dispatch(root,&json!({"action":"host_speech","session":token,"widget":"reader.voices"})).is_err());event(root,token,"settings","").unwrap();assert!(dispatch(root,&json!({"action":"host_speech","session":token,"widget":"reader.voices"})).is_ok());event(root,token,"settings.close","").unwrap();assert!(dispatch(root,&json!({"action":"host_speech","session":token,"widget":"reader.voices"})).is_err());
 event(root,token,"clear","").unwrap();event(root,token,"clear.accept","").unwrap();dispatch(root,&json!({"action":"permissions","id":"app.onklav.reader","grants":["storage.private"]})).unwrap();assert!(dispatch(root,&json!({"action":"host_speech","session":token,"widget":"reader.playback"})).is_err());assert_eq!(launch(root,"app.onklav.reader")["frame"]["state"]["note"]["text"],"");
 }
 #[test] fn actual_rss_guest_refresh_cache_bookmarks_and_offline_reopen(){
  let d=tempfile::tempdir().unwrap();let root=d.path();let caps=["storage.private","network.feeds","network.web","share.text","clipboard.write"];
  install(root,include_bytes!("../../sdk/packages/RSS.onklav-a").to_vec(),&caps).unwrap();
  let mut out=launch(root,"app.onklav.rss");let token=out["session"].as_str().unwrap().to_string();
  for n in 0..4 {assert_eq!(out["pending"]["service"],"feeds.fetch");let pending=out["pending"]["token"].clone();out=dispatch(root,&json!({"action":"reply","session":token,"token":pending,"reply":{"ok":true,"value":{"title":"Fixture","items":[{"url":format!("https://example.org/story{n}"),"title":format!("Headline {n}"),"body":"Offline article content","published":n+1}]}}})).unwrap();}
  assert!(out["pending"].is_null());assert_eq!(out["frame"]["state"]["listing"]["total"],4);
  let id=out["frame"]["state"]["listing"]["items"][0]["id"].as_str().unwrap().to_string();
  let article=event(root,&token,&format!("open:{id}"),"").unwrap();assert_eq!(article["frame"]["state"]["article"]["article"]["body"],"Offline article content");
  let marked=event(root,&token,"article.bookmark","").unwrap();assert_eq!(marked["frame"]["state"]["article"]["bookmarked"],true);
  event(root,&token,"article.back","").unwrap();let drawer=event(root,&token,"drawer.bookmarks","").unwrap();assert_eq!(drawer["frame"]["state"]["bookmarks"]["total"],1);
  event(root,&token,"drawer.close","").unwrap();event(root,&token,"settings","").unwrap();event(root,&token,"settings.unread","true").unwrap();let settings=event(root,&token,"settings.done","").unwrap();assert_eq!(settings["frame"]["state"]["listing"]["total"],3);
  dispatch(root,&json!({"action":"close","session":token})).unwrap();dispatch(root,&json!({"action":"permissions","id":"app.onklav.rss","grants":["storage.private"]})).unwrap();let reopened=launch(root,"app.onklav.rss");assert_eq!(reopened["frame"]["state"]["listing"]["total"],3);
 }
 #[test] fn rss_adoption_checks_publisher_grant_and_guest_preserves_article_page(){
  let d=tempfile::tempdir().unwrap();let root=d.path();install(root,include_bytes!("../../sdk/packages/RSS.onklav-a").to_vec(),&["storage.private","network.feeds"]).unwrap();
  let publisher=load(root).unwrap().apps["app.onklav.rss"].manifest.publisher.clone();
  let mut request=json!({"action":"adopt_own_feeds","publisher":"wrong","feeds":"[\"https://example.org/feed\"]"});assert!(dispatch(root,&request).is_err());
  request["publisher"]=json!(publisher);dispatch(root,&json!({"action":"permissions","id":"app.onklav.rss","grants":[]})).unwrap();assert!(dispatch(root,&request).is_err());
  dispatch(root,&json!({"action":"permissions","id":"app.onklav.rss","grants":["storage.private"]})).unwrap();assert_eq!(dispatch(root,&request).unwrap()["count"],1);
  dispatch(root,&json!({"action":"permissions","id":"app.onklav.rss","grants":["storage.private","network.feeds"]})).unwrap();
  let out=launch(root,"app.onklav.rss");let token=out["session"].as_str().unwrap();assert_eq!(out["pending"]["args"]["url"],"https://example.org/feed");assert!(dispatch(root,&request).is_err());
  let items:Vec<_>=(0..40).map(|i|json!({"url":format!("https://example.org/story{i}"),"title":format!("Story {i}"),"body":"Offline","published":i+1})).collect();
  dispatch(root,&json!({"action":"reply","session":token,"token":out["pending"]["token"],"reply":{"ok":true,"value":{"items":items}}})).unwrap();
  let page=event(root,token,"page.next","").unwrap();let list_key=page["frame"]["scroll_key"].clone();assert_eq!(page["frame"]["state"]["page"],1);
  let article_id=page["frame"]["state"]["listing"]["items"][0]["id"].as_str().unwrap();let article=event(root,token,&format!("open:{article_id}"),"").unwrap();assert_eq!(article["frame"]["back_action"],"article.back");assert_ne!(article["frame"]["scroll_key"],list_key);
  let back=event(root,token,"article.back","").unwrap();assert_eq!(back["frame"]["scroll_key"],list_key);
  event(root,token,"drawer.bookmarks","").unwrap();let closed=event(root,token,"drawer.close","").unwrap();assert_eq!(closed["frame"]["state"]["page"],1);assert_eq!(closed["frame"]["scroll_key"],list_key);
 }
 #[test] fn rss_fetch_permission_revocation_prevents_commit(){let d=tempfile::tempdir().unwrap();let root=d.path();install(root,include_bytes!("../../sdk/packages/RSS.onklav-a").to_vec(),&["storage.private","network.feeds"]).unwrap();let out=launch(root,"app.onklav.rss");let token=out["session"].clone();let pending=out["pending"]["token"].clone();dispatch(root,&json!({"action":"permissions","id":"app.onklav.rss","grants":["storage.private"]})).unwrap();assert!(dispatch(root,&json!({"action":"reply","session":token,"token":pending,"reply":{"ok":true,"value":{"items":[]}}})).is_err());assert!(!scope(root,"app.onklav.rss").unwrap().join("feeds.json").exists());}
 #[test] fn actual_notes_guest_pages_every_saved_match_without_losing_search(){
  let d=tempfile::tempdir().unwrap();let root=d.path();install(root,fixture(),&["storage.private"]).unwrap();let data=scope(root,"app.onklav.notes").unwrap();
  for n in 1..=45{crate::save_note(&data,&Note{id:format!("{n:032x}"),title:format!("Paged {n}"),text:"paged match".into(),updated:n}).unwrap();}
  let app=launch(root,"app.onklav.notes");let token=app["session"].as_str().unwrap();let first=event(root,token,"drawer","").unwrap();assert_eq!(first["frame"]["state"]["records"].as_array().unwrap().len(),40);
  let second=event(root,token,"library.next","").unwrap();assert_eq!(second["frame"]["state"]["records"].as_array().unwrap().len(),5);
  let reset=event(root,token,"library.query","Paged 45").unwrap();assert_eq!(reset["frame"]["state"]["offset"],0);assert_eq!(reset["frame"]["state"]["records"].as_array().unwrap().len(),1);
 }
 #[test] fn content_file_previews_require_the_live_files_grant_and_only_resolve_media(){let d=tempfile::tempdir().unwrap();let r=d.path();install(r,include_bytes!("../../sdk/packages/Files.onklav-a").to_vec(),&["files.manage"]).unwrap();fs::write(r.join("content-import.tmp"),b"image fixture").unwrap();let image=crate::content::import_staged(r,"images","Photo.jpg","image/jpeg","app.camera","Camera",true).unwrap();let request=json!({"action":"files_preview","id":image["id"]});assert_eq!(dispatch(r,&request).unwrap()["mime"],"image/jpeg");let text=crate::content::store_text(r,"test.app","Test","readme","text").unwrap();assert!(dispatch(r,&json!({"action":"files_preview","id":text["id"]})).is_err());dispatch(r,&json!({"action":"permissions","id":"app.onklav.files","grants":[]})).unwrap();assert!(dispatch(r,&request).is_err());}
 #[test] fn messenger_media_import_uses_shared_media_folders_and_rejects_other_types(){let d=tempfile::tempdir().unwrap();let root=d.path();fs::write(root.join("content-import.tmp"),b"selected audio").unwrap();let saved=dispatch(root,&json!({"action":"message_import","name":"Clip.amr","mime":"audio/amr"})).unwrap();let folder=crate::content::service(root,"library.list",&json!({"folder":"audio"})).unwrap();assert_eq!(folder["items"][0]["name"],"Clip.amr");assert_eq!(saved["parent"],"audio");let resolved=crate::content::resolve(root,saved["id"].as_str().unwrap()).unwrap();assert_eq!(fs::read(resolved["path"].as_str().unwrap()).unwrap(),b"selected audio");fs::write(root.join("content-import.tmp"),b"keep original staged file").unwrap();assert!(dispatch(root,&json!({"action":"message_import","name":"Secret","mime":"application/octet-stream"})).is_err());assert!(root.join("content-import.tmp").exists());}
 #[test] fn files_guest_manages_folders_and_the_same_live_notes(){
  let d=tempfile::tempdir().unwrap();let root=d.path();install(root,fixture(),&["storage.private"]).unwrap();install(root,include_bytes!("../../sdk/packages/Files.onklav-a").to_vec(),&["files.manage","files.external","files.export"]).unwrap();
  let notes=launch(root,"app.onklav.notes");let nt=notes["session"].as_str().unwrap();let saved=event(root,nt,"note.text","One real note").unwrap();let id=saved["frame"]["state"]["note"]["id"].as_str().unwrap();
  let files=launch(root,"app.onklav.files");let ft=files["session"].as_str().unwrap();assert_eq!(files["frame"]["state"]["items"].as_array().unwrap().len(),8);
  event(root,ft,"entry:documents","").unwrap();event(root,ft,"entry:notes","").unwrap();let options=event(root,ft,&format!("options:{id}"),"").unwrap();assert_eq!(options["frame"]["menu_open"],true);
  event(root,ft,"trash","").unwrap();event(root,ft,"confirm.accept","").unwrap();assert!(crate::content::note_list(root).unwrap().is_empty());assert!(event(root,nt,"note.text","Must not resurrect trash").unwrap()["frame"]["state"]["status"].as_str().unwrap().contains("Trash"));
  event(root,ft,"home","").unwrap();event(root,ft,"entry:trash","").unwrap();event(root,ft,&format!("options:{id}"),"").unwrap();event(root,ft,"restore","").unwrap();assert_eq!(crate::content::note_read(root,id).unwrap().text,"One real note");
 }
 #[test]fn files_trailing_create_copy_paste_and_compress_use_the_live_broker(){let d=tempfile::tempdir().unwrap();let r=d.path();install(r,include_bytes!("../../sdk/packages/Files.onklav-a").to_vec(),&["files.manage","files.external","files.export"]).unwrap();let app=launch(r,"app.onklav.files");let t=app["session"].as_str().unwrap();event(r,t,"entry:documents","").unwrap();event(r,t,"new.folder","").unwrap();event(r,t,"name","Trip").unwrap();event(r,t,"name.save","").unwrap();let listed=event(r,t,"refresh","").unwrap();let id=listed["frame"]["state"]["items"].as_array().unwrap().iter().find(|r|r["name"]=="Trip").unwrap()["id"].as_str().unwrap().to_string();event(r,t,&format!("options:{id}"),"").unwrap();event(r,t,"copy","").unwrap();let pasted=event(r,t,"paste.here","").unwrap();assert_eq!(pasted["frame"]["state"]["total"],3);event(r,t,&format!("options:{id}"),"").unwrap();let zipped=event(r,t,"compress","").unwrap();assert!(zipped["frame"]["state"]["items"].as_array().unwrap().iter().any(|r|r["name"]=="Trip.zip"));event(r,t,&format!("options:{id}"),"").unwrap();let share=event(r,t,"share","").unwrap();assert_eq!(share["pending"]["service"],"library.share");let native=dispatch(r,&json!({"action":"library_host","session":t,"token":share["pending"]["token"],"operation":"resolve"})).unwrap();assert_eq!(native["mime"],"application/zip");}
 #[test]fn signed_podcast_download_buttons_have_scoped_services_and_live_storage_permission(){let d=tempfile::tempdir().unwrap();let r=d.path();install(r,include_bytes!("../../sdk/packages/Podcasts.onklav-a").to_vec(),&["podcasts.library","audio.playback","storage.private"]).unwrap();let app=launch(r,"app.onklav.podcasts");let t=app["session"].as_str().unwrap();assert_eq!(app["pending"]["service"],"podcasts.downloads");let reply=|pending:&Value,value:Value|dispatch(r,&json!({"action":"reply","session":t,"token":pending["pending"]["token"],"reply":{"ok":true,"value":value}})).unwrap();let found=reply(&app,json!({"downloads":[]}));assert_eq!(found["pending"]["service"],"podcasts.search");let found=reply(&found,json!({"items":[{"title":"Show","feed":"https://publisher.example/rss"}]}));let action=found["frame"]["body"].as_array().unwrap().iter().find(|n|n["kind"]=="record").unwrap()["id"].as_str().unwrap();let feed=event(r,t,action,"").unwrap();let id="a".repeat(64);let loaded=reply(&feed,json!({"title":"Show","items":[{"title":"Large episode","id":id}]}));let save=event(r,t,&format!("pod.save.{id}"),"").unwrap();assert_eq!(save["pending"]["service"],"podcasts.download");let authorized=dispatch(r,&json!({"action":"authorize","session":t,"token":save["pending"]["token"]})).unwrap();assert_eq!(authorized["owner"]["id"],"app.onklav.podcasts");let done=reply(&save,json!({"downloads":[id]}));assert!(done["frame"]["body"].as_array().unwrap().iter().any(|n|n["secondary_trailing"]["icon"]=="delete"));let remove=event(r,t,&format!("pod.save.{id}"),"").unwrap();assert_eq!(remove["pending"]["service"],"podcasts.delete");dispatch(r,&json!({"action":"permissions","id":"app.onklav.podcasts","grants":["podcasts.library","audio.playback"]})).unwrap();assert!(dispatch(r,&json!({"action":"authorize","session":t,"token":remove["pending"]["token"]})).is_err());assert!(loaded["frame"]["state"]["library"].as_array().unwrap().is_empty());}
 #[test] fn system_files_show_real_public_packages_without_exposing_the_registry(){let d=tempfile::tempdir().unwrap();let root=d.path();install(root,fixture(),&["storage.private"]).unwrap();let rows=crate::content::service(root,"library.list",&json!({"folder":"system-apps"})).unwrap();let entry=&rows["items"][0];assert_eq!(entry["name"],"Notes.onklav-a");let id=entry["id"].as_str().unwrap();let info=crate::content::service(root,"library.info",&json!({"id":id})).unwrap();assert!(info["preview"].as_str().unwrap().contains("app.onklav.notes"));let exported=crate::content::resolve(root,id).unwrap();assert_eq!(fs::read(exported["path"].as_str().unwrap()).unwrap(),fixture());assert!(crate::content::service(root,"library.trash",&json!({"id":id})).is_err());assert!(crate::content::resolve(root,"registry.json").is_err());}
 #[test] fn files_android_exports_require_the_separate_live_export_permission(){let d=tempfile::tempdir().unwrap();let root=d.path();install(root,include_bytes!("../../sdk/packages/Files.onklav-a").to_vec(),&["files.manage","files.external"]).unwrap();let saved=crate::content::store_text(root,"test.app","Example","a.txt","body").unwrap();let app=launch(root,"app.onklav.files");let t=app["session"].as_str().unwrap();event(root,t,"entry:documents","").unwrap();event(root,t,&format!("entry:{}",saved["parent"].as_str().unwrap()),"").unwrap();event(root,t,&format!("options:{}",saved["id"].as_str().unwrap()),"").unwrap();let denied=event(root,t,"export","").unwrap();assert!(denied["pending"].is_null());assert!(denied["frame"]["state"]["status"].as_str().unwrap().contains("export access is off"));dispatch(root,&json!({"action":"permissions","id":"app.onklav.files","grants":["files.manage","files.external","files.export"]})).unwrap();let reopened=launch(root,"app.onklav.files");let t=reopened["session"].as_str().unwrap();event(root,t,"entry:documents","").unwrap();event(root,t,&format!("entry:{}",saved["parent"].as_str().unwrap()),"").unwrap();event(root,t,&format!("options:{}",saved["id"].as_str().unwrap()),"").unwrap();let pending=event(root,t,"export","").unwrap();assert_eq!(pending["pending"]["service"],"library.export");dispatch(root,&json!({"action":"permissions","id":"app.onklav.files","grants":["files.manage","files.external"]})).unwrap();assert!(dispatch(root,&json!({"action":"authorize","session":t,"token":pending["pending"]["token"]})).is_err());assert!(dispatch(root,&json!({"action":"library_host","session":t,"token":pending["pending"]["token"],"operation":"resolve"})).is_err());}
 #[test] fn external_file_mutations_can_refresh_without_automatically_authorizing_new_writes(){let d=tempfile::tempdir().unwrap();let root=d.path();install(root,include_bytes!("../../sdk/packages/Files.onklav-a").to_vec(),&["files.manage","files.external"]).unwrap();let app=launch(root,"app.onklav.files");let t=app["session"].as_str().unwrap();let reply=|pending:&Value,value:Value|json!({"action":"reply","session":t,"token":pending["pending"]["token"],"reply":{"ok":true,"value":value}});let list=event(root,t,"entry:external","").unwrap();let id="a".repeat(32);let listing=json!({"folder":"external","parent":"","title":"External Files","external":true,"items":[{"id":id,"name":"External.txt","folder":false,"protected":false,"mime":"text/plain"}],"total":1,"offset":0});dispatch(root,&reply(&list,listing)).unwrap();event(root,t,&format!("options:{id}"),"").unwrap();event(root,t,"rename","").unwrap();event(root,t,"name","Renamed.txt").unwrap();let rename=event(root,t,"name.save","").unwrap();let next=dispatch(root,&reply(&rename,json!({"done":true}))).unwrap();assert_eq!(next["pending"]["service"],"library.external");assert_eq!(next["pending"]["args"]["operation"],"list");assert!(dispatch(root,&json!({"action":"library_host","session":t,"token":next["pending"]["token"],"operation":"import_external","name":"bad.txt","mime":"text/plain"})).is_err());}
 #[test] fn files_permission_and_pending_tokens_cannot_be_bypassed(){
  let d=tempfile::tempdir().unwrap();let root=d.path();let package=include_bytes!("../../sdk/packages/Files.onklav-a").to_vec();install(root,package,&["files.manage","files.external"]).unwrap();let app=launch(root,"app.onklav.files");let t=app["session"].as_str().unwrap();event(root,t,"entry:downloads","").unwrap();let p=event(root,t,"import","").unwrap();
  assert!(dispatch(root,&json!({"action":"library_host","session":t,"token":"wrong","operation":"import","name":"bad.txt"})).is_err());
  dispatch(root,&json!({"action":"permissions","id":"app.onklav.files","grants":[]})).unwrap();assert!(dispatch(root,&json!({"action":"authorize","session":t,"token":p["pending"]["token"]})).is_err());assert!(dispatch(root,&json!({"action":"check","session":t})).is_err());
  assert!(dispatch(root,&json!({"action":"library_host","session":t,"token":p["pending"]["token"],"operation":"import","name":"bad.txt"})).is_err());
 }
 #[test] fn exported_notes_use_the_onklav_documents_folder_without_exposing_paths(){
  let d=tempfile::tempdir().unwrap();let root=d.path();install(root,fixture(),&["storage.private","files.export"]).unwrap();let app=launch(root,"app.onklav.notes");let t=app["session"].as_str().unwrap();event(root,t,"note.title","Exported Note").unwrap();event(root,t,"note.text","Actual saved text").unwrap();event(root,t,"export.header","").unwrap();let p=event(root,t,"export","").unwrap();let file=dispatch(root,&json!({"action":"library_host","session":t,"token":p["pending"]["token"],"operation":"save_text"})).unwrap();assert!(file.get("path").is_none());let info=crate::content::service(root,"library.info",&json!({"id":file["id"]})).unwrap();assert!(info["preview"].as_str().unwrap().contains("Actual saved text"));
  assert!(dispatch(root,&json!({"action":"library_host","session":t,"token":p["pending"]["token"],"operation":"resolve"})).is_err());
 }
 #[test] fn browser_download_commit_is_scoped_to_a_live_authorized_web_component(){
  let d=tempfile::tempdir().unwrap();let root=d.path();install(root,include_bytes!("../../sdk/packages/Browser.onklav-a").to_vec(),&["storage.private","network.web","clipboard.read","clipboard.write","links.external","share.text"]).unwrap();let app=launch(root,"app.onklav.browser");let t=app["session"].as_str().unwrap();let w=app["frame"]["body"].as_array().unwrap().iter().find(|n|n["kind"]=="web").unwrap()["id"].as_str().unwrap();fs::write(root.join("content-import.tmp"),b"download bytes").unwrap();
  assert!(dispatch(root,&json!({"action":"web_file_save","session":t,"widget":"fake","name":"bad.txt","mime":"text/plain"})).is_err());let saved=dispatch(root,&json!({"action":"web_file_save","session":t,"widget":w,"name":"download.txt","mime":"text/plain"})).unwrap();assert_eq!(crate::content::service(root,"library.list",&json!({"folder":saved["parent"]})).unwrap()["items"][0]["name"],"download.txt");
  assert_eq!(saved["parent"],"downloads");
  for _ in 0..2 {
   fs::write(root.join("content-import.tmp"),b"media bytes").unwrap();
   let media=dispatch(root,&json!({"action":"web_file_save","session":t,"widget":w,"name":"clip.mp4","mime":"video/mp4"})).unwrap();
   assert_eq!(media["parent"],"downloads");
   assert_eq!(crate::content::resolve(root,media["id"].as_str().unwrap()).unwrap()["mime"],"video/mp4");
  }
  let items=crate::content::service(root,"library.list",&json!({"folder":"downloads"})).unwrap();
  let media=items["items"].as_array().unwrap().iter().filter(|v|v["mime"]=="video/mp4").collect::<Vec<_>>();
  assert_eq!(media.len(),2);
  assert_ne!(media[0]["name"],media[1]["name"]);
  dispatch(root,&json!({"action":"permissions","id":"app.onklav.browser","grants":[]})).unwrap();assert!(dispatch(root,&json!({"action":"web_file_save","session":t,"widget":w,"name":"bad.txt","mime":"text/plain"})).is_err());
 }
 #[test] fn actual_notes_guest_create_reload_search_delete_and_scoped_storage(){let d=tempfile::tempdir().unwrap();let root=d.path();install(root,fixture(),&["storage.private"]).unwrap();let first=launch(root,"app.onklav.notes");let token=first["session"].as_str().unwrap();event(root,token,"note.title","A note").unwrap();let text="Hello 🐈 from Wasm";let saved=event(root,token,"note.text",text).unwrap();assert_eq!(saved["frame"]["state"]["note"]["text"],text);let id=saved["frame"]["state"]["note"]["id"].as_str().unwrap();assert!(crate::location(&crate::content::notes_root(root).unwrap(),id).unwrap().is_file());assert!(!root.join("notes").exists());dispatch(root,&json!({"action":"close","session":token})).unwrap();let opened=launch(root,"app.onklav.notes");assert_eq!(opened["frame"]["state"]["note"]["text"],text);let token=opened["session"].as_str().unwrap();event(root,token,"drawer","").unwrap();let search=event(root,token,"library.query","Wasm").unwrap();assert_eq!(search["frame"]["state"]["records"].as_array().unwrap().len(),1);event(root,token,&format!("delete:{id}"),"").unwrap();event(root,token,"delete.cancel","").unwrap();assert!(crate::content::note_read(root,id).is_ok());event(root,token,&format!("delete:{id}"),"").unwrap();event(root,token,"delete.confirm","").unwrap();assert!(crate::content::note_read(root,id).is_err());}
 #[test] fn separate_programmable_package_runs_without_host_rebuild(){let d=tempfile::tempdir().unwrap();install(d.path(),include_bytes!("../../sdk/packages/Counter.onklav-a").to_vec(),&[]).unwrap();let app=launch(d.path(),"developer.example.counter");let s=app["session"].as_str().unwrap();assert_eq!(event(d.path(),s,"add","").unwrap()["frame"]["state"]["count"],1);assert_eq!(event(d.path(),s,"subtract","").unwrap()["frame"]["state"]["count"],0);}
 #[test] fn denied_private_storage_never_creates_app_documents(){let d=tempfile::tempdir().unwrap();install(d.path(),fixture(),&[]).unwrap();let a=launch(d.path(),"app.onklav.notes");assert!(a["frame"]["state"]["status"].as_str().unwrap().contains("access is off"));assert!(!d.path().join("app-sdk/data/app.onklav.notes").exists());}
 #[test] fn revocation_and_uninstall_invalidate_pending_services_and_keep_publisher_pin(){let d=tempfile::tempdir().unwrap();install(d.path(),fixture(),&["storage.private","clipboard.write"]).unwrap();let a=launch(d.path(),"app.onklav.notes");let s=a["session"].as_str().unwrap();event(d.path(),s,"note.text","Secret").unwrap();let pending=event(d.path(),s,"copy","").unwrap();assert_eq!(pending["pending"]["service"],"clipboard.write");dispatch(d.path(),&json!({"action":"permissions","id":"app.onklav.notes","grants":[]})).unwrap();assert!(dispatch(d.path(),&json!({"action":"authorize","session":s,"token":pending["pending"]["token"]})).is_err());dispatch(d.path(),&json!({"action":"remove","id":"app.onklav.notes","delete_data":false})).unwrap();assert!(dispatch(d.path(),&json!({"action":"launch","id":"app.onklav.notes"})).is_err());let p=package(fixture()).unwrap();assert!(install(d.path(),repack(&p.wasm,"app.onklav.notes",2,7,&[]),&[]).is_err());install(d.path(),fixture(),&["storage.private"]).unwrap();assert_eq!(launch(d.path(),"app.onklav.notes")["frame"]["state"]["note"]["text"],"Secret");}
 #[test] fn same_code_in_other_package_cannot_read_first_app_data(){let d=tempfile::tempdir().unwrap();install(d.path(),fixture(),&["storage.private"]).unwrap();let a=launch(d.path(),"app.onklav.notes");event(d.path(),a["session"].as_str().unwrap(),"note.text","Private").unwrap();let wasm=package(fixture()).unwrap().wasm;install(d.path(),repack(&wasm,"other.example.notes",1,7,&["storage.private"]),&["storage.private"]).unwrap();assert_eq!(launch(d.path(),"other.example.notes")["frame"]["state"]["note"]["text"],"");}
 #[test] fn tamper_publisher_downgrade_and_stale_review_leave_installed_version_intact(){let d=tempfile::tempdir().unwrap();let p=package(fixture()).unwrap();install(d.path(),fixture(),&["storage.private"]).unwrap();assert!(install(d.path(),repack(&p.wasm,"app.onklav.notes",2,7,&["storage.private"]),&["storage.private"]).is_err());let mut altered=fixture();altered[0]^=255;assert!(install(d.path(),altered,&[]).is_err());let upgraded=repack(&p.wasm,"test.example.app",2,7,&["storage.private"]);install(d.path(),upgraded,&["storage.private"]).unwrap();assert!(install(d.path(),repack(&p.wasm,"test.example.app",1,7,&[]),&[]).is_err());fs::write(d.path().join("app-install.zip"),repack(&p.wasm,"fresh.example.app",1,7,&[])).unwrap();let review=dispatch(d.path(),&json!({"action":"inspect"})).unwrap();fs::write(d.path().join("app-install.zip"),repack(&p.wasm,"different.example.app",1,7,&[])).unwrap();assert!(dispatch(d.path(),&json!({"action":"install","hash":review["hash"],"grants":[]})).is_err());assert_eq!(get(&load(d.path()).unwrap(),"test.example.app").unwrap().manifest.version,2);}
 #[test] fn imports_memory_growth_cpu_abuse_and_invalid_pointers_trap(){let imports=wat::parse_str("(module (import \"wasi_snapshot_preview1\" \"fd_write\" (func)))").unwrap();assert!(compile(&imports).is_err());for wat in ["(module (memory (export \"memory\") 129) (func (export \"onklav_alloc\") (param i32)(result i32) i32.const 0) (func (export \"onklav_step\") (param i32 i32)(result i64) i64.const 0))", "(module (memory (export \"memory\") 1) (func (export \"onklav_alloc\") (param i32)(result i32) i32.const 0) (func (export \"onklav_step\") (param i32 i32)(result i64) (loop $spin (br $spin)) i64.const 0))", "(module (memory (export \"memory\") 1) (func (export \"onklav_alloc\") (param i32)(result i32) i32.const 0) (func (export \"onklav_step\") (param i32 i32)(result i64) i64.const 42949672960000001))"]{let bytes=wat::parse_str(wat).unwrap();assert!(invoke(&compile(&bytes).unwrap(),&json!({})).is_err());}}
 #[test] fn app_ui_limits_and_unknown_capabilities_fail_closed(){assert!(validate_frame(&json!({"title":"Bad","state":{},"actions":[],"body":[{"kind":"webview","id":"escape"}],"drawer":[],"menu":[]})).is_err());let d=tempfile::tempdir().unwrap();let p=package(fixture()).unwrap();assert!(install(d.path(),repack(&p.wasm,"../escape",1,7,&[]),&[]).is_err());assert!(install(d.path(),repack(&p.wasm,"test.example.app",1,7,&["android.context"]),&[]).is_err());assert!(install(d.path(),repack(&p.wasm,"test.example.app",1,7,&[]),&["storage.private"]).is_err());}
 #[test] fn notes_preserve_utf16_limits_and_full_length_unicode(){let d=tempfile::tempdir().unwrap();install(d.path(),fixture(),&["storage.private"]).unwrap();let a=launch(d.path(),"app.onklav.notes");let s=a["session"].as_str().unwrap();let text="界".repeat(100000);let result=event(d.path(),s,"note.text",&text).unwrap();assert_eq!(result["frame"]["state"]["note"]["text"],text);assert!(event(d.path(),s,"note.text",&"x".repeat(100001)).is_err());}
 #[test] fn valid_update_keeps_data_invalidates_sessions_and_binds_host_root(){let d=tempfile::tempdir().unwrap();let other=tempfile::tempdir().unwrap();let wasm=package(fixture()).unwrap().wasm;let v1=repack(&wasm,"test.example.app",1,7,&["storage.private"]);install(d.path(),v1.clone(),&["storage.private"]).unwrap();install(other.path(),v1,&["storage.private"]).unwrap();let a=launch(d.path(),"test.example.app");let token=a["session"].as_str().unwrap();event(d.path(),token,"note.text","Keep through update").unwrap();assert!(event(other.path(),token,"note.text","Wrong host").is_err());install(d.path(),repack(&wasm,"test.example.app",2,7,&["storage.private"]),&["storage.private"]).unwrap();assert!(event(d.path(),token,"note.text","Stale").is_err());assert_eq!(launch(d.path(),"test.example.app")["frame"]["state"]["note"]["text"],"Keep through update");}

 #[test] fn own_notes_migration_keeps_originals_and_does_not_restore_deleted_app_data(){let d=tempfile::tempdir().unwrap();let old=Note{id:"a".repeat(32),title:"Existing OnKlav note".into(),text:"Keep original".into(),updated:1};crate::save_note(d.path(),&old).unwrap();install(d.path(),fixture(),&["storage.private"]).unwrap();let publisher=package(fixture()).unwrap().manifest.publisher;dispatch(d.path(),&json!({"action":"adopt_own_notes","publisher":publisher,"last":old.id})).unwrap();assert!(crate::read_note(d.path(),&old.id).is_ok());assert_eq!(launch(d.path(),"app.onklav.notes")["frame"]["state"]["note"]["text"],"Keep original");dispatch(d.path(),&json!({"action":"remove","id":"app.onklav.notes","delete_data":true})).unwrap();install(d.path(),fixture(),&["storage.private"]).unwrap();dispatch(d.path(),&json!({"action":"adopt_own_notes","publisher":publisher})).unwrap();assert_eq!(launch(d.path(),"app.onklav.notes")["frame"]["state"]["note"]["text"],"");assert!(crate::read_note(d.path(),&old.id).is_ok());}
 #[test] fn native_text_controls_cannot_bypass_clipboard_grants(){let d=tempfile::tempdir().unwrap();install(d.path(),fixture(),&["storage.private"]).unwrap();let app=launch(d.path(),"app.onklav.notes");let token=app["session"].as_str().unwrap();assert!(dispatch(d.path(),&json!({"action":"host_clipboard","session":token,"cap":"clipboard.read"})).is_err());dispatch(d.path(),&json!({"action":"permissions","id":"app.onklav.notes","grants":["clipboard.read","storage.private"]})).unwrap();let app=launch(d.path(),"app.onklav.notes");assert!(dispatch(d.path(),&json!({"action":"host_clipboard","session":app["session"],"cap":"clipboard.read"})).is_ok());assert!(dispatch(d.path(),&json!({"action":"host_clipboard","session":app["session"],"cap":"clipboard.write"})).is_err());}

 #[test] fn settings_use_private_walkie_preferences_without_granting_communications(){
  let d=tempfile::tempdir().unwrap();install(d.path(),include_bytes!("../../sdk/packages/WalkieTalkie.onklav-a").to_vec(),&["storage.private"]).unwrap();
  let p=json!({"auto_play":false,"auto_send":false,"beeps":true});
  dispatch(d.path(),&json!({"action":"walkie_preferences","args":p})).unwrap();
  let state=dispatch(d.path(),&json!({"action":"walkie_preferences"})).unwrap();assert_eq!(state["preferences"],p);
  assert!(dispatch(d.path(),&json!({"action":"walkie_carrier","args":{"operation":"begin"}})).is_err());
  dispatch(d.path(),&json!({"action":"permissions","id":"app.onklav.walkie","grants":[]})).unwrap();
  assert!(dispatch(d.path(),&json!({"action":"walkie_preferences","args":p})).is_err());
 }

 #[test] fn message_video_opens_real_signed_controller_with_live_grants_and_canonical_source(){
  let d=tempfile::tempdir().unwrap();let root=d.path();let bytes=include_bytes!("../../sdk/packages/YouTube.onklav-a").to_vec();
  install(root,bytes,&["storage.private","network.web","clipboard.read","clipboard.write"]).unwrap();
  let app=launch(root,"app.onklav.youtube");let t=&app["session"];
  let out=dispatch(root,&json!({"action":"video_open","session":t,"video":"AbC_def-123","start":83})).unwrap();
  assert_eq!(out["frame"]["body"][0]["url"],"https://www.youtube.com/watch?v=AbC_def-123&t=83");assert_eq!(out["frame"]["body"][0]["epoch"],1);assert_eq!(out["frame"]["state"]["meta"]["history"],0);
  assert!(dispatch(root,&json!({"action":"event","session":t,"event":{"action":"host.video.open","url":"https://evil.test"}})).is_err());
  assert!(dispatch(root,&json!({"action":"video_open","session":t,"video":"../bad/path","start":0})).is_err());
  assert!(dispatch(root,&json!({"action":"video_open","session":t,"video":"AbC_def-123","start":86401})).is_err());
  dispatch(root,&json!({"action":"permissions","id":"app.onklav.youtube","grants":["storage.private"]})).unwrap();
  assert!(dispatch(root,&json!({"action":"video_open","session":t,"video":"AbC_def-123","start":0})).is_err());
 }

}

#[cfg(test)] mod clock_tests {
 use super::*;
 #[test] fn clock_contract_rejects_inputs_and_unexpected_reply_fields(){
  assert!(clock_args(&json!({})).is_ok());
  for args in [json!(null),json!([]),json!({"timezone":"UTC"}),json!({"epoch_ms":0})]{assert!(clock_args(&args).is_err());}
  let valid=json!({"epoch_ms":1792886400000i64,"monotonic_ms":12,"timezone":"America/Chicago","offset_seconds":-18000,"local_date":"2026-10-24","local_time":"19:00","locale":"en-US","first_weekday":7,"hour_cycle":"h12","daylight_saving":true});
  assert!(clock_reply(&valid).is_ok());
  for (field,value) in [("first_weekday",json!(0)),("offset_seconds",json!(64801)),("hour_cycle",json!("system")),("timezone",json!("bad\nzone")),("epoch_ms",json!("0")),("monotonic_ms",json!(-1)),("path",json!("/private"))]{let mut bad=valid.clone();bad[field]=value;assert!(clock_reply(&bad).is_err(),"{field}");}
 }
}

#[cfg(test)]mod reminder_contract_tests{
 use super::*;
 #[test]fn absolute_reminder_and_queries_are_strict(){assert!(device_args("notifications.schedule_at",&json!({"key":"one","title":"Reminder","text":"Body","at_ms":1792886400000u64})).is_ok());assert!(device_args("notifications.pending",&json!({})).is_ok());assert!(device_args("notifications.status",&json!({"key":"one"})).is_ok());for a in [json!({"key":"one","title":"T","text":"B","at_ms":-1}),json!({"key":"one","title":"T","text":"B","at_ms":"1000"}),json!({"key":"one","title":"T","text":"B","at_ms":1000,"delay_seconds":60})]{assert!(device_args("notifications.schedule_at",&a).is_err());}assert!(device_args("notifications.pending",&json!({"owner":"other"})).is_err());}
 #[test]fn reminder_replies_do_not_expose_content_or_imply_delivery(){let pending=json!({"pending":false,"due_ms":null,"overdue":false,"registered":false,"exact":false,"notifications_enabled":true});assert!(notification_query_reply("notifications.status",&pending).is_ok());let mut bad=pending.clone();bad["delivered"]=json!(true);assert!(notification_query_reply("notifications.status",&bad).is_err());let mut bad=pending;bad["registered"]=json!(true);assert!(notification_query_reply("notifications.status",&bad).is_err());assert!(notification_query_reply("notifications.schedule_at",&json!({"scheduled":true,"due_ms":1000,"exact":true})).is_err());}
}

#[cfg(test)]mod utility_app_tests{
 use super::*;
 fn install(root:&Path,bytes:Vec<u8>,grants:&[&str])->Result<Value>{fs::write(root.join("app-install.zip"),bytes).unwrap();let review=dispatch(root,&json!({"action":"inspect"}))?;dispatch(root,&json!({"action":"install","hash":review["hash"],"grants":grants}))}
 fn event(root:&Path,session:&Value,action:&str,value:&str)->Value{dispatch(root,&json!({"action":"event","session":session,"event":{"action":action,"value":value}})).unwrap()}
 #[test]fn signed_calc_history_copy_and_convert_reopening(){
  let d=tempfile::tempdir().unwrap();let root=d.path();
  for (bytes,caps)in[(include_bytes!("../../sdk/packages/Calc.onklav-a").as_slice(),vec!["storage.private","clipboard.write"]),(include_bytes!("../../sdk/packages/Convert.onklav-a").as_slice(),vec!["storage.private","clipboard.write"]) ]{install(root,bytes.to_vec(),&caps).unwrap();}
  let app=dispatch(root,&json!({"action":"launch","id":"app.onklav.calc"})).unwrap();let s=&app["session"];
  for a in["calc.key.2","calc.key.+","calc.key.3"]{event(root,s,a,"");}let answer=event(root,s,"calc.equals","");assert_eq!(answer["frame"]["state"]["result"],"5");assert!(answer["pending"].is_null());let copy=event(root,s,"calc.copy","");assert_eq!(copy["pending"]["service"],"clipboard.write");
  dispatch(root,&json!({"action":"close","session":s})).unwrap();let again=dispatch(root,&json!({"action":"launch","id":"app.onklav.calc"})).unwrap();assert_eq!(again["frame"]["state"]["history"][0]["result"],"5");
  let app=dispatch(root,&json!({"action":"launch","id":"app.onklav.convert"})).unwrap();let s=&app["session"];let v=event(root,s,"convert.left","12");assert_eq!(v["frame"]["state"]["right"],"3.6576");event(root,s,"convert.category","temperature");event(root,s,"convert.from","f");event(root,s,"convert.to","c");let v=event(root,s,"convert.left","212");assert_eq!(v["frame"]["state"]["right"],"100");let v=event(root,s,"convert.right","0");assert_eq!(v["frame"]["state"]["left"],"32");
 }
 #[test]fn signed_weather_offline_search_stays_bounded_and_uses_declared_provider(){
  let d=tempfile::tempdir().unwrap();let root=d.path();install(root,include_bytes!("../../sdk/packages/Weather.onklav-a").to_vec(),&["storage.private","network.http","links.external"]).unwrap();let app=dispatch(root,&json!({"action":"launch","id":"app.onklav.weather"})).unwrap();let s=&app["session"];
  if !app["pending"].is_null(){dispatch(root,&json!({"action":"reply","session":s,"token":app["pending"]["token"],"reply":{"ok":false,"error":"fixture clock unavailable"}})).unwrap();}
  event(root,s,"weather.query","Chicago");let f=event(root,s,"weather.search","");assert!(f["pending"].is_null());assert!(f["frame"]["state"]["results"].as_array().unwrap().iter().any(|p|p["zone"]=="America/Chicago"));let id=f["frame"]["state"]["results"][0]["id"].as_str().unwrap();let f=event(root,s,&format!("weather.pick.{id}"),"");assert_eq!(f["pending"]["service"],"clock.read");
 }
 #[test] fn youtube_download_saves_only_granted_media_destinations(){let d=tempfile::tempdir().unwrap();let root=d.path();install(root,include_bytes!("../../sdk/packages/YouTube.onklav-a").to_vec(),&["storage.private","network.web","network.thumbnails","clipboard.read","clipboard.write","links.external","files.manage"]).unwrap();let a=dispatch(root,&json!({"action":"launch","id":"app.onklav.youtube"})).unwrap();let t=&a["session"];fs::write(root.join("content-import.tmp"),b"test media").unwrap();let save=|folder:&str,mime:&str|dispatch(root,&json!({"action":"web_file_save","session":t,"widget":"youtube.page","folder":folder,"mime":mime,"name":"Clip.mp4"}));assert!(save("documents","video/mp4").is_err());assert!(save("video","text/plain").is_err());let saved=save("video","video/mp4").unwrap();let folders=crate::content::service(root,"library.list",&json!({"folder":"video"})).unwrap();assert_eq!(saved["parent"],"video");assert_eq!(saved["id"],folders["items"][0]["id"]);fs::write(root.join("content-import.tmp"),b"kept staged bytes").unwrap();dispatch(root,&json!({"action":"permissions","id":"app.onklav.youtube","grants":["storage.private","network.web","network.thumbnails","clipboard.read","clipboard.write","links.external"]})).unwrap();assert!(save("audio","audio/mp4").is_err());assert!(root.join("content-import.tmp").exists());}

 #[test]fn camera_shared_capture_is_owned_scoped_revocable_and_trashable(){
  let d=tempfile::tempdir().unwrap();let root=d.path();let caps=["storage.private","camera.capture","audio.record","audio.playback","files.manage","files.export"];
  install(root,include_bytes!("../../sdk/packages/Camera.onklav-a").to_vec(),&caps).unwrap();let a=dispatch(root,&json!({"action":"launch","id":"app.onklav.camera"})).unwrap();let session=&a["session"];
  let call=|op:&str,id:&str|dispatch(root,&json!({"action":"native_camera","session":session,"operation":op,"id":id,"mime":"image/jpeg","name":"IMG_test.jpg"}));
  fs::write(root.join("content-import.tmp"),b"fixture photo").unwrap();let saved=call("save","").unwrap();let id=saved["id"].as_str().unwrap();assert_eq!(call("list","").unwrap()["total"],1);let path=call("resolve",id).unwrap();assert_eq!(fs::read(path["path"].as_str().unwrap()).unwrap(),b"fixture photo");
  fs::write(root.join("content-import.tmp"),b"unrelated media").unwrap();let foreign=dispatch(root,&json!({"action":"message_import","mime":"image/jpeg","name":"Message.jpg"})).unwrap();assert!(call("resolve",foreign["id"].as_str().unwrap()).is_err());assert!(call("trash",foreign["id"].as_str().unwrap()).is_err());
  call("trash",id).unwrap();assert_eq!(call("list","").unwrap()["total"],0);assert!(call("resolve",id).is_err());
  dispatch(root,&json!({"action":"permissions","id":"app.onklav.camera","grants":["storage.private","camera.capture"]})).unwrap();fs::write(root.join("content-import.tmp"),b"keep unsaved capture").unwrap();assert!(call("save","").is_err());assert!(root.join("content-import.tmp").exists());
 }
 #[test]fn essentials_are_signed_apps_and_camera_operations_reject_other_sessions(){
  let d=tempfile::tempdir().unwrap();let root=d.path();for (pkg,id) in [(include_bytes!("../../sdk/packages/Clock.onklav-a").as_slice(),"app.onklav.clock"),(include_bytes!("../../sdk/packages/Flashlight.onklav-a").as_slice(),"app.onklav.flashlight")]{let p=package(pkg.to_vec()).unwrap();let caps=p.manifest.capabilities.iter().map(String::as_str).collect::<Vec<_>>();install(root,pkg.to_vec(),&caps).unwrap();let a=dispatch(root,&json!({"action":"launch","id":id})).unwrap();assert!(dispatch(root,&json!({"action":"native_camera","session":a["session"],"operation":"list"})).is_err());}
 }

}
