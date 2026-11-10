extends Control
const Chrome=preload("res://game_chrome.gd")
const Solo=preload("res://blackjack/solo.gd")
var hands:Array=[[],[]]
var me:=0
var friend:=false
var revealed:=false
var turn:=0
var hand_over:=false
var winner:=-1
var round:=1
var wins:Array=[0,0]
var history:Array=[]
func _ready() -> void:
	mouse_filter=Control.MOUSE_FILTER_IGNORE;custom_minimum_size=Vector2(0,112);resized.connect(queue_redraw)
func text(value:String,p:Vector2,font_size:int,ink:Color) -> void:draw_string(ThemeDB.fallback_font,p,value,HORIZONTAL_ALIGNMENT_LEFT,-1,font_size,ink)
func card(rect:Rect2,actor:int) -> void:
	var own:=actor==me;var ink:=Color("89e6cf") if own else Color("edc98c")
	var active:bool=(turn==actor and not hand_over) or (hand_over and winner==actor)
	var style:=Chrome.box("153a3d" if own else "2d3337",ink.to_html(false) if active else "3b5660",14)
	style.border_color=Color(ink,.9 if active else .3);draw_style_box(style,rect)
	if active:draw_line(rect.position+Vector2(13,2),rect.position+Vector2(rect.size.x-13,2),ink,2,true)
	var compact:bool=size.y<110
	var font:=ThemeDB.fallback_font
	var name_label:="YOU" if own else "FRIEND" if friend else "DEALER"
	var name_size:=14
	var name_width:=font.get_string_size(name_label,HORIZONTAL_ALIGNMENT_LEFT,-1,name_size).x
	var name_position:=Vector2(rect.get_center().x-name_width*.5,rect.position.y+16)
	draw_string_outline(font,name_position,name_label,HORIZONTAL_ALIGNMENT_LEFT,-1,name_size,2,Color("091b20"))
	text(name_label,name_position,name_size,ink)
	var hidden:bool=not own and not revealed
	var total:int=Solo.Rules.blackjack_total(hands[actor])
	var amount:="—" if hidden else str(total) if not hands[actor].is_empty() else "—"
	var bust:=not hidden and total>21
	var total_ink:=Color("ffa591") if bust else Color("fff3d8")
	text(amount,rect.position+Vector2(12,42 if compact else 48),28,total_ink)
	var win_count:=str(wins[actor])
	var count_size:=25
	var label_size:=10
	var count_width:=font.get_string_size(win_count,HORIZONTAL_ALIGNMENT_LEFT,-1,count_size).x
	var label_width:=font.get_string_size("WINS",HORIZONTAL_ALIGNMENT_LEFT,-1,label_size).x
	var badge_width:=count_width+label_width+19
	var center_y:=rect.get_center().y
	var badge:=Rect2(rect.end.x-badge_width-6,center_y-15,badge_width,30)
	draw_style_box(Chrome.box("112629","6d7866",7),badge)
	var count_y:=center_y+(font.get_ascent(count_size)-font.get_descent(count_size))*.5
	var label_y:=center_y+(font.get_ascent(label_size)-font.get_descent(label_size))*.5
	text(win_count,Vector2(badge.position.x+7,count_y),count_size,ink)
	text("WINS",Vector2(badge.position.x+12+count_width,label_y),label_size,ink)
	var detail:="FACE DOWN" if hidden else "BUST" if bust else "BLACKJACK" if Solo.natural(hands[actor]) else "SOFT TOTAL" if Solo.soft(hands[actor]) else "HAND TOTAL"
	text(detail,rect.position+Vector2(12,rect.size.y-7),11,Color("ffa591") if bust else ink)
func _draw() -> void:
	var font:=ThemeDB.fallback_font
	var title:=("HAND %02d / 10"%round) if round<=10 else "TIE-BREAK %d"%(round-10)
	var center:=size.x*.5
	var badge_width:=font.get_string_size(title,HORIZONTAL_ALIGNMENT_LEFT,-1,16).x+27
	draw_style_box(Chrome.box("302a20","b8965c",10),Rect2(center-badge_width*.5,0,badge_width,24))
	text(title,Vector2(center-(badge_width-27)*.5,18),16,Color("ffe9b8"))
	for i in range(10):
		var ink:=Color("89e6cf") if i<history.size() and history[i]==me else Color("edc98c") if i<history.size() and history[i]>=0 else Color("94a9a8") if i<history.size() else Color("34504f")
		draw_circle(Vector2(center+(i-4.5)*12,31),2.9 if i==round-1 else 2.2,Color("ffe0a3") if i==round-1 else ink,true,-1,true)
	var width:float=(size.x-12)*.5
	card(Rect2(0,39,width,size.y-39),me);card(Rect2(width+12,39,width,size.y-39),1-me)
