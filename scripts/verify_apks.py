#!/usr/bin/env python3
"""Check the single OnKlav host APK, including defined DEX classes and Rust bytes."""
import io
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import struct
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--variant", choices=["debug", "optimized"], default="debug")
parser.add_argument("--abis", default="arm64-v8a")
args = parser.parse_args()
expected_abis = args.abis.split(",")
assert expected_abis and len(set(expected_abis)) == len(expected_abis) and set(expected_abis) <= {"arm64-v8a", "x86_64"}
SDK = Path(os.environ["ANDROID_HOME"])
BUILD_TOOLS = SDK / "build-tools/35.0.0"
CERTIFICATE = "6ed975c5fc27d19f6291f751a8bb00fa8a2c2e7d268bffc7b6ddf7e1b1a7a69d"

def command(*args):
    return subprocess.check_output([str(a) for a in args], text=True, stderr=subprocess.STDOUT)

def sha(data):
    return hashlib.sha256(data).hexdigest()

def elf_load_alignment(data):
    assert data[:4] == b"\x7fELF" and data[5] == 1, "Expected little-endian ELF"
    if data[4] == 2:
        offset = struct.unpack_from("<Q", data, 32)[0]
        stride, count = struct.unpack_from("<HH", data, 54)
        alignment_offset, alignment_format = 48, "<Q"
    else:
        offset = struct.unpack_from("<I", data, 28)[0]
        stride, count = struct.unpack_from("<HH", data, 42)
        alignment_offset, alignment_format = 28, "<I"
    alignments = [struct.unpack_from(alignment_format, data, offset + i * stride + alignment_offset)[0]
                  for i in range(count) if struct.unpack_from("<I", data, offset + i * stride)[0] == 1]
    assert alignments and min(alignments) >= 16384, "Native LOAD segments do not support 16KB pages"
    return min(alignments)

def defined_classes(dex):
    """Resolve class_def type indices rather than searching incidental strings."""
    def u32(offset):
        return struct.unpack_from("<I", dex, offset)[0]
    strings_size, strings_offset = u32(56), u32(60)
    types_size, types_offset = u32(64), u32(68)
    classes_size, classes_offset = u32(96), u32(100)
    assert dex.startswith(b"dex\n"), "Unsupported DEX format"
    strings = []
    for index in range(strings_size):
        offset = u32(strings_offset + index * 4)
        while dex[offset] & 128:
            offset += 1
        offset += 1
        end = dex.index(b"\x00", offset)
        strings.append(dex[offset:end].decode("utf-8", errors="replace"))
    types = [strings[u32(types_offset + index * 4)] for index in range(types_size)]
    return [types[u32(classes_offset + index * 32)] for index in range(classes_size)]

apks = [("host", "app.onklav.home.prototype.rewrite", ROOT / f"android/app/build/outputs/apk/{args.variant}/app-{args.variant}.apk")]
excluded = {"build", "target", ".gradle", ".godot"}
source = [p for p in ROOT.rglob("*") if p.is_file() and not excluded.intersection(p.relative_to(ROOT).parts)]
assert not [p for p in source if p.suffix == ".java"], "Authored Java source found"
assert "innercircle" not in (ROOT / "settings.gradle.kts").read_text().lower(), "Legacy module included"
application_modules = [p.relative_to(ROOT).as_posix() for p in source
                       if p.name == "build.gradle.kts" and p.parent != ROOT and 'id("com.android.application")' in p.read_text()]
