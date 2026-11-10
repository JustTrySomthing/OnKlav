use onklav_ui::{apps,files,serde_json::Value};
fn notebook(input:Value)->Value{apps::document("Media notebook",input)}
fn step(input:Value)->Value{files::with_viewer("Media notebook",input,notebook)}
onklav_ui::app!(step);
