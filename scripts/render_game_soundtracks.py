#!/usr/bin/env python3
"""Render original, deterministic stereo loops. Requires NumPy and ffmpeg."""
from pathlib import Path
import subprocess

import numpy as np

RATE = 32000
ROOT = Path(__file__).resolve().parents[1] / "games/engine-checks"
BARS = 24

# Chord roots, voicings, and recurring motifs in semitones.
SCORES = {
    "naval_battle/audio/fleet-command.ogg": (88, "fleet", [38, 34, 41, 36, 38, 31, 34, 33],
        [(0,3,7), (0,4,7), (0,4,7), (0,4,7), (0,3,7), (0,3,7), (0,4,7), (0,4,7)],
        [0,7,12,10,7,3,5,7]),
    "dominos/audio/afterglow.ogg": (105, "wave", [45, 41, 48, 43, 45, 48, 41, 43],
        [(0,3,7,10), (0,4,7,11), (0,4,7,11), (0,4,7,10)] * 2,
        [12,7,15,19,14,12,7,10]),
    "air_hockey/audio/neon-rally.ogg": (152, "rally", [40, 40, 43, 38, 40, 47, 45, 38],
        [(0,3,7), (0,3,7), (0,4,7), (0,4,7)] * 2,
        [7,12,15,12,19,15,14,7]),
    "cat_dog/audio/playroom.ogg": (116, "play", [48, 45, 41, 43, 48, 52, 41, 43],
        [(0,4,7), (0,3,7), (0,4,7), (0,4,7)] * 2,
        [12,16,19,21,19,16,14,7]),
    "blackjack/audio/velvet-table.ogg": (99, "club", [50, 43, 48, 45, 50, 43, 48, 45],
        [(0,3,7,10), (0,4,7,10), (0,4,7,11), (0,3,7,10)] * 2,
        [15,19,22,21,16,14,12,10]),
}


