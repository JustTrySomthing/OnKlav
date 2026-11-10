from pathlib import Path
import numpy as np,wave,subprocess
r=Path(__file__).resolve().parents[3]/'games/engine-checks/dominos/audio';r.mkdir(exist_ok=True)
rate=24000;beat=60/112;mix=np.zeros((int(rate*beat*64),2));rng=np.random.default_rng(8402)
def note(at,duration,midi,gain,pan=0,bass=False):
 n=min(int(duration*rate),len(mix)-int(at*rate));t=np.arange(n)/rate;hz=440*2**((midi-69)/12)
 env=np.minimum(t/.012,1)*np.minimum((duration-t)/.045,1)*np.exp(-t/duration*(2.2 if bass else 4))
 sig=(np.sin(2*np.pi*hz*t)+.16*np.sin(4*np.pi*hz*t)+.045*np.sin(6*np.pi*hz*t))*env*gain
 at=int(at*rate);mix[at:at+n,0]+=sig*(1-pan)*.707;mix[at:at+n,1]+=sig*(1+pan)*.707
for b in range(64):
 root=[45,41,48,43][(b//16)%4];at=b*beat
 if b%2==0:note(at,beat*1.7,root-12,.22,bass=True)
 for i in range(2):note(at+i*beat/2,beat*.85,root+12+[0,7,12,7,3,7,10,7][(b*2+i)%8],.07,.25 if i else -.25)
 if b%4==0:
  for n in [0,3,7]:note(at,beat*3.8,root+12+n,.038)
 if b%4 in [0,2]:note(at+beat*.08,beat*1.6,root+24+[0,7,3,10,7,12,10,3][b//2%8],.055,.12)
 n=int(rate*.15);t=np.arange(n)/rate;fade=np.minimum(t/.004,1)*np.minimum((.15-t)/.018,1)
 drum=(np.sin(2*np.pi*(65*t-95*t*t))*np.exp(-35*t)*.13 if b%2==0 else rng.normal(0,1,n)*np.exp(-65*t)*.015)*fade
 start=int(at*rate);mix[start:start+n]+=drum[:,None]
fade=np.minimum(1,np.minimum(np.arange(len(mix)),np.arange(len(mix))[::-1])/(rate*.04));mix*=fade[:,None]
def wav(path,a):
 a=np.asarray(a);a=np.clip(a,-.9,.9)
 with wave.open(str(path),'wb')as f:f.setnchannels(2 if a.ndim==2 else 1);f.setsampwidth(2);f.setframerate(rate);f.writeframes((a*32767).astype('<i2').tobytes())
wav(r/'music.wav',np.tanh(mix));subprocess.run(['ffmpeg','-y','-loglevel','error','-i',str(r/'music.wav'),'-c:a','libvorbis','-q:a','3',str(r/'afterglow.ogg')],check=True);(r/'music.wav').unlink()
for cue,frequencies,duration in [('select',[880],.09),('deny',[220,196],.18),('slide',[330,660],.25),('land',[220,440],.14),('draw',[523,784],.24),('pass',[440,330],.25),('round',[392,523,659],.6),('win',[523,659,784,1047,1319],1.1)]:
 count=int(rate*duration);t=np.arange(count)/rate;parts=len(frequencies);idx=np.minimum(parts-1,(t/duration*parts).astype(int));frequency=np.array(frequencies)[idx]
 phase=np.cumsum(2*np.pi*frequency/rate);local=np.fmod(t,duration/parts);env=np.minimum(local/.008,1)*np.minimum((duration/parts-local)/.018,1)*np.exp(-t/duration*1.8)
 tone=(np.sin(phase)+.12*np.sin(2*phase))*env*.25
 if cue=='land':tone+=np.sin(2*np.pi*(180*t-180*t*t))*np.exp(-50*t)*np.minimum(t/.004,1)*.11
 wav(r/(cue+'.wav'),tone)
print('Original Afterglow score and eight soft-edged effects encoded')
