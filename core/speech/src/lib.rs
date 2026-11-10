//! Shared offline speech engine. Original native pronunciation frontend is intentional.
mod ring;mod audio;
use jni::{JNIEnv,objects::{JClass,JString,JFloatArray},sys::{jlong,jfloatArray,jint,jfloat}};
use sherpa_onnx::{OfflineTts,OfflineTtsConfig,OfflineTtsModelConfig,OfflineTtsKittenModelConfig,GenerationConfig};
use std::{path::Path,sync::{Mutex,OnceLock,atomic::Ordering},ptr};
static MODEL:OnceLock<Mutex<Option<OfflineTts>>>=OnceLock::new();
fn model()->&'static Mutex<Option<OfflineTts>>{MODEL.get_or_init(||Mutex::new(None))}
fn failure(env:&mut JNIEnv,message:impl AsRef<str>){let _=env.throw_new("java/lang/IllegalStateException",message.as_ref());}
#[no_mangle]pub extern "system" fn Java_app_onklav_home_NeuralSpeechNative_initialize(mut env:JNIEnv,_:JClass,path:JString){
 let result=(||->Result<(),String>{let path:String=env.get_string(&path).map_err(|e|e.to_string())?.into();let mut slot=model().lock().map_err(|_|"Speech model lock failed")?;if slot.is_some(){return Ok(())}
 let file=|n:&str|Some(Path::new(&path).join(n).to_string_lossy().into_owned());
 let config=OfflineTtsConfig{model:OfflineTtsModelConfig{kitten:OfflineTtsKittenModelConfig{model:file("model.fp32.onnx"),voices:file("voices.bin"),tokens:file("tokens.txt"),data_dir:file("espeak-ng-data"),..Default::default()},num_threads:2,..Default::default()},max_num_sentences:1,silence_scale:0.2,..Default::default()};
 *slot=Some(OfflineTts::create(&config).ok_or("Could not prepare included English voices")?);Ok(())})();if let Err(e)=result{failure(&mut env,e)}
}
#[no_mangle]pub extern "system" fn Java_app_onklav_home_NeuralSpeechNative_synthesize(mut env:JNIEnv,_:JClass,text:JString,sid:jint,speed:jfloat)->jfloatArray{
 let result=(||->Result<jfloatArray,String>{let text:String=env.get_string(&text).map_err(|e|e.to_string())?.into();if text.is_empty()||text.chars().count()>500||text.contains('\0')||![0,2].contains(&sid)||!speed.is_finite()||!(0.75..=1.5).contains(&speed){return Err("Invalid speech request".into())}
 let slot=model().lock().map_err(|_|"Speech model lock failed")?;let tts=slot.as_ref().ok_or("Speech voices are not prepared")?;
 // Compact bank keeps original speaker0 at row0 and original speaker2 at row1.
 let compact_sid=if sid==2{1}else{0};
 let audio=tts.generate_with_config(&text,&GenerationConfig{sid:compact_sid,speed,silence_scale:0.2,..Default::default()},None::<fn(&[f32],f32)->bool>).ok_or("Could not read this text")?;let s=audio.samples();if audio.sample_rate()!=24000||s.is_empty()||s.len()>24000*90||s.iter().any(|x|!x.is_finite()){return Err("Invalid generated audio".into())}
 let out=env.new_float_array(s.len()as i32).map_err(|e|e.to_string())?;env.set_float_array_region(&out,0,s).map_err(|e|e.to_string())?;Ok(out.into_raw())})();match result{Ok(v)=>v,Err(e)=>{failure(&mut env,e);ptr::null_mut()}}
}
#[no_mangle]pub extern "system" fn Java_app_onklav_home_NeuralSpeechNative_unload(_:JNIEnv,_:JClass){if let Ok(mut m)=model().lock(){*m=None}}
// Handles are owned by the Kotlin backend. Its handle lock excludes cancel/query
// from close. The audio callback touches only its preallocated Ring until close returns.
#[no_mangle]pub extern "system" fn Java_app_onklav_home_NeuralSpeechNative_open(mut env:JNIEnv,_:JClass)->jlong{match audio::Audio::open(){Ok(a)=>Box::into_raw(Box::new(a))as jlong,Err(e)=>{failure(&mut env,e);0}}}
#[no_mangle]pub unsafe extern "system" fn Java_app_onklav_home_NeuralSpeechNative_cancel(_:JNIEnv,_:JClass,h:jlong){if h!=0{(*(h as *const audio::Audio)).ring.cancelled.store(true,Ordering::Release)}}
#[no_mangle]pub unsafe extern "system" fn Java_app_onklav_home_NeuralSpeechNative_close(_:JNIEnv,_:JClass,h:jlong){if h!=0{drop(Box::from_raw(h as *mut audio::Audio))}}
#[no_mangle]pub unsafe extern "system" fn Java_app_onklav_home_NeuralSpeechNative_played(_:JNIEnv,_:JClass,h:jlong)->jlong{if h==0{0}else{(*(h as *const audio::Audio)).ring.played()as jlong}}
#[no_mangle]pub unsafe extern "system" fn Java_app_onklav_home_NeuralSpeechNative_buffered(mut env:JNIEnv,_:JClass,h:jlong)->jlong{if h==0{return 0}let r=&(*(h as *const audio::Audio)).ring;if r.failed.load(Ordering::Acquire){failure(&mut env,"Audio output changed. Tap Play to resume.")}(*(h as *const audio::Audio)).buffered()as jlong}
#[no_mangle]pub unsafe extern "system" fn Java_app_onklav_home_NeuralSpeechNative_enqueue(mut env:JNIEnv,_:JClass,h:jlong,samples:JFloatArray){if h==0{return}let result=(||->Result<(),String>{let n=env.get_array_length(&samples).map_err(|e|e.to_string())?;if n<0||n>24000*90{return Err("Speech buffer is too large".into())}let mut pcm=vec![0.;n as usize];env.get_float_array_region(&samples,0,&mut pcm).map_err(|e|e.to_string())?;let r=&(*(h as *const audio::Audio)).ring;let mut at=0;let mut progress=std::time::Instant::now();while at<pcm.len(){if r.failed.load(Ordering::Acquire){return Err("Audio output disconnected. Tap Play to resume.".into())}if r.cancelled.load(Ordering::Acquire){return Ok(())}let count=r.push(&pcm[at..]);at+=count;if count>0{progress=std::time::Instant::now()}else if progress.elapsed()>std::time::Duration::from_secs(5){return Err("Audio output stalled. Tap Play to resume.".into())}if count==0{std::thread::sleep(std::time::Duration::from_millis(2))}}Ok(())})();if let Err(e)=result{failure(&mut env,e)}}
