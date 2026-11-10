//! Text-first EPUB screen composed with the shared reading UI and private bookmarks.
use crate::*;
const STORE:&str="epub_books_v1";
fn save(s:&Value,id:&str)->Value{collections::transact(STORE,s["store_revision"].as_u64().unwrap_or(0),1,None,vec![collections::put(id,json!({"revision":s["book"]["revision"],"chapter":s["chapter"]["chapter"]}))],"epub.bookmark.saved")}
/// Parent imports an EPUB with blobs::import, then passes its private handle here.
/// Book/chapter positions are app-private. Images/CSS/script rendering is not provided.
pub fn reader(title:&str,input:Value,id:&str)->Value{
 let mut s=input["state"].clone();let e=&input["event"];let a=e["action"].as_str().unwrap_or("");let ok=e["reply"]["ok"]==true;let v=&e["reply"]["value"];let mut request=Value::Null;let mut open=None;let mut close=false;let was_read=s["mode"]=="read";
 if ok&&a.starts_with("epub."){s["error"]=json!("");}
 if s["format"]!="onklav.epub.ui.v1"||s["id"]!=id{
  s=json!({"format":"onklav.epub.ui.v1","id":id,"book":null,"chapter":null,"reading":null,"mode":"read","phase":"opening","store_revision":0,"bookmark_dirty":false,"save_chapter":false,"target":0,"anchor":"","jump":null,"query":"","matches":[],"next":null,"toc_page":0,"status":"Opening EPUB…","confirm":false,"leave_confirm":false});request=epub::info(id,"epub.opened");
 }else{match a{
  "epub.opened" if ok=>{s["book"]=v.clone();request=collections::get(STORE,id,"epub.position.loaded");},
  "epub.position.loaded" if ok=>{s["store_revision"]=v["revision"].clone();let chapter=if v["data"]["revision"]==s["book"]["revision"]{v["data"]["chapter"].as_u64().unwrap_or(0)as usize}else{0};open=Some((chapter.min(s["book"]["chapters"].as_array().map_or(1,Vec::len)-1),String::new(),None));},
  "epub.chapter.loaded" if ok=>{
   s["chapter"]=v.clone();s["reading"]=Value::Null;s["phase"]=json!("read");s["mode"]=json!("read");
   let anchor=s["anchor"].as_str().unwrap_or("");if !anchor.is_empty(){s["jump"]=v["anchors"][anchor].clone();if s["jump"].is_null(){s["status"]=json!("Section marker unavailable; chapter opened");}}
   if s["save_chapter"]==true{s["save_chapter"]=json!(false);s["phase"]=json!("saving");request=save(&s,id);}
  },
  "epub.bookmark.saved"=>{s["phase"]=json!("read");if ok{s["store_revision"]=v["revision"].clone();s["bookmark_dirty"]=json!(false);}else{s["bookmark_dirty"]=json!(true);s["status"]=json!("Chapter bookmark not saved. Your open chapter is kept.");}},
  "epub.bookmark.retry"=>request=collections::get(STORE,id,"epub.bookmark.review"),
  "epub.bookmark.review" if ok=>{s["store_revision"]=v["revision"].clone();s["confirm"]=json!(true);},
  "epub.bookmark.replace" if s["confirm"]==true=>{s["confirm"]=json!(false);request=save(&s,id);},
  "epub.bookmark.keep"=>s["confirm"]=json!(false),
  "epub.leave" if s["leave_confirm"]==true=>{s["leave_confirm"]=json!(false);close=true;},
  "epub.stay"=>s["leave_confirm"]=json!(false),
  "epub.toc"=>{s["mode"]=json!("toc");s["toc_page"]=json!(0);},
  "epub.toc.more"=>{s["toc_page"]=json!(s["toc_page"].as_u64().unwrap_or(0)+20);},
  "epub.toc.previous"=>s["toc_page"]=json!(s["toc_page"].as_u64().unwrap_or(0).saturating_sub(20)),
  _ if a.starts_with("epub.toc.")=>{if let Some(row)=a[9..].parse::<usize>().ok().and_then(|n|s["book"]["toc"].as_array().and_then(|rows|rows.get(n))){open=Some((row["chapter"].as_u64().unwrap_or(0)as usize,row["anchor"].as_str().unwrap_or("").into(),Some(0)));s["save_chapter"]=json!(true);}},
  "epub.goto"=>{if let Some(chapter)=e["value"]["chapter"].as_u64().map(|n|n as usize).filter(|n|*n<s["book"]["chapters"].as_array().map_or(0,Vec::len)){open=Some((chapter,String::new(),Some(e["value"]["offset"].as_u64().unwrap_or(0)as usize)));s["save_chapter"]=json!(true);}},
  "epub.next"|"epub.previous"=>{let current=s["chapter"]["chapter"].as_u64().unwrap_or(0)as usize;let next=if a=="epub.next"{current+1}else{current.saturating_sub(1)};if next<s["book"]["chapters"].as_array().map_or(0,Vec::len){open=Some((next,String::new(),Some(0)));s["save_chapter"]=json!(true);}},
  "epub.search"=>s["mode"]=json!("search"),
  "epub.query"=>{s["query"]=json!(e["value"].as_str().unwrap_or("").chars().take(128).collect::<String>());s["matches"]=json!([]);s["next"]=Value::Null;},
  "epub.find"|"epub.find.more"=>{let next=&s["next"];request=epub::search(id,s["query"].as_str().unwrap_or(""),if a=="epub.find.more"{next["chapter"].as_u64().unwrap_or(0)as usize}else{0},if a=="epub.find.more"{next["offset"].as_u64().unwrap_or(0)as usize}else{0},"epub.found");},
  "epub.found" if ok=>{s["matches"]=v["items"].clone();s["next"]=v["next"].clone();s["status"]=json!(if v["items"].as_array().is_none_or(|r|r.is_empty()){"No matches in this section"}else{"Choose a match"});},
  _ if a.starts_with("epub.hit.")=>{if let Some(row)=a[9..].parse::<usize>().ok().and_then(|n|s["matches"].as_array().and_then(|r|r.get(n))){open=Some((row["chapter"].as_u64().unwrap_or(0)as usize,String::new(),row["start"].as_u64().map(|n|n as usize)));s["save_chapter"]=json!(true);}},
  "epub.back" if s["chapter"].is_null()=>close=true,
  "epub.back" if s["mode"]!="read"=>s["mode"]=json!("read"),
  "epub.retry"=>{if s["book"].is_null(){request=epub::info(id,"epub.opened")}else{request=epub::chapter(id,s["target"].as_u64().unwrap_or(0)as usize,"epub.chapter.loaded")}},_=>{}
 }
 if e["reply"]["ok"]==false&&a!="epub.bookmark.saved"&&!a.starts_with("reading."){s["status"]=json!(e["reply"]["error"].as_str().unwrap_or("Could not open this book"));s["error"]=s["status"].clone();s["phase"]=json!(if s["chapter"].is_object(){"read"}else{"error"});}
 if let Some((chapter,anchor,jump))=open{s["target"]=json!(chapter);s["anchor"]=json!(anchor);s["jump"]=json!(jump);s["phase"]=json!("opening");request=epub::chapter(id,chapter,"epub.chapter.loaded");}
 }
 let mut frame=if s["phase"]=="read"&&s["chapter"].is_object(){
  let chapter=s["chapter"]["chapter"].as_u64().unwrap_or(0);let key=format!("epub_{id}_{chapter}");let revision=s["book"]["revision"].as_str().unwrap_or("");let text=s["chapter"]["text"].as_str().unwrap_or("");
  let event=if a=="epub.back"&&was_read{json!({"action":"reading.back"})}else if a.starts_with("reading."){e.clone()}else{Value::Null};
  let mut read=reading_ui::reader(title,json!({"state":s["reading"],"event":event}),&key,revision,text);
  if read["state"]["ready"]==true&&read["state"]["busy"]!=true{if let Some(jump)=s["jump"].as_u64(){if text.is_char_boundary(jump as usize){read=reading_ui::go_to(title,read["state"].clone(),&key,revision,text,jump as usize);}s["jump"]=Value::Null;}}
  s["reading"]=read["state"].clone();if read["close_requested"]==true{if s["bookmark_dirty"]==true{s["leave_confirm"]=json!(true);read["close_requested"]=json!(false);}else{close=true;}}
  if request.is_null(){request=read["effect"].clone();}
  if s["mode"]=="read"{let ready=s["reading"]["busy"]!=true&&s["reading"]["dirty"]!=true;let body=read["body"].as_array_mut().unwrap();body.insert(0,heading("epub.title",s["book"]["title"].as_str().unwrap_or("Book")));body.insert(1,label("epub.chapter_title",s["chapter"]["title"].as_str().unwrap_or("Chapter")));body.push(button("epub.previous","Previous chapter").enabled(ready&&chapter>0));body.push(button("epub.next","Next chapter").enabled(ready&&chapter+1<s["book"]["chapters"].as_array().map_or(0,Vec::len)as u64));read["actions"]=json!([icon("epub.toc","Contents","menu").enabled(ready),icon("epub.search","Search book","search").enabled(ready)]);read}else{Screen::new(title,s.clone()).finish()}
 }else{Screen::new(title,s.clone()).body(vec![label("epub.status",s["status"].as_str().unwrap_or("Opening chapter…")),button("epub.retry","Retry"),button("epub.back","Back")]).finish()};
 if s["mode"]=="toc"{let rows=s["book"]["toc"].as_array();let at=s["toc_page"].as_u64().unwrap_or(0)as usize;let mut body=Vec::new();for(i,row)in rows.into_iter().flatten().enumerate().skip(at).take(20){body.push(record(&format!("epub.toc.{i}"),row["title"].as_str().unwrap_or("Section"),""));}if at>0{body.push(button("epub.toc.previous","Previous sections"));}if at+20<rows.map_or(0,Vec::len){body.push(button("epub.toc.more","More sections"));}frame=Screen::new(title,s.clone()).body(body).scrollable().scroll_key(&format!("epub.toc.{at}")).finish();}
 if s["mode"]=="search"{let mut body=vec![field("epub.query","Find across chapters",s["query"].as_str().unwrap_or("")).limit(128),button("epub.find","Search book"),label("epub.search.status",s["status"].as_str().unwrap_or(""))];for(i,row)in s["matches"].as_array().into_iter().flatten().take(40).enumerate(){body.push(record(&format!("epub.hit.{i}"),row["title"].as_str().unwrap_or("Chapter"),&format!("Match {}",i+1)));}if s["next"].is_object(){body.push(button("epub.find.more","More matches"));}frame=Screen::new(title,s.clone()).body(body).scrollable().scroll_key("epub.search").finish();}
 if s["mode"]=="read"&&s["phase"]=="read"&&s["error"].as_str().is_some_and(|e|!e.is_empty()){frame["body"].as_array_mut().unwrap().push(label("epub.error",s["error"].as_str().unwrap()));}
 if s["bookmark_dirty"]==true{frame["body"].as_array_mut().unwrap().push(label("epub.bookmark.status","This chapter bookmark is not saved. The current chapter is kept open."));frame["body"].as_array_mut().unwrap().push(button("epub.bookmark.retry","Retry chapter bookmark"));}
 if s["confirm"]==true{frame["confirm"]=json!({"title":"Replace saved chapter?","text":"Another session may have saved a different chapter. Use the open chapter instead?","accept":"epub.bookmark.replace","cancel":"epub.bookmark.keep","accept_label":"Use this chapter"});}
 if s["leave_confirm"]==true{frame["confirm"]=json!({"title":"Leave without saving chapter?","text":"The current chapter could not be saved for reopening. Your book is unchanged.","accept":"epub.leave","cancel":"epub.stay","accept_label":"Leave"});}
 frame["state"]=s;frame["effect"]=request;frame["back_action"]=json!("epub.back");if close{frame["close_requested"]=json!(true);}frame
}