assert application_modules == ["android/app/build.gradle.kts"], application_modules
build_config=(ROOT / "android/app/build.gradle.kts").read_text()
expected_version=re.search(r'versionName="([^"]+)"',build_config).group(1)
expected_target=int(re.search(r'targetSdk=(\d+)',build_config).group(1))
expected_code=int(re.search(r'versionCode=(\d+)',build_config).group(1))
results = []
for label, expected_id, path in apks:
    signature = command(BUILD_TOOLS / "apksigner", "verify", "--verbose", "--print-certs", path)
    assert CERTIFICATE in signature and "Verified using v2 scheme (APK Signature Scheme v2): true" in signature
    command(BUILD_TOOLS / "zipalign", "-c", "-P", "16", "4", path)
    badging = command(BUILD_TOOLS / "aapt", "dump", "badging", path)
    package_id = re.search(r"package: name='([^']+)'", badging).group(1)
    assert package_id == expected_id
    version_name=re.search(r"versionName='([^']+)'",badging).group(1)
    version_code=int(re.search(r"versionCode='(\d+)'",badging).group(1))
    assert (version_name,version_code)==(expected_version,expected_code), "APK version does not match current source"
    target_sdk=int(re.search(r"targetSdkVersion:\s*'([0-9]+)'",badging).group(1))
    assert target_sdk==expected_target, "APK target SDK does not match current source"
    native_abis = re.search(r"native-code: (.+)", badging).group(1)
    assert set(re.findall(r"'([^']+)'", native_abis)) == set(expected_abis), "Unexpected/incomplete native ABI support"
    permissions = re.findall(r"uses-permission(?:-sdk-\d+)?: name='([^']+)'", badging)
    manifest = command(BUILD_TOOLS / "aapt", "dump", "xmltree", path, "AndroidManifest.xml")
    assert "android.intent.action.TTS_SERVICE" not in manifest, "Retired system TTS query returned"
    assert not re.search(r"android:allowBackup[^\n]*0xffffffff",manifest), "Private-data backup policy changed"
    # Public HTTP radio is an explicit host transport exception (RADIO-RELIABILITY.md).
    # Guest HTTP/account brokers still require HTTPS; private backups remain disabled.
    assert re.search(r"android:usesCleartextTraffic[^\n]*0xffffffff",manifest), "Public HTTP radio transport missing"
    assert "dataExtractionRules" in manifest
    resources=command(BUILD_TOOLS / "aapt", "dump", "--values", "resources", path)
    backup_resource=re.search(r'resource (0x[0-9a-f]+) [^\n]*:xml/data_extraction_rules:[^\n]*\n\s+\(string(?:8|16)\) "([^"]+)"',resources)
    assert backup_resource, "Private backup resource missing"
    backup_id,backup_path=backup_resource.groups()
    assert re.search(r'android:dataExtractionRules[^\n]*=@'+backup_id+r'\b',manifest), "Manifest backup policy does not match its resource"
    backup_rules=command(BUILD_TOOLS / "aapt", "dump", "xmltree", path, backup_path)
    assert "cloud-backup" in backup_rules and "device-transfer" in backup_rules and backup_rules.count("E: exclude")==18, "Private backup exclusions missing"
    assert "sharedUserId" not in manifest
    # Owner-requested host self-updates use Android's user-confirmed installer.
    # This does not add a guest APK installation capability or privileged installer.
    assert "android.permission.REQUEST_INSTALL_PACKAGES" in permissions
    assert not set(permissions).intersection({"android.permission.INSTALL_PACKAGES", "android.permission.DELETE_PACKAGES"})
    update_providers=[block for block in manifest.split("E: provider")[1:]
                      if 'Raw: "'+package_id+'.updates"' in block]
    assert len(update_providers)==1, "Private self-update provider missing or duplicated"
    update_provider=update_providers[0]
    assert "app.onklav.home.OnKlavUpdateProvider" in update_provider
    assert re.search(r"android:exported[^\n]*0x0\b",update_provider), "Update provider must not be exported"
    assert re.search(r"android:grantUriPermissions[^\n]*0xffffffff",update_provider)
    update_resource=re.search(r'resource (0x[0-9a-f]+) [^\n]*:xml/update_paths:[^\n]*\n\s+\(string(?:8|16)\) "([^"]+)"',resources)
    assert update_resource, "Update provider path resource missing"
    update_id,update_path=update_resource.groups()
    assert re.search(r'android:resource[^\n]*=@'+update_id+r'\b',update_provider)
    update_rules=command(BUILD_TOOLS / "aapt", "dump", "xmltree", path, update_path)
    assert update_rules.count("E: cache-path")==1 and "onklav-updates/" in update_rules
    assert not any("E: "+kind in update_rules for kind in ("root-path","files-path","external-path","external-files-path","external-cache-path"))
    assert badging.count("launchable-activity:") == 1
    assert "app.onklav.home.WebGameActivity" in manifest
    assert "app.onklav.games.GameActivity" not in manifest
    assert "app.onklav.tools.MainActivity" not in manifest
    if args.variant == "optimized":
        assert "profileable" in manifest, "Performance comparison build must permit shell profiling"
        assert not re.search(r"android:debuggable[^\n]*0xffffffff", manifest), "Optimized APK is debuggable"
    with zipfile.ZipFile(path) as archive:
        assert archive.testzip() is None
        classes = []
        for name in archive.namelist():
            if re.fullmatch(r"classes\d*\.dex", name):
                classes.extend(defined_classes(archive.read(name)))
        if args.variant == "debug":
            assert "Lapp/onklav/tools/NotesModel;" in classes
        assert "Lapp/onklav/home/MainActivity;" in classes
        if args.variant == "debug":
            for photo_class in ("DesktopArrangement", "PhoneLocalSettings", "PhoneSettingsUiKt", "PhoneDetailsUiKt", "DialPadStyleKt", "ChatFeedActionKt", "CloudModel", "CloudDav", "CloudStore", "CloudScreenKt", "DesktopFolders", "DesktopFolder", "AppsDesktopKt", "PhoneModel", "PhoneDialActivity", "PhoneCallActivity", "PhoneInCallService", "PhoneCallActionReceiver", "CalendarModel", "CalendarReceiver", "CalendarEntryActivity", "PhotoMark", "PhotoMarkup", "PhotoMarkupState", "PhotoMarkupCanvasKt", "PhotoMarkupControlsKt", "PhotoLayers", "AudioImportWave", "AndroidFolderShortcutsKt", "BulkConversationActionsKt", "MessageFeedAutoScrollKt", "AudioPreviewPlan", "AudioPreviewPlan$Companion", "PhotoHdr", "FolderVaultEngine", "WritePendingSaves", "WriteEnglish", "WriteSaveDialogKt", "WriteToolsKt", "VideoAudioProbeKt", "YoutubeAudioControlsKt", "YoutubeAudioOutputKt", "NeuralSpeechNative", "SpeechPlaybackService"):
                assert "Lapp/onklav/home/"+photo_class+";" in classes, "Integrated image tools missing: "+photo_class
            for radio_class in ("app/onklav/home/ExoRadioStream", "app/onklav/home/RadioHttpSource", "androidx/media3/exoplayer/hls/HlsMediaSource"):
                assert "L"+radio_class+";" in classes, "Public radio transport missing: "+radio_class
        for preserved in ("org/schabi/newpipe/extractor/NewPipe", "org/schabi/newpipe/extractor/utils/JavaScript", "org/mozilla/javascript/Context", "app/onklav/home/NeuralSpeechNative"):
            assert "L"+preserved+";" in classes, "Required dynamic/native path was removed: "+preserved
        assert not any(n.startswith("Lorg/mozilla/javascript/engine/") for n in classes), "Unused JSR-223 adapter returned"
        assert "Lapp/onklav/home/WebGameActivity;" in classes
        assert not any(n.startswith("Lorg/godotengine/") for n in classes), "Retired native game engine returned"
        assert not any(name.startswith(("Lapp/onklav/tools/BrowserScreen", "Lapp/onklav/tools/AnnotationScreen", "Lapp/onklav/tools/ReaderScreen", "Lapp/onklav/tools/RssScreen", "Lapp/onklav/tools/ToolScreen")) for name in classes), "Obsolete app route remains"
        legacy = [name for name in classes if name.startswith("Lcom/charlessoude/basicmessages/")]
        assert not legacy, legacy[:10]
        assert not [name for name in archive.namelist() if name.lower().endswith(".apk")], "Nested APK found"
        assert all(re.fullmatch(r"lib/(arm64-v8a|x86_64)/lib[^/]+\.so", name)
                   for name in archive.namelist() if name.startswith("lib/") and not name.endswith("/")), "Unexpected native packaging entry"
        native_alignment = {name: elf_load_alignment(archive.read(name)) for name in archive.namelist()
                            if name.startswith("lib/") and name.endswith(".so")}
        native_hashes = {name: sha(archive.read(name)) for name in native_alignment}
        native = {}
        if label == "host":
            assert "Lapp/onklav/shared/NativeCore;" in classes, "JNI bridge was removed or renamed"
            for abi in expected_abis:
                name = f"lib/{abi}/libonklav_core.so"
                data = archive.read(name)
                assert data == (ROOT / f"android/shared/src/main/jniLibs/{abi}/libonklav_core.so").read_bytes()
                native[abi] = sha(data)
            from build_speech import current as speech_current
            assert speech_current(expected_abis), "Speech model/native proof is stale"
            for abi in expected_abis:
                for library in ("libonklav_speech.so", "libsherpa-onnx-c-api.so", "libonnxruntime.so"):
                    assert archive.read(f"lib/{abi}/{library}") == (ROOT/f"android/shared/src/main/jniLibs/{abi}/{library}").read_bytes(), "Speech native library is stale"
            names=archive.namelist()
            assert len(names)==len(set(names)), "Duplicate APK archive entries"
            assert names.count("assets/speech/english.voicepack")==1, "Speech model must be shared once"
            assert not any(name.startswith("assets/speech/.") for name in names), "Temporary speech asset was packaged"
            assert not any("kokoro" in name.lower() for name in names), "Retired Kokoro assets returned"
            assert not any(name.endswith((".onnx","/voices.bin")) for name in names), "Unshared loose speech model/voice bank"
            speech_proof=json.loads((ROOT/"docs/speech-native-provenance.json").read_text())
            assert (speech_proof["default_speaker"],speech_proof["other_speaker"])==(2,0), "Unexpected selected Nano voices"
            for asset in ("english.json", "english.voicepack"):
                assert archive.read("assets/speech/"+asset) == (ROOT/"android/shared/src/main/assets/speech"/asset).read_bytes(), "Speech voice pack is stale"
            with zipfile.ZipFile(io.BytesIO(archive.read("assets/speech/english.voicepack"))) as bank:
                assert bank.getinfo("voices.bin").file_size==819200, "Only the selected two voice rows may ship"
                assert not any(n.endswith("_dict") and n!="espeak-ng-data/en_dict" for n in bank.namelist()), "Unused language dictionary returned"
                assert not any("/voices/" in n for n in bank.namelist()), "Unused phonemizer voice variants returned"
            for notice in ("Kitten-Nano-Apache-2.0.txt", "Speech-GPL-3.0.txt", "Speech-NOTICE.md", "Speech-ONNXRuntime-MIT.txt", "Speech-Piper-MIT.txt", "Speech-Sherpa-Apache-2.0.txt"):
                assert archive.read("assets/licenses/"+notice) == (ROOT/"android/shared/src/main/assets/licenses"/notice).read_bytes(), "Speech notices are missing/stale"
            onklav_notices = ROOT / "android/app/src/main/assets/licenses"
            for name in ("OnKlav-NOTICE.md", "OnKlav-GPL-3.0.txt"):
                assert archive.read(f"assets/licenses/{name}") == (onklav_notices / name).read_bytes()
            newpipe_notices = ROOT / "android/app/src/main/assets/licenses/newpipe"
            for name in ("NOTICE.md", "COPYING.txt", "nanojson-LICENSE.txt", "nanojson-NOTICE.txt",
                         "jsoup-LICENSE.txt", "protobuf-LICENSE.txt", "rhino-LICENSE.txt"):
                assert archive.read(f"assets/licenses/newpipe/{name}") == (newpipe_notices / name).read_bytes()
            for name in ("Godot-MIT.txt", "Godot-Third-Party.txt"):
                assert archive.read(f"assets/licenses/{name}") == (ROOT / "developer/games/licenses" / name).read_bytes()
            sources=json.loads((ROOT/"third_party/speech/sources.json").read_text())
            for source_archive,record in sources["archives"].items():
                assert sha((ROOT/source_archive).read_bytes())==record["sha256"], "Retain the speech dependency source kit with distributed builds"
            assert "assets/sdk/Annotation.onklav-a" not in archive.namelist(), "Retired Annotation app is still bundled"
            assert "assets/sdk/Notes.onklav-a" not in archive.namelist(), "Retired Notes app is still bundled"
            # AAPT expands .gz source assets and strips that suffix. Verify the shipped lookup.
            import gzip
            assert archive.read("assets/write/english.txt") == gzip.decompress((ROOT/"android/app/src/main/assets/write/english.txt.gz").read_bytes()), "Write dictionary differs from current source"
            for dictionary_file in ("english.json", "english-COPYRIGHT.txt"):
                assert archive.read("assets/write/"+dictionary_file) == (ROOT/"android/app/src/main/assets/write"/dictionary_file).read_bytes(), "Write dictionary metadata/license is stale"
            proof = json.loads((ROOT / "docs/app-sdk-provenance.json").read_text())
            for name in ("Clock", "Flashlight", "Camera", "Email", "VideoEditor", "Maps", "Audio", "Write", "Photo", "Calc", "Convert", "Weather", "Files", "RSS", "ReadAloud", "Browser", "YouTube", "Social", "WalkieTalkie", "Radio", "Podcasts", "AudioBooks", "eBooks"):
                package = archive.read(f"assets/sdk/{name}.onklav-a")
                assert sha(package) == proof["packages"][name]["sha256"]
                assert package == (ROOT / f"sdk/packages/{name}.onklav-a").read_bytes()
                with zipfile.ZipFile(io.BytesIO(package)) as app:
                    assert set(app.namelist()) == {"manifest.json", "main.wasm", "signature.ed25519"}
                    assert app.testzip() is None
                    assert app.read("main.wasm").startswith(b"\x00asm")
                    assert sha(app.read("main.wasm")) == proof["packages"][name]["wasm_sha256"]
                    assert len(app.read("signature.ed25519")) == 64
            games=json.loads((ROOT/"docs/games-provenance.json").read_text())
            for name,expected in games["assets_sha256"].items():
                asset="assets/"+name.split("/assets/",1)[1]
                assert sha(archive.read(asset))==expected, "Shared game runtime/content differs: "+asset
            shroom_path = "included-games/shroom.zip"
            last_light_path = "included-games/last-light.zip"
            assert {name for name in names if name.startswith("assets/included-games/")} == {
                "assets/" + shroom_path, "assets/" + last_light_path,
            }
            assert not any("/onklav-music/" in "/" + name or name.startswith("assets/licenses/MUSICCAPS-")
                           for name in names), "Retired Music Maker assets are still bundled"
            shroom = archive.read("assets/" + shroom_path)
            assert shroom == (ROOT / "android/games/src/main/assets" / shroom_path).read_bytes()
            with zipfile.ZipFile(io.BytesIO(shroom)) as package:
                manifest = json.loads(package.read("manifest.json"))
                source_manifest = json.loads((ROOT / "games/shroom/manifest.json").read_text())
                assert all(manifest[key] == source_manifest[key] for key in ("id", "version", "version_code"))
                from importlib.util import spec_from_file_location, module_from_spec
                spec = spec_from_file_location("gamekit", ROOT / "developer/games/gamekit.py")
                gamekit = module_from_spec(spec)
                spec.loader.exec_module(gamekit)
                resources = gamekit.packed_resource_names(package.read("web/index.pck"))
                assert {name for name in resources if "below_the_mycelium.ogg" in name} == {"assets/below_the_mycelium.ogg"}
                assert "web/shroom-score.ogg" not in package.namelist()
                assert not set(package.namelist()).intersection({"publisher.json", "signature.bin"})
                assert not set(package.namelist()).intersection("web/" + name for name in source_manifest["engine_assets"])
            last_light = archive.read("assets/" + last_light_path)
            assert last_light == (ROOT / "android/games/src/main/assets" / last_light_path).read_bytes()
            with zipfile.ZipFile(io.BytesIO(last_light)) as package:
                manifest = json.loads(package.read("manifest.json"))
                source_manifest = json.loads((ROOT / "games/last-light/manifest.json").read_text())
                assert manifest == source_manifest
                assert manifest["min_host"] <= version_code
                assert package.testzip() is None
                assert not set(package.namelist()).intersection("web/" + name for name in manifest["engine_assets"])
                resources = gamekit.packed_resource_names(package.read("web/index.pck"))
                assert {"audio/rail.ogg.import", "audio/pressure.ogg.import", "audio/tether.wav.import"} <= resources
            assert archive.read("assets/catalog/last-light.webp") == (ROOT / "android/app/src/main/assets/catalog/last-light.webp").read_bytes()
            assert archive.read("assets/catalog/shroom.webp") == (ROOT / "android/app/src/main/assets/catalog/shroom.webp").read_bytes()
            assert "assets/onklav-games.pck" not in archive.namelist()
            assert not any(n.endswith("/libgodot_android.so") for n in archive.namelist()), "Retired native game engine returned"
    results.append({"name": label, "variant":args.variant, "package": package_id, "version_name":version_name, "version_code":version_code, "target_sdk":target_sdk, "bytes": path.stat().st_size, "sha256": sha(path.read_bytes()), "signer_sha256": CERTIFICATE, "signature_v2_verified": True, "public_http_radio_transport": True, "zip_integrity": "pass", "zipalign_16kb": "pass", "abis": expected_abis, "elf_minimum_load_alignment": native_alignment, "native_library_sha256": native_hashes, "legacy_defined_classes": 0, "defined_class_count": len(classes), "permissions": permissions, "rust_libraries": native})
assert len({result["package"] for result in results}) == len(results)
report = {"scope": "Development builds; not full Inner Circle 2.2.85 parity", "source_counts": {extension: sum(p.suffix == extension for p in source) for extension in [".java", ".kt", ".rs", ".gd"]}, "application_modules": application_modules, "apks": results}
(ROOT / "docs/apk-verification.json").write_text(json.dumps(report, indent=2) + "\n")
(ROOT / f"docs/apk-verification-{args.variant}.json").write_text(json.dumps(report, indent=2) + "\n")
print(json.dumps({"apks_verified": len(results), "source_counts": report["source_counts"], "signer_sha256": CERTIFICATE}))
