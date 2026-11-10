//! Scoped image documents and validated image-space editing. No viewport enters exports.
use crate::Result;
use serde::{Deserialize,Serialize};
use serde_json::{json,Value};
use std::{fs,path::{Path,PathBuf}};
const MAX_DOC:u64=4*1024*1024;
const HISTORY:usize=2*1024*1024;
#[derive(Clone,Serialize,Deserialize,Debug)]
#[serde(deny_unknown_fields)]
pub struct Mark {pub tool:String,pub color:u32,pub width:f32,pub x:f32,pub y:f32,#[serde(default)]pub text:String,#[serde(default)]pub points:Vec<[f32;2]>,#[serde(default)]pub box_width:f32,#[serde(default)]pub box_height:f32}
#[derive(Serialize,Deserialize)]
#[serde(deny_unknown_fields)]
struct Document {id:String,title:String,width:u32,height:u32,updated:u64,revision:u64,marks:Vec<Mark>,undo:Vec<Vec<Mark>>,redo:Vec<Vec<Mark>>}
fn stamp()->u64{std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap_or_default().as_millis() as u64}
fn path(root:&Path,id:&str,suffix:&str)->Result<PathBuf>{if !crate::valid_id(id){return Err("Invalid Image Identity".into())}Ok(root.join(format!("drawing-{id}.{suffix}")))}
fn finite(n:f32,lo:f32,hi:f32)->bool{n.is_finite()&&n>=lo&&n<=hi}
fn bounds(m:&Mark)->[f32;4]{if m.tool=="text"{return [m.x,m.y,m.x+m.box_width,m.y+m.box_height]}let mut b=[f32::MAX,f32::MAX,f32::MIN,f32::MIN];for p in &m.points{b[0]=b[0].min(p[0]+m.x);b[1]=b[1].min(p[1]+m.y);b[2]=b[2].max(p[0]+m.x);b[3]=b[3].max(p[1]+m.y);}b}
fn valid_marks(marks:&[Mark],w:u32,h:u32)->bool{
 if marks.len()>300||marks.iter().map(|m|m.points.len()).sum::<usize>()>30000{return false}
 marks.iter().all(|m|["pen","highlight","text"].contains(&m.tool.as_str())&&finite(m.width,1.,800.)&&finite(m.x,-4000.,4000.)&&finite(m.y,-4000.,4000.)&&m.text.encode_utf16().count()<=400&&m.text==crate::plain(&m.text,400)&&m.points.iter().all(|p|finite(p[0],0.,w as f32)&&finite(p[1],0.,h as f32))&&if m.tool=="text"{!m.text.trim().is_empty()&&m.points.is_empty()&&finite(m.box_width,1.,w as f32)&&finite(m.box_height,1.,h as f32)&&m.x>=0.&&m.y>=0.&&m.x+m.box_width<=w as f32+0.01&&m.y+m.box_height<=h as f32+0.01}else{!m.points.is_empty()&&m.points.len()<=2000&&m.text.is_empty()&&{let b=bounds(m);b[0]>=-0.01&&b[1]>=-0.01&&b[2]<=w as f32+0.01&&b[3]<=h as f32+0.01}})
}
fn history_bytes(d:&Document)->usize{d.undo.iter().chain(&d.redo).map(|m|serde_json::to_vec(m).map(|b|b.len()).unwrap_or(HISTORY+1)).sum()}
fn trim(d:&mut Document){while d.undo.len()>10{d.undo.remove(0);}while d.redo.len()>10{d.redo.remove(0);}while history_bytes(d)>HISTORY{if !d.undo.is_empty(){d.undo.remove(0);}else if !d.redo.is_empty(){d.redo.remove(0);}else{break}}}
fn load(root:&Path,id:&str)->Result<Document>{let d:Document=serde_json::from_slice(&crate::sdk::read_bounded(&path(root,id,"json")?,MAX_DOC)?).map_err(|_|"Saved Image Is Damaged")?;if d.id!=id||d.width==0||d.height==0||d.width>1800||d.height>1800||d.title.encode_utf16().count()>80||!valid_marks(&d.marks,d.width,d.height)||d.undo.len()>10||d.redo.len()>10||history_bytes(&d)>HISTORY||!d.undo.iter().chain(&d.redo).all(|m|valid_marks(m,d.width,d.height)){return Err("Invalid Saved Image".into())}Ok(d)}
fn save(root:&Path,d:&Document)->Result<()> {let p=path(root,&d.id,"json")?;let bytes=serde_json::to_vec(d).map_err(|e|e.to_string())?;if bytes.len() as u64>MAX_DOC||crate::sdk::private_size(root,&p)?+bytes.len() as u64>16*1024*1024{return Err("Private Image Storage Is Full. Export or Remove Saved Images.".into())}crate::sdk::atomic(&p,&bytes)}
fn meta(d:&Document)->Value{json!({"id":d.id,"title":d.title,"width":d.width,"height":d.height,"updated":d.updated,"revision":d.revision,"count":d.marks.len(),"undo":!d.undo.is_empty(),"redo":!d.redo.is_empty()})}
fn last(root:&Path,id:&str)->Result<()> {crate::sdk::atomic(&root.join("drawing-last"),id.as_bytes())}
pub fn snapshot(root:&Path,id:&str,revision:u64,image:bool)->Result<Value>{let d=load(root,id)?;if d.revision!=revision{return Err("Image Changed. Try Again.".into())}let mut value=meta(&d);value["marks"]=json!(d.marks);if image{value["image"]=json!(encode(&crate::sdk::read_bounded(&path(root,id,"jpg")?,600*1024)?));}Ok(value)}
// Binary stays between the native broker and Rust, never inside Wasm state.
const B64:&[u8]=b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
pub fn encode(b:&[u8])->String{let mut out=String::with_capacity(b.len().div_ceil(3)*4);for c in b.chunks(3){let a=c[0] as u32;let n=(a<<16)|((c.get(1).copied().unwrap_or(0) as u32)<<8)|c.get(2).copied().unwrap_or(0) as u32;out.push(B64[(n>>18) as usize] as char);out.push(B64[((n>>12)&63) as usize] as char);out.push(if c.len()>1{B64[((n>>6)&63) as usize] as char}else{'='});out.push(if c.len()>2{B64[(n&63) as usize] as char}else{'='});}out}
fn decode(raw:&str)->Result<Vec<u8>>{if raw.len()>819200||raw.len()%4!=0{return Err("Image Is Too Large".into())}let mut out=Vec::new();for c in raw.as_bytes().chunks(4){let mut n=0u32;for x in c{n=(n<<6)|if *x==b'='{0}else{B64.iter().position(|b|b==x).ok_or("Invalid Image Encoding")? as u32};}out.push((n>>16) as u8);if c[2]!=b'='{out.push((n>>8) as u8)}if c[3]!=b'='{out.push(n as u8)}}if encode(&out)!=raw{return Err("Invalid Image Encoding".into())}Ok(out)}
fn dimensions(jpeg:&[u8])->Result<(u32,u32)>{if !jpeg.starts_with(&[255,216])||!jpeg.ends_with(&[255,217]){return Err("Choose a JPEG Image".into())}let mut i=2;while i+4<=jpeg.len(){if jpeg[i]!=255{return Err("Invalid JPEG".into())}let marker=jpeg[i+1];i+=2;if marker==255{i-=1;continue}if marker==218||marker==217{break}let len=u16::from_be_bytes([jpeg[i],jpeg[i+1]]) as usize;if len<2||i+len>jpeg.len(){break}if [192,193,194].contains(&marker)&&len>=8{let h=u16::from_be_bytes([jpeg[i+3],jpeg[i+4]]) as u32;let w=u16::from_be_bytes([jpeg[i+5],jpeg[i+6]]) as u32;if w>0&&h>0&&w<=1800&&h<=1800{return Ok((w,h))}break}i+=len;}Err("Invalid or Oversized JPEG Image".into())}
pub fn import(root:&Path,value:&Value)->Result<Value>{let count=fs::read_dir(root).map_err(|e|e.to_string())?.filter_map(|e|e.ok()).filter(|e|e.file_name().to_string_lossy().starts_with("drawing-")&&e.file_name().to_string_lossy().ends_with(".json")).take(2000).count();if count>=2000{return Err("Saved Image Limit Reached (2,000)".into())}let bytes=decode(value["image"].as_str().ok_or("Missing Image")?)?;let (w,h)=dimensions(&bytes)?;let id=uuid::Uuid::new_v4().simple().to_string();let d=Document{id:id.clone(),title:"Annotated Image".into(),width:w,height:h,updated:stamp(),revision:0,marks:vec![],undo:vec![],redo:vec![]};let p=path(root,&id,"jpg")?;if crate::sdk::private_size(root,&p)?+bytes.len() as u64+1024>16*1024*1024{return Err("Private Image Storage Is Full".into())}crate::sdk::atomic(&p,&bytes)?;if let Err(e)=save(root,&d){let _=fs::remove_file(p);return Err(e)}last(root,&id)?;Ok(meta(&d))}
fn expected(root:&Path,args:&Value)->Result<Document>{let d=load(root,args["id"].as_str().ok_or("Missing Image")?)?;if args["revision"].as_u64()!=Some(d.revision){return Err("Image Changed. Try Again.".into())}Ok(d)}
pub fn prepare(root:&Path,args:&Value)->Result<Value>{let d=expected(root,args)?;snapshot(root,&d.id,d.revision,true)}
pub fn service(root:&Path,op:&str,args:&Value)->Result<Value>{
 if op=="drawing.current"{return match crate::sdk::read_bounded(&root.join("drawing-last"),32){Ok(b)=>{let id=String::from_utf8(b).map_err(|_|"Invalid Image Identity")?;Ok(meta(&load(root,&id)?))},Err(_) if !root.join("drawing-last").exists()=>Ok(Value::Null),Err(e)=>Err(e)}}
 if op=="drawing.list"{let query=args["query"].as_str().unwrap_or("").to_lowercase();let mut list=vec![];for e in fs::read_dir(root).map_err(|e|e.to_string())?{let e=e.map_err(|e|e.to_string())?;let name=e.file_name().to_string_lossy().to_string();if let Some(id)=name.strip_prefix("drawing-").and_then(|n|n.strip_suffix(".json")){let d=load(root,id)?;if d.title.to_lowercase().contains(&query){list.push(meta(&d));}}}list.sort_by_key(|v|std::cmp::Reverse(v["updated"].as_u64().unwrap_or(0)));let total=list.len();let offset=args["offset"].as_u64().unwrap_or(0).min(2000) as usize;return Ok(json!({"items":list.into_iter().skip(offset).take(40).collect::<Vec<_>>(),"total":total}));}
 let id=args["id"].as_str().ok_or("Missing Image")?;
 if op=="drawing.load"{let d=load(root,id)?;last(root,id)?;return Ok(meta(&d))}
 if op=="drawing.delete"{let _=load(root,id)?;fs::remove_file(path(root,id,"json")?).map_err(|e|e.to_string())?;let _=fs::remove_file(path(root,id,"jpg")?);if fs::read(root.join("drawing-last")).ok().as_deref()==Some(id.as_bytes()){fs::remove_file(root.join("drawing-last")).map_err(|e|e.to_string())?;}return Ok(json!(true))}
 let mut d=expected(root,args)?;
 if op=="drawing.rename"{d.title=crate::plain(args["title"].as_str().ok_or("Missing Title")?,80).replace(['\n','\t']," ").trim().to_string();if d.title.is_empty(){d.title="Annotated Image".into();}}
 else if op=="drawing.undo"||op=="drawing.redo"{let (from,to)=if op=="drawing.undo"{(&mut d.undo,&mut d.redo)}else{(&mut d.redo,&mut d.undo)};if let Some(previous)=from.pop(){to.push(std::mem::replace(&mut d.marks,previous));}else{return Ok(meta(&d))}}
 else if op=="drawing.edit"||op=="drawing.text"{
  let old=d.marks.clone();let index=args["index"].as_u64().map(|n|n as usize);
  match if op=="drawing.text"{"text"}else{args["op"].as_str().unwrap_or("")} {
   "stroke"=>{let m:Mark=serde_json::from_value(args["mark"].clone()).map_err(|_|"Invalid Mark")?;if m.tool=="text"{return Err("Use Text Note".into())}d.marks.push(m);},
   "erase"=>{let i=index.filter(|i|*i<d.marks.len()).ok_or("Mark Is Gone")?;d.marks.remove(i);},
   "move"=>{let i=index.filter(|i|*i<d.marks.len()).ok_or("Mark Is Gone")?;let b=bounds(&d.marks[i]);let dx=args["dx"].as_f64().ok_or("Missing Movement")? as f32;let dy=args["dy"].as_f64().ok_or("Missing Movement")? as f32;if !dx.is_finite()||!dy.is_finite(){return Err("Invalid Movement".into())}d.marks[i].x+=dx.clamp(-b[0],d.width as f32-b[2]);d.marks[i].y+=dy.clamp(-b[1],d.height as f32-b[3]);},
   "text"=>{let mut m:Mark=serde_json::from_value(args["mark"].clone()).map_err(|_|"Invalid Text Note")?;if m.tool!="text"{return Err("Invalid Text Tool".into())}if !finite(m.box_width,1.,d.width as f32)||!finite(m.box_height,1.,d.height as f32){return Err("Shorten the Note So It Fits Inside This Picture".into())}m.x=m.x.clamp(0.,d.width as f32-m.box_width);m.y=m.y.clamp(0.,d.height as f32-m.box_height);if let Some(i)=index{if i>=d.marks.len()||d.marks[i].tool!="text"{return Err("Text Note Is Gone".into())}d.marks[i]=m;}else{d.marks.push(m);}},
   _=>return Err("Unsupported Drawing Edit".into())
  }
  if !valid_marks(&d.marks,d.width,d.height){return Err("Document Is Full or Mark Is Invalid (300 Marks / 30,000 Points)".into())}d.undo.push(old);d.redo.clear();
 }else{return Err("Unsupported Drawing Operation".into())}
 trim(&mut d);d.revision=d.revision.checked_add(1).ok_or("Image Revision Limit")?;d.updated=stamp();save(root,&d)?;last(root,&d.id)?;Ok(meta(&d))
}
#[cfg(test)]mod tests {use super::*;
 pub fn jpeg()->Vec<u8>{vec![255,216,255,192,0,8,8,0,100,0,120,0,255,217]}
 #[test]fn binary_dimensions_and_path_bounds(){let d=tempfile::tempdir().unwrap();let image=jpeg();assert_eq!(decode(&encode(&image)).unwrap(),image);assert!(decode("====").is_err());assert!(import(d.path(),&json!({"image":encode(&[0;20])})).is_err());assert!(path(d.path(),"../escape","json").is_err());}
 #[test]fn edits_history_reopen_scope_delete_and_invalid_revision(){let d=tempfile::tempdir().unwrap();let r=d.path();let doc=import(r,&json!({"image":encode(&jpeg())})).unwrap();let id=doc["id"].as_str().unwrap();let mark=json!({"tool":"pen","color":4293272130u32,"width":4,"x":0,"y":0,"points":[[1,2],[20,30]]});let e=service(r,"drawing.edit",&json!({"id":id,"revision":0,"op":"stroke","mark":mark})).unwrap();assert_eq!(e["count"],1);assert!(service(r,"drawing.edit",&json!({"id":id,"revision":0,"op":"erase","index":0})).is_err());service(r,"drawing.undo",&json!({"id":id,"revision":1})).unwrap();assert_eq!(snapshot(r,id,2,false).unwrap()["marks"].as_array().unwrap().len(),0);service(r,"drawing.redo",&json!({"id":id,"revision":2})).unwrap();service(r,"drawing.edit",&json!({"id":id,"revision":3,"op":"move","index":0,"dx":10000,"dy":-10000})).unwrap();let state=snapshot(r,id,4,false).unwrap();assert_eq!(state["marks"][0]["x"],100.);assert_eq!(state["marks"][0]["y"],-2.);let b=tempfile::tempdir().unwrap();assert!(snapshot(b.path(),id,4,true).is_err());assert_eq!(service(r,"drawing.current",&json!({})).unwrap()["count"],1);service(r,"drawing.delete",&json!({"id":id})).unwrap();assert!(service(r,"drawing.current",&json!({})).unwrap().is_null());assert!(snapshot(r,id,4,true).is_err());}
 #[test]fn text_bounds_and_limits(){let d=tempfile::tempdir().unwrap();let r=d.path();let doc=import(r,&json!({"image":encode(&jpeg())})).unwrap();let mut args=json!({"id":doc["id"],"revision":0,"mark":{"tool":"text","color":4294967295u32,"width":4,"x":100,"y":90,"text":"A note","points":[],"box_width":60,"box_height":30}});service(r,"drawing.text",&args).unwrap();assert_eq!(snapshot(r,doc["id"].as_str().unwrap(),1,false).unwrap()["marks"][0]["x"],60.);args["revision"]=json!(1);args["mark"]["text"]=json!("x".repeat(401));assert!(service(r,"drawing.text",&args).is_err());assert_eq!(service(r,"drawing.load",&json!({"id":doc["id"]})).unwrap()["revision"],1);}
}

