//! RSS2 enclosure adapter; fetching and playback remain signed broker operations.
use crate::*;
pub fn fetch(url:&str,then:&str)->Value{http::get(url,then)}
pub fn parse(response:&Value,then:&str)->Result<Value,String>{if response["status"]!=200{return Err("Podcast feed request failed".into())}let xml=response["text"].as_str().filter(|s|s.len()<=131072).ok_or("Missing bounded podcast XML")?;Ok(effect("podcasts.parse",json!({"xml":xml}),then))}
pub fn play(episode:&Value,then:&str)->Result<Value,String>{let url=episode["url"].as_str().ok_or("Choose an episode")?;let title=episode["title"].as_str().ok_or("Missing episode title")?;Ok(media::play(vec![media::stream(url,title)],then))}
/// Search the host's bundled public directory without a remote directory API.
pub fn search(query:&str,language:&str,offset:u64,then:&str)->Value{effect("podcasts.search",json!({"query":query,"language":language,"offset":offset}),then)}
/// Public HTTPS RSS2 episode page; audio URLs remain private to the host broker.
pub fn episodes(feed:&str,offset:u64,then:&str)->Value{effect("podcasts.feed",json!({"feed":feed,"offset":offset}),then)}
/// Play only an episode ID returned by that feed. Requires a direct tap.
pub fn episode_play(feed:&str,id:&str,then:&str)->Value{effect("podcasts.play",json!({"feed":feed,"episode":id}),then)}
