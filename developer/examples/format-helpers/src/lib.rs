use onklav_ui::{Screen,button,label,calendar,playlists,reading,serde_json::{json,Value}};
fn step(input:Value)->Value{
 let mut state=input["state"].clone();if !state.is_object(){state=json!({"status":"Tap Check helpers. No network or device service will be used."});}
 if input["event"]["action"]=="check"{
  let result=(||->Result<Value,String>{
   let streams=playlists::parse("#EXTM3U\n#EXTINF:-1,Example station\nhttps://radio.example.com/live")?;
   let page=reading::page("Hello world",0,5)?;let bookmark=reading::bookmark("sample","revision1",page.end)?;
   let ics=calendar::export_utc(&[json!({"uid":"example@onklav","start":"20261025T100000Z","end":"20261025T110000Z","summary":"Example event"})],"20261025T010000Z")?;
   Ok(json!({"streams":streams.len(),"page":page.text,"resumed":reading::resume(&bookmark,"sample","revision1","Hello world"),"calendar":ics.contains("BEGIN:VEVENT"),"status":"Local format helpers passed. Streaming, scheduling and EPUB/PDF services are not implemented by this example."}))
  })();match result{Ok(v)=>state=v,Err(e)=>state=json!({"status":e})}
 }
 Screen::new("Format helpers",state.clone()).body(vec![label("status",state["status"].as_str().unwrap_or("Ready")),button("check","Check helpers")]).finish()
}
onklav_ui::app!(step);
