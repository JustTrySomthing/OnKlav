#!/usr/bin/env python3
"""Create, test, sign and share Godot games for OnKlav. No owner approval or accounts."""
import argparse, hashlib, json, os, re, shutil, subprocess, sys, tempfile, zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent
CONTRACT=ROOT/'engine.json'
MAX_PACKAGE=64*1024*1024
MAX_ENTRY=64*1024*1024
MAX_TOTAL=128*1024*1024
TEMPLATES = ("touch2d", "scene3d", "turn", "platformer", "firstperson", "topdown", "arena", "puzzle", "idle", "runner", "racing", "defense", "rhythm", "match3", "sports", "builder", "merge", "breakout", "wordquiz", "cards", "adventure", "tactics", "campaign", "mobile")

def encoded(obj):return (json.dumps(obj,sort_keys=True,separators=(',',':'),ensure_ascii=True)+'\n').encode()
def digest(data):return hashlib.sha256(data).hexdigest()
def contract():return json.loads(CONTRACT.read_text())
def safe(name):return len(name)<=200 and bool(re.fullmatch(r'[a-zA-Z0-9_.-]+(?:/[a-zA-Z0-9_.-]+)*',name)) and all(x not in ('.','..') for x in name.split('/'))
def metadata(m, engine_contract=None):
    if not isinstance(m,dict) or m.get('schema')!='onklav.package.v1' or m.get('kind')!='game' or m.get('runtime')!='godot.web.v1':raise ValueError('Unsupported game manifest')
    if not re.fullmatch(r'[a-z][a-z0-9]*(?:\.[a-z][a-z0-9]*)+',m.get('id','')) or len(m['id'])>160:raise ValueError('Use a lowercase reverse-domain game ID')
    for k in ('name','version','description'):
        if not isinstance(m.get(k),str) or not m[k].strip() or len(m[k].encode('utf-16-le'))//2>200 or any(ord(c)<32 for c in m[k]):raise ValueError('Invalid '+k)
    modes=m.get('play_modes',['single'])
    if modes not in (['single'],['single','turn_sms'])or('turn_sms'in modes and m.get('profile')!='turn_based'):raise ValueError('Use single-only or single plus SMS turn-based play')
    c=contract() if engine_contract is None else engine_contract
    if m.get('controls')!='onklav.game.controls.v2' or type(m.get('min_host'))is not int or not 155<=m['min_host']<=2147483647:raise ValueError('Use OnKlav game controls v2 and minimum host155 or newer')
    if m.get('engine')!=c['version'] or m.get('engine_assets')!=c['files_sha256'] or m.get('entry')!='web/index.html' or m.get('profile') not in ('arcade','turn_based') or m.get('orientation') not in ('portrait','landscape','sensor'):raise ValueError('Unsupported shared engine, entry, profile or orientation')
    if type(m.get('version_code'))is not int or not 1<=m['version_code']<=2147483647 or m.get('permissions')!=[]:raise ValueError('A positive version_code and empty permissions list are required')
    return m

def run(args,timeout=120):
    p=subprocess.run([str(a)for a in args],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,encoding='utf-8',errors='replace',timeout=timeout)
    if p.returncode or 'SCRIPT ERROR:' in p.stdout or 'ERROR:' in p.stdout:raise ValueError(p.stdout[-6000:]or'Engine command failed')
    print(p.stdout,file=sys.stderr,end='');return p.stdout

def engine(binary):
    v=run([binary,'--version']).strip()
    if not v.startswith(contract()['version']+'.stable.'):raise ValueError('Use Godot '+contract()['version']+' stable to match OnKlav')
    return v

def crypto():
    from cryptography.hazmat.primitives.asymmetric import ec
    from cryptography.hazmat.primitives import serialization,hashes
    return ec,serialization,hashes

def keygen(path):
    ec,_,_=crypto();raw=ec.generate_private_key(ec.SECP256R1()).private_numbers().private_value.to_bytes(32,'big')
    with open(path,'xb')as f:os.chmod(path,0o600);f.write(raw)
    return {'key':str(path),'warning':'Keep this publisher key private and outside game source.'}

