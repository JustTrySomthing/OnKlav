# Developer host EGL/GLES regression; not part of the Android runtime.
import ctypes as C, re, json, os
from pathlib import Path
os.environ.setdefault('EGL_PLATFORM','surfaceless')
e=C.CDLL('libEGL.so.1');P=C.c_void_p;I=C.c_int;U=C.c_uint;F=C.c_float
e.eglGetProcAddress.argtypes=[C.c_char_p];e.eglGetProcAddress.restype=P
getdisplay=C.CFUNCTYPE(P,U,P,C.POINTER(I))(e.eglGetProcAddress(b'eglGetPlatformDisplayEXT'))
d=getdisplay(0x31DD,None,None)
for name,restype,args in [('eglInitialize',U,[P,C.POINTER(I),C.POINTER(I)]),('eglBindAPI',U,[U]),('eglChooseConfig',U,[P,C.POINTER(I),C.POINTER(P),I,C.POINTER(I)]),('eglCreateContext',P,[P,P,P,C.POINTER(I)]),('eglCreatePbufferSurface',P,[P,P,C.POINTER(I)]),('eglMakeCurrent',U,[P,P,P,P])]:
 f=getattr(e,name);f.restype=restype;f.argtypes=args
major=I();minor=I();assert e.eglInitialize(d,C.byref(major),C.byref(minor));assert e.eglBindAPI(0x30A0)
attrs=(I*15)(0x3024,8,0x3023,8,0x3022,8,0x3021,8,0x3033,1,0x3040,4,0x3038,0,0)
config=P();num=I();assert e.eglChooseConfig(d,attrs,C.byref(config),1,C.byref(num)) and num.value
ctx=e.eglCreateContext(d,config,None,(I*3)(0x3098,2,0x3038));assert ctx
surface=e.eglCreatePbufferSurface(d,config,(I*5)(0x3057,16,0x3056,16,0x3038));assert surface and e.eglMakeCurrent(d,surface,surface,ctx)
def gl(name,result,*args):return C.CFUNCTYPE(result,*args)(e.eglGetProcAddress(name.encode()))
create=gl('glCreateShader',U,U);source=gl('glShaderSource',None,U,I,C.POINTER(C.c_char_p),P);compile=gl('glCompileShader',None,U);getshader=gl('glGetShaderiv',None,U,U,C.POINTER(I));shaderlog=gl('glGetShaderInfoLog',None,U,I,P,C.c_char_p)
s=(Path(__file__).resolve().parents[3]/'android/app/src/main/kotlin/app/onklav/home/VideoCutoutEffect.kt').read_text()
shaders=[]
for name,kind in [('VERTEX',0x8B31),('FRAGMENT',0x8B30)]:
 text=re.search('const val '+name+'="""(.*?)"""',s,re.S).group(1).encode();shader=create(kind);strings=(C.c_char_p*1)(text);source(shader,1,strings,None);compile(shader);ok=I();getshader(shader,0x8B81,C.byref(ok));buf=C.create_string_buffer(4096);shaderlog(shader,4096,None,buf);assert ok.value,buf.value;shaders.append(shader)
program=gl('glCreateProgram',U)();attach=gl('glAttachShader',None,U,U)
for shader in shaders:attach(program,shader)
gl('glLinkProgram',None,U)(program);ok=I();gl('glGetProgramiv',None,U,U,C.POINTER(I))(program,0x8B82,C.byref(ok));assert ok.value
gl('glUseProgram',None,U)(program)
loc=gl('glGetUniformLocation',I,U,C.c_char_p);uniform=gl('glUniform1f',None,I,F)
def setf(name,value):uniform(loc(program,name.encode()),value)
gl('glUniform1i',None,I,I)(loc(program,b'uTexSampler'),0)
setf('uTolerance',.35);setf('uChroma',1);setf('uMask',0);setf('uFeather',.03);setf('uAspect',1)
gl('glUniform3f',None,I,F,F,F)(loc(program,b'uKey'),0,1,0)
tex=U();gl('glGenTextures',None,I,C.POINTER(U))(1,C.byref(tex));gl('glBindTexture',None,U,U)(0x0DE1,tex)
param=gl('glTexParameteri',None,U,U,I)
for which in [0x2800,0x2801]:param(0x0DE1,which,0x2600)
for which in [0x2802,0x2803]:param(0x0DE1,which,0x812F)
vertices=(F*16)(-1,-1,0,1,-1,1,0,1,1,-1,0,1,1,1,0,1);attribute=gl('glGetAttribLocation',I,U,C.c_char_p)(program,b'aFramePosition');gl('glEnableVertexAttribArray',None,U)(attribute);gl('glVertexAttribPointer',None,U,I,U,U,I,P)(attribute,4,0x1406,0,0,C.cast(vertices,P));gl('glViewport',None,I,I,I,I)(0,0,16,16)
upload=gl('glTexImage2D',None,U,I,I,I,I,I,U,U,P);draw=gl('glDrawArrays',None,U,I,I);read=gl('glReadPixels',None,I,I,I,I,U,U,P)
def render(rgba,x=8,y=8):
 pixels=(C.c_ubyte*4)(*rgba);upload(0x0DE1,0,0x1908,1,1,0,0x1908,0x1401,C.cast(pixels,P));draw(5,0,4);out=(C.c_ubyte*4)();read(x,y,1,1,0x1908,0x1401,C.cast(out,P));return list(out)
results={}
results['green_key']=render([0,255,0,255]);assert results['green_key'][3]==0
results['red_retained']=render([255,0,0,255]);assert results['red_retained']==[255,0,0,255]
setf('uChroma',0);setf('uMask',1)
results['circle_center']=render([255,0,0,255]);results['circle_corner']=render([255,0,0,255],0,0);assert results['circle_center'][3]==255 and results['circle_corner'][3]==0
setf('uFeather',.1);results['feather_edge']=render([255,0,0,255],14,8);assert 0<results['feather_edge'][3]<255
setf('uMask',2);results['letterbox_outside']=render([255,0,0,255],8,0);assert results['letterbox_outside'][3]==0
renderer=gl('glGetString',C.c_char_p,U)(0x1F01).decode()
print(json.dumps({'shader_compile_link':'passed','renderer':renderer,'pixel_cases':results,'phone_qualified':False},indent=2))
