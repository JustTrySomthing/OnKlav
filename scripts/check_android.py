#!/usr/bin/env python3
"""Check Kotlin changes using Android framework host tests; no emulator boot loop."""
import hashlib
import argparse
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import time
import re
import shutil
import zipfile
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument("--tests-only",action="store_true")
parser.add_argument("--working",action="store_true",help="Verify source-matched unreleased JNI; tests only, never APK assembly")
parser.add_argument("--test",action="append",default=[],help="App test class or method; repeat to select a bounded set")
parser.add_argument("--online",action="store_true",help="Fetch missing restored-cache dependencies")
parser.add_argument("--timeout",type=int,default=420,choices=range(30,601),metavar="30..600")
args=parser.parse_args()
TESTS_ONLY = args.tests_only
if args.working and not TESTS_ONLY:
    sys.exit("--working requires --tests-only. Release assembly retains its separate provenance checks.")
env = os.environ.copy()
sdk = Path(env.get("ANDROID_HOME", ""))
jdk = Path(env.get("JAVA_HOME", ""))
if not (sdk / "platforms/android-36/android.jar").is_file() or not (jdk / "bin/javac").is_file():
    sys.exit("Set ANDROID_HOME (platform 36/build tools 35) and JAVA_HOME (full JDK 17).")
if not TESTS_ONLY and not (Path.home() / ".android/debug.keystore").is_file():
    sys.exit("Restore the existing OnKlav signing backup first.")

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

# This quick check is only valid while mobile Rust sources/binaries remain unchanged.
provenance = json.loads((ROOT / ("docs/host-native-provenance.json" if TESTS_ONLY else "docs/native-provenance.json")).read_text())
if args.working:
    from working_native import verified_library
    try:
        working_library=verified_library(ROOT)
    except (ValueError, OSError, KeyError) as error:
        sys.exit(str(error))
    if sha(ROOT / "core/target/debug/libonklav_core.so") != sha(working_library):
        sys.exit("Test JNI differs from the verified working library. Restore that library before testing.")
    print("Verified unreleased Linux/ARM64 source checkpoint. Included signed app packages are retained; no SDK or APK release is implied.",flush=True)
for name, expected in ([] if args.working else provenance["files_sha256"].items()):
    if sha(ROOT / name) != expected:
        sys.exit("Rust changed; use scripts/build_native.py for a full Android native rebuild.")
if not args.working:
    assert set(provenance["files_sha256"]) == {"core/Cargo.toml", "core/Cargo.lock", *(p.relative_to(ROOT).as_posix() for p in (ROOT / "core/src").rglob("*.rs"))}, "New Rust source needs a native rebuild"
if not args.working and sha(ROOT / "core/target/debug/libonklav_core.so") != provenance["host_library_sha256"]:
    sys.exit("Host JNI library changed; rebuild with scripts/build_native.py.")
for abi, expected in provenance.get("mobile_libraries_sha256", {}).items():
    if sha(ROOT / f"android/shared/src/main/jniLibs/{abi}/libonklav_core.so") != expected:
        sys.exit("Mobile native library changed; perform and verify a full native rebuild.")

# Use the same complete source/package/bundle check as APK assembly. Checking
# only recorded paths misses added sources and incomplete per-package proofs.
from build_speech import current as speech_current
if not speech_current(host=True):sys.exit("Build source-matched speech JNI with scripts/build_speech.py --host before host checks.")
from build_inputs import sdk_current
if not args.working and not sdk_current(ROOT):
    sys.exit("SDK sources/packages/bundles are stale or incomplete. Run scripts/build_sdk_apps.py --plan first.")

proxy = urlparse(env.get("HTTPS_PROXY", env.get("https_proxy", "")))
options = []
if proxy.hostname:
    for scheme in ("http", "https"):
        options += [f"-D{scheme}.proxyHost={proxy.hostname}", f"-D{scheme}.proxyPort={proxy.port or 80}"]
certs = Path("/etc/ssl/certs/java/cacerts")
if certs.is_file():
    options += [f"-Djavax.net.ssl.trustStore={certs}"]
env["GRADLE_OPTS"] = env.get("GRADLE_OPTS", "") + " " + " ".join(options)
env["JAVA_TOOL_OPTIONS"] = env.get("JAVA_TOOL_OPTIONS", "") + " " + " ".join(options)
version = re.search(r'versionName="([^"]+)"', (ROOT/"android/app/build.gradle.kts").read_text()).group(1).split("-")[0]
evidence = ROOT / f"docs/evidence/v{version}"
evidence.mkdir(parents=True, exist_ok=True)