def render(bpm, style, roots, chords, motif):
    beat = 60 / bpm
    frames = round(BARS * 4 * beat * RATE)
    output = np.zeros((frames, 2), dtype=np.float32)
    random = np.random.default_rng(42)
    tau = 2 * np.pi

    def mix(signal, start, volume, pan=0):
        first = round(start * beat * RATE) % frames
        gains = volume * np.sqrt([(1 - pan) / 2, (1 + pan) / 2])
        length = min(frames - first, len(signal))
        output[first:first + length] += signal[:length, None] * gains
        if length < len(signal):
            output[:len(signal) - length] += signal[length:, None] * gains

    def note(start, duration, midi, voice, volume, pan=0):
        t = np.arange(round(duration * beat * RATE)) / RATE
        phase = tau * 440 * 2 ** ((midi - 69) / 12) * t
        attack, release = 0.012, 0.045
        if voice == "pad":
            wave = (np.sin(phase) + .48 * np.sin(phase * 1.004)
                    + .23 * np.sin(phase * 2.003) + .14 * np.sin(phase * 3))
            attack, release = .27, .36
        elif voice == "brass":
            wave = (np.sin(phase + .48 * np.sin(phase * 2))
                    + .36 * np.sin(phase * 2) + .24 * np.sin(phase * 3))
            wave *= .87 + .13 * np.sin(tau * 4.7 * t)
            attack, release = .06, .14
        elif voice == "piano":
            wave = (np.sin(phase) * np.exp(-3.6 * t)
                    + .48 * np.sin(phase * 2.003) * np.exp(-6 * t)
                    + .24 * np.sin(phase * 3.012) * np.exp(-10 * t))
        elif voice == "bell":
            wave = (np.sin(phase) + .36 * np.sin(phase * 2.76) * np.exp(-12 * t)) * np.exp(-5 * t)
        elif voice == "bass":
            wave = (np.sin(phase) + .2 * np.sin(phase * 2)) * np.exp(-2.1 * t)
        else:
            wave = np.sin(phase) + .26 * np.sin(phase * 2) + .22 * np.sin(phase * 3)
            wave *= .95 + .05 * np.sin(tau * 6 * t)
        envelope = np.minimum(t / attack, 1) * np.clip((len(t) / RATE - t) / release, 0, 1)
        mix(wave * envelope, start, volume, pan)

    def drum(start, kind, volume, pan=0):
        length = {"kick": .32, "snare": .17, "hat": .075, "tom": .46}[kind]
        t = np.arange(round(length * RATE)) / RATE
        noise = random.uniform(-1, 1, len(t))
        if kind in ("kick", "tom"):
            low = 46 if kind == "kick" else 67
            wave = np.sin(tau * (low * t + 5 * (1 - np.exp(-35 * t)))) * np.exp(-t * (13 if kind == "kick" else 9))
        elif kind == "snare":
            wave = (.74 * noise + .26 * np.sin(tau * 168 * t)) * np.exp(-t * 24)
        else:
            wave = np.diff(noise, prepend=0) * np.exp(-t * 70)
        mix(wave * np.minimum(t / .004, 1) * np.clip((length - t) / .018, 0, 1), start, volume, pan)

    for bar in range(BARS):
        at, root, chord = bar * 4, roots[bar % 8], chords[bar % 8]
        section = bar // 8
        if style == "fleet":
            for index, interval in enumerate(chord):
                note(at, 4.35, root + 12 + interval, "pad", .055, (index - 1) * .48)
            for beat_at in [0, 1.5, 2.5, 3.5] if section else [0, 2.5]:
                note(at + beat_at, .47, root - 2 if beat_at == 3.5 else root, "brass", .11, -.15)
                drum(at + beat_at, "tom", .2)
            if section != 1 or bar % 2:
                for j, position in enumerate([0, .75, 2, 3]):
                    pitch = root + 12 + motif[(bar + j) % 8]
                    note(at + position, [.64, 1.12, .84, .83][j], pitch, "brass", .13, .23)
                    if section == 2:
                        note(at + position, .63, pitch - 12, "brass", .065, -.3)
            drum(at + 2, "snare", .08, .2)
        elif style in ("wave", "rally"):
            rally = style == "rally"
            for index, interval in enumerate(chord):
                note(at + .12, 3.95, root + 12 + interval, "pad", .046 if rally else .066, (index - 1.5) * .39)
            for j in range(8):
                interval = [0, 0, 12, 0, 7, 0, 12, 7][j] if rally else [0, 0, 0, 12, 0, 0, 7, 12][j]
                note(at + j / 2, .44, root - 12 + interval, "bass", .20 if j % 2 == 0 else .12)
                if section != 1 or j % 2:
                    note(at + j / 2 + .06, .28 if rally else .38, root + 12 + chord[(j + bar) % len(chord)], "synth" if rally else "bell", .06, -.42 if j % 2 else .42)
                if section != 1 or j % 2:
                    drum(at + j / 2 + .03, "hat", .043 if j % 2 else .023, .28)
            for position in ([0, 1.5, 2.75] if rally else [0, 1, 2, 3]):
                drum(at + position, "kick", .26)
            for position in (1, 3):
                drum(at + position, "snare", .14)
            if section != 1 or bar % 2 == 0:
                for j, position in enumerate([.5, 1.25, 2.5] if rally else [0, 1.5, 2.25]):
                    note(at + position, .56 if rally else .85, root + 12 + motif[(2 * bar + j + section) % 8], "synth", .093, .12)
        else:
            jazz = style == "club"
            for j in range(4):
                pitch = root - 12 + ([0, 7, 10, 11][j] if jazz else [0, 0, 7, 0][j])
                note(at + j, .67 if jazz else .5, pitch, "bass", .15)
                drum(at + j + (.65 if jazz else .5), "hat", .035 if jazz else .025, .28)
            for position in ([.65, 2.65] if jazz else [.5, 2.5]):
                for i, interval in enumerate(chord):
                    note(at + position + i * .022, .75, root + interval + (12 if jazz else 0), "piano", .045, -.32 + i * .15)
            for j, position in enumerate([0, .66, 1.67, 2.65, 3.4] if jazz else [0, .5, 1.5, 2, 3]):
                if section == 1 and j in [1, 3]:
                    continue
                pitch = root + 12 + motif[(bar * 2 + j + section) % 8]
                note(at + position, .58 if jazz else .42, pitch, "piano" if jazz else "bell", .13 if j == 0 else .094, .2)
            drum(at + 2.85, "snare" if jazz else "tom", .047, -.2)

    # Wrap stereo reflections at the loop boundary instead of cutting tails.
    dry = output.copy()
    for delay, gain in [(.055, .11), (.116, .085), (.193, .055), (.29, .032)]:
        output += np.roll(dry[:, ::-1], round(delay * RATE), axis=0) * gain
    output = np.tanh(output * 1.4)
    output *= .82 / np.max(np.abs(output))
    return np.asarray(output * 32767, dtype="<i2")


if __name__ == "__main__":
    for name, score in SCORES.items():
        target = ROOT / name
        target.parent.mkdir(parents=True, exist_ok=True)
        audio = render(*score)
        subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
                        "-f", "s16le", "-ar", str(RATE), "-ac", "2", "-i", "-",
                        "-c:a", "libvorbis", "-q:a", "4", str(target)],
                       input=audio.tobytes(), check=True)
        print(f"{name}: {len(audio) / RATE:.1f} seconds")
