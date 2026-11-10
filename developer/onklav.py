#!/usr/bin/env python3
"""OnKlav app tools: Python3.11+ and Rust1.90+. Optional native PNG preview also needs the host Android toolchain."""
import argparse
import hashlib
import contextlib
import math
import uuid
import time
import struct
import zlib
import queue
import threading
import json
import os
from pathlib import Path
import re
import shutil
import signal
import subprocess
import sys
import tempfile
import tomllib

ROOT = Path(__file__).resolve().parents[1]
HERE = ROOT / 'developer'
CONTRACT = json.loads((HERE/'contract.json').read_text(encoding='utf-8'))
HOST_BUILD = CONTRACT['reference_host_build']
FLAGS = '-C link-arg=-zstack-size=524288 -C link-arg=--max-memory=8388608'
CAPS = frozenset(CONTRACT['capabilities'])

def execute(args, *, env=None, capture=False, timeout=240):
    command=[str(a) for a in args]
    try:
        p=subprocess.Popen(command,text=True,env=env,stdout=subprocess.PIPE if capture else None,encoding='utf-8',errors='replace',start_new_session=os.name!='nt')
        try:output,_=p.communicate(timeout=timeout)
        except subprocess.TimeoutExpired as e:
            if os.name!='nt':
                try:os.killpg(p.pid,signal.SIGKILL)
                except ProcessLookupError:pass
            else:
                try:subprocess.run(['taskkill','/PID',str(p.pid),'/T','/F'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,timeout=10)
                except (OSError,subprocess.TimeoutExpired):pass
                if p.poll() is None:p.kill()
            p.communicate()
            raise ValueError(f'Command exceeded its {timeout}-second limit; process stopped. Inspect its output before retrying.') from e
        if p.returncode:raise subprocess.CalledProcessError(p.returncode,command,output=output)
        return output
    except FileNotFoundError as e:
        raise ValueError(f'{args[0]} is unavailable. Run doctor for prerequisites.') from e

def host_env():
    env=os.environ.copy();env['CARGO_TARGET_DIR']=str(HERE/'target/host');return env

def validate_identity(app_id, name):
    if not isinstance(app_id,str) or not isinstance(name,str):raise ValueError('App ID and name must be strings.')
    if app_id.startswith('app.onklav.'):
        raise ValueError('The app.onklav namespace is reserved for included platform apps.')
    if len(app_id)>128 or not re.fullmatch(r'[a-z][a-z0-9]*(?:\.[a-z][a-z0-9]*)+', app_id):
        raise ValueError('Use a reverse-domain ID with lowercase letters/digits; each segment starts with a letter.')
    if not name.strip() or len(name.encode('utf-16-le'))//2>60 or any(ord(c)<32 or ord(c)==127 for c in name):
        raise ValueError('App name must be 1–60 UTF-16 units without control characters.')

def validate_network(policy, declared):
    if policy is None and not declared:return
    if not declared or not isinstance(policy,dict) or not {'origins'}.issubset(policy) or set(policy)-{'origins','accounts'}:
        raise ValueError('network.http requires a network policy with exact HTTPS origins.')
    origins=policy['origins']
    if not isinstance(origins,list) or not 1<=len(origins)<=8 or any(not isinstance(v,str) for v in origins) or len(set(origins))!=len(origins):
        raise ValueError('Declare1–8 unique HTTPS origins.')
    for origin in origins:
        host=origin[8:] if origin.startswith('https://') else ''
        labels=host.split('.')
        if len(host)>253 or len(labels)<2 or host.endswith(('.local','.localhost')) or all(c.isdigit() or c=='.' for c in host) or any(not re.fullmatch('[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?',label) for label in labels):
            raise ValueError('Use canonical HTTPS DNS origins, without paths, ports, credentials or wildcards.')


    accounts=policy.get('accounts',[])
    if not isinstance(accounts,list) or len(accounts)>4:raise ValueError('Declare up to four account providers.')
    ids=set()
    for a in accounts:
        required={'id','label','authorization_endpoint','token_endpoint','client_id','scopes','api_origins'}
        if not isinstance(a,dict) or set(a)-{'revocation_endpoint'}!=required:raise ValueError('Use only documented public account-provider fields; no client secrets.')
        if not isinstance(a['id'],str) or not re.fullmatch('[a-z][a-z0-9-]{0,47}',a['id']) or a['id']in ids:raise ValueError('Invalid or duplicate account provider ID.')
        ids.add(a['id'])
        label=a['label'];client=a['client_id']
        if not isinstance(label,str) or not label.strip() or len(label.encode('utf-16-le'))//2>60 or any(ord(c)<32 or 127<=ord(c)<=159 for c in label):raise ValueError('Invalid account label.')
        if not isinstance(client,str) or not 1<=len(client)<=256 or any(not 33<=ord(c)<=126 for c in client):raise ValueError('Invalid public client ID.')
        for key in ['authorization_endpoint','token_endpoint']+(['revocation_endpoint'] if 'revocation_endpoint' in a else []):
            url=a[key]
            if not isinstance(url,str) or len(url)>4096 or not re.fullmatch(r'https://[a-z0-9.-]+/[A-Za-z0-9/._~%\-]*',url) or url.split('/',3)[0]+'//'+url.split('/',3)[2] not in origins:raise ValueError('Account endpoints need declared canonical HTTPS origins and fixed paths.')
        scopes=a['scopes']
        if not isinstance(scopes,list) or not 1<=len(scopes)<=32 or any(not isinstance(v,str) or not 1<=len(v)<=128 or any(not(ord(c)==33 or 35<=ord(c)<=91 or 93<=ord(c)<=126)for c in v)for v in scopes) or len(set(scopes))!=len(scopes):raise ValueError('Declare unique bounded account scopes.')
        apis=a['api_origins']
        if not isinstance(apis,list) or not 1<=len(apis)<=8 or any(not isinstance(o,str) or o not in origins for o in apis) or len(set(apis))!=len(apis):raise ValueError('Account API origins must be unique signed network origins.')

def manifest(project):
    path=project/'manifest.json';m=json.loads(path.read_text(encoding='utf-8'))
    required={'schema','runtime','id','name','version','capabilities'}
    if not isinstance(m,dict) or not required.issubset(m) or set(m)-required-{'min_host','icon','network'}:raise ValueError('Author manifest requires '+', '.join(sorted(required))+'; optional fields: min_host, icon, network.')
    if 'min_host' in m and (type(m['min_host']) is not int or not 1<=m['min_host']<=HOST_BUILD):raise ValueError(f'Minimum host must be a positive build number no greater than {HOST_BUILD}.')
    if 'icon' in m:
        icon=m['icon']
        if not isinstance(icon,dict) or set(icon) not in ({'symbol'},{'png_hex'}):raise ValueError('Choose exactly one icon symbol or PNG.')
        if 'symbol' in icon:
            if icon['symbol'] not in desktop_symbols():raise ValueError('Unknown desktop icon symbol. See contract.json.')
        else:
            data=icon['png_hex']
            if not isinstance(data,str) or not 66<=len(data)<=12288 or len(data)%2 or not re.fullmatch('[0-9a-f]+',data):raise ValueError('Invalid bounded lowercase PNG hex.')
            validate_png(bytes.fromhex(data))
    validate_identity(m['id'],m['name'])
    if m['schema']!='onklav.app.v1' or m['runtime']!='rust-wasm-ui.v1':raise ValueError('Unsupported package schema/runtime.')
    if type(m['version']) is not int or not 1<=m['version']<=2147483647:raise ValueError('Version must be an integer from1 through2147483647.')
    caps=m['capabilities']
    if not isinstance(caps,list) or any(not isinstance(c,str) or c not in CAPS for c in caps) or len(set(caps))!=len(caps):raise ValueError('Unknown or duplicate capability. See the API reference.')
    validate_network(m.get('network'),'network.http' in caps)
    if 'accounts.connect' in caps and ('network.http' not in caps or not m.get('network',{}).get('accounts')):raise ValueError('accounts.connect requires network.http and signed providers.')
    return m

def desktop_symbols():return json.loads((HERE/'contract.json').read_text())['desktop_symbols']

def validate_png(data):
    if not 33<=len(data)<=6144 or data[:8]!=b'\x89PNG\r\n\x1a\n':raise ValueError('Use a PNG icon no larger than6144bytes.')
    offset=8;idat=bytearray();width=height=channels=0;ended=False;chunks=0
    while offset+12<=len(data):
        chunks+=1
        if chunks>64:raise ValueError('Too many PNG chunks.')
        n=struct.unpack('>I',data[offset:offset+4])[0];kind=data[offset+4:offset+8];end=offset+12+n
        if end>len(data):raise ValueError('Incomplete PNG chunk.')
        chunk=data[offset+8:offset+8+n];crc=struct.unpack('>I',data[offset+8+n:end])[0]
        if zlib.crc32(kind+chunk)!=crc:raise ValueError('PNG checksum failed.')
        if kind==b'IHDR':
            if offset!=8 or n!=13:raise ValueError('Invalid PNG header.')
            width,height,depth,color,compression,filtering,interlace=struct.unpack('>IIBBBBB',chunk)
            if not 1<=width<=128 or not 1<=height<=128 or depth!=8 or color not in (2,6) or compression or filtering or interlace:raise ValueError('Use a noninterlaced8-bit RGB/RGBA PNG, at most128×128.')
            channels=3 if color==2 else 4
        elif kind==b'IDAT':idat.extend(chunk)
        elif kind==b'IEND':
            if n or end!=len(data):raise ValueError('Invalid PNG end.')
            ended=True
        elif kind in (b'sRGB',b'gAMA',b'cHRM',b'pHYs'):
            if idat or n!={b'sRGB':1,b'gAMA':4,b'cHRM':32,b'pHYs':9}[kind]:raise ValueError('Invalid ancillary PNG chunk.')
        else:raise ValueError('Export PNG without animation, text or compressed color profiles.')
        offset=end
    if not ended or not width or not idat:raise ValueError('Incomplete PNG icon.')
    expected=height*(1+width*channels);decoder=zlib.decompressobj()
    try:pixels=decoder.decompress(idat,expected+1)
    except zlib.error as e:raise ValueError('Invalid PNG pixels.') from e
    if len(pixels)!=expected or not decoder.eof or decoder.unused_data or decoder.unconsumed_tail or any(pixels[y*(1+width*channels)]>4 for y in range(height)):raise ValueError('PNG pixels exceed or disagree with the header.')
    return data

def desktop_icon(value):
    if value in desktop_symbols():return {'symbol':value}
    with Path(value).open('rb') as src:data=src.read(6145)
    return {'png_hex':validate_png(data).hex()}

TEMPLATES=('document','list','form','settings','files','viewer','notes','rss','reader','browser','youtube','annotation','social','walkie')

def vendor_sdk(destination):
    for source,relative in [(ROOT/'sdk/rust',Path('vendor/sdk/rust')),(HERE/'components',Path('vendor/developer/components'))]:
        for p in source.rglob('*'):
            path=p.relative_to(source)
            if p.is_file() and not p.is_symlink() and not {'target','.git','__pycache__'}.intersection(path.parts) and p.suffix in ('.rs','.toml','.lock','.md'):
                out=destination/relative/path;out.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,out)

