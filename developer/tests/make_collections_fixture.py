"""Deterministic test-only signed guest; no shipping publisher key is used."""
import hashlib
import io
import json
from pathlib import Path
import zipfile
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from cryptography.hazmat.primitives.serialization import Encoding, PublicFormat

def uleb(n):
    out=bytearray()
    while True:
        value=n&127;n>>=7;out.append(value|(128 if n else 0))
        if not n:return bytes(out)

def sleb(n):
    out=bytearray()
    while True:
        value=n&127;n>>=7;done=(n==0 and value&64==0) or (n==-1 and value&64!=0)
        out.append(value|(0 if done else 128))
        if done:return bytes(out)

def vector(items):return uleb(len(items))+b''.join(items)
def text(value):
    data=value.encode();return uleb(len(data))+data
def section(kind,data):return bytes([kind])+uleb(len(data))+data
def fixture(http=False):
    base={'title':'Storage fixture','state':{},'actions':[],'body':[],'drawer':[],'menu':[]}
    start={**base,'effect':{'service':'collections.transact','args':{'name':'items','expected_revision':0,'schema':1,'operations':[{'operation':'put','id':'one','data':{'title':'Test item'}}]},'then':'done'}}
    if http:
        base['title']='HTTPS fixture'
        start={**base,'effect':{'service':'http.request','args':{'url':'https://api.example.com/items'},'then':'done'}}
    start=json.dumps(start,separators=(',',':')).encode();done=json.dumps(base,separators=(',',':')).encode()
    wasm=b'\0asm\x01\0\0\0'
    wasm+=section(1,vector([b'\x60\x01\x7f\x01\x7f',b'\x60\x02\x7f\x7f\x01\x7e']))
    wasm+=section(3,vector([b'\0',b'\1']))+section(5,b'\x01\x00\x01')
    wasm+=section(7,vector([text('memory')+b'\x02\x00',text('onklav_alloc')+b'\0\0',text('onklav_step')+b'\0\1']))
    alloc=b'\0\x41\0\x0b'
    step=b'\0\x20\x01\x41'+sleb(80)+b'\x4b\x04\x7e\x42'+sleb((8192<<32)|len(done))+b'\x05\x42'+sleb((4096<<32)|len(start))+b'\x0b\x0b'
    wasm+=section(10,vector([uleb(len(alloc))+alloc,uleb(len(step))+step]))
    wasm+=section(11,vector([b'\0\x41'+sleb(offset)+b'\x0b'+uleb(len(data))+data for offset,data in [(4096,start),(8192,done)]]))
    key=Ed25519PrivateKey.from_private_bytes(bytes([9])*32)  # Public test fixture identity only.
    manifest=json.dumps({'schema':'onklav.app.v1','runtime':'rust-wasm-ui.v1','id':'org.example.httpfixture' if http else 'org.example.collectionsfixture','name':'HTTPS fixture' if http else 'Storage fixture','version':1,'publisher':key.public_key().public_bytes(Encoding.Raw,PublicFormat.Raw).hex(),'capabilities':['network.http'] if http else ['storage.private'],**({'network':{'origins':['https://api.example.com']}} if http else {})},separators=(',',':')).encode()
    signature=key.sign(b'onklav.app.v1\0'+len(manifest).to_bytes(8,'little')+manifest+hashlib.sha256(wasm).digest())
    out=io.BytesIO()
    with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED) as archive:
        for name,data in [('manifest.json',manifest),('main.wasm',wasm),('signature.ed25519',signature)]:
            entry=zipfile.ZipInfo(name,date_time=(2020,1,1,0,0,0));entry.compress_type=zipfile.ZIP_DEFLATED;archive.writestr(entry,data)
    return out.getvalue()

if __name__=='__main__':
    path=Path(__file__).resolve().parents[2]/'docs/fixtures/collections-wip.onklav-a'
    path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(fixture())
    print('Wrote test-only collections fixture')
