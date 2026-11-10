use onklav_ui::{*,serde_json::{json,Value}};
fn step(input:Value)->Value{let m=email::Mailbox::new("https://mail.example.org/jmap","fixture-account","drafts","aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa").unwrap();email_compose::controller(input,&m,"fixture-identity",json!({"from":"fixture@example.org","to":["demo@example.org"],"subject":"Synthetic draft fixture","text":"No real provider or recipient is contacted by desktop tests.","attachments":[]}))}
onklav_ui::app!(step);