def new_project(destination, app_id, name, template_name='document', portable=True, icon=None, capabilities=(), features=(), network_origins=()):
    validate_identity(app_id,name)
    if destination.exists():raise ValueError('Destination already exists; choose a new folder.')
    if template_name not in TEMPLATES:raise ValueError('Choose a listed app template.')
    if any(cap not in CAPS for cap in capabilities):raise ValueError('Unknown capability. See contract.json capabilities.')
    if any(feature not in ('file-viewer',) for feature in features):raise ValueError('Unknown feature. Available: file-viewer.')
    if network_origins:capabilities=tuple(set(capabilities)|{'network.http'})
    validate_network({'origins':list(network_origins)} if network_origins else None,'network.http' in capabilities)
    chosen_icon=desktop_icon(icon or {'files':'files','viewer':'files','settings':'settings','notes':'notes','rss':'rss','reader':'reader','browser':'browser','youtube':'youtube','annotation':'annotation','social':'social','walkie':'walkie'}.get(template_name,'notes'))
    shutil.copytree(HERE/'templates/basic',destination)
    cargo=(destination/'Cargo.toml').read_text(encoding='utf-8').replace('"__SDK_RUST_PATH__"',json.dumps(str(ROOT/'sdk/rust'))).replace('"__UI_COMPONENTS_PATH__"',json.dumps(str(HERE/'components')))
    (destination/'Cargo.toml').write_text(cargo,encoding='utf-8')
    if portable:
        vendor_sdk(destination)
        cargo=cargo.replace(json.dumps(str(ROOT/'sdk/rust')),'"vendor/sdk/rust"').replace(json.dumps(str(HERE/'components')),'"vendor/developer/components"')
        (destination/'Cargo.toml').write_text(cargo,encoding='utf-8')
    title=json.dumps(name,ensure_ascii=False)
    controller='onklav_ui::collection::documents' if template_name=='list' else 'onklav_ui::apps::'+template_name if template_name in ('document','settings') else 'onklav_ui::files::manager' if template_name=='files' else 'onklav_ui::reference_apps::'+template_name+'::controller'
    src='use onklav_ui::serde_json::Value;\nfn step(input:Value)->Value{'+controller+'('+title+',input)}\nonklav_ui::app!(step);\n'
    if template_name=='form':src='use onklav_ui::{forms::{form,FormField},serde_json::Value};\nfn step(input:Value)->Value{form('+title+',input,&[FormField::text("name","Name").required(),FormField::multiline("details","Details"),FormField::toggle("active","Active",true),FormField::choice("priority","Priority",&[("normal","Normal"),("high","High")])])}\nonklav_ui::app!(step);\n'
    if template_name=='form':
        schema={'schema':'onklav.form.v1','fields':[{'id':'name','label':'Name','required':True},{'id':'details','label':'Details','type':'multiline'},{'id':'active','label':'Active','type':'toggle','default':True},{'id':'priority','label':'Priority','type':'choice','options':['normal','high']}]}
        (destination/'form.json').write_text(json.dumps(schema,indent=2)+'\n',encoding='utf-8')
        with (destination/'Cargo.toml').open('a',encoding='utf-8') as out:out.write('\n[package.metadata.onklav]\nform_schema = "form.json"\n')
        src='use onklav_ui::{form_schema::configured_form,serde_json::Value};\nfn step(input:Value)->Value{configured_form('+title+',input,include_str!("../form.json"))}\nonklav_ui::app!(step);\n'
    if template_name=='viewer':src='use onklav_ui::{Screen,label,serde_json::Value};\nfn app(input:Value)->Value{Screen::new('+title+',input["state"].clone()).body(vec![label("hint","Choose Open Files to view images, videos or audio.")]).finish()}\nfn step(input:Value)->Value{onklav_ui::files::with_viewer('+title+',input,app)}\nonklav_ui::app!(step);\n'
    if 'file-viewer' in features and template_name!='viewer':
        src=src.replace('fn step(', 'fn template_step(').replace('onklav_ui::app!(step);','fn step(input:Value)->Value{onklav_ui::files::with_viewer('+title+',input,template_step)}\nonklav_ui::app!(step);')
    (destination/'src/lib.rs').write_text(src,encoding='utf-8')
    caps=['storage.private']
    if template_name=='viewer':caps=['files.manage']
    elif template_name not in ('document','list','form','settings'):caps=json.loads((ROOT/'sdk/apps'/template_name/'manifest.json').read_text())['capabilities']
    required=set(caps)
    if 'file-viewer' in features:required.add('files.manage')
    caps=sorted(required.union(capabilities))
    metadata='required_capabilities = '+json.dumps(sorted(required))+'\n'
    with (destination/'Cargo.toml').open('a',encoding='utf-8') as out:
        out.write(('' if template_name=='form' else '\n[package.metadata.onklav]\n')+metadata)
    (destination/'manifest.json').write_text(json.dumps({'schema':'onklav.app.v1','runtime':'rust-wasm-ui.v1','id':app_id,'name':name,'version':1,'capabilities':caps,'min_host':HOST_BUILD,'icon':chosen_icon,**({'network':{'origins':list(network_origins)}} if network_origins else {})},indent=2)+'\n',encoding='utf-8')
    scenario={'grants':['storage.private'],'steps':[{'action':'title','value':'My first document'},{'action':'body','value':'Hello from an independent OnKlav app.'},{'action':'save','expect':{'doc.title':'My first document','status':'Saved'}},{'reopen':True,'expect':{'doc.text':'Hello from an independent OnKlav app.'}}]}
    if template_name=='settings':scenario={'grants':['storage.private'],'steps':[{'action':'show_details','value':'false'},{'action':'order','value':'oldest'},{'action':'save','expect':{'status':'Saved'}},{'reopen':True,'expect':{'preferences.show_details':False,'preferences.order':'oldest'}}]}
    elif template_name=='form':scenario={'grants':['storage.private'],'steps':[{'action':'form.field.name','value':'My item'},{'action':'form.field.active','value':'false'},{'action':'form.save','expect':{'status':'Saved'}},{'reopen':True,'expect':{'values.name':'My item','values.active':False}}]}
    elif template_name=='list':scenario={'grants':['storage.private'],'steps':[{'action':'new'},{'action':'title','value':'My item'},{'action':'body','value':'Item details'},{'action':'save','expect':{'status':'Saved'}},{'reopen':True,'expect':{'route':'list','total':1}}]}
    elif template_name=='files':scenario={'grants':['files.manage'],'steps':[{'action':'refresh','expect':{'folder':''}}]}
    elif template_name=='viewer':scenario={'grants':['files.manage'],'steps':[{'action':'sdk.files.open','expect':{'picker.folder':''}},{'action':'sdk.files.cancel','expect':{'picker':None}}]}
    elif template_name not in ('document','form','list'):scenario={'grants':[],'steps':[]}
    if 'file-viewer' in features:
        scenario['grants']=sorted(set(scenario['grants'])|{'files.manage'})
        if template_name!='viewer':
            for step in scenario['steps']:
                if 'expect' in step:step['expect']={'app.'+path:value for path,value in step['expect'].items()}
        scenario['steps'] += [{'action':'sdk.files.open','expect':{'picker.folder':''}},{'action':'sdk.files.cancel','expect':{'picker':None}}]
    (destination/'scenario.json').write_text(json.dumps(scenario,indent=2)+'\n',encoding='utf-8')
    (destination/'.gitignore').write_text('/target/\n/dist/\n*.key\n',encoding='utf-8')
    (destination/'README.md').write_text('Generated OnKlav app. Read developer/docs/COMPONENTS.md in the SDK.\nKeep publisher keys outside the project. Vendored dependencies move with this project. Use --linked-sdk only when intentionally developing against a live SDK tree.\n',encoding='utf-8')
    return {'project':str(destination),'id':app_id,'template':template_name,'portable_dependencies':portable,'next':'build, inspect, test, then install inside OnKlav'}

