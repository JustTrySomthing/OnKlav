from pathlib import Path
import numpy as np,wave,subprocess
r=Path(__file__).resolve().parents[3]/'games/engine-checks/air_hockey/audio';rate=24000;beat=60/124;duration=beat*80;n=int(duration*rate);mix=np.zeros((n,2));rng=np.random.default_rng(2207)
def note(at,dur,midi,gain,voice=0,pan=0):
 count=min(int(dur*rate),n-int(at*rate));t=np.arange(count)/rate;f=440*2**((midi-69)/12);u=t/dur
 env=np.minimum(t/(.09 if voice==1 else .008),1)*np.minimum((dur-t)/.08,1)*np.exp(-u*(1.4 if voice==1 else 4))
 tone=np.sin(2*np.pi*f*t)+(.19 if voice==1 else .3)*np.sin(4*np.pi*f*t)+.09*np.sin(6*np.pi*f*t)
 signal=tone*env*gain;start=int(at*rate);mix[start:start+count,0]+=signal*.707*(1-pan);mix[start:start+count,1]+=signal*.707*(1+pan)
melody=[76,79,83,86,83,79,76,-1,74,78,81,86,84,81,78,-1,72,76,79,84,83,79,76,-1,74,78,81,83,86,83,78,-1]
for b in range(80):
 at=b*beat;root=[40,38,36,38][(b//8)%4]
 if b%2==0:note(at,beat*1.65,root,.23)
 if b%4==0:
  for chord in [0,7,12,15]:note(at,beat*3.9,root+12+chord,.047,1,(-.3 if chord%2 else .3))
 if b>=8:note(at,beat*.85,melody[(b-8)%32],.17,1,-.1)
 for sub in range(2):note(at+sub*beat*.5,beat*.42,root+24+[0,7,12,7][(b*2+sub)%4],.052,0,(-.3 if sub else .3))
 count=int(rate*.16);t=np.arange(count)/rate
 drum=np.sin(2*np.pi*(61*t-100*t*t))*np.exp(-28*t)*.27 if b%2==0 else rng.normal(0,1,count)*np.exp(-35*t)*.045
 start=int(at*rate);mix[start:start+count]+=drum[:,None]
 if b%8==7:
  for sub in [0,.5,.75]:
   count=int(rate*.07);t=np.arange(count)/rate;v=rng.normal(0,1,count)*np.exp(-60*t)*.024;start=int((at+sub*beat)*rate);mix[start:start+count]+=v[:,None]
# Low ocean air under the score; deterministic, softly filtered noise.
noise=rng.normal(0,1,n);ocean=np.convolve(noise,np.ones(100)/100,mode='same')*.055;mix+=ocean[:,None]
fade=np.minimum(1,np.minimum(np.arange(n),np.arange(n)[::-1])/(rate*.05));mix*=fade[:,None];mix=np.tanh(mix*1.15);mix*=.86/max(.86,np.max(np.abs(mix)))
p=r/'neon-rally.wav'
with wave.open(str(p),'wb')as f:f.setnchannels(2);f.setsampwidth(2);f.setframerate(rate);f.writeframes((mix*32767).astype('<i2').tobytes())
subprocess.run(['ffmpeg','-loglevel','error','-y','-i',str(p),'-c:a','libvorbis','-q:a','4',str(r/'neon-rally.ogg')],check=True);p.unlink()
print('Neon Rally original upbeat hockey score generated')
