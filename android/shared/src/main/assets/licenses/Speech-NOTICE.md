Offline English speech: Kitten Nano0.8 FP32, speakers0 and2 (user-facing labels Female and Male respectively); original model license Apache2.0. https://github.com/KittenML/KittenTTS
Sherpa-ONNX official Rust/C API1.13.8: Apache2.0; original native Piper/eSpeak pronunciation frontend. https://github.com/k2-fsa/sherpa-onnx/tree/v1.13.8
ONNX Runtime: MIT. https://github.com/microsoft/onnxruntime
Piper phonemizer / eSpeak NG and pronunciation data: GPL3.0 or later. https://github.com/rhasspy/piper-phonemize ; https://github.com/espeak-ng/espeak-ng
The GPL combined-distribution and corresponding-source requirements already documented for OnKlav's NewPipe dependency also apply to this native speech distribution. Preserve licenses, pinned dependency/source provenance and corresponding source with distributed builds. Do not describe the whole native runtime as Apache2.0 solely because Sherpa/model use that license.
This voice is generated entirely offline. Generated speech audio is held in memory, not written as playback files. Reading-position highlighting is estimated from played PCM and text weights, not native forced-alignment word timestamps.

OnKlav compact distribution: only original speaker rows0/2 and English pronunciation files. ONNX metadata reindexes two rows; FP32 graph/tensor weights, selected style rows and speed priors are preserved. Derivation/build provenance: scripts/build_compact_speech.py and assets/speech/english.json.
