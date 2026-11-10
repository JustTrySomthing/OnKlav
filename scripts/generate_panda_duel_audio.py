from pathlib import Path
import numpy as np,wave,subprocess
root=Path(__file__).resolve().parents[1]/'games/engine-checks/panda/audio';rate=32000;rng=np.random.default_rng(417)
def save(name,a):
 a=np.nan_to_num(a);peak=np.max(np.abs(a));a=a/max(1,peak/.62)
 # Small fades avoid discontinuities even for percussion and game interruptions.
 n=min(320,len(a)//8);a[:n]*=np.linspace(0,1,n);a[-n:]*=np.linspace(1,0,n)
 path=root/(name+'.wav')
 with wave.open(str(path),'wb')as f:f.setnchannels(1);f.setsampwidth(2);f.setframerate(rate);f.writeframes((a*32767).astype('<i2').tobytes())
 return path
def tone(freq,duration,style='pluck'):
 t=np.arange(int(rate*duration))/rate;phase=2*np.pi*freq*t
 if style=='bass':return (np.sin(phase)+.22*np.sin(phase*2))*np.minimum(t/.012,1)*np.exp(-t*5)
 if style=='brass':return (np.sin(phase)+.26*np.sin(phase*2)+.12*np.sin(phase*3))*np.minimum(t/.045,1)*np.minimum((duration-t)/.07,1)*.42
 return (np.sin(phase)+.28*np.sin(phase*2.01)+.11*np.sin(phase*3.98))*np.minimum(t/.005,1)*np.exp(-t*(10 if style=='wood'else 5))
def midi(n):return 440*2**((n-69)/12)
for biome,base in enumerate([60,62,65]):
 bpm=116+biome*4;beat=60/bpm;duration=beat*64;mix=np.zeros(int(rate*duration))
 def add(t,a,gain):
  k=int(t*rate);end=min(len(mix),k+len(a));mix[k:end]+=a[:end-k]*gain
 chords=[[0,4,7],[5,9,12],[7,11,14],[0,4,9]]
 melody=[7,9,12,9,7,4,2,4,5,9,12,14,12,9,7,4]
 for b in range(64):
  chord=chords[(b//4)%4]
  add(b*beat,tone(midi(base-24+chord[0]),beat*.85,'bass'),.28)
  for offset in [.0,.5]:
   for j,n in enumerate(chord):add((b+offset)*beat+j*.012,tone(midi(base+n),.28,'wood'),.085)
  note=melody[(b+biome*2)%len(melody)]+base+12
  add((b+.22 if b%4==3 else b)*beat,tone(midi(note),beat*.63,'pluck'if biome!=2 else'brass'),.16)
  if b%4==3:add((b+.64)*beat,tone(midi(base+19),.18),.1)
  t=np.arange(int(rate*.14))/rate
  kick=np.sin(2*np.pi*(54*t+22*(1-np.exp(-t*30))/30))*np.exp(-t*25)
  if b%2==0:add(b*beat,kick,.3)
  shaker=rng.normal(0,1,int(rate*.065));shaker=np.diff(shaker,prepend=0)*np.exp(-np.arange(len(shaker))/rate*50)
  add((b+.5)*beat,shaker,.028)
  if b%2==1:
   snap=rng.normal(0,1,int(rate*.1));snap=np.diff(snap,prepend=0)*np.exp(-np.arange(len(snap))/rate*35);add(b*beat,snap,.045)
 # Same structured loop endpoints; brief fades and no hard edge.
 path=save('duel_music'+str(biome),mix)
 subprocess.run(['/usr/bin/ffmpeg','-y','-v','error','-i',str(path),'-c:a','libvorbis','-q:a','4',str(path.with_suffix('.ogg'))],check=True)
 path.unlink()
for name,dur in [('whizz',.58),('tumble',.35),('combo',.55),('bird',.5),('miss',.65),('ready',.38),('glass',.34),('power',.58)]:
 t=np.arange(int(rate*dur))/rate;p=t/dur
 if name=='whizz':a=np.sin(np.cumsum(2*np.pi*(1150*(1-p)**1.6+130)/rate))*np.sin(np.pi*p)**2*.28
 elif name=='miss':a=np.sin(np.cumsum(2*np.pi*(360-220*p+16*np.sin(t*21))/rate))*np.sin(np.pi*p)*.26
 elif name=='tumble':a=np.sin(np.cumsum(2*np.pi*(210*np.exp(-t*7)+80)/rate))*np.exp(-t*7)*.32
 elif name=='glass':
  a=sum(np.sin(2*np.pi*f*t)*np.exp(-t*(12+j*5))for j,f in enumerate([1750,2315,3218,4013]))*.085
 elif name=='power':
  a=rng.normal(0,1,len(t))*np.exp(-t*18)*.15+np.sin(2*np.pi*72*t)*np.exp(-t*8)*.4
 else:
  notes=[0,4,7,12]if name!='bird'else[12,7,16,19];a=np.zeros(len(t))
  for i,n in enumerate(notes):
   sound=tone(midi(72+n),dur/2);start=int(i*dur/6*rate);end=min(len(a),start+len(sound));a[start:end]+=sound[:end-start]*.27
 save('duel_'+name,a)
print('Three original 16-bar playful arrangements and eight smooth-edged comic cues generated')
