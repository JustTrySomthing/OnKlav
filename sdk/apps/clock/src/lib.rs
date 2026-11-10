use onklav_ui::{*,serde_json::Value};
fn step(input:Value)->Value{Screen::new("Clock",input["state"].clone()).body(vec![label("clock.workspace","Clock")]).finish()}
onklav_ui::app!(step);
