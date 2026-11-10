"""Build a disposable signed guest for account connection broker checks."""
import importlib.util,tempfile,json
from pathlib import Path
HERE=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('account_sdk',HERE/'onklav.py');sdk=importlib.util.module_from_spec(spec);spec.loader.exec_module(sdk)
with tempfile.TemporaryDirectory(prefix='onklav-accounts-')as temp:
 root=Path(temp);p=root/'app';sdk.new_project(p,'org.example.accountfixture','Account fixture',template_name='social',capabilities=[])
 m=json.loads((p/'manifest.json').read_text());m['capabilities']=['network.http','accounts.connect'];m['network']={'origins':['https://api.example.com'],'accounts':[{'id':'example','label':'Example','authorization_endpoint':'https://api.example.com/auth','token_endpoint':'https://api.example.com/token','client_id':'public-client','scopes':['profile'],'api_origins':['https://api.example.com']}]};(p/'manifest.json').write_text(json.dumps(m))
 (p/'src/lib.rs').write_text('''use onklav_ui::{accounts,http,NodeExt,Screen,button,label,serde_json::{json,Value}};
fn step(v:Value)->Value{let mut s=v["state"].clone();if !s.is_object(){s=json!({"account":null,"last":null});}let action=v["event"]["action"].as_str().unwrap_or("");let handle=s["account"]["account"].as_str().unwrap_or("").to_owned();let effect=match action{
"connect"=>accounts::connect("example","connected"),"request" if !handle.is_empty()=>accounts::authenticated(http::get("https://api.example.com/items","data"),&handle),"disconnect" if !handle.is_empty()=>accounts::disconnect(&handle,"disconnected"),_=>Value::Null};
if action=="connected"&&v["event"]["reply"]["ok"]==true{s["account"]=v["event"]["reply"]["value"].clone();}if action=="data"||action=="disconnected"{s["last"]=v["event"]["reply"].clone();if action=="disconnected"&&v["event"]["reply"]["ok"]==true{s["account"]=Value::Null;}}
let connected=s["account"].is_object();Screen::new("Account fixture",s).body(vec![label("status","Account connection test"),button("connect","Connect account"),button("request","Read account data").enabled(connected),button("disconnect","Disconnect").enabled(connected)]).effect(effect).finish()}onklav_ui::app!(step);''')
 key=root/'fixture.key';sdk.packer('keygen',key);output=root/'accounts.onklav-a';sdk.build_project(p,key,output)
 out=HERE.parent/'docs/fixtures/accounts-wip.onklav-a';out.parent.mkdir(parents=True,exist_ok=True);out.write_bytes(output.read_bytes())
