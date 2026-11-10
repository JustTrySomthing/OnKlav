#!/usr/bin/env python3
"""Build/test Rust JNI for host and requested Android ABIs, recording exact provenance."""
import hashlib, json, os, shutil, subprocess, time, re, sys, argparse
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
TARGET_DIR = ROOT / os.environ.get('CARGO_TARGET_DIR', 'core/target')
parser=argparse.ArgumentParser(description=__doc__)
mode=parser.add_mutually_exclusive_group()
mode.add_argument('--host-only',action='store_true')
mode.add_argument('--android-only',action='store_true')
parser.add_argument('--abis',default='arm64-v8a')
parser.add_argument('--working',action='store_true',help='Build isolated WIP JNI/proof without replacing released mobile artifacts')
parser.add_argument('--test-filter',help='Focused Rust filter for --working (default sdk)')
args=parser.parse_args()
if args.test_filter and not args.working:parser.error('--test-filter is only allowed for an isolated working build')
if args.working and (args.host_only or args.android_only or args.abis!='arm64-v8a'):parser.error('--working builds both host and qualified ARM64 outputs')
HOST_ONLY=args.host_only
ANDROID_ONLY=args.android_only
targets={'arm64-v8a':'aarch64-linux-android','x86_64':'x86_64-linux-android'}
abis=args.abis.split(',')
assert abis and len(set(abis))==len(abis) and all(a in targets for a in abis),'Unsupported or duplicate ABI'
sdk = Path(os.environ['ANDROID_HOME'])
ndk = sdk / 'ndk/27.2.12479018/toolchains/llvm/prebuilt/linux-x86_64/bin'
assert HOST_ONLY or ndk.is_dir(), 'Restore NDK 27.2.12479018 before rebuilding Android Rust'
version = re.search(r'versionName="([^"]+)"', (ROOT/'android/app/build.gradle.kts').read_text()).group(1).split('-')[0]
evidence = ROOT / f'docs/evidence/v{version}'
evidence.mkdir(parents=True, exist_ok=True)
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
inputs = [ROOT/'core/Cargo.toml', ROOT/'core/Cargo.lock', *sorted((ROOT/'core/src').rglob('*.rs'))]
source = {p.relative_to(ROOT).as_posix(): sha(p) for p in inputs}
commands = [(['cargo','test','--locked','--manifest-path','core/Cargo.toml'], 'rust-tests', os.environ.copy()),
            (['cargo','build','--lib','--locked','--manifest-path','core/Cargo.toml'], 'host-native', os.environ.copy())]
if ANDROID_ONLY:
    proof=json.loads((ROOT/'docs/host-native-provenance.json').read_text())
    assert proof['files_sha256']==source and proof['host_library_sha256']==sha(TARGET_DIR/'debug/libonklav_core.so'), 'Build/test current host JNI first'
    commands=[]
env = os.environ.copy()
env['CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER'] = str(ndk/'aarch64-linux-android29-clang')
env['CARGO_TARGET_X86_64_LINUX_ANDROID_LINKER'] = str(ndk/'x86_64-linux-android29-clang')
env['RUSTFLAGS'] = '-C link-arg=-Wl,-z,max-page-size=16384'
if not HOST_ONLY: commands.append((['cargo','build','--lib','--locked','--release','--manifest-path','core/Cargo.toml',*[v for abi in abis for v in ['--target',targets[abi]]]], 'android-native', env))
if args.working:
    commands[0][0].append(args.test_filter or 'sdk')
    for command,_,_ in commands:command.insert(2,'--offline')
for command, name, process_env in commands:
    start=time.monotonic(); print(name+': starting',flush=True)
    with (evidence/((('working-' if args.working else '')+name)+'.log')).open('wb') as f:
        subprocess.run(command,cwd=ROOT,env=process_env,stdout=f,stderr=subprocess.STDOUT,check=True,timeout=600 if name=="android-native" else 240)
    print(name+': passed in '+str(round(time.monotonic()-start,1))+'s',flush=True)
