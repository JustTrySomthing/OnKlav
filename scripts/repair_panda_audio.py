"""Deterministic, softly enveloped Panda impact cues without harsh white-noise bursts."""
from pathlib import Path
import wave
import numpy as np

ROOT=Path(__file__).resolve().parents[1]/'games/engine-checks/panda/audio'
RATE=32000
rng=np.random.default_rng(5920)

def save(name,duration,start,end,noise=0.0):
    t=np.arange(int(RATE*duration))/RATE
    progress=t/duration
    frequency=start+(end-start)*progress
    phase=np.cumsum(2*np.pi*frequency/RATE)
    signal=np.sin(phase)+.13*np.sin(phase*2)+.05*np.sin(phase*3)
    if noise:
        smooth=np.convolve(rng.normal(size=len(t)),np.ones(48)/48,mode='same')
        signal+=noise*smooth
    envelope=np.minimum(t/.012,1)*np.minimum((duration-t)/.035,1)*np.exp(-progress*3)
    signal*=np.maximum(envelope,0)
    signal*=.44/max(1,np.max(np.abs(signal)))
    signal[0]=signal[-1]=0
    with wave.open(str(ROOT/name),'wb') as output:
        output.setnchannels(1);output.setsampwidth(2);output.setframerate(RATE)
        output.writeframes(np.round(signal*32767).astype('<i2').tobytes())

save('panda_launch.wav',.26,210,65,.12)
save('panda_hit.wav',.19,260,115,.08)
save('panda_block.wav',.13,330,155,.05)
save('duel_power.wav',.50,95,42,.18)
