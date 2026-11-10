use onklav_ui::serde_json::Value;
fn step(input:Value)->Value{onklav_ui::collection::documents("Document list",input)}
onklav_ui::app!(step);