TEMPLATE = ".onklav-web-template.zip"
def template_bytes():
    data=(ROOT/'engine-template.zip').read_bytes()
    if digest(data)!=contract()['export_template_sha256']:raise ValueError('OnKlav export template is damaged; restore the SDK')
    return data

def set_template(preset):
    sections=re.split(r'(?m)(?=^\[preset\.)',preset)
    web=[i for i,section in enumerate(sections) if section.startswith('[preset.0]\n')]
    options=[i for i,section in enumerate(sections) if section.startswith('[preset.0.options]\n')]
    if len(web)!=1 or len(options)!=1:raise ValueError('Expected one OnKlav Web preset at preset.0')
    def excluded(match):
        patterns=[item.strip() for item in match[1].split(',') if item.strip()]
        return 'exclude_filter="'+','.join(dict.fromkeys([*patterns,TEMPLATE,'.onklav-engine-backup/**']))+'"'
    sections[web[0]]=re.sub(r'^exclude_filter="(.*)"$',excluded,sections[web[0]],flags=re.M)
    def included(match):
        patterns=[item.strip() for item in match[1].split(',') if item.strip()]
        return 'include_filter="'+','.join(dict.fromkeys([*patterns,'music/music.json','music/*/music.json']))+'"'
    sections[web[0]]=re.sub(r'^include_filter="(.*)"$',included,sections[web[0]],flags=re.M)
    marker='[preset.0.options]'
    section=re.sub(r'^custom_template/(?:release|debug)=.*\n?', '',sections[options[0]],flags=re.M)
    sections[options[0]]=section.replace(marker,marker+'\ncustom_template/release="res://'+TEMPLATE+'"',1)
    return ''.join(sections)

def upgrade(path):
    manifest=path/'manifest.json';preset=path/'export_presets.cfg';tool=path/TEMPLATE;project=path/'project.godot'
    shared=ROOT/'templates/shared'
    helpers=[path/source.name for source in sorted(shared.glob('*.gd')) if source.name!='tests.gd']
    helpers += [path/name for name in ('onklav-splash.png','onklav-splash.svg')]
    old={p:p.read_bytes() if p.exists() else None for p in [manifest,preset,tool,project,*helpers]}
    versions=json.loads((ROOT/'helper-versions.json').read_text())
    for p in helpers:
        if old[p] is not None and digest(old[p]) not in versions.get(p.name,[]):raise ValueError('Custom helper retained; merge the new SDK helper explicitly before upgrading: '+p.name)
    m=json.loads(old[manifest]);c=contract()
    if m.get('engine')!=c['version'] or m.get('engine_assets')!=c['files_sha256']:
        raise ValueError('Engine fingerprint differs; use the matching SDK or migrate the project explicitly')
    m.update(engine=c['version'],engine_assets=c['files_sha256'],controls='onklav.game.controls.v2',min_host=max(166,m.get('min_host',166)))
    metadata(m)
    fixed=set_template(old[preset].decode());data=template_bytes()
    backup=path/'.onklav-engine-backup'
    if backup.exists():raise ValueError('Existing engine backup retained; archive it explicitly before another upgrade')
    backup.mkdir()
    for p,b in old.items():
        if b is not None:(backup/p.name).write_bytes(b)
    try:
        tool.write_bytes(data);preset.write_text(fixed);manifest.write_bytes(encoded(m))
        config=old[project].decode('utf-8')
        if 'boot_splash/image='not in config:
            config=config.replace('[application]','[application]\nboot_splash/image="res://onklav-splash.png"\nboot_splash/bg_color=Color(0.027,0.055,0.094,1)\nboot_splash/show_image=true')
        project.write_text(config,encoding='utf-8')
        for p in helpers:p.write_bytes((shared/p.name).read_bytes())
    except BaseException:
        for p,b in old.items():
            if b is None:p.unlink(missing_ok=True)
            else:p.write_bytes(b)
        raise
    return {'project':str(path),'engine_updated':True,'backup':str(backup),'next':'Increment version_code before publishing an updated game. Source, identity, version and publisher key are preserved.'}

