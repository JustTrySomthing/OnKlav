#!/usr/bin/env python3
"""Build/sign only changed internal SDK apps. Unchanged packages need no Rust tools/key."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import zipfile
from build_inputs import sha,proof
from sdk_build_plan import APPS,FLAGS,app_inputs,inputs,plan,validate_update,RETIRED_BUNDLES

ROOT=Path(__file__).resolve().parents[1]


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--app',action='append',choices=list(APPS),help='Build only this app if stale; repeat for several')
    parser.add_argument('--plan',action='store_true')
    args=parser.parse_args()
    previous=proof(ROOT,'app-sdk-provenance.json')
    current,decisions=plan(ROOT,previous)
    selected=set(args.app or APPS)
    pending=[n for n in APPS if n in selected and decisions[n]['build']]
    copies=[n for n in APPS if n in selected and decisions[n]['restore_bundle']]
    removals=[n for n in APPS if n in selected and decisions[n].get('remove_bundle')]
    summary={'build':pending,'restore_bundles':copies,'remove_bundles':removals,
             'reuse':[n for n in APPS if n in selected and not decisions[n]['build']],
             'unselected_stale':[n for n in APPS if n not in selected and decisions[n]['build']]}
    print(json.dumps(summary,indent=2),flush=True)
    if args.plan:return
    report=dict(previous)
    report['package_inputs_sha256']={n:previous.get('package_inputs_sha256',{}).get(n,app_inputs(previous.get('inputs_sha256',{}),n)) for n in APPS}
    report.setdefault('packages',{})
    report['package_flags']={n:previous.get('package_flags',{}).get(n,previous.get('flags')) for n in APPS}
    report['flags']=FLAGS
    report['runtime']='rust-wasm-ui.v1';report['wasm_target']='wasm32-unknown-unknown'
    def run(command,env=None):
        subprocess.run(list(map(str,command)),cwd=ROOT,env=env,check=True,timeout=180)
    def save():
        now,states=plan(ROOT,report)
        complete=not any(v['build'] for v in states.values())
        if complete:report['inputs_sha256']=now
        report['all_packages_current']=complete
        # Atomic proof publication; a failed later app cannot erase earlier work.
        path=ROOT/'docs/app-sdk-provenance.json';path.parent.mkdir(parents=True,exist_ok=True);temporary=path.with_suffix('.json.tmp')
        temporary.write_text(json.dumps(report,indent=2)+'\n');os.replace(temporary,path)
    if pending:
        raw=os.environ.get('ONKLAV_APPS_SIGNING_KEY')
        if not raw:raise SystemExit('Changed SDK apps need the existing ONKLAV_APPS_SIGNING_KEY; unchanged apps do not')
        key=Path(raw).resolve()
        if not key.is_file() or ROOT in key.parents:raise SystemExit('Keep the existing publisher key outside the public source tree')
        run(['cargo','build','--locked','--manifest-path','core/Cargo.toml','--bin','onklav-pack'])
        toolchain=subprocess.check_output(['rustc','--version'],text=True).strip()
        env=os.environ.copy();env['RUSTFLAGS']=FLAGS
        for name in pending:
            folder,stem=APPS[name];before=app_inputs(inputs(ROOT),name)
            print(f'{name}: compiling changed app',flush=True)
            run(['cargo','build','--locked','--manifest-path',folder+'/Cargo.toml','--release','--target','wasm32-unknown-unknown'],env)
            wasm=ROOT/f'{folder}/target/wasm32-unknown-unknown/release/{stem}.wasm'
            manifest=json.loads((ROOT/f'{folder}/manifest.json').read_text())
            wasm_sha=sha(wasm)
            if name in report['packages']:
                validate_update(report['packages'][name],dict(manifest,publisher=report['packages'][name]['manifest']['publisher']),wasm_sha)
            output=ROOT/f'sdk/packages/{name}.onklav-a';output.parent.mkdir(exist_ok=True)
            with tempfile.TemporaryDirectory(prefix='.sdk-build-',dir=output.parent) as scratch:
                staged=Path(scratch)/output.name
                run([ROOT/'core/target/debug/onklav-pack','pack',folder+'/manifest.json',wasm,key,staged])
                with zipfile.ZipFile(staged) as z:
                    signed=json.loads(z.read('manifest.json'))
                    assert signed==dict(manifest,publisher=signed['publisher'])
                    if 'publisher' in manifest:assert manifest['publisher']==signed['publisher']
                    known={p['manifest']['publisher'] for p in report['packages'].values()}
                    if known:assert signed['publisher'] in known, 'Restore the existing bundled app publisher key'
                    manifest=signed
                    assert z.read('main.wasm')==wasm.read_bytes()
                    assert len(z.read('signature.ed25519'))==64
                if before!=app_inputs(inputs(ROOT),name):raise RuntimeError('App inputs changed during compilation/signing')
                package_sha=sha(staged)
                validate_update(report['packages'].get(name),manifest,wasm_sha,package_sha)
                os.replace(staged,output)
            report['packages'][name]={'path':output.relative_to(ROOT).as_posix(),'sha256':package_sha,'wasm_sha256':wasm_sha,'manifest':manifest,'rust':toolchain}
            report['package_inputs_sha256'][name]=before
            report['package_flags'][name]=FLAGS
            report['rust']=toolchain
            if name!='Counter' and name not in RETIRED_BUNDLES and (ROOT/'android/app/src/main').is_dir():shutil.copyfile(output,ROOT/f'android/app/src/main/assets/sdk/{name}.onklav-a')
            save()
    for name in removals:
        (ROOT/f'android/app/src/main/assets/sdk/{name}.onklav-a').unlink(missing_ok=True)
    for name in copies:
        shutil.copyfile(ROOT/f'sdk/packages/{name}.onklav-a',ROOT/f'android/app/src/main/assets/sdk/{name}.onklav-a')
    save()
    print(json.dumps({'built':pending,'bundles_restored':copies,'all_packages_current':report['all_packages_current']}))


if __name__=='__main__':main()
