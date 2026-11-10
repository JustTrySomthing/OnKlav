use onklav_ui::{*,serde_json::{json,Value}};
const MIME:&[u8]=b"From: fixture@example.org\r\nTo: reader@example.org\r\nSubject: Synthetic MIME fixture\r\nContent-Type: multipart/mixed; boundary=demo\r\n\r\n--demo\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Transfer-Encoding: quoted-printable\r\n\r\nPlain=20fixture\r\n--demo\r\nContent-Type: application/octet-stream; name=demo.bin\r\nContent-Disposition: attachment; filename=demo.bin\r\nContent-Transfer-Encoding: base64\r\n\r\nAP+A\r\n--demo--\r\n";
fn step(input:Value)->Value{let mut s=input["state"].clone();if !s.is_object(){s=json!({"status":"Synthetic local fixture; no provider contacted"});}let e=&input["event"];let a=e["action"].as_str().unwrap_or("");let v=&e["reply"]["value"];let ok=e["reply"]["ok"]==true;let mut req=Value::Null;match a{
"fixture.parse"=>req=blobs::begin("fixture.eml","message/rfc822",false,"fixture.begun"),
"fixture.begun" if ok=>{s["file"]=v["id"].clone();req=blobs::append_bytes(v["id"].as_str().unwrap(),0,MIME,"fixture.appended").unwrap();},
"fixture.appended" if ok=>req=blobs::finish(s["file"].as_str().unwrap(),None,"fixture.finished"),
"fixture.finished" if ok=>req=email_transfer::parse_mime(s["file"].as_str().unwrap(),"fixture.parsed"),
"fixture.parsed" if ok=>{s["mime"]=v.clone();s["status"]=json!("MIME fixture parsed by private broker");},
"fixture.upload"=>if let Some(id)=s["file"].as_str(){req=http::upload("https://mail.example.org/upload",id,"message/rfc822","aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa","fixture.uploaded");},
"fixture.export"=>if let Some(id)=s["mime"]["attachments"][0]["id"].as_str(){req=blobs::export(id,"fixture.exported");},_=>{}}
let status=s["status"].as_str().unwrap_or("").to_string();Screen::new("Workflow fixture",s).body(vec![label("status",&status),button("fixture.parse","Parse synthetic MIME"),button("fixture.upload","Request upload (native pending only)"),button("fixture.export","Export fixture attachment")]).effect(req).finish()}
onklav_ui::app!(step);
