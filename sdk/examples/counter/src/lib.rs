//! Developer-only programmable example. Not added to the user's default Apps page.
use onklav_app::{frame,node,serde_json::{json,Value}};
fn step(input:Value)->Value{let mut count=input["state"]["count"].as_i64().unwrap_or(0);match input["event"]["action"].as_str(){Some("add")=>count=count.saturating_add(1),Some("subtract")=>count=count.saturating_sub(1),_=>{}}frame("SDK Counter",json!({"count":count}),vec![],vec![node("label","count",&count.to_string()),node("button","add","Add One"),node("button","subtract","Subtract One")])}
onklav_app::app!(step);
