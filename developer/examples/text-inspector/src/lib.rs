//! Domain logic only: the SDK owns selection, import, bounded reading and dialogs.
use onklav_ui::{Screen,files,label,plain,serde_json::{json,Value}};
fn inspect(input:Value)->Value{
 let mut state=input["state"].clone();if !state.is_object(){state=json!({"name":"","characters":0,"words":0,"lines":0,"preview":"","status":"Choose Open Files to inspect a text file."});}
 let event=&input["event"];
 if event["action"]=="file.text.loaded"{
  let file=&event["value"];let text=file["text"].as_str().unwrap_or("");let display=files::text_for_editor(file);
  state=json!({"name":file["name"],"characters":text.chars().count(),"words":text.split_whitespace().count(),"lines":display.lines().count(),"preview":plain(&display,1000),"status":"Loaded. The original file is unchanged."});
 }else if event["action"]=="file.text.error"{state["status"]=json!("Could not load this file. The previous result is kept.");}
 Screen::new("Text inspector",state.clone()).scrollable().body(vec![
  label("status",state["status"].as_str().unwrap_or("")),label("name",state["name"].as_str().unwrap_or("")),
  label("counts",&format!("{} characters · {} words · {} lines",state["characters"],state["words"],state["lines"])),label("preview",state["preview"].as_str().unwrap_or("")),
 ]).finish()
}
fn step(input:Value)->Value{files::with_text_picker("Text inspector",input,inspect)}
onklav_ui::app!(step);