def packer(*args):
    output=execute(['cargo','run','--quiet','--locked','--manifest-path',ROOT/'core/Cargo.toml','--bin','onklav-pack','--',*args],env=host_env(),capture=True)
    if output:print(output,end='',file=sys.stderr)

def runtime_inputs():
    paths=[ROOT/'core/Cargo.toml',ROOT/'core/Cargo.lock',HERE/'host/Cargo.toml',HERE/'host/Cargo.lock',*(ROOT/'core/src').rglob('*.rs'),*(HERE/'host/src').rglob('*.rs'),HERE/'components/Cargo.toml',HERE/'components/Cargo.lock',*(HERE/'components/src').rglob('*.rs'),ROOT/'sdk/rust/Cargo.toml',*(ROOT/'sdk/rust/src').rglob('*.rs')]
    return sorted(set(paths))

def runtime_fingerprint():
    h=hashlib.sha256()
    for p in runtime_inputs():h.update(str(p.relative_to(ROOT)).encode());h.update(p.read_bytes())
    return h.hexdigest()

def refresh_runtime_inputs(ledger, inputs):
    # Restored sources can have older mtimes than compiled output. Refresh only
    # changed inputs so Cargo notices their content without deleting its cache.
    try:previous=json.loads(ledger.read_text(encoding='utf-8'))
    except (OSError,ValueError):previous={}
    if not isinstance(previous,dict):previous={}
    current={str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in inputs}
    for p in inputs:
        if previous.get(str(p.relative_to(ROOT)))!=current[str(p.relative_to(ROOT))]:p.touch()
    return current

def build_host():
    stamp=HERE/'target/host/onklav-runtime-source.txt';fingerprint=runtime_fingerprint()
    ledger=stamp.with_name('onklav-runtime-inputs.json')
    env=host_env();env['ONKLAV_RUNTIME_SOURCE']=fingerprint
    inputs=refresh_runtime_inputs(ledger,runtime_inputs())
    execute(['cargo','build','--quiet','--locked','--manifest-path',HERE/'host/Cargo.toml'],env=env)
    if runtime_fingerprint()!=fingerprint:raise ValueError('SDK sources changed during the host build. Run the command again after the edit finishes.')
    binary=HERE/('target/host/debug/onklav-sdk-host.exe' if os.name=='nt' else 'target/host/debug/onklav-sdk-host')
    with tempfile.TemporaryDirectory(prefix='onklav-host-version-') as root,Runtime(root,binary) as runtime:
        info=runtime.request({'action':'host_info'})
        if info.get('build')!=HOST_BUILD or info.get('source')!=fingerprint:raise ValueError('Linked desktop runtime does not match current host sources. Rebuild the local host cache.')
    stamp.write_text(fingerprint)
    ledger.write_text(json.dumps(inputs,sort_keys=True)+'\n',encoding='utf-8')
    return binary

class Runtime:
    def __init__(self, root, binary=None, request_timeout=30):
        self.request_timeout=request_timeout
        self.responses=queue.Queue(maxsize=32)
        self.process=subprocess.Popen([str(binary or build_host()),str(root)],stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True,encoding='utf-8')
        self.reader=threading.Thread(target=self._read_responses,daemon=True)
        self.reader.start()
    def _read_responses(self):
        try:
            for line in self.process.stdout:
                self.responses.put_nowait(line)
        except (OSError,ValueError,queue.Full):pass
        finally:
            try:self.responses.put_nowait(None)
            except queue.Full:pass
    def request(self, request, *, error=False):
        p=self.process
        payload=json.dumps(request,ensure_ascii=False)+'\n'
        def send():
            try:p.stdin.write(payload);p.stdin.flush()
            except (BrokenPipeError,OSError,ValueError):
                try:self.responses.put_nowait(None)
                except queue.Full:pass
        sender=threading.Thread(target=send,daemon=True);sender.start()
        try:line=self.responses.get(timeout=self.request_timeout)
        except queue.Empty:
            p.kill();p.wait(timeout=5)
            raise ValueError('Headless runtime response timed out; process stopped. Inspect the failed action before retrying.')
        if not line:raise ValueError('Headless runtime stopped unexpectedly.')
        result=json.loads(line)
        if error:
            if result.get('ok'):raise AssertionError('Request unexpectedly succeeded: '+str(request.get('action')))
            return result['error']
        if not result.get('ok'):raise ValueError(result.get('error','Runtime request failed'))
        return result['value']
    def close(self):
        if self.process.poll() is None:
            self.process.stdin.close()
            try:self.process.wait(timeout=10)
            except subprocess.TimeoutExpired:self.process.kill();self.process.wait()
        self.reader.join(timeout=2)
        for stream in (self.process.stdin,self.process.stdout):
            if stream is not None and not stream.closed:stream.close()
    def __enter__(self):return self
    def __exit__(self,*_):self.close()

