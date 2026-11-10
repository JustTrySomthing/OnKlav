//! Shared, deterministic input checks and typed values for common app fields.
use crate::serde_json::{json,Value};
#[derive(Clone,Copy,Debug,PartialEq,Eq)]pub enum InputKind{Text,Integer,Decimal,Email,Phone,Date,Time}
impl InputKind{
 pub fn name(self)->&'static str{match self{Self::Text=>"text",Self::Integer=>"integer",Self::Decimal=>"decimal",Self::Email=>"email",Self::Phone=>"phone",Self::Date=>"date",Self::Time=>"time"}}
 pub fn check(self,text:&str)->Result<Value,&'static str>{
  if self==Self::Text{return Ok(json!(text))}let value=text.trim();if value.is_empty(){return Ok(Value::Null)}
  match self{
   Self::Integer=>value.parse::<i64>().map(|n|json!(n)).map_err(|_|"Enter a whole number within the signed64-bit range."),
   Self::Decimal=>value.parse::<f64>().ok().filter(|n|n.is_finite()).map(|n|json!(n)).ok_or("Enter a finite decimal number using a dot."),
   Self::Email=>if value.split_once('@').is_some_and(|(a,b)|!a.is_empty()&&!b.is_empty()&&!b.contains('@'))&&!value.chars().any(char::is_whitespace){Ok(json!(value))}else{Err("Enter an email address such as name@example.com.")},
   Self::Phone=>{let digits=value.chars().filter(char::is_ascii_digit).count();if (3..=20).contains(&digits)&&value.chars().all(|c|c.is_ascii_digit()||" +()-.".contains(c)){Ok(json!(value))}else{Err("Enter a phone number with3–20 digits and optional +, spaces or ()-.")}},
   Self::Date=>if valid_date(value){Ok(json!(value))}else{Err("Enter a real date as YYYY-MM-DD.")},
   Self::Time=>if valid_time(value){Ok(json!(value))}else{Err("Enter a time as HH:MM, from00:00 to23:59.")},Self::Text=>unreachable!()
  }
 }
}
fn digits(value:&str)->bool{value.bytes().all(|c|c.is_ascii_digit())}
pub fn valid_date(value:&str)->bool{
 let b=value.as_bytes();if b.len()!=10||b[4]!=b'-'||b[7]!=b'-'||!digits(&value[..4])||!digits(&value[5..7])||!digits(&value[8..]){return false}
 let y=value[..4].parse::<u32>().unwrap_or(0);let m=value[5..7].parse::<usize>().unwrap_or(0);let d=value[8..].parse::<u32>().unwrap_or(0);if y==0||!(1..=12).contains(&m){return false}
 let feb=if y%4==0&&(y%100!=0||y%400==0){29}else{28};d>0&&d<=[31,feb,31,30,31,30,31,31,30,31,30,31][m-1]
}
pub fn valid_time(value:&str)->bool{let b=value.as_bytes();b.len()==5&&b[2]==b':'&&digits(&value[..2])&&digits(&value[3..])&&value[..2].parse::<u8>().is_ok_and(|n|n<24)&&value[3..].parse::<u8>().is_ok_and(|n|n<60)}
