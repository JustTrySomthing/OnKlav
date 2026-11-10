//! Generated app uses the shared private editor; replace with app-specific behavior.
use onklav_ui::serde_json::Value;
fn step(input:Value)->Value{onklav_ui::apps::document(__APP_NAME__,input)}
onklav_ui::app!(step);