def inspect_package(path, binary=None):
    with tempfile.TemporaryDirectory(prefix='onklav-inspect-') as root,Runtime(root,binary) as runtime:
        return runtime.request({'action':'stage','path':str(path.resolve())})

def project_schema(project):
    cargo=project/'Cargo.toml'
    try:config=tomllib.loads(cargo.read_text(encoding='utf-8'))
    except tomllib.TOMLDecodeError as e:raise ValueError(f'{cargo}: {e}') from e
    package=config.get('package',{});metadata=package.get('metadata',{}) if isinstance(package,dict) else {}
    settings=metadata.get('onklav',{}) if isinstance(metadata,dict) else {}
    if not isinstance(settings,dict):raise ValueError('package.metadata.onklav must be a table.')
    reference=settings.get('form_schema')
    if reference is None:return None
    if not isinstance(reference,str) or not reference or Path(reference).is_absolute():raise ValueError('form_schema must be a relative project file.')
    path=(project/reference).resolve()
    if not path.is_relative_to(project.resolve()):raise ValueError('form_schema must stay inside the app project.')
    try:
        with path.open('rb') as stream:raw=stream.read(65537)
    except OSError as e:raise ValueError(f'{reference}: cannot read the declared form schema: {e.strerror}') from e
    if len(raw)>65536:raise ValueError(f'{reference}: form schema exceeds64KB.')
    try:value=json.loads(raw.decode('utf-8'))
    except UnicodeDecodeError as e:raise ValueError(f'{reference}: use UTF-8 JSON.') from e
    except json.JSONDecodeError as e:raise ValueError(f'{reference}:{e.lineno}:{e.colno}: {e.msg}') from e
    return reference,value

def validate_project(project,binary=None):
    project=project.resolve();author=manifest(project);schema=project_schema(project);summary=None
    config=tomllib.loads((project/'Cargo.toml').read_text(encoding='utf-8'))
    required=config.get('package',{}).get('metadata',{}).get('onklav',{}).get('required_capabilities',[])
    if not isinstance(required,list) or any(not isinstance(cap,str) or cap not in CAPS for cap in required):raise ValueError('package.metadata.onklav.required_capabilities must list known capabilities.')
    missing=sorted(set(required)-set(author['capabilities']))
    if missing:raise ValueError('Add '+', '.join(missing)+' to manifest.json capabilities for the selected app features; users must still grant access.')
    if schema is not None:
        reference,value=schema
        dependency=config.get('dependencies',{}).get('onklav-ui',{})
        relative=dependency.get('path') if isinstance(dependency,dict) else None
        if not isinstance(relative,str):raise ValueError('Form schema validation needs the matching SDK source dependency. Use a generated project or its matching SDK tools.')
        gui=(project/relative).resolve()
        for filename in ('form_schema.rs','forms.rs','form_values.rs'):
            actual=gui/'src'/filename;expected=HERE/'components/src'/filename
            if not actual.is_file() or actual.read_bytes()!=expected.read_bytes():raise ValueError('The project form library differs from this validator. Run upgrade PROJECT or use its matching SDK tools; custom SDK forks need their matching validator.')
        if 'storage.private' not in author['capabilities']:raise ValueError(f'{reference}: this form saves app-private data. Add storage.private to manifest.json capabilities; users must still grant access.')
        with tempfile.TemporaryDirectory(prefix='onklav-validate-') as root,Runtime(root,binary) as runtime:
            try:summary=runtime.request({'action':'validate_form_schema','schema':value})
            except ValueError as e:raise ValueError(f'{reference}: {e}') from e
        summary=dict(summary,file=reference)
    return {'valid':True,'id':author['id'],'capabilities':author['capabilities'],'required_capabilities':sorted(set(required)),'form_schema':summary,'phone_permissions_checked':False}

def build_project(project, key, output):
    m=manifest(project)
    output=output or project/'dist'/(m['id']+'-v'+str(m['version'])+'.onklav-a')
    if output.suffix!='.onklav-a':raise ValueError('Output filename must end in .onklav-a.')
    if output.exists():raise ValueError('Output already exists. Choose a new output filename; existing releases are never overwritten.')
    key=key.resolve()
    if key.is_relative_to(project) or key.is_relative_to(ROOT):raise ValueError('Keep the private publisher key outside the SDK and app project.')
    if not key.is_file() or key.stat().st_size!=32:raise ValueError('Expected an existing 32-byte publisher key. Use keygen once for a new author.')
    wasm=compile_project(project)
    output=output or project/'dist'/(m['id']+'-v'+str(m['version'])+'.onklav-a')
    if output.suffix!='.onklav-a':raise ValueError('Output filename must end in .onklav-a.')
    if output.exists():raise ValueError('Output already exists. Choose a new output filename; existing releases are never overwritten.')
    output.parent.mkdir(parents=True,exist_ok=True)
    packer('pack',project/'manifest.json',wasm,key,output)
    try:
        result=check_package(output)
        review=result.pop("review")
    except Exception:
        output.unlink(missing_ok=True);raise
    return {'package':str(output),'review':review}

def compile_project(project):
    validate_project(project)
    env=os.environ.copy();env['RUSTFLAGS']=FLAGS;env['CARGO_TARGET_DIR']=str(project/'target')
    meta=json.loads(execute(['cargo','metadata','--locked','--no-deps','--format-version','1','--manifest-path',project/'Cargo.toml'],env=env,capture=True))
    package=next(p for p in meta['packages'] if Path(p['manifest_path']).resolve()==project/'Cargo.toml')
    libraries=[t for t in package['targets'] if 'cdylib' in t.get('crate_types',[])]
    if len(libraries)!=1:raise ValueError('The project must expose one cdylib app target.')
    execute(['cargo','build','--locked','--release','--target','wasm32-unknown-unknown','--manifest-path',project/'Cargo.toml'],env=env)
    wasm=project/'target/wasm32-unknown-unknown/release'/(libraries[0]['name'].replace('-','_')+'.wasm')
    return wasm

def build_run_update(project,key,scratch,sequence,app_id=None,previous_digest=None):
    author=manifest(project)
    if app_id is not None and author['id']!=app_id:raise ValueError('App identity changed. Start a new local run for a different app ID.')
    version=sequence+1
    if version>2147483647:raise ValueError('Local test version limit reached. Start a new run; source versions were not changed.')
    wasm=compile_project(project)
    if manifest(project)!=author:raise ValueError('Manifest changed during compilation. Finish the edit before reloading.')
    digest=hashlib.sha256(json.dumps(author,sort_keys=True).encode()+wasm.read_bytes()).hexdigest()
    if digest==previous_digest:return None
    local=dict(author,version=version)
    attempt=Path(tempfile.mkdtemp(prefix='update-'+str(version)+'-',dir=scratch))
    local_manifest=attempt/'manifest.json';local_manifest.write_text(json.dumps(local),encoding='utf-8')
    package=attempt/'run.onklav-a'
    packer('pack',local_manifest,wasm,key,package)
    return {'package':package,'version':version,'digest':digest,'manifest':author}

def check_package(path):
    with tempfile.TemporaryDirectory(prefix='onklav-check-') as root,Runtime(root) as runtime:
        review=runtime.request({'action':'stage','path':str(path.resolve())})
        runtime.request({'action':'install','hash':review['hash'],'grants':[]})
        runtime.request({'action':'launch','id':review['manifest']['id']})
        return {'review':review,'signature_verified':True,'launch_without_grants':'passed','reference_host':CONTRACT['reference_host'],'phone_acceptance_required':True,'minimum_host':review['manifest'].get('min_host'),'icon':review['manifest'].get('icon',{'symbol':'apps'})}

