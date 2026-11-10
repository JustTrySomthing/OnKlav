use onklav_ui::{*,serde_json::Value};
fn step(input:Value)->Value{Screen::new("Video Editor",input["state"].clone()).body(vec![label("video.workspace","Video editor")]).finish()}
onklav_ui::app!(step);
