use onklav_ui::{*,serde_json::{json,Value}};
fn step(input:Value)->Value {
 let mut s=input["state"].clone();if !s.is_object(){s=json!({"settings_epoch":0});}
 if input["event"]["action"]=="audiobooks.settings"{s["settings_epoch"]=json!(s["settings_epoch"].as_u64().unwrap_or(0)+1);}
 let mut library=media::player("audiobooks.library");library["audiobook"]=json!(true);library["settings_epoch"]=s["settings_epoch"].clone();
 Screen::new("AudioBooks",s).body(vec![library]).actions(vec![icon("audiobooks.settings","AudioBooks settings","settings")]).finish()
}
onklav_ui::app!(step);
