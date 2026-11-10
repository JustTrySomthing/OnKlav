"""Build a disposable signed fixture for integrated JNI/native-service checks."""
import importlib.util,tempfile,os,shutil
from pathlib import Path
HERE=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('native_sdk',HERE/'onklav.py');sdk=importlib.util.module_from_spec(spec);spec.loader.exec_module(sdk)
with tempfile.TemporaryDirectory(prefix='onklav-native-fixture-')as tmp:
 root=Path(tmp);project=root/'app';sdk.new_project(project,'org.example.nativebridge.checks2','Native bridge',capabilities=['camera.capture','notifications.post','storage.secrets'])
 (project/'src/lib.rs').write_text('''use onklav_ui::{Screen,button,label,camera,notifications,secrets,serde_json::{json,Value}};
fn step(v:Value)->Value{
 let mut s=v["state"].clone();if !s.is_object(){s=json!({"status":"Ready. Tap Store secret, then Read secret.","operation":""});}
 let e=&v["event"];let action=e["action"].as_str().unwrap_or("");
 let effect=match action{
 "camera"=>{s["operation"]=json!("camera");s["status"]=json!("Opening camera…");camera::capture("done")},
 "notice"=>{s["operation"]=json!("notice");s["status"]=json!("Sending notification…");notifications::show("demo","Native bridge","Notice","done")},
 "secret"=>{s["operation"]=json!("read");s["status"]=json!("Reading test secret…");secrets::get("token","done")},
 "store"=>{s["operation"]=json!("store");s["status"]=json!("Saving test secret…");secrets::set("token","private fixture","done")},
 "done"=>{let op=s["operation"].as_str().unwrap_or("");let r=&e["reply"];let message=if r["ok"]!=true{"Test failed. Try again and check permissions."}else{match op{
 "store"=>if r["value"]["stored"]==true{"Test secret saved. Tap Read secret next."}else{"Save was not confirmed. Try again."},
 "read"=>if r["value"]["value"]=="private fixture"{"Secure storage preview passed."}else if r["value"]["value"].is_null(){"No test secret saved yet."}else{"Secure storage preview failed: stored test value did not match. Tap Store secret and retry."},
 "camera"=>"Camera completed. Your test photo was captured.","notice"=>"Notification submitted. Check the notification shade.",_=>"Test completed."}};s["status"]=json!(message);Value::Null},_=>Value::Null};
 let status=s["status"].as_str().unwrap_or("Ready").to_owned();
 Screen::new("Native bridge",s).body(vec![label("instructions","Camera: take and accept a photo. Notify: check your notification shade. Tap Store secret, then Read secret. Look below for the result; the secret itself is never displayed."),button("camera","Camera"),button("notice","Notify"),button("secret","Read secret"),button("store","Store secret"),label("result",&status)]).effect(effect).finish()
}onklav_ui::app!(step);''')
 key=Path(os.environ['ONKLAV_FIXTURE_KEY']) if 'ONKLAV_FIXTURE_KEY' in os.environ else root/'fixture.key'
 if 'ONKLAV_FIXTURE_KEY' not in os.environ:sdk.packer('keygen',key)
 output=root/'NativeServices.onklav-a';sdk.build_project(project,key,output)
 shutil.copyfile(output,HERE/'tests/fixtures/NativeServices.onklav-a')
