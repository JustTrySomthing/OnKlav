"""Disposable signed app exercising jobs, downloads and the shared native player."""
import importlib.util,tempfile,json,hashlib
from pathlib import Path
HERE=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('tasks_sdk',HERE/'onklav.py');sdk=importlib.util.module_from_spec(spec);spec.loader.exec_module(sdk)
with tempfile.TemporaryDirectory(prefix='onklav-tasks-')as temp:
 root=Path(temp);p=root/'app';sdk.new_project(p,'org.example.tasksfixture','Tasks fixture',template_name='social',capabilities=[])
 m=json.loads((p/'manifest.json').read_text());m['capabilities']=['network.http','storage.private','background.tasks','audio.playback'];m['network']={'origins':['https://api.example.com']};(p/'manifest.json').write_text(json.dumps(m))
 source='''use onklav_ui::{jobs,downloads,media,http,Screen,button,label,serde_json::{json,Value}};
fn step(v:Value)->Value{let mut s=v["state"].clone();if !s.is_object(){s=json!({"value":null});}let action=v["event"]["action"].as_str().unwrap_or("");let effect=match action{
"download"=>downloads::Download::new("sample","https://api.example.com/sample","Sample.mp3","audio/mpeg").max_bytes(1024).sha256("HASH").start("done"),
"job"=>jobs::schedule("sync",http::request("GET","https://api.example.com/items",None,"unused"),0,"done"),
"media"=>media::play(vec![media::stream("https://api.example.com/sample","Sample")],"done"),
"done"=>{s["value"]=v["event"]["reply"].clone();Value::Null},_=>Value::Null};
Screen::new("Tasks fixture",s).body(vec![label("status","Native task services"),button("download","Download"),button("job","Sync"),button("media","Play sample"),media::player("player")]).effect(effect).finish()}onklav_ui::app!(step);'''.replace('HASH',hashlib.sha256(b'0123456789').hexdigest())
 # Public HTTP helper's builder is used by the fixture, just as in external apps.
 source=source.replace('http::request("GET","https://api.example.com/items",None,"unused")','http::get("https://api.example.com/items","unused")')
 (p/'src/lib.rs').write_text(source)
 key=root/'fixture.key';sdk.packer('keygen',key);output=root/'tasks.onklav-a';sdk.build_project(p,key,output)
 out=HERE.parent/'docs/fixtures/tasks-wip.onklav-a';out.parent.mkdir(parents=True,exist_ok=True);out.write_bytes(output.read_bytes())