def run(label, args, limit):
    print(f"{label}: starting (maximum {limit}s)", flush=True)
    started = time.monotonic()
    log_path = evidence / f"{label}.log"
    for attempt in range(2):
        with log_path.open("wb") as log:
            process = subprocess.Popen(args, cwd=ROOT, env=env, stdout=log,
                                       stderr=subprocess.STDOUT, start_new_session=True)
            try:
                deadline = started + limit
                while process.poll() is None:
                    remaining = deadline - time.monotonic()
                    if remaining <= 0:
                        raise subprocess.TimeoutExpired(args, limit)
                    try:
                        process.wait(timeout=min(30, remaining))
                    except subprocess.TimeoutExpired:
                        print(f"{label}: running, {time.monotonic()-started:.0f}s elapsed", flush=True)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGKILL)
                process.wait()
                sys.exit(f"{label} exceeded its limit; stopped the process group. Inspect {log.name}.")
        if not process.returncode:
            break
        if attempt == 0 and "gradlew" in args and time.monotonic()-started < limit-15:
            from gradle_cache_repair import quarantine_unreadable_transforms
            try:
                repaired = quarantine_unreadable_transforms(
                    log_path.read_text(errors="replace"),
                    Path(env.get("GRADLE_USER_HOME", str(Path.home()/".gradle"))),
                )
            except OSError:
                repaired = []
            if repaired:
                shutil.copy2(log_path, evidence/f"{label}.metadata-failure.log")
                print(f"{label}: quarantined unreadable generated cache; retrying once within the same time limit", flush=True)
                continue
        sys.exit(f"{label} failed ({process.returncode}); inspect {log_path}.")
    print(f"{label}: passed in {time.monotonic()-started:.1f}s", flush=True)

gradle = ["bash", "gradlew", *([] if args.online else ["--offline"]), "--no-daemon", "--max-workers=2", "--console=plain",
    "-Pandroid.aapt2FromMavenOverride="+str(sdk/"build-tools/35.0.0/aapt2"),
    "-Dorg.gradle.internal.http.connectionTimeout=15000", "-Dorg.gradle.internal.http.socketTimeout=15000"]
# The selected test task compiles its dependencies. One Gradle invocation avoids
# two redundant daemon/configuration startups for every focused edit.
# AGP's incremental runtime bundler retained a replaced Kotlin method in a
# previous run. Recreate this generated bundle after compilation, before tests.
runtime_bundle = ROOT / "android/app/build/intermediates/runtime_app_classes_jar/debug/bundleDebugClassesToRuntimeJar"
if runtime_bundle.exists():
    shutil.rmtree(runtime_bundle)
selection=[value for selector in args.test for value in ["--tests",selector]]
run("android-host-check", gradle + [":android:app:testDebugUnitTest", *selection, ":android:shared:testDebugUnitTest"], args.timeout)
with zipfile.ZipFile(runtime_bundle / "classes.jar") as runtime:
    compiled = ROOT / "android/app/build/tmp/kotlin-classes/debug"
    for cls in compiled.rglob("*.class"):
        assert runtime.read(cls.relative_to(compiled).as_posix()) == cls.read_bytes(), f"Stale runtime class: {cls.name}"
print("Android runtime bundle matches compiled Kotlin classes.", flush=True)
if TESTS_ONLY:
    print("Source/host tests complete. APK assembly is a separate build phase.")
    sys.exit(0)
# New resource jars can leave obsolete project DEX entries in AGP's incremental
# outputs. Recreate this generated archive before producing a requested APK.
for name in ["project_dex_archive/debug","incremental/dexBuilderDebug","dex/debug/mergeProjectDexDebug"]:
    generated=ROOT/"android/app/build/intermediates"/name
    if generated.exists():shutil.rmtree(generated)
# Restored incremental asset/package state can retain an older signed SDK app
# or APK manifest even when the task executes. Recreate these derived outputs.
for name in ["assets/debug/mergeDebugAssets", "compressed_assets/debug/compressDebugAssets",
             "incremental/mergeDebugAssets", "incremental/packageDebug"]:
    generated=ROOT/"android/app/build/intermediates"/name
    if generated.exists():shutil.rmtree(generated)
generated=ROOT/"android/app/build/outputs/apk/debug"
if generated.exists():shutil.rmtree(generated)
# Library directory bundling can retain old synthetic lambda classes after Kotlin edits.
for module in ("shared","tools"):
    generated=ROOT/f"android/{module}/build/intermediates/runtime_library_classes_dir/debug/bundleLibRuntimeToDirDebug"
    if generated.exists():shutil.rmtree(generated)
# Native copy/merge outputs can survive a replaced source JNI library in restored caches.
for module,name in [("shared","library_jni/debug/copyDebugJniLibsProjectOnly"),("shared","merged_native_libs/debug/mergeDebugNativeLibs"),("app","merged_native_libs/debug/mergeDebugNativeLibs"),("app","stripped_native_libs/debug/stripDebugDebugSymbols")]:
    generated=ROOT/f"android/{module}/build/intermediates"/name
    if generated.exists():shutil.rmtree(generated)
run("android-apk-build", gradle + ["--no-build-cache",":android:app:assembleDebug"], 180)
run("apk-verification", [sys.executable, "scripts/verify_apks.py"], 60)
print("Android framework host tests and APK checks complete. Physical-device/carrier acceptance is separate.")