def create(path,game_id,name,template,play_mode='single',orientation=None):
    if template not in TEMPLATES:raise ValueError('Unknown template')
    orientation=orientation or ('landscape' if template in ('platformer','firstperson','topdown','arena') else 'portrait')
    if play_mode not in ('single','turn_sms')or(play_mode=='turn_sms'and template!='turn'):raise ValueError('SMS turns require the turn-based template')
    c=contract();m={'schema':'onklav.package.v1','kind':'game','runtime':'godot.web.v1','engine':c['version'],'engine_assets':c['files_sha256'],'entry':'web/index.html','id':game_id,'name':name,'version':'0.1.0','version_code':1,'description':'A new OnKlav game','profile':'turn_based'if template=='turn'else'arcade','orientation':orientation,'permissions':[],'controls':'onklav.game.controls.v2','min_host':166,'play_modes':['single','turn_sms']if play_mode=='turn_sms'else['single']}
    metadata(m)
    if path.exists():raise ValueError('Destination already exists')
    path.mkdir(parents=True)
    for folder in ['shared',template]:
        for file in (ROOT/'templates'/folder).iterdir():
            if file.is_file():shutil.copy2(file,path/file.name)
    (path/TEMPLATE).write_bytes(template_bytes())
    preset=path/'export_presets.cfg'
    preset_text=preset.read_text()
    authored_data={'wordquiz':'questions.json','campaign':'levels.json'}.get(template)
    if authored_data:
        preset_text=preset_text.replace('include_filter=""', f'include_filter="{authored_data}"')
    preset.write_text(set_template(preset_text))
    (path/'manifest.json').write_bytes(encoded(m))
    p=path/'project.godot'
    tokens={'TITLE':json.dumps(name,ensure_ascii=False)[1:-1],'GAME_ID':game_id}
    p.write_text(re.sub(r'TITLE|GAME_ID',lambda match:tokens[match[0]],p.read_text(encoding='utf-8')),encoding='utf-8')
    if orientation=='landscape':p.write_text(p.read_text().replace('viewport_width=600','viewport_width=1000').replace('viewport_height=1000','viewport_height=600'))
    return {'project':str(path),'template':template,'engine':c['version']}

def check(path,binary):
    m=metadata(json.loads((path/'manifest.json').read_text(encoding='utf-8')));engine(binary)
    if not (path/TEMPLATE).is_file() or digest((path/TEMPLATE).read_bytes())!=contract()['export_template_sha256']:raise ValueError('Use the matching OnKlav template; run upgrade PROJECT with this SDK')
    if not(path/'tests.gd').is_file():raise ValueError('Provide tests.gd before packaging')
    run([binary,'--headless','--path',path,'--editor','--import'])
    run([binary,'--headless','--path',path,'--script','res://tests.gd'])
    boot=run([binary,'--headless','--path',path,'--quit-after','20'])
    if 'ONKLAV_SESSION_READY'not in boot:raise ValueError('Connect snapshot/restore/new-game through OnKlavGame.attach_session; see GAME-CONTROLS.md')
    return m

def package_files(m,files,output,key):
    metadata(m)
    if output.exists():raise ValueError('Output exists; choose a new output path')
    ec,s,hashes=crypto();raw=key.read_bytes()
    if len(raw)!=32:raise ValueError('Expected a 32-byte publisher private key')
    signer=ec.derive_private_key(int.from_bytes(raw,'big'),ec.SECP256R1());pub=signer.public_key().public_bytes(s.Encoding.DER,s.PublicFormat.SubjectPublicKeyInfo)
    files={**files,'manifest.json':encoded(m)}
    inventory={n:digest(b)for n,b in sorted(files.items())}
    publisher=encoded({'algorithm':'ECDSA-P256-SHA256','public_key':pub.hex(),'files_sha256':inventory})
    files['publisher.json']=publisher;files['signature.bin']=signer.sign(b'onklav.game.signature.v1\0'+publisher,ec.ECDSA(hashes.SHA256()))
    if len(files)>128 or any(len(b)>MAX_ENTRY for b in files.values())or sum(map(len,files.values()))>MAX_TOTAL:raise ValueError('Game content exceeds limits')
    output.parent.mkdir(parents=True,exist_ok=True)
    # Verify in a private sibling directory before publishing an installable file.
    with tempfile.TemporaryDirectory(prefix='.onklav-package-', dir=output.parent) as temporary:
        staged=Path(temporary)/output.name
        with zipfile.ZipFile(staged,'w',zipfile.ZIP_DEFLATED) as z:
            for name,data in sorted(files.items()):
                info=zipfile.ZipInfo(name,(2026,1,1,0,0,0))
                info.compress_type=zipfile.ZIP_DEFLATED
                info.external_attr=0o100644<<16
                z.writestr(info,data)
        result=verify(staged)
        with output.open('xb') as destination:
            try:
                with staged.open('rb') as source:
                    shutil.copyfileobj(source,destination)
                destination.flush()
            except BaseException:
                destination.close()
                output.unlink()
                raise
    return result

