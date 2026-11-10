//! Offline private-blob EPUB text profile. Host resolves ZIP/XML; guest gets no paths.
use crate::*;
pub fn info(id:&str,then:&str)->Value{effect("epub.info",json!({"id":id}),then)}
pub fn chapter(id:&str,index:usize,then:&str)->Value{effect("epub.chapter",json!({"id":id,"chapter":index}),then)}
pub fn search(id:&str,query:&str,chapter:usize,offset:usize,then:&str)->Value{effect("epub.search",json!({"id":id,"query":query,"chapter":chapter,"offset":offset}),then)}
