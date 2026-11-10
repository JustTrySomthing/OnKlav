use crate::ring::Ring;
#[cfg(target_os="android")]
mod android {
 use super::*;use std::{ffi::c_void,ptr,sync::atomic::Ordering};
 type Stream=c_void;type Builder=c_void;
 #[link(name="aaudio")]extern "C"{
 fn AAudio_createStreamBuilder(b:*mut *mut Builder)->i32;fn AAudioStreamBuilder_delete(b:*mut Builder)->i32;
 fn AAudioStreamBuilder_setDirection(b:*mut Builder,v:i32);fn AAudioStreamBuilder_setFormat(b:*mut Builder,v:i32);fn AAudioStreamBuilder_setChannelCount(b:*mut Builder,v:i32);fn AAudioStreamBuilder_setSampleRate(b:*mut Builder,v:i32);fn AAudioStreamBuilder_setSharingMode(b:*mut Builder,v:i32);fn AAudioStreamBuilder_setPerformanceMode(b:*mut Builder,v:i32);fn AAudioStreamBuilder_setUsage(b:*mut Builder,v:i32);fn AAudioStreamBuilder_setContentType(b:*mut Builder,v:i32);
 fn AAudioStreamBuilder_setDataCallback(b:*mut Builder,f:Option<unsafe extern "C" fn(*mut Stream,*mut c_void,*mut c_void,i32)->i32>,u:*mut c_void);fn AAudioStreamBuilder_setErrorCallback(b:*mut Builder,f:Option<unsafe extern "C" fn(*mut Stream,*mut c_void,i32)>,u:*mut c_void);
 fn AAudioStream_getTimestamp(s:*mut Stream,clock:i32,frame:*mut i64,time:*mut i64)->i32;fn AAudioStream_getFramesRead(s:*mut Stream)->i64;fn AAudioStream_getSampleRate(s:*mut Stream)->i32;fn AAudioStream_getFormat(s:*mut Stream)->i32;fn AAudioStream_getChannelCount(s:*mut Stream)->i32;
 fn AAudioStreamBuilder_openStream(b:*mut Builder,s:*mut *mut Stream)->i32;fn AAudioStream_requestStart(s:*mut Stream)->i32;fn AAudioStream_requestStop(s:*mut Stream)->i32;fn AAudioStream_close(s:*mut Stream)->i32;
 }
 pub struct Audio{pub ring:Box<Ring>,stream:*mut Stream}
 unsafe extern "C" fn callback(_: *mut Stream,u:*mut c_void,data:*mut c_void,n:i32)->i32{if n>0{let r=&*(u as *const Ring);r.pop(std::slice::from_raw_parts_mut(data as *mut f32,n as usize));}0}
 unsafe extern "C" fn error(_: *mut Stream,u:*mut c_void,_:i32){let r=&*(u as *const Ring);r.failed.store(true,Ordering::Release);r.cancelled.store(true,Ordering::Release);}
 impl Audio{
  pub fn buffered(&self)->usize{let queued=self.ring.buffered();if queued>0{return queued}let target=self.ring.last_audio.load(Ordering::Acquire)as i64;let(mut frame,mut time)=(0i64,0i64);unsafe{if AAudioStream_getTimestamp(self.stream,1,&mut frame,&mut time)!=0{frame=AAudioStream_getFramesRead(self.stream)}}target.saturating_sub(frame.max(0)).max(0)as usize}
  pub fn open()->Result<Self,String>{unsafe{
   let ring=Box::new(Ring::new(24000*10));let mut b=ptr::null_mut();if AAudio_createStreamBuilder(&mut b)!=0{return Err("Could not create audio output".into())}
   AAudioStreamBuilder_setDirection(b,0);AAudioStreamBuilder_setFormat(b,2);AAudioStreamBuilder_setChannelCount(b,1);AAudioStreamBuilder_setSampleRate(b,24000);AAudioStreamBuilder_setSharingMode(b,1);AAudioStreamBuilder_setPerformanceMode(b,12);AAudioStreamBuilder_setUsage(b,1);AAudioStreamBuilder_setContentType(b,1);
   let data=(&*ring as *const Ring)as *mut c_void;AAudioStreamBuilder_setDataCallback(b,Some(callback),data);AAudioStreamBuilder_setErrorCallback(b,Some(error),data);
   let mut stream=ptr::null_mut();let result=AAudioStreamBuilder_openStream(b,&mut stream);AAudioStreamBuilder_delete(b);if result!=0||stream.is_null(){return Err(format!("Audio output could not open ({result})"))}
   if AAudioStream_getSampleRate(stream)!=24000||AAudioStream_getFormat(stream)!=2||AAudioStream_getChannelCount(stream)!=1{AAudioStream_close(stream);return Err("Audio device could not preserve the speech format".into())}
   if AAudioStream_requestStart(stream)!=0{AAudioStream_close(stream);return Err("Audio output could not start".into())}Ok(Self{ring,stream})
  }}
 }
 impl Drop for Audio{fn drop(&mut self){self.ring.cancelled.store(true,Ordering::Release);unsafe{AAudioStream_requestStop(self.stream);AAudioStream_close(self.stream);}}}
}
#[cfg(target_os="android")]pub use android::Audio;
#[cfg(not(target_os="android"))]pub struct Audio{pub ring:Box<Ring>}
#[cfg(not(target_os="android"))]impl Audio{pub fn buffered(&self)->usize{self.ring.buffered()}pub fn open()->Result<Self,String>{Err("Hardware speech output is Android only".into())}}
