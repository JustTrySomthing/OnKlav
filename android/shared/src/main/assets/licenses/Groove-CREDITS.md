# Groove MIDI Dataset attribution

Derived timing and relative-velocity statistics from the Groove MIDI Dataset v1.0.0, Google LLC, released under Creative Commons Attribution 4.0 International. Source: https://magenta.tensorflow.org/datasets/groove . License: https://creativecommons.org/licenses/by/4.0/ ; original dataset license notice in Groove-CC-BY-4.txt.

Jon Gillick, Adam Roberts, Jesse Engel, Douglas Eck, and David Bamman. “Learning to Groove with Inverse Sequence Transformations.” ICML, 2019.

Changes by OnKlav: selected 4/4 beat performances, mapped five drum families, derived timing deviations and per-performance centered velocities, fitted 560 conditional means (including 80 genre-pooled timing cells) on the official training split, shrank toward neutral performance, bounded and quantized the results. Original MIDI and audio are not included. Validation and test were evaluation-only. This statistical performance model is not the original GrooVAE neural model and does not generate or reproduce recorded drum phrases.
