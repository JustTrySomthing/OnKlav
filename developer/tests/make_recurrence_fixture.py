"""Disposable signed recurrence/absolute-reminder public-helper fixture."""
import importlib.util,tempfile
from pathlib import Path
HERE=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('recurrence_sdk',HERE/'onklav.py');sdk=importlib.util.module_from_spec(spec);spec.loader.exec_module(sdk)
with tempfile.TemporaryDirectory(prefix='onklav-recurrence-') as temp:
 root=Path(temp);p=root/'app';sdk.new_project(p,'org.example.recurrencefixture','Recurrence fixture',template_name='social',capabilities=['notifications.post'])
 (p/'src/lib.rs').write_text('''use onklav_ui::{recurrence::Rule,notifications,clock,Screen,button,label,serde_json::{json,Value}};
fn step(v:Value)->Value{let mut s=v["state"].clone();if !s.is_object(){s=json!({"result":null});}let a=v["event"]["action"].as_str().unwrap_or("");let effect=match a{
"start"|"expand"=>Rule::timed("2026-03-07T09:00:00","America/Chicago","daily",3600,3).between(0,253402300799999,"expanded"),
"expanded"=>{s["result"]=v["event"]["reply"].clone();clock::read("clock.ready")},
"clock.ready"=>{s["now"]=v["event"]["reply"]["value"]["epoch_ms"].clone();Value::Null},
"at"=>notifications::schedule_at("fixture","Reminder","Test reminder",s["now"].as_u64().unwrap_or(0)+120000,"done"),
"status"=>notifications::status("fixture","done"),"pending"=>notifications::pending("done"),"cancel"=>notifications::cancel("fixture","done"),
"done"=>{s["result"]=v["event"]["reply"].clone();Value::Null},_=>Value::Null};
Screen::new("Recurrence fixture",s.clone()).body(vec![label("status.text",&s["result"].to_string()),button("expand","Expand"),button("at","Schedule"),button("status","Status"),button("pending","Pending"),button("cancel","Cancel")]).effect(effect).finish()}onklav_ui::app!(step);''')
 key=root/'fixture.key';sdk.packer('keygen',key);out=root/'fixture.onklav-a';sdk.build_project(p,key,out);(HERE.parent/'docs/fixtures/recurrence-wip.onklav-a').write_bytes(out.read_bytes())
