#!/usr/bin/env python3
"""Verify pinned upstream source/patch and rebuild OnKlav's one shared Web engine.

Download the exact archive and emsdk release in docs/godot-web-engine.json. Extract
source into a private build directory, install/activate Emscripten4.0.20 and apply
the recorded patch with patch -p1. Run this script after sourcing emsdk_env.sh.
No new game-specific engine or native Android Godot library is produced.
"""
import argparse,hashlib,json,os,subprocess,sys,tarfile,shutil
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def verify(source,archive):
    info=json.loads((ROOT/'docs/godot-web-engine.json').read_text())
    digest=lambda b:hashlib.sha256(b).hexdigest()
    if hashlib.sha512(archive.read_bytes()).hexdigest()!=info['source_sha512']:raise ValueError('Upstream source archive checksum differs')
    if digest((ROOT/info['patch']).read_bytes())!=info['patch_sha256']:raise ValueError('Recorded patch differs')
    count=0
    with tarfile.open(archive)as tar:
        for entry in tar.getmembers():
            if not entry.isfile():continue
            parts=Path(entry.name).parts[1:]
            if '..' in parts or not parts:raise ValueError('Unsafe upstream archive path')
            relative=Path(*parts).as_posix();original=tar.extractfile(entry).read()
            expected=info['patched_file_sha256']if relative==info['patched_file']else digest(original)
            if digest((source/relative).read_bytes())!=expected:raise ValueError('Unexpected source modification: '+relative)
            count+=1
    version=subprocess.check_output(['emcc','--version'],text=True)
    if info['emscripten'] not in version.splitlines()[0]:raise ValueError('Use the recorded Emscripten version')
    return info,count

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--source',type=Path,required=True);p.add_argument('--source-archive',type=Path,required=True);p.add_argument('--verify-only',action='store_true');a=p.parse_args()
    info,count=verify(a.source.resolve(),a.source_archive.resolve())
    if not a.verify_only:
        subprocess.run([sys.executable,'-m','SCons',*info['scons_args']],cwd=a.source,env=dict(os.environ,BUILD_NAME=info['build_name']),check=True)
        template=a.source/'bin/godot.web.template_release.wasm32.nothreads.zip'
        shutil.copy2(template,ROOT/'developer/games/engine-template.zip')
        sys.path.insert(0,str(ROOT/'developer/games'))
        from loading import theme_template
        theme_template(ROOT/'developer/games/engine-template.zip')
    print(json.dumps({'verified_upstream_files':count,'emscripten':info['emscripten'],'source_and_patch':'matched','built':not a.verify_only}))
if __name__=='__main__':main()
