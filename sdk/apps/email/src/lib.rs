use onklav_ui::{*,serde_json::Value};
fn step(input:Value)->Value{Screen::new("Email",input["state"].clone()).body(vec![label("email.workspace","Email")]).finish()}
onklav_ui::app!(step);
