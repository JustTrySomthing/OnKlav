use onklav_ui::{reading_ui,serde_json::Value};
fn step(input:Value)->Value{reading_ui::reader("Text reader",input,"sample","sample-v1",&"alpha 😀\n".repeat(1500))}
onklav_ui::app!(step);
