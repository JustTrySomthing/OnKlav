from pathlib import Path
import numpy as np,wave,subprocess,re
r=Path(__file__).resolve().parents[3]/'games/engine-checks/panda/audio'
import json
composition=json.loads(Path(__file__).with_name('music-composition.json').read_text())
melodies=composition['melodies']
rate=24000
for level,bpm in enumerate([100,88,112]):
 beat=60/bpm;mix=np.zeros((int(rate*beat*64),2));rng=np.random.default_rng(220+level)
 def note(at,dur,midi,gain,voice=0,pan=0):
  if midi<0:return
  n=min(int(dur*rate),len(mix)-int(at*rate));t=np.arange(n)/rate;u=t/dur;f=440*2**((midi-69)/12)
  env=np.minimum(t/.008,1)*np.exp(-u*(5 if voice==0 else 3))*np.minimum((dur-t)/.05,1)
  tone=np.sin(2*np.pi*f*t)+(.22 if voice==0 else .1)*np.sin(4*np.pi*f*t)+.04*np.sin(6*np.pi*f*t)
  signal=env*tone*gain;start=int(at*rate);mix[start:start+n,0]+=signal*(1-pan)*.707;mix[start:start+n,1]+=signal*(1+pan)*.707
 for b in range(64):
  root=[48,50,55][level]+[0,5,9,7][(b//8)%4];at=b*beat
  note(at,beat*.9,melodies[level][b%32],.3,0,-.12)
  if b%2==0:note(at,beat*1.8,root-12,.24,1,.05)
  for half in range(2):note(at+half*beat*.5,beat*.45,root+12+[0,7,12,7][(b*2+half)%4],.07,0,(-.35 if half else .35))
  if b%4==0:
   for offset in [0,4,7]:note(at,beat*3.5,root+12+offset,.035,1,.1)
  n=int(rate*.09);t=np.arange(n)/rate;noise=rng.normal(0,1,n);drum=(np.sin(2*np.pi*(65*t-110*t*t))*np.exp(-45*t)*.13 if b%2==0 else noise*np.exp(-70*t)*.025)
  start=int(at*rate);mix[start:start+n]+=drum[:,None]
 fade=np.minimum(1,np.minimum(np.arange(len(mix)),np.arange(len(mix))[::-1])/(rate*.025));mix*=fade[:,None];mix=np.tanh(mix*1.2);pcm=(mix/ max(1,np.max(np.abs(mix)))*25000).astype('<i2')
 path=r/f'music{level}.wav'
 with wave.open(str(path),'wb')as f:f.setnchannels(2);f.setsampwidth(2);f.setframerate(rate);f.writeframes(pcm.tobytes())
 subprocess.run(['ffmpeg','-loglevel','error','-y','-i',str(path),'-c:a','libvorbis','-q:a','3',str(r/f'music{level}.ogg')],check=True);path.unlink()
print('Three original Panda scores arranged and encoded')
