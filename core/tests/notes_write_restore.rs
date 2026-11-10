use onklav_core::dispatch;
use serde_json::{json,Value};
use std::fs;
fn call(root:&std::path::Path,value:Value)->Value{let r=dispatch(root,&value.to_string());assert_eq!(r["ok"],true,"{r}");r["value"].clone()}
#[test]fn restoring_an_old_backup_after_retirement_creates_write_documents(){
 let d=tempfile::tempdir().unwrap();let root=d.path();call(root,json!({"op":"notes_to_write"}));
 let id="a".repeat(32);let backup=json!({"format":"onklav-notes-backup","version":1,"notes":[{"id":id,"title":"Restored","text":"Keep my old backup 🐈","updated":10}]});
 fs::write(root.join("notes-backup-import.json"),backup.to_string()).unwrap();
 let review=call(root,json!({"op":"sdk","action":"notes_backup","operation":"inspect"}));
 call(root,json!({"op":"sdk","action":"notes_backup","operation":"restore","sha256":review["sha256"]}));
 assert_eq!(call(root,json!({"op":"notes_to_write","action":"status"}))["complete"],true);
 let linked=call(root,json!({"op":"notes_to_write","action":"linked","id":id}));
 let path=root.join(format!("write/documents/{}.json",linked["id"].as_str().unwrap()));let mut doc:Value=serde_json::from_slice(&fs::read(&path).unwrap()).unwrap();assert_eq!(doc["text"],"Keep my old backup 🐈");
 doc["text"]=json!("Edited later");fs::write(&path,doc.to_string()).unwrap();call(root,json!({"op":"notes_to_write","action":"sync","id":linked["id"]}));
 let shared:Value=serde_json::from_slice(&fs::read(root.join("onklav-content/objects").join(&id)).unwrap()).unwrap();assert_eq!(shared["text"],"Edited later");
 let repeated=call(root,json!({"op":"sdk","action":"notes_backup","operation":"restore","sha256":review["sha256"]}));assert_eq!(repeated["kept"],1);
 assert_eq!(serde_json::from_slice::<Value>(&fs::read(path).unwrap()).unwrap()["text"],"Edited later");
}
