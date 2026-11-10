//! Walkie SDK controller. Audio and carrier operations are mediated by the host broker.
use crate::{effect,frame,node,plain,serde_json::{json,Value}};
fn toggle(id:&str,text:&str,checked:&Value)->Value {json!({"kind":"toggle","id":id,"text":text,"checked":checked})}
fn live_status(v:&Value)->Option<&'static str>{match v["phase"].as_str().unwrap_or(""){
 "probe_queued"|"probe_sending"|"checking"=>Some("Verifying contact…"),
 "invite_queued"|"invite_sending"|"inviting"=>Some("Waiting for reply…"),
 "accept_queued"|"accept_sending"=>Some("Joining…"),"incoming"=>Some("Incoming Walkie Talkie invitation"),
 "decline_queued"|"decline_sending"=>Some("Declining invitation…"),
 "connected"=>Some(if v["can_record"]==true{"Ready to talk"}else{"Connected"}),_=>None
}}
fn step(input:Value)->Value {
    let mut s=input["state"].clone();
    if !s.is_object(){s=json!({"meta":null,"peer":"","status":"Opening…","settings":false,"history":false});}
    let e=&input["event"];let a=e["action"].as_str().unwrap_or("");let mut request=Value::Null;
    match a {
        "session.changed" => request=effect("walkie.state",json!({}),"session.loaded"),
        "session.loaded" if e["reply"]["ok"]==true => {let current=&e["reply"]["value"]["session"];if s["meta"]["session"]!=*current {s["meta"]["session"]=current.clone();s["status"]=json!(live_status(current).unwrap_or("Offline"));}},
        "start"|"retry" => request=effect("walkie.state",json!({}),"loaded"),
        "loaded" if e["reply"]["ok"]==true => {
            if s["quit_pending"]==true {s["quit"]=json!(true);}
            s["meta"]=e["reply"]["value"].clone();s["status"]=json!(match s["meta"]["session"]["reason"].as_str().unwrap_or("") {
                "interrupted"|"clock_changed"=>"Connection interrupted — reconnect when available",
                "timeout"=>"Connection timed out", "idle_timeout"=>"Connection ended after inactivity",
                "uncertain"=>"Invitation outcome unconfirmed", "failed"=>"Invitation failed",
                "declined"=>"Invitation declined", _=>"Offline"
            });
            if let Some(status)=live_status(&s["meta"]["session"]){s["status"]=json!(status);}
            let selected=s["meta"]["selected"].clone();
            s["label"]=s["meta"]["recent"].as_array().and_then(|rows|rows.iter().find(|r|r["id"]==selected)).map(|r|r["label"].clone()).unwrap_or(json!(""));
            s["peer"]=s["meta"]["recent"].as_array().and_then(|rows|rows.iter().find(|r|r["id"]==selected)).map(|r|r["peer"].clone()).unwrap_or(json!(""));
        },
        "peer" => {s["peer"]=json!(plain(e["value"].as_str().unwrap_or(""),64));s["label"]=json!("");},
        "label" => s["label"]=json!(plain(e["value"].as_str().unwrap_or(""),120)),
        "contacts" => request=effect("contacts.pick",json!({}),"contact.picked"),
        "contact.picked" if e["reply"]["ok"]==true => {
            let contact=&e["reply"]["value"];
            request=effect("walkie.prepare",json!({"peer":contact["phone"],"label":contact["name"]}),"loaded");
        },
        "record.toggle" => s["record_panel"]=json!(s["record_panel"]!=true),
        "prepare" => request=effect("walkie.prepare",json!({"peer":s["peer"],"label":s["label"]}),"loaded"),
        "capture.saved" => s["status"]=json!(if e["value"]["sending_requested"]==true||s["meta"]["session"]["phase"]=="connected"{"Voice clip queued for sending"}else{"Voice clip saved locally."}),
        "connect"|"connection.retry" => request=effect("communications.walkie",json!({"operation":"begin"}),"connection"),
        "connection" => {if e["reply"]["ok"]==true{s["meta"]["session"]=e["reply"]["value"].clone();s["connection_open"]=json!(false);s["status"]=json!(live_status(&e["reply"]["value"]).unwrap_or("Offline"));}else{s["connection_open"]=json!(true);}},
        "invite.response" if e["reply"]["ok"]==true => request=effect("walkie.state",json!({}),"loaded"),
        "connection.close" => s["connection_open"]=json!(false),
        "invite.accept" => {let invite=&s["meta"]["session"];request=effect("communications.walkie",json!({"operation":"accept","id":invite["id"]}),"invite.response");},
        "invite.decline" => request=effect("walkie.decline",json!({"id":s["meta"]["session"]["id"]}),"loaded"),
        "end" => {s["quit_pending"]=json!(true);request=effect("walkie.end",json!({}),"loaded");},
        "settings" => {s["archive"]=json!(false);s["history"]=json!(false);s["draft"]=s["meta"]["preferences"].clone();s["settings"]=json!(true);},
        "settings.close" => s["settings"]=json!(false),
        "settings.save" => {request=effect("walkie.preferences",s["draft"].clone(),"settings.saved");},
        "preferences.saved" if e["reply"]["ok"]==true => {s["meta"]=e["reply"]["value"].clone();s["status"]=json!("Settings saved");},
        "settings.saved" if e["reply"]["ok"]==true => {s["meta"]=e["reply"]["value"].clone();s["settings"]=json!(false);s["status"]=json!("Settings saved");},
        "clips" => {s["clip_offset"]=json!(0);s["settings"]=json!(false);s["history"]=json!(false);s["archive"]=json!(true);request=effect("walkie.clips",json!({}),"clips.loaded");},
        "clip.retry.response" if e["reply"]["ok"]==true => {s["status"]=json!("Voice clip queued for retry");request=effect("walkie.clips",json!({}),"clips.played.loaded");},
        _ if a.starts_with("clip.retry.") => request=effect("communications.walkie",json!({"operation":"retry","clip":&a[11..]}),"clip.retry.response"),
        "clip.played" => request=effect("walkie.clips",json!({}),"clips.played.loaded"),
        "clips.played.loaded" if e["reply"]["ok"]==true => s["clips"]=e["reply"]["value"].clone(),
        "clips.refresh" => request=effect("walkie.clips",json!({}),"clips.loaded"),
        "clips.next" => {let n=s["clip_offset"].as_u64().unwrap_or(0);s["clip_offset"]=json!(n+6);},
        "clips.previous" => {let n=s["clip_offset"].as_u64().unwrap_or(0);s["clip_offset"]=json!(n.saturating_sub(6));},
        "clips.close" => s["archive"]=json!(false),
        "clips.loaded" if e["reply"]["ok"]==true => {s["clips"]=e["reply"]["value"].clone();s["clip_offset"]=json!(0);},
        "ready.delete" => s["delete_clip"]=s["meta"]["session"]["unheard"].clone(),
        "clips.clear" => s["clip_clear"]=json!(true),
        "clips.cancel" => {s["clip_clear"]=json!(false);s["delete_clip"]=Value::Null;},
        "clips.accept" => {s["clip_clear"]=json!(false);let id=s["delete_clip"].take();request=if id.is_string(){effect("walkie.clip.delete",json!({"id":id}),"clips.loaded")}else{effect("walkie.clips.clear",json!({}),"clips.loaded")};},
        _ if a.starts_with("clip.delete.") => s["delete_clip"]=json!(&a[12..]),
        "history" => {s["archive"]=json!(false);s["settings"]=json!(false);s["history"]=json!(true);}, "history.close" => s["history"]=json!(false),
        "history.clear" => s["clear"]=json!(true),"clear.cancel" => s["clear"]=json!(false),
        "clear.accept" => {s["clear"]=json!(false);request=effect("walkie.clear_recent",json!({}),"loaded");},
        _ if a.starts_with("settings.") => {let key=&a[9..];if ["auto_play","auto_send","beeps"].contains(&key){s["draft"][key]=json!(e["value"]=="true");}},
        _ if a.starts_with("quick.") => {let key=&a[6..];if ["auto_play","auto_send"].contains(&key){let mut p=s["meta"]["preferences"].clone();p[key]=json!(e["value"]=="true");request=effect("walkie.preferences",p,"preferences.saved");}},
        _ if a.starts_with("recent.") => {s["history"]=json!(false);request=effect("walkie.select",json!({"id":&a[7..]}),"loaded");},
        _ => {}
    }
    if e["reply"]["ok"]==false{if a=="clips.loaded"{s["archive"]=json!(false);}s["status"]=json!(e["reply"]["error"].as_str().unwrap_or("Could not complete action"));}
    let ready=s["meta"].is_object();let selected=!s["meta"]["selected"].is_null();
    let mut body=vec![];
    if selected {
        let label=s["label"].as_str().filter(|v|!v.is_empty());
        let mut name=node("label","recipient.name",label.unwrap_or(s["peer"].as_str().unwrap_or("Recipient")));name["font"]=json!(20);name["strong"]=json!(true);name["center"]=json!(true);body.push(name);
    }
    if !selected {body.push(node("button","contacts","Choose Contact"));body.push(json!({"kind":"field","id":"peer","value":s["peer"],"hint":"Recipient phone number","limit":64}));body.push(node("button","prepare","Save recipient"));}

    let live=s["meta"]["session"]["phase"]=="connected";
    if !live{body.push(json!({"kind":"voice_dial","id":"dial","phase":"offline","enabled":false}));}
    if s["status"]!="Offline" {body.push(json!({"kind":"label","id":"status","text":s["status"],"font":16,"center":true}));}
    if live {body.push(node("label","availability","Hold the dial to talk. Release to send."));} else if !selected {body.push(node("label","availability","Choose a contact to start."));}
    if selected && !live {let phase=s["meta"]["session"]["phase"].as_str().unwrap_or("");body.push(json!({"kind":"button","id":"connect","text":"Click To Verify User","primary":true,"tone":"positive","enabled":ready && live_status(&s["meta"]["session"]).is_none() && phase!="incoming"}));}
    if selected {if live || s["record_panel"]==true {body.push(json!({"kind":"voice_capture","id":"capture","peer":s["peer"],"live":live,"on_change":"capture.saved"}));}let mut quit=node("button","end","Quit");quit["center"]=json!(true);body.push(quit);}
    if let Some(clip)=s["meta"]["session"]["unheard"].as_str().filter(|id|!id.is_empty()){
        body.push(json!({"kind":"voice_clip","id":"ready.clip","clip":clip,"text":"Voice message ready","detail":"Listen to your voice message","on_change":"clip.played","delete":"ready.delete","attention":true,"autoplay":true}));
    }
    if ready {for (id,text) in [("auto_play","Auto Play"),("auto_send","Auto Send")] {let mut t=toggle(&format!("quick.{id}"),text,&s["meta"]["preferences"][id]);t["row"]=json!("quick");t["neutral"]=json!(true);if id=="auto_send"{t["align"]=json!("end");}body.push(t);}}
    if !ready {body.push(node("button","retry","Retry open"));}
    let mut settings=node("button","settings","Walkie Talkie Settings");settings["icon"]=json!("settings");
    let mut history=node("button","history","Recent Recipients");history["icon"]=json!("history");
    let mut actions=vec![history,node("button","clips","Voice Clips"),settings];
    if selected && !live {actions.insert(0,node("button","record.toggle",if s["record_panel"]==true{"Hide voice message controls"}else{"Record voice message"}));}
    if selected {let mut contact=node("button","contacts","Change Contact");contact["icon"]=json!("contacts");actions.insert(0,contact);}
    let mut f=frame("Walkie-Talkie",s.clone(),if ready{actions}else{vec![]},body);
    f["presentation"]=json!("panel");f["close_requested"]=s["quit"].clone();
    f["effect"]=request;f["menu_open"]=s["settings"].clone();f["menu_title"]=json!("Walkie Talkie");f["menu_close"]=json!("settings.close");
    if s["settings"]==true {f["menu"]=json!([
        toggle("settings.auto_play","Auto Play",&s["draft"]["auto_play"]),
        toggle("settings.auto_send","Auto Send",&s["draft"]["auto_send"]),
        toggle("settings.beeps","Beeps",&s["draft"]["beeps"]),
        node("label","settings.help","Recording stops after 12 seconds. Review and queue a recording locally, or discard it. Leaving discards unsent drafts. Auto Send and Auto Play apply to a connected chat; Carrier testing must be enabled in Setup before connecting. Played clips expire after 24 hours, unheard clips after 7 days (100 clips maximum)."),
        node("button","settings.save","Save"),node("button","settings.close","Close")]);}
    if s["connection_open"]==true {
        f["menu_open"]=json!(true);f["menu_title"]=json!("Walkie Talkie");f["menu_close"]=json!("connection.close");
        let mut recipient=node("label","connection.peer",s["label"].as_str().filter(|name|!name.trim().is_empty()).unwrap_or(s["peer"].as_str().unwrap_or("")));recipient["font"]=json!(14);recipient["center"]=json!(true);
        let mut status=node("label","connection.status","Connection unavailable");status["font"]=json!(18);status["center"]=json!(true);
        let mut cancel=node("button","connection.close","Cancel");cancel["row"]=json!("connection.buttons");
        let mut retry=node("button","connection.retry","Try Again");retry["row"]=json!("connection.buttons");
        f["menu"]=json!([recipient,status,node("label","connection.detail",s["status"].as_str().unwrap_or("No invitation was sent.")),cancel,retry]);
    }
    if s["meta"]["session"]["phase"]=="incoming" {
        f["menu_open"]=json!(true);f["menu_title"]=json!("Walkie Talkie Invitation");f["menu_close"]=json!("invite.decline");
        let peer=json!({"kind":"label","id":"invite.peer","text":s["meta"]["session"]["peer"],"font":14,"center":true});
        let status=json!({"kind":"label","id":"invite.status","text":s["status"],"font":18,"center":true});
        let mut decline=node("button","invite.decline","Decline");decline["row"]=json!("invite.buttons");
        let mut accept=node("button","invite.accept","Accept");accept["row"]=json!("invite.buttons");
        f["menu"]=json!([peer,status,node("label","invite.help","Join this Walkie Talkie session? Microphone access remains a separate permission."),decline,accept]);
    }
    f["drawer_open"]=s["history"].clone();f["drawer_title"]=json!("Recent Recipients");f["drawer_close"]=json!("history.close");
    if s["history"]==true {let mut rows=vec![node("label","history.help","Saved recipients only. These are not connected calls or delivered messages.")];
        if let Some(recent)=s["meta"]["recent"].as_array(){for r in recent.iter().take(80){rows.push(node("button",&format!("recent.{}",r["id"].as_str().unwrap_or("")),&match r["label"].as_str().filter(|v|!v.is_empty()){Some(name)=>format!("{} · {}",name,r["peer"].as_str().unwrap_or("")),None=>r["peer"].as_str().unwrap_or("Recipient").to_owned()}));}}
        rows.push(node("button","history.clear","Clear Recent Recipients"));f["drawer"]=json!(rows);
    }
    if s["archive"]==true {
        f["drawer_open"]=json!(true);f["drawer_title"]=json!("Voice Clips");f["drawer_close"]=json!("clips.close");
        let all=s["clips"].as_array();let offset=s["clip_offset"].as_u64().unwrap_or(0) as usize;
        let mut rows=vec![node("label","clips.help","Saved voice clips. Listening marks incoming clips heard on this device. Delivery depends on your carrier and the other phone.")];
        if let Some(clips)=all {for c in clips.iter().skip(offset).take(6) {let status=match c["state"].as_str().unwrap_or(""){"queued"=>"Queued locally","sending"=>"Sending","carrier"=>"Accepted by carrier","delivered"=>"Delivered","heard"=>"Heard","failed"=>"Failed","uncertain"=>"Unconfirmed — retry could duplicate","received"=>"Received",_=>"Unknown"};
            let mut row=node("voice_clip",&format!("clip.{}",c["id"].as_str().unwrap_or("")),c["peer"].as_str().unwrap_or("Recipient"));
            row["clip"]=c["id"].clone();row["on_change"]=json!("clip.played");row["detail"]=json!(format!("{} · {:.1} seconds",status,c["millis"].as_u64().unwrap_or(0) as f64/1000.));
            row["delete"]=json!(format!("clip.delete.{}",c["id"].as_str().unwrap_or("")));if c["outgoing"]==true&&["failed","uncertain"].contains(&c["state"].as_str().unwrap_or("")){row["retry"]=json!(format!("clip.retry.{}",c["id"].as_str().unwrap_or("")));}rows.push(row);
        }}
        if all.is_none_or(|v|v.is_empty()){rows.push(node("label","clips.empty","No saved voice clips"));}
        if offset>0{rows.push(node("button","clips.previous","Previous"));}
        if all.is_some_and(|v|offset+6<v.len()){rows.push(node("button","clips.next","Next"));}
        rows.push(node("button","clips.refresh","Refresh"));rows.push(node("button","clips.clear","Clear Voice Clips"));f["drawer"]=json!(rows);
    }
    if s["clip_clear"]==true||s["delete_clip"].is_string(){f["confirm"]=json!({"title":"Delete voice clips?","text":"Remove saved audio from OnKlav on this device. Duplicate protection is retained; Android messages are unchanged.","accept":"clips.accept","accept_label":"Delete","cancel":"clips.cancel"});}
    if s["clear"]==true {f["confirm"]=json!({"title":"Clear recent recipients?","text":"Remove saved Walkie recipients on this device. Messages and contacts are unchanged.","accept":"clear.accept","accept_label":"Clear","cancel":"clear.cancel"});}
    f
}


/// Shared controller using the included app behavior and the caller's title.
pub fn controller(title:&str,input:Value)->Value{let mut f=step(input);f["title"]=crate::serde_json::json!(title);f}
