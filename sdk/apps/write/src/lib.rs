use onklav_ui::{*,serde_json::Value};
fn step(input:Value)->Value{Screen::new("Write",input["state"].clone()).body(vec![label("write.workspace","Word processor")]).finish()}
onklav_ui::app!(step);
