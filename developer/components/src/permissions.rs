//! Shared denial guidance; grants remain under host/user control.
use crate::*;
const STATE:&str="sdk.permission.recovery";
const DISMISS:&str="sdk.permission.dismiss";
pub fn capability_label(cap:&str)->Option<&'static str>{Some(match cap{
 "ebooks.library"=>"Free ebooks and Project Gutenberg catalog",
 "audiobooks.library"=>"Personal audiobooks and free LibriVox books",
 "podcasts.library"=>"Podcasts directory and public feeds",
"radio.stations"=>"Radio stations","background.tasks"=>"Background jobs and downloads","accounts.connect"=>"Connected accounts","network.http"=>"HTTPS data requests","camera.capture"=>"Camera capture","notifications.post"=>"App notifications","storage.secrets"=>"App secrets","storage.private"=>"Private documents","clipboard.read"=>"Paste from clipboard","clipboard.write"=>"Copy to clipboard","files.export"=>"File export","share.text"=>"Text sharing","network.feeds"=>"Feed downloads","network.web"=>"Web browsing","speech.output"=>"Text to speech","links.external"=>"External links","network.thumbnails"=>"Thumbnail downloads","files.images"=>"Image selection","images.edit"=>"Image editing","audio.record"=>"Microphone","audio.playback"=>"Audio playback","contacts.pick"=>"Contact selection","communications.walkie"=>"Walkie invitations","files.manage"=>"OnKlav files","files.external"=>"Android files",_=>return None})}
/// Keep the app's original state shape, drafts and error handling. The shared
/// dialog dismisses locally; it never retries an action or changes a grant.
pub fn with_recovery(mut input:Value,app:fn(Value)->Value)->Value{
 let mut recovery=input["state"][STATE].clone();
 if let Some(state)=input["state"].as_object_mut(){state.remove(STATE);}
 let dismiss=input["event"]["action"]==DISMISS;
 if dismiss{recovery=Value::Null;input["event"]=json!({"action":"sdk.render"});}
 let access=&input["event"]["reply"]["access"];
 if input["event"]["reply"]["ok"]==false&&capability_label(access["capability"].as_str().unwrap_or("")).is_some()&&access["declared"].is_boolean(){recovery=access.clone();}
 let mut frame=app(input);
 if !frame["state"].is_object(){return frame}
 if recovery.is_object(){
  frame["state"][STATE]=recovery.clone();let name=capability_label(recovery["capability"].as_str().unwrap_or("")).unwrap_or("Requested access");
  let count:usize=["body","actions","drawer"].iter().map(|key|frame[key].as_array().map_or(0,Vec::len)).sum();
  if count>98{for key in ["body","actions","drawer"]{frame[key]=json!([]);}}
  let message=if recovery["declared"]==true{format!("{name} access is off. Open Settings > Apps, select this app, and review its permissions. After changing permissions, reopen the app. No blocked action was retried.")}else{format!("This app does not declare {name} access. The app developer must add the required capability. Changing Android permissions will not fix this.")};
  frame["menu_open"]=json!(true);frame["menu_title"]=json!("Permission needed");frame["menu_close"]=json!(DISMISS);frame["menu"]=json!([label("sdk.permission.message",&message),button(DISMISS,"Keep working")]);
 }
 frame
}

#[cfg(test)]mod tests{
 use super::*;
 fn app(input:Value)->Value{Screen::new("Test",if input["state"].is_object(){input["state"].clone()}else{json!({"draft":"keep"})}).finish()}
 #[test]fn denied_recovery_keeps_draft_and_dismisses_without_a_service(){let f=with_recovery(json!({"state":{"draft":"unsaved"},"event":{"reply":{"ok":false,"access":{"capability":"share.text","declared":true}}}}),app);assert_eq!(f["state"]["draft"],"unsaved");assert_eq!(f["menu_open"],true);assert!(f["effect"].is_null());let done=with_recovery(json!({"state":f["state"],"event":{"action":DISMISS}}),app);assert_eq!(done["state"]["draft"],"unsaved");assert!(done["state"].get(STATE).is_none());assert_ne!(done["menu_open"],true);assert!(done["effect"].is_null());}
 #[test]fn undeclared_access_identifies_an_app_configuration_problem(){let f=with_recovery(json!({"event":{"reply":{"ok":false,"access":{"capability":"files.manage","declared":false}}}}),app);assert!(f["menu"][0]["text"].as_str().unwrap().contains("app developer"));assert!(capability_label("android.context").is_none());let f=with_recovery(json!({"event":{"reply":{"ok":false,"error":"access is off"}}}),app);assert_ne!(f["menu_open"],true);}
}