#[cfg(test)]mod tests{
 use super::*;
 #[test]fn epub_ui_unavailable_book_back_closes_and_retry_is_explicit(){let f=reader("Book",json!({}),"a");assert_eq!(f["effect"]["service"],"epub.info");let failed=reader("Book",json!({"state":f["state"],"event":{"action":"epub.opened","reply":{"ok":false,"error":"Unsupported book"}}}),"a");assert!(failed["effect"].is_null());assert!(failed["body"][0]["text"].as_str().unwrap().contains("Unsupported"));let retry=reader("Book",json!({"state":failed["state"],"event":{"action":"epub.retry"}}),"a");assert_eq!(retry["effect"]["service"],"epub.info");let close=reader("Book",json!({"state":failed["state"],"event":{"action":"epub.back"}}),"a");assert_eq!(close["close_requested"],true);}
 #[test]fn epub_bookmark_conflict_keeps_current_chapter_and_requires_confirmation(){let state=json!({"format":"onklav.epub.ui.v1","id":"book","book":{"revision":"r","chapters":[{"chapter":0}],"toc":[]},"chapter":{"chapter":0,"title":"One","text":"Text","anchors":{}},"reading":{"format":"onklav.reading.ui.v1","content":"epub_book_0","revision":"r","offset":0,"query":"","results":[],"ready":true,"busy":false,"dirty":false,"store_revision":1},"mode":"read","phase":"read","store_revision":2,"status":"","bookmark_dirty":false});let failed=reader("Book",json!({"state":state,"event":{"action":"epub.bookmark.saved","reply":{"ok":false}}}),"book");assert_eq!(failed["state"]["bookmark_dirty"],true);assert_eq!(failed["state"]["chapter"]["chapter"],0);assert!(failed["effect"].is_null());let leaving=reader("Book",json!({"state":failed["state"],"event":{"action":"epub.back"}}),"book");assert_eq!(leaving["confirm"]["title"],"Leave without saving chapter?");assert_ne!(leaving["close_requested"],true);let review=reader("Book",json!({"state":failed["state"],"event":{"action":"epub.bookmark.review","reply":{"ok":true,"value":{"revision":9}}}}),"book");assert_eq!(review["confirm"]["title"],"Replace saved chapter?");let accept=reader("Book",json!({"state":review["state"],"event":{"action":"epub.bookmark.replace"}}),"book");assert_eq!(accept["effect"]["args"]["expected_revision"],9);}
}

#[cfg(test)]mod failure_display_tests{
 use super::*;
 #[test]fn unsupported_chapter_keeps_previous_text_and_displays_the_error(){let s=json!({"format":"onklav.epub.ui.v1","id":"book","book":{"revision":"r","chapters":[{"chapter":0}],"toc":[]},"chapter":{"chapter":0,"title":"One","text":"Keep me","anchors":{}},"reading":{"format":"onklav.reading.ui.v1","content":"epub_book_0","revision":"r","offset":0,"query":"","results":[],"ready":true,"busy":false,"dirty":false,"store_revision":1},"mode":"read","phase":"opening"});let f=reader("Book",json!({"state":s,"event":{"action":"epub.chapter.loaded","reply":{"ok":false,"error":"Active EPUB content is unsupported"}}}),"book");assert_eq!(f["state"]["chapter"]["text"],"Keep me");assert!(f["body"].as_array().unwrap().iter().any(|n|n["id"]=="epub.error"&&n["text"]=="Active EPUB content is unsupported"));assert!(f["effect"].is_null());}
}
