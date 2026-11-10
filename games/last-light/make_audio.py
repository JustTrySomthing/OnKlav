"""Render the original clean PCM cues and four synchronized scores and the train bed."""
from pathlib import Path
import math
import wave
from array import array

RATE = 44100
OUT = Path(__file__).parent / "audio"
OUT.mkdir(exist_ok=True)
TAU = math.tau
LENGTH = 16


def write(name, duration, sample):
    pcm = array("h", (int(max(-1, min(1, sample(i / RATE))) * 32767)
                      for i in range(round(duration * RATE))))
    wav = OUT / (name + ".wav")
    with wave.open(str(wav), "wb") as out:
        out.setparams((1, 2, RATE, 0, "NONE", "not compressed"))
        out.writeframes(pcm.tobytes())


def note(t, pitch, length, attack, brightness):
    if not 0 <= t < length:
        return 0.0
    envelope = min(1.0, t / attack, (length - t) / 0.07) * math.exp(-t * 2)
    f = 440 * 2 ** ((pitch - 69) / 12)
    return envelope * (math.sin(TAU * f * t) + brightness * math.sin(TAU * 2 * f * t))


# D minor, Bb major, G minor, A major. Eight bars with a returning three-note motif.
chords = [(38, 57, 62, 65), (34, 53, 58, 62), (31, 55, 58, 62), (33, 57, 61, 64)]
motif = [(0.5, 69), (1.25, 65), (1.75, 64), (4.5, 67), (5.25, 62),
         (6.0, 65), (8.5, 69), (9.25, 70), (10.0, 67), (12.5, 64), (13.25, 61), (14.0, 62)]