def event_value(value):
    if value is None:return ''
    if isinstance(value,str):return value
    if isinstance(value,bool):return 'true' if value else 'false'
    if isinstance(value,int) or isinstance(value,float) and math.isfinite(value):return str(value)
    raise ValueError('Event values must be text, a boolean, or a finite number.')

def scenario_spec(project,scenario=None):
    path=scenario or project/'scenario.json'
    try:spec=json.loads(path.read_text(encoding='utf-8'))
    except json.JSONDecodeError as e:raise ValueError(f'{path}:{e.lineno}:{e.colno}: {e.msg}') from e
    if not isinstance(spec,dict) or not isinstance(spec.get('steps'),list):raise ValueError('Scenario requires a steps array.')
    if len(spec['steps'])>1000:raise ValueError('Scenario exceeds1000 steps; split it into focused checks.')
    if not isinstance(spec.get('grants',[]),list) or any(not isinstance(v,str) or v not in CAPS for v in spec.get('grants',[])):raise ValueError('Scenario grants must be known capability names.')
    for n,step in enumerate(spec['steps'],1):
        if not isinstance(step,dict):raise ValueError(f'Scenario step {n}: expected an object.')
        if step.get('reopen') is not True and (not isinstance(step.get('action'),str) or not step['action']):raise ValueError(f'Scenario step {n}: specify action or reopen:true.')
        if step.get('reopen') is True and 'action' in step:raise ValueError(f'Scenario step {n}: choose action or reopen, not both.')
        for name in ('expect','frame_expect'):
            if name in step and (not isinstance(step[name],dict) or any(not isinstance(k,str) or not k for k in step[name])):raise ValueError(f'Scenario step {n}: {name} must map nonempty paths to expected values.')
        event_value(step.get('value',''))
    return spec

class ScenarioFailure(AssertionError):
    def __init__(self,step,action,cause,diagnostic=None,hint=''):
        self.step=step;self.action=action;self.diagnostic=diagnostic
        super().__init__(f'Scenario step {step} ({action}): {cause}'+(' '+hint if hint else '')+(f' Local diagnostic: {diagnostic}' if diagnostic else ''))

def sensitive_project(project):
    try:return bool({'storage.secrets','accounts.connect'} & set(manifest(project)['capabilities']))
    except (OSError,ValueError,TypeError):return True

def failure_diagnostic(project,step,action,error,current,events):
    pending=(current or {}).get('pending') or {};frame=(current or {}).get('frame')
    root=project/'target/onklav-diagnostics';root.mkdir(parents=True,exist_ok=True,mode=0o700)
    fd,path=tempfile.mkstemp(prefix='scenario-failure-',suffix='.json',dir=root)
    data={'step':step,'action':action,'error':str(error),'last_frame':frame,'pending_service':pending.get('service'),'events':events[-20:],'native_ui_tested':False}
    if sensitive_project(project):data.update(action='redacted',error='Sensitive app failure; payload omitted.',last_frame=None,events=[],sensitive_payload_redacted=True)
    # Local debug data may contain the developer's test values. No session tokens,
    # private signer data or automatic external upload is included.
    with os.fdopen(fd,'w',encoding='utf-8') as out:json.dump(data,out,ensure_ascii=False,indent=2);out.write('\n')
    return path

def test_project(project, package, scenario=None):
    spec=scenario_spec(project,scenario);current=None;events=[];number=0;action='install'
    try:
        with tempfile.TemporaryDirectory(prefix='onklav-test-') as root,Runtime(root) as runtime:
            review=runtime.request({'action':'stage','path':str(package)})
            runtime.request({'action':'install','hash':review['hash'],'grants':spec.get('grants',[])})
            app_id=review['manifest']['id'];action='launch';current=runtime.request({'action':'launch','id':app_id});session=current['session']
            for number,step in enumerate(spec['steps'],1):
                action='reopen' if step.get('reopen') else step['action'];events.append({'step':number,'action':action,'value':step.get('value','')})
                if step.get('reopen'):
                    runtime.request({'action':'close','session':session});current=runtime.request({'action':'launch','id':app_id});session=current['session']
                else:current=runtime.request({'action':'event','session':session,'event':{'action':action,'value':event_value(step.get('value',''))}})
                for path,expected in step.get('expect',{}).items():
                    actual=path_value(current['frame']['state'],path)
                    if actual!=expected:raise AssertionError(f'{path}: expected {expected!r}, received {actual!r}')
                for path,expected in step.get('frame_expect',{}).items():
                    actual=path_value(current['frame'],path)
                    if actual!=expected:raise AssertionError(f'frame.{path}: expected {expected!r}, received {actual!r}')
                if 'pending' in step and (current.get('pending') or {}).get('service')!=step['pending']:raise AssertionError('Unexpected pending Android service')
            return {'passed':len(spec['steps']),'runtime':'production Rust app broker','android_ui_tested':False}
    except (ValueError,AssertionError,OSError) as error:
        pending=(current or {}).get('pending') or {};status=((current or {}).get('frame') or {}).get('state',{})
        hint=''
        if pending.get('service'):hint=f'Pending {pending["service"]} needs Android; desktop tests do not fake native replies.'
        elif 'access is off' in str(status).lower() or 'permission' in str(error).lower():hint='Check manifest capabilities and local scenario grants; device grants are separate.'
        try:diagnostic=failure_diagnostic(project,number,action,error,current,events)
        except OSError:diagnostic=None
        if sensitive_project(project):raise ScenarioFailure(number,'redacted','Sensitive app check failed; payload omitted.',diagnostic,hint) from None
        raise ScenarioFailure(number,action,error,diagnostic,hint) from error


def path_value(value,path):
    try:
        for part in path.split('.'):value=value[int(part)] if isinstance(value,list) else value[part]
        return value
    except (KeyError,IndexError,ValueError,TypeError) as e:raise AssertionError('Missing scenario path: '+path) from e

def verify_project(project,scenario=None):
    validate_project(project)
    with tempfile.TemporaryDirectory(prefix='onklav-verify-') as root:
        scratch=Path(root);key=scratch/'disposable-author.key';packer('keygen',key)
        built=build_project(project,key,scratch/'check.onklav-a')
        result=test_project(project,Path(built['package']),scenario)
        return {'project':str(project),'signature_install_startup':'passed','scenario':result,'signer':'disposable local test author; key and package discarded','shipping_package_created':False,'native_phone_acceptance_required':True}

def frame_summary(current):
    frame=current['frame']
    def nodes(key):
        return [{k:(v[:240]+'…' if isinstance(v,str) and len(v)>240 else v) for k,v in n.items() if k in ('kind','id','text','value','checked','enabled','hint')} for n in frame.get(key,[])][:100]
    summary={'title':frame.get('title'),'actions':nodes('actions'),'body':nodes('body')}
    if frame.get('menu_open'):summary['dialog']={'title':frame.get('menu_title'),'nodes':nodes('menu')}
    if frame.get('drawer_open'):summary['drawer']={'title':frame.get('drawer_title'),'nodes':nodes('drawer')}
    if frame.get('confirm'):summary['confirm']=frame['confirm']
    if current.get('pending'):summary['pending_service']=current['pending']['service'];summary['notice']='Needs a real Android host; no native reply is simulated.'
    return summary