def export_web(path,output,binary):
    m=check(path,binary)
    if output.exists():raise ValueError('Export folder already exists')
    output.mkdir(parents=True)
    run([binary,'--headless','--path',path,'--export-release','OnKlav Web',output/'index.html'])
    html=output/'index.html'
    from loading import configure_brand
    text=configure_brand(html.read_text(),path);marker='engine.startGame({'
    if text.count(marker)==1:html.write_text(text.replace(marker,marker+'onExit: function() { console.log("ONKLAV_GAME_EXIT"); },'))
    shutil.copy2(ROOT/'onklav-controls.json',output/'onklav-controls.json')
    for name,h in contract()['files_sha256'].items():
        if not(output/name).is_file()or digest((output/name).read_bytes())!=h:raise ValueError('Export engine differs from the shared OnKlav engine: '+name)
    return m

def build(path,output,key,binary):
    with tempfile.TemporaryDirectory(prefix='onklav-game-')as temp:
        web=Path(temp)/'export';m=export_web(path,web,binary)
        files={'web/'+p.name:p.read_bytes()for p in web.iterdir()if p.is_file()and p.name not in contract()['files_sha256']}
        if 'web/index.html'not in files or 'web/index.pck'not in files:raise ValueError('Web export incomplete')
        return package_files(m,files,output,key)

def preview(path,binary):
    from http.server import ThreadingHTTPServer,SimpleHTTPRequestHandler
    from functools import partial
    import webbrowser
    with tempfile.TemporaryDirectory(prefix='onklav-game-preview-')as temp:
        web=Path(temp)/'export';export_web(path,web,binary)
        class Handler(SimpleHTTPRequestHandler):
            def end_headers(self):
                self.send_header('Content-Security-Policy',"default-src 'none'; script-src 'self' 'unsafe-inline' 'unsafe-eval' 'wasm-unsafe-eval'; connect-src 'self'; img-src 'self' data: blob:; media-src 'self' blob:; style-src 'self' 'unsafe-inline'; font-src 'self'; worker-src 'self' blob:; frame-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'")
                super().end_headers()
        with ThreadingHTTPServer(('127.0.0.1',0),partial(Handler,directory=str(web)))as server:
            url='http://127.0.0.1:'+str(server.server_port)+'/index.html'
            print('Preview: '+url+' (Ctrl+C to stop)',flush=True);webbrowser.open(url)
            try:server.serve_forever()
            except KeyboardInterrupt:pass
    return {'preview_closed':True}

