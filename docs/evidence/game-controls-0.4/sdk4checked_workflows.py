from pathlib import Path
import tempfile,sys,json
w=Path('/workspace/scratch/b5e698131b7f');r=w/'source/OnKlav';sys.path.insert(0,str(r/'developer/games'));import gamekit as g
results=[]
with tempfile.TemporaryDirectory(prefix='onklav-sdk-flow-')as t:
 key=Path(t)/'publisher.key';g.keygen(key)
 for name in ['touch2d','scene3d','turn']:
  p=w/('sdk4checked-' +name);g.create(p,'app.example.'+name,name,name)
  out=w/('sdk4checked-' +name+'.onklav-g');z=g.build(p,out,key,str(w/'toolchain/Godot_v4.7.2-stable_linux.x86_64'));results.append({'template':name,'bytes':out.stat().st_size,'signature_verified':z['signature_verified'],'minimum_host':z['manifest']['min_host']})
(w/'flow-sdk-workflows.json').write_text(json.dumps(results,indent=2));print(json.dumps(results))