/// Portable editable images, including marks/history; excludes viewport and last-open state.
pub(crate) fn backup_export(root:&Path)->Result<(Value,std::collections::BTreeMap<String,PathBuf>)>{
 let mut records=Vec::new();let mut paths=std::collections::BTreeMap::new();
 for e in fs::read_dir(root).map_err(|e|e.to_string())?{let name=e.map_err(|e|e.to_string())?.file_name().to_string_lossy().into_owned();
  if let Some(id)=name.strip_prefix("drawing-").and_then(|n|n.strip_suffix(".json")){
   let d=load(root,id)?;let p=path(root,id,"jpg")?;let image=crate::sdk::read_bounded(&p,600*1024)?;if dimensions(&image)?!=(d.width,d.height){return Err("Saved image dimensions do not match".into())}
   records.push(serde_json::to_value(d).map_err(|e|e.to_string())?);paths.insert(format!("images/{id}.jpg"),p);
  }
 }Ok((json!(records),paths))
}
fn backup_documents(v:&Value)->Result<Vec<Document>>{
 let docs:Vec<Document>=serde_json::from_value(v.clone()).map_err(|_|"Invalid annotated image backup")?;
 if docs.len()>2000{return Err("Too many annotated images".into())}let mut ids=std::collections::BTreeSet::new();
 for d in &docs{if !crate::valid_id(&d.id)||!ids.insert(&d.id)||d.revision==u64::MAX||d.width==0||d.height==0||d.width>1800||d.height>1800||d.title.encode_utf16().count()>80||d.title!=crate::plain(&d.title,80)||!valid_marks(&d.marks,d.width,d.height)||d.undo.len()>10||d.redo.len()>10||history_bytes(d)>HISTORY||!d.undo.iter().chain(&d.redo).all(|m|valid_marks(m,d.width,d.height))||serde_json::to_vec(d).map_err(|e|e.to_string())?.len() as u64>MAX_DOC{return Err("Invalid annotated image backup".into())}}
 Ok(docs)
}
pub(crate) fn backup_plan(root:&Path,v:&Value,blobs:&std::collections::BTreeMap<String,crate::library_backup::Blob>)->Result<crate::library_backup::Plan>{
 let docs=backup_documents(v)?;let expected:std::collections::BTreeSet<_>=docs.iter().map(|d|format!("images/{}.jpg",d.id)).collect();
 if expected!=blobs.keys().cloned().collect(){return Err("Annotated image backup payload mismatch".into())}
 let current=fs::read_dir(root).map_err(|e|e.to_string())?.filter_map(|e|e.ok()).filter(|e|e.file_name().to_string_lossy().starts_with("drawing-")&&e.file_name().to_string_lossy().ends_with(".json")).count();
 let mut added=0;let mut fresh=0;let mut targets=std::collections::BTreeMap::new();
 for d in &docs{let key=format!("images/{}.jpg",d.id);if blobs[&key].bytes>600*1024{return Err("Annotated image is too large".into())}
  if !path(root,&d.id,"json")?.try_exists().map_err(|e|e.to_string())?{fresh+=1;added+=serde_json::to_vec(d).map_err(|e|e.to_string())?.len() as u64;let image=path(root,&d.id,"jpg")?;if !image.exists(){added+=blobs[&key].bytes;}targets.insert(key,image);}
 }
 if current+fresh>2000||crate::sdk::private_size(root,&root.join("unused"))?+added>16*1024*1024{return Err("Restored images would exceed the private image library limit".into())}
 Ok(crate::library_backup::Plan{new:fresh,kept:docs.len()-fresh,targets})
}
pub(crate) fn backup_commit(host:&Path,root:&Path,v:&Value,blobs:&std::collections::BTreeMap<String,crate::library_backup::Blob>)->Result<()>{
 backup_plan(root,v,blobs)?;
 for d in backup_documents(v)?{crate::library_backup::check(host)?;let p=path(root,&d.id,"json")?;if p.exists(){continue}
  let image=crate::sdk::read_bounded(&path(root,&d.id,"jpg")?,600*1024)?;if dimensions(&image)?!=(d.width,d.height){return Err("Restored image dimensions do not match".into())}
  crate::sdk::atomic(&p,&serde_json::to_vec(&d).map_err(|e|e.to_string())?)?;
 }Ok(())
}

pub(crate) fn backup_validate_image(v:&Value,bytes:&[u8])->Result<()>{let docs=backup_documents(&json!([v]))?;let d=&docs[0];if dimensions(bytes)?!=(d.width,d.height){return Err("Image dimensions do not match backup metadata".into())}Ok(())}