def run_project(project,commands=None,grants=None,output=None):
    project=project.resolve();validate_project(project);spec=scenario_spec(project);commands=commands if commands is not None else sys.stdin;output=output if output is not None else sys.stdout
    local_grants=spec.get('grants',[]) if grants is None else grants
    if not isinstance(local_grants,list) or any(not isinstance(cap,str) or cap not in CAPS for cap in local_grants):raise ValueError('Local grants must list known capability names.')
    if not set(local_grants).issubset(manifest(project)['capabilities']):raise ValueError('Local grants include capabilities not declared by this app.')
    sensitive=sensitive_project(project)
    def emit(value):
        if sensitive:
            if 'state' in value or 'body' in value:value={'notice':'Frame/state output is redacted for apps declaring storage.secrets.'}
            elif 'error' in value:
                value={key:item for key,item in value.items() if key in ('notice','reload','active_session_kept')}
                value['error']='Sensitive app error; payload omitted.'
        output.write(json.dumps(value,ensure_ascii=False)+'\n');output.flush()
    with tempfile.TemporaryDirectory(prefix='onklav-run-') as root:
        scratch=Path(root);key=scratch/'disposable-author.key';packer('keygen',key);built=build_run_update(project,key,scratch,0)
        with Runtime(scratch/'data') as runtime:
            review=runtime.request({'action':'stage','path':str(built['package'])});runtime.request({'action':'install','hash':review['hash'],'grants':local_grants});app_id=review['manifest']['id']
            current=runtime.request({'action':'launch','id':app_id});session=current['session'];sequence=built['version'];digest=built['digest']
            emit({'mode':'app logic only; no native UI','commands':'show, state, back, reopen, reload saved, quit, or ACTION followed by its value','reload_notice':'reload saved rebuilds code and reopens saved documents; unsaved runtime state is discarded only after a changed package installs. New permissions are not automatically granted.','local_test_grants':local_grants});emit(frame_summary(current))
            events=0;reopens=0;reloads=0;unchanged=0
            for number,line in enumerate(commands,1):
                if number>1000:raise ValueError('Logic runner command limit reached; start a new focused session.')
                if len(line)>1100000:raise ValueError('Logic runner command exceeds its size limit.')
                text=line.strip()
                if not text:continue
                if text=='quit':break
                if text=='reload':emit({'notice':'Use reload saved to rebuild and reopen saved data. Unsaved runtime state will be discarded if the code changed.'});continue
                if text=='reload saved':
                    installed=False
                    try:
                        candidate=build_run_update(project,key,scratch,sequence,app_id,digest)
                        if candidate is None:
                            unchanged+=1;emit({'reload':'unchanged','active_session_kept':current is not None,'notice':'Compiled app and manifest are unchanged; active session and drafts kept.' if current is not None else 'Compiled app is unchanged. Use reopen to try startup again.'});continue
                        review=runtime.request({'action':'stage','path':str(candidate['package'])})
                        sensitive=sensitive or bool({'storage.secrets','accounts.connect'} & set(review['manifest']['capabilities']))
                        active_grants=sorted(set(local_grants).intersection(review['manifest']['capabilities']))
                        runtime.request({'action':'install','hash':review['hash'],'grants':active_grants});installed=True;session=None
                        sequence=candidate['version'];digest=candidate['digest'];current=runtime.request({'action':'launch','id':app_id});session=current['session'];reloads+=1
                        emit({'reload':'updated','local_version':sequence,'local_test_grants':active_grants,'ungranted_capabilities':sorted(set(review['manifest']['capabilities'])-set(active_grants))});emit(frame_summary(current))
                    except (ValueError,OSError,subprocess.CalledProcessError) as error:
                        if installed:current=None
                        emit({'error':str(error),'reload':'failed','active_session_kept':not installed,'notice':'New code installed but did not open. Fix it and use reload saved or reopen.' if installed else 'Reload stopped before installation. The active session and its drafts were kept.'})
                    continue
                if text=='reopen':
                    try:
                        if session is not None:runtime.request({'action':'close','session':session})
                        session=None;current=runtime.request({'action':'launch','id':app_id});session=current['session'];reopens+=1;emit(frame_summary(current))
                    except ValueError as error:current=None;emit({'error':str(error),'notice':'No active session. Fix the code and use reload saved or reopen.'})
                    continue
                if current is None:emit({'notice':'No active session. Fix the code and use reload saved or reopen.'});continue
                if text=='show':emit(frame_summary(current));continue
                if text=='state':emit({'state':current['frame'].get('state')});continue
                if text=='back':
                    frame=current['frame'];action=frame.get('drawer_close') if frame.get('drawer_open') else frame.get('menu_close') if frame.get('menu_open') else (frame.get('confirm') or {}).get('cancel') or frame.get('back_action')
                    if not action:emit({'notice':'At the root screen. Use quit to close the local session.'});continue
                    value=''
                else:action,_,value=text.partition(' ')
                if current.get('pending'):emit({'notice':'An Android service is pending. Use show, state, reload saved, reopen or quit; no OS result is faked.'});continue
                try:current=runtime.request({'action':'event','session':session,'event':{'action':action,'value':value}});events+=1
                except ValueError as error:emit({'error':str(error),'action':action});continue
                emit(frame_summary(current))
                if current['frame'].get('close_requested'):break
    return {'events':events,'reopens':reopens,'reloads':reloads,'unchanged_reloads':unchanged,'native_ui_rendered':False,'persistent_phone_data_changed':False}

def preview_snapshot(project,events=None,grants=None):
    """Capture only validated UI from a disposable real broker; never simulate OS replies."""
    if sensitive_project(project):raise ValueError('Native preview omits apps declaring storage.secrets to avoid saving sensitive screen content.')
    validate_project(project)
    spec=scenario_spec(project,events);local_grants=spec.get('grants',[]) if grants is None else grants
    if not isinstance(local_grants,list) or any(not isinstance(cap,str) or cap not in CAPS for cap in local_grants) or not set(local_grants).issubset(manifest(project)['capabilities']):raise ValueError('Preview grants must be declared app capabilities.')
    steps=spec['steps'] if events is not None else []
    if len(steps)>100:raise ValueError('Preview supports up to100 focused scenario steps.')
    with tempfile.TemporaryDirectory(prefix='onklav-preview-app-') as root:
        scratch=Path(root);key=scratch/'disposable-author.key';packer('keygen',key);built=build_run_update(project,key,scratch,0)
        with Runtime(scratch/'data') as runtime:
            review=runtime.request({'action':'stage','path':str(built['package'])});runtime.request({'action':'install','hash':review['hash'],'grants':local_grants});app_id=review['manifest']['id']
            current=runtime.request({'action':'launch','id':app_id});session=current['session']
            for step in steps:
                if step.get('reopen'):
                    runtime.request({'action':'close','session':session});current=runtime.request({'action':'launch','id':app_id});session=current['session']
                else:
                    if current.get('pending'):raise ValueError('Preview reached an Android service. No OS reply is simulated; test that interaction on a device.')
                    current=runtime.request({'action':'event','session':session,'event':{'action':step['action'],'value':event_value(step.get('value',''))}})
                for key,target in [('expect',current['frame']['state']),('frame_expect',current['frame'])]:
                    for path,expected in step.get(key,{}).items():
                        if path_value(target,path)!=expected:raise ValueError('Preview scenario expectation failed: '+path)
            frame=dict(current['frame']);frame.pop('state',None);frame.pop('effect',None)
            return {'frame':frame,'local_test_grants':local_grants,'pending_service':(current.get('pending')or{}).get('service')}