assert all(sha(ROOT/n)==h for n,h in source.items()), 'Rust changed during build'
if args.working:
    host=TARGET_DIR/'debug/libonklav_core.so'
    subprocess.run(['strip','--strip-debug',str(host)],check=True,timeout=30)
    fallback=ROOT/'core/host-libs/linux-x86_64/libonklav_core.so'
    fallback.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(host,fallback)
    mobile=TARGET_DIR/'aarch64-linux-android/release/libonklav_core.so'
    overlay=ROOT/'android/shared/src/diagnostic/jniLibs/arm64-v8a/libonklav_core.so'
    overlay.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(mobile,overlay)
    output=subprocess.run(['readelf','--wide','-l',str(mobile)],capture_output=True,text=True,check=True).stdout
    aligns=[int(line.split()[-1],16) for line in output.splitlines() if line.strip().startswith('LOAD ')]
    assert aligns and min(aligns)>=16384,'Android native load segments must retain16KiB alignment'
    log=evidence/'working-rust-tests.log'
    match=re.search(r'test result: ok\. (\d+) passed',log.read_text())
    report={'status':'unreleased source-matched WIP JNI; released mobile artifact unchanged; no APK produced',
        'core_source_sha256':source,'linux_jni_sha256':sha(host),'arm64_jni_sha256':sha(mobile),
        'rust_test_filter':args.test_filter or 'sdk','sdk_core_tests':int(match.group(1)) if match else None,
        'arm64_load_alignment':min(aligns),'android_host_native_bridge':'pending current-source host checks',
        'physical_device_or_carrier_tested':False,'logs':[str((evidence/('working-'+name+'.log')).relative_to(ROOT)) for _,name,_ in commands]}
    if TARGET_DIR.resolve() != (ROOT/'core/target').resolve():
        local_host=ROOT/'core/target/debug/libonklav_core.so'
        local_host.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(host,local_host)
    (ROOT/'docs/working-native-verification.json').write_text(json.dumps(report,indent=2)+'\n')
    print('Working JNI saved; released mobile JNI/provenance unchanged',flush=True)
    sys.exit(0)
binaries={}
previous_path=ROOT/'docs/native-provenance.json'
if previous_path.is_file() and not HOST_ONLY:
    previous=json.loads(previous_path.read_text())
    if previous.get('files_sha256')==source and previous.get('rustflags')==env['RUSTFLAGS']:
        for abi,digest in previous.get('mobile_libraries_sha256',{}).items():
            retained=ROOT/f'android/shared/src/main/jniLibs/{abi}/libonklav_core.so'
            if abi in targets and retained.is_file() and sha(retained)==digest:binaries[abi]=digest
for abi,target in ([] if HOST_ONLY else [(abi,targets[abi]) for abi in abis]):
    destination=ROOT/f'android/shared/src/main/jniLibs/{abi}/libonklav_core.so'
    destination.parent.mkdir(parents=True,exist_ok=True)
    shutil.copyfile(TARGET_DIR/f'{target}/release/libonklav_core.so',destination)
    binaries[abi]=sha(destination)
# Keep a small source-matched Linux JNI fallback for the existing reusable tool cache.
host = TARGET_DIR/'debug/libonklav_core.so'
if not ANDROID_ONLY: subprocess.run(['strip','--strip-debug',str(host)],check=True,timeout=30)
fallback = ROOT/'core/host-libs/linux-x86_64/libonklav_core.so'
fallback.parent.mkdir(parents=True,exist_ok=True)
if not ANDROID_ONLY: shutil.copyfile(host,fallback)
else: assert sha(host)==sha(fallback)
if TARGET_DIR.resolve() != (ROOT/'core/target').resolve():
    local_host=ROOT/'core/target/debug/libonklav_core.so'
    local_host.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(host,local_host)
report={'files_sha256':source,'mobile_libraries_sha256':binaries,'host_library_sha256':sha(TARGET_DIR/'debug/libonklav_core.so'),'ndk':'27.2.12479018','rustflags':env['RUSTFLAGS'],'rust_tests':proof['rust_tests'] if ANDROID_ONLY else f'passed; see {evidence.relative_to(ROOT)}/rust-tests.log'}
(ROOT/('docs/host-native-provenance.json' if HOST_ONLY else 'docs/native-provenance.json')).write_text(json.dumps(report,indent=2)+'\n')
if not HOST_ONLY:
    (ROOT/'docs/host-native-provenance.json').write_text(json.dumps(report,indent=2)+'\n')
print('Native provenance saved',flush=True)
