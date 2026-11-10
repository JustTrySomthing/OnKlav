# Recorded instrument credits

Bundled game soundtrack production used instrument recordings from [Versilian Studios Chamber Orchestra 2 Community Edition](https://github.com/sgossner/VSCO-2-CE/tree/440300901dfe9275fd84e0b7763af1f8443ae62e), distributed under [CC0 1.0](Music-Instruments-CC0.txt). Source paths below are relative to the pinned repository's `Samples` directory. OnKlav uses shortened, level-adjusted, mono 22.05 kHz PCM versions of these recordings.

| OnKlav sample | Source WAV |
| --- | --- |
| `cello-low.wav` | `Strings/Cello Section/susvib/susvib_D2_v1_1.wav` |
| `cello-high.wav` | `Strings/Cello Section/susvib/susvib_C3_v1_1.wav` |
| `horn-low.wav` | `Brass/F Horn/sus/MOHorn_sus_F2_v2_1.wav` |
| `horn-high.wav` | `Brass/F Horn/sus/MOHorn_sus_D4_v1_1.wav` |
| `reed-low.wav` | `Woodwinds/Clarinet/susLong/DCClar_susLong_D3_v3_rr1_sum.wav` |
| `reed-high.wav` | `Woodwinds/Clarinet/susLong/DCClar_susLong_F3_v3_rr1_sum.wav` |
| `timpani.wav` | `Percussion/Timpani/Timpani2_Hit_v3_rr1_Sum.wav` |
| `cello-deep.wav` | `Strings/Cello Section/susvib/susvib_C1_v1_1.wav` |

## Studio expansion

These additional recordings use the same pinned CC0 VSCO 2 CE source. Files are cropped to at most 2.4 seconds, mixed to mono at 22.05 kHz, DC-corrected, level-adjusted, and faded at their ends.  Root pitches were checked against the audio; a strong second harmonic in the low pizzicato sample required manual octave correction. 

| OnKlav sample | Source WAV | Root MIDI |
| --- | --- | ---: |
| `flute-low.wav` | `Woodwinds/Flute/susNV/LDFlute_susNV_C3_v3_1.wav` | 60 |
| `flute-high.wav` | `Woodwinds/Flute/susNV/LDFlute_susNV_C4_v3_1.wav` | 72 |
| `oboe-low.wav` | `Woodwinds/Oboe/Sus/Oboe_Sus_D3_v1_Main.wav` | 62 |
| `oboe-high.wav` | `Woodwinds/Oboe/Sus/Oboe_Sus_D4_v1_Main.wav` | 74 |
| `bassoon.wav` | `Woodwinds/Bassoon/sus/PSBassoon_C2_v1_1.wav` | 48 |
| `trumpet-low.wav` | `Brass/Trumpet/sus/Sum_SHTrumpet_sus_C3_v3_rr1.wav` | 60 |
| `trumpet-high.wav` | `Brass/Trumpet/sus/Sum_SHTrumpet_sus_A4_v1_rr1.wav` | 81 |
| `violin-low.wav` | `Strings/Violin Section/susVib/VlnEns_susVib_A2_v1.wav` | 57 |
| `violin-high.wav` | `Strings/Violin Section/susVib/VlnEns_susVib_A3_v1.wav` | 69 |
| `pizz-low.wav` | `Strings/Violin Section/Pizz/VlnEns_Pizz_A2_v2_rr1.wav` | 57 |
| `pizz-high.wav` | `Strings/Violin Section/Pizz/VlnEns_Pizz_A3_v2_rr1.wav` | 69 |
| `harp-low.wav` | `Strings/Harp/KSHarp_C3_mf.wav` | 48 |
| `harp-high.wav` | `Strings/Harp/KSHarp_C5_mf.wav` | 72 |
| `marimba-low.wav` | `Percussion/Marimba/Marimba_hit_Outrigger_C2_loud_01.wav` | 48 |
| `marimba-high.wav` | `Percussion/Marimba/Marimba_hit_Outrigger_C4_loud_01.wav` | 72 |
| `crash.wav` | `Percussion/cymbal-crash1_mf_rr1.wav` | 49 |
| `snare.wav` | `Percussion/Snare2-HitSN_v3_rr1_Sum.wav` | 38 |

## Expressive upright piano revision

Twelve samples from VSCO 2 CE, revision 440300901dfe9275fd84e0b7763af1f8443ae62e, originally sampled by Simon Dalzell of Ivy Audio (Winter 2015–16), distributed by Versilian Studios. The instrument's Info.txt expressly permits redistribution. Repository CC0 notice retained in Music-Instruments-CC0.txt.

Source: Keys/Upright Piano/Player_dyn{1,3}_rr1_{008,012,016,020,024,028}.wav. The supplied MappingChart.txt identifies root MIDI keys 37,45,53,61,69,77. Soft and hard playing are separate recordings, blended according to velocity; not just amplitude changes. Converted to mono 22.05 kHz, trimmed, DC corrected, bounded to 2.4 seconds and faded. These are compact upright-piano samples, not a full concert-grand library.
