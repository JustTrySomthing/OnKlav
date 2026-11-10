"""Per-app source/artifact planning for the signed bundled Rust/Wasm SDK."""
import json
import zipfile
from build_inputs import sha

APPS = {
    'Clock': ('sdk/apps/clock','onklav_clock'),
    'Flashlight': ('sdk/apps/flashlight','onklav_flashlight'),
    'Camera': ('sdk/apps/camera','onklav_camera'),

    'Email': ('sdk/apps/email','onklav_email'),
    'VideoEditor': ('sdk/apps/video','onklav_video'),
    'Maps': ('sdk/apps/maps','onklav_maps'),
    'Audio': ('sdk/apps/audio','onklav_audio'),
    'Write': ('sdk/apps/write','onklav_write'),
    'Photo': ('sdk/apps/photo','onklav_photo'),
    'Calc': ('sdk/apps/calc','onklav_calc'),
    'Convert': ('sdk/apps/convert','onklav_convert'),
    'Weather': ('sdk/apps/weather','onklav_weather'),
    'eBooks': ('sdk/apps/ebooks','onklav_ebooks'),
    'AudioBooks': ('sdk/apps/audiobooks','onklav_audiobooks'),
    'Podcasts': ('sdk/apps/podcasts','onklav_podcasts'),
    'Radio': ('sdk/apps/radio','onklav_radio'),
    'Files': ('sdk/apps/files','onklav_files'),
    'Notes': ('sdk/apps/notes','onklav_notes'),
    'Counter': ('sdk/examples/counter','onklav_sdk_counter'),
    'RSS': ('sdk/apps/rss','onklav_rss'),
    'ReadAloud': ('sdk/apps/reader','onklav_reader'),
    'Browser': ('sdk/apps/browser','onklav_browser'),
    'YouTube': ('sdk/apps/youtube','onklav_youtube'),
    'Annotation': ('sdk/apps/annotation','onklav_annotation'),
    'Social': ('sdk/apps/social','onklav_social'),
    'WalkieTalkie': ('sdk/apps/walkie','onklav_walkie'),
}
RETIRED_BUNDLES={'Annotation','Notes'}
FLAGS='-C link-arg=-zstack-size=524288 -C link-arg=--max-memory=8388608'


def inputs(root):
    paths=[*(root/'sdk').rglob('*'),*(root/'developer/components').rglob('*')]
    return {p.relative_to(root).as_posix():sha(p) for p in sorted(paths)
            if p.is_file() and not {'target','.git','__pycache__'}.intersection(p.relative_to(root).parts)
            and p.suffix in {'.rs','.toml','.lock','.json'}}


def app_inputs(all_inputs,name):
    others=[folder+'/' for key,(folder,_) in APPS.items() if key!=name]
    # Unknown source paths are conservatively shared, never silently ignored.
    return {p:h for p,h in all_inputs.items() if not any(p.startswith(x) for x in others) and not (name=='Counter' and p.startswith('developer/components/'))}


def package_valid(root,name,record):
    try:
        if record['path'] != f'sdk/packages/{name}.onklav-a':return False
        path=root/record['path']
        if sha(path)!=record['sha256']:return False
        with zipfile.ZipFile(path) as z:
            if len(z.namelist())!=3 or set(z.namelist())!={'manifest.json','main.wasm','signature.ed25519'}:return False
            if json.loads(z.read('manifest.json'))!=record['manifest']:return False
            if len(z.read('signature.ed25519'))!=64 or not z.read('main.wasm').startswith(b'\0asm'):return False
            import hashlib
            return hashlib.sha256(z.read('main.wasm')).hexdigest()==record['wasm_sha256']
    except (OSError,KeyError,ValueError,TypeError,AttributeError,zipfile.BadZipFile):
        return False


def plan(root,previous):
    current=inputs(root);old=previous.get('inputs_sha256',{})
    per_app=previous.get('package_inputs_sha256',{})
    result={}
    for name in APPS:
        expected=app_inputs(current,name)
        recorded=per_app.get(name,app_inputs(old,name))
        valid=package_valid(root,name,previous.get('packages',{}).get(name,{}))
        flags=previous.get('package_flags',{}).get(name,previous.get('flags'))
        stale=(not (root/f'{APPS[name][0]}/manifest.json').is_file() or not expected or recorded!=expected or not valid or flags!=FLAGS)
        bundled=root/f'android/app/src/main/assets/sdk/{name}.onklav-a'
        copy=((root/'android/app/src/main').is_dir() and not stale and name!='Counter' and name not in RETIRED_BUNDLES and (not bundled.is_file() or sha(bundled)!=previous['packages'][name]['sha256']))
        result[name]={'build':stale,'restore_bundle':copy,'remove_bundle':name in RETIRED_BUNDLES and bundled.is_file(),'inputs_sha256':expected}
    return current,result


def validate_update(previous,manifest,wasm_sha,package_sha=None):
    if not previous:return
    old=previous['manifest']
    if manifest['publisher']!=old['publisher'] or manifest['id']!=old['id']:
        raise ValueError('Retain the existing app identity and publisher')
    changed=manifest!=old or wasm_sha!=previous['wasm_sha256'] or (package_sha is not None and package_sha!=previous['sha256'])
    if manifest['version']<old['version'] or (changed and manifest['version']<=old['version']):
        raise ValueError('Changed app code requires a newer manifest version for installed-app updates')
