use onklav_ui::serde_json::Value;
fn step(input:Value)->Value{onklav_ui::reference_apps::mail::controller_with_workflows(input,&onklav_ui::reference_apps::mail::Config{endpoint:"https://mail.example.org/jmap",provider:"mail",server_account:"pending",mailbox:"pending"},&onklav_ui::reference_apps::mail::Workflows{session_url:"https://mail.example.org/.well-known/jmap",drafts_mailbox:"",sent_mailbox:"",upload_url:"",download_url:""})}
onklav_ui::app!(step);