def rail(t):
    chord = chords[int(t // 4)]
    local = t % 4
    bass = 0.18 * note(t % 0.5, chord[0], 0.47, 0.03, 0.18)
    bed = sum(0.055 * note(local, pitch, 4.0, 0.55, 0.09) for pitch in chord[1:])
    lead = sum(0.105 * note(t - start, pitch, 0.81, 0.014, 0.23)
               + 0.031 * note(t - start - 0.25, pitch, 0.68, 0.03, 0.13) for start, pitch in motif)
    return bass + bed + lead


def pressure(t):
    pulse = t % 0.5
    # Round off the kick's first transient and omit the sharp offbeat tick.
    attack = math.sin(math.pi * 0.5 * min(pulse / 0.018, 1.0)) ** 2
    kick = 0.34 * attack * math.sin(TAU * (46 * pulse + 3.9 * (1 - math.exp(-31 * pulse)))) * math.exp(-23 * pulse)
    chord = chords[int(t // 4)]
    pulse_note = 0.14 * note((t + 0.25) % 0.5, chord[1], 0.4, 0.025, 0.4)
    return min(1.0, t * 150, (LENGTH - t) * 150) * (kick + pulse_note)


def blackout(t):
    chord = chords[int(t // 4)]
    pad = sum(0.038 * note(t % 4, pitch, 4.0, 0.75, 0.04) for pitch in chord[:3])
    lead = sum(0.061 * note(t - start, pitch - 12, 1.13, 0.10, 0.08) for start, pitch in motif)
    return pad + lead


def warden(t):
    chord = chords[int(t // 4)]
    low = 0.13 * note(t % 0.5, chord[0] - 12, 0.49, 0.065, 0.37)
    brass = sum(0.064 * note(t - start, pitch - 12, 0.79, 0.048, 0.52)
                for start, pitch in motif)
    return 0.70 * rail(t) + 0.47 * pressure(t) + low + brass


def arrangement(t, name):
    phrase = t % LENGTH
    section = int(t // LENGTH)
    chord = chords[int(phrase // 4)]
    local = phrase % 4
    if name == "blackout":
        mix = blackout(phrase)
    elif name == "warden":
        mix = warden(phrase)
    elif name == "storm":
        rhythm = 0.30 * pressure(phrase)
        pulse = 0.09 * note(local % 1.0, chord[0] + 12, .94, .09, .32)
        mix = .78 * rail(phrase) + rhythm + pulse
    else:
        mix = .83 * rail(phrase) + .34 * pressure(phrase)
    if section == 1:
        counterline = sum(0.047 * note(phrase - at - 0.36, pitch + 7, 1.1, .09, .15)
                          for at, pitch in motif[::2])
        weight = .27 if name == "blackout" else .82
        mix = .83 * mix + weight * (counterline + .039 * note(local, chord[1] + 12, 4.0, .45, .12))
    return mix


def train_bed():
    # Low wheel roll and two rounded wheel knocks per rail joint.
    def sample(t):
        turn = .85 + .15 * math.cos(TAU * t * .5)
        roll = turn * (.046 * math.sin(TAU * 34 * t) + .020 * math.sin(TAU * 68 * t))
        joints = 0.0
        for delay, weight in ((0.04, 1.0), (0.17, .68)):
            age = t % .8 - delay
            if age >= 0.0:
                attack = math.sin(math.pi * .5 * min(1.0, age / .023)) ** 2
                joints += weight * attack * math.exp(-age * 20) * (
                    .060 * math.sin(TAU * 83 * age) + .018 * math.sin(TAU * 151 * age))
        return (roll + joints) * min(1.0, t * 25, (8.0 - t) * 25)
    write("train-bed", 8.0, sample)


def jump_sound():
    # A short boot push with a soft rising body. No noise or sharp attack.
    def sample(t):
        attack = math.sin(math.pi * .5 * min(1.0, t / .018)) ** 2
        decay = (1 - t / .18) ** 2
        body = math.sin(TAU * (150 * t + 300 * t * t))
        sole = math.sin(TAU * 88 * t) * math.exp(-34 * t)
        return attack * decay * (.17 * body + .07 * sole)
    write("jump", .18, sample)


def signal(name, duration, tones, gain):
    def sample(t):
        envelope = min(1.0, t / 0.045) * (1 - t / duration) ** 1.3
        return gain * envelope * sum(
            weight * math.sin(TAU * (start * t + (end - start) * t * t / (2 * duration)))
            for start, end, weight in tones
        )
    write(name, duration, sample)


def cue(name, seconds, start, end, body, gain):
    def sample(t):
        contour = min(1.0, t / 0.012) * (1 - t / seconds) ** 2
        phase = TAU * (start * t + (end - start) * t * t / (2 * seconds))
        color = math.sin(phase) + 0.22 * math.sin(2.02 * phase)
        return gain * contour * (0.8 * color + body * math.sin(0.54 * phase))
    write(name, seconds, sample)


if __name__ == "__main__":
    for name in ("rail", "blackout", "warden", "storm"):
        write(name, LENGTH * 2, lambda t: arrangement(t, name))
    train_bed()
    jump_sound()
    signal("block", .19, [(470, 350, .55), (680, 530, .22)], .36)
    signal("impact", .17, [(180, 95, .70), (310, 160, .20)], .43)
    signal("gust", 0.82, [(310, 780, .59), (478, 1190, .21)], .42)
    signal("groan", 0.76, [(187, 65, .52), (293, 99, .27), (431, 147, .13)], .54)
    signal("sweep", 0.69, [(760, 230, .67), (1150, 310, .23)], .46)
    signal("charge", 0.78, [(96, 319, .64), (198, 630, .29)], .51)
    signal("furnace", 0.48, [(523, 523, .45), (784, 784, .32), (1047, 1047, .13)], .47)
    for spec in [("tether", 0.29, 285, 585, 0.16, 0.32),
                 ("strike", 0.23, 220, 80, 0.57, 0.78),
                 ("hit", 0.28, 103, 39, 0.34, 0.70),
                 ("switch", 0.64, 402, 610, 0.12, 0.52),
                 ("warn", 0.56, 534, 520, 0.05, 0.56),
                 ("pickup", 0.26, 540, 980, 0.07, 0.58),
                 ("win", 1.15, 392, 1047, 0.16, 0.68)]:
        cue(*spec)