def native_preview(project,output,events=None,grants=None,width=432,height=936,font_scale=1.0,dark=True,palette=3):
    """Render a PNG with host Compose controls under Robolectric; not an interactive phone."""
    if type(width)is not int or type(height)is not int or not 280<=width<=1200 or not 320<=height<=1600:raise ValueError('Preview size must be280–1200 by320–1600 pixels/dp.')
    if type(font_scale)not in(float,int) or not math.isfinite(font_scale) or not .8<=font_scale<=2:raise ValueError('Preview font scale must be0.8–2.')
    if type(dark)is not bool:raise ValueError('Preview dark mode must be true or false.')
    if type(palette)is not int or not 0<=palette<=11:raise ValueError('Preview palette must be0–11.')
    output=output.resolve()
    if output.suffix.lower()!='.png' or output.exists():raise ValueError('Choose a new .png output path; previews never overwrite existing files.')
    if sensitive_project(project):raise ValueError('Native preview omits apps declaring storage.secrets to avoid saving sensitive screen content.')
    if not (ROOT/'gradlew').is_file() or not (ROOT/'android/app/src/test/kotlin/app/onklav/home/SdkNativePreviewTest.kt').is_file():raise ValueError('Native preview currently needs the OnKlav host source checkout, JDK17 and Android SDK36. Portable SDK-only installations can use run for logic checks.')
    if not os.environ.get('JAVA_HOME') or not os.environ.get('ANDROID_HOME'):raise ValueError('Native preview needs JAVA_HOME (JDK17) and ANDROID_HOME (Android SDK36).')
    snapshot=preview_snapshot(project,events,grants)
    with tempfile.TemporaryDirectory(prefix='onklav-native-preview-') as root:
        scratch=Path(root);request=scratch/'frame.json';rendered=scratch/'preview.png'
        request.write_text(json.dumps(dict(snapshot,width=width,height=height,font_scale=font_scale,dark=dark,palette=palette)),encoding='utf-8');request.chmod(0o600)
        env=os.environ.copy();env['ONKLAV_PREVIEW_INPUT']=str(request);env['ONKLAV_PREVIEW_OUTPUT']=str(rendered)
        command=['bash',ROOT/'gradlew','--project-dir',ROOT,'--offline','--no-daemon','--max-workers=2','--console=plain']
        aapt=Path(env['ANDROID_HOME'])/'build-tools/35.0.0/aapt2'
        if aapt.is_file():command.append('-Pandroid.aapt2FromMavenOverride='+str(aapt))
        command+=[':android:app:testDebugUnitTest','--tests','app.onklav.home.SdkNativePreviewTest']
        env['ONKLAV_PREVIEW_RUN']=str(request)
        execute(command,env=env,timeout=240)
        data=rendered.read_bytes()
        if len(data)<24 or data[:8]!=b'\x89PNG\r\n\x1a\n' or struct.unpack('>II',data[16:24])!=(width,height):raise ValueError('Native renderer did not produce the requested PNG.')
        output.parent.mkdir(parents=True,exist_ok=True)
        with os.fdopen(os.open(output,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600),'wb')as dest:dest.write(data)
    return {'preview':str(output),'native_controls_rendered':True,'interactive':False,'device_services_executed':False,'local_test_grants':snapshot['local_test_grants'],'pending_service':snapshot['pending_service'],'note':'Native Compose image preview. Web, media, drawing and speech use labelled device placeholders. No Android service result is simulated; phone/provider acceptance remains required.'}

def device_preview(project,serial,host_package='app.onklav.home.prototype.rewrite',feedback=False):
    """Deploy to an already installed debug host; real service interactions occur on the device."""
    if not isinstance(serial,str)or not serial or not re.fullmatch(r'[A-Za-z0-9.:_-]+',serial):raise ValueError('Choose one explicit adb device serial.')
    if not re.fullmatch(r'[a-z][a-z0-9_]*(?:\.[a-z][a-z0-9_]*)+',host_package):raise ValueError('Invalid debug host package.')
    adb=shutil.which('adb')
    if not adb:raise ValueError('Device preview needs Android platform-tools (adb) and an authorized device; no device result is simulated.')
    base=[adb,'-s',serial]
    execute(base+['get-state'],capture=True,timeout=15)
    execute(base+['shell','run-as',host_package,'id'],capture=True,timeout=15)
    if feedback:return json.loads(execute(base+['shell','run-as',host_package,'cat','files/sdk-dev/feedback.json'],capture=True,timeout=15))
    component=host_package+'/app.onklav.home.SdkDevPreviewActivity'
    resolved=execute(base+['shell','cmd','package','resolve-activity','--brief','-n',component],capture=True,timeout=15)
    if 'SdkDevPreviewActivity'not in resolved:raise ValueError('Install an updated OnKlav debug host with device-preview support first.')
    project=project.resolve()
    if sensitive_project(project):raise ValueError('Device preview currently omits secret-capability apps; install their signed package normally for device qualification.')
    validate_project(project)
    state_dir=project/'target/onklav-device-preview';state_dir.mkdir(parents=True,exist_ok=True,mode=0o700);state_dir.chmod(0o700)
    key=state_dir/'author.key'
    if not key.exists():packer('keygen',key);key.chmod(0o600)
    counter=state_dir/'version.json';sequence=json.loads(counter.read_text()).get('version',0)if counter.exists()else 0
    ticket=uuid.uuid4().hex
    with tempfile.TemporaryDirectory(prefix='onklav-device-preview-')as tmp:
        scratch=Path(tmp);built=build_run_update(project,key,scratch,sequence);counter.write_text(json.dumps({'version':built['version']}));counter.chmod(0o600)
        request=scratch/'request.json';request.write_text(json.dumps({'ticket':ticket,'created':int(time.time()*1000),'id':built['manifest']['id']}));request.chmod(0o600)
        remote='/data/local/tmp/onklav-preview-'+ticket
        try:
            execute(base+['push',built['package'],remote+'.pkg'],timeout=30);execute(base+['push',request,remote+'.json'],timeout=30)
            execute(base+['shell','run-as',host_package,'mkdir','-p','files/sdk-dev'],timeout=15)
            execute(base+['shell','run-as',host_package,'cp',remote+'.pkg','files/sdk-dev/package.onklav-a'],timeout=15)
            execute(base+['shell','run-as',host_package,'cp',remote+'.json','files/sdk-dev/request.json'],timeout=15)
            execute(base+['shell','am','start','-W','-n',component,'--es','ticket',ticket],timeout=30)
        finally:execute(base+['shell','rm','-f',remote+'.pkg',remote+'.json'],timeout=15)
    return {'device_preview_started':True,'serial':serial,'host_package':host_package,'permissions':'Review and approve on the device; none are auto-granted.','feedback':'Use device-preview --feedback for phase/error status.','note':'Uses a persistent private development signer and increasing local versions. Existing differently signed apps are not replaced. No shipping APK was built.'}

def upgrade_project(project):
    config=tomllib.loads((project/'Cargo.toml').read_text(encoding='utf-8'))
    deps=config.get('dependencies',{})
    gui=deps.get('onklav-ui',{});base=deps.get('onklav-app',{})
    if not isinstance(gui,dict) or not isinstance(base,dict) or gui.get('path')!='vendor/developer/components' or base.get('path')!='vendor/sdk/rust':raise ValueError('Upgrade supports portable generated projects. Review linked/custom dependency paths explicitly.')
    if not (project/'vendor').is_dir() or not (project/'Cargo.lock').is_file():raise ValueError('Expected the portable vendor directory and Cargo.lock.')
    target=project/'target';target.mkdir(exist_ok=True);backup=Path(tempfile.mkdtemp(prefix='sdk-backup-',dir=target))
    shutil.copyfile(project/'Cargo.lock',backup/'Cargo.lock');(project/'vendor').rename(backup/'vendor')
    try:
        vendor_sdk(project)
        execute(['cargo','update','--manifest-path',project/'Cargo.toml','-p','onklav-ui'])
    except BaseException:
        shutil.rmtree(project/'vendor',ignore_errors=True);(backup/'vendor').rename(project/'vendor');shutil.copyfile(backup/'Cargo.lock',project/'Cargo.lock');raise
    return {'sdk':json.loads((HERE/'contract.json').read_text())['sdk_tools'],'backup':str(backup),'note':'Previous vendor and lockfile retained. Review changes and verify before shipping; keep your app ID/key and increase its release version.'}

def catalog(query=''):
    items=json.loads((HERE/'catalog.json').read_text(encoding='utf-8'))['components']
    return [item for item in items if query.casefold() in json.dumps(item,ensure_ascii=False).casefold()]

