//! Shared read-only text screen. Uses the real app-private collection broker for
//! page-level position persistence. Content and revision are supplied by the parent.
use crate::*;
const PAGE:usize=4000;
const STORE:&str="reading_positions_v1";
fn offset(s:&Value)->usize{s["offset"].as_u64().unwrap_or(0) as usize}
fn save(s:&Value,id:&str,revision:&str)->Value{
 collections::transact(STORE,s["store_revision"].as_u64().unwrap_or(0),1,None,vec![collections::put(id,reading::bookmark(id,revision,offset(s)).unwrap())],"reading.saved")
}
fn snippet(text:&str,start:usize,end:usize)->String{
 let before=text[..start].char_indices().rev().nth(30).map(|(i,_)|i).unwrap_or(0);
 let after=text[end..].char_indices().nth(70).map(|(i,_)|end+i).unwrap_or(text.len());
 format!("{}{}{}",if before>0{"…"}else{""},text[before..after].replace('\n'," "),if after<text.len(){"…"}else{""})
}
/// `id` is a stable ASCII record key (1–64 letters/digits/._-); `revision`
/// identifies immutable content, e.g. its digest. Saved offsets are UTF-8 bytes.
/// Search is case-sensitive literal text. This is not an EPUB/PDF parser.
pub fn reader(title:&str,input:Value,id:&str,revision:&str,text:&str)->Value{
 if id.is_empty()||id.len()>64||!id.bytes().all(|b|b.is_ascii_alphanumeric()||b"._-".contains(&b))||reading::bookmark(id,revision,0).is_err()||text.len()>1_000_000{
  return Screen::new(title,json!({})).body(vec![label("reading.error","This text or its content identity is unsupported.")]).finish()
 }
 let mut s=input["state"].clone();let e=&input["event"];let action=e["action"].as_str().unwrap_or("");let mut request=Value::Null;let mut close=false;
 let reset=s["format"]!="onklav.reading.ui.v1"||s["content"]!=id||s["revision"]!=revision;
 if reset{
  s=json!({"format":"onklav.reading.ui.v1","content":id,"revision":revision,"offset":0,"query":"","results":[],"search_end":0,"searched":false,"show_results":false,"store_revision":null,"ready":false,"busy":true,"dirty":false,"phase":"load","confirm":"","status":"Loading reading position…"});
  request=collections::get(STORE,id,"reading.loaded");
 }else{
  // Treat damaged retained offsets as beginning, never index a partial code point.
  if !text.is_char_boundary(offset(&s)){s["offset"]=json!(0);}
  match action{
   "reading.loaded" if e["reply"]["ok"]==true=>{
    let v=&e["reply"]["value"];s["store_revision"]=v["revision"].clone();s["busy"]=json!(false);s["ready"]=json!(true);
    if s["phase"]=="reload"{
     let saved=reading::resume(&v["data"],id,revision,text);
     if v["data"]["content"]==id&&v["data"]["revision"]==revision&&saved==offset(&s)&&v["data"]["offset"].as_u64()==Some(offset(&s) as u64){s["dirty"]=json!(false);s["status"]=json!("Position saved");}
     else{s["confirm"]=json!("replace");s["status"]=json!("Choose which reading position to keep");}
    }else{s["offset"]=json!(reading::resume(&v["data"],id,revision,text));s["status"]=json!(if !v["data"].is_null()&&v["data"]["revision"]!=revision{"Content changed. Starting at the beginning."}else{"Ready"});}
   },
   "reading.loaded"=>{s["busy"]=json!(false);s["ready"]=json!(true);s["store_revision"]=Value::Null;s["status"]=json!("Reading position is unavailable. You can keep reading or retry.");},
   "reading.saved"=>{s["busy"]=json!(false);if e["reply"]["ok"]==true{s["store_revision"]=e["reply"]["value"]["revision"].clone();s["dirty"]=json!(false);s["status"]=json!("Position saved");}else{s["dirty"]=json!(true);s["status"]=json!("Position not saved. Your current page is kept. Retry to review the saved position.");}},
   "reading.query"=>{let q=e["value"].as_str().unwrap_or("");s["query"]=json!(q.chars().take(128).collect::<String>());s["results"]=json!([]);s["search_end"]=json!(0);s["searched"]=json!(false);s["show_results"]=json!(false);},
   "reading.find"|"reading.more"=>{
    let q=s["query"].as_str().unwrap_or("");let from=if action=="reading.more"{s["search_end"].as_u64().unwrap_or(0) as usize}else{0};
    match reading::find(text,q,from){Ok(results)=>{s["results"]=json!(results);s["search_end"]=json!(results.last().map(|r|r.1).unwrap_or(text.len()));s["searched"]=json!(true);s["show_results"]=json!(true);s["status"]=json!(if results.is_empty(){"No matches in this section"}else{"Choose a match to read from there"});},Err(_)=>s["status"]=json!("Search text is empty or too long. Try a shorter phrase.")}
   },
   "reading.results"=>s["show_results"]=json!(true),
   "reading.goto"=>{if let Some(at)=e["value"].as_str().and_then(|v|v.parse::<usize>().ok()).filter(|at|text.is_char_boundary(*at)){s["offset"]=json!(at);s["dirty"]=json!(true);s["show_results"]=json!(false);}},
   "reading.next"|"reading.previous" if s["ready"]==true=>{
    s["show_results"]=json!(false);
    let at=offset(&s);let next=if action=="reading.next"{reading::page(text,at,PAGE).unwrap().end}else{text[..at].char_indices().rev().nth(PAGE-1).map(|(i,_)|i).unwrap_or(0)};
    if next<text.len()||next==0{s["offset"]=json!(next);s["dirty"]=json!(true);}
   },
   _ if action.starts_with("reading.hit.")=>{
    if let Ok(index)=action[12..].parse::<usize>(){if let Some(row)=s["results"].as_array().and_then(|r|r.get(index)).cloned(){
     let start=row[0].as_u64().unwrap_or(u64::MAX) as usize;let end=row[1].as_u64().unwrap_or(0) as usize;
     if text.get(start..end)==s["query"].as_str()&&start<end{s["offset"]=json!(start);s["dirty"]=json!(true);s["show_results"]=json!(false);}
    }}
   },
   "reading.retry"=>{s["busy"]=json!(true);s["phase"]=json!(if s["dirty"]==true{"reload"}else{"load"});request=collections::get(STORE,id,"reading.loaded");},
   "reading.replace" if s["confirm"]=="replace"=>{s["confirm"]=json!("");s["busy"]=json!(true);request=save(&s,id,revision);},
   "reading.keep"=>{s["confirm"]=json!("");s["status"]=json!("Current page kept; saved position was not changed");},
   "reading.back"=>{if s["dirty"]==true{s["confirm"]=json!("leave");}else{close=true;}},
   "reading.leave" if s["confirm"]=="leave"=>close=true,_=>{}
  }
  if s["dirty"]==true&&s["busy"]!=true&&(matches!(action,"reading.next"|"reading.previous"|"reading.goto")||action.starts_with("reading.hit.")){
   if s["store_revision"].is_u64(){s["busy"]=json!(true);request=save(&s,id,revision);}else{s["status"]=json!("Position not saved. Retry when storage access is available.");}
  }
 }
 let at=offset(&s);let page=reading::page(text,at,PAGE).unwrap();let busy=s["busy"]==true;let mut body=vec![field("reading.query","Find in text (case-sensitive)",s["query"].as_str().unwrap_or("")).limit(128).enabled(!busy),button("reading.find","Find").enabled(!busy),label("reading.status",s["status"].as_str().unwrap_or(""))];
 if s["show_results"]==true{
 for(index,row)in s["results"].as_array().into_iter().flatten().take(40).enumerate(){if let(Some(start),Some(end))=(row[0].as_u64(),row[1].as_u64()){if text.get(start as usize..end as usize).is_some(){body.push(record(&format!("reading.hit.{index}"),&format!("Match {}",index+1),&snippet(text,start as usize,end as usize)).enabled(!busy));}}}
 if s["results"].as_array().is_some_and(|r|r.len()==40)&&s["search_end"].as_u64().unwrap_or(0)<text.len() as u64{body.push(button("reading.more","More matches").enabled(!busy));}
 }else if s["results"].as_array().is_some_and(|r|!r.is_empty()){body.push(button("reading.results","Show matches").enabled(!busy));}
 if text.is_empty(){body.push(empty_state("reading.empty","This document has no text."));}else{body.push(label("reading.page",page.text));}
 body.extend([button("reading.previous","Previous page").enabled(!busy&&at>0),button("reading.next","Next page").enabled(!busy&&page.end<text.len())]);
 if s["dirty"]==true||s["store_revision"].is_null(){body.push(button("reading.retry","Retry position storage").enabled(!busy));}
 let percent=if text.is_empty(){100}else{at*100/text.len()};body.push(label("reading.progress",&format!("Progress: {percent}%")));
 let mut frame=Screen::new(title,s.clone()).body(body).back("reading.back").scrollable().scroll_key(&format!("reading.{id}.{revision}.{at}"));
 if s["confirm"]=="replace"{frame=frame.confirm_labeled("Replace saved position?","Another session may have saved a different position. Use this page instead?","reading.replace","reading.keep","Use this page");}
 if s["confirm"]=="leave"{frame=frame.confirm_labeled("Leave without saving position?","Your document is unchanged. This page could not be saved.","reading.leave","reading.keep","Leave");}
 if close{frame=frame.close();}frame.effect(request).finish()
}

