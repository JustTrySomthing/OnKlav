use onklav_ui::serde_json::Value;
fn step(input:Value)->Value{onklav_ui::reference_apps::weather_app::controller(input)}
onklav_ui::app!(step);
