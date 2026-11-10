use onklav_ui::{*,serde_json::Value};
fn step(input:Value)->Value{Screen::new("Maps",input["state"].clone()).body(vec![label("maps.workspace","Maps")]).finish()}
onklav_ui::app!(step);
