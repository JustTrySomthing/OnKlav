#!/usr/bin/env python3
"""Build/verify the shared neural speech JNI separately from the SDK core.
Matching Sherpa1.13.8 runtime binaries are vendored with their hashes/notices.
No model substitution or silent phonemizer upgrade is allowed.
"""
import argparse,hashlib,json,os,shutil,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def sha(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def inputs():return sorted(['docs/speech-dependencies.json','core/speech/Cargo.toml','core/speech/Cargo.lock','scripts/build_speech.py','scripts/build_compact_speech.py']+[str(p.relative_to(ROOT))for p in (ROOT/'core/speech/src').rglob('*.rs')])
def current(abis=('arm64-v8a',),host=False):
 try:
  proof=json.loads((ROOT/'docs/speech-native-provenance.json').read_text())
  if proof['inputs']!={p:sha(ROOT/p)for p in inputs()}:return False
  deps=json.loads((ROOT/'docs/speech-dependencies.json').read_text())
  for name,digest in (deps['runtime_files']|deps['voice_assets']).items():
   if not host and name.startswith('core/'):continue
   if not (ROOT/name).is_file() or sha(ROOT/name)!=digest:return False
  required=[f'android/shared/src/main/jniLibs/{a}/{n}'for a in abis for n in ['libonklav_speech.so','libsherpa-onnx-c-api.so','libonnxruntime.so']]+['android/shared/src/main/assets/speech/english.voicepack','android/shared/src/main/assets/speech/english.json']
  if host:required += ['core/speech/host-libs/linux-x86_64/'+n for n in ['libonklav_speech.so','libsherpa-onnx-c-api.so','libonnxruntime.so']]
  return all(p in proof['outputs']and(ROOT/p).is_file()and sha(ROOT/p)==proof['outputs'][p]for p in required)
 except (OSError,ValueError,KeyError):return False

def main():
 p=argparse.ArgumentParser();p.add_argument('--abis',default='arm64-v8a');p.add_argument('--host',action='store_true');p.add_argument('--check',action='store_true');a=p.parse_args();abis=a.abis.split(',')
 if any(x not in {'arm64-v8a','x86_64'}for x in abis):p.error('Unsupported speech ABI')
 if a.check:
  if not current(abis):raise SystemExit('Speech native/model proof is stale. Run scripts/build_speech.py with the pinned runtime dependencies.')
  print('Speech native/model proof matches');return
 deps=json.loads((ROOT/'docs/speech-dependencies.json').read_text())
 for name,digest in (deps['runtime_files']|deps['voice_assets']).items():
  assert (ROOT/name).is_file()and sha(ROOT/name)==digest, f'Pinned speech dependency changed: {name}'
 env=os.environ.copy();sdk=Path(env['ANDROID_HOME']);ndk=sdk/'ndk/27.2.12479018/toolchains/llvm/prebuilt/linux-x86_64/bin';target={'arm64-v8a':('aarch64-linux-android','aarch64-linux-android29-clang'),'x86_64':('x86_64-linux-android','x86_64-linux-android29-clang')};outputs={}
 for abi in abis:
  rust,linker=target[abi];libs=ROOT/f'android/shared/src/main/jniLibs/{abi}'
  assert all((libs/n).is_file()for n in ['libsherpa-onnx-c-api.so','libonnxruntime.so']),f'Restore the pinned Sherpa1.13.8 {abi} runtime from docs/speech-dependencies.json first'
  e=env|{'SHERPA_ONNX_LIB_DIR':str(libs),f'CARGO_TARGET_{rust.upper().replace("-","_")}_LINKER':str(ndk/linker),'RUSTFLAGS':'-C link-arg=-Wl,-z,max-page-size=16384'}
  subprocess.run(['cargo','build','--release','--locked','--manifest-path','core/speech/Cargo.toml','--target',rust],cwd=ROOT,env=e,check=True,timeout=300)
  shutil.copyfile(ROOT/f'core/speech/target/{rust}/release/libonklav_speech.so',libs/'libonklav_speech.so')
  for n in ['libonklav_speech.so','libsherpa-onnx-c-api.so','libonnxruntime.so']:
   file=libs/n;out=subprocess.check_output(['readelf','--wide','-l',str(file)],text=True);align=[int(line.split()[-1],16)for line in out.splitlines()if line.strip().startswith('LOAD ')];assert align and min(align)>=16384,(file,'16KiB alignment required');outputs[str(file.relative_to(ROOT))]=sha(file)
 if a.host:
  libs=ROOT/'core/speech/host-libs/linux-x86_64';e=env|{'SHERPA_ONNX_LIB_DIR':str(libs),'LD_LIBRARY_PATH':str(libs)};e.pop('RUSTFLAGS',None)
  subprocess.run(['cargo','test','--locked','--manifest-path','core/speech/Cargo.toml'],cwd=ROOT,env=e,check=True,timeout=300)
  subprocess.run(['cargo','build','--locked','--manifest-path','core/speech/Cargo.toml'],cwd=ROOT,env=e,check=True,timeout=300);shutil.copyfile(ROOT/'core/speech/target/debug/libonklav_speech.so',libs/'libonklav_speech.so')
  for n in ['libonklav_speech.so','libsherpa-onnx-c-api.so','libonnxruntime.so']:outputs[str((libs/n).relative_to(ROOT))]=sha(libs/n)
 for n in ['android/shared/src/main/assets/speech/english.voicepack','android/shared/src/main/assets/speech/english.json']:outputs[n]=sha(ROOT/n)
 proof={'engine':'official sherpa-onnx Rust1.13.8; original native Piper/eSpeak frontend','model':'kitten-nano-en-v0_8-fp32','default_speaker':2,'other_speaker':0,'inputs':{p:sha(ROOT/p)for p in inputs()},'outputs':outputs,'audio_output':'native AAudio,24kHz mono float, preallocated10second SPSC queue','android_device_tested':False}
 (ROOT/'docs/speech-native-provenance.json').write_text(json.dumps(proof,indent=2)+'\n')
if __name__=='__main__':main()