def verify(path,expected_publisher=None,host_code=None,engine_contract=None):
    engine_contract=contract() if engine_contract is None else engine_contract
    ec,serialization,hashes=crypto()
    if path.stat().st_size>MAX_PACKAGE:raise ValueError('Package exceeds 64 MiB')
    with zipfile.ZipFile(path)as z:
        es=z.infolist();names=[e.filename for e in es]
        if not 5<=len(es)<=128 or len(set(names))!=len(names)or any(not safe(e.filename)or e.is_dir()or e.file_size>MAX_ENTRY or e.flag_bits&1 or((e.external_attr>>16)&0o170000)==0o120000 for e in es)or sum(e.file_size for e in es)>MAX_TOTAL:raise ValueError('Unsafe, duplicate or oversized package members')
        if not{'manifest.json','publisher.json','signature.bin','web/index.html','web/index.pck'}<=set(names):raise ValueError('Incomplete game')
        if z.getinfo('manifest.json').file_size>16384 or z.getinfo('publisher.json').file_size>32768 or not 64<=z.getinfo('signature.bin').file_size<=80:raise ValueError('Metadata too large')
        m=metadata(json.loads(z.read('manifest.json')),engine_contract)
        if host_code is not None and host_code < m['min_host']:
            raise ValueError(f"Game requires OnKlav build {m['min_host']}; target build is {host_code}")
        praw=z.read('publisher.json');p=json.loads(praw)
        pub=bytes.fromhex(p['public_key']);fingerprint=digest(pub)
        if p.get('algorithm')!='ECDSA-P256-SHA256' or len(pub)!=91:raise ValueError('Unsupported publisher signature')
        public=serialization.load_der_public_key(pub)
        if not isinstance(public,ec.EllipticCurvePublicKey)or not isinstance(public.curve,ec.SECP256R1):raise ValueError('Unsupported publisher key')
        public.verify(z.read('signature.bin'),b'onklav.game.signature.v1\0'+praw,ec.ECDSA(hashes.SHA256()))
        if expected_publisher and fingerprint!=expected_publisher:raise ValueError('Publisher identity changed')
        inv=p.get('files_sha256');expected=set(names)-{'publisher.json','signature.bin'}
        if not isinstance(inv,dict)or set(inv)!=expected:raise ValueError('Signed inventory mismatch')
        for n,h in inv.items():
            if n!='manifest.json'and(not n.startswith('web/')or n[4:]in engine_contract['files_sha256']):raise ValueError('Only game web content is permitted; engine is shared')
            if digest(z.read(n))!=h:raise ValueError('Content digest mismatch: '+n)
    return {'manifest':m,'package_sha256':digest(path.read_bytes()),'publisher_sha256':fingerprint,'signature_verified':True,'owner_approval_required':False}

def verify_engine(directory):
    """Check an unpacked host engine bundle before including it in an APK."""
    descriptor=json.loads((directory/'engine.json').read_text(encoding='utf-8'))
    inventory=descriptor.get('files_sha256')
    if descriptor.get('runtime')!='godot.web.v1' or not isinstance(inventory,dict) or not inventory:
        raise ValueError('Invalid host engine descriptor')
    for name, expected in inventory.items():
        if not safe(name) or '/' in name or not isinstance(expected,str) or not re.fullmatch(r'[0-9a-f]{64}',expected):
            raise ValueError('Invalid engine inventory entry')
        with (directory/name).open('rb') as source:
            actual=hashlib.file_digest(source,'sha256').hexdigest()
        if actual!=expected:
            raise ValueError('Engine file hash mismatch: '+name)
    return {'engine':descriptor['version'],'files_verified':len(inventory)}

def packed_resource_names(data):
    """Read the bounded directory of the unencrypted Godot 4.7 Web PCK."""
    import struct
    if len(data)<40 or data[:4]!=b'GDPC' or struct.unpack_from('<I',data,4)[0]!=4:
        raise ValueError('Unsupported PCK directory format')
    if struct.unpack_from('<I',data,20)[0]!=2:
        raise ValueError('Unsupported PCK directory flags')
    offset=struct.unpack_from('<Q',data,32)[0]
    if offset<40 or offset+4>len(data):
        raise ValueError('Invalid PCK directory offset')
    count=struct.unpack_from('<I',data,offset)[0]
    offset+=4
    if count>100000:
        raise ValueError('PCK directory exceeds diagnostic limit')
    names=set()
    for _ in range(count):
        if offset+4>len(data):
            raise ValueError('Truncated PCK directory')
        length=struct.unpack_from('<I',data,offset)[0]
        offset+=4
        if not 1<=length<=4096 or offset+length+36>len(data):
            raise ValueError('Invalid PCK resource entry')
        name=data[offset:offset+length].rstrip(b'\x00').decode('utf-8').removeprefix('res://')
        names.add(name)
        offset+=length+36
    return names

