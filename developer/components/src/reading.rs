//! Bounded UTF-8 text pagination and reading positions. Not an EPUB/PDF renderer.
//! Persist a bookmark through collections; resolve it only against the same revision.
use crate::serde_json::{json,Value};

pub struct TextPage<'a>{pub text:&'a str,pub start:usize,pub end:usize,pub total:usize}

/// Case-sensitive literal search, with no regex or locale assumptions. Returns
/// at most 40 UTF-8 byte ranges. Continue at the last end; empty queries fail.
pub fn find(text:&str,query:&str,start:usize)->Result<Vec<(usize,usize)>,String>{
 if text.len()>1_000_000||query.is_empty()||query.len()>256||!text.is_char_boundary(start){return Err("Use bounded text, a 1–256 byte query and a valid reading position".into())}
 Ok(text[start..].match_indices(query).take(40).map(|(offset,value)|(start+offset,start+offset+value.len())).collect())
}

/// Offsets are UTF-8 byte boundaries, never character or Android UTF-16 offsets.
/// Bounds keep page text below the shared speech widget's 50,000-unit limit.
pub fn page(text:&str,start:usize,characters:usize)->Result<TextPage<'_>,String>{
 if text.len()>1_000_000{return Err("Text exceeds the one-megabyte reader limit".into())}
 if !(1..=20000).contains(&characters){return Err("Choose 1 to 20,000 characters per page".into())}
 if !text.is_char_boundary(start){return Err("Reading position is outside a UTF-8 boundary".into())}
 let rest=&text[start..];let size=rest.char_indices().nth(characters).map(|(n,_)|n).unwrap_or(rest.len());
 Ok(TextPage{text:&rest[..size],start,end:start+size,total:text.len()})
}

/// Caller supplies the immutable content identity and revision, not a file path.
pub fn bookmark(id:&str,revision:&str,offset:usize)->Result<Value,String>{
 if [id,revision].iter().any(|v|v.is_empty()||v.len()>128||v.chars().any(char::is_control))||offset>1_000_000{return Err("Invalid reading bookmark".into())}
 Ok(json!({"content":id,"revision":revision,"offset":offset}))
}
/// Changed content resets to the beginning. Never apply an old offset to a new book.
pub fn resume(saved:&Value,id:&str,revision:&str,text:&str)->usize{
 if saved["content"]!=id||saved["revision"]!=revision{return 0}
 saved["offset"].as_u64().and_then(|n|usize::try_from(n).ok()).filter(|n|text.is_char_boundary(*n)).unwrap_or(0)
}

#[cfg(test)]mod tests{
 use super::*;
 #[test]fn literal_search_pages_use_valid_unicode_offsets(){let s="😀 alpha 😀 alpha";assert_eq!(vec![(0,4),(11,15)],find(s,"😀",0).unwrap());assert_eq!(vec![(11,15)],find(s,"😀",4).unwrap());assert!(find(s,"",0).is_err());assert!(find(s,"a",1).is_err());assert!(find(s,"a",usize::MAX).is_err());assert!(find(s,"ALPHA",0).unwrap().is_empty());let many="x ".repeat(100);let first=find(&many,"x",0).unwrap();assert_eq!(40,first.len());assert_eq!(40,find(&many,"x",first.last().unwrap().1).unwrap().len());assert!(find(s,&"x".repeat(257),0).is_err());}
 #[test]fn unicode_pages_reassemble_without_splitting_codepoints(){let s="A😀é مرحبا\nSecond page";let mut offset=0;let mut joined=String::new();while offset<s.len(){let p=page(s,offset,3).unwrap();assert!(p.end>offset);joined.push_str(p.text);offset=p.end;}assert_eq!(s,joined);assert!(page(s,2,3).is_err());assert!(page(s,usize::MAX,3).is_err());assert!(page(s,0,0).is_err());assert_eq!("",page(s,s.len(),3).unwrap().text);}
 #[test]fn bookmarks_require_matching_content_and_revision(){let b=bookmark("book","v1",5).unwrap();assert_eq!(5,resume(&b,"book","v1","hello world"));assert_eq!(0,resume(&b,"book","v2","hello world"));assert_eq!(0,resume(&b,"other","v1","hello world"));let b=bookmark("book","v1",2).unwrap();assert_eq!(0,resume(&b,"book","v1","A😀"));assert!(bookmark("","v1",0).is_err());}
}
