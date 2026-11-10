use onklav_ui::{*,serde_json::Value};
fn step(input:Value)->Value{Screen::new("Audio Editor",input["state"].clone()).body(vec![label("audio.workspace","Audio Editor")]).finish()}
onklav_ui::app!(step);
