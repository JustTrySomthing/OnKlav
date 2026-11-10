"""Disposable independent-author packages for native host containment tests."""
import importlib.util,json,tempfile,shutil,struct,zlib
from pathlib import Path
HERE=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('sdk',HERE/'onklav.py');sdk=importlib.util.module_from_spec(spec);spec.loader.exec_module(sdk)
with tempfile.TemporaryDirectory(prefix='onklav-host-fixture-') as root:
 root=Path(root);key=root/'author.key';sdk.packer('keygen',key);p=root/'fixture'
 def chunk(kind,data):return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data))
 png=root/'icon.png';png.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',1,1,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(b'\x00\x00\x00\xff\xff'))+chunk(b'IEND',b''))
 sdk.new_project(p,'org.example.hostfixture','External Fixture',icon=str(png));m=json.loads((p/'manifest.json').read_text());m['capabilities']=[];(p/'manifest.json').write_text(json.dumps(m))
 (p/'src/lib.rs').write_text('''use onklav_ui::{Screen,label,icon,toggle,button,field,record,NodeExt,serde_json::{json,Value}};
fn step(input:Value)->Value{let mut s=input["state"].clone();if !s.is_object(){s=json!({"menu":false,"drawer":false,"secondary":false});}if input["event"]["action"]=="action0"{s["menu"]=json!(true);}if input["event"]["action"]=="action1"{s["drawer"]=json!(true);}if input["event"]["action"]=="secondary"{s["secondary"]=json!(true);}let mut f=Screen::new("External Fixture",s.clone()).body(vec![label("body",if s["secondary"]==true{"Secondary action ran"}else{"External body"}),toggle("disabled-toggle","Unavailable toggle",false).enabled(false),button("disabled-button","Unavailable button").enabled(false),field("disabled-field","Unavailable field","").enabled(false),record("disabled-record","Unavailable record","").enabled(false)]).actions((0..80).map(|n|{let node=icon(&format!("action{n}"),&format!("Action {n}"),"add");if n==2{node.long_action("secondary","Secondary action")}else{node}}).collect()).back("ignored.back");if s["menu"]==true{f=f.dialog("Guest modal","ignored.menu",vec![label("message","Guest ignores cancel")]);}if s["drawer"]==true{f=f.drawer("Guest drawer","ignored.drawer",vec![label("drawer.body","Guest ignores close")]);}let mut f=f.finish();f["presentation"]=json!("panel");f["hide_header_landscape"]=json!(true);f}
onklav_ui::app!(step);
''')
 out=HERE/'tests/fixtures';out.mkdir(exist_ok=True);shutil.copy2(sdk.build_project(p,key,None)['package'],out/'HostContainment.onklav-a')
 m['id']='app.onklav.untrusted';(root/'reserved.json').write_text(json.dumps(m));sdk.packer('pack',root/'reserved.json',p/'target/wasm32-unknown-unknown/release/onklav_starter_app.wasm',key,root/'ReservedNamespace.onklav-a');shutil.copy2(root/'ReservedNamespace.onklav-a',out/'ReservedNamespace.onklav-a')
 m['id']='org.example.future';m['min_host']=sdk.HOST_BUILD+1;(root/'future.json').write_text(json.dumps(m));sdk.packer('pack',root/'future.json',p/'target/wasm32-unknown-unknown/release/onklav_starter_app.wasm',key,root/'FutureHost.onklav-a');shutil.copy2(root/'FutureHost.onklav-a',out/'FutureHost.onklav-a')

with tempfile.TemporaryDirectory(prefix='onklav-form-fixture-') as root:
 root=Path(root);key=root/'author.key';sdk.packer('keygen',key);p=root/'form';sdk.new_project(p,'org.example.formfixture','Form Fixture','form')
 (p/'src/lib.rs').write_text('use onklav_ui::{forms::{form,FormField},serde_json::Value};\nfn step(input:Value)->Value{form("Form Fixture",input,&(0..18).map(|n|FormField::text(&format!("field{n}"),&format!("Field {n}"))).collect::<Vec<_>>())}\nonklav_ui::app!(step);\n')
 out=HERE/'tests/fixtures';shutil.copy2(sdk.build_project(p,key,None)['package'],out/'FormLayout.onklav-a')