def diagnose(path, host_code=None, engine_contract=None, required=()):
    if path.stat().st_size>MAX_PACKAGE:
        raise ValueError('Package exceeds 64 MiB')
    with zipfile.ZipFile(path) as archive:
        mandatory={'manifest.json','publisher.json','signature.bin','web/index.html','web/index.pck'}
        missing=mandatory-set(archive.namelist())
        if missing:
            raise ValueError('Missing exported package entries: '+', '.join(sorted(missing)))
    result=verify(path,host_code=host_code,engine_contract=engine_contract)
    with zipfile.ZipFile(path) as archive:
        resources=packed_resource_names(archive.read('web/index.pck'))
        missing_resources=[]
        for resource in required:
            name=resource.removeprefix('res://')
            if not name or not any(candidate in resources for candidate in (name,name+'.remap',name+'.import')):
                missing_resources.append(resource)
        if missing_resources:
            raise ValueError('Missing exported resources: '+', '.join(missing_resources))
        expanded=sum(entry.file_size for entry in archive.infolist())
        largest=max(archive.infolist(),key=lambda entry:entry.file_size)
    return {'game':result['manifest']['id'],'engine':result['manifest']['engine'],
            'signature_verified':True,'package_bytes':path.stat().st_size,'package_limit':MAX_PACKAGE,
            'expanded_bytes':expanded,'expanded_limit':MAX_TOTAL,'largest_entry':largest.filename,
            'largest_entry_bytes':largest.file_size,'entry_limit':MAX_ENTRY,
            'pck_resources':len(resources),'required_resources':list(required)}

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--godot',default=os.environ.get('GODOT_BIN','godot'));s=p.add_subparsers(dest='command',required=True)
    n=s.add_parser('new');n.add_argument('path',type=Path);n.add_argument('--id',required=True);n.add_argument('--name',required=True);n.add_argument('--template',choices=TEMPLATES,default='touch2d');n.add_argument('--play-mode',choices=['single','turn_sms'],default='single');n.add_argument('--orientation',choices=['portrait','landscape','sensor'])
    n=s.add_parser('keygen');n.add_argument('path',type=Path)
    n=s.add_parser('upgrade');n.add_argument('path',type=Path)
    for cmd in ['check','run','preview','export','build']:
        n=s.add_parser(cmd);n.add_argument('path',type=Path)
        if cmd in ('build','export'):n.add_argument('--output',type=Path,required=True)
        if cmd=='build':n.add_argument('--key',type=Path,required=True)
    n=s.add_parser('verify');n.add_argument('package',type=Path);n.add_argument('--publisher');n.add_argument('--host-code',type=int,help='Check the minimum OnKlav build as well as package integrity')
    n.add_argument('--engine-contract',type=Path,help='Use a retained host engine.json for verification')
    n=s.add_parser('verify-engine');n.add_argument('directory',type=Path)
    n=s.add_parser('diagnose')
    n.add_argument('package',type=Path)
    n.add_argument('--host-code',type=int)
    n.add_argument('--engine-contract',type=Path)
    n.add_argument('--require-resource',action='append',default=[])

    a=p.parse_args()
    if a.command=='new':result=create(a.path.resolve(),a.id,a.name,a.template,a.play_mode,a.orientation)
    elif a.command=='keygen':result=keygen(a.path)
    elif a.command=='upgrade':result=upgrade(a.path.resolve())
    elif a.command=='verify':result=verify(a.package,a.publisher,a.host_code,json.loads(a.engine_contract.read_text()) if a.engine_contract else None)
    elif a.command=='diagnose':result=diagnose(a.package,a.host_code,json.loads(a.engine_contract.read_text()) if a.engine_contract else None,a.require_resource)
    elif a.command=='verify-engine':result=verify_engine(a.directory)
    elif a.command=='preview':result=preview(a.path.resolve(),a.godot)
    elif a.command=='export':result={'manifest':export_web(a.path.resolve(),a.output.resolve(),a.godot),'export':str(a.output.resolve())}
    elif a.command=='check':result={'manifest':check(a.path.resolve(),a.godot),'checked':True}
    elif a.command=='run':engine(a.godot);subprocess.run([a.godot,'--path',str(a.path.resolve())],check=True);result={'closed':True}
    else:result=build(a.path.resolve(),a.output.resolve(),a.key.resolve(),a.godot)
    print(json.dumps(result,indent=2))
if __name__=='__main__':
    try:main()
    except Exception as e:print('Game SDK: '+str(e),file=sys.stderr);sys.exit(1)