def capabilities(name=None, channel='current'):
    """Discover this toolkit snapshot without claiming live device access."""
    if channel not in ('current', 'released', 'working'):
        raise ValueError('Choose current, released or working capabilities.')
    contract=json.loads((HERE/'contract.json').read_text(encoding='utf-8'))
    if name is not None and name not in contract['capabilities']:
        raise ValueError('Unsupported capability: '+name+'. Run capabilities to see implemented services.')
    selected=[name] if name is not None else contract['capabilities']
    items=[]
    for cap in selected:
        detail=contract['capability_details'][cap]
        available=channel in ('current','working') or detail['channel']=='released'
        items.append({'id':cap,**detail,'available_in_snapshot':available,'qualification':('documented baseline' if detail['channel']=='released' else 'implemented preview; device/provider checks required')})
    return {'scope':contract['discovery']['scope'],'channel':channel,
            'reference_host_build':contract['reference_host_build'],'host_protocol_build':contract['reference_host_build'],'reference_apk':contract.get('reference_apk'),
            'working_build_note':contract['discovery']['working_build_note'],
            'device_probed':False,'permissions_checked':False,
            'capabilities':items,'extensions':contract['extension_policy']}

def doctor():
    if sys.version_info<(3,11):raise ValueError('Python3.11 or newer is required.')
    version=execute(['rustc','--version'],capture=True).strip()
    match=re.search(r'rustc (\d+)\.(\d+)\.',version)
    if not match or tuple(map(int,match.groups()))<(1,90):raise ValueError('Rust1.90 or newer is required.')
    targets=execute(['rustup','target','list','--installed'],capture=True).splitlines()
    if 'wasm32-unknown-unknown' not in targets:raise ValueError('Run rustup target add wasm32-unknown-unknown.')
    for path in ['sdk/rust/Cargo.toml','core/Cargo.toml','core/src/bin/onklav-pack.rs','developer/host/Cargo.lock','developer/components/Cargo.toml','developer/components/Cargo.lock']:
        if not (ROOT/path).is_file():raise ValueError('Incomplete SDK: missing '+path)
    native=re.search(r'HOST_BUILD:u32=(\d+)',(ROOT/'core/src/sdk.rs').read_text(encoding='utf-8'))
    if not native or int(native.group(1))!=HOST_BUILD:raise ValueError('SDK contract and native guest protocol disagree; extract a complete current kit.')
    return {'ready':True,'sdk_tools':CONTRACT['sdk_tools'],'reference_apk':CONTRACT.get('reference_apk'),'host_protocol_build':HOST_BUILD,'rust':version,'python':sys.version.split()[0],'target':'wasm32-unknown-unknown','note':'First build downloads Cargo dependencies and needs a native linker. Android UI/media still require a phone.'}

def main():
    parser=argparse.ArgumentParser(description=__doc__);sub=parser.add_subparsers(dest='command',required=True)
    sub.add_parser('doctor')
    caps=sub.add_parser('capabilities');caps.add_argument('name',nargs='?');caps.add_argument('--channel',choices=['current','released','working'],default=CONTRACT['discovery']['default_channel'])
    preflight=sub.add_parser('validate');preflight.add_argument('project',type=Path)
    local=sub.add_parser('run');local.add_argument('project',type=Path);local.add_argument('--commands',type=Path);local.add_argument('--grant',action='append',choices=sorted(CAPS))
    preview=sub.add_parser('preview');preview.add_argument('project',type=Path);preview.add_argument('--output',type=Path,required=True);preview.add_argument('--events',type=Path);preview.add_argument('--grant',action='append',choices=sorted(CAPS));preview.add_argument('--width',type=int,default=432);preview.add_argument('--height',type=int,default=936);preview.add_argument('--font-scale',type=float,default=1.0);preview.add_argument('--palette',type=int,default=3);preview.add_argument('--light',action='store_true')
    device=sub.add_parser('device-preview');device.add_argument('project',type=Path);device.add_argument('--serial',required=True);device.add_argument('--host-package',default='app.onklav.home.prototype.rewrite');device.add_argument('--feedback',action='store_true')
    upgrade=sub.add_parser('upgrade');upgrade.add_argument('project',type=Path)
    discover=sub.add_parser('catalog');discover.add_argument('query',nargs='?',default='')
    verify=sub.add_parser('verify');verify.add_argument('project',type=Path);verify.add_argument('--scenario',type=Path)
    new=sub.add_parser('new');new.add_argument('destination',type=Path);new.add_argument('--id',required=True);new.add_argument('--name',required=True);new.add_argument('--template',choices=TEMPLATES,default='document');new.add_argument('--icon',help='Stock symbol or bounded128px PNG path');new.add_argument('--capability',action='append',choices=sorted(CAPS),default=[],help='Declare an additional capability; users still control grants');new.add_argument('--feature',action='append',choices=['file-viewer'],default=[],help='Add a reusable feature and its required capabilities');new.add_argument('--origin',action='append',default=[],help='Declare an exact HTTPS origin and network.http access (current host snapshot)');new.add_argument('--linked-sdk',action='store_true',help='Use live SDK paths instead of portable frozen dependencies')
    key=sub.add_parser('keygen');key.add_argument('path',type=Path)
    build=sub.add_parser('build');build.add_argument('project',type=Path);build.add_argument('--key',type=Path,required=True);build.add_argument('--output',type=Path)
    check=sub.add_parser('inspect');check.add_argument('package',type=Path)
    compatible=sub.add_parser('check');compatible.add_argument('package',type=Path)
    test=sub.add_parser('test');test.add_argument('project',type=Path);test.add_argument('--package',type=Path);test.add_argument('--scenario',type=Path)
    host=sub.add_parser('host');host.add_argument('--data-dir',type=Path,required=True)
    args=parser.parse_args()
    if args.command=='doctor':result=doctor()
    elif args.command=='capabilities':result=capabilities(args.name,args.channel)
    elif args.command=='run':
        with (args.commands.open(encoding='utf-8') if args.commands else contextlib.nullcontext(sys.stdin)) as commands:result=run_project(args.project.resolve(),commands,args.grant)
    elif args.command=='device-preview':result=device_preview(args.project,args.serial,args.host_package,args.feedback)
    elif args.command=='preview':result=native_preview(args.project.resolve(),args.output,args.events,args.grant,args.width,args.height,args.font_scale,not args.light,args.palette)
    elif args.command=='validate':result=validate_project(args.project.resolve())
    elif args.command=='upgrade':result=upgrade_project(args.project.resolve())
    elif args.command=='catalog':result=catalog(args.query)
    elif args.command=='verify':result=verify_project(args.project.resolve(),args.scenario)
    elif args.command=='new':result=new_project(args.destination.resolve(),args.id,args.name,args.template,portable=not args.linked_sdk,icon=args.icon,capabilities=args.capability,features=args.feature,network_origins=args.origin)
    elif args.command=='keygen':
        path=args.path.resolve()
        if path.is_relative_to(ROOT):raise ValueError('Keep publisher keys outside the SDK source tree.')
        path.parent.mkdir(parents=True,exist_ok=True);packer('keygen',path);result={'key_created':str(path),'note':'Keep private; reuse this key for updates.'}
    elif args.command=='build':result=build_project(args.project.resolve(),args.key,args.output.resolve() if args.output else None)
    elif args.command=='inspect':result=inspect_package(args.package.resolve())
    elif args.command=='check':result=check_package(args.package.resolve())
    elif args.command=='test':
        project=args.project.resolve();m=manifest(project);result=test_project(project,(args.package or project/'dist'/(m['id']+'-v'+str(m['version'])+'.onklav-a')).resolve(),args.scenario)
    else:
        execute([build_host(),args.data_dir.resolve()]);return
    print(json.dumps(result,indent=2,ensure_ascii=False))

if __name__=='__main__':
    if hasattr(sys.stdout,'reconfigure'):sys.stdout.reconfigure(encoding='utf-8')
    try:main()
    except (ValueError,AssertionError,KeyError,TypeError,OSError,subprocess.CalledProcessError) as e:
        print('OnKlav SDK: '+str(e),file=sys.stderr);sys.exit(1)
