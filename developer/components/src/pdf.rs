//! Shared native PDF viewer. Requires an owned immutable private blob and storage.private.
use crate::*;
pub fn viewer(id:&str,file:&str)->Node{let mut n=node("pdf_viewer",id,"");n["file"]=json!(file);n}
/// Fill the bounded host body; the viewer supplies its own page/search/text controls.
pub fn reader(title:&str,input:Value,file:&str)->Value{
 let close=input["event"]["action"]=="pdf.back";
 let mut frame=Screen::new(title,json!({"file":file})).body(vec![viewer("pdf.document",file)]).back("pdf.back");if close{frame=frame.close()}frame.finish()
}
#[cfg(test)]mod tests{use super::*;#[test]fn shared_pdf_widget_uses_only_a_private_handle_and_host_layout(){let f=reader("PDF",json!({}),&"a".repeat(32));assert_eq!(f["body"][0]["kind"],"pdf_viewer");assert_eq!(f["body"][0]["file"],"a".repeat(32));assert_ne!(f["scroll_body"],true);assert_eq!(reader("PDF",json!({"event":{"action":"pdf.back"}}),"a")["close_requested"],true);assert!(f["body"][0].get("path").is_none());}}
