//! Included app delegates to the public shared SDK controller.
use onklav_ui::serde_json::Value;
fn step(input:Value)->Value{onklav_ui::reference_apps::rss::controller("RSS Reader",input)}
onklav_ui::app!(step);
