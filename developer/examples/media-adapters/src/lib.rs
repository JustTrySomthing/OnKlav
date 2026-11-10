use onklav_ui::{*,serde_json::{json,Value}};
fn step(input:Value)->Value{let mut s=input["state"].clone();if !s.is_object(){s=json!({"status":"Synthetic fixtures; no provider contacted"});}let e=&input["event"];let a=e["action"].as_str().unwrap_or("");let mut request=Value::Null;match a{
"demo.podcast"=>request=podcasts::parse(&json!({"status":200,"text":"<rss version='2.0'><channel><title>Demo podcast</title><item><title>Demo episode</title><enclosure url='https://audio.example.org/episode.mp3' type='audio/mpeg' length='123'/></item></channel></rss>"}),"podcast.parsed").unwrap(),
"podcast.parsed" if e["reply"]["ok"]==true=>{s["podcast"]=e["reply"]["value"].clone();s["status"]=json!("Podcast fixture parsed");},
"podcast.parsed"=>s["status"]=json!("Podcast access off"),
"demo.radio"=>{s["radio"]=radio::metadata(&json!({"status":200,"text":"{\"icestats\":{\"source\":{\"listenurl\":\"https://audio.example.org/live\",\"title\":\"Demo song\"}}}"}),"https://audio.example.org/live").unwrap();s["status"]=json!("Radio fixture parsed");},
"demo.play"=>if s["podcast"]["episodes"][0].is_object(){request=podcasts::play(&s["podcast"]["episodes"][0],"played").unwrap()},_=>{}}
let status=s["status"].as_str().unwrap_or("").to_string();let song=s["radio"]["title"].as_str().unwrap_or("No station metadata loaded").to_string();let mut f=Screen::new("Media adapters",s).body(vec![label("notice","Synthetic fixtures; playback needs Android"),label("status",&status),label("song",&song),button("demo.podcast","Parse podcast fixture"),button("demo.radio","Parse radio fixture"),button("demo.play","Play selected demo episode")]).finish();if !request.is_null(){f["effect"]=request;}f}
onklav_ui::app!(step);
