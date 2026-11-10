from pathlib import Path
import tempfile,subprocess,sys,json,zipfile,shutil
w=Path('/workspace/scratch/b5e698131b7f');r=w/'recovered/OnKlav';sys.path.insert(0,str(r/'developer/games'));import gamekit as g
binary=str(w/'toolchain/Godot_v4.7.2-stable_linux.x86_64');result=[]
with tempfile.TemporaryDirectory(prefix='onklav-game-test-key-')as temp:
 key=Path(temp)/'publisher.key';g.keygen(key)
 for name in ['touch2d','scene3d','turn']:
  p=w/('finished-sdk-'+name);g.create(p,'app.example.tap'if name=='touch2d'else 'app.example.'+name,name,name)
  out=w/('finished-sdk-'+name+'.onklav-g');report=g.build(p,out,key,binary);assert out.stat().st_size<2*1024*1024
  with zipfile.ZipFile(out)as z:assert not any(n.endswith(('.wasm','engine-template.zip'))for n in z.namelist())
  if name=='touch2d':shutil.copy2(out,r/'docs/fixtures/games/tap.onklav-g')
  result.append({'template':name,'path':str(out),'bytes':out.stat().st_size,'signature_verified':report['signature_verified'],'shared_engine':report['manifest']['engine'],'owner_approval_required':report['owner_approval_required']})
(w/'finished-sdk-workflows.json').write_text(json.dumps(result,indent=2));print(json.dumps(result))
