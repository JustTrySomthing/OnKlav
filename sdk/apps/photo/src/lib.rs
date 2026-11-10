use onklav_ui::{*,serde_json::Value};
fn step(input:Value)->Value{Screen::new("Image Editor",input["state"].clone()).body(vec![label("photo.workspace","Image editor")]).finish()}
onklav_ui::app!(step);
