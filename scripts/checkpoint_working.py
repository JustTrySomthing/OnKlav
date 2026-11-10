"""Save an incremental source checkpoint without downloading tools or building releases.

Overlay tracked members of an existing checkpoint; name new files with --include.
Native proofs are preserved and any mismatch is recorded, never silently repaired.
The caller must save the resulting archive durably after this command succeeds.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import tempfile
import zipfile


def safe_name(name):
    p = PurePosixPath(name)
    if p.is_absolute() or not name or "\\" in name or any(x in (".", "..") for x in name.split("/")):
        raise ValueError("Unsafe checkpoint path")
    return name


def sha(data):
    return hashlib.sha256(data).hexdigest()


def native_status(root):
    proof = root / "docs/working-native-verification.json"
    if not proof.is_file():
        return {"matches": False, "reason": "working native proof absent"}
    v = json.loads(proof.read_text())
    expected = dict(v["core_source_sha256"])
    expected["core/host-libs/linux-x86_64/libonklav_core.so"] = v["linux_jni_sha256"]
    expected["android/shared/src/diagnostic/jniLibs/arm64-v8a/libonklav_core.so"] = v["arm64_jni_sha256"]
    changed = [n for n, h in expected.items() if not (root / safe_name(n)).is_file() or sha((root / n).read_bytes()) != h]
    actual = {str(p.relative_to(root)) for p in (root / "core/src").rglob("*.rs")}
    changed += sorted(actual - set(expected))
    return {"matches": not changed, "rebuild_required": bool(changed), "changed_or_missing": sorted(set(changed))}


def create(root, base, output, include, delete=()):
    root, base, output = map(lambda p: Path(p).resolve(), (root, base, output))
    if base == output:
        raise ValueError("Use a separate output so the last saved checkpoint remains intact")
    with zipfile.ZipFile(base) as old:
        complete_base = "checkpoint.json" not in old.namelist()
        if "checkpoint.json" in old.namelist():
            checkpoint = json.loads(old.read("checkpoint.json"))
        elif "OnKlav/HANDOFF-MANIFEST.json" in old.namelist():
            handoff = json.loads(old.read("OnKlav/HANDOFF-MANIFEST.json"))
            hashes = handoff["files_sha256"]
            if any(not n.startswith("OnKlav/") for n in hashes):
                raise ValueError("Unexpected handoff hash path")
            checkpoint = {"updated_sha256": {n[len("OnKlav/"):]: h for n, h in hashes.items()},
                          "new_files": [], "base_format": "complete_handoff"}
        else:
            raise ValueError("Base has neither checkpoint.json nor a complete handoff manifest")
        members = {}
        for entry in old.infolist():
            if entry.is_dir() or entry.filename == "checkpoint.json":
                continue
            safe_name(entry.filename)
            if not entry.filename.startswith("OnKlav/") or entry.filename in members:
                raise ValueError("Unexpected or duplicate checkpoint member")
            members[entry.filename] = old.read(entry)
        if complete_base:
            expected = {"OnKlav/" + safe_name(n) for n in checkpoint["updated_sha256"]}
            if expected != set(members) - {"OnKlav/HANDOFF-MANIFEST.json"}:
                raise ValueError("Handoff manifest inventory mismatch")
        for n, h in checkpoint["updated_sha256"].items():
            if sha(members["OnKlav/" + safe_name(n)]) != h:
                raise ValueError("Base checkpoint hash mismatch: " + n)
    for n in include:
        safe_name(n)
        p = PurePosixPath(n)
        vendor_manifest = root / "docs/vendor-dependencies.json"
        vendor_hashes = json.loads(vendor_manifest.read_text()) if vendor_manifest.is_file() else {}
        vendored_asset = n in vendor_hashes and sha((root / n).read_bytes()) == vendor_hashes[n]
        evidence_image = n.startswith("docs/evidence/") and p.suffix == ".png"
        game_proof = root / "docs/games-provenance.json"
        game_hashes = json.loads(game_proof.read_text()).get("assets_sha256", {}) if game_proof.is_file() else {}
        game_asset = n in game_hashes and sha((root / n).read_bytes()) == game_hashes[n]
        game_source = n in {"games/engine-checks/onklav-splash.png", "developer/games/templates/shared/onklav-splash.png"} or p.parts[0] in {"games", "developer"} and p.suffix in {".gd", ".tscn", ".cfg", ".godot", ".uid", ".svg", ".import", ".wav", ".webp", ".ogg", ".css", ".js", ".html"}
        game_fixture = n in {
            "docs/fixtures/games/tap.onklav-g",
            "android/games/src/main/assets/included-games/shroom.zip",
            "android/games/src/main/assets/included-games/last-light.zip",
        }
        shroom_source = p.parts[:2] == ("games", "shroom") and (
            p.suffix == ".png" or (
                p.name == ".onklav-web-template.zip"
                and sha((root / n).read_bytes()) == sha((root / "developer/games/engine-template.zip").read_bytes())
            )
        )
        catalog_asset = n in {"android/app/src/main/assets/radio/catalog.json.gz", "android/app/src/main/assets/catalog/panda.webp", "android/app/src/main/assets/catalog/dominos.webp", "android/app/src/main/assets/catalog/shroom.webp", "android/app/src/main/assets/catalog/last-light.webp"}
        if not p.parts or p.parts[0] not in {"core", "android", "developer", "scripts", "docs", "sdk", "third_party", "games"} or (p.suffix not in {".py", ".rs", ".kt", ".xml", ".json", ".md", ".log", ".onklav-a", ".toml", ".lock", ".txt"} and not catalog_asset and not vendored_asset and not evidence_image and not game_asset and not game_source and not game_fixture and not shroom_source) or any(x in {"build", "target", "keys", ".gradle"} for x in p.parts):
            raise ValueError("New checkpoint entries must be explicit source or evidence files")
        members.setdefault("OnKlav/" + n, b"")
    deleted = set(checkpoint.get("deleted_files", [])) | {safe_name(n) for n in delete}
    checkpoint["deleted_files"] = sorted(deleted)
    for member in list(members):
        n = member[len("OnKlav/"):]
        p = root / n
        if n in deleted:
            del members[member]
            continue
        if not p.is_file() or p.is_symlink() or not p.resolve().is_relative_to(root):
            raise ValueError("Tracked source is absent or unsafe; record intentional removals first: " + n)
        members[member] = p.read_bytes()
    if "OnKlav/HANDOFF-MANIFEST.json" in members:
        handoff = json.loads(members["OnKlav/HANDOFF-MANIFEST.json"])
        handoff["files_sha256"] = {n: sha(data) for n, data in sorted(members.items()) if n != "OnKlav/HANDOFF-MANIFEST.json"}
        members["OnKlav/HANDOFF-MANIFEST.json"] = (json.dumps(handoff, indent=2) + "\n").encode()
    checkpoint["updated_sha256"] = {n[len("OnKlav/"):]: sha(data) for n, data in sorted(members.items())}
    checkpoint["new_files"] = sorted(set(checkpoint.get("new_files", [])) | set(include))
    checkpoint["native_consistency_at_save"] = native_status(root)
    for name, field in [("docs/developer-improvement-progress.json", "developer_improvements"), ("docs/working-native-verification.json", "working_native_verification")]:
        checkpoint[field] = json.loads((root / name).read_text())
    latest = checkpoint["developer_improvements"].get("latest_review")
    if latest:
        if "OnKlav/" + safe_name(latest) not in members:
            raise ValueError("Include the latest verification report in the checkpoint: " + latest)
        checkpoint["latest_increment"] = latest
    output.parent.mkdir(parents=True, exist_ok=True)
    fd, temp = tempfile.mkstemp(prefix="checkpoint-", suffix=".zip", dir=output.parent)
    try:
        with os.fdopen(fd, "wb") as f:
            with zipfile.ZipFile(f, "w", zipfile.ZIP_DEFLATED) as archive:
                for name, data in sorted(members.items()):
                    archive.writestr(name, data)
                archive.writestr("checkpoint.json", json.dumps(checkpoint, indent=2) + "\n")
            f.flush()
            os.fsync(f.fileno())
        with zipfile.ZipFile(temp) as archive:
            if archive.testzip() is not None:
                raise ValueError("Checkpoint verification failed")
        os.replace(temp, output)
    finally:
        Path(temp).unlink(missing_ok=True)
    return checkpoint["native_consistency_at_save"]


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--base", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--include", action="append", default=[])
    parser.add_argument("--delete", action="append", default=[])
    args = parser.parse_args()
    print(json.dumps(create(args.root, args.base, args.output, args.include, args.delete), indent=2))
