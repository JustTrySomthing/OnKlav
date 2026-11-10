extends RefCounted
const CAT=preload("res://cat_dog/art/cat.webp")
const DOG=preload("res://cat_dog/art/dog.webp")
const ROSE=Color("ff8eae")
const BLUE=Color("79c9ff")
static func color(cat:bool) -> Color:return ROSE if cat else BLUE
static func face(c:CanvasItem,point:Vector2,radius:float,cat:bool,alpha:float=1.0) -> void:
	c.draw_circle(point+Vector2(0,2),radius*.92,Color(0,0,0,.4*alpha))
	c.draw_texture_rect(CAT if cat else DOG,Rect2(point-Vector2.ONE*radius,Vector2.ONE*radius*2),false,Color(1,1,1,alpha))

static func tilted_face(c:CanvasItem,point:Vector2,radius:float,cat:bool,angle:float,edge:float=1.0) -> void:
	c.draw_set_transform(point,angle,Vector2(edge,1))
	face(c,Vector2.ZERO,radius,cat)
	c.draw_set_transform(Vector2.ZERO)

static func paw(c:CanvasItem,point:Vector2,radius:float,ink:Color) -> void:
	c.draw_circle(point+Vector2(0,radius*.28),radius*.46,ink)
	for d in [Vector2(-.65,-.25),Vector2(-.25,-.68),Vector2(.25,-.68),Vector2(.65,-.25)]:c.draw_circle(point+d*radius,radius*.23,ink)
