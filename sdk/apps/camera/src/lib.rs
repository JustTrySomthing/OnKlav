use onklav_ui::{*,serde_json::Value};
fn step(input:Value)->Value{Screen::new("Camera",input["state"].clone()).body(vec![label("camera.workspace","Camera")]).finish()}
onklav_ui::app!(step);
