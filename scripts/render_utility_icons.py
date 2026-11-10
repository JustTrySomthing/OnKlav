from pathlib import Path
import xml.etree.ElementTree as E
import subprocess,io,ctypes as C,ctypes.util,tempfile
from PIL import Image,ImageDraw,ImageFont
r=Path(__file__).resolve().parents[1]
out=r/'docs/evidence/utility-icons';out.mkdir(parents=True,exist_ok=True)
ns='{http://schemas.android.com/apk/res/android}'
icons=[('format_swap','Format Swap'),('screen_recorder','Screen Recorder'),('voice_recorder','Voice Recorder'),('vault','Vault')]
def svg_node(n,color):
 if n.tag=='group':
  x=n.get(ns+'translateX','0');y=n.get(ns+'translateY','0');rotation=n.get(ns+'rotation','0');px=n.get(ns+'pivotX','0');py=n.get(ns+'pivotY','0')
  return f'<g transform="translate({x} {y}) rotate({rotation} {px} {py})">'+''.join(svg_node(c,color) for c in n)+'</g>'
 if n.tag=='path':
  fill='none' if n.get(ns+'fillColor') in ('#00000000','@android:color/transparent') else color
  stroke=color if n.get(ns+'strokeColor') else 'none'
  return f'<path d="{n.get(ns+"pathData")}" fill="{fill}" stroke="{stroke}" stroke-width="{n.get(ns+"strokeWidth","0")}" stroke-linecap="round" stroke-linejoin="round"/>'
 return ''.join(svg_node(c,color) for c in n)
rsvg=C.CDLL(C.util.find_library('rsvg-2'));cairo=C.CDLL(C.util.find_library('cairo'));gobject=C.CDLL(C.util.find_library('gobject-2.0'))
rsvg.rsvg_handle_new_from_data.argtypes=[C.c_void_p,C.c_size_t,C.c_void_p];rsvg.rsvg_handle_new_from_data.restype=C.c_void_p
rsvg.rsvg_handle_render_cairo.argtypes=[C.c_void_p,C.c_void_p];rsvg.rsvg_handle_render_cairo.restype=C.c_int
cairo.cairo_image_surface_create.argtypes=[C.c_int,C.c_int,C.c_int];cairo.cairo_image_surface_create.restype=C.c_void_p
cairo.cairo_create.argtypes=[C.c_void_p];cairo.cairo_create.restype=C.c_void_p
cairo.cairo_surface_write_to_png.argtypes=[C.c_void_p,C.c_char_p];cairo.cairo_surface_write_to_png.restype=C.c_int
cairo.cairo_destroy.argtypes=[C.c_void_p];cairo.cairo_surface_destroy.argtypes=[C.c_void_p];gobject.g_object_unref.argtypes=[C.c_void_p]
def render(name,color,size):
 n=E.parse(r/f'android/shared/src/main/res/drawable/ref_app_{name}.xml').getroot();assert n.get(ns+'viewportWidth')=='32'
 svg=f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32" width="{size}" height="{size}">'+svg_node(n,color)+'</svg>'
 data=svg.encode();handle=rsvg.rsvg_handle_new_from_data(data,len(data),None);assert handle
 surface=cairo.cairo_image_surface_create(0,size,size);context=cairo.cairo_create(surface)
 try:
  assert rsvg.rsvg_handle_render_cairo(handle,context)
  with tempfile.NamedTemporaryFile(suffix='.png') as f:
   assert cairo.cairo_surface_write_to_png(surface,f.name.encode())==0
   return Image.open(f.name).convert('RGBA')
 finally:cairo.cairo_destroy(context);cairo.cairo_surface_destroy(surface);gobject.g_object_unref(handle)
font='/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf';title=ImageFont.truetype(font,19);small=ImageFont.truetype(font,12)
canvas=Image.new('RGB',(960,580),'#F7F5FA');d=ImageDraw.Draw(canvas)
for row,(bg,ink,edge,muted) in enumerate([('#F7F5FA','#6C3D91','#D7CAE4','#696071'),('#25232B','#D0AFF0','#655272','#BCB4C5')]):
 y=row*290;d.rectangle((0,y,960,y+290),fill=bg)
 for col,(name,label) in enumerate(icons):
  cx=120+240*col;d.rounded_rectangle((cx-62,y+24,cx+62,y+148),radius=29,outline=edge,width=2)
  im=render(name,ink,86);canvas.paste(im,(cx-43,y+43),im)
  d.text((cx,y+170),label,font=title,fill=ink,anchor='mm')
  for i,size in enumerate((36,24)):
   im=render(name,ink,size);canvas.paste(im,(cx-45+64*i-size//2,y+208-size//2),im)
  d.text((cx,y+250),'36 px desktop  ·  24 px compact',font=small,fill=muted,anchor='mm')
canvas.save(out/'utility-icons-preview.png')
# Ensure paths fit the viewport, including the microphone's rotation/stroke.
checks=[]
for name,label in icons:
 im=render(name,'#FFFFFF',320);box=im.getchannel('A').getbbox();assert box and box[0]>0 and box[1]>0 and box[2]<320 and box[3]<320,(name,box)
 checks.append((name,box))
print('Rendered actual XML paths; all four glyphs fit viewport:',checks)
print(out/'utility-icons-preview.png')
