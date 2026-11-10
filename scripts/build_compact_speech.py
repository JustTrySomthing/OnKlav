#!/usr/bin/env python3
"""Derive the two selected English voices without changing the FP32 graph.

Build-time dependency: onnx==1.18.0 (and its numpy/protobuf dependencies).
Input: the pinned upstream model tar.bz2 or the original eight-voice ZIP pack.
Runtime requires neither Python nor this tool's dependencies.
"""
import argparse
import hashlib
import io
import json
from pathlib import Path
import tarfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SELECTED = (0, 2)
ENGLISH_DATA = {
    "espeak-ng-data/en_dict", "espeak-ng-data/intonations",
    "espeak-ng-data/phondata", "espeak-ng-data/phondata-manifest",
    "espeak-ng-data/phonindex", "espeak-ng-data/phontab",
    "espeak-ng-data/lang/gmw/en", "espeak-ng-data/lang/gmw/en-US",
}


def sha(data):
    return hashlib.sha256(data).hexdigest()


def compact(source):
    import onnx
    deps = json.loads((ROOT / "docs/speech-dependencies.json").read_text())
    upstream = deps["model"]["kitten-nano-en-v0_8-fp32"]
    if zipfile.is_zipfile(source):
        with zipfile.ZipFile(source) as z:
            files = {n: z.read(n) for n in z.namelist() if not n.endswith("/")}
    else:
        with tarfile.open(source) as t:
            files = {m.name.split("/", 1)[1]: t.extractfile(m).read()
                     for m in t.getmembers() if m.isfile() and "/" in m.name}
    # Verify every retained upstream byte before applying the documented derivation.
    retained = ENGLISH_DATA | {"model.fp32.onnx", "voices.bin", "tokens.txt", "LICENSE"}
    for name in retained:
        assert sha(files[name]) == upstream["files"][name]["sha256"], name
    original = onnx.load_model_from_string(files["model.fp32.onnx"])
    model = onnx.load_model_from_string(files["model.fp32.onnx"])
    metadata = {item.key: item.value for item in model.metadata_props}
    assert int(metadata["n_speakers"]) == 8
    rows, width = map(int, metadata["style_dim"].split(","))
    stride = rows * width * 4
    voices = files["voices.bin"]
    assert len(voices) == 8 * stride
    selected_names = [metadata["speaker_names"].split(",")[i] for i in SELECTED]
    metadata.update({
        "n_speakers": "2",
        "speaker_names": ",".join(selected_names),
        "speaker2id": ",".join(f"{n}->{i}" for i, n in enumerate(selected_names)),
        "id2speaker": ",".join(f"{i}->{n}" for i, n in enumerate(selected_names)),
        "speaker_speed_priors": ",".join(metadata["speaker_speed_priors"].split(",")[i] for i in SELECTED),
    })
    for item in model.metadata_props:
        item.value = metadata[item.key]
    # No quantization, tensor edits, frontend substitution or graph optimization.
    assert model.graph.SerializeToString() == original.graph.SerializeToString()
    result = {n: files[n] for n in retained}
    result["model.fp32.onnx"] = model.SerializeToString()
    result["voices.bin"] = b"".join(voices[i * stride:(i + 1) * stride] for i in SELECTED)
    assert len(result["voices.bin"]) == 2 * stride
    result["README.md"] = (
        "OnKlav English-only Kitten Nano v0.8 FP32.\n"
        "Original upstream speakers 0 and 2 occupy compact runtime rows 0 and 1.\n"
        "Only speaker metadata, voice-bank rows and unused language data change.\n"
        "The FP32 graph, selected voice rows, speed priors and English data are unchanged.\n"
        "Reproduction: scripts/build_compact_speech.py; provenance: speech/english.json.\n"
    ).encode()
    manifest = {
        "url": upstream["url"], "upstream_archive_sha256": upstream["archive_sha256"],
        "upstream_archive_bytes": upstream["archive_bytes"], "format": "nano-fp32-english-two-v1",
        "upstream_speakers": list(SELECTED), "runtime_speakers": {"0": 0, "2": 1},
        "graph_sha256": sha(original.graph.SerializeToString()),
        "source_model_sha256": sha(files["model.fp32.onnx"]),
        "source_voicebank_sha256": sha(voices),
        "voice_row_bytes": stride,
        "files": {n: {"sha256": sha(b), "bytes": len(b)} for n, b in sorted(result.items())},
    }
    out = io.BytesIO()
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for name, data in sorted(result.items()):
            entry = zipfile.ZipInfo(name, (2026, 1, 1, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(entry, data, compresslevel=9)
    packed = out.getvalue()
    manifest.update(archive_sha256=sha(packed), archive_bytes=len(packed))
    return packed, manifest


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--source", required=True, type=Path)
    a = p.parse_args()
    packed, manifest = compact(a.source)
    folder = ROOT / "android/shared/src/main/assets/speech"
    (folder / "english.voicepack").write_bytes(packed)
    (folder / "english.json").write_text(json.dumps(manifest, indent=2) + "\n")
    deps = json.loads((ROOT / "docs/speech-dependencies.json").read_text())
    deps["voice_assets"] = {str(p.relative_to(ROOT)): sha(p.read_bytes()) for p in sorted(folder.iterdir())}
    (ROOT / "docs/speech-dependencies.json").write_text(json.dumps(deps, indent=2) + "\n")
    print(json.dumps({"pack_bytes": len(packed), "files": len(manifest["files"]),
                      "voicebank_bytes": manifest["files"]["voices.bin"]["bytes"],
                      "graph_sha256": manifest["graph_sha256"]}))


if __name__ == "__main__":
    main()
