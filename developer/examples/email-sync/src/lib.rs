use onklav_ui::{*,serde_json::{json,Value}};
fn mailbox()->email::Mailbox{email::Mailbox::new("https://mail.example.org/jmap","demo","inbox","fixture-opaque-handle").unwrap()}
fn step(input:Value)->Value{let mut s=input["state"].clone();if !s.is_object(){s=json!({"status":"Synthetic headers; no live account connected","revision":0});}let e=&input["event"];let a=e["action"].as_str().unwrap_or("");let v=&e["reply"]["value"];let mut effect=Value::Null;match a{
"start"=>effect=email::load("loaded"),
"loaded" if e["reply"]["ok"]==true=>{s["revision"]=v["revision"].clone();if let Ok(snapshot)=email::saved(&mailbox(),v){s["snapshot"]=snapshot;s["status"]=json!("Saved fixture headers");}},
"fixture.sync"=>{let raw=json!({"methodResponses":[["Email/query",{"accountId":"demo","position":0,"queryState":"q1","ids":["e1"]},"q"],["Email/get",{"accountId":"demo","state":"s1","notFound":[],"list":[{"id":"e1","subject":"Demo header — fixture","from":[],"receivedAt":"2026-10-25T00:00:00Z"}]},"g"]]});let page=email::page(&mailbox(),&json!({"status":200,"text":raw.to_string()}),0).unwrap();s["draft_snapshot"]=email::merge(&s["snapshot"],&page).unwrap();effect=email::save(s["draft_snapshot"].clone(),s["revision"].as_u64().unwrap_or(0),"saved").unwrap();},
"saved" if e["reply"]["ok"]==true=>{s["revision"]=v["revision"].clone();s["snapshot"]=s["draft_snapshot"].take();s["status"]=json!("Fixture snapshot saved");},
"saved"=>s["status"]=json!("Save denied or conflicted; previous snapshot retained"),
"account.connect"=>effect=email::connect("mail","connected"),_=>{}}
let status=s["status"].as_str().unwrap_or("").to_string();let mut body=vec![label("notice","Synthetic fixture; no live mailbox or credentials"),label("status",&status),button("fixture.sync","Sync fixture headers"),button("account.connect","Connect configured mail provider")];for(i,row)in s["snapshot"]["items"].as_array().into_iter().flatten().enumerate(){body.push(label(&format!("header.{i}"),row["subject"].as_str().unwrap_or("")));}let mut f=Screen::new("Email sync",s).body(body).scrollable().finish();if !effect.is_null(){f["effect"]=effect;}f}
onklav_ui::app!(step);