#[cfg(test)]mod tests{
 use super::*;
 fn step(state:Value,action:&str,value:&str,text:&str)->Value{reader("Reading",json!({"state":state,"event":{"action":action,"value":value}}),"book","v1",text)}
 fn loaded(text:&str,data:Value)->Value{let f=step(Value::Null,"start","",text);reader("Reading",json!({"state":f["state"],"event":{"action":"reading.loaded","reply":{"ok":true,"value":{"revision":7,"schema":1,"data":data}}}}),"book","v1",text)}
 #[test]fn unicode_search_selects_exact_match_and_saves_page_offset(){let text="prefix 😀 alpha 😀";let f=loaded(text,Value::Null);let query=step(f["state"].clone(),"reading.query","😀",text);let found=step(query["state"].clone(),"reading.find","",text);assert_eq!(found["state"]["results"],json!([[7,11],[18,22]]));let selected=step(found["state"].clone(),"reading.hit.1","",text);assert_eq!(selected["state"]["offset"],18);assert_eq!(selected["state"]["show_results"],false);assert!(!selected["body"].as_array().unwrap().iter().any(|n|n["kind"]=="record"));assert_eq!(selected["effect"]["service"],"collections.transact");assert_eq!(selected["effect"]["args"]["expected_revision"],7);assert_eq!(selected["effect"]["args"]["operations"][0]["data"]["offset"],18);}
 #[test]fn reopen_requires_exact_revision_and_valid_unicode_boundary(){let text="A😀B";let f=loaded(text,reading::bookmark("book","v1",5).unwrap());assert_eq!(f["state"]["offset"],5);let changed=loaded(text,reading::bookmark("book","old",5).unwrap());assert_eq!(changed["state"]["offset"],0);let invalid=loaded(text,json!({"content":"book","revision":"v1","offset":2}));assert_eq!(invalid["state"]["offset"],0);}
 #[test]fn page_turns_are_bounded_and_reversible_without_storing_full_text(){let text="😀".repeat(9000);let f=loaded(&text,Value::Null);let next=step(f["state"].clone(),"reading.next","",&text);assert_eq!(next["state"]["offset"],16000);assert!(next["state"].to_string().len()<2000);let back=step(next["state"].clone(),"reading.previous","",&text);assert_eq!(back["state"]["offset"],0);assert!(back["body"].as_array().unwrap().iter().find(|n|n["id"]=="reading.page").unwrap()["text"].as_str().unwrap().chars().count()<=PAGE);}
 #[test]fn failed_save_retains_page_and_requires_review_before_overwriting_conflict(){let text="a".repeat(5000);let f=loaded(&text,Value::Null);let next=step(f["state"].clone(),"reading.next","",&text);let failed=reader("Reading",json!({"state":next["state"],"event":{"action":"reading.saved","reply":{"ok":false,"error":"Conflict"}}}),"book","v1",&text);assert_eq!(failed["state"]["offset"],4000);assert_eq!(failed["state"]["dirty"],true);assert!(failed["effect"].is_null());let retry=step(failed["state"].clone(),"reading.retry","",&text);let reviewed=reader("Reading",json!({"state":retry["state"],"event":{"action":"reading.loaded","reply":{"ok":true,"value":{"revision":9,"data":{"content":"book","revision":"v1","offset":0}}}}}),"book","v1",&text);assert_eq!(reviewed["confirm"]["title"],"Replace saved position?");assert!(reviewed["effect"].is_null());let replace=step(reviewed["state"].clone(),"reading.replace","",&text);assert_eq!(replace["effect"]["args"]["expected_revision"],9);let cancel=step(reviewed["state"].clone(),"reading.keep","",&text);assert!(cancel["effect"].is_null());assert_eq!(cancel["state"]["offset"],4000);}
 #[test]fn search_continuation_is_bounded_and_stale_query_results_are_discarded(){let text="x ".repeat(100);let f=loaded(&text,Value::Null);let q=step(f["state"].clone(),"reading.query","x",&text);let first=step(q["state"].clone(),"reading.find","",&text);assert_eq!(first["state"]["results"].as_array().unwrap().len(),40);let second=step(first["state"].clone(),"reading.more","",&text);assert_eq!(second["state"]["results"][0],json!([80,81]));let changed=step(second["state"].clone(),"reading.query","other",&text);assert!(changed["state"]["results"].as_array().unwrap().is_empty());let stale=step(changed["state"].clone(),"reading.hit.0","",&text);assert_eq!(stale["state"]["offset"],0);assert!(stale["effect"].is_null());}
}

/// Parent-owned navigation to an exact Unicode boundary, with normal position saving.
pub fn go_to(title:&str,state:Value,id:&str,revision:&str,text:&str,offset:usize)->Value{
 reader(title,json!({"state":state,"event":{"action":"reading.goto","value":offset.to_string()}}),id,revision,text)
}

#[cfg(test)]mod chapter_jump_tests{
 use super::*;
 #[test]fn parent_jump_rejects_partial_unicode_and_saves_a_valid_exact_offset(){let text="A😀B";let start=reader("Reading",json!({}),"book","r",text);let loaded=reader("Reading",json!({"state":start["state"],"event":{"action":"reading.loaded","reply":{"ok":true,"value":{"revision":0,"data":null}}}}),"book","r",text);let invalid=go_to("Reading",loaded["state"].clone(),"book","r",text,2);assert_eq!(invalid["state"]["offset"],0);assert!(invalid["effect"].is_null());let valid=go_to("Reading",loaded["state"].clone(),"book","r",text,5);assert_eq!(valid["state"]["offset"],5);assert_eq!(valid["effect"]["service"],"collections.transact");}
}
