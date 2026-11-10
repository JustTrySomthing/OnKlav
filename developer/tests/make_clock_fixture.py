"""Build a disposable signed permission-free clock app using the public helper."""
import importlib.util, json, tempfile
from pathlib import Path
HERE=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('clock_sdk',HERE/'onklav.py')
sdk=importlib.util.module_from_spec(spec);spec.loader.exec_module(sdk)
with tempfile.TemporaryDirectory(prefix='onklav-clock-') as temp:
 root=Path(temp);p=root/'app'
 sdk.new_project(p,'org.example.clockfixture','Clock fixture',template_name='social',capabilities=[])
 (p/'src/lib.rs').write_text('''use onklav_ui::{clock,Screen,label,button,serde_json::{json,Value}};
fn step(v:Value)->Value{let mut s=v["state"].clone();if !s.is_object(){s=json!({});}
let effect=match v["event"]["action"].as_str().unwrap_or(""){
"done"=>{s["result"]=v["event"]["reply"].clone();Value::Null},
"start"|"refresh"=>clock::read("done"),_=>Value::Null};
Screen::new("Clock fixture",s.clone()).body(vec![label("status",&s["result"].to_string()),button("refresh","Refresh")]).effect(effect).finish()}
onklav_ui::app!(step);''')
 key=root/'fixture.key';sdk.packer('keygen',key)
 output=root/'clock.onklav-a';sdk.build_project(p,key,output)
 out=HERE.parent/'docs/fixtures/clock-wip.onklav-a';out.parent.mkdir(parents=True,exist_ok=True);out.write_bytes(output.read_bytes())
