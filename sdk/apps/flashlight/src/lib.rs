use onklav_ui::{*,serde_json::Value};
fn step(input:Value)->Value{Screen::new("Flashlight",input["state"].clone()).body(vec![label("flashlight.workspace","Flashlight")]).finish()}
onklav_ui::app!(step);
