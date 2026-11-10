"""Generate original Shroom textures, industrial score and synthesized effects."""
import math
import random
import struct
import subprocess
import wave
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

assets = Path(__file__).resolve().parent / "assets"
assets.mkdir(exist_ok=True)
rng = random.Random(5113)
rate = 24000
tau = math.tau

im = Image.new("RGB", (128, 128))
pixels = im.load()
for y in range(128):
    for x in range(128):
        noise = rng.randrange(-16, 17)
        seam = min(x % 32, y % 32)
        v = 49 if seam < 2 else (190 if seam < 4 else 128 + 8 * ((x // 32 + y // 32) % 2))
        pixels[x, y] = tuple(max(0, min(255, v + noise + c)) for c in (7, 0, 13))
draw = ImageDraw.Draw(im)
for x in range(6,128,32):
    for y in range(6,128,32):
        draw.ellipse((x,y,x+3,y+3),fill=(68,62,76),outline=(205,192,215))
im.save(assets/"foundry.png")

def wav(path, samples):
    with wave.open(str(path), "wb") as out:
        out.setparams((1,2,rate,0,"NONE",""))
        out.writeframes(b"".join(struct.pack("<h",round(max(-.98,min(.98,x))*32767)) for x in samples))

def clip(name, duration, fn):
    wav(assets/(name+".wav"), (fn(i/rate) * min(1,i/45,(int(duration*rate)-i)/70) for i in range(int(duration*rate))))

def tone(f,t):
    return math.sin(tau*f*t)

def sweep(start,end,t,d):
    return math.sin(tau*(start*t+(end-start)*t*t/(2*d)))

def noise():
    return rng.uniform(-1,1)

clip("nail",.22,lambda t: (.55*noise()*math.exp(-t*43)+.49*sweep(245,50,t,.22)*math.exp(-t*19)))
clip("break",.48,lambda t: (.69*noise()*math.exp(-t*13)+.66*sweep(121,32,t,.48)*math.exp(-t*8)))
clip("hit",.12,lambda t: .48*sweep(420,159,t,.12)*math.exp(-t*28)+.23*noise()*math.exp(-t*41))
clip("pop",.37,lambda t: .63*sweep(250,41,t,.37)*math.exp(-t*12)+.23*noise()*math.exp(-t*19))
clip("dash",.35,lambda t: .48*noise()*math.sin(math.pi*t/.35)**2+.21*sweep(160,910,t,.35)*math.exp(-t*4))
clip("swap",.23,lambda t: .35*noise()*math.exp(-t*25)+.40*tone(285 if t<.11 else 480,t)*math.exp(-((t% .11)*38)))
clip("spit",.24,lambda t: .55*sweep(710,110,t,.24)*math.exp(-t*13)+.18*noise()*math.exp(-t*19))
clip("hurt",.31,lambda t: .56*sweep(93,34,t,.31)*math.exp(-t*9)+.18*noise()*math.exp(-t*25))
clip("death",.83,lambda t: .38*sweep(183,22,t,.83)*math.exp(-t*3)+.18*sweep(126,25,t,.83)*math.exp(-t*4))
def arpeggio(notes, step):
    return lambda t: (.38*tone(notes[min(len(notes)-1,int(t/step))],t)+.14*tone(notes[min(len(notes)-1,int(t/step))]*2,t))*math.exp(-(t%step)*8)
clip("heal",.49,arpeggio([523,659,784,1046],.1))
clip("wave",.48,arpeggio([130,155,196],.15))
clip("clear",.95,arpeggio([261,329,392,523,659,784],.14))

# 32 bars at 150 BPM. Distorted low riff, machine drums, alarm arpeggio and evolving breakdown.
beat = .4
step = beat/4
roots = [55,55,51.913,65.406,55,73.416,51.913,61.735]
riff = [0,0,0,7,0,0,3,0,0,12,0,6,3,0,7,3]
samples = []
for i in range(round(rate*beat*128)):
    t=i/rate
    tick=int(t/step)
    bar=tick//16
    pos=t%step
    quarter=t%beat
    f=roots[(bar//2)%8]*2**(riff[tick%16]/12)
    a=math.tanh(2.8*(tone(f,pos)+.40*tone(2*f,pos)+.15*tone(3*f,pos)))
    bass=.24*a*min(1,pos*280)*math.exp(-pos*(8 if tick%4 else 3))
    kick=.42*sweep(139,42,quarter,.22)*math.exp(-quarter*25)
    off=(t+beat)%(.8)
    snare=(.27*noise()+.11*tone(177,off))*math.exp(-off*23)
    hats=.088*noise()*math.exp(-pos*(67 if tick%2 else 41))
    ar=t%(.2)
    lead_freq=220*2**([0,7,12,15,12,7,3,10][(tick//2)%8]/12)
    lead=(.063*tone(lead_freq,ar)+.031*tone(lead_freq*2.006,ar))*min(1,ar*180)*math.exp(-ar*12)
    if 16<=bar<20:
        bass*=.34
        kick*=.42
        lead*=1.6
    elif bar>=24:
        lead*=1.27
    else:
        lead*=.65 if bar<4 else 1
    pad=.035*tone(roots[(bar//2)%8]*2,t)+.022*tone(roots[(bar//2)%8]*3,t)
    samples.append(math.tanh((bass+kick+snare+hats+lead+pad)*1.1)*.86)
# Smooth the seam without adding a silent beat.
for i in range(190):
    samples[-190+i] = samples[-190+i]*(1-i/190)+samples[0]*i/190
temp=assets/"score.wav"
wav(temp,samples)
subprocess.run(["ffmpeg","-v","error","-y","-i",str(temp),"-c:a","libvorbis","-q:a","5",str(assets/"under_the_cap.ogg")],check=True)
temp.unlink()

# Original splash emblem drawn at 2× so the final edges stay clean.
canvas = Image.new("RGB", (1024, 1024), "#100a19")
draw = ImageDraw.Draw(canvas)
for radius in range(492, 45, -3):
    f = (492-radius)/447
    draw.ellipse((512-radius,490-radius,512+radius,490+radius),
                 fill=(int(18+36*f),int(10+17*f),int(29+34*f)))
for offset in (0, 24, 48):
    draw.arc((156+offset,123+offset,868-offset,815-offset),201,338,fill="#644161",width=4)
draw.rounded_rectangle((292,391,732,724),radius=100,fill="#15101c",outline="#684964",width=14)
draw.rounded_rectangle((329,412,695,677),radius=88,fill="#bcaa98")
draw.pieslice((193,177,831,650),180,360,fill="#ef5974",outline="#271127",width=15)
draw.ellipse((193,385,831,468),fill="#9b2d58",outline="#271127",width=15)
for x,y,r in ((357,312,33),(479,235,26),(623,279,41),(739,350,24)):
    draw.ellipse((x-r,y-r,x+r,y+r),fill="#ffc99a")
draw.polygon(((367,492),(476,532),(479,572),(383,544)),fill="#26112d")
draw.polygon(((657,492),(548,532),(545,572),(641,544)),fill="#26112d")
for x in (406,586):
    draw.ellipse((x,526,x+28,547),fill="#7effcf")
draw.rounded_rectangle((472,586,552,627),radius=10,fill="#4d2346")
font = ImageFont.truetype("DejaVuSans-Bold.ttf", 144)
small = ImageFont.truetype("DejaVuSans-Bold.ttf", 29)
draw.text((512,805),"SHROOM",font=font,anchor="mm",fill="#f9d8a9",stroke_width=8,stroke_fill="#2b142a")
draw.text((512,908),"D E E P   S P O R E   C O N T A I N M E N T",font=small,anchor="mm",fill="#91f5d8")
canvas.resize((512,512), Image.Resampling.LANCZOS).save(assets/"shroom.png")
