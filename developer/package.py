#!/usr/bin/env python3
"""Build a self-contained, source-only ADK starter without touching an APK."""
import argparse,hashlib,json,os,re,tempfile,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
EXCLUDED={'target','build','dist','.git','.gradle','.godot','.kotlin','__pycache__','.pytest_cache'}
SECRET_SUFFIXES={'.key','.pem','.p12','.keystore','.jks','.apk','.pyc','.so','.a','.exe'}
def source_files(root=ROOT):
    files={}
    def add(p):
        if p.is_symlink() or not p.is_file() or not p.resolve().is_relative_to(root.resolve()):raise ValueError('Unsafe source: '+str(p))
        rel=p.relative_to(root).as_posix()
        if rel.startswith('developer/games/') and rel != 'developer/games/GAME-MUSIC.md':return
        if set(p.relative_to(root).parts)&EXCLUDED or p.suffix.lower() in SECRET_SUFFIXES or p.name=='local.properties':return
        files[rel]=p
    for folder in ['developer','sdk','core/src','docs/licenses/rust']:
        for base,dirs,names in os.walk(root/folder):
            dirs[:]=sorted(v for v in dirs if v not in EXCLUDED and not (Path(base)/v).is_symlink())
            for name in sorted(names):add(Path(base)/name)
    for name in ['core/Cargo.toml','core/Cargo.lock']:add(root/name)
    # Include the transitive local Markdown-link closure instead of a stale hand-maintained guide list.
    checked=set()
    while True:
        pending=[(n,p)for n,p in files.items()if p.suffix=='.md' and n not in checked]
        if not pending:break
        for name,p in pending:
            checked.add(name)
            for target in re.findall(r'\[[^\]]*\]\(([^)]+)\)',p.read_text(encoding='utf-8')):
                if '://' in target or target.startswith('#'):continue
                link=(p.parent/target.split('#')[0]).resolve()
                if not link.is_relative_to(root.resolve()) or not link.exists():raise ValueError('Broken local documentation link: '+name+' -> '+target)
                if link.is_dir():
                    for base,dirs,names in os.walk(link):
                        dirs[:]=[v for v in dirs if v not in EXCLUDED]
                        for item in names:add(Path(base)/item)
                else:add(link)
    return files

def verify_starter(path):
    with zipfile.ZipFile(path)as z:
        entries=z.infolist();names=[e.filename for e in entries]
        if len(names)!=len(set(names))or len(names)>10000:raise ValueError('Invalid starter entry inventory.')
        if any(Path(n).is_absolute()or ".." in Path(n).parts or "\\" in n for n in names):raise ValueError('Unsafe starter path.')
        if any(e.file_size>64*1024*1024 for e in entries)or sum(e.file_size for e in entries)>256*1024*1024:raise ValueError('Starter exceeds source bundle bounds.')
        if z.getinfo('CONTENTS.json').file_size>2*1024*1024:raise ValueError('Starter manifest is too large.')
        manifest=json.loads(z.read('CONTENTS.json'));hashes=manifest.get('files_sha256')
        if not isinstance(hashes,dict)or set(names)!=set(hashes)|{'CONTENTS.json'}:raise ValueError('Starter content inventory mismatch.')
        for name,digest in hashes.items():
            if hashlib.sha256(z.read(name)).hexdigest()!=digest:raise ValueError('Starter content mismatch: '+name)
        return {'verified':True,'files':len(names),'sdk_tools':manifest['sdk_tools'],'reference_apk':manifest['reference_apk'],'host_protocol_build':manifest['host_protocol_build']}

def build_starter(destination,root=ROOT):
    destination=Path(destination).resolve()
    if destination.exists():raise ValueError('Choose a new starter output; existing files are preserved.')
    contract=json.loads((root/'developer/contract.json').read_text())
    files=source_files(root)
    docs={'README.md':('# OnKlav ADK '+contract['sdk_tools']+' — Developer Preview\n\nStart with [developer/README.md](developer/README.md), then [current host/API status](developer/docs/CURRENT-HOST.md). Install prerequisites and run `python3 developer/ci_smoke.py` (`python` on Windows). Apps ship as signed .onklav-a packages. This is source tooling, not an APK, compiler bundle or game SDK.\n').encode()}
    entries={name:p.read_bytes()for name,p in files.items()};entries.update(docs)
    manifest={'sdk_tools':contract['sdk_tools'],'reference_apk':contract['reference_apk'],'host_protocol_build':contract['reference_host_build'],'files_sha256':{name:hashlib.sha256(data).hexdigest()for name,data in sorted(entries.items())}}
    entries['CONTENTS.json']=(json.dumps(manifest,indent=2)+'\n').encode()
    destination.parent.mkdir(parents=True,exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=destination.parent,suffix='.zip',delete=False)as f:temp=Path(f.name)
    try:
        with zipfile.ZipFile(temp,'w',zipfile.ZIP_DEFLATED)as z:
            for name,data in sorted(entries.items()):z.writestr(name,data)
        with zipfile.ZipFile(temp)as z:
            if z.testzip()is not None:raise ValueError('Starter archive failed CRC verification.')
            for name,digest in manifest['files_sha256'].items():
                if hashlib.sha256(z.read(name)).hexdigest()!=digest:raise ValueError('Starter content mismatch: '+name)
        # Exclusive publication: do not replace a concurrently-created release.
        with destination.open('xb')as out,temp.open('rb')as src:
            import shutil;shutil.copyfileobj(src,out)
    finally:temp.unlink(missing_ok=True)
    return {'starter':str(destination),'files':len(entries),'bytes':destination.stat().st_size,'sdk_tools':contract['sdk_tools'],'host_protocol_build':contract['reference_host_build']}
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('output',type=Path,nargs='?');p.add_argument('--verify',type=Path);a=p.parse_args()
    if (a.output is None)==(a.verify is None):p.error('Provide an output path or --verify ARCHIVE.')
    try:print(json.dumps(verify_starter(a.verify)if a.verify else build_starter(a.output),indent=2))
    except (ValueError,OSError,KeyError,zipfile.BadZipFile)as e:p.exit(1,'OnKlav ADK: '+str(e)+'\n')
