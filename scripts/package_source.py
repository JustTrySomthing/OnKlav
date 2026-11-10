#!/usr/bin/env python3
"""Save a tested source/SDK checkpoint without building, relabelling or copying an APK."""
import hashlib,json,sys,zipfile,argparse
from build_inputs import native_current, sdk_current, games_current
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('destination')
parser.add_argument('--allow-known-failures',action='store_true',help='Save an explicitly incomplete source checkpoint with recorded known failures')
args=parser.parse_args()
dest=Path(args.destination).resolve();dest.mkdir(parents=True,exist_ok=True)
report=json.loads((ROOT/'docs/source-verification.json').read_text())
failed=report['tests']['failed']
if failed:
 assert args.allow_known_failures, 'Failing checks must be explicit when saving an incomplete checkpoint'
 known=report['tests'].get('known_failures',[])
 assert len(known)==failed and all(x.get('name') and x.get('evidence') and x.get('previously_documented') for x in known)
 assert all((ROOT/x['evidence']).is_file() and all((ROOT/p).is_file() for p in x['previously_documented']) for x in known)
 assert report['tests'].get('unexpected_failures')==0
 assert report.get('full_rewrite_complete') is False
assert sdk_current(ROOT), 'SDK inputs or packages are stale'
assert games_current(ROOT), 'Game inputs or exported pack are stale'
proof=json.loads((ROOT/'docs/host-native-provenance.json').read_text())
assert hashlib.sha256((ROOT/'core/host-libs/linux-x86_64/libonklav_core.so').read_bytes()).hexdigest()==proof['host_library_sha256']
for name,digest in proof['files_sha256'].items():assert hashlib.sha256((ROOT/name).read_bytes()).hexdigest()==digest, name
sdk=json.loads((ROOT/'docs/app-sdk-provenance.json').read_text())
for name,digest in sdk['inputs_sha256'].items():assert hashlib.sha256((ROOT/name).read_bytes()).hexdigest()==digest, name
games=json.loads((ROOT/'docs/games-provenance.json').read_text())
for name,digest in games['files_sha256'].items():assert hashlib.sha256((ROOT/name).read_bytes()).hexdigest()==digest, name
for name,digest in games['assets_sha256'].items():assert hashlib.sha256((ROOT/name).read_bytes()).hexdigest()==digest, name
excluded={'build' ,'target','.gradle','.godot','.kotlin','__pycache__','.git'}
files=[]
# Prune build trees before traversing them.
import os
for base,dirs,names in os.walk(ROOT):
 dirs[:]=sorted(d for d in dirs if d not in excluded)
 for name in sorted(names):
  p=Path(base)/name;r=p.relative_to(ROOT)
  if p.is_symlink() or name=='local.properties' or p.suffix in {'.apk','.keystore','.jks','.key','.pyc'}:continue
  if r.parts[:2]==('docs','evidence') and p.suffix.lower() in {'.png','.jpg','.jpeg','.webp'}:continue
  # Retain verified reusable mobile artifacts. A fresh chat can assemble a
  # Kotlin-only change without restoring Rust/NDK or downloading a prior APK.
  if 'jniLibs' in r.parts and name=='libonklav_core.so':
   if not native_current(ROOT,[p.parent.name]):continue
  assert p.suffix!='.java',r
  files.append((p,r.as_posix()))
source=dest/'OnKlav-Platform-Source.zip'
with zipfile.ZipFile(source,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
 for p,r in files:z.write(p,'OnKlav/'+r)
with zipfile.ZipFile(dest/'OnKlav-App-SDK-Starter.zip','w',zipfile.ZIP_DEFLATED) as z:
 z.writestr('README.md','# OnKlav App SDK0.4 — Developer Preview\n\nStart with [the developer guide](developer/README.md). It contains prerequisites, project creation, build, signing, inspection, testing and phone installation.\n\nThis archive is app-development source, not an Android APK or game SDK.\n')
 for p,r in files:
  if r == 'developer/games/GAME-MUSIC.md' or (not r.startswith('developer/games/') and (r.startswith(('sdk/','developer/','core/src/','docs/fixtures/','docs/licenses/rust/')) or r in {'core/Cargo.toml','core/Cargo.lock','scripts/build_sdk_apps.py','scripts/sdk_build_plan.py','scripts/build_inputs.py','scripts/test_sdk_build_plan.py','docs/SDK-BUILD-WORKFLOW.md','docs/NON-GAME-REVIEW.md','docs/RESUME-0.46.0.md','docs/RESUME-0.47.0.md','docs/RESUME-0.48.0.md','docs/RESUME-0.49.0.md','docs/RESUME-0.50.0.md','docs/RESUME-0.51.0.md','docs/MEDIA-CARRIER-0.48.0.md','docs/FILES-APP.md','docs/app-sdk-provenance.json','docs/APPS-PORT.md','docs/RSS-PORT.md','docs/READER-PORT.md','docs/BROWSER-PORT.md','docs/YOUTUBE-PORT.md','docs/ANNOTATION-PORT.md','docs/SOCIAL-PLACEHOLDER.md','docs/WALKIE-PORT.md','docs/SETTINGS-PORT.md','docs/HISTORY-BACKUP.md','docs/SDK-HISTORY.md','docs/SDK-READINESS-0.2.md','docs/SDK-READINESS-0.3.md','docs/SDK-READINESS-0.4.md','docs/SDK-REVIEW-0.4.md','docs/RESUME-0.70.0.md','docs/RESUME-0.69.0.md','TODO.md'})):z.write(p,r)
for p in [source,dest/'OnKlav-App-SDK-Starter.zip']:
 with zipfile.ZipFile(p) as z:assert z.testzip() is None
for name in report.get('internal_packages',[]):
 (dest/(name+'.onklav-a')).write_bytes((ROOT/('sdk/packages/'+name+'.onklav-a')).read_bytes())
print(json.dumps({'source':str(source),'source_files':len(files),'apk_created':False,'sdk_packages':len(sdk['packages'])-1}))
